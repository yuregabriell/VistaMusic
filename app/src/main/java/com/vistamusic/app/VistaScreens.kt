package com.vistamusic.app

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import coil.compose.AsyncImage

val VmBackground = Color(0xFF090B0E)
val VmCard = Color(0xFF191D21)
val VmMuted = Color(0xFFA1A5A9)
val VmWhite = Color(0xFFF7F8F8)
val VmAccent = Color(0xFFFF4FA3)
val VmDarkGreen = Color(0xFF5A2043)

@Composable
fun VmArt(url: String, modifier: Modifier = Modifier, circular: Boolean = false) {
    val shape = if (circular) CircleShape else RoundedCornerShape(14.dp)
    Box(modifier.clip(shape).background(Color(0xFF293433)), contentAlignment = Alignment.Center) {
        Icon(Icons.Default.MusicNote, contentDescription = null,
            tint = VmAccent, modifier = Modifier.size(32.dp))
        if (url.isNotBlank()) AsyncImage(model = url, contentDescription = null,
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

@Composable
fun VmSongArt(song: Song, modifier: Modifier = Modifier) {
    val primary by produceState(initialValue = "",
        key1 = song.id, key2 = song.title, key3 = song.artist) {
        value = AlbumArtwork.resolve(song)
    }
    var replacement by remember(song.id, song.title, song.artist) {
        mutableStateOf<String?>(null)
    }
    val artwork = replacement ?: primary
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    if (artwork.isNotBlank()) {
        Box(modifier.clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF293433)), contentAlignment = Alignment.Center) {
            AsyncImage(
                model = remember(artwork, context) {
                    ImageRequest.Builder(context).data(artwork)
                        .crossfade(180).build()
                },
                contentDescription = "Capa de ${song.title}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onError = {
                    if (replacement == null) {
                        scope.launch {
                            val other = runCatching {
                                AlbumArtwork.alternate(song)
                            }.getOrDefault("")
                            replacement = other.takeIf { it.isNotBlank() && it != artwork } ?: ""
                        }
                    } else {
                        replacement = ""
                    }
                }
            )
        }
    } else {
        VmNeutralArtwork(song, modifier)
    }
}

