/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.smartskip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaySessionRecorderTest {
    private fun recorder() = PlaySessionRecorder()

    @Test
    fun accumulatesOnlyWhilePlaying() {
        val recorder = recorder()
        recorder.startItem("song", positionMs = 0, nowElapsedMs = 1_000, wallClockMs = 10)

        recorder.setPlaying(true, nowElapsedMs = 1_000)
        recorder.setPlaying(false, nowElapsedMs = 5_000) // 4s listened
        recorder.setPlaying(true, nowElapsedMs = 9_000)
        recorder.setPlaying(false, nowElapsedMs = 11_000) // 2s listened

        val finished = recorder.finishItem(endedNaturally = false, nowElapsedMs = 20_000)!!

        assertEquals(6_000L, finished.session.playedMs)
        assertEquals("song", finished.session.songId)
        assertTrue(!finished.session.endedNaturally)
    }

    @Test
    fun countsUnpausedPlaybackWhenItemEndsWhilePlaying() {
        val recorder = recorder()
        recorder.startItem("song", positionMs = 0, nowElapsedMs = 0, wallClockMs = 0)
        recorder.setPlaying(true, nowElapsedMs = 1_000)

        val finished = recorder.finishItem(endedNaturally = true, nowElapsedMs = 31_000)!!

        assertEquals(30_000L, finished.session.playedMs)
        assertTrue(finished.session.endedNaturally)
    }

    @Test
    fun recordsForwardSeeksAndIgnoresTheRest() {
        val recorder = recorder()
        recorder.startItem("song", positionMs = 0, nowElapsedMs = 0, wallClockMs = 0)

        recorder.recordSeek(fromMs = 1_000, toMs = 20_000, nowElapsedMs = 2_000, wallClockMs = 2_000) // kept
        recorder.recordSeek(fromMs = 30_000, toMs = 31_000, nowElapsedMs = 5_000, wallClockMs = 5_000) // too short
        recorder.recordSeek(fromMs = 60_000, toMs = 40_000, nowElapsedMs = 9_000, wallClockMs = 9_000) // backwards
        recorder.recordSeek(fromMs = 70_000, toMs = 90_000, nowElapsedMs = 20_000, wallClockMs = 20_000) // kept

        val seeks = recorder.finishItem(endedNaturally = false, nowElapsedMs = 25_000)!!.seeks

        assertEquals(2, seeks.size)
        assertEquals(20_000L, seeks[0].toMs)
        assertEquals(90_000L, seeks[1].toMs)
    }

    @Test
    fun deduplicatesOneSliderGesture() {
        val recorder = recorder()
        recorder.startItem("song", positionMs = 0, nowElapsedMs = 0, wallClockMs = 0)

        recorder.recordSeek(fromMs = 1_000, toMs = 20_000, nowElapsedMs = 10_000, wallClockMs = 10_000)
        recorder.recordSeek(fromMs = 20_000, toMs = 40_000, nowElapsedMs = 10_400, wallClockMs = 10_400)

        assertEquals(1, recorder.finishItem(endedNaturally = false, nowElapsedMs = 20_000)!!.seeks.size)
    }

    @Test
    fun keepsLastKnownDuration() {
        val recorder = recorder()
        recorder.startItem("song", positionMs = 0, nowElapsedMs = 0, wallClockMs = 0)
        recorder.setDuration(0) // player not ready yet
        recorder.setDuration(210_000)

        assertEquals(210_000L, recorder.finishItem(endedNaturally = true, nowElapsedMs = 1_000)!!.session.durationMs)
    }

    @Test
    fun finishingWithoutAnItemReturnsNull() {
        assertNull(recorder().finishItem(endedNaturally = false, nowElapsedMs = 0))
    }
}
