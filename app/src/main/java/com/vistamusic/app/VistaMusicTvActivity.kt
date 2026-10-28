package com.vistamusic.app

import android.os.Bundle
import android.content.ComponentName
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import androidx.media3.common.Player
import kotlinx.coroutines.launch
import org.json.JSONArray

/**
 * Native 16:9/D-pad VistaMusic experience. Launching VistaMusic from Android TV
 * opens this Activity, phones continue to use the existing premium MainActivity.
 */
class VistaMusicTvActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(13, 10, 23)
        window.navigationBarColor = android.graphics.Color.rgb(13, 10, 23)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = VmAccent, onPrimary = VmBackground,
                background = Color(0xFF0D0A17), surface = Color(0xFF211526),
                onBackground = Color.White, onSurface = Color.White
            )) { VistaMusicTvPage() }
        }
    }
}

@Composable
private fun VistaMusicTvPage() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("vistamusic", Context.MODE_PRIVATE) }
    val store = remember { VmPlaylistStore(context) }
    val api = remember { MusicApi() }
    val scope = rememberCoroutineScope()
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var playlists by remember { mutableStateOf(store.load()) }
    var favorites by remember { mutableStateOf(readTvFavorites(prefs.getString("favorites","[]"))) }
    var hits by remember { mutableStateOf<List<Song>>(emptyList()) }
    var results by remember { mutableStateOf<List<Song>>(emptyList()) }
    var search by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var current by remember { mutableStateOf<Song?>(null) }
    var playing by remember { mutableStateOf(false) }
    var account by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            runCatching { future.get() }.onSuccess { connected ->
                controller = connected
                playing = connected.isPlaying
                connected.currentMediaItem?.mediaMetadata?.extras
                    ?.getString(PlaybackService.SONG_JSON)?.let { raw ->
                        current = runCatching { Song.fromJson(org.json.JSONObject(raw)) }.getOrNull()
                    }
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            controller = null
            MediaController.releaseFuture(future)
        }
    }
    DisposableEffect(controller) {
        val linked = controller
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                current = mediaItem?.mediaMetadata?.extras?.getString(PlaybackService.SONG_JSON)
                    ?.let { runCatching { Song.fromJson(org.json.JSONObject(it)) }.getOrNull() }
            }
        }
        linked?.addListener(listener)
        onDispose { linked?.removeListener(listener) }
    }
    DisposableEffect(prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "favorites") {
                favorites = readTvFavorites(prefs.getString("favorites", "[]"))
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    LaunchedEffect(Unit) {
        runCatching { api.popular("pt") }.onSuccess { hits = it }
            .onFailure { error = "O catálogo musical está temporariamente indisponível." }
    }
    fun play(list: List<Song>, index: Int) {
        val player = controller
        if (player == null) {
            error = "O reprodutor está a ligar. Tenta novamente."
            return
        }
        if (index !in list.indices) return
        current = list[index]
        val json = JSONArray().apply { list.forEach { put(it.toJson()) } }.toString()
        player.sendCustomCommand(SessionCommand(PlaybackService.PLAY_QUEUE, Bundle.EMPTY),
            Bundle().apply {
                putString(PlaybackService.QUEUE_JSON, json)
                putInt(PlaybackService.QUEUE_INDEX, index)
            })
    }
    val background = Brush.verticalGradient(listOf(Color(0xFF2B1937), Color(0xFF120E1E), Color(0xFF0D0A17)))
    Box(Modifier.fillMaxSize().background(background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 52.dp, end = 52.dp, top = 28.dp, bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(17.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("VISTA MUSIC", color = VmAccent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("A tua música. Agora na TV.", color = Color.White,
                            fontSize = 31.sp, fontWeight = FontWeight.ExtraBold)
                        Spacer(Modifier.height(4.dp))
                        Text("Navega com o comando • playlists e favoritos guardados localmente",
                            color = Color(0xFFBFB2CA), fontSize = 15.sp)
                    }
                    TvMusicButton("Vista ID", { account = true })
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = search, onValueChange = { search = it.take(100) },
                        label = { Text("Pesquisar artistas, álbuns ou músicas") },
                        modifier = Modifier.weight(1f), singleLine = true)
                    TvMusicButton(if (searching) "A procurar…" else "Pesquisar",
                        onClick = {
                            if (search.isNotBlank() && !searching) {
                                searching = true
                                scope.launch {
                                    runCatching {
                                        api.search(search, prefs.getString("jamendo","").orEmpty(),
                                            prefs.getBoolean("previews",false), "")
                                    }.onSuccess { results = it }
                                        .onFailure { error = "Pesquisa indisponível agora." }
                                    searching = false
                                }
                            }
                        })
                }
            }
            if (results.isNotEmpty()) item {
                TvMusicRail("Resultados da pesquisa", results, ::play)
            }
            item {
                Text("As tuas playlists", color = Color.White,
                    fontSize = 23.sp, fontWeight = FontWeight.Bold)
                if (playlists.isEmpty()) {
                    Text("Cria playlists no VistaMusic e elas aparecerão aqui nesta TV.",
                        color = Color(0xFFBFB2CA))
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                        itemsIndexed(playlists, key = { _, item -> item.id }) { _, playlist ->
                            TvMusicCard(playlist.name, "${playlist.tracks.size} músicas", 260) {
                                if (playlist.tracks.isNotEmpty()) play(playlist.tracks, 0)
                                else error = "Esta playlist ainda está vazia."
                            }
                        }
                    }
                }
            }
            if (favorites.isNotEmpty()) item {
                TvMusicRail("Músicas curtidas", favorites, ::play)
            }
            if (hits.isNotEmpty()) item {
                TvMusicRail("Descobrir música • Portugal", hits, ::play)
            }
            if (error.isNotEmpty()) item {
                Text(error, color = Color(0xFFFFB6CE), fontSize = 14.sp)
            }
        }
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            .background(Color(0xEF201429)).padding(horizontal = 48.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(current?.title ?: "VistaMusic", color = Color.White,
                    fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(current?.artist ?: "Escolhe uma música", color = Color(0xFFBFB2CA))
            }
            TvMusicButton("Anterior") {
                controller?.sendCustomCommand(SessionCommand(PlaybackService.PREVIOUS, Bundle.EMPTY), Bundle.EMPTY)
            }
            TvMusicButton(if (playing) "Pausa" else "Reproduzir") {
                val player = controller
                if (player != null) { if (player.isPlaying) player.pause() else player.play() }
            }
            TvMusicButton("Próxima") {
                controller?.sendCustomCommand(SessionCommand(PlaybackService.NEXT, Bundle.EMPTY), Bundle.EMPTY)
            }
        }
        if (account) {
            AlertDialog(onDismissRequest = { account = false },
                confirmButton = { TextButton(onClick = { account = false }) { Text("Fechar") } },
                title = { Text("Vista ID • opcional") },
                text = { VistaIdLocalCard() })
        }
    }
}

