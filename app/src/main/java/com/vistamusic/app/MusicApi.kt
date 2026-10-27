package com.vistamusic.app

import kotlinx.coroutines.Dispatchers
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
        connection.setRequestProperty("User-Agent", "VistaMusic/0.1")
        try {
            if (connection.responseCode !in 200..299)
                throw IllegalStateException("HTTP " + connection.responseCode)
            return JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally { connection.disconnect() }
    }
    suspend fun trending(): List<Song> = withContext(Dispatchers.IO) {
        readAudius("tracks/trending?limit=40")
    }
    suspend fun search(query: String, jamendoClientId: String, includePreviews: Boolean): List<Song> =
        withContext(Dispatchers.IO) {
            if (query.isBlank()) return@withContext emptyList()
            val all = mutableListOf<Song>()
            runCatching { readAudius("tracks/search?query=" + enc(query) + "&limit=35") }
                .onSuccess { all.addAll(it) }
            if (jamendoClientId.isNotBlank()) runCatching {
                val url = "https://api.jamendo.com/v3.0/tracks/?client_id=" +
                    enc(jamendoClientId) + "&format=json&limit=20&search=" + enc(query)
                val arr = request(url).optJSONArray("results") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val j = arr.optJSONObject(i) ?: continue
                    val audio = j.optString("audio")
                    if (audio.startsWith("https")) all.add(Song("jamendo:" + j.optString("id"),
                        j.optString("name"), j.optString("artist_name"), j.optString("image"),
                        audio, "Jamendo"))
                }
            }
            if (includePreviews) runCatching {
                val arr = request("https://itunes.apple.com/search?term=" + enc(query) +
                    "&entity=song&limit=12").optJSONArray("results") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val j = arr.optJSONObject(i) ?: continue
                    val audio = j.optString("previewUrl")
                    if (audio.startsWith("https")) all.add(Song("itunes:" + j.optString("trackId"),
                        j.optString("trackName"), j.optString("artistName"),
                        j.optString("artworkUrl100").replace("100x100bb", "600x600bb"),
                        audio, "iTunes · prévia"))
                }
            }
            all.distinctBy { it.id }
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
