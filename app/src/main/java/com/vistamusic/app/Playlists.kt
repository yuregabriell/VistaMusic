package com.vistamusic.app

import android.content.Context
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class VmPlaylist(
    val id: String,
    val name: String,
    val description: String = "",
    val tracks: List<Song> = emptyList()
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("description", description)
        .put("tracks", JSONArray().also { a -> tracks.forEach { a.put(it.toJson()) } })

    companion object {
        fun fromJson(o: JSONObject): VmPlaylist? {
            val id = o.optString("id")
            val name = o.optString("name").trim()
            if (id.isBlank() || name.isBlank()) return null
            val arr = o.optJSONArray("tracks") ?: JSONArray()
            val songs = (0 until arr.length()).mapNotNull {
                arr.optJSONObject(it)?.let { j ->
                    runCatching { Song.fromJson(j) }.getOrNull()
                }
            }
            return VmPlaylist(id, name, o.optString("description"), songs)
        }
    }
}

class VmPlaylistStore(context: Context) {
    private val prefs = context.getSharedPreferences("vistamusic_playlists", Context.MODE_PRIVATE)
    fun load(): List<VmPlaylist> {
        val arr = runCatching {
            JSONArray(prefs.getString("playlists", "[]"))
        }.getOrDefault(JSONArray())
        return (0 until arr.length()).mapNotNull {
            arr.optJSONObject(it)?.let(VmPlaylist::fromJson)
        }
    }

    fun save(items: List<VmPlaylist>) {
        val arr = JSONArray()
        items.forEach { arr.put(it.toJson()) }
        prefs.edit().putString("playlists", arr.toString()).apply()
    }

    fun create(name: String, description: String): VmPlaylist =
        VmPlaylist(UUID.randomUUID().toString(), name.trim(), description.trim())
}

@Composable
fun VmPlaylistLibrary(
    playlists: List<VmPlaylist>,
    onCreate: () -> Unit,
    onOpen: (VmPlaylist) -> Unit
) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 21.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text("As tuas playlists", color = VmWhite, fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
        TextButton(onClick = onCreate) {
            Icon(Icons.Default.Add, "Criar", tint = VmAccent)
            Spacer(Modifier.width(5.dp))
            Text("Criar", color = VmAccent, fontWeight = FontWeight.Bold)
        }
    }
    if (playlists.isEmpty()) {
        Text("Cria uma playlist para reunir as músicas de que mais gostas.",
            color = VmMuted, fontSize = 13.sp,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 13.dp))
    } else {
        playlists.forEach { playlist ->
            Row(Modifier.fillMaxWidth().clickable { onOpen(playlist) }
                .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                VmPlaylistCover(playlist, Modifier.size(69.dp))
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text(playlist.name, color = VmWhite,
                        fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Text("Playlist · ${playlist.tracks.size} músicas",
                        color = VmMuted, fontSize = 12.sp)
                }
                Icon(Icons.Default.ChevronRight, null, tint = VmMuted)
            }
        }
    }
}

@Composable
fun VmPlaylistCover(playlist: VmPlaylist, modifier: Modifier = Modifier) {
    val shown = playlist.tracks.take(4)
    if (shown.isEmpty()) {
        Box(modifier.clip(RoundedCornerShape(12.dp))
            .background(Brush.linearGradient(listOf(
                VmAccent.copy(alpha = .56f), Color(0xFF422538)))),
            contentAlignment = Alignment.Center) {
            Icon(Icons.Default.LibraryMusic, null,
                tint = VmWhite, modifier = Modifier.fillMaxSize(.52f))
        }
    } else {
        // Single album covers remain legible at small sizes.
        VmSongArt(shown.first(), modifier)
    }
}

@Composable
fun VmPlaylistEditorDialog(
    initialName: String,
    initialDescription: String,
    title: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var description by remember(initialDescription) { mutableStateOf(initialDescription) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it.take(65) },
                    label = { Text("Nome da playlist") }, singleLine = true)
                OutlinedTextField(value = description,
                    onValueChange = { description = it.take(240) },
                    label = { Text("Descrição (opcional)") }, maxLines = 3)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim(), description.trim()) },
                enabled = name.isNotBlank()) { Text("Guardar") }
        }
    )
}

