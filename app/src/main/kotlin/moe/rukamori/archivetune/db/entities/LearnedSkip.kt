/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * Materialized HyprMix rule for one song.
 *
 * Materialized (instead of recomputed at read time) for two product reasons:
 * turning learning off must freeze existing rules, and a user edit must survive
 * later recordings. [manual] marks an edit and blocks relearning for that song.
 */
@Immutable
@Entity(
    tableName = "learned_skip",
    foreignKeys = [
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class LearnedSkip(
    @PrimaryKey val songId: String,
    /** Where to start playback. 0 = nothing to skip. */
    val introUntilMs: Long,
    val samples: Int,
    val manual: Boolean,
    val updatedAt: Long,
)
