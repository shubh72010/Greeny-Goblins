/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.subsonic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubsonicIdTest {
    @Test
    fun tokenAuthMatchesKnownMd5Vector() {
        // md5("ab") — well-known vector; guards the u/t/s auth scheme.
        assertEquals("187ef4436122d1cc2f40dc2b92f0eba0", SubsonicClient.buildToken("a", "b"))
    }

    @Test
    fun idRoundTripKeepsOpaqueTrackId() {
        // Server ids are opaque strings (may contain ':') — never parsed as numbers.
        val encoded = SubsonicId.encode(trackId = "abc:123")
        assertTrue(SubsonicId.isSubsonic(encoded))
        assertEquals("abc:123", SubsonicId.trackIdOf(encoded))
        assertEquals(SubsonicId.PRIMARY_SERVER_ID to "abc:123", SubsonicId.decode(encoded))
    }

    @Test
    fun rejectsNonSubsonicAndMalformed() {
        assertFalse(SubsonicId.isSubsonic("11abcdef"))
        assertNull(SubsonicId.decode("11abcdef"))
        assertNull(SubsonicId.decode("subsonic:"))
        assertNull(SubsonicId.decode("subsonic:noseparator"))
    }

    @Test
    fun normalizesBaseUrl() {
        assertEquals("https://music.example.com", SubsonicClient.normalizeBaseUrl("https://music.example.com/"))
    }

    @Test
    fun streamUrlCarriesAuthAndId() {
        val url =
            SubsonicClient.streamUrl(
                SubsonicConfig("https://music.example.com/", "user", "pass"),
                "track-1",
            )
        assertTrue(url.startsWith("https://music.example.com/rest/stream.view?"))
        assertTrue(url.contains("u=user"))
        assertTrue(url.contains("id=track-1"))
    }

    @Test
    fun albumArtistIdsAreDisplayOnlyAndRecoverable() {
        val album = SubsonicId.encodeAlbum("album-9")
        val artist = SubsonicId.encodeArtist("artist-3")
        assertEquals("album-9", SubsonicId.albumIdOf(album))
        assertEquals("artist-3", SubsonicId.artistIdOf(artist))
        // Display ids never leak into the playback path.
        assertNull(SubsonicId.albumIdOf(SubsonicId.encode(trackId = "track-1")))
    }

    @Test
    fun mapperKeepsPlaybackAndNavPaths() {
        val config = SubsonicConfig("https://music.example.com", "user", "pass")
        val track = TrackPayload(id = "t1", title = "Song", artist = "A", duration = 200)
        val song = SubsonicMapper.trackToSongItem(track, config)
        // Player decodes the exact server track id.
        assertEquals("t1", SubsonicId.trackIdOf(song.id))
        assertTrue(song.thumbnail.isNotBlank())

        val album = SubsonicMapper.albumToAlbumItem(AlbumRef(id = "a1", name = "LP"), config)
        // Album screen recovers the raw server album id for getAlbum.
        assertEquals("a1", SubsonicId.albumIdOf(album.id))
        assertTrue(album.thumbnail.isNotBlank())
    }

    @Test
    fun matcherAcceptsExactTitleWithArtist() {
        val track = TrackPayload(id = "t1", title = "Midnight City", artist = "M83")
        val best = SubsonicMatcher.best("Midnight City", listOf("M83"), listOf(track))
        assertEquals("t1", best?.id)
    }

    @Test
    fun matcherStripsVideoSuffixes() {
        val track = TrackPayload(id = "t1", title = "Midnight City", artist = "M83")
        // YouTube-style title still matches the clean server title.
        val best = SubsonicMatcher.best("Midnight City (Official Video)", listOf("M83"), listOf(track))
        assertEquals("t1", best?.id)
    }

    @Test
    fun matcherRejectsWrongArtist() {
        val track = TrackPayload(id = "t1", title = "Midnight City", artist = "Someone Else")
        assertNull(SubsonicMatcher.best("Midnight City", listOf("M83"), listOf(track)))
    }

    @Test
    fun matcherRejectsWeakTitleOverlap() {
        val track = TrackPayload(id = "t1", title = "Completely Different Words Here", artist = "M83")
        assertNull(SubsonicMatcher.best("Midnight City", listOf("M83"), listOf(track)))
    }

    @Test
    fun matcherPicksBestCandidate() {
        val wrong = TrackPayload(id = "w", title = "Midnight City", artist = "Cover Band")
        val right = TrackPayload(id = "r", title = "Midnight City", artist = "M83")
        assertEquals("r", SubsonicMatcher.best("Midnight City", listOf("M83"), listOf(wrong, right))?.id)
    }

    @Test
    fun playlistIdRoundTripsForNav() {
        val config = SubsonicConfig("https://music.example.com", "user", "pass")
        val item =
            SubsonicMapper.playlistToPlaylistItem(
                PlaylistRef(id = "p7", name = "Mix", songCount = 3),
                config,
                "3 songs",
            )
        assertEquals("p7", SubsonicId.playlistIdOf(item.id))
        assertEquals("Mix", item.title)
        assertNull(SubsonicId.playlistIdOf(SubsonicId.encode(trackId = "t1")))
    }
}
