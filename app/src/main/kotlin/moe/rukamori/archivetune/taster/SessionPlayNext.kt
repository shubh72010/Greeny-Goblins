package moe.rukamori.archivetune.taster

import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sqrt
import javax.inject.Inject
import javax.inject.Singleton

data class SessionContext(
    val songIds: List<String>,
    val currentSongId: String,
)

@Singleton
class SessionPlayNext @Inject constructor(
    private val database: MusicDatabase,
    private val audioFeatureCache: AudioFeatureCache,
) {
    suspend fun next(
        session: SessionContext,
        limit: Int = 3,
    ): List<Song> = withContext(Dispatchers.IO) {
        val excludeIds = session.songIds.toMutableSet()
        excludeIds.add(session.currentSongId)

        val sessionFeatures = audioFeatureCache.getCachedBatch(
            session.songIds.filter { it != session.currentSongId }
        )

        val centroid = if (sessionFeatures.size >= 2) {
            val dims = sessionFeatures.values.first().toVector().size
            val sum = FloatArray(dims)
            for (f in sessionFeatures.values) {
                val vec = f.toVector()
                for (i in vec.indices) sum[i] += vec[i]
            }
            for (i in sum.indices) sum[i] /= sessionFeatures.size
            sum
        } else {
            audioFeatureCache.computeCentroid(session.songIds)
        }

        if (centroid == null) return@withContext emptyList()

        val cachedSongIds = try {
            database.allCachedAudioFeatureSongIds().toSet()
        } catch (_: Exception) {
            emptySet()
        }

        val candidates = cachedSongIds
            .filter { it !in excludeIds }
            .let { ids -> audioFeatureCache.findAudioSimilarCandidates(centroid, ids, limit * 3) }

        val sessionArtistIds = mutableSetOf<String>()
        val allSessionSongs = database.getSongsByIds(session.songIds.toList())
        for (s in allSessionSongs) {
            for (artist in s.artists) {
                sessionArtistIds.add(artist.id)
            }
        }

        val scored = candidates.mapNotNull { candidateId ->
            val entity = audioFeatureCache.getCached(candidateId) ?: return@mapNotNull null
            val song = database.getSongById(candidateId) ?: return@mapNotNull null

            val audioScore = cosineSimilarity(centroid, entity.toVector())

            val currentEntity = audioFeatureCache.getCached(session.currentSongId)
            val flowScore = if (currentEntity != null) {
                cosineSimilarity(currentEntity.toVector(), entity.toVector())
            } else {
                audioScore
            }

            val artistPenalty = song.artists.any { it.id in sessionArtistIds }
                .let { if (it) 0.3f else 0f }

            val finalScore = audioScore * 0.4f + flowScore * 0.4f - artistPenalty

            candidateId to finalScore
        }

        val topIds = scored
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }

        database.getSongsByIds(topIds)
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f
        var dot = 0f
        var na = 0f
        var nb = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            na += a[i] * a[i]
            nb += b[i] * b[i]
        }
        val denom = sqrt(na) * sqrt(nb)
        return if (denom == 0f) 0f else (dot / denom).coerceIn(0f, 1f)
    }
}
