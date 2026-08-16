/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player

import androidx.compose.runtime.Composable
import moe.rukamori.archivetune.R

enum class CanvasSource {
    AUTO,
    APPLE,
    YOUTUBE,
}

@Composable
fun CanvasSource.label(): String =
    when (this) {
        CanvasSource.AUTO -> androidx.compose.ui.res.stringResource(R.string.canvas_source_auto)
        CanvasSource.APPLE -> androidx.compose.ui.res.stringResource(R.string.canvas_source_apple)
        CanvasSource.YOUTUBE -> androidx.compose.ui.res.stringResource(R.string.canvas_source_youtube)
    }