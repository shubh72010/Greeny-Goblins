/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.subsonic

/**
 * Namespaced media IDs for the OpenSubsonic source.
 *
 * Server-side track IDs are opaque strings that can collide across servers
 * (and are not guaranteed numeric), so every imported track keeps its server id:
 * `subsonic:<serverId>:<trackId>`. Phase 1 uses a single `"primary"` server id;
 * multi-server later replaces it with the real per-server id — decode shape is stable.
 */
object SubsonicId {
    const val PREFIX = "subsonic:"
    const val PRIMARY_SERVER_ID = "primary"

    fun encode(serverId: String = PRIMARY_SERVER_ID, trackId: String): String =
        "$PREFIX$serverId:$trackId"

    fun isSubsonic(mediaId: String?): Boolean = mediaId?.startsWith(PREFIX) == true

    /** Returns (serverId, trackId) or null if malformed. Track id may itself contain ':'. */
    fun decode(mediaId: String): Pair<String, String>? {
        if (!isSubsonic(mediaId)) return null
        val rest = mediaId.removePrefix(PREFIX)
        val sep = rest.indexOf(':')
        if (sep <= 0 || sep == rest.length - 1) return null
        return rest.substring(0, sep) to rest.substring(sep + 1)
    }

    fun trackIdOf(mediaId: String): String? = decode(mediaId)?.second

    private const val ALBUM_KIND = "album:"
    private const val ARTIST_KIND = "artist:"

    /** Display-only ids for albums/artists (never reach the player). */
    fun encodeAlbum(albumId: String, serverId: String = PRIMARY_SERVER_ID): String =
        "$PREFIX$serverId:$ALBUM_KIND$albumId"

    fun encodeArtist(artistId: String, serverId: String = PRIMARY_SERVER_ID): String =
        "$PREFIX$serverId:$ARTIST_KIND$artistId"

    fun albumIdOf(mediaId: String): String? =
        decode(mediaId)?.second?.takeIf { it.startsWith(ALBUM_KIND) }?.removePrefix(ALBUM_KIND)

    fun artistIdOf(mediaId: String): String? =
        decode(mediaId)?.second?.takeIf { it.startsWith(ARTIST_KIND) }?.removePrefix(ARTIST_KIND)

    private const val PLAYLIST_KIND = "playlist:"

    fun encodePlaylist(playlistId: String, serverId: String = PRIMARY_SERVER_ID): String =
        "$PREFIX$serverId:$PLAYLIST_KIND$playlistId"

    fun playlistIdOf(mediaId: String): String? =
        decode(mediaId)?.second?.takeIf { it.startsWith(PLAYLIST_KIND) }?.removePrefix(PLAYLIST_KIND)

    private const val MATCH_KEY_PREFIX = "subsonic-match:"

    /**
     * Distinct Media3 cache key for server copies played via the SUBSONIC client.
     * YouTube bytes stay under the raw videoId, so switching clients never serves
     * stale audio from the other source.
     */
    fun matchKeyFor(videoId: String): String = "$MATCH_KEY_PREFIX$videoId"
}
