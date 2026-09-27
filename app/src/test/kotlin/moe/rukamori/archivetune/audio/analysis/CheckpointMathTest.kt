/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.audio.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckpointMathTest {
    @Test
    fun rejectsTracksUnder30s() {
        assertFalse(CheckpointMath.isEligible(29_999L))
        assertTrue(CheckpointMath.isEligible(30_000L))
    }

    @Test
    fun trailingWindowClampsAtTrackStart() {
        val w = CheckpointMath.windowFor(durationMs = 240_000L, positionMs = 48_000L)
        assertEquals(45_000L, w.startMs)
        assertEquals(48_000L, w.endMs)
    }

    @Test
    fun windowNeverRunsPastDuration() {
        val w = CheckpointMath.windowFor(durationMs = 31_000L, positionMs = 31_000L)
        assertEquals(28_000L, w.startMs)
        assertEquals(31_000L, w.endMs)
    }

    @Test
    fun firesEachCheckpointOnce() {
        val dur = 240_000L
        val f1 = CheckpointMath.crossedFractions(0L, 50_000L, dur, CheckpointMath.BASE_FRACTIONS, emptySet())
        assertEquals(listOf(0.2), f1)
        val f2 = CheckpointMath.crossedFractions(50_000L, 130_000L, dur, CheckpointMath.BASE_FRACTIONS, setOf(0.2))
        assertEquals(listOf(0.5), f2)
        val none = CheckpointMath.crossedFractions(130_000L, 140_000L, dur, CheckpointMath.BASE_FRACTIONS, setOf(0.2, 0.5))
        assertTrue(none.isEmpty())
    }

    @Test
    fun seekBackRearmsCheckpoint() {
        // crossed set managed by sampler; math fires again once unfired
        val dur = 240_000L
        val again = CheckpointMath.crossedFractions(0L, 50_000L, dur, CheckpointMath.BASE_FRACTIONS, setOf(0.5))
        assertEquals(listOf(0.2), again)
    }

    @Test
    fun extraSampleTargetsLargestGap() {
        // fired 0.2 + 0.5: largest gap is [0.5, 1.0] → midpoint 0.75
        assertEquals(0.75, CheckpointMath.largestGapMidpoint(setOf(0.2, 0.5)), 1e-9)
        // all base fired: gaps ~0.2/0.3/0.3/0.2 — float ordering picks [0.5,0.8] → 0.65
        assertEquals(0.65, CheckpointMath.largestGapMidpoint(setOf(0.2, 0.5, 0.8)), 1e-9)
    }
}
