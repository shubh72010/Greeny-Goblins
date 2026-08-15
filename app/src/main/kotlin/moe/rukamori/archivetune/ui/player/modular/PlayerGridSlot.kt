package moe.rukamori.archivetune.ui.player.modular

import kotlinx.serialization.Serializable

@Serializable
data class PlayerGridSlot(
    val typeId: String,
    val row: Int,
    val col: Int,
    val width: Int,
    val height: Int,
) {
    val right: Int get() = col + width
    val bottom: Int get() = row + height

    fun overlaps(other: PlayerGridSlot): Boolean {
        return col < other.right && right > other.col &&
            row < other.bottom && bottom > other.row
    }
}

@Serializable
data class PlayerLayout(
    val slots: List<PlayerGridSlot> = emptyList(),
    val rows: Int = 9,
    val columns: Int = 4,
    val textSizeScale: Float = 1f,
    val playButtonScale: Float = 1f,
    val showTimeOnSeekBar: Boolean = true,
) {
    fun sanitize(): PlayerLayout {
        val validIds = PlayerComponentType.entries.map { it.id }.toSet()
        return copy(slots = slots.filter { it.typeId in validIds })
    }
}

object DefaultPlayerLayouts {
    val classic = PlayerLayout(
        slots = listOf(
            PlayerGridSlot("cover_art", 0, 0, 4, 4),
            PlayerGridSlot("title", 4, 0, 4, 1),
            PlayerGridSlot("artist", 5, 0, 4, 1),
            PlayerGridSlot("seek_bar", 6, 0, 4, 1),
            PlayerGridSlot("time_display", 7, 0, 4, 1),
            PlayerGridSlot("play_previous", 8, 0, 1, 1),
            PlayerGridSlot("play_pause", 8, 1, 2, 1),
            PlayerGridSlot("play_next", 8, 3, 1, 1),
        ),
        rows = 9,
        columns = 4,
    )

    val compact = PlayerLayout(
        slots = listOf(
            PlayerGridSlot("cover_art", 0, 0, 2, 3),
            PlayerGridSlot("title", 0, 2, 2, 1),
            PlayerGridSlot("artist", 1, 2, 2, 1),
            PlayerGridSlot("play_previous", 2, 2, 1, 1),
            PlayerGridSlot("play_pause", 2, 3, 1, 1),
            PlayerGridSlot("play_next", 3, 2, 1, 1),
            PlayerGridSlot("seek_bar", 3, 0, 4, 1),
            PlayerGridSlot("shuffle", 4, 0, 1, 1),
            PlayerGridSlot("like", 4, 1, 1, 1),
            PlayerGridSlot("queue", 4, 2, 1, 1),
            PlayerGridSlot("repeat", 4, 3, 1, 1),
        ),
        rows = 5,
        columns = 4,
    )

    val minimal = PlayerLayout(
        slots = listOf(
            PlayerGridSlot("cover_art", 0, 0, 4, 5),
            PlayerGridSlot("play_previous", 5, 0, 1, 1),
            PlayerGridSlot("play_pause", 5, 1, 2, 1),
            PlayerGridSlot("play_next", 5, 3, 1, 1),
            PlayerGridSlot("seek_bar", 6, 0, 4, 1),
        ),
        rows = 7,
        columns = 4,
    )
}
