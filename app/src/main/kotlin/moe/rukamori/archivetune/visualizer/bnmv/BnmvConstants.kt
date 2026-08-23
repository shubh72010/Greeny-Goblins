package moe.rukamori.archivetune.visualizer.bnmv

/**
 * Intent actions and extras for the external Better Nothing Music Visualizer (BNMV) app.
 * JusPlayer does NOT bundle BNMV; it communicates with the independently installed app
 * via explicit broadcasts (control) and UDP (60fps FFT streaming).
 *
 * @see <a href="https://github.com/rukamori/better-nothing-music-visualizer">BNMV docs</a>
 */
object BnmvConstants {
    const val PACKAGE_NAME = "com.better.nothing.music.vizualizer"
    const val GITHUB_RELEASES_URL = "https://github.com/Aleks-Levet/better-nothing-music-visualizer/releases"
    /** Kept for compat — now points to GitHub releases (source of truth) */
    const val PLAY_STORE_URL = GITHUB_RELEASES_URL
    const val DOWNLOAD_URL = GITHUB_RELEASES_URL

    // Playback / lifecycle
    const val ACTION_START = "com.better.nothing.music.vizualizer.ACTION_START"
    const val ACTION_STOP = "com.better.nothing.music.vizualizer.ACTION_STOP"
    const val ACTION_TOGGLE = "com.better.nothing.music.vizualizer.ACTION_TOGGLE"

    // Feature toggles — support optional boolean extra "enabled"
    const val ACTION_TOGGLE_GLYPHS = "com.better.nothing.music.vizualizer.ACTION_TOGGLE_GLYPHS"
    const val ACTION_TOGGLE_HAPTICS = "com.better.nothing.music.vizualizer.ACTION_TOGGLE_HAPTICS"
    const val ACTION_TOGGLE_TORCH = "com.better.nothing.music.vizualizer.ACTION_TOGGLE_TORCH"
    const val ACTION_TOGGLE_BROADCAST = "com.better.nothing.music.vizualizer.ACTION_TOGGLE_BROADCAST"
    const val ACTION_TOGGLE_OVERLAY = "com.better.nothing.music.vizualizer.ACTION_TOGGLE_OVERLAY"
    const val ACTION_TOGGLE_EDGE = "com.better.nothing.music.vizualizer.ACTION_TOGGLE_EDGE"
    const val ACTION_TOGGLE_LENS = "com.better.nothing.music.vizualizer.ACTION_TOGGLE_LENS"

    // Source & preset
    const val ACTION_SET_SOURCE = "com.better.nothing.music.vizualizer.ACTION_SET_SOURCE"
    const val ACTION_SET_PRESET = "com.better.nothing.music.vizualizer.ACTION_SET_PRESET"
    const val EXTRA_SOURCE = "source"
    const val EXTRA_PRESET = "preset"
    const val EXTRA_ENABLED = "enabled"

    // UDP handshake
    const val ACTION_CONNECT_UDP = "com.better.nothing.music.vizualizer.ACTION_CONNECT_UDP"
    const val EXTRA_IP = "ip"
    const val EXTRA_PORT = "port"

    // UDP protocol
    const val HANDSHAKE_MESSAGE = "BNMV_DISCOVER"
    const val HANDSHAKE_LISTEN_PORT = 8888
    const val STREAM_TARGET_PORT = 8889
    const val PACKET_SIZE = 768 // 512 bins *12bit /8 = 768 bytes
    const val BINS = 512
    const val F_MIN_HZ = 30f
    const val F_MAX_HZ = 16000f

    object Source {
        const val INTERNAL = "INTERNAL"
        const val MIC = "MIC"
        const val VIZUALIZER = "VIZUALIZER"
        const val NETWORK = "NETWORK"
    }
}
