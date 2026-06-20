/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.spotify.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SpotifyAudioFeatures(
    val danceability: Float = 0f,
    val energy: Float = 0f,
    val key: Int = 0,
    val loudness: Float = 0f,
    val mode: Int = 0,
    val speechiness: Float = 0f,
    val acousticness: Float = 0f,
    val instrumentalness: Float = 0f,
    val liveness: Float = 0f,
    val valence: Float = 0f,
    val tempo: Float = 0f,
    @SerialName("duration_ms") val durationMs: Int = 0,
    @SerialName("time_signature") val timeSignature: Int = 0,
)
