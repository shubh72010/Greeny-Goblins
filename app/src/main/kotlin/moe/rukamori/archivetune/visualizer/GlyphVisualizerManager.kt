/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Ported from Better Nothing Music Visualizer — AudioCaptureService Glyph logic
 */

package moe.rukamori.archivetune.visualizer

import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.nothing.ketchum.Common
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphManager
import com.nothing.ketchum.GlyphMatrixManager
import timber.log.Timber

class GlyphVisualizerManager(private val context: Context) {

    private var deviceType: Int = DeviceProfile.detectDevice()
    private var glyphManager: GlyphManager? = null
    private var glyphMatrixManager: GlyphMatrixManager? = null
    private var gmConnected: Boolean = false
    private var gmmConnected: Boolean = false
    private var sessionOpen: Boolean = false
    private var lastFrameHash: Int = Int.MIN_VALUE

    private val mainHandler = Handler(Looper.getMainLooper())
    private val glyphRenderer = GlyphRenderer(2.2f, false, deviceType)
    private var visualizerConfig: AudioProcessor.VisualizerConfig? = null
    private var maxBrightness: Int = 4095
    private var gamma: Float = 2.2f
    private var enabled: Boolean = false

    private val gmCallback = object : GlyphManager.Callback {
        override fun onServiceConnected(name: ComponentName) {
            gmConnected = true
            registerGlyphManager()
            ensureSession()
        }
        override fun onServiceDisconnected(name: ComponentName) {
            gmConnected = false
            sessionOpen = false
        }
    }

    private val gmmCallback = object : GlyphMatrixManager.Callback {
        override fun onServiceConnected(name: ComponentName) {
            gmmConnected = true
            registerMatrixManager()
        }
        override fun onServiceDisconnected(name: ComponentName) {
            gmmConnected = false
        }
    }

    fun setEnabled(e: Boolean) {
        enabled = e
        if (!e) {
            clearSession()
            glyphRenderer.setMaxBrightness(0)
        } else {
            glyphRenderer.setMaxBrightness(maxBrightness)
            ensureGlyphManagerInitialized()
            ensureSession()
        }
    }

    fun setMaxBrightness(b: Int) {
        maxBrightness = b.coerceIn(0, 4095)
        if (!enabled || deviceType == DeviceProfile.DEVICE_UNKNOWN) {
            glyphRenderer.setMaxBrightness(0)
        } else {
            glyphRenderer.setMaxBrightness(maxBrightness)
            if (maxBrightness == 0) clearSession() else ensureSession()
        }
    }

    fun setGamma(g: Float) { gamma = g; glyphRenderer.setGamma(g) }

    fun setIdleBreathingEnabled(e: Boolean) { glyphRenderer.setIdleBreathingEnabled(e) }

    fun setConfig(config: AudioProcessor.VisualizerConfig?) {
        visualizerConfig = config
        glyphRenderer.resetState(config)
        lastFrameHash = Int.MIN_VALUE
    }

    fun submitFramePublic(colors: IntArray) = submitFrame(colors)

    fun setDeviceType(device: Int) {
        deviceType = device
        glyphRenderer.setDeviceType(device)
        if (device != DeviceProfile.DEVICE_UNKNOWN) {
            ensureGlyphManagerInitialized()
            registerGlyphManager()
            registerMatrixManager()
        }
        // Reload config will be done by caller
    }

    fun onFftFrame(fftraw: IntArray) {
        if (!enabled || deviceType == DeviceProfile.DEVICE_UNKNOWN) return
        val config = visualizerConfig ?: return
        if (maxBrightness == 0) return
        val now = System.currentTimeMillis()
        val frame = glyphRenderer.processFrame(fftraw, config, now) ?: return
        // Dedupe already handled in renderer
        submitFrame(frame)
    }

    fun tickIdle() {
        // Keep idle breathing animating even without audio by feeding silence frame
        if (!enabled || !glyphRenderer.isBreathing() && !glyphRenderer.isDeeplySilent()) return
        val config = visualizerConfig ?: return
        val now = System.currentTimeMillis()
        // Use zeroed fft to trigger breathing path
        val silence = IntArray(512)
        val frame = glyphRenderer.processFrame(silence, config, now) ?: return
        submitFrame(frame)
    }

