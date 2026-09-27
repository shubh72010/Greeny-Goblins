/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

class SongHidesTest {
    private val now = 1_000_000L

    @Test
    fun roundTripsActiveHide() {
        val raw = setSongHide("", "abc", 7.days, now = now)

        assertEquals(setOf("abc"), parseSongHides(raw, now = now).keys)
        assertEquals(now + 7.days.inWholeMilliseconds, parseSongHides(raw, now = now).getValue("abc"))
    }

    @Test
    fun dropsLapsedHidesOnRead() {
        val raw = setSongHide("", "abc", 1.hours, now = now)

        assertTrue(parseSongHides(raw, now = now + 2.hours.inWholeMilliseconds).isEmpty())
    }

    @Test
    fun keepingOneHidePreservesTheOther() {
        val raw = setSongHide(setSongHide("", "abc", 1.days, now = now), "xyz", 1.days, now = now)

        assertEquals(setOf("abc", "xyz"), parseSongHides(raw, now = now).keys)
    }

    @Test
    fun nullDurationClearsHide() {
        val raw = setSongHide("", "abc", 1.days, now = now)

        assertTrue(parseSongHides(setSongHide(raw, "abc", null, now = now), now = now).isEmpty())
    }

    @Test
    fun ignoresMalformedEntries() {
        assertTrue(parseSongHides("abc,,:1000,def:notanumber,", now = now).isEmpty())
    }
}
