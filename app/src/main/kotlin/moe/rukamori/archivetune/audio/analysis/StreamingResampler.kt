/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.audio.analysis

/**
 * Stateful linear-interp resampler to fixed 16 kHz mono.
 * Same math as [DspAnalyzer.resampleLinearMono], but consumes the tee's
 * arbitrarily-chunked hops while keeping output positions stream-continuous:
 * feeding hop-by-hop (+[flush]) yields the same samples as one-shot resampling.
 */
class StreamingResampler {
    private var fromRate = 0
    private val pending = ArrayDeque<Short>()
    private var readPos = 0.0
    private var totalIn = 0L
    private var emitted = 0

    fun feed(hop: ShortArray, fromRate: Int): ShortArray {
        if (hop.isEmpty()) return ShortArray(0)
        if (fromRate != this.fromRate) {
            reset()
            this.fromRate = fromRate
        }
        if (fromRate == DspAnalyzer.TARGET_RATE) return hop.copyOf()
        totalIn += hop.size
        for (s in hop) pending.addLast(s)
        val ratio = fromRate.toDouble() / DspAnalyzer.TARGET_RATE
        val out = mutableListOf<Short>()
        while (true) {
            val idx = readPos.toInt()
            if (idx + 1 >= pending.size) break
            val frac = (readPos - idx).toFloat()
            val a = pending[idx].toFloat()
            val b = pending[idx + 1].toFloat()
            out.add((a + (b - a) * frac).toInt().coerceIn(-32768, 32767).toShort())
            readPos += ratio
            emitted++
        }
        // Drop consumed input but always retain ≥1 sample: output positions stay
        // continuous across feeds, and the tail is emitted by flush().
        val drop = readPos.toInt().coerceIn(0, (pending.size - 1).coerceAtLeast(0))
        repeat(drop) { pending.removeFirst() }
        readPos -= drop
        return out.toShortArray()
    }

    /** Emit remaining positions with clamped edge, mirroring one-shot tail behavior. */
    fun flush(): ShortArray {
        if (pending.isEmpty() || fromRate == DspAnalyzer.TARGET_RATE) {
            val tail = pending.toShortArray()
            reset()
            return tail
        }
        val ratio = fromRate.toDouble() / DspAnalyzer.TARGET_RATE
        // Float accumulation in readPos can overshoot the exact end position by an
        // ulp; cap total emissions at what one-shot would produce for this input.
        val maxOut = (totalIn / ratio).toInt()
        val out = mutableListOf<Short>()
        while (readPos.toInt() < pending.size && emitted < maxOut) {
            val idx = readPos.toInt()
            val nxt = (idx + 1).coerceIn(0, pending.size - 1)
            val frac = (readPos - idx).toFloat()
            val a = pending[idx].toFloat()
            val b = pending[nxt].toFloat()
            out.add((a + (b - a) * frac).toInt().coerceIn(-32768, 32767).toShort())
            readPos += ratio
            emitted++
        }
        reset()
        return out.toShortArray()
    }

    fun reset() {
        pending.clear()
        readPos = 0.0
        totalIn = 0L
        emitted = 0
    }
}
