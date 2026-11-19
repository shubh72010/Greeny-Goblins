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
                song("lyric1111111", "Hello", "Wrong Artist", isOmv = false),
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
    fun livePerformanceIsAllowedButRankedBelowOfficialVideo() {
        val official = song("o1111111111", "Señorita", "Adele", isOmv = true)
        val live = song("l2222222222", "Señorita (Live)", "Adele")
        val result = selectYouTubeMusicVideoCandidate(listOf(live, official), "Señorita", "Adele")
        assertEquals("o1111111111", result?.id)
    }

    @Test
    fun livePerformanceIsKeptWhenItIsTheOnlyOption() {
        val live = song("l2222222222", "Señorita (Live)", "Adele")
        val result = selectYouTubeMusicVideoCandidate(listOf(live), "Señorita", "Adele")
        assertEquals("l2222222222", result?.id)
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

    @Test
    fun matchesSongTitleWithWordMusicVideoInTitle() {
        val mv = song("mv111111111", "Hello (Official Music Video)", "Adele", isOmv = false)
        val result = selectYouTubeMusicVideoCandidate(listOf(mv), "Hello", "Adele")
        assertEquals("mv111111111", result?.id)
    }

    @Test
    fun matchesFeaturedSongTitleToMusicVideoTitle() {
        val mv = song("mv222222222", "The Weeknd - Starboy ft. Daft Punk (Official Music Video)", "The Weeknd", isOmv = false)
        val result = selectYouTubeMusicVideoCandidate(listOf(mv), "Starboy (feat. Daft Punk)", "The Weeknd")
        assertEquals("mv222222222", result?.id)
    }

    @Test
    fun matchesVideoWithoutMusicVideoInTitleWhenArtistAndTitleMatch() {
        val plainVideo = song("sub11111111", "Like Love", "Sub Urban", isOmv = false)
        val result = selectYouTubeMusicVideoCandidate(listOf(plainVideo), "Like Love", "Sub Urban")
        assertEquals("sub11111111", result?.id)
    }

    @Test
    fun matchesArtistPrefixVideoWithoutMusicVideoInTitle() {
        val artistTitledVideo = song("sub22222222", "Sub Urban - Like Love", "Sub Urban", isOmv = false)
        val result = selectYouTubeMusicVideoCandidate(listOf(artistTitledVideo), "Like Love", "Sub Urban")
        assertEquals("sub22222222", result?.id)
    }

    @Test
    fun rejectsTopicChannelAudioUpload() {
        val topicSong = song("t1111111111", "Hello", "Adele - Topic")
        val result = selectYouTubeMusicVideoCandidate(listOf(topicSong), "Hello", "Adele")
        assertNull(result)
    }
}
