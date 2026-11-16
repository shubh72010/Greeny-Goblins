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
}
