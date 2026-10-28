package com.vistamusic.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun VmGenrePage(genre: GenreCategory, songs: List<Song>, albums: List<AlbumInfo>,
    artistPhotos: Map<String, String>, loading: Boolean,
    onBack: () -> Unit, onArtist: (String) -> Unit, onAlbum: (AlbumInfo) -> Unit,
    onPlay: (Song) -> Unit, onSong: (Song) -> Unit,
    isFavorite: (Song) -> Boolean, onFavorite: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit = {}) {
    val tones = listOf(Color(genre.color1.toInt()), Color(genre.color2.toInt()), VmBackground)
    val featured = songs.take(12)
    Column(Modifier.fillMaxSize().background(VmBackground)) {
        VmBackBar("Explorar", onBack)
        LazyColumn(contentPadding = PaddingValues(bottom = 28.dp)) {
            item {
                Box(Modifier.fillMaxWidth().height(230.dp)
                    .background(Brush.verticalGradient(tones))
                    .padding(horizontal = 22.dp, vertical = 25.dp)) {
                    Icon(Icons.Default.Album, null,
                        modifier = Modifier.align(Alignment.BottomEnd).size(175.dp)
                            .offset(x = 32.dp, y = 34.dp),
                        tint = VmWhite.copy(alpha = .12f))
                    Column(Modifier.align(Alignment.BottomStart)) {
                        Text("VISTAMUSIC · DESCOBERTA", color = VmWhite.copy(alpha = .79f),
                            fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        Text(genre.title, color = VmWhite, fontSize = 39.sp,
                            fontWeight = FontWeight.ExtraBold, lineHeight = 42.sp)
                        Spacer(Modifier.height(5.dp))
                        Text("Explora artistas, músicas e álbuns deste universo.",
                            color = VmWhite.copy(alpha = .8f), fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("A tua seleção", color = VmWhite, fontSize = 21.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.weight(1f))
                    Button(onClick = { featured.firstOrNull()?.let(onPlay) },
                        enabled = featured.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = VmAccent)) {
                        Icon(Icons.Default.PlayArrow, null, tint = VmBackground)
                        Text("Ouvir", color = VmBackground, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            if (loading) item {
                Text("A descobrir músicas…", color = VmMuted,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp))
            }
            items(featured) { song ->
                VmSongRow(song, isFavorite(song), { onPlay(song) },
                    { onSong(song) }, { onFavorite(song) },
                    onAddToPlaylist = { onAddToPlaylist(song) })
            }
            item {
                Spacer(Modifier.height(25.dp))
                VmHeading("Artistas para descobrir",
                    Modifier.padding(horizontal = 20.dp))
                Spacer(Modifier.height(13.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(15.dp)) {
                    items(Genres.artists(genre.title)) { name ->
                        Column(Modifier.width(112.dp).clickable { onArtist(name) },
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            VmArtistAvatar(artistPhotos[name].orEmpty(), Modifier.size(102.dp))
                            Spacer(Modifier.height(9.dp))
                            Text(name, color = VmWhite, fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold, maxLines = 2,
                                overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            if (albums.isNotEmpty()) item {
                Spacer(Modifier.height(27.dp))
                VmHeading("Álbuns para explorar", Modifier.padding(horizontal = 20.dp))
                Spacer(Modifier.height(13.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                    items(albums.take(20)) { album ->
                        VmAlbumTile(album) { onAlbum(album) }
                    }
                }
            }
            if (songs.size > 12) {
                item {
                    Spacer(Modifier.height(27.dp))
                    VmHeading("Mais músicas", Modifier.padding(horizontal = 20.dp))
                    Spacer(Modifier.height(9.dp))
                }
                items(songs.drop(12)) { song ->
                    VmSongRow(song, isFavorite(song), { onPlay(song) },
                        { onSong(song) }, { onFavorite(song) },
                    onAddToPlaylist = { onAddToPlaylist(song) })
                }
            }
            if (!loading && songs.isEmpty()) item {
                Text("Não foi possível carregar músicas deste género.",
                    color = VmMuted, modifier = Modifier.padding(20.dp))
            }
        }
    }
}
