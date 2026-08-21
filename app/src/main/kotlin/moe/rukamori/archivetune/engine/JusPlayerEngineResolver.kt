/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.engine

import org.jusplayer.engine.provider.ProviderException
import org.jusplayer.engine.provider.newpipe.NewPipeProvider
import timber.log.Timber

/**
 * Bridge to JusPlayer-Engine (com.github.shubh72010.JusPlayer-Engine:engine-provider-newpipe:v1.6.0).
 *
 * Real path: JUSPLAYER_ENGINE -> NewPipeProvider -> JusPlayerEngineResolver.extractAudioUrl()
 *            -> MusicService.resolveJusPlayerEngineDataSpec() -> ExoPlayer DataSpec
 *
 * Declared in `app/build.gradle.kts`:
 *   implementation("com.github.shubh72010.JusPlayer-Engine:engine-api:v1.6.0")
 *   implementation("com.github.shubh72010.JusPlayer-Engine:engine-provider-newpipe:v1.6.0")
 * via JitPack in `settings.gradle.kts` exclusiveContent.
 */
object JusPlayerEngineResolver {
    private const val TAG = "JusPlayerEngine"

    // Lazy singleton — NewPipe.init(downloader) is called once in constructor
    private val provider: NewPipeProvider by lazy { NewPipeProvider() }

    /**
     * Extract stream URL via NewPipeProvider.getStream(videoId).
     * NewPipe's StreamInfo.getInfo expects a full watch URL; we try both forms.
     * @return direct stream URL or null if engine failed (caller falls back to InnerTube)
     */
    suspend fun extractAudioUrl(videoId: String): String? {
        // Prefer www.youtube.com for NewPipe (more reliable than music.youtube.com for extractor)
        val candidates = listOf(
            "https://www.youtube.com/watch?v=$videoId",
            videoId,
            "https://music.youtube.com/watch?v=$videoId",
        )
        for (candidate in candidates) {
            try {
                val stream = provider.getStream(candidate)
                val url = stream.url
                if (url.isBlank()) {
                    Timber.tag(TAG).w("NewPipeProvider returned blank url for $videoId (candidate=$candidate)")
                    continue
                } else {
                    Timber.tag(TAG).d("NewPipeProvider resolved $videoId via $candidate -> ${url.take(80)}...")
                    return url
                }
            } catch (e: ProviderException.NotFound) {
                Timber.tag(TAG).w(e, "NewPipeProvider NotFound for $videoId candidate=$candidate")
                continue
            } catch (e: ProviderException.RateLimited) {
                Timber.tag(TAG).w(e, "NewPipeProvider RateLimited for $videoId candidate=$candidate")
                return null
            } catch (e: ProviderException.Network) {
                Timber.tag(TAG).w(e, "NewPipeProvider Network error for $videoId candidate=$candidate")
                return null
            } catch (e: ProviderException) {
                Timber.tag(TAG).w(e, "NewPipeProvider ProviderException for $videoId candidate=$candidate")
                continue
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "NewPipeProvider unexpected failure for $videoId candidate=$candidate")
                continue
            }
        }
        Timber.tag(TAG).w("NewPipeProvider all candidates failed for $videoId")
        return null
    }
}
