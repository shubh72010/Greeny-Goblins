/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.audio.analysis

import org.jtransforms.fft.DoubleFFT_1D
import timber.log.Timber
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sqrt
import moe.rukamori.archivetune.audio.essentia.EssentiaNative

/**
 * Offline DSP over a captured 3s mono window. Pure JVM, no Android dependency.
 * Operates on fixed 16 kHz mono (linear-interp resample from the tee rate).
 *
 * [analyzeLongContext] is the single full-context pass (tempo + key over up to
 * 120s at the 80% checkpoint); [analyze] stays the 3s-window spectral path.
 * Both share the same STFT front-end — no second pipeline.
 */
object DspAnalyzer {
    const val TARGET_RATE = 16_000
    private const val FRAME = 2048
    private const val HOP = 1024
    private const val MIN_BPM = 60f
    private const val MAX_BPM = 200f
    private const val COMB_MAX_ELEM = 4

    /** Linear-interp resample, mono shorts. Locked choice for this phase: cheap + sufficient. */
    fun resampleLinearMono(input: ShortArray, fromRate: Int): ShortArray {
        if (fromRate == TARGET_RATE || input.isEmpty()) return input
        val ratio = fromRate.toDouble() / TARGET_RATE
        val outSize = (input.size / ratio).toInt().coerceAtLeast(1)
        return ShortArray(outSize) { i ->
            val pos = i * ratio
            val idx = pos.toInt().coerceIn(0, input.size - 1)
            val frac = (pos - idx).toFloat()
            val a = input[idx].toFloat()
            val b = input[(idx + 1).coerceIn(0, input.size - 1)].toFloat()
            (a + (b - a) * frac).toInt().coerceIn(-32768, 32767).toShort()
        }
    }

    /** Shared STFT front-end: Hann 2048/1024, magnitude spectra per frame. */
    internal fun stftMagnitudes(x: FloatArray): List<FloatArray> {
        val fft = DoubleFFT_1D(FRAME.toLong())
        val hann = DoubleArray(FRAME) { 0.5 * (1 - cos(2 * PI * it / (FRAME - 1))) }
        val mags = mutableListOf<FloatArray>()
        var pos = 0
        while (pos + FRAME <= x.size) {
            val buf = DoubleArray(FRAME) { x[pos + it] * hann[it] }
            fft.realForward(buf)
            val m = FloatArray(FRAME / 2 + 1)
            m[0] = buf[0].toFloat()
            m[FRAME / 2] = buf[1].toFloat()
            for (k in 1 until FRAME / 2) {
                val re = buf[2 * k]
                val im = buf[2 * k + 1]
                m[k] = sqrt(re * re + im * im).toFloat() / (FRAME / 2)
            }
            mags.add(m)
            pos += HOP
        }
        return mags
    }

    /** Positive spectral-difference onset envelope, one value per frame. */
    internal fun fluxEnvelope(mags: List<FloatArray>): FloatArray {
        val envelope = FloatArray(mags.size)
        for (fi in 1 until mags.size) {
            val m = mags[fi]
            var flux = 0f
            for (k in m.indices) {
                val d = m[k] - mags[fi - 1][k]
                if (d > 0) flux += d * d
            }
            envelope[fi] = flux / m.size
        }
        return envelope
    }

    data class LongContextResult(
        val bpm: Float,
        val rhythmicity: Float,
        val key: Int?,
        /** "major"/"minor" from Essentia; null when the JVM key estimator (mode-less) won. */
        val scale: String? = null,
        val analysisSource: String = "none",
        val error: String? = null,
    )

    data class TempoCandidate(val lag: Int, val bpm: Float, val score: Float)

    data class TempoEstimate(val bpm: Float, val rhythmicity: Float, val candidates: List<TempoCandidate>)

