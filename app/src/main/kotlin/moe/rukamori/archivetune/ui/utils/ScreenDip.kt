/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.utils

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.PI
import kotlin.math.sin

/** Peak shrink, as a fraction. Small enough that nothing visibly jumps. */
private const val DipScale = 0.015f

/** Peak dim, as a fraction. Blends the screen into the backdrop for depth. */
private const val DipAlpha = 0.15f

/**
 * Screen-switch depth dip, after curbox's `BlurFadeAnimator`: whenever
 * [triggerKey] changes (the back-stack entry id) the screen pulls back and dims,
 * then settles, on top of the NavHost's own push/pop.
 *
 * Deliberately no `RenderEffect` blur: a real-time blur has to re-rasterize and
 * re-blur the whole layer every frame while both screens are sliding, which is
 * exactly the per-frame cost that made navigation stutter.
 *
 * One eased run over the whole dip, shaped by a sine, instead of two hard 125ms
 * halves: `FastOutSlowInEasing` has zero velocity at both ends and the sine's own
 * zero at the peak, so the dip leaves, turns and returns without any velocity
 * break — the halves used to snap at the turn, which read as a flick.
 *
 * The animated value is read inside [graphicsLayer], so the dip updates layer
 * properties in the draw phase and never recomposes the NavHost.
 */
@Composable
fun rememberScreenDip(
    triggerKey: Any?,
    enabled: Boolean,
): Modifier {
    val progress = remember { Animatable(0f) }
    val firstRun = remember { mutableStateOf(true) }
    LaunchedEffect(triggerKey, enabled) {
        // No dip on first composition: there is no outgoing screen yet.
        if (firstRun.value) {
            firstRun.value = false
            return@LaunchedEffect
        }
        if (!enabled) {
            progress.snapTo(0f)
            return@LaunchedEffect
        }
        progress.animateTo(1f, tween(Motion.PushMillis, easing = FastOutSlowInEasing))
        // sin(1 * PI) is 0 already, so the reset is invisible and the next dip can run.
        progress.snapTo(0f)
    }
    return Modifier.graphicsLayer {
        val depth = sin(progress.value * PI).toFloat()
        scaleX = 1f - DipScale * depth
        scaleY = 1f - DipScale * depth
        alpha = 1f - DipAlpha * depth
    }
}
