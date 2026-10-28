import SwiftUI
import Foundation
import Combine

private struct LyricLine: Identifiable {
    let id: Int
    let time: Double
    let text: String
}

private struct LyricResponse: Decodable {
    let plainLyrics: String?
    let syncedLyrics: String?
}

@MainActor
private final class LyricState: ObservableObject {
    @Published var lines: [LyricLine] = []
    @Published var plain = ""
    @Published var loading = false

    func load(_ track: MusicTrack?) async {
        lines = []
        plain = ""
        guard let track else { return }
        loading = true
        defer { loading = false }
        guard var parts = URLComponents(string: "https://lrclib.net/api/get") else { return }
        parts.queryItems = [
            .init(name: "artist_name", value: track.artist),
            .init(name: "track_name", value: track.title)
        ]
        guard let url = parts.url else { return }
        var request = URLRequest(url: url)
        request.setValue("VistaMusic-iOS/0.2 (lyrics; public client)", forHTTPHeaderField: "User-Agent")
        request.timeoutInterval = 13
        guard let (data, response) = try? await URLSession.shared.data(for: request),
              (response as? HTTPURLResponse)?.statusCode == 200,
              let result = try? JSONDecoder().decode(LyricResponse.self, from: data) else { return }
        plain = result.plainLyrics ?? ""
        guard let source = result.syncedLyrics else { return }
        lines = source.components(separatedBy: .newlines).enumerated().compactMap { index, line in
            guard line.first == "[", let bracket = line.firstIndex(of: "]") else { return nil }
            let rawTime = line[line.index(after: line.startIndex)..<bracket]
            let parts = rawTime.split(separator: ":")
            guard parts.count == 2, let minute = Double(parts[0]),
                  let seconds = Double(parts[1]) else { return nil }
            let lyric = line[line.index(after: bracket)...].trimmingCharacters(in: .whitespaces)
            guard !lyric.isEmpty else { return nil }
            return LyricLine(id: index, time: minute * 60 + seconds, text: lyric)
        }
    }
}

struct MusicLyricsView: View {
    @ObservedObject var player: MusicPlayer
    @StateObject private var lyrics = LyricState()
    @Environment(\.dismiss) private var dismiss

    private var currentIndex: Int {
        lyrics.lines.lastIndex(where: { $0.time <= player.elapsed }) ?? -1
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 22) {
                    if let song = player.current {
                        Text(song.title).font(.title2.bold())
                        Text(song.artist).foregroundStyle(.secondary)
                    }
                    if lyrics.loading { ProgressView("A procurar letras...") }
                    if !lyrics.lines.isEmpty {
                        ForEach(Array(lyrics.lines.enumerated()), id: \.element.id) { index, line in
                            Button {
                                player.seek(to: line.time)
                            } label: {
                                Text(line.text)
                                    .font(.system(size: 25, weight: index == currentIndex ? .bold : .medium))
                                    .foregroundStyle(index == currentIndex ? .white : Color.white.opacity(0.43))
                                    .frame(maxWidth: .infinity, alignment: .leading)
                            }
                            .buttonStyle(.plain)
                        }
                    } else if !lyrics.plain.isEmpty {
                        Text(lyrics.plain).font(.title3).lineSpacing(12)
                            .foregroundStyle(.white)
                    } else if !lyrics.loading {
                        Text("Ainda não encontramos a letra desta faixa no LRCLIB.")
                            .foregroundStyle(.secondary)
                    }
                    Text("Letras fornecidas pela comunidade LRCLIB, quando disponíveis.")
                        .font(.caption).foregroundStyle(.secondary)
                }
                .padding(22)
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .background(Color(red: 0.16, green: 0.08, blue: 0.15))
            .navigationTitle("Letras")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Fechar") { dismiss() }
                }
            }
        }
        .preferredColorScheme(.dark)
        .task(id: player.current?.id) {
            await lyrics.load(player.current)
        }
    }
}
