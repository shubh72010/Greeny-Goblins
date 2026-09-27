/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.audio.analysis

/**
 * One 3-second analysis sample. Bounded features are normalized 0..1.
 * [bpm] is 0 when indeterminate; [key] is null when indeterminate (0..11 = C..B).
 */
data class DspSample(
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
)
