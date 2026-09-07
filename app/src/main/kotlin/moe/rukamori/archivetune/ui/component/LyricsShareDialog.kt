/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package moe.rukamori.archivetune.ui.component

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import moe.rukamori.archivetune.utils.GlobalLog
import timber.log.Timber
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.getSystemService
import androidx.palette.graphics.Palette
import androidx.window.core.layout.WindowSizeClass
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AudioQuality
import moe.rukamori.archivetune.constants.AudioQualityKey
import moe.rukamori.archivetune.constants.PlayerStreamClient
import moe.rukamori.archivetune.constants.PlayerStreamClientKey
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.ui.player.resolveCanvasArtworkForPlayback
import moe.rukamori.archivetune.utils.ComposeToImage
import moe.rukamori.archivetune.utils.LyricsKaraokeRenderer
import moe.rukamori.archivetune.utils.LyricsVideoExporter
import moe.rukamori.archivetune.utils.YTPlayerUtils
import moe.rukamori.archivetune.utils.enumPreference
import moe.rukamori.archivetune.utils.isLowDataModeActive
import moe.rukamori.archivetune.utils.retryWithoutPlaybackLoginContext
import java.util.Locale

@Immutable
private data class LyricsGlassStyleOptions(
    val items: ImmutableList<LyricsGlassStyle>,
)

fun shareLyricsAsText(
    context: Context,
    payload: LyricsSharePayload,
    songId: String?,
) {
    val songLink = songId?.takeIf { it.isNotBlank() }?.let { "https://music.youtube.com/watch?v=$it" }
    val shareBody =
        buildString {
            append("\"")
            append(payload.lyricsText)
            append("\"\n\n")
            append(payload.songTitle)
            append(" - ")
            append(payload.artists)
            if (songLink != null) {
                append('\n')
                append(songLink)
            }
        }

    val shareIntent =
        Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareBody)
        }
    context.startActivity(
        Intent.createChooser(
            shareIntent,
            context.getString(R.string.share_lyrics),
        ),
    )
}

