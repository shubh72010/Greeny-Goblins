/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * One row per media item playback, for HyprMix only.
 *
 * Deliberately separate from [Event]: that table feeds history UI, scrobbling and
 * ListenBrainz through a threshold/finalization state machine. This one is written
 * straight from the player callbacks and can never corrupt it.
 */
@Immutable
@Entity(
    tableName = "play_session",
    foreignKeys = [
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class PlaySession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(index = true) val songId: String,
    val startedAt: Long,
    val startPositionMs: Long,
    val playedMs: Long,
    val durationMs: Long,
    val endedNaturally: Boolean,
)
