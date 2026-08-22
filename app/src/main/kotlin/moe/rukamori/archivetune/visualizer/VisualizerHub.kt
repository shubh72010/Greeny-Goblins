/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Exact port of Better Nothing Music Visualizer — AudioCaptureService Visualizer pipeline
 * Uses AudioProcessor, GlyphRenderer, FlashlightEngine, ContinuousHapticEngine, BeatDetectionHapticEngine verbatim
 */

package moe.rukamori.archivetune.visualizer

import android.content.Context
import android.media.audiofx.Visualizer
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber
import java.util.ArrayDeque

class VisualizerHub(private val context: Context) {

    private var visualizer: Visualizer? = null
    private var attachedSessionId: Int = -1
    private var captureSize: Int = 0

    // Exact BNGV engines
    private val audioProcessor = AudioProcessor()
    private val glyphRenderer = GlyphRenderer(2.2f, false, DeviceProfile.detectDevice())
    private val flashlightEngine = FlashlightEngine(context)
    private val continuousHapticEngine = ContinuousHapticEngine(context)
    private val beatHapticEngine = BeatDetectionHapticEngine(context)

    // Glyph session handling via existing manager (wraps GlyphManager/GlyphMatrixManager exactly as BNGV does)
    private val glyphManager = GlyphVisualizerManager(context)

    // State
    private var deviceType: Int = DeviceProfile.detectDevice()
    private var visualizerConfig: AudioProcessor.VisualizerConfig? = null
    private var manualGain: Float = 4.0f
    private var glyphDecaySpeed: Float = 0.75f
    private var latencyMs: Int = 0
    private val presetConfigVersion = java.util.concurrent.atomic.AtomicInteger(0)
    private val pendingFrames: ArrayDeque<PendingFrame> = ArrayDeque()
    private var lastFft: IntArray = IntArray(512)

    // PendingFrame exactly as BNGV
    private data class PendingFrame(
        val fftraw: IntArray,
        val config: AudioProcessor.VisualizerConfig?,
        val configVersion: Int,
        val dueAtMs: Long
    )

    // Exposed for UI
    private val _fftBands = MutableStateFlow(IntArray(512))
    val fftBands: StateFlow<IntArray> = _fftBands
    private val _activePresetName = MutableStateFlow<String?>(null)
    val activePresetName: StateFlow<String?> = _activePresetName

    // Prefs – mirrored exactly as BNGV prefs
    var glyphEnabled: Boolean = false
        set(value) {
            field = value
            glyphManager.setEnabled(value)
            if (value) glyphManager.setMaxBrightness(glyphBrightness) else glyphManager.setMaxBrightness(0)
        }
    var glyphBrightness: Int = 4095
        set(value) {
            field = value.coerceIn(0, 4095)
            glyphManager.setMaxBrightness(field)
            if (field == 0) glyphManager.setEnabled(false)
        }
    var glyphGamma: Float = 2.2f
        set(value) {
            field = value
            glyphRenderer.setGamma(value)
        }
    var glyphIdleBreathing: Boolean = false
        set(value) {
            field = value
            glyphRenderer.setIdleBreathingEnabled(value)
        }
    var glyphPresetKey: String = "np1"
        set(value) {
            field = value
            reloadConfig()
        }

    var flashlightEnabled: Boolean = false
    var hapticEnabled: Boolean = false
    var hapticMode: HapticMode = HapticMode.BASS_TO_AMPLITUDE
    var hapticBeatMode: BeatEngineMode = BeatEngineMode.SMOOTH
    var hapticIntensity: Float = 1.0f
        set(value) {
            field = value.coerceIn(0.1f, 1.5f)
            continuousHapticEngine.setHapticMultiplier(field)
            beatHapticEngine.setHapticMultiplier(field)
        }
    private var _flashlightMode: TorchMode = TorchMode.AMPLITUDE
    var flashlightMode: TorchMode
        get() = _flashlightMode
        set(value) { _flashlightMode = value; flashlightEngine.setTorchMode(value) }
    private var _flashlightBeatMode: BeatEngineMode = BeatEngineMode.SMOOTH
    var flashlightBeatMode: BeatEngineMode
        get() = _flashlightBeatMode
        set(value) { _flashlightBeatMode = value; flashlightEngine.setBeatEngineMode(value) }
    var flashlightThreshold: Float = 0.15f
        set(value) { field = value; flashlightEngine.setFlashlightThreshold(value) }
    var hapticRange: AudioProcessor.FrequencyRange = AudioProcessor.FrequencyRange(60f, 250f)
    var flashlightRange: AudioProcessor.FrequencyRange = AudioProcessor.FrequencyRange(60f, 250f)

