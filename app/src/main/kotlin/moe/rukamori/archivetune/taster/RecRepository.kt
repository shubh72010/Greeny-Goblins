package moe.rukamori.archivetune.taster

import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.RecExposure
import moe.rukamori.archivetune.db.entities.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecRepository @Inject constructor(
    private val database: MusicDatabase,
    private val tasteExtractor: TasteExtractor,
    private val candidatePool: CandidatePool,
    private val scorer: Scorer,
    private val sessionPlayNext: SessionPlayNext,
) {
    private val refreshTrigger = MutableStateFlow(0L)

    fun observeRecommendations(limit: Int = 20): Flow<List<Song>> {
        return refreshTrigger.map {
            runCatching {
                computeRecommendations(limit)
            }.getOrNull() ?: emptyList()
        }
    }

    fun refresh() {
        refreshTrigger.value = System.currentTimeMillis()
    }

    suspend fun computeRecommendations(limit: Int = 20): List<Song> = withContext(Dispatchers.IO) {
        val profile = tasteExtractor.extract()
        val candidates = candidatePool.generate(profile)
        val scored = scorer.score(candidates, profile, limit)
        recordExposure(scored)
        scored.map { it.song }
    }

    suspend fun playNext(
        currentSongId: String,
        sessionSongIds: List<String>,
        limit: Int = 3,
    ): List<Song> = withContext(Dispatchers.IO) {
        sessionPlayNext.next(
            session = SessionContext(
                songIds = sessionSongIds,
                currentSongId = currentSongId,
            ),
            limit = limit,
        )
    }

    private fun recordExposure(scored: List<ScoredSong>) {
        val now = System.currentTimeMillis()
        for (item in scored) {
            database.insert(
                RecExposure(
                    songId = item.song.song.id,
                    shownAt = now,
                )
            )
        }
    }
}
