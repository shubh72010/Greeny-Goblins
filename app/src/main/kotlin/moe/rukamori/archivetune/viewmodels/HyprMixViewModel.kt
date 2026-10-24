/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.LearnedSkip
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.smartskip.HyprMixScoring
import moe.rukamori.archivetune.smartskip.SkipLearning
import javax.inject.Inject

@HiltViewModel
class HyprMixViewModel
    @Inject
    constructor(
        private val database: MusicDatabase,
    ) : ViewModel() {
        private val _state = MutableStateFlow(HyprMixUiState())
        val state: StateFlow<HyprMixUiState> = _state

        init {
            refresh()
        }

        fun refresh() {
            viewModelScope.launch {
                val loaded = withContext(Dispatchers.IO) { load() }
                _state.value = loaded
            }
        }

        /** User picked an exact start point (0 = never skip this song). */
        fun setIntro(
            songId: String,
            introUntilMs: Long,
        ) {
            viewModelScope.launch {
                withContext(Dispatchers.IO) {
                    database.insert(
                        LearnedSkip(
                            songId = songId,
                            introUntilMs = introUntilMs,
                            samples = 0,
                            manual = true,
                            updatedAt = System.currentTimeMillis(),
                        ),
                    )
                }
                refresh()
            }
        }

        /** Drops the manual override and recomputes from the recorded evidence. */
        fun resetIntro(songId: String) {
            viewModelScope.launch {
                withContext(Dispatchers.IO) {
                    database.deleteLearnedSkip(songId)
                    val durationMs = database.getSongById(songId)?.song?.duration?.takeIf { it > 0 }?.times(1000L) ?: 0L
                    val learned = SkipLearning.learnIntro(durationMs, database.seekRecords(songId))
                    if (learned != null) {
                        database.insert(
                            LearnedSkip(
                                songId = songId,
                                introUntilMs = learned.introUntilMs,
                                samples = learned.samples,
                                manual = false,
                                updatedAt = System.currentTimeMillis(),
                            ),
                        )
                    }
                }
                refresh()
            }
        }

        fun forgetEverything() {
            viewModelScope.launch {
                withContext(Dispatchers.IO) {
                    database.clearPlaySessions()
                    database.clearSeekRecords()
                    database.clearLearnedSkips()
                }
                refresh()
            }
        }

        private suspend fun load(): HyprMixUiState {
            val now = System.currentTimeMillis()
            val cutoff = now - RETENTION_MS
            database.prunePlaySessions(cutoff)
            database.pruneSeekRecords(cutoff)

            val sessions = database.playSessionsSince(cutoff)
            val candidates = HyprMixScoring.rankDetailed(sessions, now)
            if (candidates.isEmpty()) return HyprMixUiState(loading = false, playCount = sessions.size)

            val songsById = database.getSongsByIds(candidates.map { it.songId }).associateBy { it.id }
            val rules = database.learnedSkips().associateBy { it.songId }

            return HyprMixUiState(
                loading = false,
                playCount = sessions.size,
                entries =
                    candidates.mapNotNull { candidate ->
                        val song = songsById[candidate.songId] ?: return@mapNotNull null
                        val rule = rules[candidate.songId]
                        HyprMixEntry(
                            song = song,
                            candidate = candidate,
                            introUntilMs = rule?.introUntilMs ?: 0L,
                            manual = rule?.manual == true,
                            samples = rule?.samples ?: 0,
                        )
                    },
            )
        }

        companion object {
            private const val RETENTION_MS = 180L * 24 * 60 * 60 * 1000
        }
    }

@androidx.compose.runtime.Immutable
data class HyprMixEntry(
    val song: Song,
    val candidate: HyprMixScoring.Candidate,
    val introUntilMs: Long,
    val manual: Boolean,
    val samples: Int,
)

@androidx.compose.runtime.Immutable
data class HyprMixUiState(
    val loading: Boolean = true,
    val entries: List<HyprMixEntry> = emptyList(),
    val playCount: Int = 0,
)
