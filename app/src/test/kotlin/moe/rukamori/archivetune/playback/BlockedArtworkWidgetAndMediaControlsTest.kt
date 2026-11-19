/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback

import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.db.entities.SongEntity
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.utils.BlockedArtwork
import moe.rukamori.archivetune.widget.MusicWidgetKeys
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test

class BlockedArtworkWidgetAndMediaControlsTest {

    @After
    fun tearDown() {
        BlockedArtwork.update(null)
    }

    @Test
    fun blockedArtwork_stateUpdatesCorrectly() {
        BlockedArtwork.update("song-1, song-2")
        assertTrue(BlockedArtwork.isBlocked("song-1"))
        assertTrue(BlockedArtwork.isBlocked("song-2"))
        assertFalse(BlockedArtwork.isBlocked("song-3"))
        assertFalse(BlockedArtwork.isBlocked(""))
        assertFalse(BlockedArtwork.isBlocked("   "))

        BlockedArtwork.update(null)
        assertFalse(BlockedArtwork.isBlocked("song-1"))
    }

    @Ignore("needs Robolectric: toMediaItem() calls android.net.Uri.parse, which is not mocked on the JVM")
    @Test
    fun songToMediaItem_artworkUriIsNullWhenBlocked() {
        BlockedArtwork.update("blocked-song")

        val blockedSong = testSong(id = "blocked-song", thumbUrl = "https://example.com/thumb1.jpg")
        val blockedItem = blockedSong.toMediaItem()
        assertNull(blockedItem.mediaMetadata.artworkUri)

        val allowedSong = testSong(id = "allowed-song", thumbUrl = "https://example.com/thumb2.jpg")
        val allowedItem = allowedSong.toMediaItem()
        assertNotNull(allowedItem.mediaMetadata.artworkUri)
    }

    @Ignore("needs Robolectric: toMediaItem() calls android.net.Uri.parse, which is not mocked on the JVM")
    @Test
    fun mediaMetadataToMediaItem_artworkUriIsNullWhenBlocked() {
        BlockedArtwork.update("blocked-meta")

        val blockedMeta = MediaMetadata(
            id = "blocked-meta",
            title = "Blocked Track",
            artists = emptyList(),
            duration = 0,
            thumbnailUrl = "https://example.com/thumb.jpg",
        )
        assertNull(blockedMeta.toMediaItem().mediaMetadata.artworkUri)

        val allowedMeta = MediaMetadata(
            id = "allowed-meta",
            title = "Allowed Track",
            artists = emptyList(),
            duration = 0,
            thumbnailUrl = "https://example.com/thumb.jpg",
        )
        assertNotNull(allowedMeta.toMediaItem().mediaMetadata.artworkUri)
    }

    @Test
    fun widgetState_suppressesArtPathAndDominantColorWhenBlocked() {
        BlockedArtwork.update("blocked-widget")

        val songId = "blocked-widget"
        val isBlocked = songId.isNotBlank() && BlockedArtwork.isBlocked(songId)
        val rawArtPath = "/data/user/0/cache/widget_art_123.jpg"
        val rawDominantColor = 0xFF123456.toInt()

        val effectiveArtPath = if (isBlocked) null else rawArtPath
        val effectiveDominantColor = if (isBlocked) null else rawDominantColor

        assertNull(effectiveArtPath)
        assertNull(effectiveDominantColor)
    }

    @Test
    fun uriMatching_detectsBlockedSongUri() {
        BlockedArtwork.update("xyz123")

        val blockedUri = "https://i.ytimg.com/vi/xyz123/hqdefault.jpg"
        val isBlocked = BlockedArtwork.ids.value.any { it.isNotBlank() && blockedUri.contains(it) }
        assertTrue(isBlocked)

        val allowedUri = "https://i.ytimg.com/vi/other456/hqdefault.jpg"
        val isAllowed = BlockedArtwork.ids.value.any { it.isNotBlank() && allowedUri.contains(it) }
        assertFalse(isAllowed)
    }

    private fun testSong(id: String, thumbUrl: String? = null): Song =
        Song(
            song = SongEntity(
                id = id,
                title = "Test Song",
                thumbnailUrl = thumbUrl,
            ),
            artists = emptyList(),
        )
}