@Composable
private fun VmNeutralArtwork(song: Song, modifier: Modifier = Modifier) {
    val shades = listOf(
        listOf(Color(0xFF8D3E68), Color(0xFF34233D)),
        listOf(Color(0xFF574A77), Color(0xFF242C42)),
        listOf(Color(0xFF87565A), Color(0xFF44324A)),
        listOf(Color(0xFF78405E), Color(0xFF36233E))
    )
    val colors = shades[(kotlin.math.abs(song.id.hashCode().toLong()) %
        shades.size).toInt()]
    BoxWithConstraints(
        modifier.clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(colors)),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.DiscFull, null,
            tint = VmWhite.copy(alpha = .27f),
            modifier = Modifier.fillMaxSize(.65f))
        if (maxWidth >= 100.dp) {
            Column(Modifier.align(Alignment.BottomStart)
                .fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text(LyricsApi.cleanTitle(song.title, song.artist),
                    color = VmWhite, fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.sp, maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
                Text(song.artist, color = VmWhite.copy(alpha = .79f),
                    fontSize = 10.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun VmHeading(text: String, modifier: Modifier = Modifier) {
    Text(text, color = VmWhite, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
        modifier = modifier)
}

@Composable
fun VmSongRow(song: Song, isFavorite: Boolean, onPlay: () -> Unit,
              onOpen: () -> Unit, onFavorite: () -> Unit,
              index: Int? = null, onAddToPlaylist: (() -> Unit)? = null) {
    var menu by remember(song.id) { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clickable(onClick = onPlay)
            .padding(horizontal = 18.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (index != null) {
            Text("$index", color = VmMuted, fontSize = 14.sp,
                modifier = Modifier.width(24.dp), textAlign = TextAlign.Center)
        }
        VmSongArt(song, Modifier.size(54.dp).clickable(onClick = onOpen))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(LyricsApi.cleanTitle(song.title, song.artist),
                color = VmWhite, fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            Text(song.artist, color = VmMuted, fontSize = 12.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
        Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Default.MoreHoriz, "Opções da música", tint = VmMuted)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Ver música") },
                    onClick = { menu = false; onOpen() })
                if (onAddToPlaylist != null) {
                    DropdownMenuItem(text = { Text("Adicionar à playlist") },
                        onClick = { menu = false; onAddToPlaylist() })
                }
            }
        }
        IconButton(onClick = onFavorite, modifier = Modifier.size(38.dp)) {
            Icon(if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                "Favorito", tint = if (isFavorite) VmAccent else VmMuted,
                modifier = Modifier.size(21.dp))
        }
    }
}

@Composable
fun VmAlbumTile(album: AlbumInfo, onClick: () -> Unit) {
    Column(Modifier.width(144.dp).clickable(onClick = onClick)) {
        VmArt(album.art, Modifier.size(144.dp))
        Spacer(Modifier.height(9.dp))
        Text(album.title, color = VmWhite, fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(album.year + " · " + album.artist, color = VmMuted, fontSize = 11.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun VmBackBar(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(62.dp).padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, "Voltar", tint = VmWhite)
        }
        Spacer(Modifier.width(5.dp))
        Text(title, color = VmWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun VmArtistPage(
    name: String, info: ArtistInfo?, bio: ArtistBio?, songs: List<Song>,
    topSongs: List<Song>, isTopSampled: Boolean, albums: List<AlbumInfo>,
    loading: Boolean, followed: Boolean, onFollow: () -> Unit, onBack: () -> Unit,
    onPlay: (Song) -> Unit, onSong: (Song) -> Unit,
    onAlbum: (AlbumInfo) -> Unit, onAllSongs: () -> Unit,
    isFavorite: (Song) -> Boolean, onFavorite: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit = {}
) {
    val realTop = isTopSampled && topSongs.size >= 5
    val highlighted = topSongs.take(10).ifEmpty { songs.take(10) }
    Column(Modifier.fillMaxSize().background(VmBackground)) {
        VmBackBar("Artista", onBack)
        LazyColumn(contentPadding = PaddingValues(bottom = 30.dp)) {
            item {
                val portrait = bio?.photo.orEmpty()
                val header = bio?.banner?.takeIf { it.isNotBlank() } ?: portrait
                Box(Modifier.fillMaxWidth().height(280.dp).background(
                    Brush.verticalGradient(listOf(Color(0xFF6B2E54), VmBackground)))) {
                    if (header.isNotBlank()) {
                        AsyncImage(model = header, contentDescription = name,
                            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                        listOf(Color(0x44090B0E), Color(0x11090B0E), VmBackground))))
                    Text(name, color = VmWhite, fontSize = 35.sp,
                        fontWeight = FontWeight.ExtraBold, lineHeight = 40.sp,
                        modifier = Modifier.align(Alignment.BottomStart).padding(
                            start = 22.dp, end = 18.dp, bottom = 22.dp))
                }
                Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    if (!info?.genre.isNullOrBlank()) {
                        Text(info?.genre.orEmpty(), color = VmMuted, fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(18.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = onFollow, shape = RoundedCornerShape(50)) {
                            Text(if (followed) "A seguir" else "Seguir")
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { highlighted.firstOrNull()?.let(onPlay) },
                            enabled = highlighted.isNotEmpty(),
                            modifier = Modifier.size(59.dp).background(VmAccent, CircleShape)) {
                            Icon(Icons.Default.PlayArrow, "Reproduzir artista",
                                tint = VmBackground, modifier = Modifier.size(33.dp))
                        }
                    }
                }
            }
            if (loading) item {
                Text("A carregar discografia…", color = VmMuted, modifier = Modifier.padding(22.dp))
            }
            if (highlighted.isNotEmpty()) {
                item {
                    VmHeading(if (realTop) "Mais vistas no YouTube" else "Músicas em destaque",
                        Modifier.padding(start = 20.dp, top = 20.dp, bottom = 10.dp))
                    if (realTop) Text("Seleção por visualizações dos vídeos encontrados",
                        color = VmMuted, fontSize = 11.sp,
                        modifier = Modifier.padding(start = 20.dp, bottom = 6.dp))
                }
                items(highlighted.size) { index ->
                    val song = highlighted[index]
                    VmSongRow(song, isFavorite(song), { onPlay(song) },
                        { onSong(song) }, { onFavorite(song) }, if (realTop) index + 1 else null,
                        onAddToPlaylist = { onAddToPlaylist(song) })
                }
            }
            if (albums.isNotEmpty()) {
                item {
                    VmHeading("Álbuns e singles", Modifier.padding(start = 20.dp, top = 26.dp, bottom = 14.dp))
                }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(albums) { album -> VmAlbumTile(album) { onAlbum(album) } }
                    }
                }
            }
            if (songs.isNotEmpty()) {
                item {
                    OutlinedButton(onClick = onAllSongs,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
                        shape = RoundedCornerShape(18.dp)) {
                        Text("Ver todas as músicas de $name")
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Default.ChevronRight, null)
                    }
                }
            }
            if (bio != null && bio.summary.isNotBlank()) item {
                VmHeading("Sobre o artista", Modifier.padding(start = 20.dp, top = 19.dp))
                Column(Modifier.fillMaxWidth().padding(18.dp)
                    .clip(RoundedCornerShape(22.dp)).background(VmCard).padding(19.dp)) {
                    Text(bio.summary, color = VmWhite.copy(alpha = .86f),
                        fontSize = 14.sp, lineHeight = 21.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("Fonte: Wikipédia", color = VmMuted, fontSize = 11.sp)
                }
            }
            if (!loading && songs.isEmpty() && albums.isEmpty()) item {
                Text("Não há lançamentos disponíveis neste catálogo.",
                    color = VmMuted, modifier = Modifier.padding(20.dp))
            }
        }
    }
}

@Composable
fun VmAllArtistSongsPage(name: String, songs: List<Song>,
    albums: List<AlbumInfo>, onBack: () -> Unit,
    onPlay: (Song) -> Unit, onPlayAlbum: (List<Song>, Int) -> Unit,
    onLoadAlbum: suspend (AlbumInfo) -> List<Song>,
    onSong: (Song) -> Unit, isFavorite: (Song) -> Boolean,
    onFavorite: (Song) -> Unit, onAddToPlaylist: (Song) -> Unit = {}) {

    val scope = rememberCoroutineScope()
    val tracks = remember(name) { mutableStateMapOf<Long, List<Song>>() }
    val loading = remember(name) { mutableStateMapOf<Long, Boolean>() }
    var expandedAlbum by remember(name) { mutableStateOf<Long?>(null) }
    var showIndividual by remember(name) { mutableStateOf(false) }
    val grouped = remember(albums) {
        albums.distinctBy { it.id }
            .sortedWith(compareByDescending<AlbumInfo> { it.year }.thenBy { it.title })
            .groupBy { it.year.takeIf { y -> y.length == 4 } ?: "Ano desconhecido" }
    }
    Column(Modifier.fillMaxSize().background(VmBackground)) {
        VmBackBar(name, onBack)
        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                VmHeading("Discografia por álbum e ano",
                    Modifier.padding(start = 20.dp, top = 16.dp, bottom = 9.dp))
                Text("${albums.size} álbuns e singles no catálogo. Abre um álbum para ver as faixas.",
                    color = VmMuted, fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 5.dp))
                Text("A disponibilidade varia por país e serviço; o catálogo pode não incluir todas as gravações.",
                    color = VmMuted, fontSize = 11.sp,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 18.dp))
            }
            grouped.forEach { (year, list) ->
                item(key = "year_$year") {
                    Text(year, color = VmWhite, fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 10.dp))
                }
                items(list, key = { "album_${it.id}" }) { album ->
                    Column {
                        Row(Modifier.fillMaxWidth().clickable {
                            if (expandedAlbum == album.id) {
                                expandedAlbum = null
                            } else {
                                expandedAlbum = album.id
                                if (!tracks.containsKey(album.id) && loading[album.id] != true) {
                                    loading[album.id] = true
                                    scope.launch {
                                        val result = runCatching { onLoadAlbum(album) }
                                            .getOrDefault(emptyList())
                                        tracks[album.id] = result
                                        loading[album.id] = false
                                    }
                                }
                            }
                        }.padding(horizontal = 20.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            VmArt(album.art, Modifier.size(72.dp))
                            Spacer(Modifier.width(13.dp))
                            Column(Modifier.weight(1f)) {
                                Text(album.title, color = VmWhite,
                                    fontSize = 15.sp, fontWeight = FontWeight.Bold,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text("${album.year.ifBlank { "—" }} · ${album.trackCount} faixas",
                                    color = VmMuted, fontSize = 12.sp)
                            }
                            Icon(if (expandedAlbum == album.id) Icons.Default.ExpandLess
                                else Icons.Default.ExpandMore, null, tint = VmAccent)
                        }
                        if (expandedAlbum == album.id) {
                            if (loading[album.id] == true) {
                                Text("A carregar faixas…", color = VmMuted,
                                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp))
                            } else {
                                val found = tracks[album.id].orEmpty()
                                if (found.isEmpty()) {
                                    Text("Sem faixas disponíveis neste catálogo.",
                                        color = VmMuted,
                                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp))
                                }
                                found.forEachIndexed { i, song ->
                                    VmSongRow(song, isFavorite(song),
                                        { onPlayAlbum(found, i) }, { onSong(song) },
                                        { onFavorite(song) }, i + 1,
                                        onAddToPlaylist = { onAddToPlaylist(song) })
                                }
                            }
                        }
                    }
                }
            }
            item(key = "index") {
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth().clickable { showIndividual = !showIndividual }
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Lista de faixas identificadas", color = VmWhite,
                            fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("${songs.size} resultados na pesquisa direta (não é o total da discografia)",
                            color = VmMuted, fontSize = 12.sp)
                    }
                    Icon(if (showIndividual) Icons.Default.ExpandLess
                         else Icons.Default.ExpandMore, null, tint = VmAccent)
                }
            }
            if (showIndividual || albums.isEmpty()) {
                items(songs, key = { "track_${it.id}" }) { song ->
                    VmSongRow(song, isFavorite(song), { onPlay(song) }, { onSong(song) },
                        { onFavorite(song) }, onAddToPlaylist = { onAddToPlaylist(song) })
                }
            }
        }
    }
}

