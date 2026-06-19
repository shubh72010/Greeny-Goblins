package moe.rukamori.archivetune.ui.player.modular.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import moe.rukamori.archivetune.constants.CropThumbnailToSquareKey
import moe.rukamori.archivetune.constants.HidePlayerThumbnailKey
import moe.rukamori.archivetune.constants.ModularCoverArtStyle
import moe.rukamori.archivetune.constants.ModularCoverArtStyleKey
import moe.rukamori.archivetune.lyrics.LyricsEntry
import moe.rukamori.archivetune.lyrics.LyricsUtils
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.ui.player.modular.LocalThumbnailCornerRadius
import moe.rukamori.archivetune.ui.player.modular.PlayerComponentRegistry
import moe.rukamori.archivetune.ui.player.modular.PlayerComponentType
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference

fun registerCoverArtComponent() {
    PlayerComponentRegistry.register(PlayerComponentType.COVER_ART.id) { _, metadata, playerConnection, isPlaying, _, _, _, _, _, _, modifier, _ ->
        val (coverArtStyle) = rememberEnumPreference(ModularCoverArtStyleKey, defaultValue = ModularCoverArtStyle.NORMAL)
        val (hideThumbnail) = rememberPreference(HidePlayerThumbnailKey, defaultValue = false)
        val (cropToSquare) = rememberPreference(CropThumbnailToSquareKey, defaultValue = false)
        val cornerRadius = LocalThumbnailCornerRadius.current
        CoverArtComponent(
            metadata = metadata,
            playerConnection = playerConnection,
            isPlaying = isPlaying,
            coverArtStyle = coverArtStyle,
            hideThumbnail = hideThumbnail,
            cropToSquare = cropToSquare,
            cornerRadius = cornerRadius,
            modifier = modifier,
        )
    }
}

@Composable
fun CoverArtComponent(
    metadata: MediaMetadata?,
    playerConnection: PlayerConnection,
    isPlaying: Boolean,
    coverArtStyle: ModularCoverArtStyle = ModularCoverArtStyle.NORMAL,
    hideThumbnail: Boolean = false,
    cropToSquare: Boolean = false,
    cornerRadius: Float = 16f,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(cornerRadius.dp)
    when (coverArtStyle) {
        ModularCoverArtStyle.STYLE_CARD -> StyleCardVariant(
            metadata = metadata,
            hideThumbnail = hideThumbnail,
            shape = shape,
            modifier = modifier,
        )
        ModularCoverArtStyle.LYRIC -> LyricVariant(
            metadata = metadata,
            playerConnection = playerConnection,
            shape = shape,
            modifier = modifier,
        )
        else -> DefaultCoverArtVariant(
            metadata = metadata,
            hideThumbnail = hideThumbnail,
            cropToSquare = cropToSquare,
            shape = shape,
            modifier = modifier,
        )
    }
}

@Composable
private fun DefaultCoverArtVariant(
    metadata: MediaMetadata?,
    hideThumbnail: Boolean = false,
    cropToSquare: Boolean = false,
    shape: RoundedCornerShape = RoundedCornerShape(16.dp),
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        val imageModifier = if (cropToSquare) {
            Modifier.fillMaxSize().aspectRatio(1f).padding(8.dp).clip(shape)
        } else {
            Modifier.fillMaxSize().padding(8.dp).clip(shape)
        }

        if (!hideThumbnail && metadata?.thumbnailUrl != null) {
            AsyncImage(
                model = metadata.thumbnailUrl,
                contentDescription = metadata?.title,
                contentScale = ContentScale.Fit,
                modifier = imageModifier,
            )
        }
    }
}

@Composable
private fun StyleCardVariant(
    metadata: MediaMetadata?,
    hideThumbnail: Boolean = false,
    shape: RoundedCornerShape = RoundedCornerShape(16.dp),
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        if (!hideThumbnail && metadata?.thumbnailUrl != null) {
            AsyncImage(
                model = metadata.thumbnailUrl,
                contentDescription = metadata?.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.7f),
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(
                text = metadata?.title ?: "",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = metadata?.artists?.joinToString(", ") { it.name } ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LyricVariant(
    metadata: MediaMetadata?,
    playerConnection: PlayerConnection,
    shape: RoundedCornerShape = RoundedCornerShape(16.dp),
    modifier: Modifier = Modifier,
) {
    val lyricsEntity by playerConnection.currentLyrics.collectAsState(initial = null)
    val lyricsState = remember(lyricsEntity) {
        lyricsEntity?.let { parseLyricsWithTimings(it.lyrics) }
    }

    var currentPositionMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            currentPositionMs = playerConnection.player.currentPosition.coerceAtLeast(0L)
            withFrameNanos { }
        }
    }

    val currentLineIndex = remember(lyricsState, currentPositionMs) {
        if (lyricsState != null) {
            val (entries, isSynced) = lyricsState!!
            if (entries.isEmpty() || !isSynced) -1
            else LyricsUtils.findCurrentLineIndex(entries, currentPositionMs)
        } else -1
    }

    val lines = remember(lyricsState, currentLineIndex) {
        if (lyricsState == null) emptyList()
        else {
            val (entries, isSynced) = lyricsState!!
            when {
                entries.isEmpty() && lyricsEntity != null ->
                    LyricsUtils.normalizeLyricsText(lyricsEntity!!.lyrics)
                        .lines().map { it.trim() }.filter { it.isNotEmpty() }
                entries.isEmpty() -> emptyList()
                isSynced -> entries.map { it.text }
                else -> entries.map { it.text }
            }
        }
    }

    val currentLine = lines.getOrNull(currentLineIndex) ?: ""
    val nextLine = lines.getOrNull(currentLineIndex + 1) ?: ""
    val hasLyrics = lines.isNotEmpty()

    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        if (metadata?.thumbnailUrl != null) {
            AsyncImage(
                model = metadata.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.5f),
                                Color.Black.copy(alpha = 0.7f),
                            )
                        )
                    )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (currentLine.isNotEmpty()) {
                AnimatedContent(
                    targetState = currentLine,
                    transitionSpec = {
                        ContentTransform(
                            targetContentEnter = slideInVertically { it / 3 } + fadeIn(),
                            initialContentExit = slideOutVertically { -it / 3 } + fadeOut(),
                        )
                    },
                    label = "lyricLine",
                    modifier = Modifier.fillMaxWidth(),
                ) { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else if (!hasLyrics && lyricsEntity != null) {
                Text(
                    text = "♪",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center,
                )
            }

            if (nextLine.isNotEmpty()) {
                Text(
                    text = nextLine,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.45f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }
        }
    }
}

private fun parseLyricsWithTimings(lyrics: String): Pair<List<LyricsEntry>, Boolean> {
    val normalized = LyricsUtils.normalizeLyricsText(lyrics)
    if (normalized.isEmpty()) return Pair(emptyList(), false)

    if (LyricsUtils.isTtml(normalized)) {
        val entries = LyricsUtils.parseTtml(normalized)
        return Pair(entries, entries.isNotEmpty())
    }

    if (LyricsUtils.isLineSyncedLrc(normalized)) {
        val entries = LyricsUtils.parseLyrics(normalized)
        return Pair(entries, entries.isNotEmpty())
    }

    val lines = normalized.lines().map { it.trim() }.filter { it.isNotEmpty() }
    val entries = lines.map { LyricsEntry(time = 0L, text = it) }
    return Pair(entries, false)
}
