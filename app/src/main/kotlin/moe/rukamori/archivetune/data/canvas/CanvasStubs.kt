package moe.rukamori.archivetune.data.canvas

data class AppleArtistArt(val url: String? = null)
data class CanvasArtwork(val url: String? = null)
object CanvasRepository { fun get() = this }
object AppleArtistArtRepository { fun get() = this }
val appleCatalogue: List<Any> = emptyList()
