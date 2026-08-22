/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback.haptic

import android.content.Context
import android.media.AudioAttributes
import android.media.audiofx.Visualizer
import android.os.Build
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlin.math.max
import kotlin.math.pow
import timber.log.Timber

enum class HapticVisualizerMode {
    CONTINUOUS,
    BEAT,
}

/**
 * Drives the device vibration motor in sync with the bass content of the
 * currently playing audio. Taps the playback audio session through
 * [android.media.audiofx.Visualizer] so no changes to the Media3 pipeline
 * are required.
 *
 * - CONTINUOUS: vibration amplitude tracks the smoothed bass energy.
 * - BEAT: a spectral-flux beat detector fires a short decaying pulse on each beat.
 */
class HapticVisualizer(context: Context) {
    private val appContext = context.applicationContext

    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                ?.defaultVibrator
                ?: appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } else {
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    private val hasAmplitudeControl: Boolean = vibrator?.hasAmplitudeControl() ?: false

    @Volatile
    var enabled: Boolean = false

    @Volatile
    var mode: HapticVisualizerMode = HapticVisualizerMode.CONTINUOUS

    @Volatile
    var intensity: Float = 1f
        set(value) {
            field = value.coerceIn(0.1f, 1.5f)
        }

    private var visualizer: Visualizer? = null
    private var attachedSessionId: Int = -1
    @Volatile
    private var captureSize = 0
    @Volatile
    private var sampleRate = 44100

    private val beatDetector = HapticBeatDetector()

    private var lastSubmitMs = 0L
    private var lastAmplitude = 0
    private var envelope = 0f
    private var longTermLevel = 0.001f

    @Synchronized
    fun attach(sessionId: Int) {
        if (sessionId <= 0) return
        if (attachedSessionId == sessionId && visualizer != null) return
        detachInternal()
        attachedSessionId = sessionId
        try {
            val newVisualizer = Visualizer(sessionId)
            val sizeRange = Visualizer.getCaptureSizeRange()
            captureSize = minOf(sizeRange[1], MAX_CAPTURE_SIZE)
            newVisualizer.captureSize = captureSize
            newVisualizer.scalingMode = Visualizer.SCALING_MODE_AS_PLAYED
            newVisualizer.setDataCaptureListener(
                object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        visualizer: Visualizer?,
                        waveform: ByteArray,
                        samplingRate: Int,
                    ) = Unit

                    override fun onFftDataCapture(
                        visualizer: Visualizer?,
                        fft: ByteArray,
                        samplingRate: Int,
                    ) {
                        onFftData(fft, samplingRate)
                    }
                },
                Visualizer.getMaxCaptureRate(),
                false,
                true,
            )
            newVisualizer.enabled = true
            visualizer = newVisualizer
            Timber.tag(TAG).d("Haptic visualizer attached to session %d (capture size %d)", sessionId, captureSize)
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Haptic visualizer init failed for session %d", sessionId)
            detachInternal()
        }
    }

    @Synchronized
    fun detach() {
        detachInternal()
    }

    @Synchronized
    fun stop() {
        try {
            vibrator?.cancel()
        } catch (_: Exception) {
        }
        lastSubmitMs = 0L
        lastAmplitude = 0
        envelope = 0f
        longTermLevel = 0.001f
        beatDetector.reset()
    }

    private fun detachInternal() {
        try {
            visualizer?.enabled = false
        } catch (_: Exception) {
        }
        try {
            visualizer?.release()
        } catch (_: Exception) {
        }
        visualizer = null
        attachedSessionId = -1
        captureSize = 0
    }

    private fun onFftData(fft: ByteArray, reportedRate: Int) {
        if (!enabled || captureSize <= 0 || fft.size < 2) return
        val samplingRate = normalizeSampleRate(reportedRate)
        sampleRate = samplingRate

        val magnitudes = bassMagnitudes(fft, samplingRate) ?: return
        val rawPeak = magnitudes.maxOrNull() ?: 0f

        // Downward-only envelope: rise instantly with the music, decay smoothly.
        envelope = if (rawPeak > envelope) rawPeak else envelope + (rawPeak - envelope) * ENVELOPE_RELEASE
        longTermLevel += (envelope - longTermLevel) * LONG_TERM_RATE
        val normalized = (envelope / maxOf(longTermLevel * AGC_FACTOR, AGC_FLOOR)).coerceIn(0f, 1f)

        when (mode) {
            HapticVisualizerMode.CONTINUOUS -> driveContinuous(normalized)
            HapticVisualizerMode.BEAT -> {
                if (beatDetector.detect(magnitudes)) {
                    fireBeat()
                }
            }
        }
    }

    private fun bassMagnitudes(fft: ByteArray, samplingRate: Int): FloatArray? {
        val bins = captureSize / 2
        if (bins <= 0) return null
        val hzPerBin = samplingRate.toFloat() / captureSize.toFloat()
        val loBin = (BASS_MIN_HZ / hzPerBin).toInt().coerceIn(0, bins - 1)
        val hiBin = (BASS_MAX_HZ / hzPerBin).toInt().coerceIn(loBin, bins - 1)
        val magnitudes = FloatArray(hiBin - loBin + 1)
        for (bin in loBin..hiBin) {
            val index = bin * 2
            if (index + 1 >= fft.size) break
            magnitudes[bin - loBin] = (fft[index].toInt() and 0xFF) / 255f
        }
        return magnitudes
    }

    private fun normalizeSampleRate(rate: Int): Int {
        val hz = if (rate > 1_000_000) rate / 1000 else rate
        return if (hz in 8_000..192_000) hz else sampleRate
    }

    private fun driveContinuous(normalized: Float) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastSubmitMs < CONTINUOUS_RESUBMIT_INTERVAL_MS) return

        if (!hasAmplitudeControl) {
            if (normalized > BINARY_PULSE_THRESHOLD) {
                vibrateOnce(VibrationEffect.createOneShot(BINARY_PULSE_MS.toLong(), VibrationEffect.DEFAULT_AMPLITUDE))
                lastSubmitMs = now
            }
            return
        }

        val shaped = normalized.pow(CONTINUOUS_GAMMA)
        val amplitude = (shaped * 255f * intensity).toInt().coerceIn(0, 255)
        if (amplitude <= 0) {
            if (lastAmplitude != 0) {
                try {
                    vibrator?.cancel()
                } catch (_: Exception) {
                }
                lastAmplitude = 0
            }
            return
        }
        vibrateOnce(VibrationEffect.createOneShot(CONTINUOUS_ONE_SHOT_MS.toLong(), amplitude))
        lastAmplitude = amplitude
        lastSubmitMs = now
    }

    private fun fireBeat() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastSubmitMs < BEAT_COOLDOWN_MS) return
        val effect =
            if (hasAmplitudeControl) {
                buildBeatWaveform()
            } else {
                VibrationEffect.createOneShot(BEAT_PULSE_MS.toLong(), VibrationEffect.DEFAULT_AMPLITUDE)
            }
        vibrateOnce(effect)
        lastSubmitMs = now
    }

    private fun buildBeatWaveform(): VibrationEffect {
        val count = (BEAT_SUSTAIN_MS + BEAT_DECAY_MS) / BEAT_STEP_MS
        val timings = LongArray(count) { BEAT_STEP_MS.toLong() }
        val amplitudes =
            IntArray(count) { step ->
                val t = step * BEAT_STEP_MS
                val amp =
                    if (t < BEAT_SUSTAIN_MS) {
                        1f
                    } else {
                        val x = 1f - (t - BEAT_SUSTAIN_MS).toFloat() / BEAT_DECAY_MS.toFloat()
                        x.coerceIn(0f, 1f).pow(BEAT_DECAY_GAMMA)
                    }
                (amp * 255f * intensity).toInt().coerceIn(1, 255)
            }
        return VibrationEffect.createWaveform(timings, amplitudes, -1)
    }

    private fun vibrateOnce(effect: VibrationEffect) {
        val vibrator = vibrator ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val attributes =
                    VibrationAttributes.Builder()
                        .setUsage(VibrationAttributes.USAGE_MEDIA)
                        .build()
                vibrator.vibrate(effect, attributes)
            } else {
                val attributes =
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                vibrator.vibrate(effect, attributes)
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Vibration failed")
        }
    }

    private companion object {
        const val TAG = "HapticVisualizer"
        const val MAX_CAPTURE_SIZE = 1024
        const val BASS_MIN_HZ = 30f
        const val BASS_MAX_HZ = 250f
        const val ENVELOPE_RELEASE = 0.08f
        const val LONG_TERM_RATE = 0.002f
        const val AGC_FACTOR = 2f
        const val AGC_FLOOR = 0.01f
        const val CONTINUOUS_RESUBMIT_INTERVAL_MS = 20L
        const val CONTINUOUS_ONE_SHOT_MS = 100
        const val CONTINUOUS_GAMMA = 1.6f
        const val BINARY_PULSE_THRESHOLD = 0.35f
        const val BINARY_PULSE_MS = 60
        const val BEAT_COOLDOWN_MS = 60L
        const val BEAT_PULSE_MS = 60
        const val BEAT_SUSTAIN_MS = 30
        const val BEAT_DECAY_MS = 250
        const val BEAT_STEP_MS = 10
        const val BEAT_DECAY_GAMMA = 1.5f
    }
}

