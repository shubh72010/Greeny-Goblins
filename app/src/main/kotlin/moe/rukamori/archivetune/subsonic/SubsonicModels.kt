/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.subsonic

import kotlinx.serialization.Serializable

/**
 * Provider-agnostic OpenSubsonic models (JSON, `f=json`).
 *
 * Works with any compatible server: Navidrome, Airsonic-Advanced, Gonic, Ampache, …
 * Server-side IDs are opaque strings — never parse them as numbers.
 */
@Serializable
data class SubsonicEnvelope(val `subsonic-response`: SubsonicResponse = SubsonicResponse())

@Serializable
data class SubsonicResponse(
    val status: String = "",
    val version: String = "",
    val type: String = "",
    val serverVersion: String = "",
    val openSubsonic: Boolean = false,
    val error: SubsonicError? = null,
    val musicFolders: MusicFolders? = null,
    val artists: ArtistsContainer? = null,
    val artist: ArtistPayload? = null,
    val album: AlbumPayload? = null,
    val albumList2: AlbumList2? = null,
    val searchResult3: SearchResult3? = null,
    val playlists: PlaylistsContainer? = null,
    val playlist: PlaylistDetail? = null,
    val randomSongs: RandomSongs? = null,
)

@Serializable
data class RandomSongs(val song: List<TrackPayload> = emptyList())

@Serializable
data class SubsonicError(
    val code: Int = 0,
    val message: String = "",
)

@Serializable
data class MusicFolders(val musicFolder: List<MusicFolder> = emptyList())

@Serializable
data class MusicFolder(
    val id: String = "",
    val name: String = "",
)

@Serializable
data class ArtistsContainer(val index: List<ArtistIndex> = emptyList())

@Serializable
data class ArtistIndex(
    val name: String = "",
    val artist: List<ArtistRef> = emptyList(),
)

@Serializable
data class ArtistRef(
    val id: String = "",
    val name: String = "",
    val albumCount: Int = 0,
    val coverArt: String? = null,
)

@Serializable
data class ArtistPayload(
    val id: String = "",
    val name: String = "",
    val album: List<AlbumRef> = emptyList(),
)

@Serializable
data class AlbumRef(
    val id: String = "",
    val name: String = "",
    val artist: String? = null,
    val artistId: String? = null,
    val coverArt: String? = null,
    val songCount: Int = 0,
    val year: Int? = null,
)

@Serializable
data class AlbumPayload(
    val id: String = "",
    val name: String = "",
    val artist: String? = null,
    val song: List<TrackPayload> = emptyList(),
)

@Serializable
data class AlbumList2(val album: List<AlbumRef> = emptyList())

@Serializable
data class SearchResult3(
    val artist: List<ArtistRef> = emptyList(),
    val album: List<AlbumRef> = emptyList(),
    val song: List<TrackPayload> = emptyList(),
)

@Serializable
data class PlaylistsContainer(val playlist: List<PlaylistRef> = emptyList())

@Serializable
data class PlaylistRef(
    val id: String = "",
    val name: String = "",
    val songCount: Int = 0,
    val duration: Int? = null,
    val coverArt: String? = null,
    val owner: String? = null,
)

@Serializable
data class PlaylistDetail(
    val id: String = "",
    val name: String = "",
    val songCount: Int = 0,
    val entry: List<TrackPayload> = emptyList(),
)

@Serializable
data class TrackPayload(
    val id: String = "",
    val title: String = "",
    val album: String? = null,
    val albumId: String? = null,
    val artist: String? = null,
    val artistId: String? = null,
    val duration: Int? = null,
    val track: Int? = null,
    val year: Int? = null,
    val coverArt: String? = null,
    val contentType: String? = null,
    val suffix: String? = null,
)
