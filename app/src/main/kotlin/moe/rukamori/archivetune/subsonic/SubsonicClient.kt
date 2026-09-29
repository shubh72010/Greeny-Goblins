/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.subsonic

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "Subsonic"

/**
 * Provider-agnostic OpenSubsonic client.
 *
 * Compatible with Navidrome, Airsonic-Advanced, Gonic, Ampache and any server
 * implementing Subsonic API v1.16.1 + OpenSubsonic extensions. No Navidrome-specific
 * code paths — server differences are handled by ignoring unknown fields.
 */
data class SubsonicConfig(
    val baseUrl: String,
    val username: String,
    val password: String,
)

@Singleton
class SubsonicClient @Inject constructor() {
    private val client =
        HttpClient(OkHttp) {
            engine {
                config {
                    connectTimeout(15, TimeUnit.SECONDS)
                    readTimeout(15, TimeUnit.SECONDS)
                    writeTimeout(15, TimeUnit.SECONDS)
                    retryOnConnectionFailure(false)
                }
            }
        }

    private val json =
        Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            isLenient = true
        }

    suspend fun ping(config: SubsonicConfig): Boolean =
        runCatching { get(config, "ping").status == "ok" }.getOrDefault(false)

    suspend fun getMusicFolders(config: SubsonicConfig): List<MusicFolder> =
        get(config, "getMusicFolders").musicFolders?.musicFolder.orEmpty()

    suspend fun getArtists(config: SubsonicConfig, musicFolderId: String? = null): List<ArtistRef> {
        val response =
            get(config, "getArtists") {
                if (!musicFolderId.isNullOrBlank()) it["musicFolderId"] = musicFolderId
            }
        return response.artists?.index.orEmpty().flatMap { it.artist }
    }

    suspend fun getArtist(config: SubsonicConfig, id: String): ArtistPayload? =
        get(config, "getArtist") { it["id"] = id }.artist

    suspend fun getAlbum(config: SubsonicConfig, id: String): AlbumPayload? =
        get(config, "getAlbum") { it["id"] = id }.album

    suspend fun getAlbumList2(
        // "newest" = recently added (shows fresh scans). NOT "recent" (= recently played = empty on a fresh server).
        config: SubsonicConfig,
        type: String = "newest",
        size: Int = 50,
        offset: Int = 0,
        musicFolderId: String? = null,
    ): List<AlbumRef> {
        val response =
            get(config, "getAlbumList2") {
                it["type"] = type
                it["size"] = size.toString()
                it["offset"] = offset.toString()
                if (!musicFolderId.isNullOrBlank()) it["musicFolderId"] = musicFolderId
            }
        return response.albumList2?.album.orEmpty()
    }

    suspend fun getPlaylists(config: SubsonicConfig): List<PlaylistRef> =
        get(config, "getPlaylists").playlists?.playlist.orEmpty()

    suspend fun getPlaylist(config: SubsonicConfig, id: String): PlaylistDetail? =
        get(config, "getPlaylist") { it["id"] = id }.playlist

    suspend fun getRandomSongs(
        config: SubsonicConfig,
        size: Int = 10,
        musicFolderId: String? = null,
    ): List<TrackPayload> =
        get(config, "getRandomSongs") {
            it["size"] = size.coerceIn(1, 50).toString()
            if (!musicFolderId.isNullOrBlank()) it["musicFolderId"] = musicFolderId
        }.randomSongs?.song.orEmpty()

    suspend fun search3(
        config: SubsonicConfig,
        query: String,
        musicFolderId: String? = null,
    ): SearchResult3? {
        if (query.isBlank()) return null
        return get(config, "search3") {
            it["query"] = query
            it["artistCount"] = "20"
            it["albumCount"] = "20"
            it["songCount"] = "25"
            if (!musicFolderId.isNullOrBlank()) it["musicFolderId"] = musicFolderId
        }.searchResult3
    }

    private suspend fun get(
        config: SubsonicConfig,
        endpoint: String,
        extra: (MutableMap<String, String>) -> Unit = {},
    ): SubsonicResponse {
        val extras = mutableMapOf<String, String>().also(extra)
        val httpResponse =
            client.get(endpointUrl(config, endpoint)) {
                val auth = authParams(config)
                auth.forEach { (k, v) -> parameter(k, v) }
                parameter("f", "json")
                extras.forEach { (k, v) -> parameter(k, v) }
            }
        val text = httpResponse.bodyAsText()
        Timber.tag(TAG).d("%s -> http=%d body=%d chars", endpoint, httpResponse.status.value, text.length)
        val response =
            runCatching { json.decodeFromString<SubsonicEnvelope>(text).`subsonic-response` }
                .getOrElse { throwable ->
                    Timber.tag(TAG).w(
                        throwable,
                        "%s: parse failed, head=%.200s",
                        endpoint,
                        text,
                    )
                    throw throwable
                }
        if (response.status != "ok") {
            Timber.tag(TAG).w(
                "%s: server status=%s err=%s",
                endpoint,
                response.status,
                response.error,
            )
        } else {
            Timber.tag(TAG).d(
                "%s: ok folders=%d indexes=%d albums=%d songs=%d playlists=%d entries=%d random=%d",
                endpoint,
                response.musicFolders?.musicFolder?.size ?: -1,
                response.artists?.index?.size ?: -1,
                (response.albumList2?.album?.size ?: response.album?.song?.size) ?: -1,
                response.searchResult3?.song?.size ?: -1,
                response.playlists?.playlist?.size ?: -1,
                response.playlist?.entry?.size ?: -1,
                response.randomSongs?.song?.size ?: -1,
            )
        }
        return response
    }

    companion object {
        const val API_VERSION = "1.16.1"
        const val CLIENT_NAME = "archivetune"

        fun normalizeBaseUrl(raw: String): String = raw.trim().trimEnd('/')

        /** Direct stream URL (authenticated). Pure — no HTTP client needed. ExoPlayer plays it as-is. */
        fun streamUrl(config: SubsonicConfig, id: String): String =
            endpointUrl(config, "stream") + "?" + authQuery(config) + "&id=" + encode(id)

        fun coverArtUrl(config: SubsonicConfig, id: String, size: Int? = null): String =
            endpointUrl(config, "getCoverArt") + "?" + authQuery(config) +
                "&id=" + encode(id) +
                (if (size != null) "&size=$size" else "")

        fun endpointUrl(config: SubsonicConfig, endpoint: String): String =
            "${normalizeBaseUrl(config.baseUrl)}/rest/$endpoint.view"

        /** `t=md5(password+salt)` per Subsonic token auth. Pure — unit-tested. */
        fun buildToken(password: String, salt: String): String {
            val md = MessageDigest.getInstance("MD5")
            val digest = md.digest((password + salt).toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }

        fun newSalt(length: Int = 12): String {
            val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
            return (1..length).map { chars.random() }.joinToString("")
        }

        internal fun authParams(config: SubsonicConfig, salt: String = newSalt()): Map<String, String> =
            mapOf(
                "u" to config.username,
                "t" to buildToken(config.password, salt),
                "s" to salt,
                "v" to API_VERSION,
                "c" to CLIENT_NAME,
            )

        internal fun authQuery(config: SubsonicConfig, salt: String = newSalt()): String =
            authParams(config, salt).entries.joinToString("&") { "${it.key}=${encode(it.value)}" }

        private fun encode(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")
    }
}
