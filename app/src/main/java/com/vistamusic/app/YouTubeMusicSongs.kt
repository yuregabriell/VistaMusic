package com.vistamusic.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** Read-only, unauthenticated, experimental YouTube Music SONGS search.
 * Does not handle user sessions, premium access, DRM or ad removal.
 */
object YouTubeMusicSongs {
    private val http = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS).readTimeout(9, TimeUnit.SECONDS).build()
    private val idPattern = Regex("^[A-Za-z0-9_-]{11}$")
    private const val SONGS_FILTER = "EgWKAQIIAWoMEA4QChADEAQQCRAF"
    @Volatile private var apiKey: String? = null
    @Volatile private var clientVersion: String? = null
    private const val webAgent = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/127.0 Safari/537.36"

    private fun ensureWebConfig() {
        if (apiKey != null && clientVersion != null) return
        synchronized(this) {
            if (apiKey != null && clientVersion != null) return
            val req = Request.Builder().url("https://music.youtube.com/")
                .header("User-Agent", webAgent).build()
            http.newCall(req).execute().use { response ->
                val html = response.body?.string().orEmpty()
                val key = Regex(""""INNERTUBE_API_KEY"\s*:\s*"([^"]+)"""")
                    .find(html)?.groupValues?.getOrNull(1)
                val version = Regex(""""INNERTUBE_CLIENT_VERSION"\s*:\s*"([^"]+)"""")
                    .find(html)?.groupValues?.getOrNull(1)
                if (key.isNullOrBlank() || version.isNullOrBlank())
                    error("YouTube Music search configuration unavailable")
                apiKey = key
                clientVersion = version
            }
        }
    }

    private fun runs(renderer: JSONObject, index: Int): JSONArray? =
        renderer.optJSONArray("flexColumns")?.optJSONObject(index)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")?.optJSONArray("runs")

    private fun text(runs: JSONArray?): String =
        (0 until (runs?.length() ?: 0)).mapNotNull {
            runs?.optJSONObject(it)?.optString("text")
        }.joinToString("").trim()

    private fun videoId(renderer: JSONObject): String {
        val id = renderer.optJSONObject("playlistItemData")?.optString("videoId").orEmpty()
        if (idPattern.matches(id)) return id
        val overlay = renderer.optJSONObject("overlay")
            ?.optJSONObject("musicItemThumbnailOverlayRenderer")
            ?.optJSONObject("content")?.optJSONObject("musicPlayButtonRenderer")
            ?.optJSONObject("playNavigationEndpoint")
            ?.optJSONObject("watchEndpoint")?.optString("videoId").orEmpty()
        return overlay.takeIf { idPattern.matches(it) }.orEmpty()
    }

    private fun parse(renderer: JSONObject): Song? {
        val id = videoId(renderer)
        val title = text(runs(renderer, 0))
        if (!idPattern.matches(id) || title.isBlank()) return null
        val details = runs(renderer, 1)
        val tokens = (0 until (details?.length() ?: 0)).mapNotNull {
            details?.optJSONObject(it)?.optString("text")
        }
        val artist = tokens.firstOrNull {
            it.isNotBlank() && !it.contains(" • ") && it !in listOf("Song", "Music", "Video")
                && !it.matches(Regex("""\d+:\d+"""))
        }.orEmpty().removeSuffix(" - Topic").trim()
        if (artist.isBlank()) return null
        val thumbs = renderer.optJSONObject("thumbnail")
            ?.optJSONObject("musicThumbnailRenderer")
            ?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
        val art = (0 until (thumbs?.length() ?: 0))
            .mapNotNull { thumbs?.optJSONObject(it) }
            .maxByOrNull { it.optInt("width") }
            ?.optString("url").orEmpty().takeIf { it.startsWith("https://") }.orEmpty()
        return Song("youtube:$id", title, artist, art, "youtube:$id", "YouTube Music · song")
    }

    private fun traverse(node: Any?, found: MutableList<JSONObject>, depth: Int = 0) {
        if (depth > 18 || found.size > 100) return
        when (node) {
            is JSONObject -> {
                node.optJSONObject("musicResponsiveListItemRenderer")?.let { found.add(it) }
                val keys = node.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    if (key != "musicResponsiveListItemRenderer")
                        traverse(node.opt(key), found, depth + 1)
                }
            }
            is JSONArray -> for (i in 0 until minOf(node.length(), 200)) {
                traverse(node.opt(i), found, depth + 1)
            }
        }
    }

    suspend fun search(query: String, limit: Int = 25): List<Song> =
        withContext(Dispatchers.IO) {
            if (query.isBlank()) return@withContext emptyList()
            ensureWebConfig()
            val payload = JSONObject()
                .put("context", JSONObject().put("client", JSONObject()
                    .put("clientName", "WEB_REMIX")
                    .put("clientVersion", clientVersion)
                    .put("hl", "en").put("gl", "PT")))
                .put("query", query.trim()).put("params", SONGS_FILTER)
            val url = "https://music.youtube.com/youtubei/v1/search?key=" +
                URLEncoder.encode(apiKey.orEmpty(), "UTF-8") + "&prettyPrint=false"
            val request = Request.Builder().url(url)
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .header("Origin", "https://music.youtube.com")
                .header("User-Agent", webAgent)
                .header("X-YouTube-Client-Name", "67")
                .header("X-YouTube-Client-Version", clientVersion.orEmpty()).build()
            val json = http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Music search HTTP " + response.code)
                JSONObject(response.body?.string().orEmpty())
            }
            val found = mutableListOf<JSONObject>()
            traverse(json, found)
            found.mapNotNull(::parse).distinctBy { it.id }.take(limit)
        }
}
