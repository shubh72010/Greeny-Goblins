package moe.rukamori.archivetune.taster

import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.AudioFeatureEntity
import moe.rukamori.archivetune.spotify.Spotify
import moe.rukamori.archivetune.spotify.SpotifyMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sqrt
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioFeatureCache @Inject constructor(
    private val database: MusicDatabase,
    private val spotify: Spotify,
) {
    fun getCached(songId: String): AudioFeatureEntity? {
        return database.getAudioFeature(songId)
    }

    fun getCachedBatch(songIds: List<String>): Map<String, AudioFeatureEntity> {
        if (songIds.isEmpty()) return emptyMap()
        return database.getAudioFeatures(songIds).associateBy { it.songId }
    }

    suspend fun fetchForTrack(
        videoId: String,
        title: String,
        artist: String,
    ): AudioFeatureEntity? = withContext(Dispatchers.IO) {
        val cached = database.getAudioFeature(videoId)
        if (cached != null) return@withContext cached

        val spotifyId = resolveSpotifyTrackId(title, artist) ?: return@withContext null

        val features = spotify.audioFeatures(spotifyId).getOrNull() ?: return@withContext null

        val entity = AudioFeatureEntity(
            songId = videoId,
            danceability = features.danceability,
            energy = features.energy,
            key = features.key,
            loudness = features.loudness,
            mode = features.mode,
            speechiness = features.speechiness,
            acousticness = features.acousticness,
            instrumentalness = features.instrumentalness,
            liveness = features.liveness,
            valence = features.valence,
            tempo = features.tempo,
            timeSignature = features.timeSignature,
            fetchedAt = System.currentTimeMillis(),
        )
        database.insert(entity)
        entity
    }

    private suspend fun resolveSpotifyTrackId(
        title: String,
        artist: String,
    ): String? {
        val query = "$artist $title"
        val searchResult = spotify.search(query, limit = 5).getOrNull() ?: return null

        val tracks = searchResult.tracks?.items ?: return null
        if (tracks.isEmpty()) return null

        val precomputed = SpotifyMapper.precompute(title, artist, 0)
        var bestScore = 0.0
        var bestId: String? = null
        for (track in tracks) {
            val candidateArtist = track.artists.firstOrNull()?.name.orEmpty()
            val score = SpotifyMapper.matchScorePrecomputed(
                precomputed = precomputed,
                candidateTitle = track.name,
                candidateArtist = candidateArtist,
                candidateDurationSec = if (track.durationMs > 0) track.durationMs / 1000 else null,
            )
            if (score > bestScore) {
                bestScore = score
                bestId = track.id
            }
            if (score >= SpotifyMapper.earlyExitThreshold()) break
        }
        return bestId?.takeIf { it.isNotBlank() }
    }

    fun computeCentroid(songIds: Collection<String>): FloatArray? {
        val features = database.getAudioFeatures(songIds.toList())
        if (features.isEmpty()) return null

        val dims = features.first().toVector().size
        val centroid = FloatArray(dims)
        for (f in features) {
            val vec = f.toVector()
            for (i in vec.indices) {
                centroid[i] += vec[i]
            }
        }
        for (i in centroid.indices) {
            centroid[i] /= features.size
        }
        return centroid
    }

    fun findAudioSimilarCandidates(
        centroid: FloatArray,
        candidateSongIds: List<String>,
        topN: Int = 20,
    ): List<String> {
        val features = database.getAudioFeatures(candidateSongIds)
        if (features.isEmpty()) return emptyList()

        val scored = features.map { f ->
            f.songId to cosineSimilarity(centroid, f.toVector())
        }

        return scored
            .sortedByDescending { it.second }
            .take(topN)
            .map { it.first }
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
