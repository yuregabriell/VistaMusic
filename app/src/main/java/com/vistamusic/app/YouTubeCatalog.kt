package com.vistamusic.app

/**
 * The catalog is now connected to the native audio engine.
 * No embedded YouTube page or player is used.
 */
object YouTubeCatalog {
    suspend fun search(query: String, apiKey: String): List<Song> =
        if (query.isBlank()) emptyList() else runCatching {
            NativeMusic.search(query)
        }.getOrDefault(emptyList())
}
