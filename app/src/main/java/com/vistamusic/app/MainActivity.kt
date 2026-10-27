package com.vistamusic.app

import android.os.Bundle
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
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(9, 11, 14)
        window.navigationBarColor = android.graphics.Color.rgb(9, 11, 14)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = VmAccent, onPrimary = VmBackground,
                    background = VmBackground, surface = VmCard,
                    onSurface = VmWhite, onBackground = VmWhite)
            ) { VistaMusicApp() }
        }
    }
}

@Composable
private fun VistaMusicApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("vistamusic", android.content.Context.MODE_PRIVATE) }
    val music = remember { MusicApi() }
    val catalog = remember { LibraryCatalog() }
    val taste = remember { LocalTaste(prefs) }
    val player = remember { ExoPlayer.Builder(context).build() }
    val scope = rememberCoroutineScope()

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
        val song = list[index]
        queue = list
        queueIndex = index
        nowPlaying = song
        lastLoggedId = ""
        loadingAudio = false
        playbackError = ""
        player.stop(); player.clearMediaItems()
        if (song.audio.startsWith("youtube:") || song.audio.startsWith("discover:")) {
            loadingAudio = true
            scope.launch {
                try {
                    val id = if (song.audio.startsWith("youtube:")) {
                        song.audio.removePrefix("youtube:")
                    } else {
                        val term = song.audio.removePrefix("discover:")
                        val results = NativeMusic.search(term)
                        val exact = results.firstOrNull() ?: error("Música não encontrada.")
                        exact.audio.removePrefix("youtube:")
                    }
                    val uri = NativeMusic.resolve(id)
                    if (nowPlaying?.id == song.id) {
                        player.setMediaItem(MediaItem.fromUri(uri))
                        player.prepare()
                        player.play()
                    }
                } catch (ex: Exception) {
                    if (nowPlaying?.id == song.id) playbackError =
                        "Não foi possível reproduzir esta faixa. Tenta outra música."
                } finally {
                    if (nowPlaying?.id == song.id) loadingAudio = false
                }
            }
        } else {
            player.setMediaItem(MediaItem.fromUri(song.audio))
            player.prepare()
            player.play()
        }
    }
    fun nextSong() {
        val current = nowPlaying
        if (current != null && durationMs > 0L && positionMs < durationMs - 15000L) {
            taste.add(current, "skip")
            historyVersion++
        }
        if (queue.isEmpty()) return
        val next = if (shuffle && queue.size > 1) {
            (queue.indices.filter { it != queueIndex }).random()
        } else if (queueIndex + 1 < queue.size) queueIndex + 1
        else if (repeat) 0 else -1
        if (next >= 0) { playSongs(queue, next); return }
        if (!autoDj || recommending || current == null) return
        recommending = true
        scope.launch {
            try {
                val chartPool = (ytTrendingPt + ytTrendingUs + globalHits + featured)
                    .distinctBy { it.id }
                val related = if (current.audio.startsWith("youtube:"))
                    runCatching { NativeMusic.relatedTo(current.audio.removePrefix("youtube:")) }
                        .getOrDefault(emptyList())
                    else emptyList()
                val candidates = taste.nextSongs(current, queue.map { it.id }.toSet(),
                    favorites.toList(), chartPool, catalog)
                val next = (related + candidates).distinctBy { it.id }
                    .filterNot { song -> queue.any { it.id == song.id } }
                if (next.isNotEmpty() && nowPlaying?.id == current.id) {
                    playSongs(queue + next.take(18), queue.size)
                }
            } finally { recommending = false }
        }
    }
    fun previousSong() {
        if (positionMs > 3500L) { player.seekTo(0); return }
        if (queueIndex > 0) playSongs(queue, queueIndex - 1)
        else player.seekTo(0)
    }
    fun togglePlayback() {
        if (loadingAudio) return
        if (player.mediaItemCount == 0) {
            if (queue.isNotEmpty()) playSongs(queue, queueIndex)
        } else if (player.isPlaying) player.pause() else player.play()
    }
    fun goBack() {
        when {
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
    BackHandler(enabled = genrePage != null || artistAllOpen || lyricsOpen || playerOpen || songPage != null ||
        albumPage != null || artistPage != null || tab != 0) { goBack() }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                playbackError = "O áudio desta faixa não está disponível. Experimenta outra."
                loadingAudio = false
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                nowPlaying?.let { taste.add(it, "complete"); historyVersion++ }
                nextSong()
            }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener); player.release() }
    }
    LaunchedEffect(player) {
        while (true) {
            delay(360)
            positionMs = player.currentPosition.coerceAtLeast(0L)
            durationMs = player.duration.takeIf { it > 0L } ?: 0L
            bufferedMs = player.bufferedPosition.coerceAtLeast(0L)
        }
    }
    LaunchedEffect(Unit) {
        coroutineScope {
            val u = async { runCatching { NativeMusic.trending("US") }.getOrDefault(emptyList()) }
            val p = async { runCatching { NativeMusic.trending("PT") }.getOrDefault(emptyList()) }
            val au = async { runCatching { music.popular("us") }.getOrDefault(emptyList()) }
            val ap = async { runCatching { music.popular("pt") }.getOrDefault(emptyList()) }
            val ind = async { runCatching { music.trending() }.getOrDefault(emptyList()) }
            ytTrendingUs = u.await(); ytTrendingPt = p.await()
            globalHits = au.await(); featured = ap.await()
            independent = ind.await()
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
    val forYou = remember(historyVersion, ytTrendingPt, ytTrendingUs, youtubeRadio,
        globalHits, featured, independent, favorites.size) {
        taste.rankedForYou(
            (youtubeRadio + ytTrendingPt + ytTrendingUs + globalHits + featured + independent),
            favorites.toList())
    }
    LaunchedEffect(historyVersion) {
        val seed = taste.recentlyListened().firstOrNull() ?: favorites.firstOrNull()
        if (seed != null && youtubeRadio.none { it.artist.equals(seed.artist, true) }) {
            youtubeRadio = runCatching { NativeMusic.search(seed.artist + " popular songs") }
                .getOrDefault(emptyList())
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
                    onNext = ::nextSong, onSeek = { player.seekTo(it) },
                    onLyrics = { lyricsOpen = true },
                    onArtist = { openArtist(nowPlaying!!.artist) },
                    onSong = { openSong(nowPlaying!!) },
                    onFavorite = { toggleFavorite(nowPlaying!!) },
                    onShuffle = { shuffle = !shuffle },
                    onRepeat = {
                        repeat = !repeat
                        player.repeatMode = if (repeat) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                    })
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
                    onFavorite = { toggleFavorite(songPage!!) })
                artistAllOpen && artistPage != null -> VmAllArtistSongsPage(
                    name = artistPage!!, songs = artistSongs, onBack = { artistAllOpen = false },
                    onPlay = { playSongs(artistSongs, artistSongs.indexOf(it)); playerOpen = true },
                    onSong = ::openSong, isFavorite = ::isFavorite, onFavorite = ::toggleFavorite)
                genrePage != null -> VmGenrePage(
                    genre = genrePage!!, songs = genreSongs, albums = genreAlbums,
                    artistPhotos = genrePhotos, loading = genreLoading,
                    onBack = { genrePage = null },
                    onArtist = { name -> genrePage = null; openArtist(name) },
                    onAlbum = { album -> genrePage = null; openAlbum(album) },
                    onPlay = { song -> playSongs(listOf(song), 0); playerOpen = true },
                    onSong = { song -> genrePage = null; openSong(song) },
                    isFavorite = ::isFavorite, onFavorite = ::toggleFavorite)
                albumPage != null -> VmAlbumPage(albumPage!!, albumSongs, albumLoading,
                    onBack = { albumPage = null }, onArtist = ::openArtist,
                    onPlay = { playSongs(albumSongs, albumSongs.indexOf(it)); playerOpen = true },
                    onSong = ::openSong, isFavorite = ::isFavorite, onFavorite = ::toggleFavorite)
                artistPage != null -> VmArtistPage(
                    name = artistPage!!, info = artistInfo, bio = artistBio, songs = artistSongs,
                    topSongs = artistTopSongs, isTopSampled = artistTopFromYoutube,
                    albums = artistAlbums,
                    loading = artistLoading, followed = followed.contains(artistPage),
                    onFollow = { toggleFollow(artistPage!!) }, onBack = { artistPage = null },
                    onPlay = { playSongs(artistSongs, artistSongs.indexOf(it)); playerOpen = true },
                    onSong = ::openSong, onAlbum = ::openAlbum, onAllSongs = { artistAllOpen = true },
                    isFavorite = ::isFavorite, onFavorite = ::toggleFavorite)
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
                        IconButton(onClick = { tab = 3 }) {
                            Icon(Icons.Default.Settings, "Definições", tint = VmMuted)
                        }
                    }
                    when (tab) {
                        0 -> LazyColumn(contentPadding = PaddingValues(bottom = 26.dp)) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(horizontal = 17.dp)
                                    .height(170.dp).clip(RoundedCornerShape(24.dp))
                                    .background(Brush.linearGradient(listOf(Color(0xFF204A42),
                                        Color(0xFF363A50), Color(0xFF232C2D))))
                                    .clickable {
                                        val list = ytTrendingPt.ifEmpty { featured }
                                        if (list.isNotEmpty()) {
                                            playSongs(list, 0); playerOpen = true
                                        }
                                    }.padding(20.dp)) {
                                    Column(Modifier.align(Alignment.CenterStart)
                                        .fillMaxWidth(.67f)) {
                                        Text("DESCOBRE A MÚSICA", color = VmAccent,
                                            fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        Spacer(Modifier.height(12.dp))
                                        Text("A tua próxima\nfavorita está aqui.",
                                            color = VmWhite, fontWeight = FontWeight.ExtraBold,
                                            fontSize = 25.sp, lineHeight = 30.sp)
                                    }
                                    (ytTrendingPt.firstOrNull() ?: featured.firstOrNull())?.let { song ->
                                        VmArt(song.art, Modifier.size(96.dp)
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
                                                VmArt(song.art, Modifier.size(140.dp))
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
                                Text(if (ytTrendingPt.isNotEmpty())
                                        "YouTube Charts · Portugal · vídeos em tendência"
                                    else "Apple Music · Portugal · dados alternativos",
                                    color = VmMuted, fontSize = 12.sp,
                                    modifier = Modifier.padding(start = 20.dp, bottom = 13.dp))
                            }
                            val mainTrending = ytTrendingPt.ifEmpty { featured }
                            if (mainTrending.isNotEmpty()) {
                                item {
                                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp),
                                        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                        items(mainTrending.take(25)) { song ->
                                            Column(Modifier.width(145.dp).clickable {
                                                playSongs(listOf(song), 0)
                                                playerOpen = true
                                            }) {
                                                VmArt(song.art, Modifier.size(145.dp))
                                                Spacer(Modifier.height(9.dp))
                                                Text(song.title, color = VmWhite, fontSize = 14.sp,
                                                    fontWeight = FontWeight.SemiBold, maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis)
                                                Text(song.artist, color = VmMuted, fontSize = 12.sp,
                                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                    }
                                }
                            } else item {
                                Text("O ranking está temporariamente indisponível.",
                                    color = VmMuted, modifier = Modifier.padding(20.dp))
                            }
                            item {
                                Spacer(Modifier.height(26.dp))
                                VmHeading("Em alta em Portugal", Modifier.padding(start = 20.dp, bottom = 6.dp))
                                Text(if (ytTrendingUs.isNotEmpty())
                                        "YouTube Charts · Estados Unidos · tendências"
                                    else "Apple Music · Estados Unidos · dados alternativos",
                                    color = VmMuted, fontSize = 12.sp,
                                    modifier = Modifier.padding(start = 20.dp, bottom = 12.dp))
                                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp),
                                    horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                                    items(ytTrendingUs.ifEmpty { globalHits }.take(20)) { song ->
                                        Column(Modifier.width(145.dp).clickable {
                                            playSongs(listOf(song), 0)
                                            playerOpen = true
                                        }) {
                                            VmArt(song.art, Modifier.size(145.dp))
                                            Spacer(Modifier.height(9.dp))
                                            Text(song.title, color = VmWhite, fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold, maxLines = 1,
                                                overflow = TextOverflow.Ellipsis)
                                            Text(song.artist, color = VmMuted, fontSize = 11.sp,
                                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                                Spacer(Modifier.height(26.dp))
                                VmHeading("Explora o teu som", Modifier.padding(start = 20.dp, bottom = 12.dp))
                                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    items(Genres.categories.take(12)) { g ->
                                        SuggestionChip(onClick = { genrePage = g },
                                            label = { Text(g.title) })
                                    }
                                }
                                Spacer(Modifier.height(24.dp))
                                VmHeading("Novos sons para descobrir", Modifier.padding(start = 20.dp, bottom = 8.dp))
                            }
                            items(independent.take(18)) { song ->
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
                                    if (selectedGenre != null) item {
                                        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp,
                                            vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text("A explorar: ${selectedGenre}", color = VmWhite,
                                                fontSize = 18.sp, fontWeight = FontWeight.Bold,
                                                modifier = Modifier.weight(1f))
                                            TextButton(onClick = { query = ""; selectedGenre = null }) {
                                                Text("Voltar")
                                            }
                                        }
                                    }
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
                            Text("VistaMusic 0.6.0", color = VmAccent, fontWeight = FontWeight.Bold)
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
                    NavigationBarItem(selected = tab == idx, onClick = { tab = idx },
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
