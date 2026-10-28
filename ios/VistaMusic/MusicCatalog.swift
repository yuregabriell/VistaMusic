import Foundation
import Combine

enum MusicCatalogError: LocalizedError {
    case malformedURL
    case badResponse
    var errorDescription: String? { "Não foi possível carregar este conteúdo." }
}

enum MusicAPI {
    private struct AudiusResult: Decodable {
        let data: [AudiusTrack]
    }
    private struct AudiusTrack: Decodable {
        let id: String
        let title: String
        let user: AudiusUser?
        let artwork: AudiusArtwork?
    }
    private struct AudiusUser: Decodable { let name: String? }
    private struct AudiusArtwork: Decodable {
        let large: String?
        let medium: String?
        let small: String?
        enum CodingKeys: String, CodingKey {
            case large = "1000x1000", medium = "480x480", small = "150x150"
        }
    }
    private struct AppleChart: Decodable {
        let feed: ChartFeed
    }
    private struct ChartFeed: Decodable {
        let results: [ChartEntry]
    }
    private struct ChartEntry: Decodable {
        let id: String
        let name: String
        let artistName: String
        let artworkUrl100: String?
    }
    private struct ITunesResponse: Decodable {
        let results: [ITunesItem]
    }
    private struct ITunesItem: Decodable {
        let wrapperType: String?
        let kind: String?
        let trackId: Int?
        let collectionId: Int?
        let trackName: String?
        let collectionName: String?
        let artistName: String?
        let artworkUrl100: String?
        let previewUrl: String?
        let releaseDate: String?
    }

    private static func json<T: Decodable>(_ type: T.Type, url: URL) async throws -> T {
        var request = URLRequest(url: url)
        request.timeoutInterval = 20
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("VistaMusic-iOS/0.1", forHTTPHeaderField: "User-Agent")
        let (data, response) = try await URLSession.shared.data(for: request)
        guard (response as? HTTPURLResponse).map({ (200...299).contains($0.statusCode) }) == true else {
            throw MusicCatalogError.badResponse
        }
        return try JSONDecoder().decode(type, from: data)
    }

    private static func url(_ base: String, params: [URLQueryItem] = []) throws -> URL {
        guard var parts = URLComponents(string: base) else { throw MusicCatalogError.malformedURL }
        if !params.isEmpty { parts.queryItems = params }
        guard let result = parts.url else { throw MusicCatalogError.malformedURL }
        return result
    }

    static func trending() async throws -> [MusicTrack] {
        let response = try await json(AudiusResult.self,
            url: url("https://api.audius.co/v1/tracks/trending",
                     params: [.init(name: "limit", value: "45")]))
        return response.data.map { t in
            MusicTrack(id: "audius:\(t.id)", title: t.title,
                       artist: t.user?.name ?? "Artista",
                       art: t.artwork?.large ?? t.artwork?.medium ?? t.artwork?.small ?? "",
                       audio: "https://api.audius.co/v1/tracks/\(t.id)/stream",
                       source: "Audius")
        }
    }

    static func searchAudius(_ term: String) async throws -> [MusicTrack] {
        let response = try await json(AudiusResult.self,
            url: url("https://api.audius.co/v1/tracks/search",
                     params: [.init(name: "query", value: term),
                              .init(name: "limit", value: "30")]))
        return response.data.map { t in
            MusicTrack(id: "audius:\(t.id)", title: t.title,
                       artist: t.user?.name ?? "Artista",
                       art: t.artwork?.large ?? t.artwork?.medium ?? t.artwork?.small ?? "",
                       audio: "https://api.audius.co/v1/tracks/\(t.id)/stream",
                       source: "Audius")
        }
    }

    static func charts(country: String) async throws -> [MusicTrack] {
        let market = ["pt", "us", "br"].contains(country) ? country : "pt"
        let result = try await json(AppleChart.self,
            url: url("https://rss.applemarketingtools.com/api/v2/\(market)/music/most-played/50/songs.json"))
        return result.feed.results.map {
            MusicTrack(id: "chart:\($0.id)", title: $0.name,
                       artist: $0.artistName,
                       art: ($0.artworkUrl100 ?? "").replacingOccurrences(of: "100x100bb", with: "600x600bb"),
                       audio: "", source: "Apple Music · chart \(market.uppercased())")
        }
    }

    static func searchITunes(_ term: String, entity: String = "song") async throws -> [ITunesItem] {
        let result = try await json(ITunesResponse.self,
            url: url("https://itunes.apple.com/search",
                     params: [.init(name: "term", value: term),
                              .init(name: "country", value: "PT"),
                              .init(name: "entity", value: entity),
                              .init(name: "limit", value: "50")]))
        return result.results
    }

