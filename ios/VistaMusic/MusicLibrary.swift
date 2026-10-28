import Foundation
import Combine

@MainActor
final class MusicLibrary: ObservableObject {
    private struct Saved: Codable {
        var favorites: [MusicTrack] = []
        var playlists: [MusicPlaylist] = []
        var recent: [MusicTrack] = []
        var counts: [String: Int] = [:]
    }
    private let key = "VistaMusic.iOS.LocalLibrary.v1"

    @Published private(set) var favorites: [MusicTrack] = []
    @Published private(set) var playlists: [MusicPlaylist] = []
    @Published private(set) var recent: [MusicTrack] = []
    @Published private(set) var counts: [String: Int] = [:]

    init() {
        if let data = UserDefaults.standard.data(forKey: key),
           let saved = try? JSONDecoder().decode(Saved.self, from: data) {
            favorites = saved.favorites
            playlists = saved.playlists
            recent = saved.recent
            counts = saved.counts
        }
    }

    private func persist() {
        let value = Saved(favorites: favorites, playlists: playlists,
                          recent: recent, counts: counts)
        if let data = try? JSONEncoder().encode(value) {
            UserDefaults.standard.set(data, forKey: key)
        }
    }

    func isLiked(_ track: MusicTrack) -> Bool {
        favorites.contains(where: { $0.id == track.id })
    }

    func toggleLike(_ track: MusicTrack) {
        if isLiked(track) {
            favorites.removeAll { $0.id == track.id }
        } else {
            favorites.insert(track, at: 0)
        }
        persist()
    }

    func registerPlay(_ track: MusicTrack) {
        recent.removeAll { $0.id == track.id }
        recent.insert(track, at: 0)
        recent = Array(recent.prefix(80))
        counts[track.artist, default: 0] += 1
        persist()
    }

    func recommended(_ candidates: [MusicTrack]) -> [MusicTrack] {
        let likedArtists = Set(favorites.map { $0.artist.lowercased() })
        let recentlyHeard = Set(recent.prefix(12).map(\.id))
        return candidates.sorted {
            func score(_ t: MusicTrack) -> Int {
                (counts[t.artist, default: 0] * 4) +
                (likedArtists.contains(t.artist.lowercased()) ? 12 : 0) -
                (recentlyHeard.contains(t.id) ? 8 : 0)
            }
            return score($0) > score($1)
        }
    }

    func createPlaylist(name: String, description: String = "") {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        playlists.append(MusicPlaylist(name: String(trimmed.prefix(65)),
                                       description: String(description.prefix(240))))
        persist()
    }

    func add(_ track: MusicTrack, to playlistID: String) {
        guard let index = playlists.firstIndex(where: { $0.id == playlistID }) else { return }
        guard !playlists[index].tracks.contains(where: { $0.id == track.id }) else { return }
        playlists[index].tracks.append(track)
        persist()
    }

    func remove(_ track: MusicTrack, from playlistID: String) {
        guard let index = playlists.firstIndex(where: { $0.id == playlistID }) else { return }
        playlists[index].tracks.removeAll { $0.id == track.id }
        persist()
    }

    func renamePlaylist(id: String, name: String) {
        guard let index = playlists.firstIndex(where: { $0.id == id }),
              !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
        playlists[index].name = String(name.prefix(65))
        persist()
    }

    func deletePlaylist(id: String) {
        playlists.removeAll { $0.id == id }
        persist()
    }
}