    private val mainHandler = Handler(Looper.getMainLooper())
    private var idleRunnable: Runnable? = null

    init {
        audioProcessor.setManualGain(manualGain)
        reloadConfig()
        glyphRenderer.setDeviceType(deviceType)
        glyphRenderer.setGamma(glyphGamma)
        glyphRenderer.setIdleBreathingEnabled(glyphIdleBreathing)
        glyphManager.setDeviceType(deviceType)
        glyphManager.setEnabled(glyphEnabled)
        glyphManager.setMaxBrightness(glyphBrightness)
        glyphManager.setGamma(glyphGamma)
        glyphManager.setIdleBreathingEnabled(glyphIdleBreathing)
        hapticRange = AudioProcessor.FrequencyRange(60f, 250f)
        flashlightRange = AudioProcessor.FrequencyRange(60f, 250f)
        startIdleTicks()
    }

    fun setDeviceType(t: Int) {
        deviceType = t
        glyphRenderer.setDeviceType(t)
        glyphManager.setDeviceType(t)
        reloadConfig()
    }

    private fun reloadConfig() {
        try {
            val cfg = VisualizerConfigLoader.buildConfigExact(context, glyphPresetKey, 44100, glyphDecaySpeed)
            visualizerConfig = cfg
            glyphRenderer.resetState(cfg)
            glyphManager.setConfig(cfg)
            _activePresetName.value = cfg?.name
            presetConfigVersion.incrementAndGet()
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "reloadConfig failed")
        }
    }

    fun setPreset(key: String) { glyphPresetKey = key }

    fun listPresets(): List<VisualizerConfigLoader.PresetInfo> = VisualizerConfigLoader.loadPresetInfos(context, deviceType)

    @Synchronized
    fun attach(sessionId: Int) {
        if (sessionId <= 0) return
        if (attachedSessionId == sessionId && visualizer != null) return
        // Keep existing tee even if Visualizer fails — we have direct PCM via BngvTeeAudioProcessor
        try { visualizer?.enabled = false } catch (_: Exception) {}
        try { visualizer?.release() } catch (_: Exception) {}
        visualizer = null
        attachedSessionId = sessionId
        try {
            audioProcessor.updateFFTSize()
            hapticRange = AudioProcessor.FrequencyRange(60f, 250f)
            flashlightRange = AudioProcessor.FrequencyRange(60f, 250f)
        } catch (_: Exception) {}
        // Try Visualizer as secondary (for haptics fallback), but don't fail if it errors — tee is primary
        try {
            val viz = try { Visualizer(sessionId) } catch (e: Exception) {
                Log.e(TAG, "Failed to create Visualizer(sessionId), trying 0", e)
                SystemClock.sleep(200)
                Visualizer(0)
            }
            val captureSizeLocal = try { minOf(Visualizer.getCaptureSizeRange()[1], 1024) } catch (_: Exception) { 1024 }
            // setCaptureSize must be called before setEnabled and when not enabled
            try { viz.captureSize = captureSizeLocal } catch (e: IllegalStateException) {
                Log.w(TAG, "setCaptureSize failed, trying release+retry", e)
                try { viz.release() } catch (_: Exception) {}
                return
            }
            captureSize = captureSizeLocal
            val captureRate = try { minOf(Visualizer.getMaxCaptureRate(), 50000) } catch (_: Exception) { 20000 }
            viz.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, samplingRate: Int) {
                    if (waveform != null) processVisualizerWaveform(waveform, samplingRate)
                }
                override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, samplingRate: Int) {}
            }, captureRate, true, false)
            val result = viz.setEnabled(true)
            if (result != Visualizer.SUCCESS) {
                Log.e(TAG, "visualizer enable failed $result")
                viz.release()
                return
            }
            visualizer = viz
            Timber.tag(TAG).d("VisualizerHub attached session %d size %d rate %d (tee primary)", sessionId, captureSize, captureRate)
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Visualizer attach failed %d — continuing with tee only", sessionId)
            // Do not clear attachedSessionId; tee still works
            visualizer = null
        }
    }

    @Synchronized
    fun detach() { detachInternal() }

    private fun detachInternal() {
        try { visualizer?.enabled = false } catch (_: Exception) {}
        try { visualizer?.release() } catch (_: Exception) {}
        visualizer = null
        attachedSessionId = -1
        captureSize = 0
        synchronized(pendingFrames) { pendingFrames.clear() }
    }

    fun stop() {
        detach()
        continuousHapticEngine.stopHaptics()
        beatHapticEngine.stopHaptics()
        flashlightEngine.stopFlashlight()
        glyphManager.setEnabled(false)
    }

    fun release() {
        stop()
        glyphManager.release()
        stopIdleTicks()
    }

    // Exact processVisualizerWaveform from BNGV AudioCaptureService:1397
    private fun processVisualizerWaveform(waveform: ByteArray, samplingRate: Int) {
        val deepSilence = glyphRenderer.isDeeplySilent()
        if (deepSilence) {
            var hasAnySignal = false
            for (b in waveform) {
                if (kotlin.math.abs((b.toInt() and 0xFF) - 128) > 3) { hasAnySignal = true; break }
            }
            if (!hasAnySignal) {
                val frame = PendingFrame(IntArray(512), visualizerConfig, presetConfigVersion.get(), SystemClock.elapsedRealtime() + latencyMs)
                synchronized(pendingFrames) { pendingFrames.addLast(frame); dispatchDueFrames(pendingFrames) }
                return
            }
        }
        var hz = if (samplingRate > 1000000) samplingRate / 1000 else samplingRate
        if (hz < 8000) hz = 44100
        audioProcessor.updateFFTSize(hz)
        val hop = ShortArray(waveform.size) { i -> (((waveform[i].toInt() and 0xFF) - 128) shl 8).toShort() }
        val result = audioProcessor.processAudioFrame(hop, AudioProcessor.SourceType.VIZUALIZER, visualizerConfig?.decay ?: 0.85f) ?: return
        val frame = PendingFrame(result.fftraw, visualizerConfig, presetConfigVersion.get(), SystemClock.elapsedRealtime() + latencyMs)
        synchronized(pendingFrames) { pendingFrames.addLast(frame); dispatchDueFrames(pendingFrames) }
    }

    // Direct PCM tee from ExoPlayer — we have control of the music itself, no need for screen capture.
    // Called on audio thread from BngvTeeAudioProcessor — post to main for glyph/haptic/flash dispatch
    fun onPcm(hop: ShortArray, sampleRate: Int) {
        if (!glyphEnabled && !flashlightEnabled && !hapticEnabled) return
        // Copy hop to avoid mutation on audio thread
        val hopCopy = hop.copyOf()
        mainHandler.post {
            if (glyphRenderer.isDeeplySilent()) {
                var hasSignal = false
                for (s in hopCopy) if (kotlin.math.abs(s.toInt()) > 750) { hasSignal = true; break }
                if (!hasSignal) {
                    val frame = PendingFrame(IntArray(512), visualizerConfig, presetConfigVersion.get(), SystemClock.elapsedRealtime() + latencyMs)
                    synchronized(pendingFrames) { pendingFrames.addLast(frame); dispatchDueFrames(pendingFrames) }
                    return@post
                }
            }
            audioProcessor.updateFFTSize(sampleRate)
            val result = audioProcessor.processAudioFrame(hopCopy, AudioProcessor.SourceType.INTERNAL, visualizerConfig?.decay ?: 0.85f) ?: return@post
            val frame = PendingFrame(result.fftraw, visualizerConfig, presetConfigVersion.get(), SystemClock.elapsedRealtime() + latencyMs)
            synchronized(pendingFrames) { pendingFrames.addLast(frame); dispatchDueFrames(pendingFrames) }
        }
    }

    // Exact dispatchDueFrames from BNGV:1338
    private fun dispatchDueFrames(pending: ArrayDeque<PendingFrame>) {
        val now = SystemClock.elapsedRealtime()
        var latestDue: PendingFrame? = null
        while (pending.isNotEmpty() && pending.first.dueAtMs <= now) {
            latestDue = pending.removeFirst()
        }
        if (latestDue != null) {
            // drop older, keep latest
            pending.clear()
            _fftBands.value = latestDue.fftraw
            lastFft = latestDue.fftraw
            processFrame(latestDue.fftraw, latestDue.config, latestDue.configVersion)
        } else if (pending.size > 10) {
            // overflow – drop
            while (pending.size > 5) pending.removeFirst()
        }
    }

    // Exact processFrame from BNGV:1281 (glyph + haptics + flashlight)
    private fun processFrame(fftraw: IntArray, config: AudioProcessor.VisualizerConfig?, configVersion: Int) {
        try {
            val now = System.currentTimeMillis()
            if (glyphEnabled && config != null) {
                val frameColors = glyphRenderer.processFrame(fftraw, config, now)
                if (frameColors != null) glyphManager.submitFrame(frameColors)
            }
            // Haptics – exact BNGV logic: choose based on mode
            if (hapticEnabled) {
                when (hapticMode) {
                    HapticMode.BASS_TO_AMPLITUDE -> {
                        var max = 0
                        for (i in hapticRange.logBinLo..hapticRange.logBinHi) if (fftraw[i] > max) max = fftraw[i]
                        val peak = max / 4095f
                        continuousHapticEngine.performHapticFeedback(peak, config)
                    }
                    HapticMode.BEAT_DETECTION -> {
                        beatHapticEngine.performHapticFeedback(fftraw, hapticRange)
                    }
                }
            }
            // Flashlight – exact
            if (flashlightEnabled) {
                if (_flashlightMode == TorchMode.BEAT_DETECTION) {
                    flashlightEngine.performFlashlightFeedback(0f, config, fftraw, flashlightRange.logBinLo, flashlightRange.logBinHi)
                } else {
                    var max = 0
                    for (i in flashlightRange.logBinLo..flashlightRange.logBinHi) if (fftraw[i] > max) max = fftraw[i]
                    val peak = max / 4095f
                    flashlightEngine.performFlashlightFeedback(peak, config, fftraw, flashlightRange.logBinLo, flashlightRange.logBinHi)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "processFrame error", e)
        }
    }

    private fun startIdleTicks() {
        if (idleRunnable != null) return
        val r = object : Runnable {
            override fun run() {
                // BNGV: dispatchDueFrames tick + breathing
                synchronized(pendingFrames) { dispatchDueFrames(pendingFrames) }
                // If no frames, still tick glyph breathing
                if (pendingFrames.isEmpty() && visualizerConfig != null) {
                    // Trigger idle breathing tick
                    val now = System.currentTimeMillis()
                    val silence = IntArray(512)
                    val colors = glyphRenderer.processFrame(silence, visualizerConfig, now)
                    if (colors != null) glyphManager.submitFrame(colors)
                }
                mainHandler.postDelayed(this, 16)
            }
        }
        idleRunnable = r
        mainHandler.postDelayed(r, 16)
    }
    private fun stopIdleTicks() {
        idleRunnable?.let { mainHandler.removeCallbacks(it) }
        idleRunnable = null
    }

    fun updateManualGain(g: Float) {
        manualGain = g
        audioProcessor.setManualGain(g)
    }
    fun updateFlashlightEnabled(e: Boolean) {
        flashlightEnabled = e
        if (!e) flashlightEngine.stopFlashlight()
    }
    fun updateFlashlightSpeedMs(v: Float) = flashlightEngine.setFlashlightSpeedMs(v)
    fun stopFlashlight() = flashlightEngine.stopFlashlight()
    fun setGlyphDecay(speed: Float) {
        glyphDecaySpeed = speed
        reloadConfig()
    }

    companion object { private const val TAG = "VisualizerHub" }
}
