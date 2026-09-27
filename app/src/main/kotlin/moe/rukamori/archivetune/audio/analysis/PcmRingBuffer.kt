/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.audio.analysis

/**
 * Thread-safe circular buffer of mono PCM shorts fed from the ExoPlayer tee.
 * Writers: audio-pipeline thread (via Handler post). Readers: analysis coroutines.
 *
 * Capacity covers one full analysis window plus headroom so a trailing 3s
 * snapshot is available the moment a checkpoint fires.
 */
class PcmRingBuffer(
    capacityMs: Long = CheckpointMath.WINDOW_MS + HEADROOM_MS,
    private val maxSampleRate: Int = 48_000,
) {
    companion object {
        private const val HEADROOM_MS = 1_500L
        private const val MIN_SNAPSHOT_SAMPLES = 8_000
    }

    private val capacity = ((capacityMs * maxSampleRate) / 1_000L).toInt().coerceAtLeast(16_000)
    private val buf = ShortArray(capacity)
    private var writePos = 0
    private var filled = 0
    private var sampleRate = 44_100
    private val lock = Any()

    fun write(hop: ShortArray, sr: Int) {
        if (hop.isEmpty()) return
        synchronized(lock) {
            sampleRate = sr.coerceIn(8_000, maxSampleRate)
            for (s in hop) {
                buf[writePos] = s
                writePos = (writePos + 1) % capacity
                if (filled < capacity) filled++
            }
        }
    }

    /** Last [windowMs] of buffered audio, oldest-first. Null when underfilled. */
    fun snapshot(windowMs: Long = CheckpointMath.WINDOW_MS): Snapshot? {
        synchronized(lock) {
            val want = ((windowMs * sampleRate) / 1_000L).toInt()
            if (filled < MIN_SNAPSHOT_SAMPLES) return null
            val n = minOf(want, filled)
            val out = ShortArray(n)
            var readPos = (writePos - n + capacity * 2) % capacity
            for (i in 0 until n) {
                out[i] = buf[readPos]
                readPos = (readPos + 1) % capacity
            }
            return Snapshot(samples = out, sampleRate = sampleRate)
        }
    }

    fun reset() {
        synchronized(lock) {
            writePos = 0
            filled = 0
        }
    }

    data class Snapshot(val samples: ShortArray, val sampleRate: Int)
}
