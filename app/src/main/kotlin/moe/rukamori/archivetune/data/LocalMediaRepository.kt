package moe.rukamori.archivetune.data

object LocalMediaRepository {
    var extraLocalMusic: ((android.content.Context, List<moe.rukamori.archivetune.data.model.Song>) -> List<moe.rukamori.archivetune.data.model.Song>)? = null
}
