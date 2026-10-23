/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.utils

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Max width of the centered content pane on medium+/expanded (non-TV) screens.
 * The shell (rail, floating bars, bottom sheet player) stays full-bleed — never wrap it here.
 */
val MaxContentPaneWidth = 840.dp

/**
 * Centers and caps scrollable content so phone layouts read as intentional on wide screens.
 * On compact (rail-less) screens and on TV this is a pure passthrough — no behavior change.
 */
@Composable
fun AdaptiveContentPane(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (enabled) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(
                modifier =
                    Modifier
                        .widthIn(max = MaxContentPaneWidth)
                        .fillMaxSize(),
            ) {
                content()
            }
        }
    } else {
        Box(modifier = modifier.fillMaxSize()) { content() }
    }
}