class HapticBeatDetector(
    private val sensitivity: Float = 1f,
) {
    private var previous: FloatArray? = null
    private var flux0 = 0f
    private var flux1 = 0f
    private var flux2 = 0f
    private var mean = 0f
    private var variance = 0f
    private var thresholdMask = 0f
    private var lastBeatMs = 0L

    fun detect(magnitudes: FloatArray): Boolean {
        val prev = previous
        if (prev == null || prev.size != magnitudes.size) {
            previous = magnitudes.copyOf()
            return false
        }

        // Positive spectral flux: sum of squared upward magnitude changes,
        // weighted toward the low end where kick drums live.
        var flux = 0f
        var weightSum = 0f
        for (i in magnitudes.indices) {
            val diff = magnitudes[i] - prev[i]
            if (diff > 0f) {
                val energy = diff * diff
                val weight = 1f / (1f + i * 0.05f)
                flux += energy * weight
                weightSum += weight
            }
        }
        System.arraycopy(magnitudes, 0, prev, 0, magnitudes.size)

        val rawFlux = if (weightSum > 0f) flux / weightSum else 0f
        val smoothedFlux = rawFlux * 0.7f + flux0 * 0.3f
        flux2 = flux1
        flux1 = flux0
        flux0 = smoothedFlux

        val stdDev = max(kotlin.math.sqrt(variance), 1e-4f)
        val multiplier = (1.5f / max(0.1f, sensitivity)).coerceIn(0.5f, 4f)
        val dynamicThreshold = max(mean + multiplier * stdDev, thresholdMask)

        val now = SystemClock.elapsedRealtime()
        val isLocalPeak = flux1 > flux2 && flux1 > flux0
        val aboveThreshold = flux1 > dynamicThreshold && flux1 > NOISE_FLOOR
        val cooldownPassed = now - lastBeatMs >= COOLDOWN_MS

        val triggered = isLocalPeak && aboveThreshold && cooldownPassed
        if (triggered) {
            lastBeatMs = now
            thresholdMask = flux1 * 0.7f
        } else {
            thresholdMask *= 0.95f
        }

        val delta = flux0 - mean
        mean += MEAN_ALPHA * delta
        variance = (1f - MEAN_ALPHA) * (variance + MEAN_ALPHA * delta * delta)
        return triggered
    }

    fun reset() {
        previous = null
        flux0 = 0f
        flux1 = 0f
        flux2 = 0f
        mean = 0f
        variance = 0f
        thresholdMask = 0f
        lastBeatMs = 0L
    }

    private companion object {
        const val NOISE_FLOOR = 0.0005f
        const val COOLDOWN_MS = 130L
        const val MEAN_ALPHA = 0.02f
    }
}