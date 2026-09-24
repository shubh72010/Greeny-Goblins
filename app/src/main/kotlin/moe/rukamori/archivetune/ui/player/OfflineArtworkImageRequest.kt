/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import moe.rukamori.archivetune.utils.budgets
import moe.rukamori.archivetune.utils.deviceTier

@Composable
internal fun rememberOfflineArtworkImageRequest(
    imageUrl: String?,
    maxPx: Int? = null,
): ImageRequest? {
    val context = LocalContext.current
    // ponytail: fullscreen decode cap from RAM tier (720/864/1080) unless overridden.
    val effectiveMaxPx = maxPx ?: remember { context.deviceTier().budgets().fullscreenArtCapPx }
    return remember(context, imageUrl, effectiveMaxPx) {
        imageUrl
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?.let { url ->
                ImageRequest
                    .Builder(context)
                    .data(url)
                    // ponytail: cap full-screen decode, bg+art would otherwise decode raw.
                    .size(effectiveMaxPx)
                    .memoryCacheKey(url)
                    .diskCacheKey(url)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .networkCachePolicy(CachePolicy.ENABLED)
                    .build()
            }
    }
}
