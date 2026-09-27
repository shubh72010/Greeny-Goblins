/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.audio.analysis

/**
 * Bounded accumulation of 16 kHz mono context for the single long-context
 * tempo/key pass at the 80% checkpoint.
 *
 * Resource guard, not a requirement: holds at most [maxSeconds] (120s default),
 * then drops everything beyond the cap. Short tracks simply accumulate less —
 * `min(songDuration, cap)`. Transient: cleared per track and after finalization.
 * Max footprint at defaults: 120s × 16kHz × 2B ≈ 3.8 MB.
 */
class LongContextBuffer(
    private val maxSeconds: Int = MAX_SECONDS,
    private val rate: Int = DspAnalyzer.TARGET_RATE,
) {
    companion object {
        const val MAX_SECONDS = 120
        /** Below this the long-context pass abstains and window means stand. */
        const val MIN_CONTEXT_SEC = 10
    }

    private val cap = maxSeconds * rate
    private val buf = ShortArray(cap)
    private val resampler = StreamingResampler()
    var size = 0
        private set

    val secondsBuffered: Float
        get() = size.toFloat() / rate

    fun feed(hop: ShortArray, fromRate: Int) {
        if (size >= cap || hop.isEmpty()) return
        val conv = resampler.feed(hop, fromRate)
        val n = minOf(conv.size, cap - size)
        conv.copyInto(buf, size, 0, n)
        size += n
    }

    fun snapshot(): ShortArray = buf.copyOf(size)

    fun reset() {
        size = 0
        resampler.reset()
    }
}
