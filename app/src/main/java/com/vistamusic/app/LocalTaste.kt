package com.vistamusic.app

import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.max

/**
 * On-device taste ranking: no account, remote AI service, user telemetry or server.
 * Listening history is persisted only in private app preferences and capped.
 */
class LocalTaste(private val prefs: SharedPreferences) {
    data class Event(val song: Song, val action: String, val at: Long)

    private fun events(): List<Event> {
        val json = runCatching { JSONArray(prefs.getString("taste_events", "[]")) }
            .getOrDefault(JSONArray())
        return (0 until json.length()).mapNotNull { i ->
            val j = json.optJSONObject(i) ?: return@mapNotNull null
            val track = j.optJSONObject("song") ?: return@mapNotNull null
            runCatching { Event(Song.fromJson(track), j.optString("action"), j.optLong("at")) }
                .getOrNull()
        }
    }
    fun add(song: Song, action: String) {
        if (song.id.isBlank() || action !in setOf("play", "complete", "skip")) return
        val current = events().toMutableList()
        current.add(Event(song, action, System.currentTimeMillis()))
        val json = JSONArray()
        current.takeLast(180).forEach {
            json.put(JSONObject().put("song", it.song.toJson())
                .put("action", it.action).put("at", it.at))
        }
        prefs.edit().putString("taste_events", json.toString()).apply()
    }
    fun recentlyListened(): List<Song> =
        events().asReversed().filter { it.action != "skip" }
            .map { it.song }.distinctBy { it.id }.take(25)
    fun listenedCount(): Int = events().count { it.action == "play" || it.action == "complete" }

    fun score(candidate: Song, seed: Song?, favorites: List<Song>,
              popular: List<Song>, recent: List<Song>): Double {
        val actions = events()
        val name = candidate.artist.lowercase().trim()
        val favoriteIds = favorites.map { it.id }.toSet()
        val recentIds = recent.take(8).map { it.id }.toSet()
        val artistAffinity = (favorites.count { it.artist.equals(candidate.artist, true) } * 1.5 +
            actions.count { it.song.artist.equals(candidate.artist, true) &&
                    it.action == "complete" } * 1.2 +
            actions.count { it.song.artist.equals(candidate.artist, true) &&
                    it.action == "play" } * 0.2 -
            actions.count { it.song.artist.equals(candidate.artist, true) &&
                    it.action == "skip" } * 1.4).coerceIn(-4.0, 7.0)
        // Genre similarity is computed from the app's curated artist-to-genre map.
        // No inference is presented as an official music-service genre classification.
        val candidateGenres = Genres.categories.filter { genre ->
            Genres.artists(genre.title).any { it.equals(candidate.artist, ignoreCase = true) }
        }.map { it.title }.toSet()
        val recentGenres = (favorites + recent).take(18).flatMap { listened ->
            Genres.categories.filter { genre ->
                Genres.artists(genre.title).any {
                    it.equals(listened.artist, ignoreCase = true)
                }
            }.map { it.title }
        }
        val genreBias = (if (seed?.artist.equals(candidate.artist, true)) 4.0 else 0.0) +
            candidateGenres.sumOf { genre -> recentGenres.count { it == genre } * 0.32 }
                .coerceAtMost(3.0)
        val index = popular.indexOfFirst { it.id == candidate.id }
        val chartBonus = if (index >= 0) (2.4 - index.toDouble() / 50).coerceAtLeast(0.4) else 0.0
        val completed = actions.count { it.song.id == candidate.id && it.action == "complete" }
        val skipped = actions.count { it.song.id == candidate.id && it.action == "skip" }
        val novelty = if (recentIds.contains(candidate.id)) -8.0 else 1.3
        val favoriteBias = if (favoriteIds.contains(candidate.id)) 0.6 else 0.0
        return artistAffinity + genreBias + chartBonus + novelty + favoriteBias -
            completed * 0.8 - skipped * 3.5 + (abs((candidate.id + name).hashCode()) % 100) / 300.0
    }
    fun rankedForYou(candidates: List<Song>, favorites: List<Song>): List<Song> {
        val recent = recentlyListened()
        val seed = recent.firstOrNull() ?: favorites.firstOrNull()
        return candidates.distinctBy { it.id }.sortedByDescending {
            score(it, seed, favorites, candidates, recent)
        }.take(50)
    }

    /**
     * Genre affinities are derived from artists in listening history and likes.
     * This is an on-device recommendation model, not an official YouTube profile.
     */
    fun preferredGenres(favorites: List<Song>): List<String> {
        val inputs = (recentlyListened() + favorites).take(30)
        val scores = Genres.categories.map { genre ->
            val matching = inputs.count { song ->
                Genres.artists(genre.title).any { artist ->
                    song.artist.contains(artist, ignoreCase = true) ||
                        artist.contains(song.artist, ignoreCase = true)
                }
            }
            genre.title to matching
        }
        return scores.filter { it.second > 0 }.sortedByDescending { it.second }
            .take(3).map { it.first }
    }

    fun discoveryRank(candidates: List<Song>, favorites: List<Song>,
                      charts: List<Song>): List<Song> {
        val recent = recentlyListened()
        val knownIds = (recent + favorites).map { it.id }.toSet()
        val recentNames = recent.map {
            it.title.lowercase().trim() + "|" + it.artist.lowercase().trim()
        }.toSet()
        val seen = mutableSetOf<String>()
        val unique = candidates.filter { track ->
            val id = track.title.lowercase().trim() + "|" + track.artist.lowercase().trim()
            track.title.isNotBlank() && track.artist.isNotBlank() &&
                track.id !in knownIds && id !in recentNames && seen.add(id)
        }
        val seed = recent.firstOrNull() ?: favorites.firstOrNull()
        val popular = charts.distinctBy { it.id }
        return unique.sortedByDescending {
            score(it, seed, favorites, popular, recent)
        }.take(40)
    }

    suspend fun nextSongs(
        seed: Song, excluded: Set<String>, favorites: List<Song>,
        charts: List<Song>, catalog: LibraryCatalog
    ): List<Song> = withContext(Dispatchers.IO) {
        val recent = recentlyListened()
        val preferred = (favorites.asReversed() + recent).distinctBy { it.artist.lowercase() }
            .take(3).map { it.artist }.filter { it.isNotBlank() }
        val artists = (listOf(seed.artist) + preferred)
            .distinctBy { it.lowercase() }.take(4)
        val candidates = mutableListOf<Song>()
        for (artist in artists) {
            candidates.addAll(runCatching { catalog.artistSongs(artist, 0L) }
                .getOrDefault(emptyList()).take(30))
        }
        candidates.addAll(charts)
        candidates.distinctBy { it.title.lowercase().trim() + "|" + it.artist.lowercase().trim() }
            .filter { it.id !in excluded }
            .sortedByDescending { score(it, seed, favorites, charts, recent) }
            .take(12)
    }
}
