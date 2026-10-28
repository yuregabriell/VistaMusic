package com.vistamusic.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap

/**
 * Searches for actual square album covers by title and artist, never video frames.
 * When metadata is missing or uncertain, return "" for a neutral designed cover.
 * Third-party catalog availability/asset rights must be respected.
 */
object AlbumArtwork {
    private val cache = ConcurrentHashMap<String, String>()
    private val limiter = Semaphore(3)
    private val catalogCache = ConcurrentHashMap<String, String>()

    /**
     * YouTube Music image URLs typically contain a small requested thumbnail
     * size (e.g. w120-h120). Request a new rendition from the original image,
     * never upscale a downloaded 120px bitmap in the UI.
     */
    fun highResolution(url: String, size: Int = 1000): String {
        if (!url.startsWith("https://")) return ""
        val host = runCatching { URL(url).host.lowercase() }.getOrDefault("")
        if (host.endsWith(".googleusercontent.com") || host == "lh3.googleusercontent.com") {
            return url.replace(Regex("""=w\d+-h\d+[^?&#]*"""),
                "=w${size}-h${size}-l90-rj")
                .replace(Regex("""=s\d+(?:-c)?$"""), "=s${size}-c")
        }
        return url.replace(Regex("""\d{2,4}x\d{2,4}bb"""), "${size}x${size}bb")
    }

    private fun canonicalTitle(name: String): String {
        return name.replace(Regex("""(?i)\s*(?:ft\.?|feat\.?|featuring|with)\s+.+$"""), "")
            .replace(Regex("""(?i)\s*\((?:official|audio|music video|lyric video)[^)]*\)"""), "")
            .trim().trimEnd('(', ')', '-', '–', ',', ' ')
    }

    private fun normalize(input: String): String =
        Normalizer.normalize(input.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9]+"), " ").trim()
    private fun compact(input: String) = normalize(input).replace(" ", "")

    private fun cleanArtist(input: String): String =
        input.trim().removePrefix("@")
            .replace(Regex("""(?i)(?:\s*[-–]\s*topic|vevo|official|music)$"""), "")
            .trim()

    private fun cleanTitle(song: Song): String {
        var result = song.title.trim()
        val artist = cleanArtist(song.artist)
        if (artist.isNotBlank()) {
            val prefix = Regex("""^\s*""" + Regex.escape(artist) + """\s*[-–:]\s*""",
                RegexOption.IGNORE_CASE)
            result = result.replace(prefix, "")
        }
        result = result.replace(
            Regex("""\s*[\[(](?:official|audio|visualizer|lyrics|lyric video|music video|4k|hd)[^\])]*[\])]""",
                RegexOption.IGNORE_CASE), "")
        result = result.replace(Regex("""(?i)\s*(?:\||-)\s*(?:official\s*)?(?:music\s*)?video.*$"""), "")
        return canonicalTitle(result).take(130)
    }

    private fun sameArtist(found: String, expected: String): Boolean {
        val candidate = compact(cleanArtist(found))
        val target = compact(cleanArtist(expected))
        if (candidate.isBlank() || target.isBlank()) return false
        return candidate == target ||
            (candidate.length >= 6 && target.length >= 6 &&
                (candidate.startsWith(target) || target.startsWith(candidate))) ||
            (candidate.length >= 6 && target.length >= 6 &&
                candidate.contains(target) && candidate.length - target.length < 5)
    }

    private fun sameTitle(found: String, expected: String): Boolean {
        val a = compact(canonicalTitle(found.replace(
            Regex("""(?i)\s*\((?:remastered|deluxe|explicit)[^)]*\)"""), "")))
        val b = compact(canonicalTitle(expected))
        return a.isNotBlank() && b.length >= 3 &&
            (a == b || (b.length >= 8 && a.startsWith(b) && a.length - b.length < 12))
    }