    static func searchPreviews(_ term: String) async throws -> [MusicTrack] {
        try await searchITunes(term).compactMap { item in
            guard let id = item.trackId, let name = item.trackName,
                  let artist = item.artistName, let preview = item.previewUrl,
                  preview.hasPrefix("https://") else { return nil }
            return MusicTrack(id: "itunes:\(id)", title: name, artist: artist,
                              art: (item.artworkUrl100 ?? "").replacingOccurrences(of: "100x100bb", with: "600x600bb"),
                              audio: preview, source: "iTunes · prévia de 30 s")
        }
    }

    static func searchAlbums(_ term: String) async throws -> [MusicAlbum] {
        try await searchITunes(term, entity: "album").compactMap { item in
            guard let id = item.collectionId, let title = item.collectionName,
                  let artist = item.artistName else { return nil }
            let year = String((item.releaseDate ?? "").prefix(4))
            let cover = (item.artworkUrl100 ?? "").replacingOccurrences(of: "100x100bb", with: "600x600bb")
            return MusicAlbum(id: id, title: title, artist: artist, year: year, art: cover)
        }
    }

    static func albumTracks(id: Int) async throws -> [MusicTrack] {
        let response = try await json(ITunesResponse.self,
            url: url("https://itunes.apple.com/lookup",
                     params: [.init(name: "id", value: "\(id)"),
                              .init(name: "entity", value: "song"),
                              .init(name: "country", value: "PT")]))
        return response.results.compactMap { item in
            guard let trackId = item.trackId, item.kind == "song",
                  let name = item.trackName, let artist = item.artistName else { return nil }
            return MusicTrack(id: "itunes:\(trackId)", title: name, artist: artist,
                              art: (item.artworkUrl100 ?? "").replacingOccurrences(of: "100x100bb", with: "600x600bb"),
                              audio: item.previewUrl ?? "", source: "iTunes · prévia de 30 s")
        }
    }

    static func playable(_ track: MusicTrack) async -> MusicTrack? {
        if track.audioURL != nil { return track }
        guard let matches = try? await searchPreviews("\(track.artist) \(track.title)") else {
            return nil
        }
        let wantedTitle = track.title.folding(options: [.diacriticInsensitive, .caseInsensitive], locale: .current)
        let wantedArtist = track.artist.folding(options: [.diacriticInsensitive, .caseInsensitive], locale: .current)
        return matches.first {
            let title = $0.title.folding(options: [.diacriticInsensitive, .caseInsensitive], locale: .current)
            let artist = $0.artist.folding(options: [.diacriticInsensitive, .caseInsensitive], locale: .current)
            return title == wantedTitle && (artist == wantedArtist ||
                    artist.contains(wantedArtist) || wantedArtist.contains(artist))
        }
    }
}

@MainActor
final class MusicCatalog: ObservableObject {
    @Published var trending: [MusicTrack] = []
    @Published var portugalChart: [MusicTrack] = []
    @Published var usaChart: [MusicTrack] = []
    @Published var searchSongs: [MusicTrack] = []
    @Published var searchAlbums: [MusicAlbum] = []
    @Published var loading = false
    @Published var searching = false
    @Published var errorMessage: String?
    private var lastQuery = ""

    func refresh() async {
        loading = true
        async let audius = (try? MusicAPI.trending()) ?? []
        async let portugal = (try? MusicAPI.charts(country: "pt")) ?? []
        async let unitedStates = (try? MusicAPI.charts(country: "us")) ?? []
        trending = await audius
        portugalChart = await portugal
        usaChart = await unitedStates
        errorMessage = trending.isEmpty && portugalChart.isEmpty
            ? "Não foi possível carregar os catálogos. Verifica a ligação à Internet."
            : nil
        loading = false
    }

    func search(_ raw: String) async {
        let query = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        lastQuery = query
        guard !query.isEmpty else {
            searchSongs = []
            searchAlbums = []
            searching = false
            return
        }
        searching = true
        async let audius = (try? MusicAPI.searchAudius(query)) ?? []
        async let previews = (try? MusicAPI.searchPreviews(query)) ?? []
        async let albums = (try? MusicAPI.searchAlbums(query)) ?? []
        let songs = await audius + previews
        let foundAlbums = await albums
        guard lastQuery == query else { return }
        searchSongs = songs
        searchAlbums = foundAlbums
        searching = false
    }
}
