/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import android.app.ActivityManager
import android.content.Context

fun Context.isLowRamDevice(): Boolean {
    val activityManager = applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    return activityManager?.isLowRamDevice == true
}

/** RAM-based tier. Cached per process — totalMem never changes at runtime. */
fun Context.deviceTier(): DeviceTier {
    val activityManager = applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    val memInfo = ActivityManager.MemoryInfo()
    activityManager?.getMemoryInfo(memInfo)
    return classifyDeviceTier(memInfo.totalMem, activityManager?.isLowRamDevice == true)
}
