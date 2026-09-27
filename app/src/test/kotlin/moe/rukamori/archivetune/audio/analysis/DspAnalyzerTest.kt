/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.audio.analysis

import moe.rukamori.archivetune.audio.essentia.EssentiaNative
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class DspAnalyzerTest {
    private fun sine440(durationSec: Float = 3f, rate: Int = 16_000): ShortArray {
        val n = (durationSec * rate).toInt()
        return ShortArray(n) { (sin(2 * PI * 440 * it / rate) * 20_000).toInt().toShort() }
    }

    private fun clickTrack(bpm: Int, durationSec: Int, rate: Int = 16_000): ShortArray {
        val pcm = ShortArray(rate * durationSec)
        val period = (rate * 60 / bpm)
        var i = 0
        while (i < pcm.size) {
            for (k in 0 until 200) {
                if (i + k < pcm.size) pcm[i + k] = 25_000
            }
            i += period
        }
        return pcm
    }

    // Custom estimator tests are commented out pending removal of the old
    // tempo/key path in favor of Essentia.
    /*
    @Test
    fun clickTrack120bpmDetected() {
        val rate = 16_000
        val n = rate * 3
        val pcm = ShortArray(n)
        val period = (rate * 60 / 120)
        var i = 0
        while (i < n) {
            for (k in 0 until 200) {
                if (i + k < n) pcm[i + k] = 25_000
            }
            i += period
        }
        val s = DspAnalyzer.analyze(pcm)
        assertTrue("bpm=${s.bpm}", s.bpm in 110f..130f)
        assertTrue("rhythmicity=${s.rhythmicity}", s.rhythmicity > 0.2f)
    }

    @Test
    fun halfTimeGroove93bpmNotDoubleTime() {
        val rate = 16_000
        val pcm = ShortArray(rate * 3)
        val quarter = (rate * 60 / 93.0).toInt()
        fun click(at: Int, amp: Int, len: Int) {
            for (k in 0 until len) {
                val idx = at + k
                if (idx < pcm.size) pcm[idx] = amp.toShort()
            }
        }
        var beat = 0
        var t = 0
        while (t < pcm.size) {
            click(t, if (beat % 2 == 1) 25_000 else 10_000, 200)
            t += quarter
            beat++
        }
        var h = quarter / 2
        while (h < pcm.size) {
            click(h, 8_000, 60)
            h += quarter / 2
        }
        val s = DspAnalyzer.analyze(pcm)
        assertTrue("bpm=${s.bpm}", s.bpm in 85f..105f)
    }

    @Test
    fun overlapCorrectionPrefersTrueTempoOverSubdivision() {
        val env = floatArrayOf(
             0.207139f, 0.016327f, 0.011099f, 0.018118f, 0.018772f, 0.625756f,
             0.026605f, 0.025124f, 0.007781f, 0.007030f, 1.546806f, 0.147172f,
             0.025094f, 0.014291f, 0.019172f, 0.575890f, 0.097674f, 0.026041f,
             0.015695f, 0.022238f, 0.733046f, 0.139017f, 0.022747f, 0.017733f,
             0.009038f, 0.519883f, 0.157013f, 0.014182f, 0.021565f, 0.026364f,
             1.272233f, 0.426824f, 0.011849f, 0.024027f, 0.013339f, 0.494601f,
             0.209833f, 0.002924f, 0.004079f, 0.006510f, 0.604773f, 0.287277f,
             0.018799f, 0.009031f, 0.015217f, 0.425690f,
         )
        val (bpm, _) = DspAnalyzer.estimateTempo(env, 1024f / 16_000f)
        assertEquals(93.75f, bpm, 0.1f)
    }

    @Test
    fun silenceGivesNoTempoOrKey() {
        val s = DspAnalyzer.analyze(ShortArray(16_000 * 3))
        assertEquals(0f, s.bpm)
        assertNull(s.key)
        assertEquals(0f, s.spectralFlux)
    }
    */

    @Test
    fun sine440HasMidEnergyNotBass() {
        val s = DspAnalyzer.analyze(sine440())
        assertTrue("mids=${s.mids} bass=${s.bass}", s.mids > s.bass)
        assertTrue("treble=${s.treble}", s.treble < 0.3f)
        assertTrue("rms=${s.rms}", s.rms > 0.3f)
    }

    @Test
    fun resample44100to16000KeepsDuration() {
        val pcm = sine440(rate = 44_100)
        val out = DspAnalyzer.resampleLinearMono(pcm, 44_100)
        assertTrue("size=${out.size}", kotlin.math.abs(out.size - 16_000 * 3) <= 2)
    }

    // Native lib never loads on the JVM unit-test runtime, so these pin the
    // Essentia→JVM fallback chain deterministically (source must be "jvm",
    // never "essentia" with zeros).

    @Test
    fun fallbackDetects120bpmClickViaJvm() {
        val long = DspAnalyzer.analyzeTempoKey(clickTrack(bpm = 120, durationSec = 12))
        assertEquals("jvm", long.analysisSource)
        assertTrue("bpm=${long.bpm}", long.bpm in 110f..130f)
        assertTrue("rhythmicity=${long.rhythmicity}", long.rhythmicity > 0f)
    }

    @Test
    fun offGrid150bpmClickDetected() {
        // 150 BPM sits between integer envelope lags — the reported Diet Pepsi case.
        val long = DspAnalyzer.analyzeTempoKey(clickTrack(bpm = 150, durationSec = 12))
        assertTrue("bpm=${long.bpm}", long.bpm in 140f..160f)
    }

    @Test
    fun shortContextAbstainsWithNoneSource() {
        val long = DspAnalyzer.analyzeTempoKey(clickTrack(bpm = 120, durationSec = 3))
        assertEquals(0f, long.bpm)
        assertEquals("none", long.analysisSource)
    }

    @Test
    fun pureSineHasNoTempo() {
        val long = DspAnalyzer.analyzeTempoKey(sine440(durationSec = 12f))
        assertEquals(0f, long.bpm)
        assertNull(long.key)
    }

    @Test
    fun parseResultAcceptsWireFormat() {
        val ok = EssentiaNative.parseResult("120|C|major")
        assertEquals(120, ok.bpm)
        assertEquals("C", ok.key)
        assertEquals("major", ok.scale)
        assertNull(ok.error)
    }

    @Test
    fun parseResultKeepsMinorScale() {
        val minor = EssentiaNative.parseResult("88|F#|minor")
        assertEquals(88, minor.bpm)
        assertEquals("F#", minor.key)
        assertEquals("minor", minor.scale)
    }

    @Test
    fun parseResultTreatsStubTokenAsError() {
        val err = EssentiaNative.parseResult("0|||essentia not bundled for this ABI")
        assertEquals(0, err.bpm)
        assertNotNull(err.error)
    }

    @Test
    fun resolveOctaveAveragesAgreement() {
        assertEquals(121f, DspAnalyzer.resolveOctave(120f, 122f)!!, 0.01f)
    }

    @Test
    fun resolveOctavePrefersLowerOnDoubling() {
        // The reported case: 96 BPM song read as ~187.
        assertEquals(93.75f, DspAnalyzer.resolveOctave(187f, 93.75f)!!, 0.01f)
        assertEquals(96f, DspAnalyzer.resolveOctave(96f, 190f)!!, 0.01f)
    }

    @Test
    fun resolveOctaveRejectsUnrelatedEstimates() {
        assertNull(DspAnalyzer.resolveOctave(120f, 80f))
        assertNull(DspAnalyzer.resolveOctave(0f, 100f))
    }
}
