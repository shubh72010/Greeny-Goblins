package moe.rukamori.archivetune.taster

import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CandidatePool @Inject constructor(
    private val database: MusicDatabase,
    private val audioFeatureCache: AudioFeatureCache,
) {
    suspend fun generate(profile: TasteProfile): List<Candidate> = withContext(Dispatchers.IO) {
        val candidates = mutableMapOf<String, Candidate>()

        val topPlayed = profile.playCounts.entries
            .sortedByDescending { it.value }
            .take(10)
            .map { it.key }

        val recentFromProfile = profile.recentSongIds.take(5)

        for (sourceSongId in (topPlayed + recentFromProfile).distinct()) {
            val related = try {
                database.relatedSongs(sourceSongId)
            } catch (_: Exception) {
                emptyList()
            }
            for (song in related) {
                if (song.song.id !in profile.recentlyPlayedSet) {
                    val key = song.song.id
                    if (key !in candidates) {
                        candidates[key] = Candidate(song = song, source = CandidateSource.RELATED)
                    }
                }
            }
        }

        for ((artistId, _) in profile.topArtistIds.entries.take(5)) {
            val artistSongs = try {
                database.artistSongsPreview(artistId, 30).first()
            } catch (_: Exception) {
                emptyList()
            }
            for (song in artistSongs) {
                val key = song.song.id
                val lastPlayedIdx = profile.recentSongIds.indexOf(key)
                val daysSincePlayed = if (lastPlayedIdx >= 0) {
                    (lastPlayedIdx + 1) * 2
                } else {
                    Int.MAX_VALUE
                }
                if (daysSincePlayed > 30 && key !in candidates) {
                    candidates[key] = Candidate(song = song, source = CandidateSource.ARTIST_DISCOVERY)
                }
            }
        }

        val oldPlayTimeThreshold = profile.playCounts.entries
            .filter { it.key !in profile.recentlyPlayedSet }
            .sortedByDescending { it.value }
            .take(20)
            .map { it.key }

        if (oldPlayTimeThreshold.isNotEmpty()) {
            val forgottenSongs = try {
                database.getSongsByIds(oldPlayTimeThreshold)
            } catch (_: Exception) {
                emptyList()
            }
            for (song in forgottenSongs) {
                val key = song.song.id
                if (key !in candidates) {
                    candidates[key] = Candidate(song = song, source = CandidateSource.FORGOTTEN)
                }
            }
        }

        val metadataSimilar = candidates.values
            .filter { it.song.song.duration > 0 }
            .filter {
                val dur = it.song.song.duration * 1000.0
                kotlin.math.abs(dur - profile.preferredDurationAvg) < profile.preferredDurationStd * 2
            }
            .take(20)
            .map { it.song.song.id }

        if (metadataSimilar.isNotEmpty()) {
            val similarSongs = try {
                database.getSongsByIds(metadataSimilar)
            } catch (_: Exception) {
                emptyList()
            }
            for (song in similarSongs) {
                val key = song.song.id
                if (key !in candidates) {
                    candidates[key] = Candidate(song = song, source = CandidateSource.METADATA_SIMILAR)
                }
            }
        }

        val topSongIds = profile.playCounts.entries
            .sortedByDescending { it.value }
            .take(3)
            .map { it.key }
        for (seedSongId in topSongIds) {
            val related = try {
                database.relatedSongs(seedSongId)
            } catch (_: Exception) {
                emptyList()
            }
            for (song in related.take(10)) {
                val key = song.song.id
                if (key !in profile.recentlyPlayedSet && key !in candidates) {
                    candidates[key] = Candidate(song = song, source = CandidateSource.EXPLORATION)
                }
            }
        }

        val cachedFeatureSongIds = try {
            database.allCachedAudioFeatureSongIds().toSet()
        } catch (_: Exception) {
            emptySet()
        }
        if (cachedFeatureSongIds.isNotEmpty()) {
            val centroid = audioFeatureCache.computeCentroid(topPlayed.filter { it in cachedFeatureSongIds })
            if (centroid != null) {
                val availableForAudio = cachedFeatureSongIds
                    .filter { it !in candidates && it !in profile.recentlyPlayedSet }
                val similarIds = audioFeatureCache.findAudioSimilarCandidates(
                    centroid = centroid,
                    candidateSongIds = availableForAudio,
                    topN = 15,
                )
                for (similarId in similarIds) {
                    if (similarId !in candidates) {
                        val song = try {
                            database.getSongsByIds(listOf(similarId)).firstOrNull()
                        } catch (_: Exception) {
                            null
                        }
                        if (song != null) {
                            candidates[similarId] = Candidate(song = song, source = CandidateSource.AUDIO_SIMILAR)
                        }
                    }
                }
            }
        }

        candidates.values.toList()
    }
}

data class Candidate(
    val song: Song,
    val source: CandidateSource,
)

enum class CandidateSource {
    RELATED,
    ARTIST_DISCOVERY,
    FORGOTTEN,
    METADATA_SIMILAR,
    EXPLORATION,
    AUDIO_SIMILAR,
}
