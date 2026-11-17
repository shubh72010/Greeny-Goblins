package moe.rukamori.archivetune.tv.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.tv.components.TvMediaCard
import moe.rukamori.archivetune.tv.theme.TvAccentCoral
import moe.rukamori.archivetune.tv.theme.TvFocusedBorder
import moe.rukamori.archivetune.tv.theme.TvPaper
import moe.rukamori.archivetune.tv.theme.TvSurfaceVariant
import moe.rukamori.archivetune.tv.theme.TvTextSecondary

@Composable
fun TvSearchScreen(
    modifier: Modifier = Modifier
) {
    val playerConnection = LocalPlayerConnection.current
    var query by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<SongItem>>(emptyList()) }
    var isInputFocused by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 16.dp, end = 24.dp)
    ) {
        Text(
            text = "Search",
            color = TvPaper,
            fontSize = 28.sp,
            fontWeight = FontWeight.Black
        )

        Spacer(modifier = Modifier.height(16.dp))

        // TV Search Input
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(TvSurfaceVariant, RoundedCornerShape(12.dp))
                .then(
                    if (isInputFocused) {
                        Modifier.border(2.dp, TvFocusedBorder, RoundedCornerShape(12.dp))
                    } else {
                        Modifier
                    }
                )
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            BasicTextField(
                value = query,
                onValueChange = {
                    query = it
                    if (it.length >= 2) {
                        scope.launch {
                            val res = withContext(Dispatchers.IO) {
                                try {
                                    YouTube.search(it, YouTube.SearchFilter.FILTER_SONG)
                                        .getOrNull()
                                        ?.items
                                        ?.filterIsInstance<SongItem>()
                                        ?: emptyList()
                                } catch (e: Exception) {
                                    emptyList()
                                }
                            }
                            searchResults = res
                        }
                    }
                },
                singleLine = true,
                cursorBrush = SolidColor(TvAccentCoral),
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { isInputFocused = it.isFocused },
                decorationBox = { innerTextField ->
                    if (query.isEmpty()) {
                        Text(
                            text = "Search songs, artists, or albums...",
                            color = TvTextSecondary,
                            fontSize = 16.sp
                        )
                    }
                    innerTextField()
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            contentPadding = PaddingValues(bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(searchResults) { item ->
                TvMediaCard(
                    title = item.title,
                    subtitle = item.artists.joinToString(", ") { it.name },
                    thumbnailUrl = item.thumbnail,
                    onClick = {
                        playerConnection?.playQueue(
                            YouTubeQueue(
                                endpoint = item.endpoint,
                                preloadItem = item.toMediaMetadata()
                            )
                        )
                    }
                )
            }
        }
    }
}
