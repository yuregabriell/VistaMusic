import SwiftUI

private let background = Color(red: 0.055, green: 0.047, blue: 0.075)
private let surface = Color(red: 0.13, green: 0.10, blue: 0.15)
private let pink = Color(red: 1.0, green: 0.31, blue: 0.64)
private let muted = Color(red: 0.69, green: 0.63, blue: 0.70)

private enum MusicTab: String, CaseIterable {
    case home = "Início"
    case search = "Pesquisar"
    case library = "Biblioteca"

    var symbol: String {
        switch self {
        case .home: return "house.fill"
        case .search: return "magnifyingglass"
        case .library: return "square.stack.fill"
        }
    }
}

private struct OpenList: Identifiable {
    let id: String
}

struct MusicRootView: View {
    @StateObject private var catalog = MusicCatalog()
    @StateObject private var library = MusicLibrary()
    @StateObject private var player = MusicPlayer()

    @State private var tab: MusicTab = .home
    @State private var query = ""
    @State private var showFullPlayer = false
    @State private var createPlaylist = false
    @State private var newPlaylistName = ""
    @State private var pickerTrack: MusicTrack?
    @State private var openedList: OpenList?
    @State private var openedAlbum: MusicAlbum?
    @State private var message: String?

    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    switch tab {
                    case .home: homeContent
                    case .search: searchContent
                    case .library: libraryContent
                    }
                }
                .padding(.bottom, 28)
            }
            .scrollDismissesKeyboard(.interactively)

            if player.current != nil {
                miniPlayer
            }
            tabBar
        }
        .background(background.ignoresSafeArea())
        .tint(pink)
        .task { await catalog.refresh() }
        .sheet(isPresented: $showFullPlayer) {
            PlayerDetailView(player: player, library: library)
                .presentationDragIndicator(.visible)
        }
        .sheet(item: $pickerTrack) { track in
            AddToPlaylistView(track: track, library: library) {
                pickerTrack = nil
            }
            .presentationDetents([.medium, .large])
        }
        .sheet(item: $openedList) { selection in
            PlaylistDetailView(identifier: selection.id, library: library,
                               play: { song, list in play(song, from: list) })
        }
        .sheet(item: $openedAlbum) { album in
            AlbumDetailView(album: album,
                            play: { song, list in play(song, from: list) },
                            like: { library.toggleLike($0) },
                            isLiked: { library.isLiked($0) },
                            add: { pickerTrack = $0 })
        }
        .alert("Nova playlist", isPresented: $createPlaylist) {
            TextField("Nome da playlist", text: $newPlaylistName)
            Button("Criar") {
                library.createPlaylist(name: newPlaylistName)
                newPlaylistName = ""
            }
            Button("Cancelar", role: .cancel) { newPlaylistName = "" }
        } message: {
            Text("As playlists são privadas e guardadas neste iPhone.")
        }
        .alert("VistaMusic", isPresented: Binding(
            get: { message != nil },
            set: { if !$0 { message = nil } }
        )) {
            Button("OK", role: .cancel) { message = nil }
        } message: {
            Text(message ?? "")
        }
    }

    private var homeContent: some View {
        VStack(alignment: .leading, spacing: 25) {
            HStack {
                VStack(alignment: .leading, spacing: 3) {
                    Text("VistaMusic").font(.system(size: 31, weight: .heavy, design: .rounded))
                    Text("A tua música, do teu jeito").font(.subheadline).foregroundStyle(muted)
                }
                Spacer()
                Image(systemName: "waveform").font(.title).foregroundStyle(pink)
            }
            .padding(.horizontal, 20)
            .padding(.top, 22)

            if let featured = library.recommended(catalog.trending).first ?? catalog.trending.first {
                Button { play(featured, from: catalog.trending) } label: {
                    ZStack(alignment: .bottomLeading) {
                        LinearGradient(colors: [Color(red: 0.48, green: 0.15, blue: 0.36),
                                                Color(red: 0.22, green: 0.12, blue: 0.29), surface],
                                       startPoint: .topLeading, endPoint: .bottomTrailing)
                        HStack {
                            VStack(alignment: .leading, spacing: 12) {
                                Text("FEITO PARA TI").font(.caption.bold()).tracking(2)
                                    .foregroundStyle(Color.white.opacity(0.75))
                                Text(featured.title).font(.system(size: 28, weight: .bold))
                                    .lineLimit(2)
                                Text(featured.artist).font(.subheadline)
                                    .foregroundStyle(Color.white.opacity(0.75))
                                Label("Ouvir agora", systemImage: "play.fill")
                                    .font(.subheadline.bold())
                                    .padding(.horizontal, 14).padding(.vertical, 10)
                                    .background(pink, in: Capsule())
                            }
                            Spacer()
                            TrackArtwork(url: featured.artworkURL, size: 115)
                        }
                        .padding(20)
                    }
                    .frame(minHeight: 215)
                    .clipShape(RoundedRectangle(cornerRadius: 20))
                }
                .buttonStyle(.plain)
                .padding(.horizontal, 20)
            }

            if catalog.loading {
                ProgressView("A carregar música...").padding(.horizontal, 20)
            }
            if let error = catalog.errorMessage {
                Text(error).foregroundStyle(muted).padding(.horizontal, 20)
            }

            if !library.recent.isEmpty {
                trackSection("Ouvidas recentemente", tracks: Array(library.recent.prefix(15)))
            }

            let personal = library.recommended(catalog.trending)
            if !personal.isEmpty {
                trackSection("Sons para descobrir", tracks: Array(personal.prefix(16)))
            }

            if !catalog.portugalChart.isEmpty {
                trackSection("Em alta · Apple Music Portugal", tracks: Array(catalog.portugalChart.prefix(20)))
            }
            if !catalog.usaChart.isEmpty {
                trackSection("Em alta · Apple Music EUA", tracks: Array(catalog.usaChart.prefix(20)))
            }
            if !catalog.trending.isEmpty {
                trackSection("Faixas completas · Audius", tracks: Array(catalog.trending.prefix(22)))
            }

            Text("Os rankings da Apple são metadados públicos. A reprodução pode usar prévias de 30 segundos; as faixas Audius são reproduzidas integralmente quando disponibilizadas pela fonte.")
                .font(.caption2).foregroundStyle(muted)
                .padding(.horizontal, 20)

            Button {
                Task { await catalog.refresh() }
            } label: {
                Label("Atualizar catálogo", systemImage: "arrow.clockwise")
            }
            .padding(.horizontal, 20)
        }
    }

    private var searchContent: some View {
        VStack(alignment: .leading, spacing: 18) {
            Text("Pesquisar").font(.system(size: 31, weight: .bold))
                .padding(.horizontal, 20).padding(.top, 22)
            HStack(spacing: 9) {
                Image(systemName: "magnifyingglass").foregroundStyle(muted)
                TextField("Músicas, álbuns e artistas", text: $query)
                    .textInputAutocapitalization(.never)
                    .submitLabel(.search)
                    .onSubmit { Task { await catalog.search(query) } }
                if !query.isEmpty {
                    Button {
                        query = ""
                        Task { await catalog.search("") }
                    } label: { Image(systemName: "xmark.circle.fill").foregroundStyle(muted) }
                }
            }
            .padding(14)
            .background(surface, in: RoundedRectangle(cornerRadius: 14))
            .padding(.horizontal, 20)

            if query.isEmpty {
                Text("Descobre algo novo")
                    .font(.title2.bold()).padding(.horizontal, 20)
                LazyVGrid(columns: [.init(.flexible()), .init(.flexible())], spacing: 12) {
                    genreButton("Pop", symbol: "sparkles")
                    genreButton("Rock", symbol: "guitars.fill")
                    genreButton("Hip-Hop", symbol: "music.mic")
                    genreButton("Eletrónica", symbol: "waveform")
                    genreButton("Indie", symbol: "moon.stars.fill")
                    genreButton("Jazz", symbol: "pianokeys")
                }
                .padding(.horizontal, 20)
            } else {
                if catalog.searching {
                    ProgressView("A pesquisar...").padding(.horizontal, 20)
                }
                if !catalog.searchAlbums.isEmpty {
                    albumSection(catalog.searchAlbums)
                }
                if !catalog.searchSongs.isEmpty {
                    VStack(alignment: .leading, spacing: 10) {
                        Text("Músicas").font(.title2.bold())
                        ForEach(catalog.searchSongs) { track in
                            TrackRow(track: track, liked: library.isLiked(track),
                                     play: { play(track, from: catalog.searchSongs) },
                                     like: { library.toggleLike(track) },
                                     add: { pickerTrack = track })
                        }
                    }.padding(.horizontal, 20)
                }
                if !catalog.searching && catalog.searchSongs.isEmpty && catalog.searchAlbums.isEmpty {
                    Text("Não foram encontrados resultados. Experimenta outro nome.")
                        .foregroundStyle(muted).padding(.horizontal, 20)
                }
            }
        }
        .task(id: query) {
            guard !query.isEmpty else { return }
            try? await Task.sleep(nanoseconds: 500_000_000)
            if !Task.isCancelled { await catalog.search(query) }
        }
    }

    private func genreButton(_ name: String, symbol: String) -> some View {
        Button {
            query = name
            Task { await catalog.search(name) }
        } label: {
            HStack {
                Text(name).font(.headline)
                Spacer()
                Image(systemName: symbol).font(.title3).foregroundStyle(pink)
            }
            .padding(16).frame(height: 83)
            .background(LinearGradient(colors: [surface, Color(red: 0.31, green: 0.13, blue: 0.24)],
                                       startPoint: .topLeading, endPoint: .bottomTrailing),
                        in: RoundedRectangle(cornerRadius: 14))
        }
        .buttonStyle(.plain)
    }

    private var libraryContent: some View {
        VStack(alignment: .leading, spacing: 18) {
            HStack {
                Text("A tua biblioteca").font(.system(size: 30, weight: .bold))
                Spacer()
                Button { createPlaylist = true } label: {
                    Image(systemName: "plus.circle.fill").font(.title2)
                }
                .accessibilityLabel("Criar playlist")
            }
            .padding(.horizontal, 20).padding(.top, 22)

            Button { openedList = OpenList(id: "liked") } label: {
                HStack(spacing: 14) {
                    ZStack {
                        LinearGradient(colors: [pink, .purple], startPoint: .topLeading,
                                       endPoint: .bottomTrailing)
                        Image(systemName: "heart.fill").font(.title).foregroundStyle(.white)
                    }
                    .frame(width: 66, height: 66).clipShape(RoundedRectangle(cornerRadius: 12))
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Músicas curtidas").font(.headline)
                        Text("Playlist · \(library.favorites.count) músicas")
                            .font(.caption).foregroundStyle(muted)
                    }
                    Spacer()
                    Image(systemName: "chevron.right").foregroundStyle(muted)
                }
            }
            .buttonStyle(.plain)
            .padding(.horizontal, 20)

            Text("As tuas playlists").font(.title2.bold()).padding(.horizontal, 20)
            if library.playlists.isEmpty {
                Text("Cria uma playlist para guardar as tuas músicas preferidas.")
                    .foregroundStyle(muted).padding(.horizontal, 20)
            }
            ForEach(library.playlists) { list in
                Button { openedList = OpenList(id: list.id) } label: {
                    HStack(spacing: 13) {
                        ZStack {
                            RoundedRectangle(cornerRadius: 12).fill(surface)
                            Image(systemName: "music.note.list").foregroundStyle(pink)
                        }
                        .frame(width: 65, height: 65)
                        VStack(alignment: .leading, spacing: 3) {
                            Text(list.name).font(.headline).foregroundStyle(.white)
                            Text("\(list.tracks.count) músicas · Privada")
                                .font(.caption).foregroundStyle(muted)
                        }
                        Spacer()
                        Image(systemName: "chevron.right").foregroundStyle(muted)
                    }
                }
                .buttonStyle(.plain)
                .padding(.horizontal, 20)
            }
            if !library.recent.isEmpty {
                trackSection("Histórico recente", tracks: Array(library.recent.prefix(12)))
            }
            Text("Biblioteca guardada localmente neste iPhone. A sincronização Vista ID será integrada numa atualização futura.")
                .font(.caption).foregroundStyle(muted).padding(.horizontal, 20)
        }
    }

    private func trackSection(_ title: String, tracks: [MusicTrack]) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(title).font(.title2.bold()).padding(.horizontal, 20)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(alignment: .top, spacing: 14) {
                    ForEach(tracks) { track in
                        VStack(alignment: .leading, spacing: 7) {
                            Button { play(track, from: tracks) } label: {
                                TrackArtwork(url: track.artworkURL, size: 144)
                            }.buttonStyle(.plain)
                            Text(track.title).font(.subheadline.bold())
                                .lineLimit(1).foregroundStyle(.white)
                            Text(track.artist).font(.caption).lineLimit(1).foregroundStyle(muted)
                        }
                        .frame(width: 144, alignment: .leading)
                        .contextMenu {
                            Button { play(track, from: tracks) } label: {
                                Label("Reproduzir", systemImage: "play.fill")
                            }
                            Button { library.toggleLike(track) } label: {
                                Label(library.isLiked(track) ? "Remover curtida" : "Curtir",
                                      systemImage: "heart")
                            }
                            Button { pickerTrack = track } label: {
                                Label("Adicionar à playlist", systemImage: "plus")
                            }
                        }
                    }
                }
                .padding(.horizontal, 20)
            }
        }
    }

    private func albumSection(_ albums: [MusicAlbum]) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Álbuns").font(.title2.bold()).padding(.horizontal, 20)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(alignment: .top, spacing: 14) {
                    ForEach(albums) { album in
                        Button { openedAlbum = album } label: {
                            VStack(alignment: .leading, spacing: 7) {
                                TrackArtwork(url: album.artworkURL, size: 145)
                                Text(album.title).lineLimit(2).font(.subheadline.bold())
                                Text("\(album.artist) · \(album.year)")
                                    .font(.caption).lineLimit(1).foregroundStyle(muted)
                            }
                            .frame(width: 145, alignment: .leading)
                        }
                        .buttonStyle(.plain)
                    }
                }.padding(.horizontal, 20)
            }
        }
    }

    private var miniPlayer: some View {
        HStack(spacing: 10) {
            if let track = player.current {
                Button { showFullPlayer = true } label: {
                    HStack(spacing: 10) {
                        TrackArtwork(url: track.artworkURL, size: 43)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(track.title).font(.subheadline.bold()).lineLimit(1)
                            Text(track.artist).font(.caption).foregroundStyle(muted).lineLimit(1)
                        }
                    }
                }.buttonStyle(.plain)
                Spacer(minLength: 0)
                Button { library.toggleLike(track) } label: {
                    Image(systemName: library.isLiked(track) ? "heart.fill" : "heart")
                        .foregroundStyle(pink)
                }
                Button { player.toggle() } label: {
                    Image(systemName: player.isPlaying ? "pause.fill" : "play.fill")
                        .font(.title3)
                }
                Button { player.next() } label: { Image(systemName: "forward.end.fill") }
            }
        }
        .padding(.horizontal, 12).padding(.vertical, 10)
        .background(surface, in: RoundedRectangle(cornerRadius: 13))
        .padding(.horizontal, 9)
        .overlay(alignment: .bottom) {
            if player.duration > 0 {
                GeometryReader { proxy in
                    Rectangle().fill(pink)
                        .frame(width: proxy.size.width * min(1, player.elapsed / player.duration), height: 2)
                }
                .frame(height: 2)
                .padding(.horizontal, 18)
            }
        }
    }

    private var tabBar: some View {
        HStack {
            ForEach(MusicTab.allCases, id: \.self) { option in
                Button { tab = option } label: {
                    VStack(spacing: 5) {
                        Image(systemName: option.symbol).font(.system(size: 21))
                        Text(option.rawValue).font(.system(size: 11, weight: .medium))
                    }
                    .foregroundStyle(tab == option ? .white : muted)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 10)
                }
            }
        }
        .background(background)
    }

    private func play(_ track: MusicTrack, from tracks: [MusicTrack]) {
        Task {
            guard let playable = await MusicAPI.playable(track) else {
                message = "Esta música não tem uma fonte de reprodução disponível. Experimenta outra faixa ou uma versão Audius."
                return
            }
            let rest = tracks.filter { $0.id != track.id && $0.audioURL != nil }
            player.play([playable] + rest)
            library.registerPlay(playable)
        }
    }
}