    /**
     * Single full-context pass over up to 120s of accumulated 16 kHz mono.
     * Abstains (zeros) below [LongContextBuffer.MIN_CONTEXT_SEC] — the caller
     * then keeps the 3s-window means. Same front-end as [analyze].
     */
    fun analyzeLongContext(pcm16kMono: ShortArray): LongContextResult {
        if (pcm16kMono.size < TARGET_RATE * LongContextBuffer.MIN_CONTEXT_SEC) {
            return LongContextResult(0f, 0f, null)
        }
        val x = FloatArray(pcm16kMono.size) { pcm16kMono[it] / 32768f }
        peakNormalize(x)
        val mags = stftMagnitudes(x)
        if (mags.size < 8) return LongContextResult(0f, 0f, null)
        val envelope = fluxEnvelope(mags)
        val estimate = estimateTempoV3(envelope, HOP.toFloat() / TARGET_RATE)
        val (bpm, rhythmicity, candidates) = estimate

        val key = estimateKey(mags, TARGET_RATE.toFloat() / FRAME)
        return LongContextResult(bpm, rhythmicity, key)
    }

    /**
     * Tempo/key with cross-engine octave resolution: Essentia (BeatTrackerDegara
     * + KeyExtractor) checked against the pure-JVM long-context pass
     * (comb-filter tempo + Krumhansl key). Beat trackers classically double —
     * 8th-note energy mistaken for the beat (e.g. 96 BPM read as ~187) — so
     * two estimates an octave apart resolve to the lower: true >170 BPM is
     * rare, doublings are not. [analysisSource] names the engine whose tempo
     * survived, and the key follows it.
     *
     * Half-time readings are reported as detected (88 for a track published as
     * 87 half-time / 174 double-time — both legitimate, so no genre prior is
     * applied; promoting would mislabel genuinely slow material).
     */
    fun analyzeTempoKey(pcm16kMono: ShortArray): LongContextResult {
        if (pcm16kMono.size < TARGET_RATE * LongContextBuffer.MIN_CONTEXT_SEC) {
            return LongContextResult(0f, 0f, null)
        }
        // Always run the JVM pass: ~200ms, thread-safe, and its comb-filter
        // tempo independently confirms (or octave-corrects) Essentia.
        val jvm = analyzeLongContext(pcm16kMono)
        var essErr: String? = if (EssentiaNative.isAvailable) null else "native lib not loaded"
        val ess = if (EssentiaNative.isAvailable) {
            EssentiaNative.analyzeTrack(pcm16kMono, TARGET_RATE).also {
                essErr = it.error
            }.takeIf { it.error == null && it.bpm > 0 }
        } else {
            null
        }
        // Breadcrumb for on-device diagnosis (logcat -s DspAnalyzer): shows which
        // engine delivered and what the loser said. Timber is a no-op in unit tests.
        Timber.d(
            "tempoKey essBpm=%s jvmBpm=%.2f essErr=%s",
            ess?.bpm?.toString() ?: "–",
            jvm.bpm,
            essErr ?: "–",
        )
        if (ess != null && jvm.bpm > 0) {
            val agreed = resolveOctave(ess.bpm.toFloat(), jvm.bpm)
            if (agreed != null) {
                val essKept = kotlin.math.abs(ess.bpm - agreed) / agreed < 0.06f
                return LongContextResult(
                    bpm = agreed,
                    rhythmicity = jvm.rhythmicity,
                    key = if (essKept) noteNameToPitchClass(ess.key) ?: jvm.key else jvm.key,
                    scale = ess.scale?.takeIf { essKept },
                    analysisSource = if (essKept) "essentia" else "jvm",
                )
            }
            // Unrelated estimates: trust the primary (Essentia).
            return LongContextResult(
                bpm = ess.bpm.toFloat(),
                rhythmicity = jvm.rhythmicity,
                key = noteNameToPitchClass(ess.key) ?: jvm.key,
                scale = ess.scale,
                analysisSource = "essentia",
            )
        }
        // One engine abstained (arrhythmic/ambient for JVM, error for Essentia):
        // take whichever actually delivered a tempo, never zeros-with-a-source.
        // The Essentia error rides along so the UI can show WHY we fell back.
        if (ess != null) {
            return LongContextResult(
                bpm = ess.bpm.toFloat(),
                rhythmicity = 0f,
                key = noteNameToPitchClass(ess.key),
                scale = ess.scale,
                analysisSource = "essentia",
            )
        }
        if (jvm.bpm > 0) return jvm.copy(analysisSource = "jvm", error = essErr)
        return jvm.copy(error = essErr)
    }

