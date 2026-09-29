/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.subsonic

import androidx.datastore.preferences.core.Preferences
import moe.rukamori.archivetune.constants.SubsonicBaseUrlKey
import moe.rukamori.archivetune.constants.SubsonicPasswordKey
import moe.rukamori.archivetune.constants.SubsonicUsernameKey
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 1 single-server repository for the OpenSubsonic source.
 * Config comes from DataStore; multi-server (Room ServerEntity) slots in here later.
 */
@Singleton
class SubsonicRepository @Inject constructor(
    private val client: SubsonicClient,
) {
    fun configFromPreferences(prefs: Preferences): SubsonicConfig? {
        val baseUrl = prefs[SubsonicBaseUrlKey]?.trim().orEmpty()
        val username = prefs[SubsonicUsernameKey]?.trim().orEmpty()
        val password = prefs[SubsonicPasswordKey].orEmpty()
        if (baseUrl.isBlank() || username.isBlank() || password.isEmpty()) return null
        return SubsonicConfig(baseUrl, username, password)
    }

    suspend fun ping(config: SubsonicConfig): Boolean = client.ping(config)

    suspend fun musicFolders(config: SubsonicConfig): List<MusicFolder> =
        runCatching { client.getMusicFolders(config) }
            .onFailure { Timber.tag("Subsonic").w(it, "musicFolders failed") }
            .getOrDefault(emptyList())

    suspend fun recentAlbums(config: SubsonicConfig, musicFolderId: String? = null): List<AlbumRef> =
        runCatching { client.getAlbumList2(config, musicFolderId = musicFolderId) }
            .onFailure { Timber.tag("Subsonic").w(it, "recentAlbums failed") }
            .getOrDefault(emptyList())

    suspend fun artists(config: SubsonicConfig, musicFolderId: String? = null): List<ArtistRef> =
        runCatching { client.getArtists(config, musicFolderId) }
            .onFailure { Timber.tag("Subsonic").w(it, "artists failed") }
            .getOrDefault(emptyList())

    suspend fun album(config: SubsonicConfig, id: String): AlbumPayload? =
        runCatching { client.getAlbum(config, id) }
            .onFailure { Timber.tag("Subsonic").w(it, "album failed") }
            .getOrNull()

    suspend fun playlists(config: SubsonicConfig): List<PlaylistRef> =
        runCatching { client.getPlaylists(config) }
            .onFailure { Timber.tag("Subsonic").w(it, "playlists failed") }
            .getOrDefault(emptyList())

    suspend fun playlist(config: SubsonicConfig, id: String): PlaylistDetail? =
        runCatching { client.getPlaylist(config, id) }
            .onFailure { Timber.tag("Subsonic").w(it, "playlist failed") }
            .getOrNull()

    suspend fun randomSongs(config: SubsonicConfig, musicFolderId: String? = null): List<TrackPayload> =
        runCatching { client.getRandomSongs(config, musicFolderId = musicFolderId) }
            .onFailure { Timber.tag("Subsonic").w(it, "randomSongs failed") }
            .getOrDefault(emptyList())

    suspend fun search(config: SubsonicConfig, query: String): SearchResult3? =
        runCatching { client.search3(config, query) }
            .onFailure { Timber.tag("Subsonic").w(it, "search failed") }
            .getOrNull()

    fun streamUrl(config: SubsonicConfig, trackId: String): String =
        SubsonicClient.streamUrl(config, trackId)

    fun coverArtUrl(config: SubsonicConfig, coverArtId: String): String =
        SubsonicClient.coverArtUrl(config, coverArtId)
}