private struct TrackArtwork: View {
    let url: URL?
    let size: CGFloat

    var body: some View {
        AsyncImage(url: url) { image in
            image.resizable().scaledToFill()
        } placeholder: {
            ZStack {
                LinearGradient(colors: [Color(red: 0.40, green: 0.18, blue: 0.35),
                                        surface], startPoint: .topLeading, endPoint: .bottomTrailing)
                Image(systemName: "music.note").foregroundStyle(.white.opacity(0.85))
                    .font(.system(size: max(17, size / 4)))
            }
        }
        .frame(width: size, height: size)
        .clipShape(RoundedRectangle(cornerRadius: min(13, size / 8)))
    }
}

private struct TrackRow: View {
    let track: MusicTrack
    let liked: Bool
    let play: () -> Void
    let like: () -> Void
    let add: () -> Void

    var body: some View {
        HStack(spacing: 11) {
            Button(action: play) {
                HStack(spacing: 11) {
                    TrackArtwork(url: track.artworkURL, size: 52)
                    VStack(alignment: .leading, spacing: 4) {
                        Text(track.title).font(.subheadline.bold()).lineLimit(1)
                        Text(track.artist).font(.caption).foregroundStyle(muted).lineLimit(1)
                        Text(track.source).font(.system(size: 10)).foregroundStyle(pink)
                    }
                }
            }.buttonStyle(.plain)
            Spacer(minLength: 0)
            Button(action: like) {
                Image(systemName: liked ? "heart.fill" : "heart")
                    .foregroundStyle(liked ? pink : muted)
            }
            Button(action: add) { Image(systemName: "ellipsis").foregroundStyle(muted) }
        }
        .padding(.vertical, 4)
    }
}

