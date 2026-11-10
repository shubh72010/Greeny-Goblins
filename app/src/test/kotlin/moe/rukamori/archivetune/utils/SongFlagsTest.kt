package moe.rukamori.archivetune.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SongFlagsTest {
    @Test
    fun parseEmpty() {
        assertEquals(emptySet<String>(), parseSongIdSet(null))
        assertEquals(emptySet<String>(), parseSongIdSet(""))
        assertEquals(emptySet<String>(), parseSongIdSet("   "))
    }

    @Test
    fun parseTrimsAndDedups() {
        assertEquals(setOf("a", "b"), parseSongIdSet(" a , b ,a "))
    }

    @Test
    fun toggleAddsAndRemoves() {
        val raw = toggleSongIdInSet("", "x")
        assertTrue(isSongInSet(raw, "x"))
        val raw2 = toggleSongIdInSet(raw, "x")
        assertFalse(isSongInSet(raw2, "x"))
    }

    @Test
    fun serializeRoundTrip() {
        val set = setOf("one", "two")
        assertEquals(set, parseSongIdSet(serializeSongIdSet(set)))
    }
}