private fun readTvFavorites(raw: String?): List<Song> {
    val arr = runCatching { JSONArray(raw ?: "[]") }.getOrDefault(JSONArray())
    return (0 until arr.length()).mapNotNull {
        arr.optJSONObject(it)?.let { item -> runCatching { Song.fromJson(item) }.getOrNull() }
    }
}

@Composable
private fun TvMusicButton(label: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Button(onClick = onClick,
        modifier = Modifier.onFocusChanged { focused = it.isFocused },
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (focused) VmAccent else Color(0xFF3D2948),
            contentColor = if (focused) VmBackground else Color.White
        )) {
        Text(label, fontSize = 15.sp)
    }
}

@Composable
private fun TvMusicCard(title: String, subtitle: String, width: Int = 280, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.width(width.dp).height(112.dp)
        .onFocusChanged { focused = it.isFocused }
        .border(if (focused) 3.dp else 1.dp,
            if (focused) VmAccent else Color(0xFF4D354E), RoundedCornerShape(17.dp))
        .background(if (focused) Color(0xFF56304B) else Color(0xFF2B1C33), RoundedCornerShape(17.dp))
        .clickable(onClick = onClick)
        .padding(18.dp),
        verticalArrangement = Arrangement.Center) {
        Text(title, color = Color.White, fontSize = 18.sp,
            fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(7.dp))
        Text(subtitle, color = Color(0xFFD1C2D6), fontSize = 13.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TvMusicRail(title: String, songs: List<Song>, onPlay: (List<Song>, Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(songs, key = { i, song -> song.id + "_" + i }) { index, song ->
                TvMusicCard(song.title, song.artist, 260) { onPlay(songs, index) }
            }
        }
    }
}
