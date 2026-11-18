/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.amazon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AmazonMusicPlaylistParserTest {

    @Test
    fun extractAmazonUrl_validCatalogUrl() {
        val input = "https://music.amazon.com/playlists/B01GKIA6L8"
        val extracted = AmazonMusicPlaylistParser.extractAmazonUrl(input)
        assertEquals("https://music.amazon.com/playlists/B01GKIA6L8", extracted)
    }

    @Test
    fun extractAmazonUrl_validUserPlaylistUrlWithQuery() {
        val input = "Check out this playlist: https://music.amazon.com/user-playlists/1801f146ba184746b0c22fe28bf7d111sune?ref=dm_sh_abc123 on Amazon Music"
        val extracted = AmazonMusicPlaylistParser.extractAmazonUrl(input)
        assertEquals("https://music.amazon.com/user-playlists/1801f146ba184746b0c22fe28bf7d111sune?ref=dm_sh_abc123", extracted)
    }

    @Test
    fun extractAmazonUrl_regionalDomain() {
        val input = "https://music.amazon.co.uk/playlists/B01GKIA6L8"
        val extracted = AmazonMusicPlaylistParser.extractAmazonUrl(input)
        assertEquals("https://music.amazon.co.uk/playlists/B01GKIA6L8", extracted)
    }

    @Test
    fun extractAmazonUrl_shortlink() {
        val input = "Listen here: https://amzn.to/3xyz123"
        val extracted = AmazonMusicPlaylistParser.extractAmazonUrl(input)
        assertEquals("https://amzn.to/3xyz123", extracted)
    }

    @Test
    fun extractAmazonUrl_invalidUrls() {
        assertNull(AmazonMusicPlaylistParser.extractAmazonUrl("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M"))
        assertNull(AmazonMusicPlaylistParser.extractAmazonUrl("https://youtube.com/playlist?list=PL123"))
        assertNull(AmazonMusicPlaylistParser.extractAmazonUrl("not a url at all"))
        assertNull(AmazonMusicPlaylistParser.extractAmazonUrl(null))
        assertNull(AmazonMusicPlaylistParser.extractAmazonUrl(""))
    }
}
