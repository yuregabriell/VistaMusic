package com.vistamusic.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class ArtistInfo(val id: Long, val name: String, val genre: String, val url: String)
data class AlbumInfo(val id: Long, val title: String, val artist: String, val art: String,
                     val year: String, val trackCount: Int)
data class SongInfo(val album: String, val year: String, val genre: String,
                    val durationMs: Long, val artistId: Long, val albumId: Long)

class LibraryCatalog {
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private fun request(url: String): JSONArray {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 7000
        c.readTimeout = 9000
        c.setRequestProperty("User-Agent", "VistaMusic/0.4")
        try {
            if (c.responseCode !in 200..299) return JSONArray()
            return JSONObject(c.inputStream.bufferedReader().use { it.readText() })
                .optJSONArray("results") ?: JSONArray()
        } finally { c.disconnect() }
    }
    suspend fun searchAlbums(query: String): List<AlbumInfo> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        runCatching {
            val a = request("https://itunes.apple.com/search?term=${enc(query)}&entity=album&limit=40&country=PT")
            (0 until a.length()).mapNotNull { idx ->
                val j = a.optJSONObject(idx) ?: return@mapNotNull null
                if (j.optString("collectionType") != "Album") return@mapNotNull null
                AlbumInfo(j.optLong("collectionId"), j.optString("collectionName"),
                    j.optString("artistName"),
                    j.optString("artworkUrl100").replace("100x100bb", "600x600bb"),
                    j.optString("releaseDate").take(4), j.optInt("trackCount"))
            }.filter { it.id > 0L }.distinctBy { it.id }
        }.getOrDefault(emptyList())
    }
    suspend fun topArtistSongs(name: String, allSongs: List<Song>): List<Song> =
        withContext(Dispatchers.IO) {
            // TheAudioDB top 10 is a chart, unlike iTunes lookup's unsorted discography.
            val chart = runCatching {
                val link = "https://www.theaudiodb.com/api/v1/json/123/track-top10.php?s=${enc(name)}"
                val conn = URL(link).openConnection() as HttpURLConnection
                conn.connectTimeout = 6000; conn.readTimeout = 7000
                try {
                    if (conn.responseCode !in 200..299) emptyList<String>()
                    else {
                        val arr = JSONObject(conn.inputStream.bufferedReader().use {
                            it.readText()
                        }).optJSONArray("track")
                        (0 until (arr?.length() ?: 0)).mapNotNull { i ->
                            arr?.optJSONObject(i)?.optString("strTrack")
                                ?.takeIf { it.isNotBlank() }
                        }
                    }
                } finally { conn.disconnect() }
            }.getOrDefault(emptyList())
            fun normalize(s: String): String = s.lowercase().replace(
                Regex("[^\\p{L}\\p{N}]+"), " ").trim()
            val ordered = chart.mapNotNull { title ->
                allSongs.firstOrNull { normalize(it.title) == normalize(title) }
                    ?: allSongs.firstOrNull { normalize(it.title).startsWith(normalize(title)) }
            }.distinctBy { it.id }
            // Ranking only contains matches grounded to official chart. If empty show discography
            // as 'Em destaque' rather than mislabeling it as ranked top.
            ordered
        }
    suspend fun artist(name: String): ArtistInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val found = request("https://itunes.apple.com/search?term=${enc(name)}&media=music&entity=musicArtist&limit=25&country=PT")
            val matches = (0 until found.length()).mapNotNull { found.optJSONObject(it) }
            val best = matches.firstOrNull { it.optString("artistName").equals(name, true) }
                ?: matches.firstOrNull { it.optString("artistName").contains(name, true) }
            best?.let { ArtistInfo(it.optLong("artistId"), it.optString("artistName"),
                it.optString("primaryGenreName"), it.optString("artistLinkUrl")) }
        }.getOrNull()
    }
    suspend fun artistSongs(name: String, id: Long): List<Song> = withContext(Dispatchers.IO) {
        runCatching {
            val query = if (id > 0) "https://itunes.apple.com/lookup?id=$id&entity=song&limit=200&country=PT"
                else "https://itunes.apple.com/search?term=${enc(name)}&entity=song&limit=75&country=PT"
            val a = request(query)
            (0 until a.length()).mapNotNull { idx ->
                val j = a.optJSONObject(idx) ?: return@mapNotNull null
                if (j.optString("wrapperType") != "track" || j.optString("kind") != "song")
                    return@mapNotNull null
                if (!j.optString("artistName").contains(name, true)) return@mapNotNull null
                val title = j.optString("trackName")
                Song("catalog:" + j.optString("trackId"), title,
                    j.optString("artistName"),
                    j.optString("artworkUrl100").replace("100x100bb", "600x600bb"),
                    "discover:" + title + " " + j.optString("artistName") + " official audio",
                    "Catálogo")
            }.distinctBy { it.id }
        }.getOrDefault(emptyList())
    }
    suspend fun artistAlbums(name: String, id: Long): List<AlbumInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val url = if (id > 0) "https://itunes.apple.com/lookup?id=$id&entity=album&limit=200&country=PT"
                else "https://itunes.apple.com/search?term=${enc(name)}&entity=album&limit=80&country=PT"
            val a = request(url)
            (0 until a.length()).mapNotNull { i ->
                val j = a.optJSONObject(i) ?: return@mapNotNull null
                if (j.optString("collectionType") != "Album" ||
                    !j.optString("artistName").contains(name, true)) return@mapNotNull null
                AlbumInfo(j.optLong("collectionId"), j.optString("collectionName"),
                    j.optString("artistName"),
                    j.optString("artworkUrl100").replace("100x100bb", "600x600bb"),
                    j.optString("releaseDate").take(4), j.optInt("trackCount"))
            }.distinctBy { it.id }
        }.getOrDefault(emptyList())
    }
    suspend fun albumSongs(album: AlbumInfo): List<Song> = withContext(Dispatchers.IO) {
        runCatching {
            val a = request("https://itunes.apple.com/lookup?id=${album.id}&entity=song&limit=200&country=PT")
            (0 until a.length()).mapNotNull { i ->
                val j = a.optJSONObject(i) ?: return@mapNotNull null
                if (j.optString("wrapperType") != "track" || j.optString("kind") != "song")
                    return@mapNotNull null
                val title = j.optString("trackName")
                Song("catalog:" + j.optString("trackId"), title, j.optString("artistName"),
                    album.art, "discover:" + title + " " + j.optString("artistName") + " official audio",
                    "Catálogo")
            }
        }.getOrDefault(emptyList())
    }
    suspend fun songInfo(song: Song): SongInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val clean = LyricsApi.cleanTitle(song.title, song.artist)
            val a = request("https://itunes.apple.com/search?term=${enc(clean + " " + song.artist)}&entity=song&limit=20&country=PT")
            val options = (0 until a.length()).mapNotNull { a.optJSONObject(it) }
            val j = options.firstOrNull { it.optString("trackName").equals(clean, true) &&
                it.optString("artistName").contains(song.artist, true) }
                ?: options.firstOrNull { it.optString("trackName").contains(clean, true) }
                ?: return@runCatching null
            SongInfo(j.optString("collectionName"),
                j.optString("releaseDate").take(4), j.optString("primaryGenreName"),
                j.optLong("trackTimeMillis"), j.optLong("artistId"), j.optLong("collectionId"))
        }.getOrNull()
    }
}