private struct PlayerDetailView: View {
    @ObservedObject var player: MusicPlayer
    @ObservedObject var library: MusicLibrary
    @Environment(\.dismiss) private var dismiss
    @State private var sliderPosition: Double = 0
    @State private var seeking = false

    var body: some View {
        VStack(spacing: 24) {
            HStack {
                Button { dismiss() } label: { Image(systemName: "chevron.down").font(.title3) }
                Spacer()
                Text("A REPRODUZIR").font(.caption.bold()).tracking(1.7)
                Spacer()
                Image(systemName: "speaker.wave.2").foregroundStyle(pink)
            }
            .padding(.top, 24)
            if let track = player.current {
                Spacer(minLength: 0)
                TrackArtwork(url: track.artworkURL, size: min(UIScreen.main.bounds.width - 75, 325))
                    .shadow(color: pink.opacity(0.17), radius: 35)
                Spacer(minLength: 0)

                HStack(alignment: .center) {
                    VStack(alignment: .leading, spacing: 7) {
                        Text(track.title).font(.system(size: 26, weight: .bold)).lineLimit(2)
                        Text(track.artist).foregroundStyle(muted)
                        Text(track.source).font(.caption2).foregroundStyle(pink)
                    }
                    Spacer()
                    Button { library.toggleLike(track) } label: {
                        Image(systemName: library.isLiked(track) ? "heart.fill" : "heart")
                            .font(.title2).foregroundStyle(pink)
                    }
                }
                Slider(value: Binding(
                    get: { seeking ? sliderPosition : player.elapsed },
                    set: { sliderPosition = $0; seeking = true }
                ), in: 0...max(1, player.duration), onEditingChanged: { editing in
                    if !editing {
                        player.seek(to: sliderPosition)
                        seeking = false
                    }
                })
                HStack {
                    Text(formatTime(player.elapsed))
                    Spacer()
                    Text(formatTime(player.duration))
                }
                .font(.caption.monospacedDigit()).foregroundStyle(muted)

                HStack(spacing: 55) {
                    Button { player.previous() } label: {
                        Image(systemName: "backward.end.fill").font(.system(size: 27))
                    }
                    Button { player.toggle() } label: {
                        Image(systemName: player.isPlaying ? "pause.fill" : "play.fill")
                            .font(.system(size: 30))
                            .frame(width: 78, height: 78)
                            .foregroundStyle(background)
                            .background(.white, in: Circle())
                    }
                    Button { player.next() } label: {
                        Image(systemName: "forward.end.fill").font(.system(size: 27))
                    }
                }
                .padding(.vertical, 12)
                Text("Controla também a reprodução pelo ecrã bloqueado e pelo Centro de Controlo.")
                    .font(.caption).foregroundStyle(muted).multilineTextAlignment(.center)
            }
            Spacer(minLength: 0)
        }
        .foregroundStyle(.white)
        .padding(.horizontal, 25)
        .background(LinearGradient(colors: [Color(red: 0.28, green: 0.13, blue: 0.24), background],
                                   startPoint: .top, endPoint: .bottom).ignoresSafeArea())
    }

