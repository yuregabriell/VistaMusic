package com.vistamusic.app

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.concurrent.TimeUnit

/**
 * Experimental client for public YouTube media. No account credentials,
 * advertisements, protected streams or DRM are modified or intercepted.
 * Playback may stop working when the external service changes.
 */
object NativeMusic {
    private val http = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val initialized: Boolean by lazy {
        NewPipe.init(object : Downloader() {
            override fun execute(request: Request): Response {
                val data = request.dataToSend()
                val method = request.httpMethod()
                val builder = okhttp3.Request.Builder()
                    .url(request.url())
                    .header("User-Agent", "Mozilla/5.0 (X11; Linux x86_64; rv:128.0) Gecko/20100101 Firefox/128.0")
                for ((key, values) in request.headers()) {
                    if (!values.isNullOrEmpty()) {
                        builder.removeHeader(key)
                        for (value in values) builder.addHeader(key, value)
                    }
                }
                builder.method(method, data?.toRequestBody())
                return http.newCall(builder.build()).execute().use { response ->
                    Response(response.code, response.message, response.headers.toMultimap(),
                        response.body?.string(), response.request.url.toString())
                }
            }
        })
        true
    }

    private fun ensureInitialized() { initialized }

    private fun idFromUrl(url: String): String? {
        val parsed = Uri.parse(url)
        val id = parsed.getQueryParameter("v") ?:
            if (parsed.host?.endsWith("youtu.be") == true)
                parsed.lastPathSegment else null
        return id?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }
    }

    suspend fun search(query: String): List<Song> = withContext(Dispatchers.IO) {
        ensureInitialized()
        val extractor = ServiceList.YouTube.getSearchExtractor(query.trim() + " official audio")
        extractor.fetchPage()
        extractor.initialPage.items.filterIsInstance<StreamInfoItem>()
            .mapNotNull { item ->
                val id = idFromUrl(item.url) ?: return@mapNotNull null
                Song("youtube:$id", item.name,
                    item.uploaderName.orEmpty().removeSuffix(" - Topic").ifBlank { "Artista" },
                    item.thumbnails.firstOrNull()?.url.orEmpty(),
                    "youtube:$id", "YouTube Music · experimental")
            }.distinctBy { it.id }.take(24)
    }

    suspend fun resolve(id: String): String = withContext(Dispatchers.IO) {
        require(id.matches(Regex("[A-Za-z0-9_-]{11}"))) { "ID de música inválido" }
        val direct = runCatching {
            ensureInitialized()
            val extractor = ServiceList.YouTube.getStreamExtractor(
                "https://www.youtube.com/watch?v=$id")
            extractor.fetchPage()
            extractor.audioStreams
                .filter { it.content.startsWith("https://") }
                .maxByOrNull { it.averageBitrate }
                ?.content
        }.getOrNull()
        direct ?: LibreTubeAudio.resolve(id)
    }
}
