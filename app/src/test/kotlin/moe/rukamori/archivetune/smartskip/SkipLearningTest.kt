/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.smartskip

import moe.rukamori.archivetune.db.entities.SeekRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SkipLearningTest {
    private val track = 240_000L

    private fun seek(
        fromMs: Long,
        toMs: Long,
    ) = SeekRecord(songId = "song", fromMs = fromMs, toMs = toMs, at = 0)

    @Test
    fun learnsFromConsistentIntroSkips() {
        val learned =
            SkipLearning.learnIntro(
                track,
                listOf(seek(0, 18_000), seek(2_000, 20_000), seek(1_000, 22_000)),
            )

        assertEquals(20_000L, learned!!.introUntilMs)
        assertEquals(3, learned.samples)
    }

    @Test
    fun refusesTwoSamples() {
        assertNull(SkipLearning.learnIntro(track, listOf(seek(0, 20_000), seek(0, 20_000))))
    }

    @Test
    fun refusesScatteredLandings() {
        val learned =
            SkipLearning.learnIntro(
                track,
                listOf(seek(0, 5_000), seek(0, 40_000), seek(0, 90_000)),
            )

        assertNull(learned)
    }

    @Test
    fun refusesJumpsPastHalfway() {
        // "I'm done with this song" is a skip, not an intro cut.
        val learned =
            SkipLearning.learnIntro(
                track,
                listOf(seek(0, 150_000), seek(0, 155_000), seek(0, 160_000)),
            )

        assertNull(learned)
    }

    @Test
    fun refusesSeeksThatStartMidSong() {
        val learned =
            SkipLearning.learnIntro(
                track,
                listOf(seek(60_000, 90_000), seek(70_000, 95_000), seek(65_000, 92_000)),
            )

        assertNull(learned)
    }

    @Test
    fun refusesShortsTracks() {
        assertNull(
            SkipLearning.learnIntro(
                60_000L,
                listOf(seek(0, 20_000), seek(0, 20_000), seek(0, 20_000)),
            ),
        )
    }

    @Test
    fun capsHowMuchOfASongCanBeSkipped() {
        val learned =
            SkipLearning.learnIntro(
                400_000L,
                listOf(seek(0, 90_000), seek(0, 92_000), seek(0, 94_000)),
            )

        assertNull(learned)
    }
}