    private func formatTime(_ duration: Double) -> String {
        guard duration.isFinite else { return "0:00" }
        let seconds = Int(max(0, duration))
        return "\(seconds / 60):" + String(format: "%02d", seconds % 60)
    }
}

private struct AddToPlaylistView: View {
    let track: MusicTrack
    @ObservedObject var library: MusicLibrary
    let dismiss: () -> Void

    var body: some View {
        NavigationStack {
            List {
                Section("Adicionar \(track.title)") {
                    ForEach(library.playlists) { playlist in
                        Button {
                            library.add(track, to: playlist.id)
                            dismiss()
                        } label: {
                            HStack {
                                Label(playlist.name, systemImage: "music.note.list")
                                Spacer()
                                if playlist.tracks.contains(where: { $0.id == track.id }) {
                                    Image(systemName: "checkmark").foregroundStyle(pink)
                                }
                            }
                        }
                    }
                    if library.playlists.isEmpty {
                        Text("Cria uma playlist na Biblioteca antes de adicionar músicas.")
                            .foregroundStyle(muted)
                    }
                }
            }
            .navigationTitle("Adicionar à playlist")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Fechar", action: dismiss)
                }
            }
        }
        .preferredColorScheme(.dark)
    }
}

private struct PlaylistDetailView: View {
    let identifier: String
    @ObservedObject var library: MusicLibrary
    let play: (MusicTrack, [MusicTrack]) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var showRename = false
    @State private var renamed = ""

