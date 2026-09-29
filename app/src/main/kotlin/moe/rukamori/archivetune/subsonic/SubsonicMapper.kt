/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.subsonic

import moe.rukamori.archivetune.innertube.models.Album
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.Artist
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.SongItem

/**
 * Maps provider-agnostic OpenSubsonic payloads to display/playable [YTItem]s so the
 * existing `YouTube*` composables and `SongItem.toMediaItem()` can be reused as-is.
 *
 * Only **track** ids round-trip through the player (`MusicService.resolveSubsonicDataSpec`
 * decodes them back to raw server ids), so only tracks use the bare encoding.
 * Album/artist ids are display-only and kind-prefixed to avoid cross-kind collisions.
 */
object SubsonicMapper {
    fun trackToSongItem(track: TrackPayload, config: SubsonicConfig): SongItem =
        SongItem(
            id = SubsonicId.encode(trackId = track.id),
            title = track.title.ifBlank { track.id },
            artists =
                track.artist?.takeIf { it.isNotBlank() }?.let {
                    listOf(Artist(name = it, id = track.artistId))
                }.orEmpty(),
            album = track.album?.let { Album(name = it, id = track.albumId.orEmpty()) },
            duration = track.duration,
            thumbnail = SubsonicClient.coverArtUrl(config, track.coverArt ?: track.id),
        )

    fun albumToAlbumItem(ref: AlbumRef, config: SubsonicConfig): AlbumItem =
        AlbumItem(
            browseId = SubsonicId.encodeAlbum(ref.id),
            playlistId = "",
            title = ref.name.ifBlank { ref.id },
            artists =
                ref.artist?.takeIf { it.isNotBlank() }?.let {
                    listOf(Artist(name = it, id = ref.artistId))
                },
            year = ref.year,
            thumbnail = SubsonicClient.coverArtUrl(config, ref.coverArt ?: ref.id),
        )

    fun artistToArtistItem(ref: ArtistRef, config: SubsonicConfig): ArtistItem =
        ArtistItem(
            id = SubsonicId.encodeArtist(ref.id),
            title = ref.name.ifBlank { ref.id },
            thumbnail = ref.coverArt?.let { SubsonicClient.coverArtUrl(config, it) },
            shuffleEndpoint = null,
            radioEndpoint = null,
        )

    fun playlistToPlaylistItem(
        ref: PlaylistRef,
        config: SubsonicConfig,
        songCountText: String,
    ): PlaylistItem =
        PlaylistItem(
            id = SubsonicId.encodePlaylist(ref.id),
            title = ref.name.ifBlank { ref.id },
            author = ref.owner?.takeIf { it.isNotBlank() }?.let { Artist(name = it, id = null) },
            songCountText = songCountText,
            thumbnail = ref.coverArt?.let { SubsonicClient.coverArtUrl(config, it) },
            playEndpoint = null,
            shuffleEndpoint = null,
            radioEndpoint = null,
        )
}