    /**
     * Internal (not private) so unit tests can pin the octave policy without audio.
     * Returns the agreed tempo, or null when the estimates are unrelated.
     */
    internal fun resolveOctave(essBpm: Float, jvmBpm: Float): Float? {
        if (essBpm <= 0 || jvmBpm <= 0) return null
        val hi = maxOf(essBpm, jvmBpm)
        val lo = minOf(essBpm, jvmBpm)
        return when (val ratio = hi / lo) {
            in 0f..1.06f -> (essBpm + jvmBpm) / 2 // agree: average absorbs jitter
            in 1.88f..2.12f -> lo // octave apart: doublings beat true-fast
            else -> null // unrelated (ratio in 1.06..1.88 or >2.12)
        }
    }

    private fun noteNameToPitchClass(note: String?): Int? {
        if (note == null) return null
        val normalized = note
            .replace(Regex("\\d+"), "")
            .replace("Db", "C#")
            .replace("Eb", "D#")
            .replace("Fb", "E")
            .replace("Gb", "F#")
            .replace("Ab", "G#")
            .replace("Bb", "A#")
            .replace("Cb", "B")
        return when (normalized) {
            "C" -> 0
            "C#" -> 1
            "D" -> 2
            "D#" -> 3
            "E" -> 4
            "F" -> 5
            "F#" -> 6
            "G" -> 7
            "G#" -> 8
            "A" -> 9
            "A#" -> 10
            "B" -> 11
            else -> null
        }
    }

    fun analyze(window16kMono: ShortArray): DspSample {
        if (window16kMono.size < FRAME) return silent()
        val x = FloatArray(window16kMono.size) { window16kMono[it] / 32768f }
        peakNormalize(x)
        val rms = sqrt(x.sumOf { (it * it).toDouble() }.toFloat() / x.size).coerceIn(0f, 1f)

        val mags = stftMagnitudes(x)
        if (mags.size < 3) return silent(rms)
        val hzPerBin = TARGET_RATE.toFloat() / FRAME

        var bassSum = 0f
        var midsSum = 0f
        var trebleSum = 0f
        var centroidSum = 0f
        var fluxSum = 0f
        val envelope = fluxEnvelope(mags)
        for (fi in mags.indices) {
            val m = mags[fi]
            var tot = 0f
            var wSum = 0f
            for (k in m.indices) {
                val hz = k * hzPerBin
                val v = m[k]
                tot += v
                wSum += v * hz
                when {
                    hz < 250 -> bassSum += v
                    hz < 2_000 -> midsSum += v
                    hz < 8_000 -> trebleSum += v
                }
            }
            if (tot > 0) centroidSum += wSum / tot
            if (fi > 0) fluxSum += envelope[fi]
        }
        val frames = mags.size.toFloat()
        val bandTot = (bassSum + midsSum + trebleSum).takeIf { it > 0 } ?: 1f
        val bass = (bassSum / bandTot).coerceIn(0f, 1f)
        val mids = (midsSum / bandTot).coerceIn(0f, 1f)
        val treble = (trebleSum / bandTot).coerceIn(0f, 1f)
        val brightness = (centroidSum / frames / (TARGET_RATE / 2)).coerceIn(0f, 1f)
        val spectralFlux = (fluxSum / frames * 40f).coerceIn(0f, 1f)

        val hopSec = HOP.toFloat() / TARGET_RATE
        val onsets = pickOnsets(envelope)
        val onsetDensity = (onsets.size / (x.size.toFloat() / TARGET_RATE) / 8f).coerceIn(0f, 1f)
        // 3s-window BPM/key disabled during Essentia validation.
        val (bpm, rhythmicity) = 0f to 0f
        val windowKey = null
        return DspSample(
            rms = rms,
            bass = bass,
            mids = mids,
            treble = treble,
            brightness = brightness,
            spectralFlux = spectralFlux,
            onsetDensity = onsetDensity,
            rhythmicity = rhythmicity,
            bpm = bpm,
            key = windowKey,
        )
    }

    private fun silent(rms: Float = 0f) =
        DspSample(rms, 0.33f, 0.33f, 0.33f, 0f, 0f, 0f, 0f, 0f, null)

    private fun peakNormalize(x: FloatArray) {
        var peak = 0f
        for (v in x) {
            val a = kotlin.math.abs(v)
            if (a > peak) peak = a
        }
        if (peak > 1e-6f) for (i in x.indices) x[i] /= peak
    }

