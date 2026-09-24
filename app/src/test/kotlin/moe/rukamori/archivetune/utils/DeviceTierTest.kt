/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceTierTest {
    private val gb = 1024L * 1024 * 1024

    @Test
    fun twoGbOppoIsConstrained() {
        assertEquals(DeviceTier.CONSTRAINED, classifyDeviceTier(2 * gb, false))
    }

    @Test
    fun lowRamFlagForcesConstrainedRegardlessOfRam() {
        assertEquals(DeviceTier.CONSTRAINED, classifyDeviceTier(8 * gb, true))
    }

    @Test
    fun fourGbRedmiIsReduced() {
        assertEquals(DeviceTier.REDUCED, classifyDeviceTier(4 * gb, false))
    }

    @Test
    fun twelveGbFlagshipIsNormal() {
        assertEquals(DeviceTier.NORMAL, classifyDeviceTier(12 * gb, false))
    }

    @Test
    fun budgetsShrinkMonotonicallyWithTier() {
        val (c, r, n) =
            listOf(
                DeviceTier.CONSTRAINED.budgets(),
                DeviceTier.REDUCED.budgets(),
                DeviceTier.NORMAL.budgets(),
            )
        check(c.coilMemoryPercent <= r.coilMemoryPercent)
        check(r.coilMemoryPercent <= n.coilMemoryPercent)
        check(c.imageDiskMb <= r.imageDiskMb)
        check(r.imageDiskMb <= n.imageDiskMb)
        check(c.bufferMaxMs <= r.bufferMaxMs)
        check(r.bufferMaxMs <= n.bufferMaxMs)
        check(c.fullscreenArtCapPx <= r.fullscreenArtCapPx)
        check(r.fullscreenArtCapPx <= n.fullscreenArtCapPx)
    }
}
