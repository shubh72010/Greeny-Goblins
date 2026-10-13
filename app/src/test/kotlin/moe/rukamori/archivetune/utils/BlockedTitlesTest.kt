/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.db.entities.SongEntity
import moe.rukamori.archivetune.extensions.filterBlockedTitles
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.extensions.containsBlockedTitleWord
import moe.rukamori.archivetune.extensions.filterBlockedTitles as filterYouTubeBlockedTitles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockedTitlesTest {
    private fun song(
        id: String,
        title: String,
    ) = Song(
        song = SongEntity(id = id, title = title),
        artists = emptyList(),
    )

    @Test
    fun matchesMontagemAndFunkCaseInsensitively() {
        assertTrue("MONTAGEM B ".containsBlockedTitleWord())
        assertTrue("funk carioca".containsBlockedTitleWord())
        assertTrue("Montagem Funk 2024".containsBlockedTitleWord())
    }

    @Test
    fun leavesCleanTitlesAlone() {
        assertFalse("Ribbon".containsBlockedTitleWord())
        assertFalse("".containsBlockedTitleWord())
    }

    @Test
    fun filtersSongEntitiesWhenEnabled() {
        val songs = listOf(song("1", "Ribbon"), song("2", "Montagem B"), song("3", "funk mix"))
        assertEquals(listOf("1"), songs.filterBlockedTitles(true).map { it.id })
    }

    @Test
    fun keepsEverythingWhenDisabled() {
        val songs = listOf(song("1", "Ribbon"), song("2", "Montagem B"))
        assertEquals(2, songs.filterBlockedTitles(false).size)
    }

    @Test
    fun filtersYouTubeItemsByTitle() {
        val items =
            listOf(
                SongItem(id = "1", title = "Ribbon", artists = emptyList(), thumbnail = ""),
                SongItem(id = "2", title = "MONTAGEM F", artists = emptyList(), thumbnail = ""),
            )
        assertEquals(listOf("1"), items.filterYouTubeBlockedTitles(true).map { it.id })
    }
}
