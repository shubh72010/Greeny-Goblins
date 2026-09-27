/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.audio.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureAggregatorTest {
    private fun sample(
        rms: Float = 0.86f,
        bpm: Float = 120f,
        key: Int? = 0,
    ) = DspSample(
        rms = rms,
        bass = 0.3f,
        mids = 0.4f,
        treble = 0.3f,
        brightness = 0.7f,
        spectralFlux = 0.5f,
        onsetDensity = 0.5f,
        rhythmicity = 0.8f,
        bpm = bpm,
        key = key,
    )

    @Test
    fun agreeingSamplesGiveHighConfidence() {
        val points = listOf(0.2, 0.5, 0.8).map {
            FeatureAggregator.SamplePoint(it, sample(rms = 0.86f))
        }
        val r = FeatureAggregator.aggregate(points)!!
        assertTrue("conf=${r.overallConfidence}", r.overallConfidence > 0.9f)
        assertEquals(0.86f, r.rms, 1e-3f)
        assertFalse(FeatureAggregator.needsExtraSamples(r, 0))
    }

    @Test
    fun disagreeingSamplesTriggerExtra() {
        // Diverge on every feature: per-feature confidences all drop, so the mean does too.
        fun divergent(level: Float, bpm: Float, key: Int?) = DspSample(
            rms = level,
            bass = level,
            mids = level,
            treble = level,
            brightness = level,
            spectralFlux = level,
            onsetDensity = level,
            rhythmicity = level,
            bpm = bpm,
            key = key,
        )
        val points = listOf(
            FeatureAggregator.SamplePoint(0.2, divergent(0.1f, 90f, 0)),
            FeatureAggregator.SamplePoint(0.5, divergent(0.9f, 170f, 7)),
            FeatureAggregator.SamplePoint(0.8, divergent(0.5f, 130f, null)),
        )
        val r = FeatureAggregator.aggregate(points)!!
        assertTrue("conf=${r.overallConfidence}", r.overallConfidence < 0.6f)
        assertTrue(FeatureAggregator.needsExtraSamples(r, 0))
        assertFalse(FeatureAggregator.needsExtraSamples(r, 2))
    }

    @Test
    fun keyUsesMajorityVote() {
        val points = listOf(
            FeatureAggregator.SamplePoint(0.2, sample(key = 0)),
            FeatureAggregator.SamplePoint(0.5, sample(key = 0)),
            FeatureAggregator.SamplePoint(0.8, sample(key = 7)),
        )
        val r = FeatureAggregator.aggregate(points)!!
        assertEquals(0, r.key)
    }

    @Test
    fun emptySamplesReturnNull() {
        assertEquals(null, FeatureAggregator.aggregate(emptyList()))
    }
}
