/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.amazon

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.db.entities.ArtistEntity
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.db.entities.SongEntity
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class AmazonPlaylistResult(
    val id: String,
    val title: String,
    val songs: List<Song>,
    val author: String? = null,
    val imageUrl: String? = null,
)

object AmazonMusicPlaylistParser {
    private const val TAG = "AmazonMusicParser"

    private val client by lazy {
        OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    private val URL_PATTERN = Pattern.compile("https?://[^\\s\"'<>]+")

    private val SKILL_ENDPOINTS = listOf(
        "https://na.web.skill.music.a2z.com",
        "https://eu.web.skill.music.a2z.com",
        "https://fe.web.skill.music.a2z.com",
    )

    private val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"

    /**
     * Extracts an Amazon Music playlist URL from raw text (e.g. from share intents).
     */
    fun extractAmazonUrl(input: String?): String? {
        if (input.isNullOrBlank()) return null
        val matcher = URL_PATTERN.matcher(input)
        while (matcher.find()) {
            val candidate = matcher.group()
            if (candidate.contains("music.amazon.") ||
                candidate.contains("amzn.to") ||
                (candidate.contains("amazon.") && (candidate.contains("/playlists/") || candidate.contains("/user-playlists/")))
            ) {
                return candidate
            }
        }
        return null
    }

    /**
     * Parses an Amazon Music playlist URL and returns all songs and metadata.
     */
    suspend fun parsePlaylist(rawInput: String): Result<AmazonPlaylistResult> = withContext(Dispatchers.IO) {
        runCatching {
            val initialUrl = extractAmazonUrl(rawInput)
                ?: throw IllegalArgumentException("No Amazon Music playlist URL found in input.")

            // Resolve any shortlinks / redirects
            val resolvedUrl = resolveUrl(initialUrl)
            Timber.tag(TAG).d("Resolved playlist URL: %s", resolvedUrl)

            val (domain, playlistId, isUserPlaylist) = parseUrlDetails(resolvedUrl)
            Timber.tag(TAG).d("Domain: %s, PlaylistId: %s, isUser: %b", domain, playlistId, isUserPlaylist)

            val config = fetchDomainConfig(domain)

            val innerHeaders = buildInnerHeaders(config, domain, resolvedUrl)
            val requestBodyJson = JSONObject().apply {
                put("id", playlistId)
                put("userHash", JSONObject().apply { put("level", "LIBRARY_MEMBER") }.toString())
                put("headers", innerHeaders.toString())
            }

            val paths = if (isUserPlaylist) {
                listOf("/api/showLibraryPlaylist", "/api/showCatalogPlaylist")
            } else {
                listOf("/api/showCatalogPlaylist", "/api/showLibraryPlaylist")
            }

            val primaryEndpoint = getPrimarySkillEndpoint(domain)
            val endpointsToTry = listOf(primaryEndpoint) + SKILL_ENDPOINTS.filter { it != primaryEndpoint }

            var lastError: Exception? = null
            var result: AmazonPlaylistResult? = null

            outerLoop@ for (endpoint in endpointsToTry) {
                for (path in paths) {
                    try {
                        val apiUrl = "$endpoint$path"
                        val req = Request.Builder()
                            .url(apiUrl)
                            .post(requestBodyJson.toString().toRequestBody("text/plain;charset=UTF-8".toMediaType()))
                            .header("Accept", "*/*")
                            .header("Accept-Language", "en-US,en;q=0.9")
                            .header("Origin", "https://$domain")
                            .header("Referer", "https://$domain/")
                            .header("User-Agent", USER_AGENT)
                            .build()

                        client.newCall(req).execute().use { response ->
                            if (!response.isSuccessful) return@use
                            val responseBody = response.body?.string() ?: return@use
                            val json = JSONObject(responseBody)

                            val parsed = parsePlaylistResponse(json, playlistId)
                            if (parsed != null && parsed.songs.isNotEmpty()) {
                                result = parsed
                                break@outerLoop
                            }
                        }
                    } catch (e: Exception) {
                        Timber.tag(TAG).w(e, "Error fetching from %s%s", endpoint, path)
                        lastError = e
                    }
                }
            }

            result ?: throw (lastError ?: IllegalStateException("Could not retrieve playlist tracks from Amazon Music."))
        }
    }

    private fun resolveUrl(url: String): String {
        return try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()
            client.newCall(req).execute().use { response ->
                response.request.url.toString()
            }
        } catch (_: Exception) {
            url
        }
    }

