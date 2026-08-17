/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.component

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.lyrics.LyricsEntry
import kotlin.math.roundToInt

enum class LyricsShareMode(
    @StringRes val labelRes: Int,
) {
    Image(
        labelRes = R.string.lyrics_share_mode_image,
    ),
    Video(
        labelRes = R.string.lyrics_share_mode_video,
    ),
}

enum class LyricsShareVideoDuration(
    @StringRes val labelRes: Int,
    val durationMs: Long,
) {
    Short(
        labelRes = R.string.lyrics_share_video_duration_short,
        durationMs = 10_000L,
    ),
    Medium(
        labelRes = R.string.lyrics_share_video_duration_medium,
        durationMs = 25_000L,
    ),
    Long(
        labelRes = R.string.lyrics_share_video_duration_long,
        durationMs = 35_000L,
    ),
    Full(
        labelRes = R.string.lyrics_share_video_duration_full,
        durationMs = 60_000L,
    ),
    ;
}

enum class LyricsShareAspectRatio(
    @StringRes val labelRes: Int,
    val exportWidth: Int,
    val exportHeight: Int,
) {
    Square(
        labelRes = R.string.lyrics_share_layout_square,
        exportWidth = 1080,
        exportHeight = 1080,
    ),
    Portrait(
        labelRes = R.string.lyrics_share_layout_portrait,
        exportWidth = 1080,
        exportHeight = 1350,
    ),
    Story(
        labelRes = R.string.lyrics_share_layout_story,
        exportWidth = 1080,
        exportHeight = 1920,
    ),
    ;

    val previewAspectRatio: Float
        get() = exportWidth.toFloat() / exportHeight.toFloat()
}

@Immutable
data class LyricsShareImageOptions(
    val aspectRatio: LyricsShareAspectRatio = LyricsShareAspectRatio.Square,
    val blurRadius: Float = 24f,
    val dimAmount: Float = 1f,
    val showArtwork: Boolean = true,
    val videoDuration: LyricsShareVideoDuration = LyricsShareVideoDuration.Medium,
) {
    val sanitizedBlurRadius: Float
        get() = blurRadius.coerceIn(0f, 48f)

    val sanitizedDimAmount: Float
        get() = dimAmount.coerceIn(0.6f, 1.6f)

    val previewBlurRadius: Int
        get() = sanitizedBlurRadius.roundToInt().coerceIn(0, 48)
}

@Immutable
data class LyricsSharePayload(
    val lyricsText: String,
    val songTitle: String,
    val artists: String,
    val timedLyrics: List<LyricsEntry>? = null,
)
