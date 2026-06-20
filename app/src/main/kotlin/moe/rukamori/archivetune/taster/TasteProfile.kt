package moe.rukamori.archivetune.taster

data class TasteProfile(
    val topArtistIds: Map<String, Float>,
    val topArtistNames: Map<String, Float>,
    val preferredDurationAvg: Double,
    val preferredDurationStd: Double,
    val preferredDecades: Map<Int, Float>,
    val likedSongIds: Set<String>,
    val skipRatios: Map<String, Float>,
    val recentSongIds: List<String>,
    val dailyPlayCounts: Map<String, Int>,
    val playCounts: Map<String, Int>,
    val sessionHour: Int,
    val artistSongIds: Map<String, List<String>>,
    val recentlyPlayedArtistIds: Set<String>,
    val recentlyPlayedSet: Set<String>,
    val totalEventCount: Int,
    val conversionRates: Map<String, Float>,
)