@Composable
fun LyricsShareImageDialog(
    mediaMetadata: MediaMetadata?,
    payload: LyricsSharePayload,
    currentPositionMs: Long = 0L,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current
    // Use lifecycleScope, not rememberCoroutineScope, to avoid ForgottenCoroutineScopeException when dialog leaves composition during export
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val scope = lifecycleOwner.lifecycleScope
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val isCompactLayout =
        !windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

    var isSharing by remember { mutableStateOf(false) }
    var sharingProgress by remember { mutableStateOf<Float?>(null) }
    var shareMode by remember { mutableStateOf(LyricsShareMode.Image) }
    var selectedGlassStyle by remember { mutableStateOf(LyricsGlassStyle.FrostedDark) }
    var paletteGlassStyle by remember { mutableStateOf<LyricsGlassStyle?>(null) }
    var options by remember { mutableStateOf(LyricsShareImageOptions()) }
    var areAdvancedOptionsVisible by remember { mutableStateOf(false) }
    val audioQuality by enumPreference(context, AudioQualityKey, AudioQuality.AUTO)
    val preferredStreamClient by enumPreference(context, PlayerStreamClientKey, PlayerStreamClient.JUSPLAYER_ENGINE)
    // Movable canvas video state – only for Video mode (player-like drag)
    var canvasPreviewUrl by remember { mutableStateOf<String?>(null) }
    var videoPan by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var videoScale by remember { mutableStateOf(1f) }

    // Fetch canvas url for preview when media or aspect changes
    androidx.compose.runtime.LaunchedEffect(mediaMetadata?.id, options.aspectRatio) {
        val id = mediaMetadata?.id ?: return@LaunchedEffect
        Timber.tag("LyricsShare").d("preview fetch canvas id=$id aspect=${options.aspectRatio}")
        GlobalLog.append(Log.DEBUG, "LyricsShare", "preview fetch canvas id=$id")
        canvasPreviewUrl = runCatching {
            withContext(Dispatchers.IO) {
                val storefront = Locale.getDefault().country.takeIf { it.length == 2 }?.lowercase(Locale.ROOT) ?: "us"
                val artwork = resolveCanvasArtworkForPlayback(
                    mediaId = id,
                    songTitleRaw = mediaMetadata.title,
                    artistNameRaw = mediaMetadata.artists.firstOrNull()?.name.orEmpty(),
                    albumId = mediaMetadata.album?.id,
                    albumTitleRaw = mediaMetadata.album?.title,
                    storefront = storefront,
                    requireVertical = options.aspectRatio == LyricsShareAspectRatio.Story,
                    allowNetwork = true,
                    currentIsMusicVideo = mediaMetadata.isMusicVideo,
                )
                artwork?.let {
                    val vertical = it.preferredVerticalAnimationUrl ?: it.videoUrlVertical
                    val horizontal = it.preferredAnimationUrl ?: it.videoUrl
                    when {
                        options.aspectRatio == LyricsShareAspectRatio.Story && !vertical.isNullOrBlank() -> vertical
                        !horizontal.isNullOrBlank() -> horizontal
                        !vertical.isNullOrBlank() -> vertical
                        else -> null
                    }
                }
            }
        }.getOrNull().also {
            Timber.tag("LyricsShare").d("preview canvas result=$it")
            GlobalLog.append(Log.DEBUG, "LyricsShare", "preview canvas result=$it")
        }
    }

    LaunchedEffect(mediaMetadata?.thumbnailUrl) {
        val coverUrl = mediaMetadata?.thumbnailUrl
        if (coverUrl == null) {
            paletteGlassStyle = null
            return@LaunchedEffect
        }
        val extractedStyle =
            withContext(Dispatchers.IO) {
                runCatching {
                    val loader = ImageLoader(context)
                    val request =
                        ImageRequest
                            .Builder(context)
                            .data(coverUrl)
                            .allowHardware(false)
                            .build()
                    val bitmap = loader.execute(request).image?.toBitmap() ?: return@runCatching null
                    LyricsGlassStyle.fromPalette(Palette.from(bitmap).generate())
                }.getOrNull()
            }
        paletteGlassStyle = extractedStyle
    }

    val availableStyles by remember(paletteGlassStyle) {
        derivedStateOf {
            LyricsGlassStyleOptions(
                items =
                    ImmutableList.copyOf(
                        buildList {
                            paletteGlassStyle?.let(::add)
                            addAll(LyricsGlassStyle.allPresets.filterNot { it == paletteGlassStyle })
                        },
                    ),
            )
        }
    }

    val handleShare: () -> Unit = {
        Timber.tag("LyricsShare").d("handleShare mode=$shareMode mediaId=${mediaMetadata?.id}")
        GlobalLog.append(Log.DEBUG, "LyricsShare", "handleShare mode=$shareMode mediaId=${mediaMetadata?.id}")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            Toast.makeText(context, R.string.lyrics_share_export_not_supported, Toast.LENGTH_SHORT).show()
        } else {
            isSharing = true
            sharingProgress = null
            scope.launch {
                try {
                    Timber.tag("LyricsShare").i("share start mode=$shareMode")
                    GlobalLog.append(Log.INFO, "LyricsShare", "share start mode=$shareMode")
                    when (shareMode) {
                        LyricsShareMode.Image -> {
                            val image =
                                ComposeToImage.createLyricsImage(
                                    context = context,
                                    coverArtUrl = mediaMetadata?.thumbnailUrl,
                                    songTitle = payload.songTitle,
                                    artistName = payload.artists,
                                    lyrics = payload.lyricsText,
                                    width = options.aspectRatio.exportWidth,
                                    height = options.aspectRatio.exportHeight,
                                    glassStyle = selectedGlassStyle,
                                    shareOptions = options,
                                )
                            val fileName = "lyrics_${System.currentTimeMillis()}"
                            val uri = ComposeToImage.saveBitmapAsFile(context, image, fileName)
                            val shareIntent =
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "image/png"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                            context.startActivity(
                                Intent.createChooser(
                                    shareIntent,
                                    context.getString(R.string.share_lyrics),
                                ),
                            )
                        }

                        LyricsShareMode.Video -> {
                            shareLyricsAsVideo(
                                context = context,
                                mediaMetadata = mediaMetadata,
                                payload = payload,
                                options = options,
                                glassStyle = selectedGlassStyle,
                                currentPositionMs = currentPositionMs,
                                audioQuality = audioQuality,
                                preferredStreamClient = preferredStreamClient,
                                videoPanX = videoPan.x,
                                videoPanY = videoPan.y,
                                videoScale = videoScale,
                                onProgress = { progress -> sharingProgress = progress },
                            )
                        }
                    }
                    Timber.tag("LyricsShare").i("share success mode=$shareMode")
                    GlobalLog.append(Log.INFO, "LyricsShare", "share success mode=$shareMode")
                    onDismissRequest()
                } catch (e: Exception) {
                    Timber.tag("LyricsShare").e(e, "share failed mode=$shareMode")
                    GlobalLog.append(Log.ERROR, "LyricsShare", "share failed mode=$shareMode: ${e.message} ${e.stackTraceToString().take(3000)}")
                    Toast
                        .makeText(
                            context,
                            context.getString(R.string.lyrics_share_export_failed, e.message ?: ""),
                            Toast.LENGTH_SHORT,
                        ).show()
                } finally {
                    isSharing = false
                    sharingProgress = null
                }
            }
        }
    }

    LyricsShareStudioDialog(
        mediaMetadata = mediaMetadata,
        payload = payload,
        options = options,
        onOptionsChange = { options = it },
        availableStyles = availableStyles,
        selectedGlassStyle = selectedGlassStyle,
        onStyleSelect = { selectedGlassStyle = it },
        shareMode = shareMode,
        onModeChange = { shareMode = it },
        canvasPreviewUrl = canvasPreviewUrl,
        videoPan = videoPan,
        onVideoPanChange = { videoPan = it },
        videoScale = videoScale,
        onVideoScaleChange = { videoScale = it },
        areAdvancedOptionsVisible = !isCompactLayout || areAdvancedOptionsVisible,
        onShowAdvancedOptions = { areAdvancedOptionsVisible = true },
        isSharing = isSharing,
        isCompactLayout = isCompactLayout,
        onShare = handleShare,
        onDismissRequest = onDismissRequest,
    )

    if (isSharing) {
        LyricsShareLoadingDialog(progress = sharingProgress)
    }
}

