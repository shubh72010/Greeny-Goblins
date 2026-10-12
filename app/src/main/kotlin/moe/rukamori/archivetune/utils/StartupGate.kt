/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import android.content.Context
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/**
 * Cold-start staging: Light (Room/cache reads, first frame, playback restore)
 * runs immediately; Medium (online discovery/Home refresh) waits for [awaitMedium].
 *
 * The settled flag is flipped after first composition — a real "UI is up" signal,
 * not a fixed delay. The tier breather on top lets GC catch up before the first
 * InnerTube parse lands on small-heap devices.
 */
object StartupGate {
    private val settled = MutableStateFlow(false)

    fun markSettled() {
        settled.value = true
    }

    suspend fun awaitMedium(context: Context) {
        settled.first { it }
        if (context.deviceTier() == DeviceTier.CONSTRAINED) delay(500)
    }
}
