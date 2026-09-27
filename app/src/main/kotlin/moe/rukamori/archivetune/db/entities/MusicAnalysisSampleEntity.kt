/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One checkpoint window's raw DSP candidates, kept for diagnosis.
 * The aggregated means live in [MusicAnalysisEntity]; the per-window winners
 * that produced them live here, so a wrong-but-confident aggregate (e.g. three
 * windows agreeing on a 1.5x tempo harmonic) stays explainable after the fact.
 *
 * No foreign key: samples are written during playback, before the parent
 * aggregate row exists at 80%. Stale rows are cleared when a track is
 * (re-)analyzed.
 */
@Entity(
    tableName = "music_analysis_sample",
    indices = [Index("videoId")],
)
data class MusicAnalysisSampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val videoId: String,
    val fraction: Double,
    val bpm: Float,
    val chromaKey: Int?,
    val analyzedAt: Long = 0L,
)
