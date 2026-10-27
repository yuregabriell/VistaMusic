package com.vistamusic.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Audio-only alternative inspired by LibreTube's Piped integration.
 * Uses Piped's documented /streams/:videoId API. Does not bundle LibreTube code.
 * Public Piped instances are untrusted and unreliable; no credentials are sent.
 */
object LibreTubeAudio {
    suspend fun resolve(videoId: String): String = withContext(Dispatchers.IO) {
        require(videoId.matches(Regex("[A-Za-z0-9_-]{11}")))
        val hosts = listOf("https://pipedapi.kavin.rocks",
            "https://pipedapi.leptons.xyz", "https://pipedapi.nosebs.ru")
        for (host in hosts) {
            try {
                val connection = URL("$host/streams/$videoId").openConnection() as HttpURLConnection
                connection.connectTimeout = 3500
                connection.readTimeout = 4500
                connection.setRequestProperty("Accept", "application/json")
                try {
                    if (connection.responseCode !in 200..299) continue
                    val root = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                    val streams = root.optJSONArray("audioStreams") ?: continue
                    val list = (0 until streams.length()).mapNotNull { streams.optJSONObject(it) }
                    val best = list.filter {
                        it.optString("url").startsWith("https://") &&
                        it.optString("mimeType").startsWith("audio/")
                    }.maxByOrNull { it.optInt("bitrate", 0) }
                    val stream = best?.optString("url").orEmpty()
                    if (stream.startsWith("https://")) return@withContext stream
                } finally { connection.disconnect() }
            } catch (_: Exception) { /* try next provider */ }
        }
        error("As instâncias públicas não disponibilizaram áudio para esta faixa.")
    }
}