    private fun pickOnsets(envelope: FloatArray): List<Int> {
        if (envelope.size < 5) return emptyList()
        val mean = envelope.average().toFloat()
        val std = sqrt(envelope.sumOf { ((it - mean) * (it - mean)).toDouble() }.toFloat() / envelope.size)
        val thresh = mean + 1.2f * std
        val out = mutableListOf<Int>()
        var last = -10
        for (i in 2 until envelope.size - 2) {
            if (envelope[i] > thresh && envelope[i] >= envelope[i - 1] && envelope[i] > envelope[i + 1] && i - last > 3) {
                out.add(i)
                last = i
            }
        }
        return out
    }

    // Internal (not private) so the tempo regression test can feed a proven-divergent
    // envelope straight into the estimator, bypassing the STFT front-end.
    internal fun estimateTempo(envelope: FloatArray, hopSec: Float): Pair<Float, Float> {
        if (envelope.size < 8) return 0f to 0f
        val mean = envelope.average().toFloat()
        val centered = FloatArray(envelope.size) { envelope[it] - mean }
        val minLag = (60f / MAX_BPM / hopSec).toInt().coerceAtLeast(1)
        val maxLag = (60f / MIN_BPM / hopSec).toInt().coerceAtMost(envelope.size - 1)
        if (maxLag <= minLag) return 0f to 0f
        var energy = 0f
        for (v in centered) energy += v * v
        if (energy <= 1e-9f) return 0f to 0f
        var bestLag = -1
        var bestCorr = 0f
        for (lag in minLag..maxLag) {
            var c = 0f
            for (i in 0 until envelope.size - lag) c += centered[i] * centered[i + lag]
            val overlap = envelope.size - lag
            c = c * envelope.size / (energy * overlap)
            if (c > bestCorr) {
                bestCorr = c
                bestLag = lag
            }
        }
        val rhythmicity = bestCorr.coerceIn(0f, 1f)
        if (bestLag < 0 || rhythmicity < 0.12f) return 0f to rhythmicity
        var bpm = 60f / (bestLag * hopSec)
        while (bpm < MIN_BPM) bpm *= 2
        while (bpm > MAX_BPM) bpm /= 2
        return bpm to rhythmicity
    }

    // v3 long-context tempo estimator: resonating comb filter on the onset envelope
    // (madmom / Böck & Krebs 2015 approach, simplified for offline use).
    // Only used by analyzeLongContext; the 3s-window path keeps the original
    // single-peak autocorrelation.
    //
    // For each candidate lag τ, run an IIR comb filter y[n] = α·y[n-τ] + (1-α)·x[n]
    // over the entire envelope and measure output energy. A period that matches the
    // underlying rhythm resonates strongly; competing periodicities (subdivisions,
    // half-time) produce weaker resonance because their feedback does not align with
    // the dominant beat pulses. This resolves octave/half-time ambiguity without
    // hard-coded corrections.
    // Reference: Sebastian Böck, Florian Krebs, Gerhard Widmer,
    // "Accurate Tempo Estimation based on Recurrent Neural Networks and Resonating
    // Comb Filters", ISMIR 2015. See madmom.features.tempo.
    internal fun estimateTempoV3(envelope: FloatArray, hopSec: Float): TempoEstimate {
        if (envelope.size < 8) return TempoEstimate(0f, 0f, emptyList())
        val minLag = (60f / MAX_BPM / hopSec).toInt().coerceAtLeast(1)
        val maxLag = (60f / MIN_BPM / hopSec).toInt().coerceAtMost(envelope.size - 1)
        if (maxLag <= minLag) return TempoEstimate(0f, 0f, emptyList())

        // Comb filter feedback factor. 0.79 is the madmom default; higher values
        // give sharper resonance but need longer context to build up.
        val alpha = 0.79f
        val y = FloatArray(envelope.size)
        val inputEnergy = envelope.sumOf { (it * it).toDouble() }.toFloat()
        val raw = FloatArray(maxLag + 1)

        for (lag in minLag..maxLag) {
            y.fill(0f)
            var energy = 0f
            for (n in envelope.indices) {
                val feedback = if (n >= lag) y[n - lag] else 0f
                y[n] = envelope[n] + alpha * feedback
                energy += y[n] * y[n]
            }
            // Normalize by input energy so rhythmicity is roughly in [0, 1+].
            // Resonant signals can exceed 1 because of feedback buildup.
            raw[lag] = if (inputEnergy > 1e-9f) energy / inputEnergy else 0f
        }

        // Octave fold-down: subdivisions (8th-note energy read as the beat) resonate
        // at least as strongly as the true period, so a bare argmax doubles.
        // Step to the slower octave while it holds >=85% of the faster peak's
        // energy — chained, so 4x doublings fold twice. Mainstream prior: true
        // >170 BPM is rarer than a doubling; documented, accept the trade.
        var best = (minLag..maxLag).maxBy { raw[it] }
        while (best * 2 <= maxLag && raw[best * 2] >= raw[best] * 0.85f) {
            best *= 2
        }

        val candidates = ArrayList<TempoCandidate>(maxLag - minLag + 1)
        for (lag in minLag..maxLag) {
            candidates.add(TempoCandidate(lag, 60f / (lag * hopSec), raw[lag]))
        }
        candidates.sortByDescending { it.score }

        // Parabolic interpolation around the winner: the 64ms envelope grid
        // quantizes badly (150 BPM falls between integer lags 6 and 7), so fit
        // a parabola through the neighbor scores for a fractional-lag BPM.
        val lagF = if (best - 1 >= minLag && best + 1 <= maxLag) {
            val a = raw[best - 1]
            val b = raw[best]
            val c = raw[best + 1]
            val denom = a - 2 * b + c
            if (denom < -1e-9f) {
                (best + 0.5f * (a - c) / denom).coerceIn(minLag.toFloat(), maxLag.toFloat())
            } else {
                best.toFloat()
            }
        } else {
            best.toFloat()
        }

        val bestScore = raw[best]
        val rhythmicity = bestScore.coerceAtMost(2f) / 2f  // map ~[0,2] → [0,1]
        if (rhythmicity < 0.05f) {
            return TempoEstimate(0f, rhythmicity, candidates.takeLast(5))
        }

        var bpm = 60f / (lagF * hopSec)
        while (bpm < MIN_BPM) bpm *= 2
        while (bpm > MAX_BPM) bpm /= 2
        return TempoEstimate(bpm, rhythmicity, candidates.take(5))
    }