@Composable
fun VmArtistAvatar(url: String, modifier: Modifier = Modifier) {
    Box(modifier.clip(CircleShape).background(Color(0xFF29473F)),
        contentAlignment = Alignment.Center) {
        Icon(Icons.Default.Person, "Artista", tint = VmAccent,
            modifier = Modifier.size(33.dp))
        if (url.isNotBlank()) AsyncImage(model = url, contentDescription = "Fotografia do artista",
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

@Composable
fun VmAlbumPage(album: AlbumInfo, songs: List<Song>, loading: Boolean,
                onBack: () -> Unit, onArtist: (String) -> Unit,
                onPlay: (Song) -> Unit, onSong: (Song) -> Unit,
                isFavorite: (Song) -> Boolean, onFavorite: (Song) -> Unit,
                onAddToPlaylist: (Song) -> Unit = {}) {
    Column(Modifier.fillMaxSize().background(VmBackground)) {
        VmBackBar("Álbum", onBack)
        LazyColumn(contentPadding = PaddingValues(bottom = 26.dp)) {
            item {
                Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(
                    listOf(Color(0xFF593049), VmBackground))).padding(bottom = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(18.dp))
                    VmArt(album.art, Modifier.size(230.dp))
                    Spacer(Modifier.height(18.dp))
                    Text(album.title, color = VmWhite, fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 18.dp))
                    Text(album.artist, color = VmAccent, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { onArtist(album.artist) }
                            .padding(vertical = 9.dp))
                    Text("${album.year} · ${album.trackCount} músicas",
                        color = VmMuted, fontSize = 12.sp)
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End) {
                    Button(onClick = { songs.firstOrNull()?.let(onPlay) },
                        enabled = songs.isNotEmpty(),
                        shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = VmAccent)) {
                        Icon(Icons.Default.PlayArrow, "Reproduzir",
                            tint = VmBackground, modifier = Modifier.size(28.dp))
                    }
                }
            }
            if (loading) item {
                Text("A carregar faixas…", color = VmMuted, modifier = Modifier.padding(20.dp))
            }
            items(songs.size) { i ->
                val song = songs[i]
                VmSongRow(song, isFavorite(song), { onPlay(song) },
                    { onSong(song) }, { onFavorite(song) }, i + 1,
                    onAddToPlaylist = { onAddToPlaylist(song) })
            }
            if (!loading && songs.isEmpty()) item {
                Text("Não foi possível carregar as músicas deste álbum.",
                    color = VmMuted, modifier = Modifier.padding(20.dp))
            }
        }
    }
}