    private fun coverFrom(results: JSONArray, artist: String, title: String): String {
        for (i in 0 until results.length()) {
            val track = results.optJSONObject(i) ?: continue
            val foundArtist = track.optJSONObject("artist")?.optString("name").orEmpty()
            val foundTitle = track.optString("title_short", track.optString("title"))
            if (!sameArtist(foundArtist, artist) || !sameTitle(foundTitle, title)) continue
            val album = track.optJSONObject("album") ?: continue
            for (field in listOf("cover_xl", "cover_big", "cover_medium")) {
                val cover = album.optString(field)
                if (cover.startsWith("https://") && !cover.contains("/images/cover/0/")) {
                    return cover
                }
            }
        }
        return ""
    }

    private fun searchDeezer(query: String, artist: String, title: String): String {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val connection = URL("https://api.deezer.com/search/track?q=$encoded&limit=25")
            .openConnection() as HttpURLConnection
        connection.connectTimeout = 5000
        connection.readTimeout = 6500
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "VistaMusic/0.8.0")
        return try {
            if (connection.responseCode !in 200..299) return ""
            val data = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            if (data.has("error")) return ""
            coverFrom(data.optJSONArray("data") ?: JSONArray(), artist, title)
        } finally { connection.disconnect() }
    }

    private fun searchITunes(artist: String, title: String): String {
        val q = URLEncoder.encode("$artist $title", "UTF-8")
        val connection = URL("https://itunes.apple.com/search?term=$q&entity=song&limit=30&country=US")
            .openConnection() as HttpURLConnection
        connection.connectTimeout = 4500
        connection.readTimeout = 5500
        connection.setRequestProperty("User-Agent", "VistaMusic/0.10")
        return try {
            if (connection.responseCode !in 200..299) return ""
            val items = JSONObject(connection.inputStream.bufferedReader().use {
                it.readText()
            }).optJSONArray("results") ?: return ""
            for (i in 0 until items.length()) {
                val j = items.optJSONObject(i) ?: continue
                if (!sameArtist(j.optString("artistName"), artist) ||
                    !sameTitle(j.optString("trackName"), title)) continue
                val url = j.optString("artworkUrl100")
                    .replace("100x100bb", "1000x1000bb")
                if (url.startsWith("https://")) return url
            }
            ""
        } finally { connection.disconnect() }
    }

    suspend fun alternate(song: Song): String = withContext(Dispatchers.IO) {
        val artist = cleanArtist(song.artist)
        val title = cleanTitle(song)
        if (artist.isBlank() || title.isBlank()) return@withContext ""
        val key = compact(artist) + "|" + compact(title)
        catalogCache[key]?.let { return@withContext it }
        limiter.withPermit {
            catalogCache[key]?.let { return@withPermit it }
            var result = ""
            for (query in listOf("$artist $title", title)) {
                result = runCatching { searchDeezer(query, artist, title) }.getOrDefault("")
                if (result.isNotBlank()) break
            }
            if (result.isBlank()) result = runCatching {
                searchITunes(artist, title)
            }.getOrDefault("")
            catalogCache[key] = result
            result
        }
    }

    suspend fun resolve(song: Song): String {
        if (!song.id.startsWith("youtube:")) return highResolution(song.art)
        val artist = cleanArtist(song.artist)
        val title = cleanTitle(song)
        if (artist.isBlank() || title.isBlank()) return ""
        if (song.source.contains("YouTube Music") && song.art.startsWith("https://"))
            return highResolution(song.art)
        val key = compact(artist) + "|" + compact(title)
        cache[key]?.let { return it }
        return limiter.withPermit {
            cache[key]?.let { return@withPermit it }
            val image = withContext(Dispatchers.IO) {
                var resolved = ""
                val musicTrack = runCatching { NativeMusic.musicVersion(song) }.getOrNull()
                if (musicTrack != null && musicTrack.source.contains("YouTube Music") &&
                    musicTrack.art.startsWith("https://")) {
                    resolved = highResolution(musicTrack.art)
                }
                if (resolved.isBlank()) resolved = alternate(song)
                resolved
            }
            cache[key] = image
            image  // Never the original YouTube thumbnail.
        }
    }
}