private suspend fun shareLyricsAsVideo(
    context: Context,
    mediaMetadata: MediaMetadata?,
    payload: LyricsSharePayload,
    options: LyricsShareImageOptions,
    glassStyle: LyricsGlassStyle,
    currentPositionMs: Long,
    audioQuality: AudioQuality,
    preferredStreamClient: PlayerStreamClient,
    videoPanX: Float = 0f,
    videoPanY: Float = 0f,
    videoScale: Float = 1f,
    onProgress: (Float) -> Unit,
) {
    val mediaId = mediaMetadata?.id ?: error("No song id available")
    val durationMs = options.videoDuration.durationMs
    val startPositionMs = currentPositionMs.coerceAtLeast(0L)
    Timber.tag("LyricsShare").i("shareLyricsAsVideo start mediaId=$mediaId durationMs=$durationMs start=$currentPositionMs pan=($videoPanX,$videoPanY) scale=$videoScale")
    GlobalLog.append(Log.INFO, "LyricsShare", "shareLyricsAsVideo start mediaId=$mediaId durationMs=$durationMs pan=($videoPanX,$videoPanY) scale=$videoScale")
    val (streamUrl, rawCanvasUrl) =
        withContext(Dispatchers.IO) {
            val connectivityManager =
                context.getSystemService<ConnectivityManager>() ?: error("No connectivity service")
            val url = context
                .retryWithoutPlaybackLoginContext {
                    YTPlayerUtils.playerResponseForPlayback(
                        videoId = mediaId,
                        audioQuality = audioQuality,
                        connectivityManager = connectivityManager,
                        preferredStreamClient = preferredStreamClient,
                        networkMetered = context.isLowDataModeActive(),
                    )
                }.getOrThrow().streamUrl
            val canvasUrl = runCatching {
                val storefront = Locale.getDefault().country.takeIf { it.length == 2 }?.lowercase(Locale.ROOT) ?: "us"
                val artwork = resolveCanvasArtworkForPlayback(
                    mediaId = mediaId,
                    songTitleRaw = mediaMetadata.title,
                    artistNameRaw = mediaMetadata.artists.firstOrNull()?.name.orEmpty(),
                    albumId = mediaMetadata.album?.id,
                    albumTitleRaw = mediaMetadata.album?.title,
                    storefront = storefront,
                    requireVertical = options.aspectRatio == LyricsShareAspectRatio.Story,
                    allowNetwork = true,
                    currentIsMusicVideo = mediaMetadata.isMusicVideo,
                )
                artwork?.let {
                    val vertical = it.preferredVerticalAnimationUrl ?: it.videoUrlVertical
                    val horizontal = it.preferredAnimationUrl ?: it.videoUrl
                    when {
                        options.aspectRatio == LyricsShareAspectRatio.Story && !vertical.isNullOrBlank() -> vertical
                        !horizontal.isNullOrBlank() -> horizontal
                        !vertical.isNullOrBlank() -> vertical
                        else -> null
                    }
                }
            }.getOrNull()
            url to canvasUrl
        }
    Timber.tag("LyricsShare").d("fetched streamUrl=${streamUrl.take(60)} rawCanvas=$rawCanvasUrl")
    GlobalLog.append(Log.DEBUG, "LyricsShare", "fetched streamUrl ok rawCanvas=$rawCanvasUrl")
    // Prefer local cached file for MediaMetadataRetriever; download remote http to temp file for reliable export
    val canvasVideoUrl = withContext(Dispatchers.IO) {
        val dl = downloadCanvasForExport(context, rawCanvasUrl) ?: rawCanvasUrl
        Timber.tag("LyricsShare").d("downloadCanvas result raw=$rawCanvasUrl -> $dl")
        GlobalLog.append(Log.DEBUG, "LyricsShare", "downloadCanvas raw=$rawCanvasUrl -> $dl")
        dl
    }
    Timber.tag("LyricsShare").d("renderer create canvas=$canvasVideoUrl isCanvas=${!canvasVideoUrl.isNullOrBlank()}")
    GlobalLog.append(Log.DEBUG, "LyricsShare", "renderer create canvas=$canvasVideoUrl")
    val renderer =
        withContext(Dispatchers.Default) {
            LyricsKaraokeRenderer(
                context = context,
                coverArtUrl = mediaMetadata?.thumbnailUrl,
                songTitle = payload.songTitle,
                artistName = payload.artists,
                timedLyrics = payload.timedLyrics.orEmpty(),
                fallbackLyricsText = payload.lyricsText,
                width = options.aspectRatio.exportWidth,
                height = options.aspectRatio.exportHeight,
                glassStyle = glassStyle,
                options = options,
                startPositionMs = startPositionMs,
                totalDurationMs = durationMs,
                canvasVideoUrl = canvasVideoUrl,
                videoPanX = videoPanX,
                videoPanY = videoPanY,
                videoScale = videoScale,
            )
        }
    val isCanvas = !canvasVideoUrl.isNullOrBlank()
    val result = try {
        LyricsVideoExporter.exportVideo(
            context = context,
            width = options.aspectRatio.exportWidth,
            height = options.aspectRatio.exportHeight,
            durationUs = durationMs * 1000L,
            audioStreamUrl = streamUrl,
            audioStartUs = startPositionMs * 1000L,
            renderFrame = { canvas, frameTimeUs ->
                renderer.render(canvas, startPositionMs + frameTimeUs / 1000L)
            },
            onProgress = onProgress,
            isCanvasBackground = isCanvas,
        )
    } finally {
        withContext(Dispatchers.Default) { runCatching { renderer.release() } }
    }
    val shareIntent =
        Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, result.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    context.startActivity(
        Intent.createChooser(
            shareIntent,
            context.getString(R.string.share_lyrics),
        ),
    )
}

private val canvasShareClient by lazy {
    okhttp3.OkHttpClient.Builder()
        .proxy(moe.rukamori.archivetune.innertube.YouTube.streamOkHttpProxy)
        .connectTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val req = chain.request()
            val host = req.url.host
            val isYouTube = host.endsWith("googlevideo.com") || host.endsWith("googleusercontent.com") || host.endsWith("youtube.com")
            if (!isYouTube) {
                return@addInterceptor chain.proceed(req.newBuilder().header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0 Mobile Safari/537.36").build())
            }
            val profile = moe.rukamori.archivetune.utils.StreamClientUtils.resolveRequestProfile(req.url)
            chain.proceed(moe.rukamori.archivetune.utils.StreamClientUtils.applyRequestProfile(req.newBuilder(), profile).build())
        }.build()
}

