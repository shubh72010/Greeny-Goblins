/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.SubsonicBaseUrlKey
import moe.rukamori.archivetune.constants.SubsonicEnabledKey
import moe.rukamori.archivetune.constants.SubsonicMusicFolderKey
import moe.rukamori.archivetune.constants.SubsonicPasswordKey
import moe.rukamori.archivetune.constants.SubsonicUsernameKey
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.subsonic.SubsonicClient
import moe.rukamori.archivetune.subsonic.SubsonicConfig
import moe.rukamori.archivetune.subsonic.SubsonicMapper
import moe.rukamori.archivetune.subsonic.SubsonicRepository
import moe.rukamori.archivetune.utils.dataStore
import timber.log.Timber
import javax.inject.Inject

sealed interface SubsonicLibraryState {
    data object Loading : SubsonicLibraryState

    /** Source disabled or server credentials missing. */
    data object Disabled : SubsonicLibraryState

    data object Empty : SubsonicLibraryState

    data class Error(
        @StringRes val messageResId: Int,
    ) : SubsonicLibraryState

    data class Success(
        val albums: List<AlbumItem>,
        val artists: List<ArtistItem>,
        val playlists: List<PlaylistItem>,
    ) : SubsonicLibraryState
}

sealed interface SubsonicPlaylistState {
    data object Loading : SubsonicPlaylistState

    data class Error(
        @StringRes val messageResId: Int,
    ) : SubsonicPlaylistState

    data class Success(
        val title: String,
        val countText: String,
        val coverUrl: String?,
        val tracks: List<SongItem>,
    ) : SubsonicPlaylistState
}

sealed interface SubsonicAlbumState {
    data object Loading : SubsonicAlbumState

    data class Error(
        @StringRes val messageResId: Int,
    ) : SubsonicAlbumState

    data class Success(
        val title: String,
        val subtitle: String?,
        val coverUrl: String?,
        val tracks: List<SongItem>,
    ) : SubsonicAlbumState
}

sealed interface SubsonicSearchState {
    data object Idle : SubsonicSearchState

    data object Loading : SubsonicSearchState

    data object Empty : SubsonicSearchState

    data class Error(
        @StringRes val messageResId: Int,
    ) : SubsonicSearchState

    data class Success(
        val songs: List<SongItem>,
        val albums: List<AlbumItem>,
        val artists: List<ArtistItem>,
    ) : SubsonicSearchState
}

