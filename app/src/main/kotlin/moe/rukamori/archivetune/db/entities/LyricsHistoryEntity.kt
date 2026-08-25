package moe.rukamori.archivetune.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "lyrics_history")
data class LyricsHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: String,
    val lyrics: String,
    val createdAt: Long = System.currentTimeMillis(),
)
