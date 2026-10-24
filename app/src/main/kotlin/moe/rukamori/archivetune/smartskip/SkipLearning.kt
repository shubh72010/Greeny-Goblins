/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.smartskip

import kotlin.math.abs
import moe.rukamori.archivetune.db.entities.SeekRecord

/**
 * Turns raw forward seeks into one conservative "start this song here" point.
 *
 * Being wrong here ruins a song, so "several people skip the intro" is not enough
 * evidence. A rule needs repeat, agreement, and a hard ceiling: jumping to the
 * chorus once, or three jumps to three different places, learns nothing.
 */
object SkipLearning {
    /** Short tracks have no meaningful structure to learn. */
    const val MIN_TRACK_MS = 90_000L

    const val MIN_SKIP_MS = 3_000L

    /** Only seeks that start at the head of the song teach us about the intro. */
    const val INTRO_SEEK_MAX_FROM_MS = 8_000L

    const val MIN_SAMPLES = 3

    /** Median absolute deviation allowed between the learn points. */
    const val MAX_SPREAD_MS = 8_000L

    /** Never auto-skip more than a minute, whatever the data says. */
    const val MAX_INTRO_MS = 60_000L

    data class Intro(
        val introUntilMs: Long,
        val samples: Int,
    )

    fun learnIntro(
        durationMs: Long,
        seeks: List<SeekRecord>,
    ): Intro? {
        if (durationMs < MIN_TRACK_MS) return null

        val landings =
            seeks
                .filter { it.fromMs <= INTRO_SEEK_MAX_FROM_MS }
                .filter { it.toMs - it.fromMs >= MIN_SKIP_MS }
                // A skip past the halfway point is "I'm done with this song", not an intro cut.
                .filter { it.toMs <= durationMs / 2 }
                .map { it.toMs }

        if (landings.size < MIN_SAMPLES) return null

        val median = landings.median()
        val spread = landings.map { abs(it - median) }.median()
        if (spread > MAX_SPREAD_MS) return null
        if (median < MIN_SKIP_MS || median > MAX_INTRO_MS) return null

        return Intro(introUntilMs = median, samples = landings.size)
    }

    private fun List<Long>.median(): Long {
        val sorted = sorted()
        return sorted[sorted.size / 2]
    }
}
