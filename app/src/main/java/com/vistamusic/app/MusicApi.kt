package com.vistamusic.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val art: String,
    val audio: String,
    val source: String = "Audius"
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("title", title); put("artist", artist)
        put("art", art); put("audio", audio); put("source", source)
    }
    companion object {
        fun fromJson(j: JSONObject) = Song(
            j.optString("id"), j.optString("title"), j.optString("artist"),
            j.optString("art"), j.optString("audio"), j.optString("source", "Audius"))
    }
}
class MusicApi {
    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")
    private fun request(uri: String): JSONObject {
        val connection = URL(uri).openConnection() as HttpURLConnection
        connection.connectTimeout = 12000
        connection.readTimeout = 12000
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "VistaMusic/0.2")
        try {
            if (connection.responseCode !in 200..299)
                throw IllegalStateException("HTTP " + connection.responseCode)
            return JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally { connection.disconnect() }
    }
    suspend fun trending(): List<Song> = withContext(Dispatchers.IO) {
        readAudius("tracks/trending?limit=40")
    }
    // Apple Music public charts: ranked music metadata (not Spotify charts). Audio
    // comes from separate experimental sources and is not guaranteed to resolve.
    suspend fun popular(market: String = "pt"): List<Song> = withContext(Dispatchers.IO) {
        val data = request(
            "https://rss.applemarketingtools.com/api/v2/" +
                (if (market in listOf("pt", "us", "br")) market else "pt") +
                "/music/most-played/70/songs.json"
        ).optJSONObject("feed")?.optJSONArray("results") ?: JSONArray()
        (0 until data.length()).mapNotNull { i ->
            val j = data.optJSONObject(i) ?: return@mapNotNull null
            val title = j.optString("name")
            val artist = j.optString("artistName")
            if (title.isBlank() || artist.isBlank()) return@mapNotNull null
            val image = j.optString("artworkUrl100")
                .replace("100x100bb", "600x600bb")
            Song("chart:" + j.optString("id"), title, artist, image,
                "discover:" + title + " " + artist + " official music",
                "Hits populares")
        }
    }

    suspend fun search(query: String, jamendoClientId: String,
        includePreviews: Boolean, youtubeApiKey: String): List<Song> =
        coroutineScope {
            if (query.isBlank()) return@coroutineScope emptyList()
            val videos = async(Dispatchers.IO) {
                runCatching { YouTubeCatalog.search(query, youtubeApiKey) }
                    .getOrDefault(emptyList())
            }
            val itunes = async(Dispatchers.IO) {
                runCatching {
                    val data = request("https://itunes.apple.com/search?term=" +
                        enc(query) + "&entity=song&limit=35&country=PT")
                        .optJSONArray("results") ?: JSONArray()
                    (0 until data.length()).mapNotNull { i ->
                        val j = data.optJSONObject(i) ?: return@mapNotNull null
                        val name = j.optString("trackName")
                        val artist = j.optString("artistName")
                        if (name.isBlank() || artist.isBlank()) return@mapNotNull null
                        val preview = j.optString("previewUrl")
                        Song("catalog:" + j.optString("trackId"),
                            name, artist,
                            j.optString("artworkUrl100").replace("100x100bb", "600x600bb"),
                            if (includePreviews && preview.startsWith("https")) preview
                            else "discover:" + name + " " + artist + " official music",
                            if (includePreviews && preview.startsWith("https")) "iTunes · prévia"
                            else "Catálogo popular")
                    }
                }.getOrDefault(emptyList())
            }
            val audius = async(Dispatchers.IO) {
                runCatching { readAudius("tracks/search?query=" + enc(query) + "&limit=25") }
                    .getOrDefault(emptyList())
            }
            val jamendo = async(Dispatchers.IO) {
                if (jamendoClientId.isBlank()) emptyList() else runCatching {
                    val url = "https://api.jamendo.com/v3.0/tracks/?client_id=" +
                        enc(jamendoClientId) + "&format=json&limit=20&search=" + enc(query)
                    val arr = request(url).optJSONArray("results") ?: JSONArray()
                    (0 until arr.length()).mapNotNull { i ->
                        val j = arr.optJSONObject(i) ?: return@mapNotNull null
                        val audio = j.optString("audio")
                        if (!audio.startsWith("https")) return@mapNotNull null
                        Song("jamendo:" + j.optString("id"), j.optString("name"),
                            j.optString("artist_name"), j.optString("image"), audio, "Jamendo")
                    }
                }.getOrDefault(emptyList())
            }
            // Mix music search and discovery, favoring tracks with playable streams.
            (videos.await() + itunes.await() + audius.await() + jamendo.await())
                .distinctBy { it.id }
        }
    private fun readAudius(endpoint: String): List<Song> {
        val array = request("https://api.audius.co/v1/" + endpoint)
            .optJSONArray("data") ?: JSONArray()
        return (0 until array.length()).mapNotNull { i ->
            val track = array.optJSONObject(i) ?: return@mapNotNull null
            val id = track.optString("id")
            val title = track.optString("title")
            if (id.isBlank() || title.isBlank()) return@mapNotNull null
            val art = track.optJSONObject("artwork")
            val cover = listOf("1000x1000", "480x480", "150x150")
                .firstNotNullOfOrNull { key -> art?.optString(key)?.takeIf { it.startsWith("https") } }
                .orEmpty()
            Song("audius:" + id, title,
                track.optJSONObject("user")?.optString("name").orEmpty().ifBlank { "Artista" },
                cover, "https://api.audius.co/v1/tracks/" + enc(id) + "/stream")
        }
    }
}
