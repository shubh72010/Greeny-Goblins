/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.db.entities

import androidx.room.ColumnInfo
import androidx.room.Embedded

/**
 * One cached analysis plus its library metadata (when the song is in the DB).
 * Plain Room POJO for the music_analysis + song join — not a table.
 */
data class AnalysisWithSong(
    @Embedded val analysis: MusicAnalysisEntity,
    @ColumnInfo(name = "songTitle") val songTitle: String?,
    @ColumnInfo(name = "artworkUrl") val artworkUrl: String?,
)
