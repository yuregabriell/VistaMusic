package com.vistamusic.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class ArtistBio(val summary: String, val photo: String, val banner: String = "")

object ArtistBiography {
    private fun fetchJson(uri: String): JSONObject? {
        val c = URL(uri).openConnection() as HttpURLConnection
        c.connectTimeout = 7000; c.readTimeout = 8000
        c.setRequestProperty("Accept", "application/json")
        c.setRequestProperty("User-Agent", "VistaMusic/0.5 (github.com/yuregabriell/VistaMusic)")
        return try {
            if (c.responseCode !in 200..299) null
            else JSONObject(c.inputStream.bufferedReader().use { it.readText() })
        } finally { c.disconnect() }
    }
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    suspend fun fetch(name: String): ArtistBio? = withContext(Dispatchers.IO) {
        // TheAudioDB provides genuine artist portraits/fanart; never substitute album covers.
        val photoData = runCatching {
            val j = fetchJson("https://www.theaudiodb.com/api/v1/json/123/search.php?s=${enc(name)}")
            val arr = j?.optJSONArray("artists")
            (0 until (arr?.length() ?: 0)).mapNotNull { arr?.optJSONObject(it) }
                .firstOrNull { it.optString("strArtist").equals(name, true) }
        }.getOrNull()
        val photo = photoData?.optString("strArtistThumb").orEmpty()
            .takeIf { it.startsWith("https://") }.orEmpty()
        val banner = photoData?.optString("strArtistFanart").orEmpty()
            .takeIf { it.startsWith("https://") }.orEmpty()
        var summary = ""
        var wikipediaPhoto = ""
        for (lang in listOf("pt", "en")) {
            val page = name.replace(' ', '_')
            val j = runCatching {
                fetchJson("https://$lang.wikipedia.org/api/rest_v1/page/summary/${enc(page).replace("+","_")}")
            }.getOrNull() ?: continue
            if (j.optString("type") == "disambiguation") continue
            summary = j.optString("extract").trim().take(900)
            wikipediaPhoto = j.optJSONObject("originalimage")?.optString("source")
                .orEmpty().takeIf { it.startsWith("https://") }
                ?: j.optJSONObject("thumbnail")?.optString("source").orEmpty()
            if (summary.isNotBlank() || wikipediaPhoto.isNotBlank()) break
        }
        val displayPhoto = photo.ifBlank { wikipediaPhoto }
        if (summary.isBlank() && displayPhoto.isBlank()) null
        else ArtistBio(summary, displayPhoto, banner)
    }
}