    fun submitFrame(frameColors: IntArray) {
        if (frameColors.isEmpty()) return
        if (!sessionOpen) ensureSession()
        if (!sessionOpen) return
        try {
            // Try matrix device first (Phone 3 / 4a Pro)
            if (DeviceProfile.getMatrixWidth(deviceType) > 0) {
                submitMatrixFrame(frameColors)
            } else {
                submitGlyphFrame(frameColors)
            }
        } catch (e: Exception) {
            Log.w(TAG, "submitFrame failed", e)
        }
    }

    private fun submitGlyphFrame(colors: IntArray) {
        val gm = glyphManager ?: return
        try {
            gm.setFrameColors(colors)
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Glyph setFrameColors failed")
        }
    }

    private fun submitMatrixFrame(colors: IntArray) {
        val gmm = glyphMatrixManager ?: return
        try {
            gmm.setAppMatrixFrame(colors)
        } catch (e: Exception) {
            try { gmm.setMatrixFrame(colors) } catch (e2: Exception) { Timber.tag(TAG).w(e2, "Matrix submit failed") }
        }
    }

    private fun ensureGlyphManagerInitialized() {
        if (deviceType == DeviceProfile.DEVICE_UNKNOWN || Build.VERSION.SDK_INT < 31) return
        if (glyphManager == null) {
            try {
                val gm = GlyphManager.getInstance(context.applicationContext)
                glyphManager = gm
                gm?.init(gmCallback)
            } catch (e: Exception) { Log.w(TAG, "GlyphManager init failed", e) }
        }
        if (glyphMatrixManager == null && DeviceProfile.getMatrixWidth(deviceType) > 0) {
            try {
                val gmm = GlyphMatrixManager.getInstance(context.applicationContext)
                glyphMatrixManager = gmm
                gmm?.init(gmmCallback)
            } catch (e: Exception) { Log.w(TAG, "GlyphMatrixManager init failed", e) }
        }
    }

    private fun registerGlyphManager() {
        val gm = glyphManager ?: return
        if (!gmConnected || deviceType == DeviceProfile.DEVICE_UNKNOWN) return
        try {
            val deviceStr = when (deviceType) {
                DeviceProfile.DEVICE_NP1 -> Glyph.DEVICE_20111
                DeviceProfile.DEVICE_NP2 -> Glyph.DEVICE_22111
                DeviceProfile.DEVICE_NP2A -> if (Build.MODEL.contains("23113")) "23113" else Glyph.DEVICE_23111
                DeviceProfile.DEVICE_NP3A -> Glyph.DEVICE_24111
                DeviceProfile.DEVICE_NP4A -> Glyph.DEVICE_25111
                DeviceProfile.DEVICE_NP4APRO -> Glyph.DEVICE_25111p
                DeviceProfile.DEVICE_NP3 -> Glyph.DEVICE_23112
                DeviceProfile.DEVICE_NP4B -> "26111"
                else -> Glyph.DEVICE_25111
            }
            gm.register(deviceStr)
        } catch (e: Exception) { Log.w(TAG, "registerGlyphManager failed", e) }
    }

    private fun registerMatrixManager() {
        val gmm = glyphMatrixManager ?: return
        if (!gmmConnected || deviceType == DeviceProfile.DEVICE_UNKNOWN) return
        try {
            if (deviceType == DeviceProfile.DEVICE_NP3) {
                gmm.register(Glyph.DEVICE_23112)
            } else if (deviceType == DeviceProfile.DEVICE_NP4APRO) {
                gmm.register(Glyph.DEVICE_25111p)
            }
        } catch (e: Exception) { Log.w(TAG, "registerMatrix failed", e) }
    }

    private fun ensureSession() {
        if (sessionOpen) return
        if (deviceType == DeviceProfile.DEVICE_UNKNOWN) return
        val gm = glyphManager ?: return
        try {
            gm.openSession()
            sessionOpen = true
        } catch (e: Exception) { Log.w(TAG, "ensureSession failed", e) }
    }

    private fun clearSession() {
        if (!sessionOpen) return
        try { glyphManager?.closeSession() } catch (_: Exception) {}
        try { glyphMatrixManager?.closeAppMatrix() } catch (_: Exception) {}
        try { glyphManager?.turnOff() } catch (_: Exception) {}
        try { glyphMatrixManager?.turnOff() } catch (_: Exception) {}
        sessionOpen = false
    }

    fun release() {
        clearSession()
        try { glyphManager?.unInit() } catch (_: Exception) {}
        try { glyphMatrixManager?.unInit() } catch (_: Exception) {}
        glyphManager = null
        glyphMatrixManager = null
        gmConnected = false
        gmmConnected = false
    }

    companion object { private const val TAG = "GlyphVisualizerManager" }
}
