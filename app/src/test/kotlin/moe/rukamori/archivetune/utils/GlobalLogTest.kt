/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class GlobalLogTest {
    @Before
    fun setUp() = GlobalLog.clear()

    @After
    fun tearDown() = GlobalLog.clear()

    @Test
    fun keepsOnlyNewestEntries() = runBlocking {
        repeat(505) { GlobalLog.append(0, "t", "line$it") }

        val snapshot = GlobalLog.logs.first()

        assertEquals(500, snapshot.size)
        assertEquals("line5", snapshot.first().message)
        assertEquals("line504", snapshot.last().message)
    }

    @Test
    fun readerGetsEntriesAppendedBeforeItSubscribed() = runBlocking {
        GlobalLog.append(0, "t", "before-any-reader")
        GlobalLog.append(0, "t", "second")

        val snapshot = GlobalLog.logs.first()

        assertEquals(listOf("before-any-reader", "second"), snapshot.map { it.message })
    }

    @Test
    fun clearDropsEverything() = runBlocking {
        GlobalLog.append(0, "t", "x")

        GlobalLog.clear()

        assertEquals(emptyList<LogEntry>(), GlobalLog.logs.first())
    }
}
