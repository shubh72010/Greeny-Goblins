package moe.rukamori.archivetune.taster

import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.Event
import moe.rukamori.archivetune.db.entities.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.exp
import kotlin.math.sqrt

@Singleton
class TasteExtractor @Inject constructor(
    private val database: MusicDatabase,
) {
    suspend fun extract(): TasteProfile = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val thirtyDaysMs = 30L * 86400 * 1000
        val sevenDaysMs = 7L * 86400 * 1000
        val oneDayMs = 86400 * 1000L

        val nowLocal = LocalDateTime.ofInstant(Instant.ofEpochMilli(now), ZoneOffset.UTC)
        val sessionHour = nowLocal.hour

        val allEvents = database.eventsSince(now - thirtyDaysMs)
        val totalEventCount = allEvents.size

        val recentSongIds = allEvents
            .distinctBy { it.songId }
            .take(50)
            .map { it.songId }

        val eventSongIds = allEvents.map { it.songId }.distinct()
        val songs = database.getSongsByIds(eventSongIds).associateBy { it.song.id }

        val nowEpochMs = now
        val recencyHalflifeMs = 14.0 * 86400 * 1000

        val artistScores = mutableMapOf<String, Float>()
        val artistNameScores = mutableMapOf<String, Float>()
        val durationSum = mutableListOf<Double>()
        val decadeScores = mutableMapOf<Int, Float>()
        val likedSet = mutableSetOf<String>()
        val skipRatios = mutableMapOf<String, Float>()
        val dailyCounts = mutableMapOf<String, Int>()
        val lifetimeCounts = mutableMapOf<String, Int>()
        val artistSongIds = mutableMapOf<String, MutableList<String>>()
        val recentlyPlayedArtistIds = mutableSetOf<String>()
        val conversionRates = mutableMapOf<String, Float>()

        val eventCounts = mutableMapOf<String, Int>()
        val skipCounts = mutableMapOf<String, Int>()

        for (event in allEvents) {
            val songId = event.songId
            val ageMs = (nowEpochMs - event.timestamp
                .atZone(ZoneOffset.UTC).toInstant().toEpochMilli()).toDouble()
            val recencyWeight = exp(-ageMs / recencyHalflifeMs).toFloat()

            eventCounts[songId] = (eventCounts[songId] ?: 0) + 1
            lifetimeCounts[songId] = (lifetimeCounts[songId] ?: 0) + 1

            if (ageMs < sevenDaysMs) {
                dailyCounts[songId] = (dailyCounts[songId] ?: 0) + 1
            }

            val song = songs[songId]
            if (song != null) {
                val durationMs = song.song.duration * 1000f
                if (durationMs > 0 && event.playTime < durationMs * 0.3f) {
                    skipCounts[songId] = (skipCounts[songId] ?: 0) + 1
                }

                if (ageMs < oneDayMs) {
                    durationSum.add(durationMs.toDouble())
                }

                if (song.song.year != null && song.song.year > 0) {
                    val decade = (song.song.year / 10) * 10
                    decadeScores[decade] = (decadeScores[decade] ?: 0f) + recencyWeight
                }

                for (artist in song.artists) {
                    artistScores[artist.id] = (artistScores[artist.id] ?: 0f) + recencyWeight
                    artistNameScores[artist.name] = (artistNameScores[artist.name] ?: 0f) + recencyWeight
                    artistSongIds.getOrPut(artist.id) { mutableListOf() }.add(songId)
                    if (ageMs < sevenDaysMs) {
                        recentlyPlayedArtistIds.add(artist.id)
                    }
                }
            }

            if (ageMs < oneDayMs && event.playTime > 0) {
                val song = songs[songId]
                if (song != null && song.song.duration > 0) {
                    if (event.playTime >= song.song.duration * 1000f * 0.8f) {
                        val exposureCount = exposureCountForSong(songId, oneDayMs)
                        if (exposureCount > 0) {
                            conversionRates[songId] = (conversionRates[songId] ?: 0f) + 1f
                        }
                    }
                }
            }
        }

        for ((songId, count) in eventCounts) {
            val skips = skipCounts[songId] ?: 0
            skipRatios[songId] = skips.toFloat() / count.toFloat()
        }

        for (song in songs.values) {
            if (song.song.liked) {
                likedSet.add(song.song.id)
            }
        }

        val avgDuration = if (durationSum.isNotEmpty()) {
            durationSum.average()
        } else {
            240_000.0
        }

        val variance = if (durationSum.isNotEmpty()) {
            durationSum.map { (it - avgDuration) * (it - avgDuration) }.average()
        } else {
            0.0
        }

        TasteProfile(
            topArtistIds = artistScores.entries
                .sortedByDescending { it.value }
                .take(20)
                .associate { it.key to it.value },
            topArtistNames = artistNameScores.entries
                .sortedByDescending { it.value }
                .take(20)
                .associate { it.key to it.value },
            preferredDurationAvg = avgDuration,
            preferredDurationStd = sqrt(variance),
            preferredDecades = decadeScores.entries
                .sortedByDescending { it.value }
                .take(5)
                .associate { it.key to it.value },
            likedSongIds = likedSet,
            skipRatios = skipRatios,
            recentSongIds = recentSongIds,
            dailyPlayCounts = dailyCounts,
            playCounts = lifetimeCounts,
            sessionHour = sessionHour,
            artistSongIds = artistSongIds,
            recentlyPlayedArtistIds = recentlyPlayedArtistIds,
            recentlyPlayedSet = recentSongIds.toSet(),
            totalEventCount = totalEventCount,
            conversionRates = conversionRates,
        )
    }

    private fun exposureCountForSong(songId: String, windowMs: Long): Int {
        val now = System.currentTimeMillis()
        return database.exposureCount(songId, now - windowMs)
    }
}
