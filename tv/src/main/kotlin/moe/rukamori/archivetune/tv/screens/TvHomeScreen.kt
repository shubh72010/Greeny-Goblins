package moe.rukamori.archivetune.tv.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.tv.components.TvMediaCard
import moe.rukamori.archivetune.tv.theme.TvAccentCoral
import moe.rukamori.archivetune.tv.theme.TvPaper

@Composable
fun TvHomeScreen(
    modifier: Modifier = Modifier
) {
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current
    val recentSongs by database.events().collectAsState(initial = emptyList())
    val favoriteSongs by database.favorites().collectAsState(initial = emptyList())

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 16.dp, end = 24.dp),
        contentPadding = PaddingValues(bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        item {
            Text(
                text = "Welcome to JusPlayer",
                color = TvPaper,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = "Small, sharp music experience for your TV",
                color = TvAccentCoral,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }

        if (recentSongs.isNotEmpty()) {
            item {
                TvSection(
                    title = "Recently Played",
                    songs = recentSongs.mapNotNull { it.song },
                    onSongClick = { selectedSong, songList ->
                        playerConnection?.playQueue(
                            ListQueue(
                                title = "Recently Played",
                                items = songList.map { it.toMediaItem() },
                                startIndex = songList.indexOf(selectedSong).coerceAtLeast(0)
                            )
                        )
                    }
                )
            }
        }

        if (favoriteSongs.isNotEmpty()) {
            item {
                TvSection(
                    title = "Favorites",
                    songs = favoriteSongs,
                    onSongClick = { selectedSong, songList ->
                        playerConnection?.playQueue(
                            ListQueue(
                                title = "Favorites",
                                items = songList.map { it.toMediaItem() },
                                startIndex = songList.indexOf(selectedSong).coerceAtLeast(0)
                            )
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun TvSection(
    title: String,
    songs: List<Song>,
    onSongClick: (Song, List<Song>) -> Unit
) {
    Column {
        Text(
            text = title,
            color = TvPaper,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(songs) { song ->
                TvMediaCard(
                    title = song.title,
                    subtitle = song.artists.joinToString(", ") { it.name },
                    thumbnailUrl = song.thumbnailUrl,
                    onClick = { onSongClick(song, songs) }
                )
            }
        }
    }
}
