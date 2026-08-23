package moe.rukamori.archivetune.visualizer.bnmv

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import timber.log.Timber

/**
 * Stateless helper to control the externally installed BNMV app via broadcasts.
 * All calls are fire-and-forget; BNMV ignores unknown intents if not installed.
 */
object BnmvController {

    fun isInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(BnmvConstants.PACKAGE_NAME, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    fun sendAction(context: Context, action: String, block: (Intent.() -> Unit)? = null) {
        // BNMV exposes ExternalControlReceiver (exported) with intent-filter for ACTION_*.
        // Use BroadcastOptions to allow background activity starts (TrampolineActivity) on Android 14.
        fun buildIntent(): Intent = Intent(action).apply {
            block?.invoke(this)
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES or Intent.FLAG_RECEIVER_FOREGROUND or 0x01000000 /* FLAG_RECEIVER_INCLUDE_BACKGROUND */)
        }
        try {
            val implicit = buildIntent().apply { `package` = BnmvConstants.PACKAGE_NAME }
            context.sendBroadcast(implicit)
            Timber.tag(TAG).d("Sent implicit $action to ${BnmvConstants.PACKAGE_NAME}")
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed implicit $action")
        }
        try {
            val explicit = buildIntent().apply {
                setClassName(
                    BnmvConstants.PACKAGE_NAME,
                    "com.better.nothing.music.vizualizer.receiver.ExternalControlReceiver",
                )
            }
            context.sendBroadcast(explicit)
            Timber.tag(TAG).d("Sent explicit $action to ExternalControlReceiver")
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed explicit $action")
        }
    }

    fun start(context: Context) = sendAction(context, BnmvConstants.ACTION_START)
    fun stop(context: Context) = sendAction(context, BnmvConstants.ACTION_STOP)
    fun toggle(context: Context) = sendAction(context, BnmvConstants.ACTION_TOGGLE)

    fun setSource(context: Context, source: String) = sendAction(context, BnmvConstants.ACTION_SET_SOURCE) {
        putExtra(BnmvConstants.EXTRA_SOURCE, source)
    }

    fun setPreset(context: Context, presetKey: String) = sendAction(context, BnmvConstants.ACTION_SET_PRESET) {
        putExtra(BnmvConstants.EXTRA_PRESET, presetKey)
    }

    fun toggleFeature(context: Context, action: String, enabled: Boolean? = null) =
        sendAction(context, action) {
            if (enabled != null) putExtra(BnmvConstants.EXTRA_ENABLED, enabled)
        }

    fun connectUdp(context: Context, ip: String, port: Int = BnmvConstants.HANDSHAKE_LISTEN_PORT) =
        sendAction(context, BnmvConstants.ACTION_CONNECT_UDP) {
            putExtra(BnmvConstants.EXTRA_IP, ip)
            putExtra(BnmvConstants.EXTRA_PORT, port)
        }

    private const val TAG = "BnmvController"
}
