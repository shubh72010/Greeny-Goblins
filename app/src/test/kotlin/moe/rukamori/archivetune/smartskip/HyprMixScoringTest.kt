/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.smartskip

import kotlin.random.Random
import moe.rukamori.archivetune.db.entities.PlaySession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HyprMixScoringTest {
    private val now = 1_800_000_000_000L
    private val minute = 60_000L

    private fun session(
        songId: String,
        startedAt: Long,
        playedMs: Long,
        endedNaturally: Boolean = true,
        durationMs: Long = 200_000L,
    ) = PlaySession(
        songId = songId,
        startedAt = startedAt,
        startPositionMs = 0,
        playedMs = playedMs,
        durationMs = durationMs,
        endedNaturally = endedNaturally,
    )

    private fun ranked(vararg sessions: PlaySession) =
        HyprMixScoring.rank(sessions.toList(), nowMs = now, random = Random(7))

    @Test
    fun repeatedlyCompletedSongOutranksOneThatWasSkipped() {
        val sessions =
            (0 until 5).map { i ->
                session("completed", startedAt = now - (i + 1) * 60 * minute, playedMs = 200_000)
            } + session("skipped", startedAt = now - 10 * minute, playedMs = 5_000, endedNaturally = false)

        val queue = ranked(*sessions.toTypedArray())

        assertEquals("completed", queue.first())
        assertTrue(queue.contains("skipped"))
    }

    @Test
    fun songSkippedEveryTimeIsDroppedEntirely() {
        val sessions =
            listOf(
                session("hated", startedAt = now - 30 * minute, playedMs = 4_000, endedNaturally = false),
                session("hated", startedAt = now - 10 * minute, playedMs = 6_000, endedNaturally = false),
            )

        assertFalse(ranked(*sessions.toTypedArray()).contains("hated"))
    }

    @Test
    fun arrivingAfterAnAbandonedSongBoostsAnEqualSong() {
        val sessions =
            buildList {
                (0 until 4).forEach { i ->
                    val offset = i * 90 * minute
                    add(session("abandoned", startedAt = now - offset - 2 * minute, playedMs = 4_000, endedNaturally = false))
                    add(session("wanted", startedAt = now - offset - minute, playedMs = 200_000))
                }
                (0 until 4).forEach { i ->
                    add(session("neutral", startedAt = now - i * 120 * minute - 45 * minute, playedMs = 200_000))
                }
            }

        val queue = ranked(*sessions.toTypedArray())

        assertTrue(queue.indexOf("wanted") < queue.indexOf("neutral"))
    }

    @Test
    fun anInterruptedPlayIsNotEnoughToBuryASong() {
        // 40% listened, then interrupted: zero completions, but only one sample.
        val sessions =
            listOf(
                session("interrupted", startedAt = now - 20 * minute, playedMs = 80_000, endedNaturally = false),
                session("other", startedAt = now - 200 * minute, playedMs = 200_000),
            )

        val queue = ranked(*sessions.toTypedArray())

        assertTrue(queue.contains("interrupted"))
    }

    @Test
    fun aSingleCompletionIsNeitherPromotedNorDropped() {
        val sessions = listOf(session("once", startedAt = now - 5 * minute, playedMs = 200_000))

        val queue = ranked(*sessions.toTypedArray())

        assertEquals(listOf("once"), queue)
    }

    @Test
    fun detailedRankingMatchesPlainRankingAndExposesItsEvidence() {
        val sessions =
            listOf(
                session("abandoned", startedAt = now - 12 * minute, playedMs = 4_000, endedNaturally = false),
                session("wanted", startedAt = now - 11 * minute, playedMs = 200_000),
                session("wanted", startedAt = now - 200 * minute, playedMs = 200_000),
            )

        val detailed = HyprMixScoring.rankDetailed(sessions, nowMs = now, random = Random(7))
        val plain = HyprMixScoring.rank(sessions, nowMs = now, random = Random(7))

        assertEquals(plain, detailed.map { it.songId })
        val wanted = detailed.first { it.songId == "wanted" }
        assertEquals(2, wanted.plays)
        assertEquals(2, wanted.completed)
        assertEquals(0, wanted.quickSkips)
        assertEquals(1, wanted.arrivals)
    }

    @Test
    fun buriedSongsAreAbsentFromTheDetailedRankingToo() {
        val sessions =
            listOf(
                session("hated", startedAt = now - 30 * minute, playedMs = 4_000, endedNaturally = false),
                session("hated", startedAt = now - 10 * minute, playedMs = 6_000, endedNaturally = false),
            )

        assertTrue(HyprMixScoring.rankDetailed(sessions, nowMs = now, random = Random(7)).isEmpty())
    }

    @Test
    fun arrivalCreditNeedsTheAbandonedSongInTheSameSession() {
        val abandoned = session("abandoned", startedAt = now - 12 * minute, playedMs = 4_000, endedNaturally = false)
        val sameSession =
            listOf(
                abandoned,
                session("wanted", startedAt = now - 11 * minute, playedMs = 200_000),
            )
        // Same pair, but hours apart: two separate listening sessions.
        val differentSession =
            listOf(
                abandoned,
                session("wanted", startedAt = now - 60 * minute, playedMs = 200_000),
            )

        val withCredit = HyprMixScoring.scoreOf(sameSession, "wanted", now, Random(7))!!
        val withoutCredit = HyprMixScoring.scoreOf(differentSession, "wanted", now, Random(7))!!

        assertTrue("expected an arrival bonus in one session", withCredit - withoutCredit > 0.5)
    }
}
