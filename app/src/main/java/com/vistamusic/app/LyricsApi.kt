package com.vistamusic.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class LyricLine(val timeMs: Long, val text: String)
data class LyricSheet(val lines: List<LyricLine>, val synchronized: Boolean, val found: Boolean)

object LyricsApi {
    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")
    fun cleanTitle(raw: String, artist: String): String {
        var name = raw.trim()
        if (name.startsWith(artist + " - ", ignoreCase = true)) name = name.substring(artist.length + 3)
        name = name.replace(Regex("\\s*\\((?:official|audio|visualizer|lyrics|lyric video|music video|live|hd|4k)[^)]*\\)", RegexOption.IGNORE_CASE), "")
        name = name.replace(Regex("\\s*\\[(?:official|audio|lyrics|music video)[^]]*]", RegexOption.IGNORE_CASE), "")
        return name.trim()
    }
    suspend fun find(title: String, artist: String): LyricSheet = withContext(Dispatchers.IO) {
        val cleaned = cleanTitle(title, artist)
        val endpoints = listOf(
            "https://lrclib.net/api/get?track_name=${enc(cleaned)}&artist_name=${enc(artist)}",
            "https://lrclib.net/api/search?track_name=${enc(cleaned)}&artist_name=${enc(artist)}"
        )
        for (endpoint in endpoints) {
            try {
                val c = URL(endpoint).openConnection() as HttpURLConnection
                c.connectTimeout = 6000
                c.readTimeout = 7000
                c.setRequestProperty("User-Agent", "VistaMusic v0.4.0 (https://github.com/yuregabriell/VistaMusic)")
                try {
                    if (c.responseCode == 429) return@withContext LyricSheet(emptyList(), false, false)
                    if (c.responseCode !in 200..299) continue
                    val body = c.inputStream.bufferedReader().use { it.readText() }
                    val j = if (body.trimStart().startsWith("[")) {
                        val a = JSONArray(body)
                        (0 until a.length()).mapNotNull { a.optJSONObject(it) }
                            .firstOrNull { it.optString("artistName").contains(artist, true) }
                            ?: a.optJSONObject(0)
                    } else JSONObject(body)
                    if (j == null || j.optBoolean("instrumental")) continue
                    val synced = j.optString("syncedLyrics").takeIf { it != "null" && it.isNotBlank() }
                    if (synced != null) {
                        val matcher = Regex("""^\[(\d{2}):(\d{2})(?:\.(\d{1,3}))?]\s*(.*)$""")
                        val lines = synced.lines().mapNotNull { raw ->
                            val m = matcher.matchEntire(raw.trim()) ?: return@mapNotNull null
                            val minutes = m.groupValues[1].toLongOrNull() ?: 0L
                            val seconds = m.groupValues[2].toLongOrNull() ?: 0L
                            val fraction = m.groupValues[3].padEnd(3, '0').take(3).toLongOrNull() ?: 0L
                            LyricLine((minutes * 60 + seconds) * 1000 + fraction, m.groupValues[4].trim())
                        }.filter { it.text.isNotBlank() }.sortedBy { it.timeMs }
                        if (lines.isNotEmpty()) return@withContext LyricSheet(lines, true, true)
                    }
                    val plain = j.optString("plainLyrics").takeIf { it != "null" && it.isNotBlank() }
                    if (plain != null) {
                        val lines = plain.lines().filter { it.isNotBlank() }.map { LyricLine(0L, it.trim()) }
                        return@withContext LyricSheet(lines, false, true)
                    }
                } finally { c.disconnect() }
            } catch (_: Exception) { /* lyric provider may be unavailable */ }
        }
        LyricSheet(emptyList(), false, false)
    }
}
