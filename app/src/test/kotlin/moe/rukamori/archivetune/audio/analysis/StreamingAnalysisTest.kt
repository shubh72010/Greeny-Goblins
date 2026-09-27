/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.audio.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class StreamingAnalysisTest {
    private fun sine440(seconds: Int, rate: Int): ShortArray {
        val n = seconds * rate
        return ShortArray(n) { (sin(2 * PI * 440 * it / rate) * 20_000).toInt().toShort() }
    }

    @Test
    fun chunkedResampleMatchesOneShot() {
        val pcm = sine440(seconds = 5, rate = 44_100)
        val oneShot = DspAnalyzer.resampleLinearMono(pcm, 44_100)

        val streamer = StreamingResampler()
        val chunked = mutableListOf<Short>()
        var pos = 0
        val chunkSizes = listOf(500, 1024, 37, 2048, 913)
        var ci = 0
        while (pos < pcm.size) {
            val len = minOf(chunkSizes[ci % chunkSizes.size], pcm.size - pos)
            chunked.addAll(streamer.feed(pcm.copyOfRange(pos, pos + len), 44_100).toList())
            pos += len
            ci++
        }
        chunked.addAll(streamer.flush().toList())

        assertTrue("chunked=${chunked.size} oneShot=${oneShot.size}", chunked.size == oneShot.size)
        val mismatches = chunked.indices.count { kotlin.math.abs(chunked[it] - oneShot[it]) > 1 }
        assertEquals("mismatches=$mismatches", 0, mismatches)
    }

    @Test
    fun bufferCapsAt120Seconds() {
        val buf = LongContextBuffer()
        val hop = ShortArray(16_000) { 1000 }
        repeat(150) { buf.feed(hop, 16_000) }
        assertEquals(120, buf.secondsBuffered.toInt())
        assertEquals(120 * 16_000, buf.snapshot().size)
    }

    @Test
    fun shortTracksAccumulateLessThanCap() {
        val buf = LongContextBuffer()
        val hop = ShortArray(16_000) { 1000 }
        repeat(47) { buf.feed(hop, 16_000) }
        assertEquals(47, buf.secondsBuffered.toInt())
    }
}
