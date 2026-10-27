package com.vistamusic.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class GenreCategory(
    val title: String, val query: String, val color1: Long, val color2: Long,
    val subtitle: String = ""
)
object Genres {
    // Editorial starter artists per genre (not rankings or generated popularity claims).
    fun artists(genre: String): List<String> = when (genre) {
        "Pop" -> listOf("Ariana Grande", "Sabrina Carpenter", "Dua Lipa", "Justin Bieber")
        "Hip-hop" -> listOf("Drake", "Kendrick Lamar", "Travis Scott", "J. Cole")
        "Rock" -> listOf("Linkin Park", "Foo Fighters", "Paramore", "Muse")
        "Música portuguesa" -> listOf("Plutónio", "Bárbara Bandeira", "Slow J", "D.A.M.A")
        "R&B" -> listOf("SZA", "The Weeknd", "Frank Ocean", "H.E.R.")
        "Eletrónica" -> listOf("Calvin Harris", "David Guetta", "Disclosure", "Martin Garrix")
        "K-pop" -> listOf("BLACKPINK", "BTS", "NewJeans", "TWICE")
        "Indie" -> listOf("Arctic Monkeys", "Tame Impala", "Clairo", "The 1975")
        "Latinas" -> listOf("Bad Bunny", "Karol G", "Rauw Alejandro", "Rosalía")
        "Afrobeats" -> listOf("Burna Boy", "Rema", "Tems", "Ayra Starr")
        "Sertanejo" -> listOf("Marília Mendonça", "Gusttavo Lima", "Ana Castela", "Zé Neto e Cristiano")
        "Animes" -> listOf("YOASOBI", "Aimer", "LiSA", "Eve")
        "Jazz" -> listOf("Norah Jones", "Kamasi Washington", "Diana Krall", "Miles Davis")
        "Clássica" -> listOf("Ludovico Einaudi", "Yiruma", "Max Richter", "Ólafur Arnalds")
        "Românticas" -> listOf("Ed Sheeran", "Adele", "John Legend", "Sam Smith")
        "Novidades" -> listOf("Sabrina Carpenter", "Tate McRae", "Billie Eilish", "Chappell Roan")
        else -> emptyList()
    }
    val categories = listOf(
        GenreCategory("Pop", "Ariana Grande", 0xFFC24F83, 0xFF773D88),
        GenreCategory("Hip-hop", "Drake", 0xFFB96C27, 0xFF75472F),
        GenreCategory("Rock", "Linkin Park", 0xFFB0443D, 0xFF622F50),
        GenreCategory("Música portuguesa", "Plutónio", 0xFF307A66, 0xFF174D65),
        GenreCategory("R&B", "SZA", 0xFF4B648F, 0xFF472B74),
        GenreCategory("Eletrónica", "Calvin Harris", 0xFF345A94, 0xFF393D82),
        GenreCategory("K-pop", "BLACKPINK", 0xFFC04482, 0xFF782D65),
        GenreCategory("Indie", "Arctic Monkeys", 0xFF7F6D45, 0xFF4D553C),
        GenreCategory("Latinas", "Bad Bunny", 0xFFB14B49, 0xFF864A25),
        GenreCategory("Afrobeats", "Burna Boy", 0xFF387C5C, 0xFF356B3E),
        GenreCategory("Sertanejo", "Marília Mendonça", 0xFF8E533D, 0xFF714D61),
        GenreCategory("Animes", "YOASOBI", 0xFF8255A7, 0xFF3A527D),
        GenreCategory("Jazz", "Norah Jones", 0xFF6C547B, 0xFF2C5874),
        GenreCategory("Clássica", "Ludovico Einaudi", 0xFF4F6D75, 0xFF394B5F),
        GenreCategory("Românticas", "Ed Sheeran", 0xFFC26877, 0xFF8E3D58),
        GenreCategory("Novidades", "Sabrina Carpenter", 0xFF658C43, 0xFF386E76)
    )
}
@Composable
private fun GenreCard(genre: GenreCategory, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.height(108.dp).clip(RoundedCornerShape(16.dp))
        .background(Brush.linearGradient(listOf(Color(genre.color1.toInt()), Color(genre.color2.toInt()))))
        .clickable(onClick = onClick).padding(13.dp)) {
        Icon(Icons.Default.Album, null, tint = Color.White.copy(alpha = .17f),
            modifier = Modifier.size(95.dp).align(Alignment.BottomEnd).offset(x = 20.dp, y = 20.dp))
        Text(genre.title, color = Color.White, fontWeight = FontWeight.Bold,
            fontSize = 17.sp, lineHeight = 21.sp,
            modifier = Modifier.align(Alignment.TopStart).fillMaxWidth(.88f))
    }
}
@Composable
fun VmGenreDiscovery(onChoose: (GenreCategory) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 20.dp)) {
        Text("Descobre algo novo", color = VmWhite, fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(start = 20.dp, bottom = 14.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            items(listOf(Genres.categories[3], Genres.categories[7],
                Genres.categories[14], Genres.categories[11])) { g ->
                GenreCard(g, Modifier.width(164.dp).height(120.dp)) { onChoose(g) }
            }
        }
        Spacer(Modifier.height(27.dp))
        Text("Explorar tudo", color = VmWhite, fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        Genres.categories.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { g ->
                    GenreCard(g, Modifier.weight(1f)) { onChoose(g) }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
