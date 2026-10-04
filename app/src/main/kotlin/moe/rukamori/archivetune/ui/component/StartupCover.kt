package moe.rukamori.archivetune.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.screens.onboarding.OnboardingAmbientBackground

/**
 * Full-screen startup veil: brand icon over the shared onboarding glow plus a
 * wave, shown until the service binds, then slides up into the app.
 *
 * Deliberately static — cold start saturates the main thread, so every
 * animated part stuttered in turn (drift, appear, fade, sweep). The one motion
 * kept is the exit slide: it runs post-bind when the storm has passed, and a
 * lone GPU transform is the cheapest animation there is.
 */
@Composable
fun StartupCover(
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier.fillMaxSize(),
        exit = slideOutVertically(targetOffsetY = { -it }, animationSpec = tween(450)),
    ) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            // Same living glow as onboarding (theme hues, real blur) — no second
            // implementation. Static: composes once, GPU-cached, zero per-frame cost.
            OnboardingAmbientBackground(showMesh = false, animate = false)
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val waveY = h * 0.74f
                val wave = Path().apply {
                    moveTo(0f, waveY)
                    cubicTo(w * 0.25f, waveY - h * 0.06f, w * 0.35f, waveY + h * 0.06f, w * 0.55f, waveY)
                    cubicTo(w * 0.75f, waveY - h * 0.06f, w * 0.85f, waveY + h * 0.05f, w, waveY - h * 0.02f)
                    lineTo(w, h)
                    lineTo(0f, h)
                    close()
                }
                drawPath(wave, Color.White.copy(alpha = 0.07f))
                drawPath(wave, Color.White.copy(alpha = 0.16f), style = Stroke(width = 3.dp.toPx()))
            }
            Image(
                painter = painterResource(R.drawable.app_icon_small),
                contentDescription = null,
                modifier = Modifier.size(112.dp),
            )
        }
    }
}
