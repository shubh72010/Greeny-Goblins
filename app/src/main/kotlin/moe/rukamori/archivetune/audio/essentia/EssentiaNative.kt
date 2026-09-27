/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.audio.essentia

/**
 * Thin bridge over the Essentia native build (BeatTrackerDegara + KeyExtractor).
 *
 * Wire format from C++ is `bpm|key|scale`, e.g. `120|C|major`. The stub build
 * (ABIs without a libessentia.so prebuilt) returns `0|||<reason>`.
 *
 * The external name MUST stay `nativeAnalyze`: it maps to the exported JNI
 * symbol `Java_..._EssentiaNative_nativeAnalyze` (see app/src/main/cpp/).
 * A previous `analyze` declaration matched no symbol and every call died
 * with UnsatisfiedLinkError — the whole Essentia path was silently dead.
 *
 * Essentia is AGPL-3.0, shipped as a dynamically-linked prebuilt .so.
 * GPLv3 §13 permits combining GPLv3 project code with AGPLv3 Essentia; the
 * distributed combination is then subject to AGPLv3's additional terms
 * (notably the §13 Corresponding Source offer). For this locally-run app
 * that cashes out as a release checklist, not a relicense: ship the AGPLv3
 * text, Corresponding Source for the exact libessentia build (version +
 * source location, see app/src/main/cpp/ESSENTIA_PROVENANCE.md), preserved
 * copyright notices, and an Essentia entry in the OSS-licenses screen.
 */
object EssentiaNative {
    private var loadError: String? = null

    init {
        try {
            System.loadLibrary("essentia_jni")
        } catch (t: UnsatisfiedLinkError) {
            loadError = t.message ?: "Library not loaded"
        } catch (t: Throwable) {
            loadError = t.message ?: "Unknown init error"
        }
    }

    /** False on ABIs/devices without the .so (x86 emulator, unit tests) — caller falls back to JVM DSP. */
    val isAvailable: Boolean
        get() = loadError == null

    private external fun nativeAnalyze(samples: FloatArray, sampleRate: Int): String

    /**
     * Essentia's global init/compute/shutdown is not thread-safe, and the
     * analysis scheduler can run up to 3 lanes concurrently — so native calls
     * are serialized here. Never remove this lock without making the C++
     * side re-entrant first.
     */
    private val nativeLock = Any()

    data class EssentiaResult(
        val bpm: Int,
        val key: String?,
        val scale: String?,
        val error: String?,
    )

    fun analyzeTrack(pcm: ShortArray, sampleRate: Int): EssentiaResult {
        val initError = loadError
        if (initError != null) {
            return EssentiaResult(0, null, null, initError)
        }
        return try {
            val floats = FloatArray(pcm.size) { pcm[it] / 32768f }
            val raw = synchronized(nativeLock) { nativeAnalyze(floats, sampleRate) }
            parseResult(raw)
        } catch (t: UnsatisfiedLinkError) {
            loadError = t.message ?: "Library not loaded"
            EssentiaResult(0, null, null, loadError)
        } catch (t: Throwable) {
            EssentiaResult(0, null, null, t.message ?: "Unknown error")
        }
    }

    /** Internal (not private) so unit tests can pin the wire format without loading native code. */
    internal fun parseResult(raw: String): EssentiaResult {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return EssentiaResult(0, null, null, "Empty result from native layer")
        val parts = trimmed.split("|")
        if (parts.size != 3) return EssentiaResult(0, null, null, "Unexpected result format: $trimmed")
        val bpm = parts[0].trim().toFloatOrNull()?.toInt() ?: 0
        if (bpm <= 0) {
            val reason = parts[2].trim().ifEmpty { "native analysis failed" }
            return EssentiaResult(0, null, null, reason)
        }
        return EssentiaResult(
            bpm = bpm,
            key = parts[1].trim().ifEmpty { null },
            scale = parts[2].trim().ifEmpty { null },
            error = null,
        )
    }
}