    private data class UrlDetails(val domain: String, val playlistId: String, val isUserPlaylist: Boolean)

    private fun parseUrlDetails(url: String): UrlDetails {
        val domainMatcher = Pattern.compile("https?://([^/]+)").matcher(url)
        val domain = if (domainMatcher.find()) domainMatcher.group(1) else "music.amazon.com"

        val isUserPlaylist = url.contains("/user-playlists/") || url.contains("/userplaylists/")

        val idPattern = Pattern.compile("/(?:user-playlists|userplaylists|playlists)/([a-zA-Z0-9]+)")
        val idMatcher = idPattern.matcher(url)
        val playlistId = if (idMatcher.find()) {
            idMatcher.group(1)
        } else {
            // Fallback: take the last non-empty path segment before any query params
            val path = url.substringBefore('?').trimEnd('/')
            path.substringAfterLast('/')
        }

        if (playlistId.isBlank()) {
            throw IllegalArgumentException("Could not extract a playlist ID from URL: $url")
        }

        return UrlDetails(domain, playlistId, isUserPlaylist)
    }

    private fun getPrimarySkillEndpoint(domain: String): String {
        return when {
            domain.endsWith(".co.uk") || domain.endsWith(".de") || domain.endsWith(".fr") ||
            domain.endsWith(".it") || domain.endsWith(".es") || domain.endsWith(".in") ||
            domain.endsWith(".sa") || domain.endsWith(".ae") -> "https://eu.web.skill.music.a2z.com"
            domain.endsWith(".co.jp") || domain.endsWith(".com.au") -> "https://fe.web.skill.music.a2z.com"
            else -> "https://na.web.skill.music.a2z.com"
        }
    }

    private fun fetchDomainConfig(domain: String): JSONObject {
        val urlsToTry = listOf(
            "https://$domain/config.json?skipToken=false&clientApplication=skyfire",
            "https://music.amazon.com/config.json?skipToken=false&clientApplication=skyfire",
        )

        for (url in urlsToTry) {
            try {
                val req = Request.Builder()
                    .url(url)
                    .post("".toRequestBody("application/json".toMediaType()))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "*/*")
                    .header("Referer", "https://$domain/")
                    .build()

                client.newCall(req).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (!body.isNullOrBlank()) {
                            return JSONObject(body)
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Failed to fetch config from %s", url)
            }
        }

        return JSONObject()
    }

