package com.vistamusic.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import kotlin.math.roundToInt

private val Back = Color(0xFF090B0D)
private val Panel = Color(0xFF171B1F)
private val Elevated = Color(0xFF22282C)
private val Lime = Color(0xFFB9F773)
private val Fog = Color(0xFF929B9D)
private val White = Color(0xFFF7FAF7)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Lime, background = Back, surface = Panel, onSurface = White,
                onBackground = White, onPrimary = Back)) {
                VistaMusic()
            }
        }
    }
}

@Composable
private fun VistaMusic() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("vistamusic", Context.MODE_PRIVATE) }
    val api = remember { MusicApi() }
    val player = remember { ExoPlayer.Builder(context).build() }
    var isPlaying by remember { mutableStateOf(false) }
    var tab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var trending by remember { mutableStateOf<List<Song>>(emptyList()) }
    var popular by remember { mutableStateOf<List<Song>>(emptyList()) }
    val scope = rememberCoroutineScope()
    var youtubeApiKey by remember { mutableStateOf(prefs.getString("youtubeApiKey", "").orEmpty()) }
    var videoId by remember { mutableStateOf<String?>(null) }
    var videoQuery by remember { mutableStateOf<String?>(null) }
    var videoBusy by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<Song>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var searching by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Song?>(null) }
    var queue by remember { mutableStateOf<List<Song>>(emptyList()) }
    var queueIndex by remember { mutableIntStateOf(0) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var jamendo by remember { mutableStateOf(prefs.getString("jamendo", "").orEmpty()) }
    var previews by remember { mutableStateOf(prefs.getBoolean("previews", false)) }
    val favorites = remember {
        mutableStateListOf<Song>().apply {
            val arr = runCatching { JSONArray(prefs.getString("favorites", "[]")) }.getOrDefault(JSONArray())
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { json ->
                    runCatching { Song.fromJson(json) }.getOrNull()?.let { add(it) }
                }
            }
        }
    }
    fun saveFavorites() {
        val arr = JSONArray()
        favorites.forEach { arr.put(it.toJson()) }
        prefs.edit().putString("favorites", arr.toString()).apply()
    }
    fun playSongs(songs: List<Song>, index: Int) {
        if (songs.isEmpty() || index !in songs.indices) return
        queue = songs
        queueIndex = index
        val song = songs[index]
        selected = song
        if (song.audio.startsWith("youtube:")) {
            player.pause()
            videoQuery = null
            videoId = song.audio.removePrefix("youtube:")
            return
        }
        if (song.audio.startsWith("discover:")) {
            player.pause()
            videoBusy = true
            val lookFor = song.audio.removePrefix("discover:")
            scope.launch {
                val found = runCatching { YouTubeCatalog.search(lookFor, youtubeApiKey) }
                    .getOrDefault(emptyList())
                if (found.isNotEmpty()) {
                    videoQuery = null
                    videoId = found.first().audio.removePrefix("youtube:")
                } else {
                    // No key or discovery service offline: open YouTube's own search.
                    videoId = null
                    videoQuery = lookFor
                }
                videoBusy = false
            }
            return
        }
        videoId = null
        videoQuery = null
        player.setMediaItem(MediaItem.fromUri(song.audio))
        player.prepare()
        player.play()
    }
    fun nextSong() {
        if (queue.isNotEmpty() && queueIndex + 1 < queue.size) {
            playSongs(queue, queueIndex + 1)
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isNowPlaying: Boolean) { isPlaying = isNowPlaying }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                message = "Não foi possível reproduzir esta faixa."
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED && queueIndex + 1 < queue.size) nextSong()
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    LaunchedEffect(Unit) {
        loading = true
        runCatching { api.trending() }
            .onSuccess { trending = it; message = if (it.isEmpty()) "Nenhuma faixa disponível." else "" }
            .onFailure { message = "Não foi possível carregar o catálogo. Confirma a ligação à Internet." }
        loading = false
    }
    LaunchedEffect(Unit) {
        runCatching { api.popular() }
            .onSuccess { popular = it }
    }
    LaunchedEffect(query, tab, jamendo, previews, youtubeApiKey) {
        if (tab != 1) return@LaunchedEffect
        if (query.isBlank()) { results = emptyList(); searching = false; return@LaunchedEffect }
        searching = true
        delay(450)
        runCatching { api.search(query, jamendo, previews, youtubeApiKey) }
            .onSuccess { results = it; message = "" }
            .onFailure { results = emptyList(); message = "Pesquisa indisponível neste momento." }
        searching = false
    }
    LaunchedEffect(selected) {
        while (true) {
            delay(650)
            position = player.currentPosition.coerceAtLeast(0)
            duration = player.duration.takeIf { it > 0 } ?: 0L
        }
    }
    Column(Modifier.fillMaxSize().background(Back)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 22.dp, end = 20.dp, top = 18.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(39.dp).clip(RoundedCornerShape(13.dp)).background(Lime),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.GraphicEq, null, tint = Back, modifier = Modifier.size(26.dp)) }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("VistaMusic", color = White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Text("A tua música, do teu jeito.", color = Fog, fontSize = 10.sp)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { tab = 3 }) {
                Icon(Icons.Default.Settings, "Definições", tint = Fog)
            }
        }
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Box(
                            Modifier.fillMaxWidth().padding(horizontal = 19.dp)
                                .height(192.dp).clip(RoundedCornerShape(25.dp))
                                .background(Brush.linearGradient(listOf(
                                    Color(0xFF2E5445), Color(0xFF343653), Color(0xFF1A2022))))
                                .padding(22.dp)
                        ) {
                            Column(Modifier.align(Alignment.CenterStart)) {
                                Text("DESCOBRE NOVOS SONS", color = Lime,
                                    fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(9.dp))
                                Text("Sente cada\nbatida.", color = White, fontSize = 31.sp,
                                    lineHeight = 34.sp, fontWeight = FontWeight.ExtraBold)
                                Spacer(Modifier.height(14.dp))
                                Button(
                                    onClick = { playSongs(trending, 0) },
                                    enabled = trending.isNotEmpty(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Back)
                                ) {
                                    Icon(Icons.Default.PlayArrow, null)
                                    Text("Ouvir agora", fontWeight = FontWeight.Bold)
                                }
                            }
                            if (trending.isNotEmpty()) {
                                Art(trending.first(), Modifier.size(112.dp)
                                    .align(Alignment.CenterEnd).clip(RoundedCornerShape(19.dp)))
                            }
                        }
                    }
                    item { TitleLine("Hits populares em Portugal") }
                    if (popular.isNotEmpty()) {
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 19.dp),
                                horizontalArrangement = Arrangement.spacedBy(13.dp)
                            ) {
                                items(popular.take(20)) { song ->
                                    Column(Modifier.width(142.dp).clickable {
                                        playSongs(popular, popular.indexOf(song))
                                    }) {
                                        Art(song, Modifier.size(142.dp)
                                            .clip(RoundedCornerShape(16.dp)))
                                        Spacer(Modifier.height(8.dp))
                                        Text(song.title, color = White, fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold, maxLines = 1,
                                            overflow = TextOverflow.Ellipsis)
                                        Text(song.artist, color = Fog, fontSize = 11.sp,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                    item { TitleLine("Explora artistas populares") }
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 19.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(listOf("Justin Bieber", "Ariana Grande", "The Weeknd",
                                "Taylor Swift", "Billie Eilish", "Bruno Mars",
                                "Dua Lipa", "Bad Bunny", "Sabrina Carpenter", "Drake")) { artist ->
                                SuggestionChip(onClick = { query = artist; tab = 1 },
                                    label = { Text(artist) })
                            }
                        }
                    }
                    item { TitleLine("Em alta no Audius") }
                    if (loading) {
                        item { CircularProgressIndicator(Modifier.padding(30.dp), color = Lime) }
                    } else if (trending.isEmpty()) {
                        item { Text(message, color = Fog, modifier = Modifier.padding(horizontal = 22.dp)) }
                    } else {
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 19.dp),
                                horizontalArrangement = Arrangement.spacedBy(13.dp)
                            ) {
                                items(trending.take(14)) { song ->
                                    Column(Modifier.width(142.dp).clickable {
                                        playSongs(trending, trending.indexOf(song))
                                    }) {
                                        Art(song, Modifier.size(142.dp).clip(RoundedCornerShape(16.dp)))
                                        Spacer(Modifier.height(8.dp))
                                        Text(song.title, color = White, fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold, maxLines = 1,
                                            overflow = TextOverflow.Ellipsis)
                                        Text(song.artist, color = Fog, fontSize = 11.sp,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                    item { TitleLine("Descobre mais") }
                    items(trending.drop(14)) { song ->
                        SongRow(song, favorites.any { it.id == song.id },
                            { playSongs(trending, trending.indexOf(song)) },
                            {
                                if (favorites.any { it.id == song.id }) favorites.removeAll { it.id == song.id }
                                else favorites.add(0, song)
                                saveFavorites()
                            })
                    }
                }
                1 -> Column {
                    Text("Pesquisar", modifier = Modifier.padding(horizontal = 22.dp), color = White,
                        fontWeight = FontWeight.Bold, fontSize = 26.sp)
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Música, artista, álbum...", color = Fog) },
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = Lime) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 19.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Lime, unfocusedBorderColor = Elevated)
                    )
                    Spacer(Modifier.height(14.dp))
                    if (searching) LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(), color = Lime)
                    if (query.isBlank()) {
                        Text("Explora novas músicas e artistas.", color = Fog,
                            modifier = Modifier.padding(22.dp))
                    } else if (!searching && results.isEmpty()) {
                        Text("Nenhuma música encontrada.", color = Fog,
                            modifier = Modifier.padding(22.dp))
                    }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        item {
                            Text("Vídeos oficiais, catálogo popular e fontes independentes",
                                color = Fog, fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp))
                        }
                        items(results) { song ->
                            SongRow(song, favorites.any { it.id == song.id },
                                { playSongs(results, results.indexOf(song)) },
                                {
                                    if (favorites.any { it.id == song.id }) favorites.removeAll { it.id == song.id }
                                    else favorites.add(0, song)
                                    saveFavorites()
                                })
                        }
                        if (query.isNotBlank()) item {
                            OutlinedButton(
                                onClick = { player.pause(); videoId = null; videoQuery = query },
                                modifier = Modifier.padding(horizontal = 19.dp, vertical = 12.dp)
                            ) {
                                Icon(Icons.Default.OpenInNew, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Procurar diretamente no YouTube")
                            }
                        }
                    }
                }
                2 -> LazyColumn {
                    item { Text("A tua biblioteca", modifier = Modifier.padding(22.dp),
                        color = White, fontSize = 26.sp, fontWeight = FontWeight.Bold) }
                    if (favorites.isEmpty()) item {
                        Text("As tuas músicas favoritas vão aparecer aqui.", color = Fog,
                            modifier = Modifier.padding(horizontal = 22.dp))
                    }
                    items(favorites.toList()) { song ->
                        SongRow(song, true,
                            { playSongs(favorites.toList(), favorites.indexOf(song)) },
                            { favorites.removeAll { it.id == song.id }; saveFavorites() })
                    }
                }
                else -> Column(Modifier.verticalScroll(rememberScrollState()).padding(22.dp)) {
                    Text("Definições", color = White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(13.dp))
                    Text("Sem conta própria, servidor ou catálogo alojado.", color = Fog)
                    Spacer(Modifier.height(24.dp))
                    Text("Fontes musicais", color = Lime, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    Text("Audius: músicas completas (ativo)", color = White)
                    Spacer(Modifier.height(8.dp))
                    Text("YouTube: vídeos oficiais e hits populares", color = White)
                    Spacer(Modifier.height(6.dp))
                    Text("Pesquisa experimental sem chave; os vídeos são reproduzidos no player oficial e podem ter anúncios.",
                        color = Fog, fontSize = 12.sp)
                    Spacer(Modifier.height(10.dp))
                    Text("Chave opcional da YouTube Data API", color = Fog, fontSize = 12.sp)
                    OutlinedTextField(
                        value = youtubeApiKey, onValueChange = {
                            youtubeApiKey = it.trim()
                            prefs.edit().putString("youtubeApiKey", youtubeApiKey).apply()
                        },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("YouTube Data API v3 (opcional)") })
                    Spacer(Modifier.height(18.dp))
                    Text("Jamendo: introduz o teu Client ID gratuito", color = Fog, fontSize = 12.sp)
                    OutlinedTextField(
                        value = jamendo, onValueChange = {
                            jamendo = it; prefs.edit().putString("jamendo", it).apply()
                        },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Jamendo Client ID") })
                    Spacer(Modifier.height(18.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Prévias do iTunes", color = White, fontWeight = FontWeight.Medium)
                            Text("Apenas 30 segundos por faixa", color = Fog, fontSize = 11.sp)
                        }
                        Switch(checked = previews, onCheckedChange = {
                            previews = it; prefs.edit().putBoolean("previews", it).apply()
                        })
                    }
                    Spacer(Modifier.height(18.dp))
                    Text("Versão 0.2.0 experimental", color = Fog, fontSize = 12.sp)
                }
            }
        }
        if (selected != null && selected?.audio?.startsWith("youtube:") != true &&
            selected?.audio?.startsWith("discover:") != true) {
            val current = selected!!
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(17.dp)).background(Panel)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Art(current, Modifier.size(48.dp).clip(RoundedCornerShape(9.dp)))
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text(current.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(current.artist + " · " + current.source,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = Fog, fontSize = 11.sp)
                    }
                    IconButton(onClick = {
                        if (player.isPlaying) player.pause() else player.play()
                    }) { Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        "Reproduzir/pausar", tint = White) }
                    IconButton(onClick = { nextSong() }, enabled = queueIndex + 1 < queue.size) {
                        Icon(Icons.Default.SkipNext, "Próxima faixa", tint = Lime)
                    }
                }
                val ratio = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
                LinearProgressIndicator(progress = { ratio },
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = Lime, trackColor = Elevated)
            }
        }
        NavigationBar(containerColor = Back, tonalElevation = 0.dp) {
            val labels = listOf("Início", "Pesquisar", "Biblioteca")
            val icons = listOf(Icons.Default.Home, Icons.Default.Search, Icons.Default.LibraryMusic)
            labels.forEachIndexed { index, text ->
                NavigationBarItem(
                    selected = tab == index, onClick = { tab = index },
                    icon = { Icon(icons[index], text) },
                    label = { Text(text) },
                    alwaysShowLabel = true,
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Back, selectedTextColor = Lime,
                        indicatorColor = Lime, unselectedIconColor = Fog,
                        unselectedTextColor = Fog)
                )
            }
        }
    }
    if (videoBusy) {
        AlertDialog(onDismissRequest = {}, title = { Text("A encontrar a música…") },
            text = { Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(14.dp))
                Text("A procurar um vídeo reproduzível")
            } }, confirmButton = {})
    }
    if (videoId != null || videoQuery != null) {
        YouTubeViewer(videoId = videoId, searchQuery = videoQuery,
            onClose = { videoId = null; videoQuery = null })
    }
}

@Composable
private fun TitleLine(text: String) {
    Text(text, color = White, fontWeight = FontWeight.Bold, fontSize = 21.sp,
        modifier = Modifier.padding(horizontal = 22.dp, vertical = 3.dp))
}

@Composable
private fun Art(song: Song, modifier: Modifier) {
    if (song.art.isNotBlank()) {
        AsyncImage(model = song.art, contentDescription = song.title,
            contentScale = ContentScale.Crop, modifier = modifier.background(Elevated))
    } else {
        Box(modifier.background(Elevated), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.MusicNote, null, tint = Lime)
        }
    }
}

@Composable
private fun SongRow(song: Song, saved: Boolean, onPlay: () -> Unit, onFavorite: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onPlay)
        .padding(horizontal = 19.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Art(song, Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, color = White, fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist + " · " + song.source, color = Fog, fontSize = 11.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onFavorite) {
            Icon(if (saved) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                "Favorito", tint = if (saved) Lime else Fog)
        }
    }
}
