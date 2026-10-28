package com.vistamusic.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap

/**
 * Match real album covers to YouTube music search metadata rather than using a
 * widescreen video thumbnail. Deezer's public metadata endpoint may be unavailable
 * in some regions, in which case the existing artwork is retained.
 */
object AlbumArtwork {
    private val cache = ConcurrentHashMap<String, String>()
    private val limiter = Semaphore(3)
    private fun normalize(s: String): String {
        val ascii = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
        return ascii.replace(Regex("[^a-z0-9]+"), " ").trim()
    }
    private fun title(song: Song): String =
        LyricsApi.cleanTitle(song.title, song.artist)
            .replace(Regex("""\s*\[(?:official|audio|video|lyrics)[^\]]*]""",
                RegexOption.IGNORE_CASE), "").trim()
    suspend fun resolve(song: Song): String {
        if (!song.id.startsWith("youtube:")) return song.art
        val cleanedTitle = title(song)
        val key = normalize(cleanedTitle) + "|" + normalize(song.artist)
        if (key.isBlank()) return song.art
        cache[key]?.let { return it.ifBlank { song.art } }
        return limiter.withPermit {
            cache[key]?.let { return@withPermit it.ifBlank { song.art } }
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val q = URLEncoder.encode(
                        "artist:\"${song.artist}\" track:\"$cleanedTitle\"", "UTF-8")
                    val c = URL("https://api.deezer.com/search?q=$q&limit=6")
                        .openConnection() as HttpURLConnection
                    c.connectTimeout = 5000; c.readTimeout = 6500
                    c.setRequestProperty("Accept", "application/json")
                    c.setRequestProperty("User-Agent", "VistaMusic/0.7 (noncommercial test)")
                    try {
                        if (c.responseCode !in 200..299) return@runCatching ""
                        val arr = JSONObject(c.inputStream.bufferedReader().use {
                            it.readText()
                        }).optJSONArray("data")
                        val artist = normalize(song.artist)
                        val songTitle = normalize(cleanedTitle)
                        (0 until (arr?.length() ?: 0)).mapNotNull { i ->
                            val j = arr?.optJSONObject(i) ?: return@mapNotNull null
                            val otherArtist = normalize(j.optJSONObject("artist")?.optString("name").orEmpty())
                            val otherTitle = normalize(j.optString("title_short",
                                j.optString("title")))
                            if (artist.isBlank() || songTitle.isBlank() ||
                                !(otherArtist == artist || otherArtist.contains(artist) ||
                                    artist.contains(otherArtist) && otherArtist.length > 4) ||
                                !(otherTitle == songTitle ||
                                    (otherTitle.startsWith(songTitle) && songTitle.length > 5)))
                                return@mapNotNull null
                            j.optJSONObject("album")?.let { album ->
                                album.optString("cover_xl").takeIf { it.startsWith("https://") }
                                    ?: album.optString("cover_big").takeIf {
                                        it.startsWith("https://")
                                    }
                            }
                        }.firstOrNull().orEmpty()
                    } finally { c.disconnect() }
                }.getOrDefault("")
            }
            cache[key] = result
            result.ifBlank { song.art }
        }
    }
}
