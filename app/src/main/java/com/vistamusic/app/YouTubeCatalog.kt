package com.vistamusic.app

import android.text.Html
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * YouTube provides broad popular-music coverage through its visible official embed player.
 * The YouTube Data API is preferred when the user configures a key.
 * Public Invidious APIs are an EXPERIMENTAL metadata-only fallback; streams are NEVER
 * downloaded or proxied through Invidious.
 */
object YouTubeCatalog {
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private fun get(uri: String): String {
        val c = URL(uri).openConnection() as HttpURLConnection
        c.connectTimeout = 4500
        c.readTimeout = 4500
        c.setRequestProperty("Accept", "application/json")
        c.setRequestProperty("User-Agent", "VistaMusic/0.2")
        try {
            if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
    }
    private fun text(raw: String): String =
        Html.fromHtml(raw, Html.FROM_HTML_MODE_LEGACY).toString().trim()

    suspend fun search(query: String, apiKey: String): List<Song> =
        withContext(Dispatchers.IO) {
            if (query.isBlank()) return@withContext emptyList()
            if (apiKey.isNotBlank()) {
                runCatching { officialSearch(query, apiKey) }
                    .getOrNull()?.takeIf { it.isNotEmpty() }?.let { return@withContext it }
            }
            // Discovery-only fallback. These volunteer services may require CAPTCHA,
            // rate-limit clients, disappear, or be inaccessible in some regions.
            val instances = listOf(
                "https://inv.nadeko.net",
                "https://invidious.tiekoetter.com",
                "https://invidious.nerdvpn.de"
            )
            for (host in instances) {
                val found = runCatching {
                    val arr = JSONArray(get("$host/api/v1/search?q=${enc(query)}&type=video"))
                    (0 until arr.length()).mapNotNull { idx ->
                        val j = arr.optJSONObject(idx) ?: return@mapNotNull null
                        val id = j.optString("videoId")
                        if (id.isBlank() || !id.matches(Regex("[A-Za-z0-9_-]{11}")))
                            return@mapNotNull null
                        Song(
                            "youtube:$id", text(j.optString("title")),
                            text(j.optString("author", "YouTube")),
                            "https://i.ytimg.com/vi/$id/hqdefault.jpg",
                            "youtube:$id", "YouTube · vídeo"
                        )
                    }.distinctBy { it.id }.take(24)
                }.getOrNull()
                if (!found.isNullOrEmpty()) return@withContext found
            }
            emptyList()
        }

    private fun officialSearch(query: String, apiKey: String): List<Song> {
        val url = "https://www.googleapis.com/youtube/v3/search?part=snippet" +
            "&type=video&videoEmbeddable=true&maxResults=24&regionCode=PT" +
            "&q=${enc(query + " official music")}&key=${enc(apiKey)}"
        val items = JSONObject(get(url)).optJSONArray("items") ?: JSONArray()
        return (0 until items.length()).mapNotNull { i ->
            val j = items.optJSONObject(i) ?: return@mapNotNull null
            val id = j.optJSONObject("id")?.optString("videoId").orEmpty()
            if (!id.matches(Regex("[A-Za-z0-9_-]{11}"))) return@mapNotNull null
            val info = j.optJSONObject("snippet") ?: JSONObject()
            Song("youtube:$id", text(info.optString("title")),
                text(info.optString("channelTitle", "YouTube")),
                info.optJSONObject("thumbnails")?.optJSONObject("high")?.optString("url")
                    ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg",
                "youtube:$id", "YouTube · vídeo")
        }
    }

    fun youtubeSearchUrl(query: String): String =
        "https://m.youtube.com/results?search_query=${enc(query)}"
}
