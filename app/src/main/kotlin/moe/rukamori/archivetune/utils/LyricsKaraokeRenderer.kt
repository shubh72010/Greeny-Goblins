/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.Log
import moe.rukamori.archivetune.utils.GlobalLog
import timber.log.Timber
import android.graphics.RectF
import android.graphics.Typeface
import android.media.MediaMetadataRetriever
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withClip
import androidx.core.graphics.withTranslation
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import moe.rukamori.archivetune.lyrics.LyricsEntry
import moe.rukamori.archivetune.ui.component.LyricsGlassStyle
import moe.rukamori.archivetune.ui.component.LyricsShareImageOptions
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.runBlocking

/**
 * Renders the animated karaoke "glass card" frames used by the lyrics share video.
 *
 * The static background (blurred cover art, glass panel, header, logo) is rendered once
 * into a reusable bitmap; each frame only redraws the synced lyrics window with a
 * word-level karaoke fill driven by [songTimeMs].
 */
class LyricsKaraokeRenderer(
    context: Context,
    coverArtUrl: String?,
    songTitle: String,
    artistName: String,
    private val timedLyrics: List<LyricsEntry>,
    private val fallbackLyricsText: String,
    private val width: Int,
    private val height: Int,
    private val glassStyle: LyricsGlassStyle,
    private val options: LyricsShareImageOptions,
    startPositionMs: Long,
    private val totalDurationMs: Long,
    canvasVideoUrl: String? = null,
    private val videoPanX: Float = 0f,
    private val videoPanY: Float = 0f,
    private val videoScale: Float = 1f,
) {
    private class WordLayout(
        val text: String,
        val startMs: Long,
        val endMs: Long,
        val x: Float,
        val width: Float,
    )

    private class LyricsLineLayout(
        val timeMs: Long,
        val durationMs: Long,
        val layout: StaticLayout,
        val words: List<WordLayout>?,
        val totalWordWidth: Float,
    )

    private val canvasWidth = width.coerceAtLeast(1)
    private val canvasHeight = height.coerceAtLeast(1)
    private val baseSize = min(canvasWidth, canvasHeight)

    private val mainTextColor = colorToArgb(glassStyle.textColor)
    private val secondaryTextColor = colorToArgb(glassStyle.secondaryTextColor)
    private val dimLineColor = withAlpha(mainTextColor, 0.35f)
    private val dimWordColor = withAlpha(mainTextColor, 0.45f)

    private val backgroundBitmap: Bitmap?
    private val overlayBitmap: Bitmap?
    private val lineLayouts: List<LyricsLineLayout>

    private val lyricsMaxWidth: Int
    private val lyricsLeft: Float
    private val lyricsTop: Float
    private val availableLyricsHeight: Float
    private val lyricsLineHeight: Float
    private val lyricsPaint: TextPaint
    private val wordPaint: TextPaint

    private val maxVisibleLines = 5

    // JusPlayer canvas video background – cached for speed (ponytail: LRU 32 buckets, 80ms granularity)
    private var videoRetriever: MediaMetadataRetriever? = null
    private var videoDurationUs: Long = 0L
    private var useVideoBackground: Boolean = false
    private val startPositionMs: Long = startPositionMs
    private val videoFrameCache = object : LinkedHashMap<Long, Bitmap>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Bitmap>?): Boolean = size > 32
    }
    private val videoCacheLock = Any()

    init {
        Timber.tag("LyricsVideo").d("Renderer init: canvasVideoUrl=$canvasVideoUrl coverArtUrl=$coverArtUrl ${width}x$height start=$startPositionMs dur=$totalDurationMs pan=($videoPanX,$videoPanY) scale=$videoScale")
        GlobalLog.append(Log.DEBUG, "LyricsVideo", "Renderer init canvas=$canvasVideoUrl cover=$coverArtUrl ${width}x$height")
        // Try to init canvas video retriever if url provided (local file preferred for reliability)
        if (!canvasVideoUrl.isNullOrBlank()) {
            runCatching {
                val retriever = MediaMetadataRetriever()
                var ok = false
                // file:// or absolute path
                when {
                    canvasVideoUrl.startsWith("file://") -> {
                        val path = canvasVideoUrl.removePrefix("file://")
                        retriever.setDataSource(path)
                        ok = true
                    }
                    canvasVideoUrl.startsWith("/") -> {
                        retriever.setDataSource(canvasVideoUrl)
                        ok = true
                    }
                    canvasVideoUrl.startsWith("content://") -> {
                        retriever.setDataSource(context, android.net.Uri.parse(canvasVideoUrl))
                        ok = true
                    }
                    canvasVideoUrl.startsWith("http") -> {
                        // Try remote with headers; may fail for HLS – caller should prefer local file
                        retriever.setDataSource(canvasVideoUrl, HashMap())
                        ok = true
                    }
                    else -> {
                        retriever.setDataSource(canvasVideoUrl)
                        ok = true
                    }
                }
                if (ok) {
                    val durMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    Timber.tag("LyricsVideo").d("Retriever ok=$ok durMs=$durMs url=$canvasVideoUrl")
                    GlobalLog.append(Log.DEBUG, "LyricsVideo", "Retriever durMs=$durMs url=$canvasVideoUrl")
                    if (durMs > 500L) {
                        videoRetriever = retriever
                        videoDurationUs = durMs * 1000L
                        useVideoBackground = true
                        Timber.tag("LyricsVideo").i("useVideoBackground=true durationUs=$videoDurationUs")
                        GlobalLog.append(Log.INFO, "LyricsVideo", "useVideoBackground=true durationUs=$videoDurationUs")
                    } else {
                        Timber.tag("LyricsVideo").w("Retriever dur too short $durMs, fallback to image")
                        GlobalLog.append(Log.WARN, "LyricsVideo", "Retriever dur too short $durMs")
                        runCatching { retriever.release() }
                    }
                } else {
                    runCatching { retriever.release() }
                }
            }.onFailure {
                // remote HLS often fails; keep fallback
                Timber.tag("LyricsVideo").e(it, "canvas retriever failed for $canvasVideoUrl")
                GlobalLog.append(Log.ERROR, "LyricsVideo", "canvas retriever failed $canvasVideoUrl: ${it.message}")
            }
        } else {
            Timber.tag("LyricsVideo").d("No canvasVideoUrl, using blurred cover")
            GlobalLog.append(Log.DEBUG, "LyricsVideo", "No canvasVideoUrl, using blurred cover")
        }

        val bgColor = 0xFF121212.toInt()

        var coverArtBitmap: Bitmap? = null
        if (coverArtUrl != null) {
            runCatching {
                val loader = ImageLoader(context)
                val request =
                    ImageRequest
                        .Builder(context)
                        .data(coverArtUrl)
                        .size(max(canvasWidth, canvasHeight))
                        .allowHardware(false)
                        .build()
                coverArtBitmap = runBlocking { loader.execute(request).image?.toBitmap() }
            }
        }

        val fittedArt =
            coverArtBitmap?.let {
                ComposeToImage.fitBitmap(
                    source = it,
                    targetWidth = canvasWidth,
                    targetHeight = canvasHeight,
                    backgroundColor = bgColor,
                )
            }

        // Shared glass geometry
        val glassMargin = baseSize * 0.045f
        val glassLeft = glassMargin
        val glassTop = glassMargin
        val glassRight = canvasWidth - glassMargin
        val glassBottom = canvasHeight - glassMargin
        val glassWidth = glassRight - glassLeft
        val glassHeight = glassBottom - glassTop
        val glassCornerRadius = baseSize * 0.05f

        var tmpBackground: Bitmap? = null
        var tmpOverlay: Bitmap? = null

        if (useVideoBackground) {
            // Build transparent overlay (glass + header + logo) for video mode
            val overlay = createBitmap(canvasWidth, canvasHeight)
            val canvas = Canvas(overlay)
            buildGlassAndHeader(
                canvas = canvas,
                context = context,
                coverArtBitmap = coverArtBitmap,
                fittedArt = null, // no blur crop over video
                glassLeft = glassLeft,
                glassTop = glassTop,
                glassRight = glassRight,
                glassBottom = glassBottom,
                glassWidth = glassWidth,
                glassHeight = glassHeight,
                glassCornerRadius = glassCornerRadius,
                drawFullDim = false,
                songTitle = songTitle,
                artistName = artistName,
            )
            tmpOverlay = overlay
            tmpBackground = null
        } else {
            val bitmap = createBitmap(canvasWidth, canvasHeight)
            val canvas = Canvas(bitmap)
            if (fittedArt != null) {
                val blurredBackground = ComposeToImage.blurBitmap(fittedArt, options.sanitizedBlurRadius)
                canvas.drawBitmap(blurredBackground, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
            } else {
                canvas.drawColor(bgColor)
            }
            val dimPaint =
                Paint().apply {
                    color =
                        Color.argb(
                            ((glassStyle.backgroundDimAlpha * options.sanitizedDimAmount).coerceIn(0f, 0.95f) * 255).toInt(),
                            0,
                            0,
                            0,
                        )
                    isAntiAlias = true
                }
            canvas.drawRect(RectF(0f, 0f, canvasWidth.toFloat(), canvasHeight.toFloat()), dimPaint)
            buildGlassAndHeader(
                canvas = canvas,
                context = context,
                coverArtBitmap = coverArtBitmap,
                fittedArt = fittedArt,
                glassLeft = glassLeft,
                glassTop = glassTop,
                glassRight = glassRight,
                glassBottom = glassBottom,
                glassWidth = glassWidth,
                glassHeight = glassHeight,
                glassCornerRadius = glassCornerRadius,
                drawFullDim = false,
                songTitle = songTitle,
                artistName = artistName,
                alreadyHasDim = true,
            )
            tmpBackground = bitmap
            tmpOverlay = null
        }
        backgroundBitmap = tmpBackground
        overlayBitmap = tmpOverlay

        // Preserve computed geometry for lyrics placement (duplicated here for scope)
        // We need to recompute lyricsTop/availableLyricsHeight etc — reuse same values as overlay builder
        val overlayMetrics = computeLyricsMetrics(
            glassLeft = glassLeft,
            glassTop = glassTop,
            glassRight = glassRight,
            glassBottom = glassBottom,
            glassWidth = glassWidth,
            glassHeight = glassHeight,
            coverArtBitmap = coverArtBitmap,
            songTitle = songTitle,
            artistName = artistName,
        )
        lyricsMaxWidth = overlayMetrics.maxWidth
        lyricsTop = overlayMetrics.top
        availableLyricsHeight = overlayMetrics.availableHeight
        lyricsLeft = overlayMetrics.left
        lyricsLineHeight = overlayMetrics.lineHeight
        lyricsPaint = overlayMetrics.lyricsPaint
        wordPaint = overlayMetrics.wordPaint
        lineLayouts = overlayMetrics.lineLayouts
        // draw logos already handled in overlay/background; nothing more
        if (!useVideoBackground) {
            // background already contains logo; nothing to patch
        }
    }

    private data class LyricsMetrics(
        val maxWidth: Int,
        val left: Float,
        val top: Float,
        val availableHeight: Float,
        val lineHeight: Float,
        val lyricsPaint: TextPaint,
        val wordPaint: TextPaint,
        val lineLayouts: List<LyricsLineLayout>,
    )

    private fun buildGlassAndHeader(
        canvas: Canvas,
        context: Context,
        coverArtBitmap: Bitmap?,
        fittedArt: Bitmap?,
        glassLeft: Float,
        glassTop: Float,
        glassRight: Float,
        glassBottom: Float,
        glassWidth: Float,
        glassHeight: Float,
        glassCornerRadius: Float,
        drawFullDim: Boolean,
        songTitle: String,
        artistName: String,
        alreadyHasDim: Boolean = false,
    ) {
        val glassRect = RectF(glassLeft, glassTop, glassRight, glassBottom)
        val glassPath = Path().apply { addRoundRect(glassRect, glassCornerRadius, glassCornerRadius, Path.Direction.CW) }
        if (fittedArt != null) {
            val frostedCrop = ComposeToImage.blurBitmap(fittedArt, (options.sanitizedBlurRadius + 10f).coerceIn(8f, 48f))
            canvas.withClip(glassPath) { drawBitmap(frostedCrop, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG)) }
        }
        canvas.drawRoundRect(
            glassRect, glassCornerRadius, glassCornerRadius,
            Paint().apply {
                color = glassStyle.surfaceTint.let { Color.argb((glassStyle.surfaceAlpha * 255).toInt(), (it.red * 255).toInt(), (it.green * 255).toInt(), (it.blue * 255).toInt()) }
                isAntiAlias = true
            },
        )
        canvas.drawRoundRect(
            glassRect, glassCornerRadius, glassCornerRadius,
            Paint().apply {
                color = glassStyle.overlayColor.let { Color.argb((glassStyle.overlayAlpha * 255).toInt(), (it.red * 255).toInt(), (it.green * 255).toInt(), (it.blue * 255).toInt()) }
                isAntiAlias = true
            },
        )
        canvas.drawRoundRect(
            glassRect, glassCornerRadius, glassCornerRadius,
            Paint().apply { style = Paint.Style.STROKE; strokeWidth = 1.5f; color = Color.argb(25, 255, 255, 255); isAntiAlias = true },
        )
        val contentPadding = min(glassWidth, glassHeight) * 0.08f
        val contentLeft = glassLeft + contentPadding
        val contentTop = glassTop + contentPadding
        val contentRight = glassRight - contentPadding
        val imageCornerRadius = baseSize * 0.035f
        val coverSize = min(glassWidth * 0.18f, glassHeight * 0.15f)
        val topRowGap = baseSize * 0.035f
        val titlePaint = TextPaint().apply { color = mainTextColor; textSize = baseSize * 0.038f; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true; letterSpacing = -0.02f }
        val artistPaint = TextPaint().apply { color = secondaryTextColor; textSize = baseSize * 0.028f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL); isAntiAlias = true }
        val showingArtwork = options.showArtwork && coverArtBitmap != null
        if (showingArtwork) {
            val rect = RectF(contentLeft, contentTop, contentLeft + coverSize, contentTop + coverSize)
            val path = Path().apply { addRoundRect(rect, imageCornerRadius, imageCornerRadius, Path.Direction.CW) }
            canvas.withClip(path) { drawBitmap(coverArtBitmap ?: return@withClip, null, rect, Paint(Paint.FILTER_BITMAP_FLAG)) }
            canvas.drawRoundRect(rect, imageCornerRadius, imageCornerRadius, Paint().apply { style = Paint.Style.STROKE; strokeWidth = 1f; color = Color.argb(38, 255, 255, 255); isAntiAlias = true })
        }
        val textMaxWidth = if (showingArtwork) (contentRight - contentLeft - coverSize - topRowGap).toInt() else (contentRight - contentLeft).toInt()
        val textStartX = if (showingArtwork) contentLeft + coverSize + topRowGap else contentLeft
        val headerAlignment = if (showingArtwork) Layout.Alignment.ALIGN_NORMAL else Layout.Alignment.ALIGN_CENTER
        val titleLayout = StaticLayout.Builder.obtain(songTitle, 0, songTitle.length, titlePaint, textMaxWidth).setAlignment(headerAlignment).setMaxLines(1).build()
        val artistLayout = StaticLayout.Builder.obtain(artistName, 0, artistName.length, artistPaint, textMaxWidth).setAlignment(headerAlignment).setMaxLines(1).build()
        val topBlockHeight = if (showingArtwork) coverSize else (titleLayout.height + artistLayout.height + 6f)
        val imageCenter = contentTop + topBlockHeight / 2f
        val textBlockHeight = titleLayout.height + artistLayout.height + 6f
        val textBlockY = imageCenter - textBlockHeight / 2f
        canvas.withTranslation(textStartX, textBlockY) {
            titleLayout.draw(this); translate(0f, titleLayout.height.toFloat() + 6f); artistLayout.draw(this)
        }
        val logoBlockHeight = (baseSize * 0.08f).toInt()
        ComposeToImage.AppLogo(context = context, canvas = canvas, canvasWidth = canvasWidth, canvasHeight = canvasHeight, padding = contentLeft, bottomPadding = glassBottom - contentPadding, circleColor = secondaryTextColor, logoTint = if (glassStyle.isDark) 0xDD000000.toInt() else 0xE6FFFFFF.toInt(), textColor = secondaryTextColor)
    }

    private fun computeLyricsMetrics(
        glassLeft: Float,
        glassTop: Float,
        glassRight: Float,
        glassBottom: Float,
        glassWidth: Float,
        glassHeight: Float,
        coverArtBitmap: Bitmap?,
        songTitle: String,
        artistName: String,
    ): LyricsMetrics {
        val contentPadding = min(glassWidth, glassHeight) * 0.08f
        val contentLeft = glassLeft + contentPadding
        val contentTop = glassTop + contentPadding
        val contentRight = glassRight - contentPadding
        val imageCornerRadius = baseSize * 0.035f
        val coverSize = min(glassWidth * 0.18f, glassHeight * 0.15f)
        val topRowGap = baseSize * 0.035f
        val titlePaint = TextPaint().apply { color = mainTextColor; textSize = baseSize * 0.038f; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true; letterSpacing = -0.02f }
        val artistPaint = TextPaint().apply { color = secondaryTextColor; textSize = baseSize * 0.028f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL); isAntiAlias = true }
        val showingArtwork = options.showArtwork && coverArtBitmap != null
        val textMaxWidth = if (showingArtwork) (contentRight - contentLeft - coverSize - topRowGap).toInt() else (contentRight - contentLeft).toInt()
        val headerAlignment = if (showingArtwork) Layout.Alignment.ALIGN_NORMAL else Layout.Alignment.ALIGN_CENTER
        val titleLayout = StaticLayout.Builder.obtain(songTitle, 0, songTitle.length, titlePaint, textMaxWidth).setAlignment(headerAlignment).setMaxLines(1).build()
        val artistLayout = StaticLayout.Builder.obtain(artistName, 0, artistName.length, artistPaint, textMaxWidth).setAlignment(headerAlignment).setMaxLines(1).build()
        val topBlockHeight = if (showingArtwork) coverSize else (titleLayout.height + artistLayout.height + 6f)
        val imageCenter = contentTop + topBlockHeight / 2f
        val textBlockHeight = titleLayout.height + artistLayout.height + 6f
        val textBlockY = imageCenter - textBlockHeight / 2f
        val headerBottom = if (showingArtwork) contentTop + coverSize else textBlockY + textBlockHeight
        val logoBlockHeight = (baseSize * 0.08f).toInt()
        val maxWidth = (glassWidth * 0.85f).toInt()
        val top = headerBottom + baseSize * 0.045f
        val bottom = glassBottom - (logoBlockHeight + contentPadding)
        val availableHeight = bottom - top
        var textSize = baseSize * 0.055f
        while (textSize * 1.45f * maxVisibleLines > availableHeight && textSize > 22f) { textSize -= 1f }
        val lineHeight = textSize * 1.45f
        val lp = TextPaint().apply { color = mainTextColor; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true; letterSpacing = -0.01f; textSize = textSize }
        val wp = TextPaint().apply { color = mainTextColor; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true; letterSpacing = -0.01f; textSize = textSize }
        val entries: List<LyricsEntry> = if (timedLyrics.isNotEmpty()) {
            timedLyrics
        } else {
            val lines = fallbackLyricsText.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
            if (lines.isEmpty()) {
                emptyList()
            } else {
                val step = totalDurationMs.coerceAtLeast(1_000L) / lines.size
                lines.mapIndexed { index, text -> LyricsEntry(time = startPositionMs + index * step, text = text) }
            }
        }
        val lineLayouts = entries.filter { it.text.isNotBlank() }.mapIndexed { index, entry ->
            val nextTime = entries.drop(index + 1).firstOrNull()?.time
            val durationMs = when { nextTime != null && nextTime > entry.time -> nextTime - entry.time; entry.durationMs > 0L -> entry.durationMs; else -> 3_000L }
            val layout = StaticLayout.Builder.obtain(entry.text, 0, entry.text.length, lp, maxWidth).setAlignment(Layout.Alignment.ALIGN_CENTER).setMaxLines(1).setIncludePad(false).setEllipsize(TextUtils.TruncateAt.END).build()
            // Temporarily use lp for word measurement; we will rebuild words with wp after
            val prevWp = wordPaint
            // Need wp for build; hack: use lp then wp – both same size
            val wordLayouts = run {
                val words = entry.words?.filter { it.text.isNotBlank() } ?: return@run null
                if (words.isEmpty()) return@run null
                val built = mutableListOf<WordLayout>(); var x = 0f
                for (raw in words) {
                    val text = raw.text.trim(); if (text.isEmpty()) continue
                    val gap = if (built.isNotEmpty()) wp.measureText(" ") else 0f
                    val startX = x + gap
                    built += WordLayout(text = text, startMs = (raw.startTime * 1000).toLong(), endMs = (raw.endTime * 1000).toLong(), x = startX, width = wp.measureText(text))
                    x = startX + built.last().width
                }
                if (built.isEmpty() || x > layout.width) null else built to x
            }
            LyricsLineLayout(timeMs = entry.time, durationMs = durationMs, layout = layout, words = wordLayouts?.first, totalWordWidth = wordLayouts?.second ?: layout.width.toFloat())
        }
        val left = glassLeft + (glassWidth - maxWidth) / 2f
        return LyricsMetrics(maxWidth, left, top, availableHeight, lineHeight, lp, wp, lineLayouts)
    }

    private fun buildWords(entry: LyricsEntry, layoutWidth: Int): Pair<List<WordLayout>, Float>? {
        val words = entry.words?.filter { it.text.isNotBlank() } ?: return null
        if (words.isEmpty()) return null
        val built = mutableListOf<WordLayout>()
        var x = 0f
        for (raw in words) {
            val text = raw.text.trim()
            if (text.isEmpty()) continue
            val gap = if (built.isNotEmpty()) wordPaint.measureText(" ") else 0f
            val startX = x + gap
            built +=
                WordLayout(
                    text = text,
                    startMs = (raw.startTime * 1000).toLong(),
                    endMs = (raw.endTime * 1000).toLong(),
                    x = startX,
                    width = wordPaint.measureText(text),
                )
            x = startX + built.last().width
        }
        if (built.isEmpty()) return null
        val total = x
        if (total > layoutWidth) return null
        return built to total
    }

    fun render(canvas: Canvas, songTimeMs: Long) {
        if (useVideoBackground && videoRetriever != null) {
            val retriever = videoRetriever
            val durUs = videoDurationUs
            var frameBmp: Bitmap? = null
            var isCached = false
            if (retriever != null && durUs > 0) {
                val elapsedUs = ((songTimeMs - startPositionMs).coerceAtLeast(0L) * 1000L) % durUs
                val bucketUs = (elapsedUs / 80_000L) * 80_000L // ~12.5fps bucket, cheap cache hit for loop
                synchronized(videoCacheLock) {
                    frameBmp = videoFrameCache[bucketUs]
                    if (frameBmp != null) isCached = true
                }
                if (frameBmp == null) {
                    frameBmp = runCatching { retriever.getFrameAtTime(bucketUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) }.getOrNull()
                        ?: runCatching { retriever.getFrameAtTime(elapsedUs, MediaMetadataRetriever.OPTION_CLOSEST) }.getOrNull()
                    if (frameBmp != null) {
                        synchronized(videoCacheLock) {
                            // store a copy for cache (avoid recycling the one we draw)
                            val toCache = frameBmp
                            if (toCache != null && videoFrameCache.size < 32) {
                                // copy for cache if we will recycle original later – keep original cached, don't recycle
                                videoFrameCache[bucketUs] = toCache
                                isCached = true // we will not recycle this instance
                                // use cached instance directly
                            }
                        }
                    }
                } else {
                    // cache hit – reuse without retriever
                }
            }
            if (frameBmp != null) {
                val srcW = frameBmp.width.toFloat()
                val srcH = frameBmp.height.toFloat()
                val dstW = canvasWidth.toFloat()
                val dstH = canvasHeight.toFloat()
                val baseScale = max(dstW / srcW, dstH / srcH)
                val scaledW = srcW * baseScale * videoScale.coerceIn(0.5f, 3f)
                val scaledH = srcH * baseScale * videoScale.coerceIn(0.5f, 3f)
                val left = (dstW - scaledW) / 2f + videoPanX * dstW
                val top = (dstH - scaledH) / 2f + videoPanY * dstH
                val dstRect = RectF(left, top, left + scaledW, top + scaledH)
                canvas.drawBitmap(frameBmp, null, dstRect, Paint(Paint.FILTER_BITMAP_FLAG))
                // subtle scrim so white text pops
                canvas.drawRect(RectF(0f, 0f, dstW, dstH), Paint().apply { color = Color.argb(90, 0, 0, 0) })
                // only recycle if not from cache (cached bitmaps live until release)
                if (!isCached && frameBmp != overlayBitmap && frameBmp != backgroundBitmap) runCatching { frameBmp.recycle() }
            } else {
                canvas.drawColor(0xFF121212.toInt())
            }
            overlayBitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        } else {
            backgroundBitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) } ?: canvas.drawColor(0xFF121212.toInt())
        }

        if (lineLayouts.isEmpty()) return

        val currentIndex = lineLayouts.indexOfLast { it.timeMs <= songTimeMs }
        val start = (currentIndex - (maxVisibleLines - 1) / 2).coerceAtLeast(0)
        val end = (start + maxVisibleLines).coerceAtMost(lineLayouts.size)
        val visibleCount = end - start

        val blockTop = lyricsTop + (availableLyricsHeight - visibleCount * lyricsLineHeight) / 2f

        for (i in start until end) {
            val line = lineLayouts[i]
            val y = blockTop + (i - start) * lyricsLineHeight
            if (i == currentIndex) {
                // video and lyrics are independent – no per-word karaoke fill when canvas video is background (much faster)
                if (useVideoBackground) drawSolidLine(canvas, line, y) else drawKaraokeLine(canvas, line, y, songTimeMs)
            } else {
                drawDimLine(canvas, line, y)
            }
        }
    }

    fun release() {
        Timber.tag("LyricsVideo").d("Renderer release: cacheSize=${videoFrameCache.size} useVideo=$useVideoBackground")
        GlobalLog.append(Log.DEBUG, "LyricsVideo", "Renderer release cache=${videoFrameCache.size} useVideo=$useVideoBackground")
        runCatching { videoRetriever?.release() }
        videoRetriever = null
        synchronized(videoCacheLock) {
            videoFrameCache.values.forEach { bmp -> runCatching { if (!bmp.isRecycled) bmp.recycle() } }
            videoFrameCache.clear()
        }
    }

    private fun drawDimLine(canvas: Canvas, line: LyricsLineLayout, y: Float) {
        line.layout.paint.color = dimLineColor
        canvas.withTranslation(lyricsLeft, y) {
            line.layout.draw(this)
        }
    }

    private fun drawSolidLine(canvas: Canvas, line: LyricsLineLayout, y: Float) {
        line.layout.paint.color = mainTextColor
        canvas.withTranslation(lyricsLeft, y) {
            line.layout.draw(this)
        }
    }

    private fun drawKaraokeLine(canvas: Canvas, line: LyricsLineLayout, y: Float, songTimeMs: Long) {
        val words = line.words
        if (words == null || line.totalWordWidth > lyricsMaxWidth) {
            drawLineFill(canvas, line, y, songTimeMs)
            return
        }

        val fm = wordPaint.fontMetrics
        val baselineY = y - fm.ascent
        val centerX = lyricsLeft + (lyricsMaxWidth - line.totalWordWidth) / 2f

        wordPaint.color = dimWordColor
        for (word in words) {
            canvas.drawText(word.text, centerX + word.x, baselineY, wordPaint)
        }

        wordPaint.color = mainTextColor
        for (word in words) {
            val fraction = fillFraction(word.startMs, word.endMs, songTimeMs)
            if (fraction > 0f) {
                val fillWidth = word.width * fraction
                if (fillWidth > 1f) {
                    canvas.save()
                    canvas.clipRect(
                        centerX + word.x,
                        y,
                        centerX + word.x + fillWidth,
                        y + lyricsLineHeight,
                    )
                    canvas.drawText(word.text, centerX + word.x, baselineY, wordPaint)
                    canvas.restore()
                }
            }
        }
    }

    private fun drawLineFill(canvas: Canvas, line: LyricsLineLayout, y: Float, songTimeMs: Long) {
        line.layout.paint.color = dimLineColor
        canvas.withTranslation(lyricsLeft, y) {
            line.layout.draw(this)
        }

        val durationMs = line.durationMs
        val fraction = if (durationMs > 0L) ((songTimeMs - line.timeMs).toFloat() / durationMs).coerceIn(0f, 1f) else 1f
        if (fraction <= 0f) return

        val lineWidth = line.layout.width.toFloat()
        val lineX = lyricsLeft + (lyricsMaxWidth - lineWidth) / 2f
        val fillWidth = lineWidth * fraction
        if (fillWidth <= 1f) return

        canvas.save()
        canvas.clipRect(lineX, y, lineX + fillWidth, y + lyricsLineHeight)
        line.layout.paint.color = mainTextColor
        canvas.withTranslation(lyricsLeft, y) {
            line.layout.draw(this)
        }
        canvas.restore()
    }

    private fun fillFraction(startMs: Long, endMs: Long, songTimeMs: Long): Float {
        if (endMs <= startMs) return if (songTimeMs >= startMs) 1f else 0f
        return ((songTimeMs - startMs).toFloat() / (endMs - startMs)).coerceIn(0f, 1f)
    }

    private fun colorToArgb(color: androidx.compose.ui.graphics.Color): Int =
        Color.argb(
            (color.alpha * 255).toInt(),
            (color.red * 255).toInt(),
            (color.green * 255).toInt(),
            (color.blue * 255).toInt(),
        )

    private fun withAlpha(color: Int, alpha: Float): Int = (color and 0x00FFFFFF) or ((alpha.coerceIn(0f, 1f) * 255).toInt() shl 24)
}
