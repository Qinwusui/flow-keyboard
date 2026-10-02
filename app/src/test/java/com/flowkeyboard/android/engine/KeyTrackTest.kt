package com.flowkeyboard.android.engine

import com.flowkeyboard.android.model.*
import org.junit.Assert.*
import org.junit.Test

class KeyTrackTest {
    @Test fun `QWERTY tracks contain all letters exactly once`() {
        val tracks = KeyTrack.create()
        assertEquals("qwertyuiop", tracks[1].keys.joinToString("") { it.value })
        val letters = (tracks[1].keys + tracks[2].keys).map { it.value }.sorted()
        assertEquals(('a'..'z').map(Char::toString), letters)
        assertEquals(KeyType.entries.filter { it != KeyType.CHARACTER }.toSet(), tracks[3].keys.map { it.type }.toSet())
    }
    @Test fun `alphabetical layout preserves contiguous order`() {
        val tracks = KeyTrack.create(KeyOrder.ALPHABETICAL)
        assertEquals("abcdefghijklmnopqrstuvwxyz", (tracks[1].keys + tracks[2].keys).joinToString("") { it.value })
    }
}
