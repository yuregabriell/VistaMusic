import Foundation

struct MusicTrack: Codable, Hashable, Identifiable {
    let id: String
    let title: String
    let artist: String
    let art: String
    let audio: String
    let source: String

    var audioURL: URL? {
        guard let url = URL(string: audio), url.scheme?.lowercased() == "https" else {
            return nil
        }
        return url
    }

    var artworkURL: URL? {
        guard let url = URL(string: art), url.scheme?.lowercased() == "https" else {
            return nil
        }
        return url
    }

    var hasFullTrack: Bool { source == "Audius" }
}

struct MusicPlaylist: Codable, Identifiable, Hashable {
    let id: String
    var name: String
    var description: String
    var tracks: [MusicTrack]

    init(name: String, description: String = "") {
        id = UUID().uuidString
        self.name = name
        self.description = description
        tracks = []
    }
}

struct MusicAlbum: Hashable, Identifiable {
    let id: Int
    let title: String
    let artist: String
    let year: String
    let art: String
    var artworkURL: URL? { URL(string: art) }
}

enum MusicPalette {
    static let background = "#100E16"
    static let surface = "#201924"
    static let accent = "#FF4FA3"
    static let secondary = "#AD91AC"
}
