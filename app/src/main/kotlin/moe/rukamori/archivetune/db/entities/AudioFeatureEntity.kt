package moe.rukamori.archivetune.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "audio_feature")
data class AudioFeatureEntity(
    @PrimaryKey val songId: String,
    val danceability: Float,
    val energy: Float,
    val key: Int,
    val loudness: Float,
    val mode: Int,
    val speechiness: Float,
    val acousticness: Float,
    val instrumentalness: Float,
    val liveness: Float,
    val valence: Float,
    val tempo: Float,
    val timeSignature: Int,
    val fetchedAt: Long,
) {
    fun toVector(): FloatArray = floatArrayOf(
        danceability, energy, (key % 12) / 12f,
        (loudness + 60f) / 60f, mode.toFloat(),
        speechiness, acousticness, instrumentalness,
        liveness, valence, tempo / 200f,
        timeSignature / 4f,
    )
}
