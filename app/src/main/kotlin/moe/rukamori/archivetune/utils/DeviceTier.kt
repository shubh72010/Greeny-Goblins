/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

/**
 * Performance tier from total device RAM. Pure function (no Android deps) so it
 * is unit-testable on the JVM. `ActivityManager.isLowRamDevice` alone misses
 * the 3–8GB budget segment (Oppo/Redmi on heavy skins), so total RAM decides.
 */
enum class DeviceTier {
    /** ≤3GB or low-RAM flag: minimal caches, small buffers, small art. */
    CONSTRAINED,

    /** ≤6GB: moderate caches and buffers. */
    REDUCED,

    /** The rest: full config. */
    NORMAL,
}

private const val CONSTRAINED_MAX_RAM_BYTES = 3L * 1024 * 1024 * 1024
private const val REDUCED_MAX_RAM_BYTES = 6L * 1024 * 1024 * 1024

fun classifyDeviceTier(
    totalMemBytes: Long,
    lowRamDevice: Boolean,
): DeviceTier =
    when {
        lowRamDevice || totalMemBytes <= CONSTRAINED_MAX_RAM_BYTES -> DeviceTier.CONSTRAINED
        totalMemBytes <= REDUCED_MAX_RAM_BYTES -> DeviceTier.REDUCED
        else -> DeviceTier.NORMAL
    }

/** Per-tier budgets. Single source of truth — App/MusicService/UI read these. */
data class TierBudgets(
    val coilMemoryPercent: Double,
    val imageDiskMb: Int,
    val bufferMinMs: Int,
    val bufferMaxMs: Int,
    val fullscreenArtCapPx: Int,
)

fun DeviceTier.budgets(): TierBudgets =
    when (this) {
        DeviceTier.CONSTRAINED -> TierBudgets(0.12, 128, 10_000, 20_000, 720)
        DeviceTier.REDUCED -> TierBudgets(0.18, 256, 12_000, 24_000, 864)
        DeviceTier.NORMAL -> TierBudgets(0.25, 512, 15_000, 30_000, 1080)
    }
