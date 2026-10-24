/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.smartskip

import java.util.LinkedHashMap
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.random.Random
import moe.rukamori.archivetune.db.entities.PlaySession

/**
 * HyprMix queue ranking. A plain weighted score, no model: every term is
 * inspectable and every coefficient is covered by [HyprMixScoringTest].
 *
 * Completion and skip rates are Bayesian-smoothed toward 0.5, so one interrupted
 * session cannot promote or bury a song; the smoothing weight is what the
 * "minimum samples" rule would otherwise have to be.
 */
object HyprMixScoring {
    /** Plays separated by more than this belong to different listening sessions. */
    const val SESSION_GAP_MS = 30 * 60_000L

    /** Below this, position-based judgement (completed / quick skip) is meaningless. */
    const val MIN_TRACK_MS = 90_000L

    const val COMPLETE_FRACTION = 0.8
    const val QUICK_SKIP_FRACTION = 0.25

    const val MIN_PLAYS_FOR_EXCLUSION = 2
    const val EXCLUDE_COMPLETION_RATE = 0.3

    const val QUEUE_LIMIT = 50

    private const val PRIOR_RATE = 0.5
    private const val PRIOR_WEIGHT = 2.0
    private const val ARRIVAL_PRIOR_WEIGHT = 1.0
    private const val JITTER = 0.3

    fun rank(
        sessions: List<PlaySession>,
        nowMs: Long,
        limit: Int = QUEUE_LIMIT,
        random: Random = Random.Default,
    ): List<String> = rankDetailed(sessions, nowMs, limit, random).map { it.songId }

    /** One queue entry with the evidence behind its score, so the UI can show why. */
    data class Candidate(
        val songId: String,
        val score: Double,
        val plays: Int,
        val completed: Int,
        val quickSkips: Int,
        val arrivals: Int,
        val lastPlayedAt: Long,
    )

    fun rankDetailed(
        sessions: List<PlaySession>,
        nowMs: Long,
        limit: Int = QUEUE_LIMIT,
        random: Random = Random.Default,
    ): List<Candidate> =
        buildStats(sessions)
            .filterValues { !it.isBuryable() }
            .map { (songId, stat) ->
                Candidate(
                    songId = songId,
                    score = stat.score(nowMs, random),
                    plays = stat.plays,
                    completed = stat.completed,
                    quickSkips = stat.quickSkips,
                    arrivals = stat.arrivals,
                    lastPlayedAt = stat.lastPlayedAt,
                )
            }.sortedByDescending { it.score }
            .take(limit)

    /**
     * Score for one song, or null when it is buried. Exposed so the coefficients can be
     * asserted on directly instead of inferred from ordering.
     */
    fun scoreOf(
        sessions: List<PlaySession>,
        songId: String,
        nowMs: Long,
        random: Random = Random.Default,
    ): Double? = buildStats(sessions)[songId]?.takeIf { !it.isBuryable() }?.score(nowMs, random)

    private fun buildStats(sessions: List<PlaySession>): LinkedHashMap<String, Stats> {
        val ordering = sessions.sortedBy { it.startedAt }
        val stats = LinkedHashMap<String, Stats>()

        var previousEnd: Long? = null
        var previousSongId: String? = null
        var previousWasQuickSkip = false

        for (session in ordering) {
            val sameSession = previousEnd != null && session.startedAt - previousEnd <= SESSION_GAP_MS
            if (!sameSession) {
                previousSongId = null
                previousWasQuickSkip = false
            }

            val stat = stats.getOrPut(session.songId) { Stats() }
            val quickSkip = session.isQuickSkip()
            stat.plays++
            stat.lastPlayedAt = max(stat.lastPlayedAt, session.startedAt)
            if (session.isCompleted()) stat.completed++
            if (quickSkip) stat.quickSkips++
            // Arriving right after another song was abandoned is the closest thing
            // we have to an explicit "this is what I actually wanted".
            if (sameSession && previousWasQuickSkip && previousSongId != session.songId) {
                stat.arrivals++
            }

            previousEnd = session.startedAt + session.playedMs
            previousSongId = session.songId
            previousWasQuickSkip = quickSkip
        }

        return stats
    }

    private class Stats {
        var plays: Int = 0
        var completed: Int = 0
        var quickSkips: Int = 0
        var arrivals: Int = 0
        var lastPlayedAt: Long = 0L

        fun isBuryable(): Boolean =
            plays >= MIN_PLAYS_FOR_EXCLUSION && completed.toDouble() / plays < EXCLUDE_COMPLETION_RATE

        fun score(
            nowMs: Long,
            random: Random,
        ): Double {
            val plays = plays.toDouble()
            val completion = (completed + PRIOR_RATE * PRIOR_WEIGHT) / (plays + PRIOR_WEIGHT)
            val quickSkipRate = quickSkips / plays
            val arrivalRate = (arrivals + PRIOR_RATE * ARRIVAL_PRIOR_WEIGHT) / (plays + ARRIVAL_PRIOR_WEIGHT)
            val daysSincePlayed = (nowMs - lastPlayedAt).coerceAtLeast(0L) / 86_400_000.0
            val recency = exp(-daysSincePlayed / 30.0)

            return 1.0 * completion +
                0.6 * ln(1.0 + plays) +
                1.2 * arrivalRate -
                1.5 * quickSkipRate +
                0.4 * recency +
                (random.nextDouble() - 0.5) * JITTER
        }
    }

    private fun PlaySession.isCompleted(): Boolean =
        endedNaturally || (durationMs >= MIN_TRACK_MS && playedMs >= durationMs * COMPLETE_FRACTION)

    private fun PlaySession.isQuickSkip(): Boolean =
        !endedNaturally && durationMs >= MIN_TRACK_MS && playedMs < durationMs * QUICK_SKIP_FRACTION
}