@Composable
fun VmTrackPage(song: Song, info: SongInfo?, lyrics: LyricSheet?,
    onBack: () -> Unit, onPlay: () -> Unit,
    onArtist: (String) -> Unit, onAlbum: () -> Unit,
    onLyrics: () -> Unit, isFavorite: Boolean, onFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit = {}) {
    Column(Modifier.fillMaxSize().background(VmBackground)) {
        VmBackBar("Sobre a música", onBack)
        LazyColumn(contentPadding = PaddingValues(bottom = 26.dp)) {
            item {
                Box(Modifier.fillMaxWidth().height(292.dp).background(Brush.verticalGradient(
                    listOf(Color(0xFF663049), VmBackground))), contentAlignment = Alignment.Center) {
                    VmSongArt(song, Modifier.size(236.dp))
                }
                Column(Modifier.padding(horizontal = 22.dp)) {
                    Text(LyricsApi.cleanTitle(song.title, song.artist),
                        color = VmWhite, fontSize = 29.sp, fontWeight = FontWeight.ExtraBold,
                        lineHeight = 33.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(song.artist, color = VmAccent, fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable { onArtist(song.artist) })
                    Spacer(Modifier.height(18.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onFavorite) {
                            Icon(if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                "Favorito", tint = if (isFavorite) VmAccent else VmMuted)
                        }
                        IconButton(onClick = onAddToPlaylist) {
                            Icon(Icons.Default.PlaylistAdd, "Adicionar à playlist", tint = VmAccent)
                        }
                        Spacer(Modifier.weight(1f))
                        Button(onClick = onPlay, colors = ButtonDefaults.buttonColors(
                            containerColor = VmAccent), shape = CircleShape) {
                            Icon(Icons.Default.PlayArrow, "Reproduzir", tint = VmBackground)
                            Text("Ouvir", color = VmBackground, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.height(22.dp))
                VmHeading("Informações", Modifier.padding(horizontal = 22.dp))
                Column(Modifier.fillMaxWidth().padding(18.dp)
                    .clip(RoundedCornerShape(19.dp)).background(VmCard).padding(19.dp)) {
                    Text("Artista", color = VmMuted, fontSize = 12.sp)
                    Text(song.artist, color = VmWhite, fontSize = 16.sp,
                        modifier = Modifier.clickable { onArtist(song.artist) })
                    if (!info?.album.isNullOrBlank()) {
                        HorizontalDivider(Modifier.padding(vertical = 14.dp), color = Color(0xFF303539))
                        Text("Álbum", color = VmMuted, fontSize = 12.sp)
                        Text(info?.album.orEmpty(), color = VmWhite, fontSize = 16.sp,
                            modifier = Modifier.clickable(onClick = onAlbum))
                    }
                    if (!info?.year.isNullOrBlank()) {
                        Spacer(Modifier.height(13.dp))
                        Text("Lançamento: ${info?.year} · ${info?.genre.orEmpty()}",
                            color = VmMuted, fontSize = 12.sp)
                    }
                }
            }
            item {
                if (lyrics?.found == true) {
                    Column(Modifier.fillMaxWidth().padding(18.dp)
                        .clip(RoundedCornerShape(22.dp)).background(Color(0xFFAD638B))
                        .clickable(onClick = onLyrics).padding(21.dp)) {
                        Text("Letra", fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, color = VmBackground)
                        Spacer(Modifier.height(15.dp))
                        lyrics.lines.take(4).forEach { line ->
                            Text(line.text, color = VmBackground, fontSize = 17.sp,
                                fontWeight = FontWeight.Bold, lineHeight = 24.sp,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.height(17.dp))
                        Text("Ver letra completa", color = VmBackground, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Text("A letra não está disponível para esta faixa.",
                        color = VmMuted, modifier = Modifier.padding(22.dp))
                }
            }
        }
    }
}

@Composable
fun VmMiniPlayer(song: Song, playing: Boolean, loading: Boolean,
    position: Long, duration: Long, buffered: Long,
    onOpen: () -> Unit, onPrevious: () -> Unit,
    onPlayPause: () -> Unit, onNext: () -> Unit) {
    val progress = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val buffer = if (duration > 0) (buffered.toFloat() / duration).coerceIn(0f, 1f) else 0f
    Column(Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 4.dp)
        .clip(RoundedCornerShape(18.dp)).background(VmCard)) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            VmSongArt(song, Modifier.size(44.dp))
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(LyricsApi.cleanTitle(song.title, song.artist), color = VmWhite,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp,
                    fontWeight = FontWeight.Bold)
                Text(song.artist, color = VmMuted, maxLines = 1, fontSize = 11.sp)
            }
            IconButton(onClick = onPrevious, modifier = Modifier.size(33.dp)) {
                Icon(Icons.Default.SkipPrevious, "Anterior", tint = VmWhite,
                    modifier = Modifier.size(27.dp))
            }
            IconButton(onClick = onPlayPause, modifier = Modifier.size(38.dp),
                enabled = !loading) {
                if (loading) CircularProgressIndicator(Modifier.size(21.dp), color = VmAccent,
                    strokeWidth = 2.dp)
                else Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    "Reproduzir ou pausar", tint = VmWhite, modifier = Modifier.size(27.dp))
            }
            IconButton(onClick = onNext, modifier = Modifier.size(33.dp)) {
                Icon(Icons.Default.SkipNext, "Próxima", tint = VmAccent,
                    modifier = Modifier.size(27.dp))
            }
        }
        Box(Modifier.fillMaxWidth().height(3.dp).background(Color(0xFF41474A))) {
            Box(Modifier.fillMaxWidth(buffer).fillMaxHeight()
                .background(Color(0xFF888F90)))
            Box(Modifier.fillMaxWidth(progress).fillMaxHeight()
                .background(VmAccent))
        }
    }
}

@Composable
private fun clock(ms: Long): String {
    if (ms <= 0) return "0:00"
    val seconds = ms / 1000
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

@Composable
fun VmPlayerPage(song: Song, position: Long, duration: Long, playing: Boolean,
    loading: Boolean, sheet: LyricSheet?, favorite: Boolean, repeat: Boolean,
    shuffle: Boolean, onBack: () -> Unit, onPlayPause: () -> Unit,
    onPrev: () -> Unit, onNext: () -> Unit, onSeek: (Long) -> Unit,
    onLyrics: () -> Unit, onArtist: () -> Unit, onSong: () -> Unit,
    onFavorite: () -> Unit, onShuffle: () -> Unit, onRepeat: () -> Unit,
    onAddToPlaylist: () -> Unit = {}) {
    val tone = remember(song.art) {
        listOf(Color(0xFF653048), Color(0xFF773A66), Color(0xFF6C354D),
            Color(0xFF53315A))[kotlin.math.abs(song.art.hashCode() % 4)]
    }
    Column(Modifier.fillMaxSize().background(
        Brush.verticalGradient(listOf(tone, VmBackground, VmBackground)))) {
        Row(Modifier.fillMaxWidth().padding(top = 10.dp, start = 12.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.KeyboardArrowDown, "Fechar player", tint = VmWhite,
                    modifier = Modifier.size(32.dp))
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("A REPRODUZIR", color = VmWhite, fontSize = 10.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text("VistaMusic", color = VmMuted, fontSize = 12.sp)
            }
            IconButton(onClick = onSong) {
                Icon(Icons.Default.MoreHoriz, "Sobre a música", tint = VmWhite)
            }
        }
        LazyColumn(contentPadding = PaddingValues(horizontal = 23.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            item {
                Spacer(Modifier.height(14.dp))
                VmSongArt(song, Modifier.fillMaxWidth().widthIn(max = 335.dp)
                    .aspectRatio(1f))
                Spacer(Modifier.height(30.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(LyricsApi.cleanTitle(song.title, song.artist),
                            color = VmWhite, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(4.dp))
                        Text(song.artist, color = VmWhite.copy(alpha = .75f), fontSize = 15.sp,
                            modifier = Modifier.clickable(onClick = onArtist))
                    }
                    IconButton(onClick = onAddToPlaylist) {
                        Icon(Icons.Default.PlaylistAdd, "Adicionar à playlist",
                            tint = VmAccent)
                    }
                    IconButton(onClick = onFavorite) {
                        Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            "Favorito", tint = if (favorite) VmAccent else VmWhite)
                    }
                }
                Spacer(Modifier.height(17.dp))
                Slider(value = if (duration > 0) position.toFloat().div(duration).coerceIn(0f, 1f) else 0f,
                    onValueChange = { if (duration > 0) onSeek((it * duration).toLong()) },
                    enabled = duration > 0,
                    colors = SliderDefaults.colors(thumbColor = VmWhite, activeTrackColor = VmWhite,
                        inactiveTrackColor = VmMuted.copy(alpha = .4f)),
                    modifier = Modifier.fillMaxWidth().height(29.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(clock(position), color = VmWhite.copy(alpha = .7f), fontSize = 11.sp)
                    Text(if (duration > 0) clock(duration) else "–:––",
                        color = VmWhite.copy(alpha = .7f), fontSize = 11.sp)
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    IconButton(onClick = onShuffle) {
                        Icon(Icons.Default.Shuffle, "Aleatório",
                            tint = if (shuffle) VmAccent else VmWhite)
                    }
                    IconButton(onClick = onPrev) {
                        Icon(Icons.Default.SkipPrevious, "Anterior", tint = VmWhite,
                            modifier = Modifier.size(33.dp))
                    }
                    IconButton(onClick = onPlayPause, enabled = !loading,
                        modifier = Modifier.size(69.dp).background(VmWhite, CircleShape)) {
                        if (loading) CircularProgressIndicator(Modifier.size(30.dp),
                            color = VmBackground, strokeWidth = 3.dp)
                        else Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                            "Reproduzir ou pausar", tint = VmBackground,
                            modifier = Modifier.size(39.dp))
                    }
                    IconButton(onClick = onNext) {
                        Icon(Icons.Default.SkipNext, "Próxima", tint = VmWhite,
                            modifier = Modifier.size(33.dp))
                    }
                    IconButton(onClick = onRepeat) {
                        Icon(Icons.Default.Repeat, "Repetir",
                            tint = if (repeat) VmAccent else VmWhite)
                    }
                }
                Spacer(Modifier.height(22.dp))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                    .background(tone.copy(alpha = .82f))
                    .clickable(onClick = onLyrics).padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Letra", color = VmWhite, fontWeight = FontWeight.Bold,
                            fontSize = 18.sp, modifier = Modifier.weight(1f))
                        Icon(Icons.Default.OpenInFull, "Expandir letra", tint = VmWhite)
                    }
                    Spacer(Modifier.height(13.dp))
                    val currentIdx = if (sheet?.synchronized == true)
                        sheet.lines.indexOfLast { it.timeMs <= position }.coerceAtLeast(0) else 0
                    val lines = sheet?.lines.orEmpty()
                    if (lines.isEmpty()) {
                        Text("Letra indisponível. Toca para tentar novamente.",
                            color = VmWhite.copy(alpha = .7f), fontSize = 13.sp)
                    } else {
                        lines.drop(if (currentIdx > 1) currentIdx - 1 else 0)
                            .take(4).forEachIndexed { idx, line ->
                                val active = idx == (if (currentIdx > 1) 1 else currentIdx)
                                Text(line.text, color = VmWhite.copy(alpha = if (active) 1f else .58f),
                                    fontSize = 19.sp, lineHeight = 25.sp,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                    modifier = Modifier.padding(vertical = 3.dp))
                            }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Expandir", color = VmWhite, fontSize = 12.sp,
                        fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(22.dp))
            }
        }
    }
}

@Composable
fun VmLyricsPage(song: Song, sheet: LyricSheet?, position: Long,
    playing: Boolean, onBack: () -> Unit, onPlayPause: () -> Unit) {
    val color = Color(0xFF888D8A)
    Column(Modifier.fillMaxSize().background(color)) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, top = 9.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.KeyboardArrowDown, "Voltar", tint = VmWhite,
                    modifier = Modifier.size(31.dp))
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(LyricsApi.cleanTitle(song.title, song.artist), color = VmWhite,
                    maxLines = 1, fontWeight = FontWeight.Bold)
                Text(song.artist, color = VmWhite.copy(alpha = .75f), fontSize = 12.sp)
            }
        }
        val index = if (sheet?.synchronized == true)
            sheet.lines.indexOfLast { it.timeMs <= position }.coerceAtLeast(0) else -1
        val lyricsScroll = androidx.compose.foundation.lazy.rememberLazyListState()
        LaunchedEffect(index) {
            if (index >= 0 && sheet?.synchronized == true) {
                lyricsScroll.animateScrollToItem((index - 2).coerceAtLeast(0))
            }
        }
        LazyColumn(Modifier.weight(1f).padding(horizontal = 25.dp),
            state = lyricsScroll,
            contentPadding = PaddingValues(top = 30.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(15.dp)) {
            if (sheet?.lines.isNullOrEmpty()) item {
                Text("Não encontrámos uma letra para esta música.",
                    fontSize = 23.sp, fontWeight = FontWeight.Bold, color = VmWhite)
            }
            items(sheet?.lines.orEmpty().size) { i ->
                val line = sheet!!.lines[i]
                Text(line.text, color = VmWhite.copy(alpha = if (index < 0 || i == index) 1f else .48f),
                    fontSize = 24.sp, lineHeight = 30.sp,
                    fontWeight = FontWeight.ExtraBold)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 25.dp, vertical = 19.dp),
            verticalAlignment = Alignment.CenterVertically) {
            VmSongArt(song, Modifier.size(48.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(LyricsApi.cleanTitle(song.title, song.artist), color = VmWhite,
                    maxLines = 1, fontWeight = FontWeight.Bold)
                Text(song.artist, color = VmWhite.copy(alpha = .7f), fontSize = 12.sp)
            }
            IconButton(onClick = onPlayPause,
                modifier = Modifier.size(54.dp).background(VmWhite, CircleShape)) {
                Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Pausar",
                    tint = VmBackground)
            }
        }
        Text("Letras: LRCLIB · Disponibilidade variável", color = VmWhite.copy(alpha = .6f),
            fontSize = 10.sp, modifier = Modifier.align(Alignment.CenterHorizontally)
                .padding(bottom = 12.dp))
    }
}
