package moe.rukamori.archivetune.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "rec_exposure",
    foreignKeys = [
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["songId"]),
        Index(value = ["shownAt"]),
    ],
)
data class RecExposure(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: String,
    val shownAt: Long,
)
