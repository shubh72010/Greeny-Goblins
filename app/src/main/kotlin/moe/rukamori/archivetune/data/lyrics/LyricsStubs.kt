package moe.rukamori.archivetune.data.lyrics

data class LyricLine(val text: String = "", val timeMs: Long = 0)
enum class LyricsSource {
    GENIUS, LRCLIB;
    companion object { fun ordered(saved: Any?): List<LyricsSource> = values().toList() }
}
