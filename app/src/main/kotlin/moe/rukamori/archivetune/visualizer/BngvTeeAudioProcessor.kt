/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Tee AudioProcessor to feed ExoPlayer PCM directly into BNGV AudioProcessor
 * Exact BNGV audio path but via ExoPlayer PCM instead of MediaProjection/Visualizer
 */

package moe.rukamori.archivetune.visualizer

import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import java.nio.ByteBuffer

/**
 * Pass-through AudioProcessor that taps PCM and forwards to VisualizerHub.
 * Keeps music visuals in sync without Visualizer API or screen capture.
 * We have direct control of the music — use it.
 */
class BngvTeeAudioProcessor(
    private val onPcm: (hop: ShortArray, sampleRate: Int) -> Unit
) : AudioProcessor {

    private var audioFormat: AudioFormat = AudioFormat.NOT_SET
    private var isActiveFlag = false
    private var inputEnded = false
    private var outputBuffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER

    override fun configure(inputAudioFormat: AudioFormat): AudioFormat {
        audioFormat = inputAudioFormat
        // We handle 16-bit PCM only; float is disabled in MusicService (setEnableFloatOutput false)
        // Still handle float by converting, but mark active for 16-bit
        isActiveFlag = inputAudioFormat.encoding == androidx.media3.common.C.ENCODING_PCM_16BIT
                || inputAudioFormat.encoding == androidx.media3.common.C.ENCODING_PCM_24BIT
                || inputAudioFormat.encoding == androidx.media3.common.C.ENCODING_PCM_32BIT
                || inputAudioFormat.encoding == androidx.media3.common.C.ENCODING_PCM_FLOAT
        // Use pass-through: output same as input
        return inputAudioFormat
    }

    override fun isActive(): Boolean = isActiveFlag

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!isActiveFlag) {
            outputBuffer = inputBuffer
            return
        }
        if (!inputBuffer.hasRemaining()) return

        // Duplicate buffer for output (pass-through)
        val position = inputBuffer.position()
        val limit = inputBuffer.limit()
        val size = limit - position

        // Copy bytes for tee processing before consuming
        val bytes = ByteArray(size)
        inputBuffer.get(bytes)
        // Reset position for output
        inputBuffer.position(position)

        // Convert to short[] hop for BNGV AudioProcessor
        // Handle encoding: for PCM_16BIT, bytes are little endian 16-bit
        // For stereo, downmix to mono by averaging
        val channelCount = audioFormat.channelCount.coerceAtLeast(1)
        val sampleRate = audioFormat.sampleRate.coerceAtLeast(44100)
        val hop: ShortArray = when (audioFormat.encoding) {
            androidx.media3.common.C.ENCODING_PCM_16BIT -> {
                val totalSamples = size / 2
                val monoSamples = totalSamples / channelCount
                val out = ShortArray(monoSamples)
                var outIdx = 0
                var i = 0
                while (i < size && outIdx < monoSamples) {
                    var sum = 0
                    for (ch in 0 until channelCount) {
                        if (i + 1 >= size) break
                        val lo = bytes[i].toInt() and 0xFF
                        val hi = bytes[i + 1].toInt()
                        val sample = (hi shl 8 or lo).toShort().toInt()
                        sum += sample
                        i += 2
                    }
                    out[outIdx++] = (sum / channelCount).toShort()
                }
                out
            }
            androidx.media3.common.C.ENCODING_PCM_FLOAT -> {
                // Float 32-bit, 4 bytes per sample
                val totalSamples = size / 4
                val monoSamples = totalSamples / channelCount
                val out = ShortArray(monoSamples)
                var byteIdx = 0
                for (s in 0 until monoSamples) {
                    var sum = 0.0
                    for (ch in 0 until channelCount) {
                        if (byteIdx + 3 >= size) break
                        val bits = (bytes[byteIdx].toInt() and 0xFF) or
                                ((bytes[byteIdx + 1].toInt() and 0xFF) shl 8) or
                                ((bytes[byteIdx + 2].toInt() and 0xFF) shl 16) or
                                (bytes[byteIdx + 3].toInt() shl 24)
                        val f = java.lang.Float.intBitsToFloat(bits)
                        sum += (f * 32767.0).toInt().coerceIn(-32768, 32767)
                        byteIdx += 4
                    }
                    out[s] = (sum / channelCount).toInt().toShort()
                }
                out
            }
            else -> {
                // Fallback: treat as 16-bit
                val totalSamples = size / 2
                ShortArray(totalSamples) { idx ->
                    if (idx * 2 + 1 < size) {
                        val lo = bytes[idx * 2].toInt() and 0xFF
                        val hi = bytes[idx * 2 + 1].toInt()
                        (hi shl 8 or lo).toShort()
                    } else 0
                }
            }
        }

        if (hop.isNotEmpty()) {
            try {
                onPcm(hop, sampleRate)
            } catch (_: Exception) {}
        }

        // Pass-through: output is the original buffer
        outputBuffer = inputBuffer
    }

    override fun queueEndOfStream() {
        inputEnded = true
    }

    override fun getOutput(): ByteBuffer {
        val out = outputBuffer
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        return out
    }

    override fun isEnded(): Boolean = inputEnded && outputBuffer === AudioProcessor.EMPTY_BUFFER

    override fun flush() {
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        inputEnded = false
    }

    override fun reset() {
        flush()
        audioFormat = AudioFormat.NOT_SET
        isActiveFlag = false
    }
}
