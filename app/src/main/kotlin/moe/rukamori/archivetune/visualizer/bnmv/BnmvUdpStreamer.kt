package moe.rukamori.archivetune.visualizer.bnmv

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.visualizer.AudioProcessor
import timber.log.Timber

/**
 * Streams 512-bin log FFT to the external BNMV app via UDP.
 *
 * Flow:
 * 1) [connect] sends ACTION_CONNECT_UDP with local IP and a listen port (default 8888).
 * 2) Listens on that port for a single "BNMV_DISCOVER" packet to learn BNMV's IP.
 * 3) After handshake, exposes [onPcm] to be called from ExoPlayer's audio thread (via BngvTeeAudioProcessor).
 *    PCM is converted to 512 log bins (30Hz-16kHz) using [AudioProcessor] and packed into 768 bytes,
 *    then sent to <bnmvIp>:8889 throttled to ~60fps (min 16ms between sends).
 *
 * The streamer owns its own [AudioProcessor] and sockets; it does NOT drive glyph/haptic/torch locally.
 */
class BnmvUdpStreamer(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val audioProcessor = AudioProcessor()
    private var handshakeJob: Job? = null
    private var handshakeSocket: DatagramSocket? = null
    private var streamingSocket: DatagramSocket? = null

    private val bnmvAddress: AtomicReference<InetAddress?> = AtomicReference(null)
    private val isConnected = AtomicBoolean(false)
    private val isStreaming = AtomicBoolean(false)

    // Throttle to 60fps — use monotonic clock, thread-safe
    @Volatile private var lastSendMs = 0L
    private val packLock = Any()

    fun isConnected(): Boolean = isConnected.get()
    fun bnmvIp(): String? = bnmvAddress.get()?.hostAddress

    /**
     * Start handshake. Call when BNMV external integration is enabled / playback starts.
     * Safe to call multiple times; restarts previous attempt.
     */
    @Synchronized
    fun connect() {
        // Cancel any in-flight handshake; allow reconnect if not yet connected
        if (isConnected.get() && handshakeJob?.isActive != true) {
            // Already connected — just ensure source/mode
            val ip = getLocalIpAddress(context)
            if (ip != null) BnmvController.connectUdp(context, ip, BnmvConstants.HANDSHAKE_LISTEN_PORT)
            return
        }
        handshakeJob?.cancel()
        closeHandshakeSocket()
        bnmvAddress.set(null)
        isConnected.set(false)

        val localIp = getLocalIpAddress(context)
        if (localIp == null) {
            Timber.tag(TAG).w("No local IP — cannot connect to BNMV")
            return
        }
        Timber.tag(TAG).d("Local IP $localIp, sending CONNECT_UDP")
        BnmvController.connectUdp(context, localIp, BnmvConstants.HANDSHAKE_LISTEN_PORT)
        // Also set source to NETWORK explicitly; CONNECT_UDP does this but be explicit for older BNMV builds
        BnmvController.setSource(context, BnmvConstants.Source.NETWORK)

        handshakeJob = scope.launch(Dispatchers.IO) {
            listenForHandshake()
        }
    }

    fun disconnect() {
        handshakeJob?.cancel()
        handshakeJob = null
        closeHandshakeSocket()
        closeStreamingSocket()
        bnmvAddress.set(null)
        isConnected.set(false)
        isStreaming.set(false)
        lastSendMs = 0L
    }

    /** Called from audio thread via BngvTeeAudioProcessor -> MusicService -> here (posted to IO). */
    fun onPcm(hop: ShortArray, sampleRate: Int) {
        val target = bnmvAddress.get() ?: return
        if (!isStreaming.get()) return
        // Throttle to ~60fps using monotonic clock
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastSendMs < 16) return
        lastSendMs = now

        try {
            // AudioProcessor is not thread-safe for concurrent calls — guard with synchronized
            val fft: IntArray
            synchronized(audioProcessor) {
                audioProcessor.updateFFTSize(sampleRate)
                val result = audioProcessor.processAudioFrame(hop, AudioProcessor.SourceType.INTERNAL, 0.85f)
                    ?: return
                fft = result.fftraw // 512 x 0..4095
            }
            // Pack into fresh buffer to avoid sharing mutable array across threads/sends
            val packed = ByteArray(BnmvConstants.PACKET_SIZE)
            pack(fft, packed)
            sendUdp(target, packed)
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "onPcm failed")
        }
    }

    fun setStreamingEnabled(enabled: Boolean) {
        isStreaming.set(enabled)
        if (enabled && bnmvAddress.get() != null) {
            BnmvController.start(context)
        }
    }

    fun setPreset(presetKey: String) {
        BnmvController.setPreset(context, presetKey)
    }

    fun setFeatureEnabled(action: String, enabled: Boolean) {
        BnmvController.toggleFeature(context, action, enabled)
    }

    // Actual port we successfully bound for handshake (may differ from 8888 if occupied by BNMV host)
    @Volatile private var actualHandshakePort: Int = BnmvConstants.HANDSHAKE_LISTEN_PORT

    private suspend fun listenForHandshake() = withContext(Dispatchers.IO) {
        var socket: DatagramSocket? = null
        var boundPort = BnmvConstants.HANDSHAKE_LISTEN_PORT
        // Try to bind to 8888, fallback to alternative ports if occupied (BNMV host already on 8888)
        val candidates = intArrayOf(
            BnmvConstants.HANDSHAKE_LISTEN_PORT, 8887, 8892, 8893, 0 // 0 = ephemeral
        )
        var lastException: IOException? = null
        for (port in candidates) {
            try {
                val s = if (port == 0) DatagramSocket() else DatagramSocket(port).apply { reuseAddress = true }
                s.soTimeout = 3500
                if (port != 0) s.reuseAddress = true
                socket = s
                boundPort = s.localPort
                actualHandshakePort = boundPort
                android.util.Log.i(TAG, "Handshake socket bound to $boundPort (requested $port)")
                break
            } catch (e: IOException) {
                lastException = e
                android.util.Log.w(TAG, "Port $port busy, trying next: ${e.message}")
            }
        }
        if (socket == null) {
            Timber.tag(TAG).w(lastException, "All handshake ports busy — giving up")
            android.util.Log.e(TAG, "All handshake ports busy", lastException)
            return@withContext
        }
        handshakeSocket = socket
        // If we bound to a non-default port, re-send CONNECT_UDP with the actual port so BNMV knows where to reply
        if (boundPort != BnmvConstants.HANDSHAKE_LISTEN_PORT) {
            val ip = getLocalIpAddress(context)
            if (ip != null) {
                android.util.Log.i(TAG, "Re-sending CONNECT_UDP with actual port $boundPort to $ip")
                BnmvController.connectUdp(context, ip, boundPort)
            }
        }
        val buf = ByteArray(1024)
        val packet = DatagramPacket(buf, buf.size)
        var attempts = 0
        while (isActive && attempts < 5) {
            attempts++
            try {
                socket.receive(packet)
                val msg = String(packet.data, 0, packet.length, Charsets.UTF_8).trim()
                android.util.Log.i(TAG, "Handshake got '$msg' from ${packet.address.hostAddress}:${packet.port} on local $boundPort")
                Timber.tag(TAG).d("Handshake got '$msg' from ${packet.address.hostAddress}")
                if (msg == BnmvConstants.HANDSHAKE_MESSAGE) {
                    bnmvAddress.set(packet.address)
                    isConnected.set(true)
                    isStreaming.set(true)
                    ensureStreamingSocket()
                    BnmvController.start(context)
                    android.util.Log.i(TAG, "BNMV connected at ${packet.address.hostAddress}, streaming to ${packet.address.hostAddress}:${BnmvConstants.STREAM_TARGET_PORT}")
                    Timber.tag(TAG).i("BNMV connected at ${packet.address.hostAddress}")
                    break
                }
            } catch (e: SocketTimeoutException) {
                // Retry broadcast — BNMV may have missed it
                if (attempts < 5) {
                    val ip = getLocalIpAddress(context)
                    if (ip != null) BnmvController.connectUdp(context, ip, boundPort)
                }
                android.util.Log.w(TAG, "Handshake timeout $attempts/5 on port $boundPort")
                Timber.tag(TAG).d("Handshake timeout attempt $attempts/5")
            }
        }
            if (!isConnected.get()) {
                android.util.Log.w(TAG, "BNMV handshake failed after 5 attempts on port $boundPort — falling back to loopback streaming")
                Timber.tag(TAG).w("BNMV handshake failed after 5 attempts — fallback")
                // Fallback for same-device: BNMV and JusPlayer share 127.0.0.1 / 192.168.1.41
                // Start streaming directly to loopback and to our own WiFi IP on 8889, so glyphs still work
                try {
                    val fallbackTargets = listOfNotNull(
                        runCatching { InetAddress.getByName("127.0.0.1") }.getOrNull(),
                        runCatching { InetAddress.getByName(getLocalIpAddress(context) ?: "127.0.0.1") }.getOrNull(),
                    ).distinctBy { it.hostAddress }
                    // Prefer 127.0.0.1 for same-device; it always reaches BNMV's 0.0.0.0:8889 listener
                    val primary = fallbackTargets.firstOrNull() ?: InetAddress.getByName("127.0.0.1")
                    bnmvAddress.set(primary)
                    isConnected.set(true)
                    isStreaming.set(true)
                    ensureStreamingSocket()
                    BnmvController.start(context)
                    android.util.Log.i(TAG, "BNMV fallback connected to ${primary.hostAddress}:${BnmvConstants.STREAM_TARGET_PORT} (loopback)")
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Fallback connect failed", e)
                }
            }
            // Keep socket open a bit for late handshakes
            if (!isConnected.get()) {
                delay(2000)
                closeHandshakeSocket()
            } else {
                delay(1000)
                closeHandshakeSocket()
            }
    }

    @Synchronized
    private fun ensureStreamingSocket() {
        if (streamingSocket == null || streamingSocket?.isClosed == true) {
            try {
                streamingSocket = DatagramSocket().apply { broadcast = true }
            } catch (e: IOException) {
                Timber.tag(TAG).w(e, "ensureStreamingSocket failed")
            }
        }
    }

    private fun sendUdp(target: InetAddress, data: ByteArray) {
        try {
            val s = synchronized(this) {
                var sock = streamingSocket
                if (sock == null || sock.isClosed) {
                    sock = try { DatagramSocket().apply { broadcast = true } } catch (e: IOException) {
                        Timber.tag(TAG).w(e, "sendUdp socket create failed")
                        return
                    }
                    streamingSocket = sock
                }
                sock
            }
            val pkt = DatagramPacket(data, data.size, target, BnmvConstants.STREAM_TARGET_PORT)
            s.send(pkt)
        } catch (e: IOException) {
            Timber.tag(TAG).w(e, "UDP send failed")
        }
    }

    @Synchronized
    private fun closeHandshakeSocket() {
        try { handshakeSocket?.close() } catch (_: Exception) {}
        handshakeSocket = null
    }

    @Synchronized
    private fun closeStreamingSocket() {
        try { streamingSocket?.close() } catch (_: Exception) {}
        streamingSocket = null
    }

    companion object {
        private const val TAG = "BnmvUdpStreamer"

        /** Best-effort local IPv4. Prefers Wi-Fi / active network. */
        fun getLocalIpAddress(context: Context): String? {
            // Try ConnectivityManager first for active network
            try {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val caps = cm.getNetworkCapabilities(cm.activeNetwork)
                if (caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                    // Still need actual IP; fall through to interface scan with preference for wifi
                }
            } catch (_: Exception) {}
            // Scan interfaces: prefer site-local (192.168.x.x etc) up & not loopback
            try {
                val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
                var candidate: String? = null
                for (intf in interfaces) {
                    if (!intf.isUp || intf.isLoopback) continue
                    for (addr in Collections.list(intf.inetAddresses)) {
                        if (addr.isLoopbackAddress) continue
                        if (addr is java.net.Inet4Address) {
                            val host = addr.hostAddress ?: continue
                            if (addr.isSiteLocalAddress) return host // 192.168 / 10. — best
                            if (candidate == null) candidate = host
                        }
                    }
                }
                return candidate
            } catch (e: Exception) {
                Log.w(TAG, "getLocalIpAddress failed", e)
            }
            return null
        }

        /** Pack 512 x 12-bit values into 768 bytes: 2 values -> 3 bytes */
        fun pack(logBins: IntArray, out: ByteArray) {
            require(logBins.size == BnmvConstants.BINS)
            require(out.size == BnmvConstants.PACKET_SIZE)
            for (i in 0 until 256) {
                val v1 = logBins[i * 2] and 0xFFF
                val v2 = logBins[i * 2 + 1] and 0xFFF
                out[i * 3] = (v1 and 0xFF).toByte()
                out[i * 3 + 1] = (((v1 shr 8) and 0x0F) or ((v2 shl 4) and 0xF0)).toByte()
                out[i * 3 + 2] = ((v2 shr 4) and 0xFF).toByte()
            }
        }
    }
}
