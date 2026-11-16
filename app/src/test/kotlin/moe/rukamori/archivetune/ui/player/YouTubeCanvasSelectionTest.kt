/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player

import moe.rukamori.archivetune.innertube.models.Artist
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YouTubeCanvasSelectionTest {
    private fun omvEndpoint() =
        WatchEndpoint(
            watchEndpointMusicSupportedConfigs =
                WatchEndpoint.WatchEndpointMusicSupportedConfigs(
                    watchEndpointMusicConfig =
                        WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig(
                            musicVideoType = WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.MUSIC_VIDEO_TYPE_OMV,
                        ),
                ),
        )

    private fun song(
        id: String,
        title: String,
        artist: String,
        duration: Int = 200,
        isOmv: Boolean = false,
        viewCount: Long? = null,
    ): SongItem =
        SongItem(
            id = id,
            title = title,
            artists = listOf(Artist(name = artist, id = null)),
            album = null,
            duration = duration,
            thumbnail = "",
            endpoint = if (isOmv) omvEndpoint() else null,
            viewCount = viewCount,
        )

    @Test
    fun rejectsExactTitleWrongArtistLyric() {
        val candidates =
            listOf(
                song("lyric1111111", "Hello", "Adele", isOmv = false),
            )
        // lyric title marker
        val lyricCandidate = song("lyric2222222", "Hello (Lyrics)", "Adele")
        val result =
            selectYouTubeMusicVideoCandidate(
                candidates = listOf(lyricCandidate) + candidates,
                songTitle = "Hello",
                artistName = "Adele",
            )
        // all lyric -> null (no confident non-lyric)
        assertNull(result)
    }

    @Test
    fun rejectsExactTitleCorrectArtistLyric() {
        val lyric = song("l1111111111", "Hello (Lyric Video)", "Adele")
        val result = selectYouTubeMusicVideoCandidate(listOf(lyric), "Hello", "Adele")
        assertNull(result)
    }

    @Test
    fun prefersExactTitleCorrectArtistOmv() {
        val omv = song("o1111111111", "Hello", "Adele", isOmv = true)
        val lyric = song("l1111111111", "Hello (Lyrics)", "Adele")
        val result = selectYouTubeMusicVideoCandidate(listOf(lyric, omv), "Hello", "Adele")
        assertEquals("o1111111111", result?.id)
    }

    @Test
    fun slightlyDifferentTitleCorrectArtistOmvEvaluated() {
        val omv = song("o1111111111", "Hello World", "Adele", isOmv = true)
        val result = selectYouTubeMusicVideoCandidate(listOf(omv), "Hello", "Adele")
        // contains match + OMV -> accepted
        assertEquals("o1111111111", result?.id)
    }

    @Test
    fun rejectsExactTitleCorrectArtistAudioUpload() {
        val audio = song("a1111111111", "Hello", "Adele") // title has no lyric, but is audio upload (no omv)
        // mark as audio via title? anti-pattern checks title contains "audio"
        val audioTitled = song("a2222222222", "Hello (Audio)", "Adele")
        val result = selectYouTubeMusicVideoCandidate(listOf(audioTitled), "Hello", "Adele")
        assertNull(result)
    }

    @Test
    fun noConfidentMatchFallsBackToNull() {
        val candidates =
            listOf(
                song("x1111111111", "Completely Different", "Someone", isOmv = false),
            )
        val result = selectYouTubeMusicVideoCandidate(candidates, "Hello", "Adele")
        assertNull(result)
    }

    @Test
    fun doesNotTakeIndexZeroBlindly() {
        val wrong = song("wrong1111111", "Hello", "Wrong Artist")
        val right = song("right1111111", "Hello", "Adele", isOmv = true)
        val result = selectYouTubeMusicVideoCandidate(listOf(wrong, right), "Hello", "Adele")
        assertEquals("right1111111", result?.id)
    }
}
