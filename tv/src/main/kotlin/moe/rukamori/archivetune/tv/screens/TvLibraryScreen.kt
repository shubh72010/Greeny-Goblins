package moe.rukamori.archivetune.tv.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.tv.components.TvMediaCard
import moe.rukamori.archivetune.tv.theme.TvPaper

@Composable
fun TvLibraryScreen(
    modifier: Modifier = Modifier
) {
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current
    val playlists by database.playlists().collectAsState(initial = emptyList())

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 16.dp, end = 24.dp)
    ) {
        Text(
            text = "Your Library",
            color = TvPaper,
            fontSize = 28.sp,
            fontWeight = FontWeight.Black
        )

        Spacer(modifier = Modifier.height(24.dp))

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            contentPadding = PaddingValues(bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(playlists) { playlist ->
                TvMediaCard(
                    title = playlist.playlist.name,
                    subtitle = "${playlist.songCount} songs",
                    thumbnailUrl = playlist.thumbnails.firstOrNull(),
                    onClick = {
                        val songs = playlist.songs
                        if (songs.isNotEmpty()) {
                            playerConnection?.playQueue(
                                ListQueue(
                                    title = playlist.playlist.name,
                                    items = songs.map { it.toMediaItem() },
                                    startIndex = 0
                                )
                            )
                        }
                    }
                )
            }
        }
    }
}
