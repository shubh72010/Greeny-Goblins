package moe.rukamori.archivetune.visualizer.bnmv

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Lifecycle owner for the external BNMV integration.
 *
 * Responsibilities:
 * - Owns [BnmvUdpStreamer] and drives its connect/disconnect based on user prefs.
 * - Forwards PCM hops (from [moe.rukamori.archivetune.visualizer.BngvTeeAudioProcessor])
 *   to the streamer at 60fps after handshake.
 * - Proxies preset / feature toggles to [BnmvController] broadcasts.
 * - Exposes simple state for UI (installed / connected / streaming).
 *
 * This replaces the previous in-process [moe.rukamori.archivetune.visualizer.VisualizerHub]
 * which bundled GlyphRenderer/FlashlightEngine/Haptics directly. Those are now owned by the
 * external BNMV app.
 */
class BnmvIntegrationManager(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private val streamer = BnmvUdpStreamer(appContext, scope)

    private val _isInstalled = MutableStateFlow(BnmvController.isInstalled(appContext))
    val isInstalled: StateFlow<Boolean> = _isInstalled

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    private var enabled = false
    private var presetKey: String = "np2"
    private var handshakePollJob: Job? = null

    fun refreshInstallState() {
        _isInstalled.value = BnmvController.isInstalled(appContext)
    }

    fun setEnabled(value: Boolean) {
        if (enabled == value) return
        enabled = value
        if (value) {
            refreshInstallState()
            if (!_isInstalled.value) {
                Timber.tag(TAG).w("BNMV not installed — enable requested but app missing")
                // Still attempt connect so that if user installs it, next connect succeeds
            }
            connectInternal()
        } else {
            disconnectInternal()
        }
    }

    fun setPreset(key: String) {
        presetKey = key
        if (enabled && _isInstalled.value) {
            streamer.setPreset(key)
        }
    }

    fun setFeature(action: String, isEnabled: Boolean) {
        if (enabled) streamer.setFeatureEnabled(action, isEnabled)
        // Always also broadcast so BNMV persists toggle even if streamer not yet connected
        BnmvController.toggleFeature(appContext, action, isEnabled)
    }

    fun startVisualizer() {
        if (!enabled) return
        BnmvController.start(appContext)
    }

    fun stopVisualizer() {
        BnmvController.stop(appContext)
    }

    /**
     * Called from [BngvTeeAudioProcessor] on audio thread.
     * Must be non-blocking and not leak coroutines: we process synchronously on the caller's
     * background thread (BnmvUdpStreamer already throttles to 60fps). Hop is already a copy
     * from BngvTeeAudioProcessor, so no extra copy needed.
     */
    fun onPcm(hop: ShortArray, sampleRate: Int) {
        if (!enabled) return
        if (!streamer.isConnected()) return
        // Direct call — streamer is thread-safe and does its own throttling; avoid per-hop coroutine flood
        streamer.onPcm(hop, sampleRate)
    }

    fun release() {
        disconnectInternal()
    }

    private fun connectInternal() {
        // Bring BNMV to foreground first to avoid ForegroundServiceStartNotAllowed on CONNECT_UDP.
        // START via ExternalControlReceiver triggers TrampolineActivity, but from background it may be
        // blocked on Android 14. Launch TrampolineActivity directly as well.
        try {
            val trampoline = Intent().apply {
                setClassName(
                    BnmvConstants.PACKAGE_NAME,
                    "com.better.nothing.music.vizualizer.ui.TrampolineActivity",
                )
                putExtra("extra_start_source", "viz_started_external")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(trampoline)
            android.util.Log.i(TAG, "Launched BNMV TrampolineActivity directly")
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Launch Trampoline failed, fallback to getLaunchIntent", e)
            try {
                val launch = appContext.packageManager.getLaunchIntentForPackage(BnmvConstants.PACKAGE_NAME)
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    appContext.startActivity(launch)
                    android.util.Log.i(TAG, "Launched BNMV via getLaunchIntentForPackage")
                }
            } catch (e2: Exception) {
                android.util.Log.w(TAG, "Launch BNMV failed", e2)
            }
        }
        BnmvController.start(appContext)
        // Apply source/preset eagerly (will be re-sent after handshake as well)
        BnmvController.setSource(appContext, BnmvConstants.Source.NETWORK)
        streamer.setPreset(presetKey)
        // Small delay to let BNMV come to foreground before we request NETWORK
        scope.launch {
            kotlinx.coroutines.delay(900)
            if (enabled) streamer.connect()
        }
        // Poll connection state for UI
        handshakePollJob?.cancel()
        handshakePollJob = scope.launch(Dispatchers.Main) {
            // Poll every 600ms for up to 8s, then keep polling every 2s while enabled
            var ticks = 0
            while (enabled) {
                val connected = streamer.isConnected()
                _isConnected.value = connected
                streamer.setStreamingEnabled(connected && enabled)
                ticks++
                val delayMs = if (ticks < 14) 600L else 2000L
                delay(delayMs)
                // Refresh install state periodically too
                if (ticks % 5 == 0) _isInstalled.value = BnmvController.isInstalled(appContext)
            }
        }
    }

    private fun disconnectInternal() {
        handshakePollJob?.cancel()
        handshakePollJob = null
        _isConnected.value = false
        streamer.disconnect()
        // Don't call BNMV stop on disable — user may want it to keep running with other sources.
        // Only stop streaming; BNMV will remain on NETWORK source until user changes it.
    }

    companion object {
        private const val TAG = "BnmvIntegration"
    }
}