    private var playlist: MusicPlaylist? {
        library.playlists.first(where: { $0.id == identifier })
    }
    private var tracks: [MusicTrack] {
        identifier == "liked" ? library.favorites : (playlist?.tracks ?? [])
    }
    private var title: String {
        identifier == "liked" ? "Músicas curtidas" : (playlist?.name ?? "Playlist")
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    ZStack {
                        LinearGradient(colors: [pink, .purple], startPoint: .topLeading,
                                       endPoint: .bottomTrailing)
                        Image(systemName: identifier == "liked" ? "heart.fill" : "music.note.list")
                            .font(.system(size: 70))
                    }
                    .frame(width: 185, height: 185).clipShape(RoundedRectangle(cornerRadius: 18))
                    .frame(maxWidth: .infinity)

                    Text(title).font(.system(size: 29, weight: .bold))
                    Text("\(tracks.count) músicas · Biblioteca local")
                        .font(.subheadline).foregroundStyle(muted)

                    if let first = tracks.first {
                        Button {
                            play(first, tracks)
                        } label: {
                            Label("Reproduzir", systemImage: "play.fill")
                                .font(.headline).padding(.vertical, 11).padding(.horizontal, 20)
                                .background(pink, in: Capsule())
                        }
                    }
                    ForEach(tracks) { track in
                        HStack {
                            Button { play(track, tracks) } label: {
                                HStack(spacing: 12) {
                                    TrackArtwork(url: track.artworkURL, size: 50)
                                    VStack(alignment: .leading, spacing: 3) {
                                        Text(track.title).lineLimit(1).font(.subheadline.bold())
                                        Text(track.artist).foregroundStyle(muted).font(.caption)
                                    }
                                }
                            }.buttonStyle(.plain)
                            Spacer()
                            if identifier == "liked" {
                                Button { library.toggleLike(track) } label: {
                                    Image(systemName: "heart.slash")
                                }
                            } else {
                                Button { library.remove(track, from: identifier) } label: {
                                    Image(systemName: "minus.circle")
                                }
                            }
                        }
                        .padding(.vertical, 5)
                    }
                }
                .padding(20)
            }
            .background(background)
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Fechar") { dismiss() }
                }
                if identifier != "liked" {
                    ToolbarItem(placement: .topBarTrailing) {
                        Menu {
                            Button("Mudar nome") {
                                renamed = title
                                showRename = true
                            }
                            Button("Eliminar playlist", role: .destructive) {
                                library.deletePlaylist(id: identifier)
                                dismiss()
                            }
                        } label: { Image(systemName: "ellipsis.circle") }
                    }
                }
            }
            .alert("Editar playlist", isPresented: $showRename) {
                TextField("Nome", text: $renamed)
                Button("Guardar") { library.renamePlaylist(id: identifier, name: renamed) }
                Button("Cancelar", role: .cancel) {}
            }
        }
        .preferredColorScheme(.dark)
    }
}

