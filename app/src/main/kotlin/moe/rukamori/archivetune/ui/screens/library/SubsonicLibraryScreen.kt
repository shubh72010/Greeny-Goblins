/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.library

import android.util.Base64
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.YTItem
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.subsonic.SubsonicId
import moe.rukamori.archivetune.ui.component.EmptyPlaceholder
import moe.rukamori.archivetune.ui.component.YouTubeGridItem
import moe.rukamori.archivetune.ui.component.YouTubeListItem
import moe.rukamori.archivetune.ui.screens.HomeSectionHeader
import moe.rukamori.archivetune.viewmodels.SubsonicAlbumState
import moe.rukamori.archivetune.viewmodels.SubsonicLibraryState
import timber.log.Timber
import moe.rukamori.archivetune.viewmodels.SubsonicLibraryViewModel
import moe.rukamori.archivetune.viewmodels.SubsonicPlaylistState
import moe.rukamori.archivetune.viewmodels.SubsonicSearchState

private val AlbumIdEncodingFlags = Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING

internal const val SubsonicAlbumRoutePrefix = "subsonic_album/"

internal fun subsonicAlbumRoute(album: AlbumItem): String {
    val raw = SubsonicId.albumIdOf(album.id) ?: album.id
    val encoded = Base64.encodeToString(raw.toByteArray(Charsets.UTF_8), AlbumIdEncodingFlags)
    return "$SubsonicAlbumRoutePrefix$encoded"
}

internal fun decodeSubsonicAlbumId(encoded: String): String =
    runCatching {
        String(Base64.decode(encoded, AlbumIdEncodingFlags), Charsets.UTF_8)
    }.getOrElse { encoded }

internal const val SubsonicPlaylistRoutePrefix = "subsonic_playlist/"

internal fun subsonicPlaylistRoute(playlist: PlaylistItem): String {
    val raw = SubsonicId.playlistIdOf(playlist.id) ?: playlist.id
    val encoded = Base64.encodeToString(raw.toByteArray(Charsets.UTF_8), AlbumIdEncodingFlags)
    return "$SubsonicPlaylistRoutePrefix$encoded"
}

internal fun decodeSubsonicPlaylistId(encoded: String): String =
    runCatching {
        String(Base64.decode(encoded, AlbumIdEncodingFlags), Charsets.UTF_8)
    }.getOrElse { encoded }

@Composable
fun LibrarySubsonicScreen(
    navController: NavController,
    onDeselect: () -> Unit,
    viewModel: SubsonicLibraryViewModel = hiltViewModel(),
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val libraryState by viewModel.libraryState.collectAsStateWithLifecycle()
    val searchState by viewModel.searchState.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                if (it.isBlank()) viewModel.clearSearch() else viewModel.search(it)
            },
            placeholder = { Text(stringResource(R.string.subsonic_search_hint)) },
            leadingIcon = { Icon(painterResource(R.drawable.search), null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = {
                        query = ""
                        viewModel.clearSearch()
                    }) {
                        Icon(painterResource(R.drawable.close), null)
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { viewModel.search(query) }),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
        )

        if (query.isBlank()) {
            when (val state = libraryState) {
                SubsonicLibraryState.Loading ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }

                SubsonicLibraryState.Disabled ->
                    EmptyPlaceholder(
                        icon = R.drawable.storage,
                        text = stringResource(R.string.subsonic_disabled_hint),
                    )

                SubsonicLibraryState.Empty ->
                    EmptyPlaceholder(
                        icon = R.drawable.album,
                        text = stringResource(R.string.playlist_is_empty),
                    )

                is SubsonicLibraryState.Error ->
                    SubsonicErrorBlock(
                        text = stringResource(state.messageResId),
                        onRetry = { viewModel.refresh() },
                    )

                is SubsonicLibraryState.Success ->
                    SubsonicBrowseGrid(
                        navController = navController,
                        playerConnection = playerConnection,
                        albums = state.albums,
                        artists = state.artists,
                        playlists = state.playlists,
                        onRefresh = { viewModel.refresh() },
                    )
            }
        } else {
            when (val state = searchState) {
                SubsonicSearchState.Idle,
                SubsonicSearchState.Loading,
                -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                SubsonicSearchState.Empty ->
                    EmptyPlaceholder(
                        icon = R.drawable.search,
                        text = stringResource(R.string.playlist_is_empty),
                    )

                is SubsonicSearchState.Error ->
                    SubsonicErrorBlock(
                        text = stringResource(state.messageResId),
                        onRetry = { viewModel.search(query) },
                    )

                is SubsonicSearchState.Success ->
                    SubsonicSearchResults(
                        navController = navController,
                        playerConnection = playerConnection,
                        state = state,
                    )
            }
        }
    }
}

