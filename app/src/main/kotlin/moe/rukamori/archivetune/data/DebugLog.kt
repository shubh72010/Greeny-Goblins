package moe.rukamori.archivetune.data

object DebugLog {
    fun d(tag: String, msg: String) = android.util.Log.d(tag, msg)
    fun d(tag: String, msg: String, t: Throwable) = android.util.Log.d(tag, msg, t)
    fun e(tag: String, msg: String) = android.util.Log.e(tag, msg)
    fun e(tag: String, msg: String, t: Throwable) = android.util.Log.e(tag, msg, t)
    fun i(tag: String, msg: String) = android.util.Log.i(tag, msg)
    fun w(tag: String, msg: String) = android.util.Log.w(tag, msg)
    fun w(tag: String, msg: String, t: Throwable) = android.util.Log.w(tag, msg, t)
}