    // Krumhansl major/minor profiles, rotated over 12 pitch classes.
    private val MAJOR = floatArrayOf(6.35f, 2.23f, 3.48f, 2.33f, 4.38f, 4.09f, 2.52f, 5.19f, 2.39f, 3.66f, 2.29f, 2.88f)
    private val MINOR = floatArrayOf(6.33f, 2.68f, 3.52f, 5.38f, 2.60f, 3.53f, 2.54f, 4.75f, 3.98f, 2.69f, 3.34f, 3.17f)

    private fun estimateKey(mags: List<FloatArray>, hzPerBin: Float): Int? {
        val chroma = FloatArray(12)
        for (m in mags) {
            for (k in 2 until m.size) {
                val hz = k * hzPerBin
                if (hz < 55 || hz > 4_000) continue
                val midi = (12 * ln(hz / 440f) / ln(2f) + 69).toInt()
                chroma[(midi % 12 + 12) % 12] += m[k]
            }
        }
        val tot = chroma.sum()
        if (tot <= 1e-9f) return null
        for (i in chroma.indices) chroma[i] /= tot
        var bestKey = -1
        var bestCorr = 0.55f
        for (root in 0 until 12) {
            val cm = correlateCircular(chroma, MAJOR, root)
            val cn = correlateCircular(chroma, MINOR, root)
            if (cm > bestCorr) {
                bestCorr = cm
                bestKey = root
            }
            if (cn > bestCorr) {
                bestCorr = cn
                bestKey = root
            }
        }
        return bestKey.takeIf { it >= 0 }
    }

    private fun correlateCircular(chroma: FloatArray, profile: FloatArray, root: Int): Float {
        var sx = 0f
        var sy = 0f
        var sxx = 0f
        var syy = 0f
        var sxy = 0f
        for (i in 0 until 12) {
            val x = chroma[i]
            val y = profile[(i - root + 12) % 12]
            sx += x
            sy += y
            sxx += x * x
            syy += y * y
            sxy += x * y
        }
        val num = 12 * sxy - sx * sy
        val den = sqrt((12 * sxx - sx * sx) * (12 * syy - sy * sy))
        return if (den > 1e-9f) num / den else 0f
    }
}