private suspend fun downloadCanvasForExport(context: Context, url: String?): String? {
    if (url.isNullOrBlank() || !url.startsWith("http")) {
        Timber.tag("LyricsShare").d("downloadCanvas skip non-http url=$url")
        GlobalLog.append(Log.DEBUG, "LyricsShare", "downloadCanvas skip $url")
        return url
    }
    if (url.startsWith("file://") || url.startsWith("/")) return url
    return withContext(Dispatchers.IO) {
        runCatching {
            val cacheFile = java.io.File(context.cacheDir, "canvas_share_${url.hashCode()}.mp4")
            if (cacheFile.exists() && cacheFile.length() > 0) {
                Timber.tag("LyricsShare").d("downloadCanvas reuse cache ${cacheFile.absolutePath} ${cacheFile.length()} bytes")
                GlobalLog.append(Log.DEBUG, "LyricsShare", "downloadCanvas reuse ${cacheFile.length()} bytes")
                return@runCatching cacheFile.absolutePath
            }
            Timber.tag("LyricsShare").d("downloadCanvas start $url")
            GlobalLog.append(Log.DEBUG, "LyricsShare", "downloadCanvas start $url")
            val request = okhttp3.Request.Builder().url(url).header("Accept", "video/mp4,video/*").build()
            canvasShareClient.newCall(request).execute().use { resp ->
                Timber.tag("LyricsShare").d("downloadCanvas http ${resp.code} ${resp.message} contentType=${resp.body?.contentType()}")
                GlobalLog.append(Log.DEBUG, "LyricsShare", "downloadCanvas http ${resp.code}")
                if (!resp.isSuccessful) return@runCatching null
                val body = resp.body ?: return@runCatching null
                cacheFile.parentFile?.mkdirs()
                cacheFile.outputStream().use { out -> body.byteStream().copyTo(out) }
                if (cacheFile.length() > 0) cacheFile.absolutePath else null
            }
        }.getOrNull()
    }
}

@Composable
private fun LyricsShareStudioDialog(
    mediaMetadata: MediaMetadata?,
    payload: LyricsSharePayload,
    options: LyricsShareImageOptions,
    onOptionsChange: (LyricsShareImageOptions) -> Unit,
    availableStyles: LyricsGlassStyleOptions,
    selectedGlassStyle: LyricsGlassStyle,
    onStyleSelect: (LyricsGlassStyle) -> Unit,
    shareMode: LyricsShareMode,
    onModeChange: (LyricsShareMode) -> Unit,
    canvasPreviewUrl: String? = null,
    videoPan: androidx.compose.ui.geometry.Offset = androidx.compose.ui.geometry.Offset.Zero,
    onVideoPanChange: (androidx.compose.ui.geometry.Offset) -> Unit = {},
    videoScale: Float = 1f,
    onVideoScaleChange: (Float) -> Unit = {},
    areAdvancedOptionsVisible: Boolean,
    onShowAdvancedOptions: () -> Unit,
    isSharing: Boolean,
    isCompactLayout: Boolean,
    onShare: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    Dialog(
        onDismissRequest = {
            if (!isSharing) onDismissRequest()
        },
        properties =
            DialogProperties(
                dismissOnBackPress = !isSharing,
                dismissOnClickOutside = !isSharing,
                usePlatformDefaultWidth = false,
            ),
    ) {
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxSize()
                    .imePadding()
                    .systemBarsPadding(),
            contentAlignment = Alignment.Center,
        ) {
            val outerPadding = if (isCompactLayout) 12.dp else 24.dp
            val maxDialogHeight = (maxHeight - outerPadding * 2).coerceAtLeast(1.dp)
            val maxDialogWidth = if (isCompactLayout) 560.dp else 980.dp

            Surface(
                modifier =
                    Modifier
                        .padding(outerPadding)
                        .fillMaxWidth()
                        .widthIn(max = maxDialogWidth)
                        .heightIn(max = maxDialogHeight),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 6.dp,
            ) {
                LyricsShareStudioScaffold(
                    mediaMetadata = mediaMetadata,
                    payload = payload,
                    options = options,
                    onOptionsChange = onOptionsChange,
                    availableStyles = availableStyles,
                    selectedGlassStyle = selectedGlassStyle,
                    onStyleSelect = onStyleSelect,
                    shareMode = shareMode,
                    onModeChange = onModeChange,
                    canvasPreviewUrl = canvasPreviewUrl,
                    videoPan = videoPan,
                    onVideoPanChange = onVideoPanChange,
                    videoScale = videoScale,
                    onVideoScaleChange = onVideoScaleChange,
                    areAdvancedOptionsVisible = areAdvancedOptionsVisible,
                    onShowAdvancedOptions = onShowAdvancedOptions,
                    isSharing = isSharing,
                    isCompactLayout = isCompactLayout,
                    onShare = onShare,
                    onDismiss = onDismissRequest,
                )
            }
        }
    }
}