@Composable
private fun SubsonicBrowseGrid(
    navController: NavController,
    playerConnection: PlayerConnection,
    albums: List<AlbumItem>,
    artists: List<ArtistItem>,
    playlists: List<PlaylistItem>,
    onRefresh: () -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding =
            PaddingValues(
                start = 24.dp,
                end = 24.dp,
                bottom =
                    LocalPlayerAwareWindowInsets.current
                        .only(WindowInsetsSides.Bottom)
                        .let { 96.dp },
            ),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(2) }) {
            SectionHeader(
                title = stringResource(R.string.albums),
                onRefresh = onRefresh,
            )
        }
        gridItems(albums, key = { it.id }) { album ->
            Box(
                Modifier.combinedClickable(onClick = {
                    navController.navigate(subsonicAlbumRoute(album))
                }),
            ) {
                YouTubeGridItem(item = album, fillMaxWidth = true)
            }
        }
        if (playlists.isNotEmpty()) {
            item(span = { GridItemSpan(2) }) {
                SectionHeader(title = stringResource(R.string.playlists), onRefresh = null)
            }
            gridItems(playlists, key = { it.id }, span = { GridItemSpan(2) }) { playlist ->
                Box(
                    Modifier.combinedClickable(onClick = {
                        navController.navigate(subsonicPlaylistRoute(playlist))
                    }),
                ) {
                    YouTubeListItem(item = playlist, isSwipeable = false)
                }
            }
        }
        if (artists.isNotEmpty()) {
            item(span = { GridItemSpan(2) }) {
                SectionHeader(title = stringResource(R.string.artists), onRefresh = null)
            }
            gridItems(artists, key = { it.id }) { artist ->
                YouTubeGridItem(item = artist, fillMaxWidth = true)
            }
        }
    }
}

@Composable
private fun SubsonicSearchResults(
    navController: NavController,
    playerConnection: PlayerConnection,
    state: SubsonicSearchState.Success,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (state.songs.isNotEmpty()) {
            item(span = { GridItemSpan(2) }) {
                SectionHeader(title = stringResource(R.string.songs), onRefresh = null)
            }
            gridItems(state.songs, key = { it.id }, span = { GridItemSpan(2) }) { song ->
                val index = state.songs.indexOf(song)
                SongRow(
                    song = song,
                    playerConnection = playerConnection,
                    onClick = {
                        playSongs(
                            playerConnection = playerConnection,
                            title = song.album?.name,
                            songs = state.songs,
                            startIndex = index,
                        )
                    },
                )
            }
        }
        if (state.albums.isNotEmpty()) {
            item(span = { GridItemSpan(2) }) {
                SectionHeader(title = stringResource(R.string.albums), onRefresh = null)
            }
            gridItems(state.albums, key = { it.id }) { album ->
                Box(
                    Modifier.combinedClickable(onClick = {
                        navController.navigate(subsonicAlbumRoute(album))
                    }),
                ) {
                    YouTubeGridItem(item = album, fillMaxWidth = true)
                }
            }
        }
        if (state.artists.isNotEmpty()) {
            item(span = { GridItemSpan(2) }) {
                SectionHeader(title = stringResource(R.string.artists), onRefresh = null)
            }
            gridItems(state.artists, key = { it.id }) { artist ->
                YouTubeGridItem(item = artist, fillMaxWidth = true)
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, onRefresh: (() -> Unit)?) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        if (onRefresh != null) {
            IconButton(onClick = onRefresh) {
                Icon(painterResource(R.drawable.update), null)
            }
        }
    }
}