    private fun buildInnerHeaders(config: JSONObject, domain: String, pageUrl: String): JSONObject {
        val csrf = config.optJSONObject("csrf")
        val csrfToken = csrf?.optString("token").orEmpty()
        val csrfTs = csrf?.opt("ts")?.toString().orEmpty()
        val csrfRnd = csrf?.opt("rnd")?.toString().orEmpty()
        val deviceId = config.optString("deviceId", UUID.randomUUID().toString().replace("-", "").take(16))
        val sessionId = config.optString("sessionId", "100-${System.currentTimeMillis()}-0000000")
        val accessToken = config.optString("accessToken", "")

        return JSONObject().apply {
            put(
                "x-amzn-authentication",
                JSONObject().apply {
                    put("interface", "ClientAuthenticationInterface.v1_0.ClientTokenElement")
                    put("accessToken", accessToken)
                }.toString(),
            )
            put("x-amzn-device-model", "WEBPLAYER")
            put("x-amzn-device-width", "1920")
            put("x-amzn-device-family", "WebPlayer")
            put("x-amzn-device-id", deviceId)
            put("x-amzn-user-agent", USER_AGENT)
            put("x-amzn-session-id", sessionId)
            put("x-amzn-device-height", "1080")
            put("x-amzn-request-id", UUID.randomUUID().toString())
            put("x-amzn-device-language", "en_US")
            put("x-amzn-currency-of-preference", "USD")
            put("x-amzn-os-version", "1.0")
            put("x-amzn-application-version", config.optString("version", ""))
            put("x-amzn-timestamp", System.currentTimeMillis().toString())
            put(
                "x-amzn-csrf",
                JSONObject().apply {
                    put("interface", "CSRFInterface.v1_0.CSRFHeaderElement")
                    put("token", csrfToken)
                    put("timestamp", csrfTs)
                    put("rndNonce", csrfRnd)
                }.toString(),
            )
            put("x-amzn-music-domain", domain)
            put("x-amzn-referer", domain)
            put("x-amzn-page-url", pageUrl)
            put("x-amzn-feature-flags", "hd-supported,uhd-supported")
        }
    }

    private fun extractText(obj: Any?): String {
        if (obj == null) return ""
        if (obj is String) return obj.trim()
        if (obj is JSONObject) {
            val directText = obj.optString("text")
            if (directText.isNotBlank()) return directText.trim()

            val obsDefault = obj.optJSONObject("observer")?.optJSONObject("defaultValue")?.optString("text")
            if (!obsDefault.isNullOrBlank()) return obsDefault.trim()

            val title = obj.optString("title")
            if (title.isNotBlank()) return title.trim()
        }
        return ""
    }

    private fun parsePlaylistResponse(data: JSONObject, playlistId: String): AmazonPlaylistResult? {
        val methods = data.optJSONArray("methods") ?: return null
        if (methods.length() == 0) return null

        val firstMethod = methods.optJSONObject(0) ?: return null
        val template = firstMethod.optJSONObject("template") ?: return null

        // Check if redirected to home / invalid
        val deeplink = template.optJSONObject("templateData")?.optString("deeplink")
        if (deeplink == "/") return null

        val widgets = template.optJSONArray("widgets") ?: return null
        if (widgets.length() == 0) return null

        var title = extractText(template.opt("headerText"))
        if (title.isBlank()) {
            title = template.optJSONObject("templateData")?.optString("title").orEmpty()
        }
        if (title.isBlank()) {
            title = "Amazon Music Playlist"
        }

        val author = extractText(template.opt("headerSecondaryText1")).ifBlank { null }
        val imageUrl = template.optString("headerImage").ifBlank { null }

        val songs = mutableListOf<Song>()

        for (wIndex in 0 until widgets.length()) {
            val widget = widgets.optJSONObject(wIndex) ?: continue
            val items = widget.optJSONArray("items") ?: continue

            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue

                val songTitle = extractText(item.opt("primaryText"))
                if (songTitle.isBlank()) continue

                val artistRaw = extractText(item.opt("secondaryText1"))
                val albumRaw = extractText(item.opt("secondaryText2")).ifBlank { null }
                val thumbnail = item.optString("image").ifBlank { null }

                val artists = if (artistRaw.isNotBlank()) {
                    artistRaw
                        .split(Regex("[,;&]|\\s+(?:feat\\.|ft\\.|featuring)\\s+", RegexOption.IGNORE_CASE))
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .map { ArtistEntity(id = "", name = it) }
                } else {
                    emptyList()
                }

                songs.add(
                    Song(
                        song = SongEntity(
                            id = "",
                            title = songTitle,
                            albumName = albumRaw,
                            thumbnailUrl = thumbnail,
                        ),
                        artists = artists,
                    ),
                )
            }
        }

        return AmazonPlaylistResult(
            id = playlistId,
            title = title,
            songs = songs,
            author = author,
            imageUrl = imageUrl,
        )
    }
}
