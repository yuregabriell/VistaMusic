package com.vistamusic.app

import android.os.Bundle
import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import androidx.media3.common.MediaMetadata
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray

class MainActivity : ComponentActivity() {
    private var openPlayerRequest by mutableStateOf(false)
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("OPEN_CURRENT_PLAYER", false))
            openPlayerRequest = true
    }
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* If denied, native controls may still appear in Android System UI. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openPlayerRequest = intent?.getBooleanExtra("OPEN_CURRENT_PLAYER", false) == true
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        window.statusBarColor = android.graphics.Color.rgb(9, 11, 14)
        window.navigationBarColor = android.graphics.Color.rgb(9, 11, 14)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = VmAccent, onPrimary = VmBackground,
                    background = VmBackground, surface = VmCard,
                    onSurface = VmWhite, onBackground = VmWhite)
            ) { VistaMusicApp(openPlayerRequest) { openPlayerRequest = false } }
        }
    }
}

@Composable
private fun VistaMusicApp(
    openPlayerRequested: Boolean,
    onPlayerRequestHandled: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("vistamusic", android.content.Context.MODE_PRIVATE) }
    val music = remember { MusicApi() }
    val catalog = remember { LibraryCatalog() }
    val taste = remember { LocalTaste(prefs) }
    val playlistStore = remember { VmPlaylistStore(context) }
    var playlists by remember { mutableStateOf(playlistStore.load()) }
    var selectedPlaylistId by remember { mutableStateOf<String?>(null) }
    var showCreatePlaylist by remember { mutableStateOf(false) }
    var editingPlaylistId by remember { mutableStateOf<String?>(null) }
    var deletingPlaylistId by remember { mutableStateOf<String?>(null) }
    var addingSong by remember { mutableStateOf<Song?>(null) }
    var songForNewPlaylist by remember { mutableStateOf<Song?>(null) }
    var playlistAddTarget by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var mediaController by remember { mutableStateOf<MediaController?>(null) }
    DisposableEffect(context) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            runCatching { future.get() }.onSuccess { mediaController = it }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            mediaController = null
            MediaController.releaseFuture(future)
        }
    }
    val player = mediaController
    var pendingCommand by remember { mutableStateOf<Pair<String, Bundle>?>(null) }
    fun sendPlaybackCommand(action: String, args: Bundle = Bundle.EMPTY) {
        val connected = player
        if (connected != null) {
            connected.sendCustomCommand(SessionCommand(action, Bundle.EMPTY), args)
        } else if (action == PlaybackService.PLAY_QUEUE) {
            pendingCommand = action to Bundle(args)
        }
    }
    LaunchedEffect(player, pendingCommand) {
        val connected = player
        val pending = pendingCommand
        if (connected != null && pending != null) {
            connected.sendCustomCommand(
                SessionCommand(pending.first, Bundle.EMPTY), pending.second)
            pendingCommand = null
        }
    }

    var tab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<Song>>(emptyList()) }
    var suggestions by remember { mutableStateOf<ArtistInfo?>(null) }
    var featured by remember { mutableStateOf<List<Song>>(emptyList()) }
    var globalHits by remember { mutableStateOf<List<Song>>(emptyList()) }
    var albumResults by remember { mutableStateOf<List<AlbumInfo>>(emptyList()) }
    var searchArtistPhoto by remember { mutableStateOf("") }
    var genrePage by remember { mutableStateOf<GenreCategory?>(null) }
    var genreSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var genreAlbums by remember { mutableStateOf<List<AlbumInfo>>(emptyList()) }
    var genrePhotos by remember { mutableStateOf<Map<String,String>>(emptyMap()) }
    var genreLoading by remember { mutableStateOf(false) }
    var ytTrendingPt by remember { mutableStateOf<List<Song>>(emptyList()) }
    var ytTrendingUs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var artistTopFromYoutube by remember { mutableStateOf(false) }
    var youtubeRadio by remember { mutableStateOf<List<Song>>(emptyList()) }
    var lastHeroId by remember { mutableStateOf("") }
    var historyVersion by remember { mutableIntStateOf(0) }
    var lastLoggedId by remember { mutableStateOf("") }
    var recommending by remember { mutableStateOf(false) }
    var autoDj by remember { mutableStateOf(prefs.getBoolean("auto_dj", true)) }
    var bufferedMs by remember { mutableLongStateOf(0L) }
    var independent by remember { mutableStateOf<List<Song>>(emptyList()) }
    var nowPlaying by remember { mutableStateOf<Song?>(null) }
    var queue by remember { mutableStateOf<List<Song>>(emptyList()) }
    var queueIndex by remember { mutableIntStateOf(0) }
    var isPlaying by remember { mutableStateOf(false) }
    var loadingAudio by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var playbackError by remember { mutableStateOf("") }
    var playerOpen by remember { mutableStateOf(false) }
    var lyricsOpen by remember { mutableStateOf(false) }
    var shuffle by remember { mutableStateOf(false) }
    var repeat by remember { mutableStateOf(false) }

    var artistPage by remember { mutableStateOf<String?>(null) }
    var artistInfo by remember { mutableStateOf<ArtistInfo?>(null) }
    var artistBio by remember { mutableStateOf<ArtistBio?>(null) }
    var artistSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var artistTopSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var artistAllOpen by remember { mutableStateOf(false) }
    var artistAlbums by remember { mutableStateOf<List<AlbumInfo>>(emptyList()) }
    var artistLoading by remember { mutableStateOf(false) }

    var albumPage by remember { mutableStateOf<AlbumInfo?>(null) }
    var albumSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var albumLoading by remember { mutableStateOf(false) }
    var songPage by remember { mutableStateOf<Song?>(null) }
    var songDetails by remember { mutableStateOf<SongInfo?>(null) }
    var songLyrics by remember { mutableStateOf<LyricSheet?>(null) }
    var playerLyrics by remember { mutableStateOf<LyricSheet?>(null) }

    var jamendoId by remember { mutableStateOf(prefs.getString("jamendo", "").orEmpty()) }
    var includePreviews by remember { mutableStateOf(prefs.getBoolean("previews", false)) }

    val favorites = remember {
        mutableStateListOf<Song>().apply {
            val a = runCatching { JSONArray(prefs.getString("favorites", "[]")) }
                .getOrDefault(JSONArray())
            for (i in 0 until a.length()) {
                a.optJSONObject(i)?.let { j -> runCatching { Song.fromJson(j) }.getOrNull()?.let { add(it) } }
            }
        }
    }
    val followed = remember {
        mutableStateListOf<String>().apply {
            val a = runCatching { JSONArray(prefs.getString("followed", "[]")) }
                .getOrDefault(JSONArray())
            for (i in 0 until a.length()) add(a.optString(i))
        }
    }
    fun updatePlaylists(change: (List<VmPlaylist>) -> List<VmPlaylist>) {
        val updated = change(playlists)
        playlists = updated
        playlistStore.save(updated)
    }
    fun addSong(playlistId: String, song: Song) {
        updatePlaylists { all ->
            all.map { pl ->
                if (pl.id != playlistId || pl.tracks.any { it.id == song.id }) pl
                else pl.copy(tracks = pl.tracks + song)
            }
        }
    }
    fun askAddToPlaylist(song: Song) {
        val target = playlistAddTarget
        if (target != null && playlists.any { it.id == target }) {
            addSong(target, song)
        } else addingSong = song
    }
    fun isFavorite(song: Song) = favorites.any { it.id == song.id }
    fun toggleFavorite(song: Song) {
        if (isFavorite(song)) favorites.removeAll { it.id == song.id } else favorites.add(0, song)
        val a = JSONArray(); favorites.forEach { a.put(it.toJson()) }
        prefs.edit().putString("favorites", a.toString()).apply()
    }
    fun toggleFollow(name: String) {
        if (followed.contains(name)) followed.remove(name) else followed.add(name)
        val a = JSONArray(); followed.forEach { a.put(it) }
        prefs.edit().putString("followed", a.toString()).apply()
    }
    fun openArtist(name: String) {
        artistPage = name
        artistInfo = null; artistBio = null; artistSongs = emptyList(); artistAlbums = emptyList()
        artistTopSongs = emptyList(); artistAllOpen = false
        albumPage = null; songPage = null
        playerOpen = false; lyricsOpen = false
    }
    fun openSong(song: Song) {
        songPage = song
        songDetails = null
        songLyrics = null
        playerOpen = false; lyricsOpen = false
    }
    fun openAlbum(album: AlbumInfo) {
        albumPage = album
        albumSongs = emptyList()
        songPage = null
        playerOpen = false; lyricsOpen = false
    }
    fun playSongs(list: List<Song>, index: Int) {
        if (index !in list.indices) return
        queue = list
        queueIndex = index
        nowPlaying = list[index]
        lastLoggedId = ""
        loadingAudio = true
        playbackError = ""
        val items = JSONArray()
        list.forEach { items.put(it.toJson()) }
        sendPlaybackCommand(PlaybackService.PLAY_QUEUE, Bundle().apply {
            putString(PlaybackService.QUEUE_JSON, items.toString())
            putInt(PlaybackService.QUEUE_INDEX, index)
        })
    }
    fun nextSong() {
        sendPlaybackCommand(PlaybackService.NEXT)
    }
    fun previousSong() {
        sendPlaybackCommand(PlaybackService.PREVIOUS)
    }
    fun togglePlayback() {
        val active = player ?: return
        if (active.mediaItemCount == 0) {
            if (queue.isNotEmpty()) playSongs(queue, queueIndex)
        } else if (active.isPlaying) active.pause() else active.play()
    }
    fun goBack() {
        when {
            selectedPlaylistId != null -> selectedPlaylistId = null
            lyricsOpen -> lyricsOpen = false
            playerOpen -> playerOpen = false
            artistAllOpen -> artistAllOpen = false
            genrePage != null -> genrePage = null
            songPage != null -> songPage = null
            albumPage != null -> albumPage = null
            artistPage != null -> artistPage = null
            tab != 0 -> tab = 0
        }
    }
    BackHandler(enabled = selectedPlaylistId != null || genrePage != null || artistAllOpen || lyricsOpen || playerOpen || songPage != null ||
        albumPage != null || artistPage != null || tab != 0) { goBack() }

    DisposableEffect(player) {
        val active = player
        if (active == null) {
            onDispose { }
        } else {
            fun refreshSong() {
                val item = active.currentMediaItem
                val raw = item?.mediaMetadata?.extras
                    ?.getString(PlaybackService.SONG_JSON).orEmpty()
                val current = runCatching { Song.fromJson(org.json.JSONObject(raw)) }.getOrNull()
                if (current != null) {
                    nowPlaying = current
                    val matched = queue.indexOfFirst { it.id == current.id }
                    if (matched >= 0) queueIndex = matched
                    loadingAudio = false
                }
            }
            val listener = object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
                override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                    refreshSong()
                }
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) loadingAudio = false
                    if (state == Player.STATE_ENDED) loadingAudio = false
                }
                override fun onPlayerError(
                    error: androidx.media3.common.PlaybackException
                ) {
                    playbackError = "O áudio desta faixa não está disponível."
                    loadingAudio = false
                }
            }
            active.addListener(listener)
            isPlaying = active.isPlaying
            refreshSong()
            onDispose { active.removeListener(listener) }
        }
    }
    LaunchedEffect(player) {
        while (true) {
            delay(360)
            val active = player
            if (active != null) {
                positionMs = active.currentPosition.coerceAtLeast(0L)
                durationMs = active.duration.takeIf { it > 0L } ?: 0L
                bufferedMs = active.bufferedPosition.coerceAtLeast(0L)
            }
        }
    }
    LaunchedEffect(Unit) {
        coroutineScope {
            val u = async { runCatching { NativeMusic.trending("US") }.getOrDefault(emptyList()) }
            val au = async { runCatching { music.popular("us") }.getOrDefault(emptyList()) }
            ytTrendingUs = u.await()
            globalHits = au.await()
        }
    }
    LaunchedEffect(nowPlaying?.id, isPlaying) {
        val track = nowPlaying
        if (isPlaying && track != null && lastLoggedId != track.id) {
            lastLoggedId = track.id
            taste.add(track, "play")
            historyVersion++
        }
    }
    val forYou = remember(historyVersion, ytTrendingUs, youtubeRadio,
        globalHits, favorites.size) {
        taste.rankedForYou(
            (youtubeRadio + ytTrendingUs.ifEmpty { globalHits }).distinctBy { it.id },
            favorites.toList())
    }
    val recentIds = remember(historyVersion) {
        taste.recentlyListened().take(12).map { it.id }.toSet()
    }
    val heroSong = forYou.firstOrNull {
        it.id != lastHeroId && it.id !in recentIds && it.id != nowPlaying?.id
    } ?: forYou.firstOrNull { it.id != lastHeroId } ?: forYou.firstOrNull()
    val freshYoutube = remember(ytTrendingUs, historyVersion) {
        ytTrendingUs.drop(8).ifEmpty { ytTrendingUs }
            .filterNot { it.id in recentIds }
            .distinctBy { it.id }.take(24)
    }
    LaunchedEffect(historyVersion) {
        val seed = taste.recentlyListened().firstOrNull() ?: favorites.firstOrNull()
        if (seed != null) {
            coroutineScope {
                val related = async {
                    if (seed.audio.startsWith("youtube:"))
                        runCatching {
                            NativeMusic.relatedTo(seed.audio.removePrefix("youtube:"))
                        }.getOrDefault(emptyList())
                    else emptyList()
                }
                val artistSongs = async {
                    runCatching { NativeMusic.search(seed.artist + " official audio") }
                        .getOrDefault(emptyList())
                }
                youtubeRadio = (related.await() + artistSongs.await())
                    .filter { it.id != seed.id }.distinctBy { it.id }.take(28)
            }
        }
    }
    LaunchedEffect(genrePage?.title) {
        val genre = genrePage ?: return@LaunchedEffect
        genreLoading = true
        genreSongs = emptyList(); genreAlbums = emptyList(); genrePhotos = emptyMap()
        val artists = Genres.artists(genre.title)
        coroutineScope {
            val yt = async { runCatching {
                NativeMusic.search(genre.title + " music official audio")
            }.getOrDefault(emptyList()) }
            val lists = artists.map { name -> async {
                runCatching { catalog.artistSongs(name, 0L) }
                    .getOrDefault(emptyList()).take(12)
            } }
            val albums = artists.take(3).map { name -> async {
                runCatching { catalog.artistAlbums(name, 0L) }
                    .getOrDefault(emptyList()).take(8)
            } }
            val photos = artists.map { name -> async {
                name to runCatching { ArtistBiography.fetch(name)?.photo }.getOrNull().orEmpty()
            } }
            val batches = lists.map { it.await() }
            val mixed = mutableListOf<Song>()
            for (i in 0 until 12) for (batch in batches) if (i < batch.size) mixed.add(batch[i])
            genreSongs = (yt.await().take(12) + mixed)
                .distinctBy { it.title.lowercase() + "|" + it.artist.lowercase() }
                .take(60)
            genreAlbums = albums.flatMap { it.await() }.distinctBy { it.id }
            genrePhotos = photos.map { it.await() }.toMap()
        }
        genreLoading = false
    }
    LaunchedEffect(query, tab, jamendoId, includePreviews) {
        if (tab != 1 || query.isBlank()) {
            if (query.isBlank()) {
                searchResults = emptyList(); suggestions = null
                albumResults = emptyList(); searchArtistPhoto = ""
            }
            return@LaunchedEffect
        }
        delay(440)
        coroutineScope {
            val mus = async { runCatching {
                music.search(query, jamendoId, includePreviews, "")
            }.getOrDefault(emptyList()) }
            val albums = async { runCatching { catalog.searchAlbums(query) }.getOrDefault(emptyList()) }
            val artist = async {
                if (query.length >= 3) runCatching { catalog.artist(query) }.getOrNull()
                else null
            }
            searchResults = mus.await(); albumResults = albums.await()
            suggestions = artist.await()
        }
    }
    LaunchedEffect(suggestions?.name) {
        searchArtistPhoto = suggestions?.name?.let {
            runCatching { ArtistBiography.fetch(it)?.photo }.getOrNull()
        }.orEmpty()
    }
    LaunchedEffect(artistPage) {
        val name = artistPage ?: return@LaunchedEffect
        artistLoading = true
        artistInfo = runCatching { catalog.artist(name) }.getOrNull()
        artistBio = runCatching { ArtistBiography.fetch(name) }.getOrNull()
        artistSongs = runCatching { catalog.artistSongs(name, artistInfo?.id ?: 0L) }
            .getOrDefault(emptyList())
        artistAlbums = runCatching { catalog.artistAlbums(name, artistInfo?.id ?: 0L) }
            .getOrDefault(emptyList())
        val rankedYoutube = runCatching { NativeMusic.popularArtist(name) }
            .getOrDefault(emptyList()).filter { it.views > 0 }
        artistTopFromYoutube = rankedYoutube.size >= 5
        val verified = rankedYoutube.map { it.song }
        val audioDb = runCatching { catalog.topArtistSongs(name, artistSongs) }
            .getOrDefault(emptyList())
        artistTopSongs = (verified + audioDb + artistSongs)
            .distinctBy { LyricsApi.cleanTitle(it.title, name).lowercase().trim() }
            .take(10)
        artistLoading = false
    }
    LaunchedEffect(albumPage?.id) {
        val album = albumPage ?: return@LaunchedEffect
        albumLoading = true
        albumSongs = runCatching { catalog.albumSongs(album) }.getOrDefault(emptyList())
        albumLoading = false
    }
    LaunchedEffect(songPage?.id) {
        val song = songPage ?: return@LaunchedEffect
        songDetails = runCatching { catalog.songInfo(song) }.getOrNull()
        songLyrics = runCatching { LyricsApi.find(song.title, song.artist) }.getOrNull()
    }
    LaunchedEffect(openPlayerRequested, nowPlaying?.id, player) {
        if (openPlayerRequested && nowPlaying != null && player != null) {
            playerOpen = true
            lyricsOpen = false
            selectedPlaylistId = null
            onPlayerRequestHandled()
        }
    }
    LaunchedEffect(nowPlaying?.id) {
        val song = nowPlaying ?: return@LaunchedEffect
        playerLyrics = null
        playerLyrics = runCatching { LyricsApi.find(song.title, song.artist) }.getOrNull()
    }

    Column(Modifier.fillMaxSize().background(VmBackground)
        .statusBarsPadding().navigationBarsPadding()) {
        Box(Modifier.weight(1f)) {
            when {
                lyricsOpen && nowPlaying != null -> VmLyricsPage(
                    song = nowPlaying!!, sheet = playerLyrics, position = positionMs,
                    playing = isPlaying, onBack = { lyricsOpen = false }, onPlayPause = ::togglePlayback)
                playerOpen && nowPlaying != null -> VmPlayerPage(
                    song = nowPlaying!!, position = positionMs, duration = durationMs,
                    playing = isPlaying, loading = loadingAudio, sheet = playerLyrics,
                    favorite = isFavorite(nowPlaying!!), repeat = repeat, shuffle = shuffle,
                    onBack = { playerOpen = false },
                    onPlayPause = ::togglePlayback, onPrev = ::previousSong,
                    onNext = ::nextSong, onSeek = { player?.seekTo(it) },
                    onLyrics = { lyricsOpen = true },
                    onArtist = { openArtist(nowPlaying!!.artist) },
                    onSong = { openSong(nowPlaying!!) },
                    onFavorite = { toggleFavorite(nowPlaying!!) },
                    onShuffle = { shuffle = !shuffle },
                    onRepeat = {
                        repeat = !repeat
                        player?.repeatMode = if (repeat) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                    }, onAddToPlaylist = { askAddToPlaylist(nowPlaying!!) })
                selectedPlaylistId != null &&
                    playlists.any { it.id == selectedPlaylistId } -> {
                    val active = playlists.first { it.id == selectedPlaylistId }
                    VmPlaylistPage(playlist = active,
                        onBack = { selectedPlaylistId = null },
                        onPlay = { at ->
                            playSongs(active.tracks, at); playerOpen = true
                        },
                        onShuffle = {
                            val randomized = active.tracks.shuffled()
                            if (randomized.isNotEmpty()) {
                                playSongs(randomized, 0); playerOpen = true
                            }
                        },
                        onRename = { editingPlaylistId = active.id },
                        onDelete = { deletingPlaylistId = active.id },
                        onRemove = { song ->
                            updatePlaylists { all -> all.map { pl ->
                                if (pl.id == active.id) pl.copy(
                                    tracks = pl.tracks.filterNot { it.id == song.id })
                                else pl
                            } }
                        },
                        onMove = { from, to ->
                            updatePlaylists { all -> all.map { pl ->
                                if (pl.id != active.id) pl else {
                                    val items = pl.tracks.toMutableList()
                                    if (from in items.indices && to in items.indices) {
                                        val item = items.removeAt(from); items.add(to, item)
                                    }
                                    pl.copy(tracks = items)
                                }
                            } }
                        },
                        onAddSongs = {
                            playlistAddTarget = active.id
                            selectedPlaylistId = null
                            tab = 1
                        },
                        onOpenSong = { openSong(it) },
                        isFavorite = ::isFavorite,
                        onFavorite = ::toggleFavorite)
                }
                songPage != null -> VmTrackPage(
                    song = songPage!!, info = songDetails, lyrics = songLyrics,
                    onBack = { songPage = null },
                    onPlay = { playSongs(listOf(songPage!!), 0); playerOpen = true },
                    onArtist = ::openArtist,
                    onAlbum = {
                        val song = songPage!!
                        val info = songDetails
                        if (info != null && info.albumId > 0) openAlbum(
                            AlbumInfo(info.albumId, info.album, song.artist, song.art,
                                info.year, 0))
                    },
                    onLyrics = { val s = songPage!!; playSongs(listOf(s), 0); playerOpen = true; lyricsOpen = true },
                    isFavorite = isFavorite(songPage!!),
                    onFavorite = { toggleFavorite(songPage!!) },
                    onAddToPlaylist = { askAddToPlaylist(songPage!!) })
                artistAllOpen && artistPage != null -> VmAllArtistSongsPage(
                    name = artistPage!!, songs = artistSongs, onBack = { artistAllOpen = false },
                    onPlay = { playSongs(artistSongs, artistSongs.indexOf(it)); playerOpen = true },
                    onSong = ::openSong, isFavorite = ::isFavorite, onFavorite = ::toggleFavorite,
                    onAddToPlaylist = ::askAddToPlaylist)
                genrePage != null -> VmGenrePage(
                    genre = genrePage!!, songs = genreSongs, albums = genreAlbums,
                    artistPhotos = genrePhotos, loading = genreLoading,
                    onBack = { genrePage = null },
                    onArtist = { name -> genrePage = null; openArtist(name) },
                    onAlbum = { album -> genrePage = null; openAlbum(album) },
                    onPlay = { song -> playSongs(listOf(song), 0); playerOpen = true },
                    onSong = { song -> genrePage = null; openSong(song) },
                    isFavorite = ::isFavorite, onFavorite = ::toggleFavorite,
                    onAddToPlaylist = ::askAddToPlaylist)
                albumPage != null -> VmAlbumPage(albumPage!!, albumSongs, albumLoading,
                    onBack = { albumPage = null }, onArtist = ::openArtist,
                    onPlay = { playSongs(albumSongs, albumSongs.indexOf(it)); playerOpen = true },
                    onSong = ::openSong, isFavorite = ::isFavorite, onFavorite = ::toggleFavorite,
                    onAddToPlaylist = ::askAddToPlaylist)
                artistPage != null -> VmArtistPage(
                    name = artistPage!!, info = artistInfo, bio = artistBio, songs = artistSongs,
                    topSongs = artistTopSongs, isTopSampled = artistTopFromYoutube,
                    albums = artistAlbums,
                    loading = artistLoading, followed = followed.contains(artistPage),
                    onFollow = { toggleFollow(artistPage!!) }, onBack = { artistPage = null },
                    onPlay = { selected ->
                        val index = artistSongs.indexOfFirst { it.id == selected.id }
                        if (index >= 0) playSongs(artistSongs, index)
                        else playSongs(listOf(selected), 0)
                        playerOpen = true
                    },
                    onSong = ::openSong, onAlbum = ::openAlbum, onAllSongs = { artistAllOpen = true },
                    isFavorite = ::isFavorite, onFavorite = ::toggleFavorite,
                    onAddToPlaylist = ::askAddToPlaylist)
                else -> Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().padding(start = 19.dp, end = 13.dp, top = 21.dp, bottom = 23.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(43.dp).clip(RoundedCornerShape(15.dp)).background(VmAccent),
                            contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.GraphicEq, "VistaMusic", tint = VmBackground,
                                modifier = Modifier.size(27.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("VistaMusic", color = VmWhite, fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold)
                            Text("A tua música, do teu jeito.", color = VmMuted, fontSize = 11.sp)
                        }
                        if (tab == 2) {
                            IconButton(onClick = { tab = 3 }) {
                                Icon(Icons.Default.Settings, "Definições", tint = VmMuted)
                            }
                        }
                    }
                    when (tab) {
                        0 -> LazyColumn(contentPadding = PaddingValues(bottom = 26.dp)) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(horizontal = 17.dp)
                                    .height(187.dp).clip(RoundedCornerShape(24.dp))
                                    .background(Brush.linearGradient(listOf(Color(0xFF632544),
                                        Color(0xFF43263D), Color(0xFF24202C))))
                                    .clickable {
                                        heroSong?.let { track ->
                                            lastHeroId = track.id
                                            playSongs(listOf(track), 0)
                                            playerOpen = true
                                        }
                                    }.padding(20.dp)) {
                                    Column(Modifier.align(Alignment.CenterStart)
                                        .fillMaxWidth(.64f)) {
                                        Text(if (taste.listenedCount() > 0 || favorites.isNotEmpty())
                                            "ESCOLHIDO PARA TI" else "DESCOBRE NOVA MÚSICA",
                                            color = VmAccent, fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold)
                                        Spacer(Modifier.height(14.dp))
                                        Text(heroSong?.let {
                                            LyricsApi.cleanTitle(it.title, it.artist)
                                        } ?: "O teu próximo favorito",
                                            color = VmWhite, fontWeight = FontWeight.ExtraBold,
                                            fontSize = 23.sp, lineHeight = 28.sp, maxLines = 3,
                                            overflow = TextOverflow.Ellipsis)
                                        Spacer(Modifier.height(9.dp))
                                        Text(heroSong?.artist ?: "Ouve e descobre algo novo",
                                            color = VmWhite.copy(alpha = .84f),
                                            fontSize = 13.sp, maxLines = 1,
                                            overflow = TextOverflow.Ellipsis)
                                    }
                                    heroSong?.let { song ->
                                        VmSongArt(song, Modifier.size(113.dp)
                                            .align(Alignment.CenterEnd))
                                    }
                                }
                                Spacer(Modifier.height(25.dp))
                            }
                            if (taste.listenedCount() > 0 || favorites.isNotEmpty()) {
                                item {
                                    VmHeading("Feito para ti", Modifier.padding(start = 20.dp, bottom = 6.dp))
                                    Text("Sugestões baseadas no que ouves e guardas",
                                        color = VmMuted, fontSize = 12.sp,
                                        modifier = Modifier.padding(start = 20.dp, bottom = 12.dp))
                                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp),
                                        horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                                        items(forYou.take(16)) { song ->
                                            Column(Modifier.width(140.dp).clickable {
                                                playSongs(forYou, forYou.indexOf(song))
                                                playerOpen = true
                                            }) {
                                                VmSongArt(song, Modifier.size(140.dp))
                                                Spacer(Modifier.height(8.dp))
                                                Text(song.title, color = VmWhite,
                                                    fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text(song.artist, color = VmMuted, fontSize = 11.sp,
                                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(23.dp))
                                }
                            }
                            item {
                                VmHeading("Em alta agora", Modifier.padding(start = 20.dp, bottom = 6.dp))
                                Text("YouTube Charts · Estados Unidos",
                                    color = VmMuted, fontSize = 12.sp,
                                    modifier = Modifier.padding(start = 20.dp, bottom = 13.dp))
                            }
                            if (ytTrendingUs.isNotEmpty()) {
                                item {
                                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp),
                                        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                        items(ytTrendingUs.take(30)) { song ->
                                            Column(Modifier.width(145.dp).clickable {
                                                playSongs(listOf(song), 0)
                                                playerOpen = true
                                            }) {
                                                VmSongArt(song, Modifier.size(145.dp))
                                                Spacer(Modifier.height(9.dp))
                                                Text(song.title, color = VmWhite, fontSize = 14.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text(song.artist, color = VmMuted, fontSize = 12.sp,
                                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                    }
                                }
                            } else {
                                item {
                                    Text("O YouTube Charts está indisponível neste momento.",
                                        color = VmMuted, modifier = Modifier.padding(20.dp))
                                }
                            }
                            item {
                                Spacer(Modifier.height(28.dp))
                                VmHeading("Explora o teu som", Modifier.padding(start = 20.dp, bottom = 12.dp))
                                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    items(Genres.categories.take(12)) { g ->
                                        SuggestionChip(onClick = { genrePage = g },
                                            label = { Text(g.title) })
                                    }
                                }
                                Spacer(Modifier.height(24.dp))
                                VmHeading("Mais sons para descobrir", Modifier.padding(start = 20.dp, bottom = 6.dp))
                                Text("Seleção de música do YouTube Charts",
                                    color = VmMuted, fontSize = 12.sp,
                                    modifier = Modifier.padding(start = 20.dp, bottom = 12.dp))
                            }
                            items(freshYoutube) { song ->
                                VmSongRow(song, isFavorite(song),
                                    { playSongs(listOf(song), 0) },
                                    { openSong(song) }, { toggleFavorite(song) })
                            }
                        }
                        1 -> Column {
                            Text("Pesquisar", color = VmWhite, fontSize = 30.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(start = 20.dp, bottom = 17.dp))
                            OutlinedTextField(
                                value = query,
                                onValueChange = { query = it },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                                singleLine = true, shape = RoundedCornerShape(18.dp),
                                leadingIcon = { Icon(Icons.Default.Search, null, tint = VmAccent) },
                                trailingIcon = if (query.isNotBlank()) {
                                    { IconButton(onClick = { query = "" }) {
                                        Icon(Icons.Default.Close, "Limpar", tint = VmMuted)
                                    } }
                                } else null,
                                placeholder = { Text("Música, álbum ou artista", color = VmMuted) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = VmAccent,
                                    unfocusedBorderColor = Color(0xFF454E50)))
                            Spacer(Modifier.height(17.dp))
                            LazyColumn(contentPadding = PaddingValues(bottom = 18.dp)) {
                                if (query.isBlank()) {
                                    item {
                                        VmGenreDiscovery { genre -> genrePage = genre }
                                    }
                                } else {
                                    suggestions?.let { artist ->
                                        item {
                                            VmHeading("Artista", Modifier.padding(start = 20.dp,
                                                top = 12.dp, bottom = 8.dp))
                                            Row(Modifier.fillMaxWidth()
                                                .clickable { openArtist(artist.name) }
                                                .padding(horizontal = 20.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically) {
                                                VmArtistAvatar(searchArtistPhoto, Modifier.size(61.dp))
                                                Spacer(Modifier.width(13.dp))
                                                Column {
                                                    Text(artist.name, color = VmWhite,
                                                        fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                                    Text("Artista · Ver perfil", color = VmMuted,
                                                        fontSize = 12.sp)
                                                }
                                            }
                                        }
                                    }
                                    if (albumResults.isNotEmpty()) {
                                        item {
                                            VmHeading("Álbuns", Modifier.padding(start = 20.dp,
                                                top = 18.dp, bottom = 12.dp))
                                            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp),
                                                horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                                                items(albumResults.take(25)) { album ->
                                                    VmAlbumTile(album) { openAlbum(album) }
                                                }
                                            }
                                        }
                                    }
                                    if (searchResults.isNotEmpty()) item {
                                        VmHeading("Músicas",
                                            Modifier.padding(start = 20.dp, top = 24.dp, bottom = 11.dp))
                                    }
                                    items(searchResults) { song ->
                                        VmSongRow(song, isFavorite(song),
                                            { playSongs(listOf(song), 0) },
                                            { openSong(song) }, { toggleFavorite(song) })
                                    }
                                    if (searchResults.isEmpty() && albumResults.isEmpty() &&
                                        suggestions == null) item {
                                        Text("A procurar resultados de músicas e álbuns…",
                                            color = VmMuted, modifier = Modifier.padding(22.dp))
                                    }
                                }
                            }
                        }
                        2 -> LazyColumn(contentPadding = PaddingValues(bottom = 30.dp)) {
                            item {
                                Text("A tua biblioteca", color = VmWhite, fontSize = 29.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp))
                                if (followed.isNotEmpty()) {
                                    VmHeading("Artistas seguidos", Modifier.padding(20.dp))
                                    followed.forEach { name ->
                                        Text(name, color = VmWhite, modifier = Modifier.fillMaxWidth()
                                            .clickable { openArtist(name) }.padding(18.dp))
                                    }
                                }
                                VmHeading("Músicas favoritas", Modifier.padding(horizontal = 20.dp,
                                    vertical = 14.dp))
                            }
                            if (favorites.isEmpty()) item {
                                Text("Adiciona músicas ao coração para as encontrares aqui.",
                                    color = VmMuted, modifier = Modifier.padding(20.dp))
                            }
                            items(favorites.toList()) { song ->
                                VmSongRow(song, true,
                                    { playSongs(favorites.toList(), favorites.indexOf(song)) },
                                    { openSong(song) }, { toggleFavorite(song) })
                            }
                        }
                        else -> Column(Modifier.verticalScroll(rememberScrollState()).padding(22.dp)) {
                            Text("Definições", color = VmWhite, fontSize = 29.sp,
                                fontWeight = FontWeight.ExtraBold)
                            Spacer(Modifier.height(18.dp))
                            Text("VistaMusic 0.7.0", color = VmAccent, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(15.dp))
                            Text("Música, letras e biblioteca sem servidor próprio.",
                                color = VmMuted)
                            Spacer(Modifier.height(22.dp))
                            Text("Jamendo · Cliente opcional", color = VmWhite)
                            OutlinedTextField(value = jamendoId, onValueChange = {
                                jamendoId = it
                                prefs.edit().putString("jamendo", it).apply()
                            }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Client ID") })
                            Spacer(Modifier.height(20.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Prévias do iTunes", color = VmWhite)
                                    Text("Quando disponíveis, são excertos de 30 segundos",
                                        color = VmMuted, fontSize = 12.sp)
                                }
                                Switch(checked = includePreviews, onCheckedChange = {
                                    includePreviews = it
                                    prefs.edit().putBoolean("previews", it).apply()
                                })
                            }
                            Spacer(Modifier.height(18.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Reprodução automática", color = VmWhite,
                                        fontWeight = FontWeight.SemiBold)
                                    Text("Continua com sugestões ao terminar uma música ou lista",
                                        color = VmMuted, fontSize = 12.sp)
                                }
                                Switch(checked = autoDj, onCheckedChange = {
                                    autoDj = it
                                    prefs.edit().putBoolean("auto_dj", it).apply()
                                })
                            }
                            Spacer(Modifier.height(15.dp))
                            Text("As recomendações usam histórico local, artistas e favoritos. Não enviamos esse histórico para servidores próprios.",
                                color = VmMuted, fontSize = 12.sp)
                            Spacer(Modifier.height(20.dp))
                            Text("A reprodução de música popular utiliza integrações experimentais. Algumas faixas podem estar indisponíveis.",
                                color = VmMuted, fontSize = 12.sp)
                            Spacer(Modifier.height(11.dp))
                            Text("Letras fornecidas por LRCLIB quando disponíveis.",
                                color = VmMuted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        if (!lyricsOpen && !playerOpen && nowPlaying != null) {
            VmMiniPlayer(nowPlaying!!, isPlaying, loadingAudio,
                positionMs, durationMs, bufferedMs, { playerOpen = true },
                ::previousSong, ::togglePlayback, ::nextSong)
        }
        if (!lyricsOpen && !playerOpen && artistPage == null && albumPage == null && songPage == null) {
            NavigationBar(containerColor = VmBackground) {
                listOf(Triple("Início", Icons.Default.Home, 0),
                    Triple("Pesquisar", Icons.Default.Search, 1),
                    Triple("Biblioteca", Icons.Default.LibraryMusic, 2)).forEach { (title, icon, idx) ->
                    NavigationBarItem(selected = tab == idx, onClick = {
                        tab = idx
                        genrePage = null
                        artistPage = null
                        artistAllOpen = false
                        albumPage = null
                        songPage = null
                        playerOpen = false
                        lyricsOpen = false
                    },
                        icon = { Icon(icon, title) }, label = { Text(title) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = VmAccent, selectedIconColor = VmBackground,
                            selectedTextColor = VmAccent, unselectedIconColor = VmMuted,
                            unselectedTextColor = VmMuted))
                }
            }
        }
    }
    if (playbackError.isNotBlank()) {
        AlertDialog(onDismissRequest = { playbackError = "" },
            title = { Text("Reprodução indisponível") },
            text = { Text(playbackError) },
            confirmButton = { TextButton(onClick = { playbackError = "" }) { Text("OK") } })
    }
}
