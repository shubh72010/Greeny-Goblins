package moe.rukamori.archivetune.taster

import moe.rukamori.archivetune.db.entities.Song
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Scorer @Inject constructor(
    private val audioFeatureCache: AudioFeatureCache,
) {

    fun score(
        candidates: List<Candidate>,
        profile: TasteProfile,
        limit: Int = 20,
    ): List<ScoredSong> {
        val now = System.currentTimeMillis()
        val dayMs = 86400 * 1000L

        val topPlayed = profile.playCounts.entries
            .sortedByDescending { it.value }
            .take(10)
            .map { it.key }
        val audioCentroid = audioFeatureCache.computeCentroid(topPlayed)

        val scored = candidates.map { candidate ->
            val song = candidate.song
            val songId = song.song.id

            val recencyPlaySignal = computeRecencyPlaySignal(songId, profile)
            val artistSignal = computeArtistSignal(song, profile)
            val feedbackSignal = computeFeedbackSignal(songId, profile)
            val coOccurrenceSignal = computeCoOccurrenceSignal(songId, profile)
            val noveltySignal = computeNoveltySignal(songId, profile)
            val explorationSignal = computeExplorationSignal(candidate.source, profile)
            val metadataSignal = computeMetadataSignal(song, profile)
            val audioFitSignal = computeAudioFitSignal(songId, audioCentroid)

            val rawScore =
                recencyPlaySignal * 0.25f +
                    artistSignal * 0.20f +
                    feedbackSignal * 0.20f +
                    coOccurrenceSignal * 0.10f +
                    noveltySignal * 0.10f +
                    explorationSignal * 0.08f +
                    metadataSignal * 0.03f +
                    audioFitSignal * 0.04f

            ScoredSong(
                song = song,
                score = rawScore,
                source = candidate.source,
                recencyPlaySignal = recencyPlaySignal,
                artistSignal = artistSignal,
                feedbackSignal = feedbackSignal,
                coOccurrenceSignal = coOccurrenceSignal,
                noveltySignal = noveltySignal,
                explorationSignal = explorationSignal,
                metadataSignal = metadataSignal,
                audioFitSignal = audioFitSignal,
            )
        }.sortedByDescending { it.score }

        val deduplicated = mutableListOf<ScoredSong>()
        val seenIds = mutableSetOf<String>()
        for (item in scored) {
            if (item.song.song.id !in seenIds) {
                deduplicated.add(item)
                seenIds.add(item.song.song.id)
            }
        }

        val artistCount = mutableMapOf<String, Int>()
        val diversified = mutableListOf<ScoredSong>()

        for (item in deduplicated) {
            val artistIds = item.song.artists.map { it.id }
            var artistPenalty = 0f
            for (artistId in artistIds) {
                val count = artistCount[artistId] ?: 0
                if (count > 0) {
                    artistPenalty += 0.1f * count
                }
            }

            val adjustedScore = item.score * (1f - min(artistPenalty, 0.5f))

            diversified.add(
                item.copy(
                    score = adjustedScore,
                    diversityPenalty = artistPenalty,
                )
            )

            for (artistId in artistIds) {
                artistCount[artistId] = (artistCount[artistId] ?: 0) + 1
            }
        }

        return diversified
            .sortedByDescending { it.score }
            .take(limit)
    }

    private fun computeRecencyPlaySignal(songId: String, profile: TasteProfile): Float {
        val playCount = profile.playCounts[songId] ?: 0
        if (playCount == 0) return 0f

        val recentIdx = profile.recentSongIds.indexOf(songId)
        val recencyFactor = if (recentIdx >= 0) {
            exp(-recentIdx.toDouble() / 10.0).toFloat()
        } else {
            0f
        }

        val countFactor = min(playCount.toFloat() / 20f, 1f)

        return (countFactor * 0.5f + recencyFactor * 0.5f).coerceIn(0f, 1f)
    }

    private fun computeArtistSignal(song: Song, profile: TasteProfile): Float {
        if (profile.topArtistIds.isEmpty()) return 0f

        var maxAffinity = 0f
        for (artist in song.artists) {
            val affinity = profile.topArtistIds[artist.id] ?: 0f
            val nameAffinity = profile.topArtistNames[artist.name] ?: 0f
            val combined = max(affinity, nameAffinity)
            if (combined > maxAffinity) {
                maxAffinity = combined
            }
        }

        val maxInProfile = profile.topArtistIds.values.maxOrNull() ?: 1f
        return (maxAffinity / max(maxInProfile, 1f)).coerceIn(0f, 1f)
    }

    private fun computeFeedbackSignal(songId: String, profile: TasteProfile): Float {
        var score = 0f

        if (songId in profile.likedSongIds) {
            score += 1.0f
        }

        val skipRatio = profile.skipRatios[songId]
        if (skipRatio != null && skipRatio > 0f) {
            score -= (skipRatio + 0.1f)
        }

        val conversionRate = profile.conversionRates[songId] ?: 0f
        if (conversionRate > 0f) {
            score += min(conversionRate * 0.5f, 1.5f)
        }

        return score.coerceIn(-1f, 2f) / 2f
    }

    private fun computeCoOccurrenceSignal(songId: String, profile: TasteProfile): Float {
        val recentSet = profile.recentSongIds.take(10).toSet()
        return if (songId in recentSet) 0.5f else 0.2f
    }

    private fun computeNoveltySignal(songId: String, profile: TasteProfile): Float {
        val dailyCount = profile.dailyPlayCounts[songId] ?: 0
        val playCount = profile.playCounts[songId] ?: 0

        val recentlyPlayed = songId in profile.recentlyPlayedSet

        return when {
            recentlyPlayed && dailyCount > 3 -> -0.5f
            recentlyPlayed && dailyCount > 0 -> 0.0f
            playCount > 5 -> 0.3f
            playCount > 2 -> 0.5f
            playCount > 0 -> 0.7f
            else -> 1.0f
        }
    }

    private fun computeExplorationSignal(source: CandidateSource, profile: TasteProfile): Float {
        val noise = Random.nextFloat() * 0.5f

        return when (source) {
            CandidateSource.RELATED -> 0.2f + noise
            CandidateSource.ARTIST_DISCOVERY -> 0.3f + noise
            CandidateSource.FORGOTTEN -> 0.4f + noise
            CandidateSource.METADATA_SIMILAR -> 0.3f + noise
            CandidateSource.EXPLORATION -> 0.5f + noise
            CandidateSource.AUDIO_SIMILAR -> 0.35f + noise
        }
    }

    private fun computeAudioFitSignal(songId: String, centroid: FloatArray?): Float {
        if (centroid == null) return 0f
        val entity = audioFeatureCache.getCached(songId) ?: return 0f
        val vec = entity.toVector()
        var dot = 0f
        var na = 0f
        var nb = 0f
        for (i in centroid.indices) {
            dot += centroid[i] * vec[i]
            na += centroid[i] * centroid[i]
            nb += vec[i] * vec[i]
        }
        val denom = sqrt(na) * sqrt(nb)
        return if (denom == 0f) 0f else (dot / denom).coerceIn(0f, 1f)
    }

    private fun computeMetadataSignal(song: Song, profile: TasteProfile): Float {
        var score = 0f

        if (song.song.duration > 0) {
            val durMs = song.song.duration * 1000.0
            val deviation = abs(durMs - profile.preferredDurationAvg)
            val durationFit = if (profile.preferredDurationStd > 0) {
                (1f - (deviation / (profile.preferredDurationStd * 3)).toFloat()).coerceIn(0f, 1f)
            } else {
                0.5f
            }
            score += durationFit * 0.6f
        }

        if (song.song.year != null && song.song.year > 0 && profile.preferredDecades.isNotEmpty()) {
            val decade = (song.song.year / 10) * 10
            val decadeFit = profile.preferredDecades[decade] ?: 0f
            val maxDecade = profile.preferredDecades.values.maxOrNull() ?: 1f
            score += (decadeFit / max(maxDecade, 1f)) * 0.4f
        }

        return score.coerceIn(0f, 1f)
    }
}

data class ScoredSong(
    val song: Song,
    val score: Float,
    val source: CandidateSource,
    val recencyPlaySignal: Float = 0f,
    val artistSignal: Float = 0f,
    val feedbackSignal: Float = 0f,
    val coOccurrenceSignal: Float = 0f,
    val noveltySignal: Float = 0f,
    val explorationSignal: Float = 0f,
    val metadataSignal: Float = 0f,
    val audioFitSignal: Float = 0f,
    val diversityPenalty: Float = 0f,
)
