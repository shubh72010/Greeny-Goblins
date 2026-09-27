/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cached Greeny-Goblins track analysis, keyed by YouTube videoId.
 * DSP-only columns in this phase; GG and Laya columns land with the
 * fine-tuned model contract (STOP line - not this change).
 */
@Entity(tableName = "music_analysis")
data class MusicAnalysisEntity(
    @PrimaryKey val videoId: String,
    val rms: Float,
    val bass: Float,
    val mids: Float,
    val treble: Float,
    val brightness: Float,
    val spectralFlux: Float,
    val onsetDensity: Float,
    val rhythmicity: Float,
    val bpm: Float?,
    val chromaKey: Int?,
    /** Essentia reports "major"/"minor"; null when the key came from the JVM estimator (mode-less). */
    val chromaScale: String? = null,
    val confidence: Float,
    val sampleCount: Int,
    val analysisVersion: Int = 1,
    val analyzedAt: Long = 0L,
    val analysisSource: String = "none",
    val analysisError: String? = null,
)