@Composable
fun VmPlaylistPickerDialog(
    song: Song,
    playlists: List<VmPlaylist>,
    onDismiss: () -> Unit,
    onPick: (VmPlaylist) -> Unit,
    onCreate: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adicionar à playlist") },
        text = {
            Column(Modifier.heightIn(max = 390.dp).verticalScroll(rememberScrollState())) {
                Text(LyricsApi.cleanTitle(song.title, song.artist) + " · " + song.artist,
                    color = VmMuted, fontSize = 12.sp, maxLines = 2)
                Spacer(Modifier.height(13.dp))
                playlists.forEach { playlist ->
                    Row(Modifier.fillMaxWidth().clickable { onPick(playlist) }
                        .padding(vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        VmPlaylistCover(playlist, Modifier.size(46.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(playlist.name, color = VmWhite,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${playlist.tracks.size} músicas",
                                color = VmMuted, fontSize = 11.sp)
                        }
                        if (playlist.tracks.any { it.id == song.id }) {
                            Icon(Icons.Default.CheckCircle, null, tint = VmAccent)
                        }
                    }
                }
                TextButton(onClick = onCreate) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Nova playlist")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } }
    )
}

@Composable
fun VmPlaylistPage(
    playlist: VmPlaylist,
    onBack: () -> Unit,
    onPlay: (Int) -> Unit,
    onShuffle: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onRemove: (Song) -> Unit,
    onMove: (Int, Int) -> Unit,
    onAddSongs: () -> Unit,
    onOpenSong: (Song) -> Unit,
    isFavorite: (Song) -> Boolean,
    onFavorite: (Song) -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(VmBackground)) {
        VmBackBar("Playlist", onBack)
        LazyColumn(contentPadding = PaddingValues(bottom = 25.dp)) {
            item {
                Column(Modifier.fillMaxWidth().background(
                    Brush.verticalGradient(listOf(
                        Color(0xFF78304E), Color(0xFF3C2436), VmBackground)))
                    .padding(horizontal = 20.dp, vertical = 20.dp)) {
                    VmPlaylistCover(playlist, Modifier.size(180.dp)
                        .align(Alignment.CenterHorizontally))
                    Spacer(Modifier.height(17.dp))
                    Text(playlist.name, color = VmWhite, fontSize = 31.sp,
                        fontWeight = FontWeight.ExtraBold)
                    if (playlist.description.isNotBlank()) {
                        Spacer(Modifier.height(7.dp))
                        Text(playlist.description, color = VmWhite.copy(alpha = .8f),
                            fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("${playlist.tracks.size} músicas · Playlist privada neste dispositivo",
                        color = VmMuted, fontSize = 12.sp)
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onAddSongs) {
                            Icon(Icons.Default.AddCircleOutline, "Adicionar músicas",
                                tint = VmAccent)
                        }
                        Box {
                            IconButton(onClick = { menu = true }) {
                                Icon(Icons.Default.MoreHoriz, "Editar playlist", tint = VmWhite)
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("Editar nome e descrição") },
                                    onClick = { menu = false; onRename() })
                                DropdownMenuItem(text = { Text("Eliminar playlist") },
                                    onClick = { menu = false; onDelete() })
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = onShuffle, enabled = playlist.tracks.isNotEmpty()) {
                            Icon(Icons.Default.Shuffle, "Reprodução aleatória", tint = VmAccent)
                        }
                        IconButton(onClick = { onPlay(0) },
                            enabled = playlist.tracks.isNotEmpty(),
                            modifier = Modifier.size(57.dp)
                                .background(VmAccent, RoundedCornerShape(50))) {
                            Icon(Icons.Default.PlayArrow, "Reproduzir playlist",
                                tint = VmBackground, modifier = Modifier.size(35.dp))
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
            if (playlist.tracks.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Text("Esta playlist ainda não tem músicas.",
                        color = VmWhite, fontWeight = FontWeight.Bold)
                    Text("Adiciona uma música através do menu da faixa ou da pesquisa.",
                        color = VmMuted, fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onAddSongs) { Text("Adicionar músicas") }
                }
            }
            items(playlist.tracks.size) { i ->
                val song = playlist.tracks[i]
                var songMenu by remember(playlist.id, song.id, i) { mutableStateOf(false) }
                Row(Modifier.fillMaxWidth().clickable { onPlay(i) }
                    .padding(start = 19.dp, end = 13.dp, top = 7.dp, bottom = 7.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    VmSongArt(song, Modifier.size(55.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(LyricsApi.cleanTitle(song.title, song.artist),
                            color = VmWhite, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(song.artist, color = VmMuted, fontSize = 12.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Box {
                        IconButton(onClick = { songMenu = true }) {
                            Icon(Icons.Default.MoreVert, "Opções da música", tint = VmMuted)
                        }
                        DropdownMenu(expanded = songMenu,
                            onDismissRequest = { songMenu = false }) {
                            DropdownMenuItem(text = { Text("Ver música") },
                                onClick = { songMenu = false; onOpenSong(song) })
                            DropdownMenuItem(text = { Text("Mover para cima") },
                                enabled = i > 0,
                                onClick = { songMenu = false; onMove(i, i - 1) })
                            DropdownMenuItem(text = { Text("Mover para baixo") },
                                enabled = i < playlist.tracks.lastIndex,
                                onClick = { songMenu = false; onMove(i, i + 1) })
                            DropdownMenuItem(text = { Text("Remover da playlist") },
                                onClick = { songMenu = false; onRemove(song) })
                        }
                    }
                    IconButton(onClick = { onFavorite(song) }) {
                        Icon(if (isFavorite(song)) Icons.Default.Favorite
                            else Icons.Default.FavoriteBorder,
                            "Favorito", tint = if (isFavorite(song)) VmAccent else VmMuted)
                    }
                }
            }
        }
    }
}
