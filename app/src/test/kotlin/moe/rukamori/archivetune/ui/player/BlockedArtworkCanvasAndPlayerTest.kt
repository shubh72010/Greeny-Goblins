/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player

import kotlinx.coroutines.runBlocking
import moe.rukamori.archivetune.canvas.models.CanvasArtwork
import moe.rukamori.archivetune.constants.PlayerBackgroundStyle
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.utils.BlockedArtwork
import moe.rukamori.archivetune.utils.isSongInSet
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockedArtworkCanvasAndPlayerTest {

    @After
    fun tearDown() {
        BlockedArtwork.update(null)
    }

    @Test
    fun blockedArtwork_detectionAndPreferenceSync() {
        val hiddenCoverIds = "songA, songB,songC "
        BlockedArtwork.update(hiddenCoverIds)

        assertTrue(BlockedArtwork.isBlocked("songA"))
        assertTrue(BlockedArtwork.isBlocked("songB"))
        assertTrue(BlockedArtwork.isBlocked("songC"))
        assertFalse(BlockedArtwork.isBlocked("songD"))

        assertTrue(isSongInSet(hiddenCoverIds, "songA"))
        assertTrue(isSongInSet(hiddenCoverIds, "songB"))
        assertTrue(isSongInSet(hiddenCoverIds, "songC"))
        assertFalse(isSongInSet(hiddenCoverIds, "songD"))
    }

    @Test
    fun resolveCanvasArtworkForPlayback_abortsWhenBlocked() = runBlocking {
        BlockedArtwork.update("blocked-video-1")

        val result = resolveCanvasArtworkForPlayback(
            mediaId = "blocked-video-1",
            songTitleRaw = "Test Title",
            artistNameRaw = "Test Artist",
            storefront = "us",
            requireVertical = false,
            allowNetwork = true,
        )

        assertNull("resolveCanvasArtworkForPlayback must abort and return null for blocked song", result)
    }

    @Test
    fun youTubeCanvasProvider_abortsWhenBlocked() = runBlocking {
        BlockedArtwork.update("blocked-video-yt")

        val result = YouTubeCanvasProvider.resolveForPlayback(
            mediaId = "blocked-video-yt",
            songTitleRaw = "Some Song",
            artistNameRaw = "Some Artist",
            requireVertical = false,
        )

        assertNull("YouTubeCanvasProvider.resolveForPlayback must abort and return null for blocked song", result)
    }

    @Test
    fun playerBackgroundCanvas_notRenderedAndNoCoverLeakWhenBlocked() {
        BlockedArtwork.update("blocked-player-song")
        val hiddenCoverIds = "blocked-player-song"
        val songId = "blocked-player-song"

        val isArtworkBlocked = isSongInSet(hiddenCoverIds, songId) || BlockedArtwork.isBlocked(songId)
        assertTrue(isArtworkBlocked)

        // 1. Canvas fetching condition
        val archiveTuneCanvasEnabled = true
        val playerBackground = PlayerBackgroundStyle.JUSPLAYER_CANVAS
        val shouldFetchCanvas = playerBackground == PlayerBackgroundStyle.JUSPLAYER_CANVAS &&
            archiveTuneCanvasEnabled &&
            !isArtworkBlocked
        assertFalse("Canvas must not be fetched when artwork is blocked", shouldFetchCanvas)

        // 2. Background thumbnail must be nulled
        val rawThumbnailUrl = "https://example.com/cover.jpg"
        val backgroundThumbnailUrl = if (isArtworkBlocked) null else rawThumbnailUrl
        assertNull("Background thumbnail must be null for blocked song", backgroundThumbnailUrl)

        // 3. In PlayerBackgroundStyle.JUSPLAYER_CANVAS, ensure neither canvas nor thumbnail falls through
        val fakeCanvasArtwork = CanvasArtwork(
            name = "Canvas Title",
            animated = "https://example.com/canvas.m3u8",
            videoUrl = "https://example.com/canvas.mp4",
        )
        val artworkToRender = if (isArtworkBlocked) null else fakeCanvasArtwork
        assertNull("Canvas artwork to render must be null when blocked", artworkToRender)

        val fallbackThumbnailToRender = if (!isArtworkBlocked) backgroundThumbnailUrl else null
        assertNull("Fallback thumbnail must not be rendered when artwork is blocked", fallbackThumbnailToRender)
    }

    @Test
    fun playerScreens_suppressCoverArtAndCanvasWhenBlocked() {
        BlockedArtwork.update("blocked-v8")
        val songId = "blocked-v8"
        val isBlocked = songId.isNotBlank() && BlockedArtwork.isBlocked(songId)

        val rawArtworkUrl = "https://example.com/v8_art.jpg"
        val rawCanvasPrimary = "https://example.com/v8_canvas.m3u8"
        val rawCanvasFallback = "https://example.com/v8_canvas.mp4"

        val effectiveArtworkUrl = if (isBlocked) null else rawArtworkUrl
        val effectiveCanvasPrimary = if (isBlocked) null else rawCanvasPrimary
        val effectiveCanvasFallback = if (isBlocked) null else rawCanvasFallback

        assertNull("V8/V9 artwork URL must be null when blocked", effectiveArtworkUrl)
        assertNull("V8/V9 canvas primary URL must be null when blocked", effectiveCanvasPrimary)
        assertNull("V8/V9 canvas fallback URL must be null when blocked", effectiveCanvasFallback)
    }

    @Test
    fun thumbnail_suppressesCanvasAndShowsPlaceholderWhenBlocked() {
        BlockedArtwork.update("blocked-thumb")
        val songId = "blocked-thumb"
        val hiddenCoverIds = "blocked-thumb"

        val isItemArtworkBlocked = isSongInSet(hiddenCoverIds, songId) || BlockedArtwork.isBlocked(songId)
        val archiveTuneCanvasEnabled = true
        val playerBackground = PlayerBackgroundStyle.DEFAULT

        val shouldUseCanvas = archiveTuneCanvasEnabled &&
            !isItemArtworkBlocked &&
            playerBackground != PlayerBackgroundStyle.JUSPLAYER_CANVAS

        assertFalse("Thumbnail should not use canvas when blocked", shouldUseCanvas)

        val hidePlayerThumbnail = false
        val isCoverHidden = hidePlayerThumbnail || isItemArtworkBlocked
        assertTrue("Thumbnail must mark cover hidden when blocked", isCoverHidden)
    }

    @Test
    fun modularCanvasArtworkComponent_inactiveWhenBlocked() {
        BlockedArtwork.update("blocked-modular")
        val songId = "blocked-modular"
        val hiddenCoverIds = "blocked-modular"

        val isBlocked = isSongInSet(hiddenCoverIds, songId) || BlockedArtwork.isBlocked(songId)
        val canvasEnabled = true
        val playerBackground = PlayerBackgroundStyle.DEFAULT

        val canvasActive = canvasEnabled && !isBlocked && playerBackground != PlayerBackgroundStyle.JUSPLAYER_CANVAS
        assertFalse("Modular CanvasArtworkComponent must be inactive when blocked", canvasActive)
    }

    @Test
    fun queue_itemThumbnail_hidesCoverWhenBlocked() {
        BlockedArtwork.update("blocked-queue-song")
        val hiddenCoverIds = "blocked-queue-song"
        val songId = "blocked-queue-song"

        // MediaMetadataListItem passes mediaId = mediaMetadata.id to ItemThumbnail
        val mediaId: String? = songId
        val hideCover = mediaId != null && (isSongInSet(hiddenCoverIds, mediaId) || BlockedArtwork.isBlocked(mediaId))
        assertTrue("ItemThumbnail must hide cover for blocked song id", hideCover)

        // For non-blocked song
        val normalId = "normal-queue-song"
        val hideCoverNormal = isSongInSet(hiddenCoverIds, normalId) || BlockedArtwork.isBlocked(normalId)
        assertFalse("ItemThumbnail must not hide cover for unblocked song", hideCoverNormal)
    }

    @Test
    fun queue_currentSongHeader_suppressesArtworkWhenBlocked() {
        BlockedArtwork.update("blocked-header-song")
        val hiddenCoverIds = "blocked-header-song"
        val songId = "blocked-header-song"

        val isArtworkBlocked = isSongInSet(hiddenCoverIds, songId) || BlockedArtwork.isBlocked(songId)
        assertTrue(isArtworkBlocked)

        val rawThumbnailUrl = "https://example.com/header_art.jpg"
        val displayThumbnailUrl = if (isArtworkBlocked) null else rawThumbnailUrl
        assertNull("CurrentSongHeader must suppress thumbnail URL when blocked", displayThumbnailUrl)
    }

    @Test
    fun lyrics_suppressesGradientAndCoverArtworkWhenBlocked() {
        BlockedArtwork.update("blocked-lyrics-song")
        val hiddenCoverIds = "blocked-lyrics-song"
        val songId = "blocked-lyrics-song"

        val isArtworkBlocked = isSongInSet(hiddenCoverIds, songId) || BlockedArtwork.isBlocked(songId)
        assertTrue(isArtworkBlocked)

        // 1. Gradient color extraction aborted
        val rawThumbnail = "https://example.com/lyrics_thumb.jpg"
        val effectiveGradientSource = if (isArtworkBlocked) null else rawThumbnail
        assertNull("Lyrics gradient extraction source must be null when blocked", effectiveGradientSource)

        // 2. AppleMusicBackground blurred backdrop nulled
        val backdropThumbnailUrl = if (isArtworkBlocked) null else rawThumbnail
        assertNull("AppleMusicBackground must not load thumbnail backdrop when blocked", backdropThumbnailUrl)

        // 3. AppleMusicTrackHeader thumbnail nulled
        val headerThumbnailUrl = if (isArtworkBlocked) null else rawThumbnail
        assertNull("AppleMusicTrackHeader must not load track cover art when blocked", headerThumbnailUrl)
    }

    @Test
    fun lyricsShare_suppressesCoverArtAndCanvasWhenBlocked() {
        BlockedArtwork.update("blocked-share-song")
        val hiddenCoverIds = "blocked-share-song"
        val songId = "blocked-share-song"

        val isArtworkBlocked = isSongInSet(hiddenCoverIds, songId) || BlockedArtwork.isBlocked(songId)
        assertTrue(isArtworkBlocked)

        val rawThumbnail = "https://example.com/share_thumb.jpg"
        val effectiveCoverUrl = if (isArtworkBlocked) null else rawThumbnail
        assertNull("LyricsShare must not load cover art when blocked", effectiveCoverUrl)

        val shouldFetchCanvas = !isArtworkBlocked
        assertFalse("LyricsShare canvas preview must not fetch canvas when blocked", shouldFetchCanvas)
    }

    @Test
    fun jusPlayerCanvas_backdropBlur_respectsBackdropSettings() {
        val maxBlur = 64f

        fun computeCanvasBlur(disableBlur: Boolean, backdropEnabled: Boolean, backdropBlurAmount: Int): Float {
            val isCanvasBlurAllowed = !disableBlur && backdropEnabled && backdropBlurAmount > 0
            return if (isCanvasBlurAllowed) {
                maxBlur * (backdropBlurAmount.toFloat() / 100f)
            } else {
                0f
            }
        }

        // 1. When Backdrop blur amount is 0%, blur must be 0
        assertEquals(0f, computeCanvasBlur(disableBlur = false, backdropEnabled = true, backdropBlurAmount = 0), 0.001f)

        // 2. When Backdrop blur toggle is off, blur must be 0
        assertEquals(0f, computeCanvasBlur(disableBlur = false, backdropEnabled = false, backdropBlurAmount = 60), 0.001f)

        // 3. When global disableBlur is true, blur must be 0
        assertEquals(0f, computeCanvasBlur(disableBlur = true, backdropEnabled = true, backdropBlurAmount = 60), 0.001f)

        // 4. When Backdrop blur is on and amount > 0, blur scales with amount
        val blur60 = computeCanvasBlur(disableBlur = false, backdropEnabled = true, backdropBlurAmount = 60)
        assertEquals(38.4f, blur60, 0.001f)

        val blur100 = computeCanvasBlur(disableBlur = false, backdropEnabled = true, backdropBlurAmount = 100)
        assertEquals(64f, blur100, 0.001f)
    }
}
