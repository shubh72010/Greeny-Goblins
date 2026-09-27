/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.audio.analysis

/**
 * Pure checkpoint math for the Greeny-Goblins analysis backbone.
 * No Android / Media3 dependency so it stays unit-testable.
 *
 * Locked decisions: 30s minimum duration, checkpoints at 20/50/80%,
 * 3s trailing PCM windows, 0.6 global confidence threshold.
 */
object CheckpointMath {
    const val MIN_DURATION_MS = 30_000L
    const val WINDOW_MS = 3_000L
    const val POLL_MS = 250L
    const val CONFIDENCE_THRESHOLD = 0.6f
    const val MAX_EXTRA_SAMPLES = 2

    val BASE_FRACTIONS = listOf(0.2, 0.5, 0.8)

    fun isEligible(durationMs: Long): Boolean = durationMs >= MIN_DURATION_MS

    data class WindowMs(val startMs: Long, val endMs: Long)

    /**
     * Trailing window ending at the checkpoint position, clamped to [0, durationMs].
     * Trailing (not centered): future audio is unavailable without delaying capture.
     */
    fun windowFor(durationMs: Long, positionMs: Long, windowMs: Long = WINDOW_MS): WindowMs {
        val end = positionMs.coerceIn(0L, durationMs)
        val start = (end - windowMs).coerceAtLeast(0L)
        return WindowMs(start, end)
    }

    /** Fractions whose checkpoint position was crossed since the previous poll. */
    fun crossedFractions(
        prevPosMs: Long,
        newPosMs: Long,
        durationMs: Long,
        fractions: List<Double>,
        fired: Set<Double>,
    ): List<Double> {
        if (newPosMs <= prevPosMs || durationMs <= 0) return emptyList()
        return fractions.filter { f ->
            f !in fired && prevPosMs < (durationMs * f).toLong() && newPosMs >= (durationMs * f).toLong()
        }
    }

    /**
     * Midpoint of the largest temporal gap between existing samples.
     * Used for adaptive extra sampling: sample where evidence is thinnest.
     */
    fun largestGapMidpoint(fired: Set<Double>): Double {
        val points = (listOf(0.0) + fired.sorted() + listOf(1.0)).distinct().sorted()
        var bestMid = 0.5
        var bestGap = -1.0
        for (i in 0 until points.size - 1) {
            val gap = points[i + 1] - points[i]
            if (gap > bestGap) {
                bestGap = gap
                bestMid = (points[i] + points[i + 1]) / 2.0
            }
        }
        return bestMid
    }
}
