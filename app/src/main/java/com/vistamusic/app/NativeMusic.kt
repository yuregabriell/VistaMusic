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
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeTrendingMusicLinkHandlerFactory
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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

    data class PopularTrack(val song: Song, val views: Long)

    private fun infoToSong(item: StreamInfoItem): Song? {
        val id = idFromUrl(item.url) ?: return null
        val originalTitle = item.name.trim()
        val author = item.uploaderName.orEmpty().trim()
        val dash = Regex("^(.+?)\\s*[-–]\\s*(.+)$").find(originalTitle)
        val prefix = dash?.groupValues?.getOrNull(1).orEmpty()
        val suffix = dash?.groupValues?.getOrNull(2).orEmpty()
        val artist = if (prefix.isNotBlank() && prefix.length < 65 &&
            !prefix.contains("official", true)) prefix
            else author.removeSuffix(" - Topic").removeSuffix("VEVO").trim()
        val cleanTitle = (if (suffix.isNotBlank()) suffix else originalTitle)
            .replace(Regex("\\s*[([](?:official|music video|visualizer|audio|lyrics|lyric video)[^)]*[)\\]]",
                RegexOption.IGNORE_CASE), "").trim()
        return Song("youtube:$id", cleanTitle.ifBlank { originalTitle },
            artist.ifBlank { author }, item.thumbnails.firstOrNull()?.url.orEmpty(),
            "youtube:$id", "YouTube · experimental")
    }

    private fun sourceSearch(query: String): List<StreamInfoItem> {
        ensureInitialized()
        val extractor = ServiceList.YouTube.getSearchExtractor(query)
        extractor.fetchPage()
        return extractor.initialPage.items.filterIsInstance<StreamInfoItem>()
            .filter { it.duration in 90..900 || it.duration == -1L }
    }

    suspend fun search(query: String): List<Song> = withContext(Dispatchers.IO) {
        sourceSearch(query.trim() + " official audio")
            .mapNotNull(::infoToSong)
            .distinctBy { it.id }.take(28)
    }

    private fun matchArtist(item: StreamInfoItem, name: String): Boolean {
        val artist = name.lowercase().trim()
        val title = item.name.lowercase()
        val uploader = item.uploaderName.orEmpty().lowercase()
        if (listOf("reaction", "cover", "karaoke", "tutorial", "slowed", "sped up",
                "fanmade", "8d audio").any { title.contains(it) }) return false
        return uploader.contains(artist) || title.startsWith("$artist -") ||
            title.startsWith("$artist –") || title.contains("feat. $artist")
    }

    private fun songKey(song: Song): String =
        song.title.lowercase().replace(Regex("\\([^)]*\\)"), "")
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    suspend fun popularArtist(name: String): List<PopularTrack> = coroutineScope {
        val searches = listOf(
            "$name official music video",
            "$name official audio",
            "$name topic song"
        )
        val found = searches.map { q -> async(Dispatchers.IO) {
            runCatching {
                sourceSearch(q).filter { matchArtist(it, name) }
                    .mapNotNull { item ->
                        infoToSong(item)?.let { PopularTrack(it.copy(artist = name), item.viewCount) }
                    }
            }.getOrDefault(emptyList())
        }}.map { it.await() }.flatten()
        // Number of views only applies to each discovered YouTube video, not to the
        // artist's song across all uploads. Sorting is a sampled popularity estimate.
        found.sortedWith(compareByDescending<PopularTrack> { it.views }
            .thenBy { it.song.title })
            .distinctBy { songKey(it.song) }.take(12)
    }

    suspend fun trending(country: String = "PT"): List<Song> =
        withContext(Dispatchers.IO) {
            ensureInitialized()
            val accepted = if (country.uppercase() in setOf("PT", "BR", "US"))
                country.uppercase() else "PT"
            val kioskList = ServiceList.YouTube.kioskList
            val extractor = kioskList.getExtractorById(
                YoutubeTrendingMusicLinkHandlerFactory.KIOSK_ID, null)
            extractor.forceContentCountry(ContentCountry(accepted))
            extractor.fetchPage()
            extractor.initialPage.items.filterIsInstance<StreamInfoItem>()
                .mapNotNull(::infoToSong).distinctBy { it.id }.take(35)
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
