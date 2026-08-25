/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsFilterTest {
    @Test
    fun skipIntervals_wordTimestamps_mutesOnlyTheWord() {
        val entries =
            listOf(
                LyricsEntry(
                    time = 10_000,
                    text = "hello world",
                    words =
                        listOf(
                            WordTimestamp(text = "hello", startTime = 10.0, endTime = 10.5),
                            WordTimestamp(text = "world", startTime = 10.6, endTime = 11.0),
                        ),
                ),
            )

        val intervals = LyricsFilter.skipIntervals(entries, listOf("world"))

        assertEquals(listOf(10_600L to 11_000L), intervals)
    }

    @Test
    fun skipIntervals_lineSynced_mutesOnlyEstimatedWordWindow_notWholeLine() {
        val entries =
            listOf(
                LyricsEntry(time = 0, text = "you are my sunshine"),
                LyricsEntry(time = 4000, text = "all clean here"),
                LyricsEntry(time = 8000, text = "end"),
            )

        val intervals = LyricsFilter.skipIntervals(entries, listOf("sunshine"))

        // 19 chars total, "sunshine" at index 11..19 → [11/20, 19/20] of 4s ≈ [2200, 3800]
        assertEquals(1, intervals.size)
        val (s, e) = intervals.single()
        assertTrue("start $s should be well after line start", s >= 2000)
        assertTrue("end $e should be before next line", e <= 4200)
        assertTrue("window must be narrower than the line", e - s < 3000)
    }

    @Test
    fun skipIntervals_lineSynced_multipleOccurrences_merged() {
        val text = "shit happens, shit happens again"
        val entries =
            listOf(
                LyricsEntry(time = 0, text = text),
                LyricsEntry(time = 10_000, text = "clean"),
            )

        val intervals = LyricsFilter.skipIntervals(entries, listOf("shit"))

        assertEquals(2, intervals.size)
        assertTrue(intervals[0].second < intervals[1].first)
    }

    @Test
    fun skipIntervals_lineSynced_syllableWeighting_notCharProportional() {
        // "I fucking love it": syllables I=1, fucking=2, love=1, it=1 → total 5,
        // so "fucking" spans [20%, 60%]; char-proportional would give [~14%, ~64%].
        val entries =
            listOf(
                LyricsEntry(time = 10_000, text = "I fucking love it"),
                LyricsEntry(time = 15_000, text = "clean"),
            )

        val intervals = LyricsFilter.skipIntervals(entries, listOf("fucking"))

        assertEquals(listOf(11_000L to 13_000L), intervals)
    }

    @Test
    fun skipIntervals_matchesProviderCensoredRenderings() {
        val entries =
            listOf(
                LyricsEntry(time = 0, text = "ni**a got arrested"),
                LyricsEntry(time = 5000, text = "clean"),
            )

        val intervals = LyricsFilter.skipIntervals(entries, LyricsFilter.defaultWords)

        assertEquals(1, intervals.size)
    }

    @Test
    fun skipIntervals_caseInsensitive() {
        val entries =
            listOf(
                LyricsEntry(time = 0, text = "Damn right"),
                LyricsEntry(time = 5000, text = "clean"),
            )

        val intervals = LyricsFilter.skipIntervals(entries, listOf("damn"))

        assertEquals(1, intervals.size)
    }
}