@Composable
private fun SongRow(
    song: SongItem,
    playerConnection: PlayerConnection,
    onClick: () -> Unit,
) {
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val isCurrent = mediaMetadata?.id == song.id
    Box(
        Modifier.combinedClickable(onClick = {
            if (isCurrent) {
                playerConnection.player.togglePlayPause()
            } else {
                onClick()
            }
        }),
    ) {
        YouTubeListItem(
            item = song,
            isActive = isCurrent,
            isPlaying = isCurrent && isPlaying,
            isSwipeable = false,
        )
    }
}

@Composable
private fun SubsonicErrorBlock(text: String, onRetry: () -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(12.dp))
        FilledTonalButton(onClick = onRetry) {
            Text(stringResource(R.string.retry))
        }
    }
}

@Composable
fun SubsonicAlbumScreen(
    navController: NavController,
    encodedAlbumId: String?,
    viewModel: SubsonicLibraryViewModel = hiltViewModel(),
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val albumState by viewModel.albumState.collectAsStateWithLifecycle()
    val serverAlbumId = remember(encodedAlbumId) { encodedAlbumId?.let(::decodeSubsonicAlbumId) }

    LaunchedEffect(serverAlbumId) {
        if (serverAlbumId != null) viewModel.loadAlbum(serverAlbumId)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 12.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { navController.navigateUp() }) {
                Icon(painterResource(R.drawable.arrow_back), null)
            }
        }

        when (val state = albumState) {
            SubsonicAlbumState.Loading ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

            is SubsonicAlbumState.Error ->
                SubsonicErrorBlock(
                    text = stringResource(state.messageResId),
                    onRetry = { if (serverAlbumId != null) viewModel.loadAlbum(serverAlbumId) },
                )

            is SubsonicAlbumState.Success ->
                TrackListDetailContent(
                    playerConnection = playerConnection,
                    title = state.title,
                    subtitle = state.subtitle,
                    countText = stringResource(R.string.tracks_count_label) + ": ${state.tracks.size}",
                    coverUrl = state.coverUrl,
                    tracks = state.tracks,
                )
        }
    }
}

/**
 * Shared header (cover + titles + Play/Shuffle) and track rows for album
 * and playlist detail screens.
 */
@Composable
private fun TrackListDetailContent(
    playerConnection: PlayerConnection,
    title: String,
    subtitle: String?,
    countText: String,
    coverUrl: String?,
    tracks: List<SongItem>,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = coverUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(12.dp)),
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            Text(
                text = countText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FilledTonalButton(
            onClick = {
                playSongs(playerConnection, title, tracks, 0)
            },
            modifier = Modifier.weight(1f),
        ) {
            Icon(painterResource(R.drawable.play), null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.play))
        }
        FilledTonalButton(
            onClick = {
                playSongs(playerConnection, title, tracks.shuffled(), 0)
            },
            modifier = Modifier.weight(1f),
        ) {
            Icon(painterResource(R.drawable.shuffle), null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.shuffle))
        }
    }
    LazyColumn(
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 96.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        listItems(
            items = tracks,
            key = { it.id },
        ) { song ->
            SongRow(
                song = song,
                playerConnection = playerConnection,
                onClick = {
                    playSongs(
                        playerConnection = playerConnection,
                        title = title,
                        songs = tracks,
                        startIndex = tracks.indexOf(song),
                    )
                },
            )
        }
    }
}