@Composable
private fun LyricsShareLoadingDialog(progress: Float?) {
    BasicAlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                LoadingIndicator(modifier = Modifier.size(40.dp))
                Text(
                    text = stringResource(R.string.generating_video),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.please_wait),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                if (progress != null) {
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "${(progress.coerceIn(0f, 1f) * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun LyricsShareStudioScaffold(
    mediaMetadata: MediaMetadata?,
    payload: LyricsSharePayload,
    options: LyricsShareImageOptions,
    onOptionsChange: (LyricsShareImageOptions) -> Unit,
    availableStyles: LyricsGlassStyleOptions,
    selectedGlassStyle: LyricsGlassStyle,
    onStyleSelect: (LyricsGlassStyle) -> Unit,
    shareMode: LyricsShareMode,
    onModeChange: (LyricsShareMode) -> Unit,
    canvasPreviewUrl: String? = null,
    videoPan: androidx.compose.ui.geometry.Offset = androidx.compose.ui.geometry.Offset.Zero,
    onVideoPanChange: (androidx.compose.ui.geometry.Offset) -> Unit = {},
    videoScale: Float = 1f,
    onVideoScaleChange: (Float) -> Unit = {},
    areAdvancedOptionsVisible: Boolean,
    onShowAdvancedOptions: () -> Unit,
    isSharing: Boolean,
    isCompactLayout: Boolean,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val motionScheme = MaterialTheme.motionScheme
    val horizontalPadding = if (isCompactLayout) 16.dp else 20.dp
    val verticalPadding = if (isCompactLayout) 16.dp else 20.dp
    val sectionSpacing = if (isCompactLayout) 14.dp else 18.dp

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .animateContentSize(animationSpec = motionScheme.defaultSpatialSpec()),
    ) {
        Column(
            modifier =
                Modifier
                    .weight(1f, fill = true)
                    .verticalScroll(scrollState)
                    .padding(horizontal = horizontalPadding, vertical = verticalPadding),
            verticalArrangement = Arrangement.spacedBy(sectionSpacing),
        ) {
            if (isCompactLayout) {
                LyricsShareHeader(
                    payload = payload,
                    options = options,
                    modifier = Modifier.fillMaxWidth(),
                )
                PreviewContainer(
                    payload = payload,
                    mediaMetadata = mediaMetadata,
                    selectedGlassStyle = selectedGlassStyle,
                    options = options,
                    shareMode = shareMode,
                    canvasPreviewUrl = canvasPreviewUrl,
                    videoPan = videoPan,
                    onVideoPanChange = onVideoPanChange,
                    videoScale = videoScale,
                    onVideoScaleChange = onVideoScaleChange,
                    isCompactLayout = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                ControlsSection(
                    options = options,
                    onOptionsChange = onOptionsChange,
                    availableStyles = availableStyles,
                    selectedGlassStyle = selectedGlassStyle,
                    onStyleSelect = onStyleSelect,
                    shareMode = shareMode,
                    onModeChange = onModeChange,
                    canvasPreviewUrl = canvasPreviewUrl,
                    videoPan = videoPan,
                    onVideoPanChange = onVideoPanChange,
                    videoScale = videoScale,
                    onVideoScaleChange = onVideoScaleChange,
                    areAdvancedOptionsVisible = areAdvancedOptionsVisible,
                    onShowAdvancedOptions = onShowAdvancedOptions,
                    isCompactLayout = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    PreviewContainer(
                        payload = payload,
                        mediaMetadata = mediaMetadata,
                        selectedGlassStyle = selectedGlassStyle,
                        options = options,
                        shareMode = shareMode,
                        canvasPreviewUrl = canvasPreviewUrl,
                        videoPan = videoPan,
                        onVideoPanChange = onVideoPanChange,
                        videoScale = videoScale,
                        onVideoScaleChange = onVideoScaleChange,
                        isCompactLayout = false,
                        modifier = Modifier.weight(1.1f),
                    )
                    Column(
                        modifier = Modifier.weight(0.9f),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        LyricsShareHeader(
                            payload = payload,
                            options = options,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        ControlsSection(
                            options = options,
                            onOptionsChange = onOptionsChange,
                            availableStyles = availableStyles,
                            selectedGlassStyle = selectedGlassStyle,
                            onStyleSelect = onStyleSelect,
                            shareMode = shareMode,
                            onModeChange = onModeChange,
                            canvasPreviewUrl = canvasPreviewUrl,
                            videoPan = videoPan,
                            onVideoPanChange = onVideoPanChange,
                            videoScale = videoScale,
                            onVideoScaleChange = onVideoScaleChange,
                            areAdvancedOptionsVisible = areAdvancedOptionsVisible,
                            onShowAdvancedOptions = onShowAdvancedOptions,
                            isCompactLayout = false,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        ActionsSection(
            isSharing = isSharing,
            isCompactLayout = isCompactLayout,
            onShare = onShare,
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun LyricsShareHeader(
    payload: LyricsSharePayload,
    options: LyricsShareImageOptions,
    modifier: Modifier = Modifier,
) {
    val lyricSnippet =
        remember(payload.lyricsText) {
            payload.lyricsText
                .lineSequence()
                .map(String::trim)
                .firstOrNull { it.isNotEmpty() }
                .orEmpty()
        }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.share_lyrics),
            style = MaterialTheme.typography.labelLargeEmphasized,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = payload.songTitle,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = payload.artists,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (lyricSnippet.isNotBlank()) {
            Text(
                text = lyricSnippet,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        LyricsShareInfoPill(
            text =
                stringResource(
                    R.string.lyrics_share_resolution_value,
                    options.aspectRatio.exportWidth,
                    options.aspectRatio.exportHeight,
                ),
            emphasized = true,
        )
    }
}

@Composable
private fun PreviewContainer(
    payload: LyricsSharePayload,
    mediaMetadata: MediaMetadata?,
    selectedGlassStyle: LyricsGlassStyle,
    options: LyricsShareImageOptions,
    shareMode: LyricsShareMode = LyricsShareMode.Image,
    canvasPreviewUrl: String? = null,
    videoPan: androidx.compose.ui.geometry.Offset = androidx.compose.ui.geometry.Offset.Zero,
    onVideoPanChange: (androidx.compose.ui.geometry.Offset) -> Unit = {},
    videoScale: Float = 1f,
    onVideoScaleChange: (Float) -> Unit = {},
    isCompactLayout: Boolean,
    modifier: Modifier = Modifier,
) {
    val previewWidthFraction =
        when (options.aspectRatio) {
            LyricsShareAspectRatio.Square -> if (isCompactLayout) 0.84f else 0.82f
            LyricsShareAspectRatio.Portrait -> if (isCompactLayout) 0.62f else 0.62f
            LyricsShareAspectRatio.Story -> if (isCompactLayout) 0.44f else 0.42f
        }
    val previewMaxWidth = if (isCompactLayout) 320.dp else 420.dp
    val useCanvasPreview = shareMode == LyricsShareMode.Video && !canvasPreviewUrl.isNullOrBlank()

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(previewWidthFraction)
                            .widthIn(max = previewMaxWidth)
                            .aspectRatio(options.aspectRatio.previewAspectRatio)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = MaterialTheme.shapes.large,
                            ).padding(10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (useCanvasPreview) {
                        MovableCanvasPreview(
                            canvasUrl = canvasPreviewUrl!!,
                            coverArtUrl = mediaMetadata?.thumbnailUrl,
                            payload = payload,
                            songTitle = payload.songTitle,
                            artistName = payload.artists,
                            glassStyle = selectedGlassStyle,
                            options = options,
                            videoPan = videoPan,
                            onVideoPanChange = onVideoPanChange,
                            videoScale = videoScale,
                            onVideoScaleChange = onVideoScaleChange,
                        )
                    } else {
                        LyricsImageCard(
                            lyricText = payload.lyricsText,
                            songTitle = payload.songTitle,
                            artistName = payload.artists,
                            coverArtUrl = mediaMetadata?.thumbnailUrl,
                            glassStyle = selectedGlassStyle,
                            shareOptions = options,
                        )
                    }
                }
                if (useCanvasPreview) {
                    Text(
                        text = stringResource(R.string.lyrics_share_drag_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

@Composable
private fun MovableCanvasPreview(
    canvasUrl: String,
    coverArtUrl: String?,
    payload: LyricsSharePayload,
    songTitle: String,
    artistName: String,
    glassStyle: LyricsGlassStyle,
    options: LyricsShareImageOptions,
    videoPan: androidx.compose.ui.geometry.Offset,
    onVideoPanChange: (androidx.compose.ui.geometry.Offset) -> Unit,
    videoScale: Float,
    onVideoScaleChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var boxSize by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(MaterialTheme.shapes.large)
            .onGloballyPositioned { coordinates -> boxSize = androidx.compose.ui.geometry.Size(coordinates.size.width.toFloat(), coordinates.size.height.toFloat()) },
        contentAlignment = Alignment.Center,
    ) {
        var previewBitmap by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<android.graphics.Bitmap?>(null) }
        val previewContext = androidx.compose.ui.platform.LocalContext.current
        androidx.compose.runtime.LaunchedEffect(canvasUrl) {
            previewBitmap = withContext(Dispatchers.IO) {
                runCatching {
                    // For http, download to local file first (reliable, handles googlevideo signatures) – same as export
                    val localPath = if (canvasUrl.startsWith("http")) {
                        downloadCanvasForExport(previewContext, canvasUrl) ?: canvasUrl
                    } else canvasUrl
                    Timber.tag("LyricsShare").d("preview extract localPath=$localPath")
                    GlobalLog.append(Log.DEBUG, "LyricsShare", "preview extract $localPath")
                    val r = android.media.MediaMetadataRetriever()
                    try {
                        when {
                            localPath.startsWith("file://") -> r.setDataSource(localPath.removePrefix("file://"))
                            localPath.startsWith("/") -> r.setDataSource(localPath)
                            localPath.startsWith("http") -> r.setDataSource(localPath, HashMap())
                            else -> r.setDataSource(localPath)
                        }
                        val bmp = r.getFrameAtTime(0, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        Timber.tag("LyricsShare").d("preview frame ${bmp?.width}x${bmp?.height} for $canvasUrl")
                        GlobalLog.append(Log.DEBUG, "LyricsShare", "preview frame ${bmp?.width}x${bmp?.height}")
                        bmp
                    } finally { runCatching { r.release() } }
                }.onFailure {
                    Timber.tag("LyricsShare").e(it, "preview extract failed $canvasUrl")
                    GlobalLog.append(Log.ERROR, "LyricsShare", "preview extract failed $canvasUrl: ${it.message}")
                }.getOrNull()
            }
        }
        androidx.compose.runtime.DisposableEffect(canvasUrl) {
            onDispose { previewBitmap?.let { runCatching { if (!it.isRecycled) it.recycle() } }; previewBitmap = null }
        }
        // Canvas preview – lightweight static first frame (no extra ExoPlayer, avoids pipeline leak & play/pause interference)
        // Video is still used for export (full motion), preview just shows first frame draggable – player-like but isolated
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    translationX = videoPan.x * (boxSize.width.takeIf { it > 0 } ?: 1f),
                    translationY = videoPan.y * (boxSize.height.takeIf { it > 0 } ?: 1f),
                    scaleX = videoScale,
                    scaleY = videoScale,
                )
                .pointerInput(videoPan, videoScale) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (videoScale * zoom).coerceIn(0.85f, 2.2f)
                        onVideoScaleChange(newScale)
                        if (pan != Offset.Zero) {
                            val w = boxSize.width.takeIf { it > 0 } ?: 1f
                            val h = boxSize.height.takeIf { it > 0 } ?: 1f
                            val dx = pan.x / w
                            val dy = pan.y / h
                            val next = Offset(
                                (videoPan.x + dx).coerceIn(-0.5f, 0.5f),
                                (videoPan.y + dy).coerceIn(-0.5f, 0.5f),
                            )
                            onVideoPanChange(next)
                        }
                    }
                },
        ) {
            if (previewBitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = previewBitmap!!.asImageBitmap(),
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
                    if (coverArtUrl != null) {
                        coil3.compose.AsyncImage(
                            model = coverArtUrl,
                            contentDescription = null,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    androidx.compose.material3.LinearProgressIndicator(modifier = Modifier.align(Alignment.Center).fillMaxWidth(0.5f))
                }
            }
        }
        // Dim scrim + centered lyrics (middle of video, glass style)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(12.dp),
            ) {
                // Glass tint panel for readability – mirrors exporter
                androidx.compose.material3.Surface(
                    shape = MaterialTheme.shapes.large,
                    color = glassStyle.surfaceTint.copy(alpha = glassStyle.surfaceAlpha * 0.82f),
                    tonalElevation = 0.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = payload.lyricsText.lineSequence().firstOrNull { it.isNotBlank() } ?: songTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = glassStyle.textColor,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "$songTitle • $artistName",
                            style = MaterialTheme.typography.labelSmall,
                            color = glassStyle.secondaryTextColor,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ControlsSection(
    options: LyricsShareImageOptions,
    onOptionsChange: (LyricsShareImageOptions) -> Unit,
    availableStyles: LyricsGlassStyleOptions,
    selectedGlassStyle: LyricsGlassStyle,
    onStyleSelect: (LyricsGlassStyle) -> Unit,
    shareMode: LyricsShareMode,
    onModeChange: (LyricsShareMode) -> Unit,
    canvasPreviewUrl: String? = null,
    videoPan: androidx.compose.ui.geometry.Offset = androidx.compose.ui.geometry.Offset.Zero,
    onVideoPanChange: (androidx.compose.ui.geometry.Offset) -> Unit = {},
    videoScale: Float = 1f,
    onVideoScaleChange: (Float) -> Unit = {},
    areAdvancedOptionsVisible: Boolean,
    onShowAdvancedOptions: () -> Unit,
    isCompactLayout: Boolean,
    modifier: Modifier = Modifier,
) {
    val motionScheme = MaterialTheme.motionScheme
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .animateContentSize(animationSpec = motionScheme.defaultSpatialSpec()),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            LyricsShareControlGroup(title = stringResource(R.string.lyrics_share_format)) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    LyricsShareMode.entries.forEach { mode ->
                        LyricsShareModeOption(
                            mode = mode,
                            selected = shareMode == mode,
                            onClick = { onModeChange(mode) },
                        )
                    }
                }
            }

            LyricsShareControlGroup(title = stringResource(R.string.lyrics_share_layout)) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    LyricsShareAspectRatio.entries.forEach { aspectRatio ->
                        LyricsAspectRatioOption(
                            aspectRatio = aspectRatio,
                            selected = options.aspectRatio == aspectRatio,
                            onClick = { onOptionsChange(options.copy(aspectRatio = aspectRatio)) },
                        )
                    }
                }
            }

            if (shareMode == LyricsShareMode.Video) {
                LyricsShareControlGroup(title = stringResource(R.string.lyrics_share_video_duration)) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        LyricsShareVideoDuration.entries.forEach { duration ->
                            LyricsVideoDurationOption(
                                duration = duration,
                                selected = options.videoDuration == duration,
                                onClick = {
                                    onOptionsChange(options.copy(videoDuration = duration))
                                },
                            )
                        }
                    }
                }
                // Movable video controls – player-like, only for this share (Video mode with canvas)
                if (shareMode == LyricsShareMode.Video && !canvasPreviewUrl.isNullOrBlank()) {
                    LyricsShareControlGroup(title = stringResource(R.string.lyrics_share_drag_hint)) {
                        LyricsShareSlider(
                            title = "Zoom",
                            valueLabel = "${(videoScale * 100).toInt()}%",
                            value = videoScale,
                            onValueChange = { onVideoScaleChange(it) },
                            valueRange = 0.85f..2.2f,
                        )
                        androidx.compose.material3.TextButton(onClick = {
                            onVideoPanChange(Offset.Zero)
                            onVideoScaleChange(1f)
                        }) { Text("Reset position") }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            LyricsShareControlGroup(title = stringResource(R.string.customize_colors)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    maxItemsInEachRow = if (isCompactLayout) 2 else 2,
                ) {
                    availableStyles.items.forEach { style ->
                        LyricsStyleOption(
                            style = style,
                            selected = selectedGlassStyle == style,
                            onClick = { onStyleSelect(style) },
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            if (areAdvancedOptionsVisible) {
                LyricsShareControlGroup(title = stringResource(R.string.more_options)) {
                    LyricsShareSlider(
                        title = stringResource(R.string.lyrics_share_background_blur),
                        valueLabel = stringResource(R.string.lyrics_share_background_blur_value, options.sanitizedBlurRadius.toInt()),
                        value = options.blurRadius,
                        onValueChange = { onOptionsChange(options.copy(blurRadius = it)) },
                        valueRange = 0f..48f,
                    )
                    LyricsShareSlider(
                        title = stringResource(R.string.lyrics_share_background_dim),
                        valueLabel = stringResource(R.string.lyrics_share_background_dim_value, (options.sanitizedDimAmount * 100).toInt()),
                        value = options.dimAmount,
                        onValueChange = { onOptionsChange(options.copy(dimAmount = it)) },
                        valueRange = 0.6f..1.6f,
                    )
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    ) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 64.dp)
                                    .clip(MaterialTheme.shapes.large)
                                    .clickable { onOptionsChange(options.copy(showArtwork = !options.showArtwork)) }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.lyrics_share_show_cover),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = stringResource(R.string.lyrics_share_show_cover_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Switch(
                                checked = options.showArtwork,
                                onCheckedChange = { onOptionsChange(options.copy(showArtwork = it)) },
                            )
                        }
                    }
                }
            } else {
                TextButton(
                    onClick = onShowAdvancedOptions,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Text(
                        text = stringResource(R.string.more_options),
                        style = MaterialTheme.typography.labelLargeEmphasized,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun LyricsShareControlGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        content()
    }
}

@Composable
private fun LyricsShareInfoPill(
    text: String,
    emphasized: Boolean = false,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color =
            if (emphasized) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
            },
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color =
                if (emphasized) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun LyricsAspectRatioOption(
    aspectRatio: LyricsShareAspectRatio,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motionScheme = MaterialTheme.motionScheme
    val optionShape = if (selected) MaterialTheme.shapes.extraLarge else MaterialTheme.shapes.medium
    val containerColor by animateColorAsState(
        targetValue =
            if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLowest
            },
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "lyricsAspectContainer",
    )
    val contentColor by animateColorAsState(
        targetValue =
            if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "lyricsAspectContent",
    )

    Surface(
        modifier =
            modifier
                .widthIn(min = 96.dp)
                .heightIn(min = 48.dp)
                .clip(optionShape)
                .clickable(onClick = onClick),
        shape = optionShape,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Text(
            text = stringResource(aspectRatio.labelRes),
            style =
                if (selected) {
                    MaterialTheme.typography.labelLargeEmphasized
                } else {
                    MaterialTheme.typography.labelLarge
                },
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun LyricsShareModeOption(
    mode: LyricsShareMode,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motionScheme = MaterialTheme.motionScheme
    val optionShape = if (selected) MaterialTheme.shapes.extraLarge else MaterialTheme.shapes.medium
    val containerColor by animateColorAsState(
        targetValue =
            if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLowest
            },
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "lyricsShareModeContainer",
    )
    val contentColor by animateColorAsState(
        targetValue =
            if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "lyricsShareModeContent",
    )

    Surface(
        modifier =
            modifier
                .heightIn(min = 48.dp)
                .clip(optionShape)
                .clickable(onClick = onClick),
        shape = optionShape,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Text(
            text = stringResource(mode.labelRes),
            style =
                if (selected) {
                    MaterialTheme.typography.labelLargeEmphasized
                } else {
                    MaterialTheme.typography.labelLarge
                },
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun LyricsVideoDurationOption(
    duration: LyricsShareVideoDuration,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motionScheme = MaterialTheme.motionScheme
    val optionShape = if (selected) MaterialTheme.shapes.extraLarge else MaterialTheme.shapes.medium
    val containerColor by animateColorAsState(
        targetValue =
            if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLowest
            },
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "lyricsVideoDurationContainer",
    )
    val contentColor by animateColorAsState(
        targetValue =
            if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "lyricsVideoDurationContent",
    )

    Surface(
        modifier =
            modifier
                .widthIn(min = 88.dp)
                .heightIn(min = 44.dp)
                .clip(optionShape)
                .clickable(onClick = onClick),
        shape = optionShape,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Text(
            text = stringResource(duration.labelRes),
            style =
                if (selected) {
                    MaterialTheme.typography.labelLargeEmphasized
                } else {
                    MaterialTheme.typography.labelLarge
                },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun LyricsStyleOption(
    style: LyricsGlassStyle,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motionScheme = MaterialTheme.motionScheme
    val optionShape = if (selected) MaterialTheme.shapes.extraLarge else MaterialTheme.shapes.large
    val borderColor by animateColorAsState(
        targetValue =
            if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "lyricsStyleBorder",
    )
    val containerColor by animateColorAsState(
        targetValue =
            if (selected) {
                MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp)
            } else {
                MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
            },
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "lyricsStyleContainer",
    )

    Surface(
        modifier =
            modifier
                .widthIn(min = 108.dp)
                .heightIn(min = 48.dp)
                .clip(optionShape)
                .clickable(onClick = onClick),
        shape = optionShape,
        color = containerColor,
        border = BorderStroke(width = if (selected) 1.5.dp else 1.dp, color = borderColor),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(28.dp)
                        .clip(MaterialTheme.shapes.extraLarge)
                        .background(style.surfaceTint.copy(alpha = 0.8f)),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(16.dp)
                            .background(
                                color = style.surfaceTint.copy(alpha = style.surfaceAlpha),
                                shape = MaterialTheme.shapes.extraLarge,
                            ),
                )
            }
            Text(
                text = stringResource(style.labelRes),
                style = MaterialTheme.typography.labelLargeEmphasized,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}

@Composable
private fun LyricsShareSlider(
    title: String,
    valueLabel: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ActionsSection(
    isSharing: Boolean,
    isCompactLayout: Boolean,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val actionModifier = Modifier.height(52.dp)
    val contentPadding =
        Modifier.padding(
            horizontal = if (isCompactLayout) 16.dp else 24.dp,
            vertical = 14.dp,
        )

    if (isCompactLayout) {
        Column(
            modifier =
                modifier
                    .fillMaxWidth()
                    .then(contentPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = onShare,
                enabled = !isSharing,
                modifier = actionModifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
            ) {
                Text(
                    text = stringResource(R.string.share),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(
                onClick = onDismiss,
                enabled = !isSharing,
                modifier = actionModifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
            ) {
                Text(
                    text = stringResource(R.string.cancel),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    } else {
        Row(
            modifier =
                modifier
                    .fillMaxWidth()
                    .then(contentPadding),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onDismiss,
                enabled = !isSharing,
                modifier =
                    actionModifier
                        .weight(1f)
                        .fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
            ) {
                Text(
                    text = stringResource(R.string.cancel),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Button(
                onClick = onShare,
                enabled = !isSharing,
                modifier =
                    actionModifier
                        .weight(1.2f)
                        .fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
            ) {
                Text(
                    text = stringResource(R.string.share),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