private struct AlbumDetailView: View {
    let album: MusicAlbum
    let play: (MusicTrack, [MusicTrack]) -> Void
    let like: (MusicTrack) -> Void
    let isLiked: (MusicTrack) -> Bool
    let add: (MusicTrack) -> Void
    @State private var tracks: [MusicTrack] = []
    @State private var loading = true

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 15) {
                    TrackArtwork(url: album.artworkURL, size: 190)
                        .frame(maxWidth: .infinity)
                    Text(album.title).font(.system(size: 28, weight: .bold))
                    Text("\(album.artist) · \(album.year)")
                        .foregroundStyle(muted)
                    Text("As faixas deste álbum incluem apenas prévias disponibilizadas pelo iTunes.")
                        .font(.caption).foregroundStyle(muted)
                    if loading { ProgressView("A carregar faixas...") }
                    ForEach(tracks) { track in
                        TrackRow(track: track, liked: isLiked(track),
                                 play: { play(track, tracks) },
                                 like: { like(track) }, add: { add(track) })
                    }
                }
                .padding(20)
            }
            .background(background)
            .navigationTitle("Álbum")
            .navigationBarTitleDisplayMode(.inline)
        }
        .preferredColorScheme(.dark)
        .task(id: album.id) {
            loading = true
            tracks = (try? await MusicAPI.albumTracks(id: album.id)) ?? []
            loading = false
        }
    }
}
