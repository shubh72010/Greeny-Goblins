/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.audio.analysis

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

/**
 * Sequential by default; bounded parallel only when falling behind.
 * Normal case (no backlog): everything runs on a single lane — low CPU/thermals.
 * Short song / slow device: an already-running analysis diverts the next sample
 * to a bounded 2-lane pool instead of queueing behind it.
 *
 * ML-aware latency tuning is deliberately out of scope (Phase 6, post-Laya).
 */
class AnalysisScheduler(private val scope: CoroutineScope) {
    private val sequential = Dispatchers.Default.limitedParallelism(1)
    private val overflow = Dispatchers.Default.limitedParallelism(2)
    private val inFlight = AtomicInteger(0)

    fun schedule(block: suspend () -> Unit) {
        val dispatcher = if (inFlight.get() > 0) overflow else sequential
        scope.launch(dispatcher) {
            inFlight.incrementAndGet()
            try {
                block()
            } finally {
                inFlight.decrementAndGet()
            }
        }
    }
}
