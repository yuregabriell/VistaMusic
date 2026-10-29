import Foundation
import YouTubeKit

/// Experimental, unauthenticated YouTube Music songs search.
/// Uses the same public WEB_REMIX search shape as the VistaMusic Android client.
/// The endpoint can change without warning and may return no results.
actor YouTubeMusicSearch {
    static let shared = YouTubeMusicSearch()

    private let filter = "EgWKAQIIAWoMEA4QChADEAQQCRAF"
    private let agent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 13_5) AppleWebKit/537.36 Chrome/127.0 Safari/537.36"
    private var apiKey: String?
    private var clientVersion: String?

    private func fetchConfig() async throws {
        guard apiKey == nil || clientVersion == nil else { return }
        guard let home = URL(string: "https://music.youtube.com/") else { throw MusicCatalogError.malformedURL }
        var req = URLRequest(url: home)
        req.timeoutInterval = 15
        req.setValue(agent, forHTTPHeaderField: "User-Agent")
        let (data, _) = try await URLSession.shared.data(for: req)
        let html = String(decoding: data, as: UTF8.self)
        func match(_ key: String) -> String? {
            let expression = "\"\(key)\"\\s*:\\s*\"([^\"]+)\""
            guard let regex = try? NSRegularExpression(pattern: expression) else { return nil }
            let range = NSRange(html.startIndex..<html.endIndex, in: html)
            guard let found = regex.firstMatch(in: html, range: range),
                  let valueRange = Range(found.range(at: 1), in: html) else { return nil }
            return String(html[valueRange])
        }
        guard let key = match("INNERTUBE_API_KEY"),
              let version = match("INNERTUBE_CLIENT_VERSION") else {
            throw MusicCatalogError.badResponse
        }
        apiKey = key
        clientVersion = version
    }

    private func value(_ root: Any?, _ keys: [String]) -> Any? {
        keys.reduce(root) { result, key in (result as? [String: Any])?[key] }
    }

    private func text(_ runs: Any?) -> String {
        ((runs as? [[String: Any]]) ?? [])
            .compactMap { $0["text"] as? String }
            .joined().trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private func decodeSong(_ item: [String: Any]) -> MusicTrack? {
        let fromPlaylist = value(item, ["playlistItemData", "videoId"]) as? String
        let fromButton = value(item, ["overlay", "musicItemThumbnailOverlayRenderer",
                                     "content", "musicPlayButtonRenderer", "playNavigationEndpoint",
                                     "watchEndpoint", "videoId"]) as? String
        guard let id = (fromPlaylist ?? fromButton),
              id.range(of: "^[A-Za-z0-9_-]{11}$", options: .regularExpression) != nil else {
            return nil
        }

        let columns = item["flexColumns"] as? [[String: Any]] ?? []
        func runs(_ index: Int) -> [[String: Any]] {
            guard columns.indices.contains(index) else { return [] }
            return value(columns[index], ["musicResponsiveListItemFlexColumnRenderer",
                                           "text", "runs"]) as? [[String: Any]] ?? []
        }
        let title = text(runs(0))
        let candidates = runs(1).compactMap { $0["text"] as? String }
        let artist = candidates.first {
            let t = $0.trimmingCharacters(in: .whitespacesAndNewlines)
            return !t.isEmpty && t != "Song" && t != "Music" && t != "Video" &&
                t != " • " && !t.contains("•") &&
                t.range(of: #"^\d+:\d+$"#, options: .regularExpression) == nil
        }?.replacingOccurrences(of: " - Topic", with: "") ?? ""
        guard !title.isEmpty && !artist.isEmpty else { return nil }

        let thumbnails = value(item, ["thumbnail", "musicThumbnailRenderer",
                                      "thumbnail", "thumbnails"]) as? [[String: Any]] ?? []
        let thumbnail = thumbnails.max {
            ($0["width"] as? Int ?? 0) < ($1["width"] as? Int ?? 0)
        }
        let art = (thumbnail?["url"] as? String ?? "")
        return MusicTrack(id: "youtube:\(id)", title: title, artist: artist,
                          art: art.hasPrefix("https://") ? art : "",
                          audio: "youtube:\(id)", source: "YouTube Music · song")
    }

    private func collect(_ node: Any, output: inout [[String: Any]], depth: Int = 0) {
        guard depth < 18 && output.count < 90 else { return }
        if let object = node as? [String: Any] {
            if let renderer = object["musicResponsiveListItemRenderer"] as? [String: Any] {
                output.append(renderer)
            }
            for (key, value) in object where key != "musicResponsiveListItemRenderer" {
                collect(value, output: &output, depth: depth + 1)
            }
        } else if let array = node as? [Any] {
            for item in array.prefix(200) {
                collect(item, output: &output, depth: depth + 1)
            }
        }
    }

    func search(_ query: String, limit: Int = 30) async throws -> [MusicTrack] {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return [] }
        try await fetchConfig()
        guard let apiKey, let clientVersion,
              var parts = URLComponents(string: "https://music.youtube.com/youtubei/v1/search")
        else { throw MusicCatalogError.malformedURL }
        parts.queryItems = [
            URLQueryItem(name: "key", value: apiKey),
            URLQueryItem(name: "prettyPrint", value: "false")
        ]
        guard let url = parts.url else { throw MusicCatalogError.malformedURL }
        let payload: [String: Any] = [
            "context": ["client": ["clientName": "WEB_REMIX", "clientVersion": clientVersion,
                                   "hl": "en", "gl": "PT"]],
            "query": trimmed, "params": filter
        ]
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.timeoutInterval = 18
        request.httpBody = try JSONSerialization.data(withJSONObject: payload)
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(agent, forHTTPHeaderField: "User-Agent")
        request.setValue("https://music.youtube.com", forHTTPHeaderField: "Origin")
        request.setValue("67", forHTTPHeaderField: "X-YouTube-Client-Name")
        request.setValue(clientVersion, forHTTPHeaderField: "X-YouTube-Client-Version")
        let (data, response) = try await URLSession.shared.data(for: request)
        guard (response as? HTTPURLResponse).map({ (200...299).contains($0.statusCode) }) == true else {
            throw MusicCatalogError.badResponse
        }
        let root = try JSONSerialization.jsonObject(with: data)
        var renderers: [[String: Any]] = []
        collect(root, output: &renderers)
        var seen = Set<String>()
        return renderers.compactMap(decodeSong)
            .filter { seen.insert($0.id).inserted }
            .prefix(limit).map { $0 }
    }
}

/// YouTubeKit is a native iOS equivalent of the Android client's experimental
/// stream resolver, not an official YouTube Music playback service.
enum YouTubeAudio {
    static func resolve(_ track: MusicTrack) async -> MusicTrack? {
        guard track.isYouTube else { return track.audioURL == nil ? nil : track }
        let id = String(track.audio.dropFirst("youtube:".count))
        guard id.range(of: "^[A-Za-z0-9_-]{11}$", options: .regularExpression) != nil else {
            return nil
        }
        do {
            let streams = try await YouTube(videoID: id).streams
            let stream = streams.filterAudioOnly()
                .filter { $0.isNativelyPlayable }
                .highestAudioBitrateStream()
            guard let stream else { return nil }
            return MusicTrack(id: track.id, title: track.title, artist: track.artist,
                              art: track.art, audio: stream.url.absoluteString,
                              source: track.source)
        } catch {
            return nil
        }
    }
}
