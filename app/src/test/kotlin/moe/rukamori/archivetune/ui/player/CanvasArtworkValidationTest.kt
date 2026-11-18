/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player

import moe.rukamori.archivetune.canvas.models.CanvasArtwork
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanvasArtworkValidationTest {
    @Test
    fun rejectsLyricVideoByName() {
        val artwork = CanvasArtwork(name = "Señorita (Official Lyric Video)", artist = "Shawn Mendes")
        assertFalse(artwork.isPlausibleCanvasFor("Señorita", "Shawn Mendes"))
    }

    @Test
    fun rejectsWrongArtist() {
        val artwork = CanvasArtwork(name = "Señorita", artist = "Some Random Channel")
        assertFalse(artwork.isPlausibleCanvasFor("Señorita", "Shawn Mendes"))
    }

    @Test
    fun acceptsAccentInsensitiveCorrectArtist() {
        val artwork = CanvasArtwork(name = "Senorita (Official Music Video)", artist = "Shawn Mendes")
        assertTrue(artwork.isPlausibleCanvasFor("Señorita", "Shawn Mendes"))
    }

    @Test
    fun acceptsLabelChannelForCorrectTitle() {
        val artwork = CanvasArtwork(name = "Senorita (Official Music Video)", artist = "Atlantic Records")
        assertTrue(artwork.isPlausibleCanvasFor("Señorita", "Shawn Mendes"))
    }

    @Test
    fun rejectsTopicChannelUploads() {
        val artwork = CanvasArtwork(name = "Senorita", artist = "Shawn Mendes - Topic")
        assertFalse(artwork.isPlausibleCanvasFor("Senorita", "Shawn Mendes"))
    }

    @Test
    fun rejectsTopicChannelForArtistTopic() {
        val artwork = CanvasArtwork(name = "Breaking Me", artist = "Topic - Topic")
        assertFalse(artwork.isPlausibleCanvasFor("Breaking Me", "Topic"))
    }

    @Test
    fun acceptsArtistNamedTopic() {
        val artwork = CanvasArtwork(name = "Breaking Me (Official Video)", artist = "Topic")
        assertTrue(artwork.isPlausibleCanvasFor("Breaking Me", "Topic"))
    }

    @Test
    fun acceptsArtistContainingTopic() {
        val artwork = CanvasArtwork(name = "Song (Official Music Video)", artist = "Hot Topic")
        assertTrue(artwork.isPlausibleCanvasFor("Song", "Hot Topic"))
    }
}
