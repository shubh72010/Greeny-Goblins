/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*

data class LogEntry(
    val time: Long,
    val level: Int,
    val tag: String?,
    val message: String,
)

object GlobalLog {
    private const val MAX_ENTRIES = 500
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())

    // append() runs on every Timber call site, so it must not build a fresh 500-element
    // list per line, and must not publish unless something is actually collecting. A late
    // reader gets a fresh snapshot on subscribe instead of the last published value.
    val logs: Flow<List<LogEntry>> =
        flow {
            emit(snapshot())
            _logs.collect { emit(it) }
        }

    private val ring = ArrayDeque<LogEntry>(MAX_ENTRIES)

    private fun snapshot(): List<LogEntry> = synchronized(ring) { ring.toList() }

    private fun publish() {
        _logs.value = snapshot()
    }

    fun append(
        level: Int,
        tag: String?,
        message: String,
    ) {
        val entry = LogEntry(System.currentTimeMillis(), level, tag, message)
        val subscribed =
            synchronized(ring) {
                if (ring.size == MAX_ENTRIES) ring.removeFirst()
                ring.addLast(entry)
                _logs.subscriptionCount.value > 0
            }
        if (subscribed) publish()
    }

    fun clear() {
        synchronized(ring) { ring.clear() }
        _logs.value = emptyList()
    }

    fun format(entry: LogEntry): String {
        val ts = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(entry.time))
        val lvl =
            when (entry.level) {
                android.util.Log.VERBOSE -> "V"
                android.util.Log.DEBUG -> "D"
                android.util.Log.INFO -> "I"
                android.util.Log.WARN -> "W"
                android.util.Log.ERROR -> "E"
                else -> "?"
            }
        val tag = entry.tag ?: ""
        return "[$ts] $lvl/$tag: ${entry.message}"
    }
}

/** Timber Tree that forwards logs to GlobalLog */
class GlobalLogTree : Timber.DebugTree() {
    override fun log(
        priority: Int,
        tag: String?,
        message: String,
        t: Throwable?,
    ) {
        try {
            val final = if (t != null) "$message\n$t" else message
            GlobalLog.append(priority, tag, final)
        } catch (_: Exception) {
            // swallow
        }
    }
}