@Composable
fun SubsonicPlaylistScreen(
    navController: NavController,
    encodedPlaylistId: String?,
    viewModel: SubsonicLibraryViewModel = hiltViewModel(),
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val playlistState by viewModel.playlistState.collectAsStateWithLifecycle()
    val serverPlaylistId = remember(encodedPlaylistId) { encodedPlaylistId?.let(::decodeSubsonicPlaylistId) }

    LaunchedEffect(serverPlaylistId) {
        if (serverPlaylistId != null) viewModel.loadPlaylist(serverPlaylistId)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 12.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { navController.navigateUp() }) {
                Icon(painterResource(R.drawable.arrow_back), null)
            }
        }

        when (val state = playlistState) {
            SubsonicPlaylistState.Loading ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

            is SubsonicPlaylistState.Error ->
                SubsonicErrorBlock(
                    text = stringResource(state.messageResId),
                    onRetry = { if (serverPlaylistId != null) viewModel.loadPlaylist(serverPlaylistId) },
                )

            is SubsonicPlaylistState.Success ->
                TrackListDetailContent(
                    playerConnection = playerConnection,
                    title = state.title,
                    subtitle = null,
                    countText = state.countText,
                    coverUrl = state.coverUrl,
                    tracks = state.tracks,
                )
        }
    }
}

/**
 * Single server shelf for the main home screen: songs and albums mixed like a
 * normal shelf. Songs play, albums open. Renders nothing unless the source is
 * enabled, configured, and returned content.
 */
@Composable
fun SubsonicHomeSections(
    navController: NavController,
    playerConnection: PlayerConnection,
    viewModel: SubsonicLibraryViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val libraryState by viewModel.libraryState.collectAsStateWithLifecycle()
    val randomSongs by viewModel.randomSongs.collectAsStateWithLifecycle()
    val success = libraryState as? SubsonicLibraryState.Success
    if (success == null) {
        Timber.tag("Subsonic").d("home shelf hidden: state=%s", libraryState::class.simpleName)
        return
    }
    val shelf: List<YTItem> =
        remember(randomSongs, success.albums) {
            val songs = randomSongs.take(8)
            val albums = success.albums.take(8)
            buildList {
                val max = maxOf(songs.size, albums.size)
                for (i in 0 until max) {
                    songs.getOrNull(i)?.let(::add)
                    albums.getOrNull(i)?.let(::add)
                }
            }
        }
    if (shelf.isEmpty()) return
    LaunchedEffect(shelf.size) {
        Timber.tag("Subsonic").d("home shelf shown: items=%d", shelf.size)
    }
    val shelfSongs = remember(shelf) { shelf.filterIsInstance<SongItem>() }

    Column(modifier) {
        HomeSectionHeader(title = stringResource(R.string.subsonic_home_songs))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            listItems(shelf, key = { it.id }) { item ->
                Box(
                    Modifier
                        .width(160.dp)
                        .combinedClickable(onClick = {
                            when (item) {
                                is SongItem ->
                                    playSongs(
                                        playerConnection = playerConnection,
                                        title = item.album?.name,
                                        songs = shelfSongs,
                                        startIndex = shelfSongs.indexOf(item),
                                    )

                                is AlbumItem -> navController.navigate(subsonicAlbumRoute(item))
                                else -> Unit
                            }
                        }),
                ) {
                    YouTubeGridItem(item = item, fillMaxWidth = true, thumbnailRatio = 1f)
                }
            }
        }
    }
}

private fun playSongs(
    playerConnection: PlayerConnection,
    title: String?,
    songs: List<SongItem>,
    startIndex: Int,
) {
    if (songs.isEmpty()) return
    playerConnection.playQueue(
        ListQueue(
            title = title,
            items = songs.map { it.toMediaItem() },
            startIndex = startIndex.coerceIn(songs.indices),
        ),
    )
}
