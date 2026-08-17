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
import android.graphics.RectF
import android.graphics.Typeface
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
    timedLyrics: List<LyricsEntry>,
    fallbackLyricsText: String,
    private val width: Int,
    private val height: Int,
    private val glassStyle: LyricsGlassStyle,
    private val options: LyricsShareImageOptions,
    startPositionMs: Long,
    totalDurationMs: Long,
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

    private val backgroundBitmap: Bitmap
    private val lineLayouts: List<LyricsLineLayout>

    private val lyricsMaxWidth: Int
    private val lyricsLeft: Float
    private val lyricsTop: Float
    private val availableLyricsHeight: Float
    private val lyricsLineHeight: Float
    private val lyricsPaint: TextPaint
    private val wordPaint: TextPaint

    private val maxVisibleLines = 5

    init {
        val bgColor = 0xFF121212.toInt()
        val bitmap = createBitmap(canvasWidth, canvasHeight)
        val canvas = Canvas(bitmap)

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

        val glassMargin = baseSize * 0.045f
        val glassLeft = glassMargin
        val glassTop = glassMargin
        val glassRight = canvasWidth - glassMargin
        val glassBottom = canvasHeight - glassMargin
        val glassWidth = glassRight - glassLeft
        val glassHeight = glassBottom - glassTop
        val glassCornerRadius = baseSize * 0.05f

        val glassRect = RectF(glassLeft, glassTop, glassRight, glassBottom)
        val glassPath =
            Path().apply {
                addRoundRect(glassRect, glassCornerRadius, glassCornerRadius, Path.Direction.CW)
            }

        if (fittedArt != null) {
            val frostedCrop = ComposeToImage.blurBitmap(fittedArt, (options.sanitizedBlurRadius + 10f).coerceIn(8f, 48f))
            canvas.withClip(glassPath) {
                drawBitmap(frostedCrop, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
            }
        }

        canvas.drawRoundRect(
            glassRect,
            glassCornerRadius,
            glassCornerRadius,
            Paint().apply {
                color =
                    glassStyle.surfaceTint.let {
                        Color.argb(
                            (glassStyle.surfaceAlpha * 255).toInt(),
                            (it.red * 255).toInt(),
                            (it.green * 255).toInt(),
                            (it.blue * 255).toInt(),
                        )
                    }
                isAntiAlias = true
            },
        )

        canvas.drawRoundRect(
            glassRect,
            glassCornerRadius,
            glassCornerRadius,
            Paint().apply {
                color =
                    glassStyle.overlayColor.let {
                        Color.argb(
                            (glassStyle.overlayAlpha * 255).toInt(),
                            (it.red * 255).toInt(),
                            (it.green * 255).toInt(),
                            (it.blue * 255).toInt(),
                        )
                    }
                isAntiAlias = true
            },
        )

        canvas.drawRoundRect(
            glassRect,
            glassCornerRadius,
            glassCornerRadius,
            Paint().apply {
                style = Paint.Style.STROKE
                strokeWidth = 1.5f
                color = Color.argb(25, 255, 255, 255)
                isAntiAlias = true
            },
        )

        val contentPadding = min(glassWidth, glassHeight) * 0.08f
        val contentLeft = glassLeft + contentPadding
        val contentTop = glassTop + contentPadding
        val contentRight = glassRight - contentPadding

        val imageCornerRadius = baseSize * 0.035f
        val coverSize = min(glassWidth * 0.18f, glassHeight * 0.15f)
        val topRowGap = baseSize * 0.035f

        val titlePaint =
            TextPaint().apply {
                color = mainTextColor
                textSize = baseSize * 0.038f
                typeface = Typeface.DEFAULT_BOLD
                isAntiAlias = true
                letterSpacing = -0.02f
            }
        val artistPaint =
            TextPaint().apply {
                color = secondaryTextColor
                textSize = baseSize * 0.028f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                isAntiAlias = true
            }

        val showingArtwork = options.showArtwork && coverArtBitmap != null
        if (showingArtwork) {
            val rect = RectF(contentLeft, contentTop, contentLeft + coverSize, contentTop + coverSize)
            val path =
                Path().apply {
                    addRoundRect(rect, imageCornerRadius, imageCornerRadius, Path.Direction.CW)
                }
            canvas.withClip(path) {
                drawBitmap(coverArtBitmap ?: return@withClip, null, rect, Paint(Paint.FILTER_BITMAP_FLAG))
            }
            canvas.drawRoundRect(
                rect,
                imageCornerRadius,
                imageCornerRadius,
                Paint().apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 1f
                    color = Color.argb(38, 255, 255, 255)
                    isAntiAlias = true
                },
            )
        }

        val textMaxWidth =
            if (showingArtwork) {
                (contentRight - contentLeft - coverSize - topRowGap).toInt()
            } else {
                (contentRight - contentLeft).toInt()
            }
        val textStartX = if (showingArtwork) contentLeft + coverSize + topRowGap else contentLeft
        val headerAlignment = if (showingArtwork) Layout.Alignment.ALIGN_NORMAL else Layout.Alignment.ALIGN_CENTER

        val titleLayout =
            StaticLayout
                .Builder
                .obtain(songTitle, 0, songTitle.length, titlePaint, textMaxWidth)
                .setAlignment(headerAlignment)
                .setMaxLines(1)
                .build()
        val artistLayout =
            StaticLayout
                .Builder
                .obtain(artistName, 0, artistName.length, artistPaint, textMaxWidth)
                .setAlignment(headerAlignment)
                .setMaxLines(1)
                .build()

        val topBlockHeight = if (showingArtwork) coverSize else (titleLayout.height + artistLayout.height + 6f)
        val imageCenter = contentTop + topBlockHeight / 2f
        val textBlockHeight = titleLayout.height + artistLayout.height + 6f
        val textBlockY = imageCenter - textBlockHeight / 2f

        canvas.withTranslation(textStartX, textBlockY) {
            titleLayout.draw(this)
            translate(0f, titleLayout.height.toFloat() + 6f)
            artistLayout.draw(this)
        }

        val logoBlockHeight = (baseSize * 0.08f).toInt()
        val headerBottom = if (showingArtwork) contentTop + coverSize else textBlockY + textBlockHeight

        lyricsMaxWidth = (glassWidth * 0.85f).toInt()
        lyricsTop = headerBottom + baseSize * 0.045f
        val lyricsBottom = glassBottom - (logoBlockHeight + contentPadding)
        availableLyricsHeight = lyricsBottom - lyricsTop

        var textSize = baseSize * 0.055f
        while (textSize * 1.45f * maxVisibleLines > availableLyricsHeight && textSize > 22f) {
            textSize -= 1f
        }

        lyricsLineHeight = textSize * 1.45f
        lyricsPaint =
            TextPaint().apply {
                color = mainTextColor
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
                letterSpacing = -0.01f
                textSize = textSize
            }
        wordPaint =
            TextPaint().apply {
                color = mainTextColor
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
                letterSpacing = -0.01f
                textSize = textSize
            }

        val entries =
            if (timedLyrics.isNotEmpty()) {
                timedLyrics
            } else {
                fallbackLyricsText
                    .lineSequence()
                    .map(String::trim)
                    .filter { it.isNotEmpty() }
                    .toList()
                    .let { lines ->
                        if (lines.isEmpty()) {
                            emptyList()
                        } else {
                            val step = (totalDurationMs.coerceAtLeast(1_000L)) / lines.size
                            lines.mapIndexed { index, text ->
                                LyricsEntry(time = startPositionMs + index * step, text = text)
                            }
                        }
                    }
            }

        lineLayouts =
            entries
                .filter { it.text.isNotBlank() }
                .mapIndexed { index, entry ->
                    val nextTime = entries.drop(index + 1).firstOrNull()?.time
                    val durationMs =
                        when {
                            nextTime != null && nextTime > entry.time -> nextTime - entry.time
                            entry.durationMs > 0L -> entry.durationMs
                            else -> 3_000L
                        }
                    val layout =
                        StaticLayout
                            .Builder
                            .obtain(entry.text, 0, entry.text.length, lyricsPaint, lyricsMaxWidth)
                            .setAlignment(Layout.Alignment.ALIGN_CENTER)
                            .setMaxLines(1)
                            .setIncludePad(false)
                            .setEllipsize(TextUtils.TruncateAt.END)
                            .build()
                    val wordLayouts = buildWords(entry, layout.width)
                    LyricsLineLayout(
                        timeMs = entry.time,
                        durationMs = durationMs,
                        layout = layout,
                        words = wordLayouts?.first,
                        totalWordWidth = wordLayouts?.second ?: layout.width.toFloat(),
                    )
                }

        lyricsLeft = glassLeft + (glassWidth - lyricsMaxWidth) / 2f

        ComposeToImage.AppLogo(
            context = context,
            canvas = canvas,
            canvasWidth = canvasWidth,
            canvasHeight = canvasHeight,
            padding = contentLeft,
            bottomPadding = glassBottom - contentPadding,
            circleColor = secondaryTextColor,
            logoTint = if (glassStyle.isDark) 0xDD000000.toInt() else 0xE6FFFFFF.toInt(),
            textColor = secondaryTextColor,
        )

        backgroundBitmap = bitmap
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
        canvas.drawBitmap(backgroundBitmap, 0f, 0f, null)

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
                drawKaraokeLine(canvas, line, y, songTimeMs)
            } else {
                drawDimLine(canvas, line, y)
            }
        }
    }

    private fun drawDimLine(canvas: Canvas, line: LyricsLineLayout, y: Float) {
        line.layout.paint.color = dimLineColor
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
