/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.audio.analysis

/**
 * Aggregates per-checkpoint DSP samples into one track profile.
 *
 * Confidence is normalized PER FEATURE (locked decision): bounded 0..1 features
 * use variance/0.25, BPM uses variance/3600 (60bpm spread = full disagreement),
 * key uses voter agreement. Overall confidence is the mean of per-feature
 * confidences; extras trigger below [CheckpointMath.CONFIDENCE_THRESHOLD].
 */
object FeatureAggregator {
    private const val BPM_FULL_DISAGREE_VAR = 3600f // 60bpm std

    data class SamplePoint(val fraction: Double, val sample: DspSample)

    data class AggregatedResult(
        val rms: Float,
        val bass: Float,
        val mids: Float,
        val treble: Float,
        val brightness: Float,
        val spectralFlux: Float,
        val onsetDensity: Float,
        val rhythmicity: Float,
        val bpm: Float,
        val key: Int?,
        val perFeatureConfidence: Map<String, Float>,
        val overallConfidence: Float,
        val sampleCount: Int,
    )

    fun aggregate(samples: List<SamplePoint>): AggregatedResult? {
        if (samples.isEmpty()) return null
        val s = samples.map { it.sample }
        fun mean(f: (DspSample) -> Float) = s.map(f).average().toFloat()
        fun variance(f: (DspSample) -> Float): Float {
            val m = mean(f)
            return s.sumOf { ((f(it) - m) * (f(it) - m)).toDouble() }.toFloat() / s.size
        }
        // ponytail: max variance of a [0,1] variable is 0.25 — exact normalizer, no tuning knob.
        fun boundedConf(f: (DspSample) -> Float) = (1f - (variance(f) / 0.25f)).coerceIn(0f, 1f)

        val conf = mutableMapOf<String, Float>()
        conf["rms"] = boundedConf { it.rms }
        conf["bass"] = boundedConf { it.bass }
        conf["mids"] = boundedConf { it.mids }
        conf["treble"] = boundedConf { it.treble }
        conf["brightness"] = boundedConf { it.brightness }
        conf["spectralFlux"] = boundedConf { it.spectralFlux }
        conf["onsetDensity"] = boundedConf { it.onsetDensity }
        conf["rhythmicity"] = boundedConf { it.rhythmicity }

        val knownBpms = s.map { it.bpm }.filter { it > 0 }
        val bpmMean = if (knownBpms.isEmpty()) 0f else knownBpms.average().toFloat()
        conf["bpm"] = when {
            knownBpms.size < 2 -> 0.5f
            else -> {
                val m = bpmMean
                val v = knownBpms.sumOf { ((it - m) * (it - m)).toDouble() }.toFloat() / knownBpms.size
                (1f - v / BPM_FULL_DISAGREE_VAR).coerceIn(0f, 1f)
            }
        }

        val keys = s.mapNotNull { it.key }
        val keyMode = keys.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        conf["key"] = when {
            keys.isEmpty() -> 0.5f
            else -> keys.count { it == keyMode }.toFloat() / keys.size
        }

        val overall = conf.values.average().toFloat().coerceIn(0f, 1f)
        return AggregatedResult(
            rms = mean { it.rms },
            bass = mean { it.bass },
            mids = mean { it.mids },
            treble = mean { it.treble },
            brightness = mean { it.brightness },
            spectralFlux = mean { it.spectralFlux },
            onsetDensity = mean { it.onsetDensity },
            rhythmicity = mean { it.rhythmicity },
            bpm = bpmMean,
            key = keyMode,
            perFeatureConfidence = conf,
            overallConfidence = overall,
            sampleCount = samples.size,
        )
    }

    fun needsExtraSamples(result: AggregatedResult, extraTaken: Int): Boolean =
        result.overallConfidence < CheckpointMath.CONFIDENCE_THRESHOLD &&
            extraTaken < CheckpointMath.MAX_EXTRA_SAMPLES

    /** Next adaptive sample goes at the midpoint of the largest evidence gap. */
    fun nextExtraFraction(fired: Set<Double>): Double = CheckpointMath.largestGapMidpoint(fired)
}
