package com.vistamusic.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class ArtistBio(val summary: String, val photo: String)

object ArtistBiography {
    suspend fun fetch(name: String): ArtistBio? = withContext(Dispatchers.IO) {
        for (lang in listOf("pt", "en")) {
            try {
                val path = URLEncoder.encode(name.replace(' ', '_'), "UTF-8").replace("+", "_")
                val conn = URL("https://$lang.wikipedia.org/api/rest_v1/page/summary/$path")
                    .openConnection() as HttpURLConnection
                conn.connectTimeout = 6500
                conn.readTimeout = 6500
                conn.setRequestProperty("Accept", "application/json")
                conn.setRequestProperty("User-Agent",
                    "VistaMusic/0.4.0 (https://github.com/yuregabriell/VistaMusic)")
                try {
                    if (conn.responseCode !in 200..299) continue
                    val j = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                    if (j.optString("type") == "disambiguation") continue
                    val summary = j.optString("extract").trim()
                    if (summary.isBlank()) continue
                    val photo = j.optJSONObject("thumbnail")?.optString("source").orEmpty()
                    return@withContext ArtistBio(summary.take(800), photo)
                } finally { conn.disconnect() }
            } catch (_: Exception) { }
        }
        null
    }
}