@HiltViewModel
class SubsonicLibraryViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val repository: SubsonicRepository,
    ) : ViewModel() {
        private val _libraryState = MutableStateFlow<SubsonicLibraryState>(SubsonicLibraryState.Loading)
        val libraryState: StateFlow<SubsonicLibraryState> = _libraryState.asStateFlow()

        private val _albumState = MutableStateFlow<SubsonicAlbumState>(SubsonicAlbumState.Loading)
        val albumState: StateFlow<SubsonicAlbumState> = _albumState.asStateFlow()

        private val _playlistState = MutableStateFlow<SubsonicPlaylistState>(SubsonicPlaylistState.Loading)
        val playlistState: StateFlow<SubsonicPlaylistState> = _playlistState.asStateFlow()

        private val _randomSongs = MutableStateFlow<List<SongItem>>(emptyList())
        val randomSongs: StateFlow<List<SongItem>> = _randomSongs.asStateFlow()

        private val _searchState = MutableStateFlow<SubsonicSearchState>(SubsonicSearchState.Idle)
        val searchState: StateFlow<SubsonicSearchState> = _searchState.asStateFlow()

        private var loadJob: Job? = null
        private var searchJob: Job? = null
        private var cachedConfig: SubsonicConfig? = null
        private var cachedMusicFolder: String? = null

        init {
            refresh()
        }

        fun refresh() {
            if (loadJob?.isActive == true) return
            loadJob?.cancel()
            _libraryState.value = SubsonicLibraryState.Loading
            loadJob =
                viewModelScope.launch {
                    try {
                        val (config, musicFolder) = readConfig() ?: run {
                            _libraryState.value = SubsonicLibraryState.Disabled
                            return@launch
                        }
                        cachedConfig = config
                        cachedMusicFolder = musicFolder
                        Timber.tag("Subsonic").d(
                            "refresh baseUrl=%s user=%s folder=%s",
                            config.baseUrl,
                            config.username,
                            musicFolder,
                        )
                        val albumsDeferred = async { repository.recentAlbums(config, musicFolder) }
                        val artistsDeferred = async { repository.artists(config, musicFolder) }
                        val playlistsDeferred = async { repository.playlists(config) }
                        val randomDeferred = async { repository.randomSongs(config, musicFolder) }
                        _randomSongs.value =
                            randomDeferred.await().map { SubsonicMapper.trackToSongItem(it, config) }
                        val albums = albumsDeferred.await().map { SubsonicMapper.albumToAlbumItem(it, config) }
                        val artists = artistsDeferred.await().map { SubsonicMapper.artistToArtistItem(it, config) }
                        val playlists =
                            playlistsDeferred.await().map {
                                SubsonicMapper.playlistToPlaylistItem(
                                    ref = it,
                                    config = config,
                                    songCountText =
                                        context.resources.getQuantityString(
                                            R.plurals.n_song,
                                            it.songCount,
                                            it.songCount,
                                        ),
                                )
                            }
                        Timber.tag("Subsonic").d(
                            "refresh done albums=%d artists=%d playlists=%d",
                            albums.size,
                            artists.size,
                            playlists.size,
                        )
                        _libraryState.value =
                            if (albums.isEmpty() && artists.isEmpty() && playlists.isEmpty()) {
                                SubsonicLibraryState.Empty
                            } else {
                                SubsonicLibraryState.Success(albums, artists, playlists)
                            }
                    } catch (throwable: Throwable) {
                        if (throwable is CancellationException) throw throwable
                        _libraryState.value = SubsonicLibraryState.Error(R.string.subsonic_test_failed)
                    }
                }
        }

        fun loadAlbum(serverAlbumId: String) {
            _albumState.value = SubsonicAlbumState.Loading
            viewModelScope.launch {
                try {
                    val config = cachedConfig ?: readConfig()?.first ?: run {
                        _albumState.value = SubsonicAlbumState.Error(R.string.subsonic_test_failed)
                        return@launch
                    }
                    val album = repository.album(config, serverAlbumId)
                    if (album == null || album.song.isEmpty()) {
                        _albumState.value = SubsonicAlbumState.Error(R.string.playlist_is_empty)
                        return@launch
                    }
                    val firstCover = album.song.firstOrNull { !it.coverArt.isNullOrBlank() }?.coverArt
                    _albumState.value =
                        SubsonicAlbumState.Success(
                            title = album.name,
                            subtitle = album.artist,
                            coverUrl = firstCover?.let { SubsonicClient.coverArtUrl(config, it) },
                            tracks = album.song.map { SubsonicMapper.trackToSongItem(it, config) },
                        )
                } catch (throwable: Throwable) {
                    if (throwable is CancellationException) throw throwable
                    _albumState.value = SubsonicAlbumState.Error(R.string.error_unknown)
                }
            }
        }

        fun loadPlaylist(serverPlaylistId: String) {
            _playlistState.value = SubsonicPlaylistState.Loading
            viewModelScope.launch {
                try {
                    val config = cachedConfig ?: readConfig()?.first ?: run {
                        _playlistState.value = SubsonicPlaylistState.Error(R.string.subsonic_test_failed)
                        return@launch
                    }
                    val playlist = repository.playlist(config, serverPlaylistId)
                    if (playlist == null || playlist.entry.isEmpty()) {
                        _playlistState.value = SubsonicPlaylistState.Error(R.string.playlist_is_empty)
                        return@launch
                    }
                    val firstCover = playlist.entry.firstOrNull { !it.coverArt.isNullOrBlank() }?.coverArt
                    _playlistState.value =
                        SubsonicPlaylistState.Success(
                            title = playlist.name,
                            countText =
                                context.resources.getQuantityString(
                                    R.plurals.n_song,
                                    playlist.entry.size,
                                    playlist.entry.size,
                                ),
                            coverUrl = firstCover?.let { SubsonicClient.coverArtUrl(config, it) },
                            tracks = playlist.entry.map { SubsonicMapper.trackToSongItem(it, config) },
                        )
                } catch (throwable: Throwable) {
                    if (throwable is CancellationException) throw throwable
                    _playlistState.value = SubsonicPlaylistState.Error(R.string.error_unknown)
                }
            }
        }

        fun search(query: String) {
            searchJob?.cancel()
            if (query.isBlank()) {
                _searchState.value = SubsonicSearchState.Idle
                return
            }
            _searchState.value = SubsonicSearchState.Loading
            searchJob =
                viewModelScope.launch {
                    try {
                        val config = cachedConfig ?: readConfig()?.first ?: run {
                            _searchState.value = SubsonicSearchState.Error(R.string.subsonic_test_failed)
                            return@launch
                        }
                        val result = repository.search(config, query.trim())
                        if (result == null ||
                            (result.song.isEmpty() && result.album.isEmpty() && result.artist.isEmpty())
                        ) {
                            _searchState.value = SubsonicSearchState.Empty
                            return@launch
                        }
                        _searchState.value =
                            SubsonicSearchState.Success(
                                songs = result.song.map { SubsonicMapper.trackToSongItem(it, config) },
                                albums = result.album.map { SubsonicMapper.albumToAlbumItem(it, config) },
                                artists = result.artist.map { SubsonicMapper.artistToArtistItem(it, config) },
                            )
                    } catch (throwable: Throwable) {
                        if (throwable is CancellationException) throw throwable
                        _searchState.value = SubsonicSearchState.Error(R.string.error_unknown)
                    }
                }
        }

        fun clearSearch() {
            searchJob?.cancel()
            _searchState.value = SubsonicSearchState.Idle
        }

        private suspend fun readConfig(): Pair<SubsonicConfig, String?>? {
            val prefs = context.dataStore.data.first()
            if (prefs[SubsonicEnabledKey] != true) return null
            val baseUrl = prefs[SubsonicBaseUrlKey]?.trim().orEmpty()
            val username = prefs[SubsonicUsernameKey]?.trim().orEmpty()
            val password = prefs[SubsonicPasswordKey].orEmpty()
            if (baseUrl.isBlank() || username.isBlank() || password.isEmpty()) return null
            val musicFolder = prefs[SubsonicMusicFolderKey]?.trim()?.takeIf { it.isNotBlank() }
            return SubsonicConfig(baseUrl, username, password) to musicFolder
        }
    }
