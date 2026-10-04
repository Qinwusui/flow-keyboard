package com.flowkeyboard.android.engine

import com.flowkeyboard.android.model.*
import org.junit.Assert.*
import org.junit.Test

class KeyTrackTest {
    @Test fun `QWERTY tracks contain dedicated number row, three letter rows and bottom function row`() {
        val tracks = KeyTrack.create()
        assertEquals(5, tracks.size)
        assertTrue(tracks[0].keys.map { it.value }.containsAll(listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")))
        assertEquals("qwertyuiop", tracks[1].keys.joinToString("") { it.value })
        assertEquals("asdfghjkl", tracks[2].keys.joinToString("") { it.value })
        assertEquals("zxcvbnm", tracks[3].keys.joinToString("") { it.value })
        val letters = (tracks[1].keys + tracks[2].keys + tracks[3].keys).map { it.value }.sorted()
        assertEquals(('a'..'z').map(Char::toString), letters)
        assertEquals(KeyType.entries.filter { it != KeyType.CHARACTER }.toSet(), tracks[4].keys.map { it.type }.toSet())
    }

    @Test fun `alphabetical layout preserves contiguous order across three rows`() {
        val tracks = KeyTrack.create(KeyOrder.ALPHABETICAL)
        assertEquals("abcdefghijklmnopqrstuvwxyz", (tracks[1].keys + tracks[2].keys + tracks[3].keys).joinToString("") { it.value })
    }

    @Test fun `function lane has fixed ergonomic order Shift Mode Symbol Emoji Space Backspace Enter`() {
        val tracks = KeyTrack.create()
        val expectedTypes = listOf(KeyType.SHIFT, KeyType.MODE, KeyType.SYMBOL, KeyType.EMOJI, KeyType.SPACE, KeyType.BACKSPACE, KeyType.ENTER)
        assertEquals(expectedTypes, tracks[4].keys.map { it.type })
    }

    @Test fun `symbol mode provides math, punctuation and special symbol tracks`() {
        val tracks = KeyTrack.create(symbolMode = true)
        assertEquals(5, tracks.size)
        assertTrue(tracks[0].keys.any { it.value == "1" } && tracks[0].keys.any { it.value == "×" })
        assertTrue(tracks[1].keys.any { it.value == "，" } && tracks[1].keys.any { it.value == "《" })
        assertTrue(tracks[2].keys.any { it.value == "@" } && tracks[2].keys.any { it.value == "{" })
        assertTrue(tracks[3].keys.any { it.value == "①" } && tracks[3].keys.any { it.value == "￥" })
        assertEquals("ABC", tracks[4].keys.first { it.type == KeyType.SYMBOL }.label)
    }

    @Test fun `emoji mode provides numbers, symbols and two lanes of emojis`() {
        val tracks = KeyTrack.create(emojiMode = true)
        assertEquals(5, tracks.size)
        assertTrue(tracks[0].keys.any { it.value == "1" })
        assertTrue(tracks[1].keys.any { it.value == "，" || it.value == "," })
        assertTrue(tracks[2].keys.any { it.value == "😀" } && tracks[2].keys.any { it.value == "😂" })
        assertTrue(tracks[3].keys.any { it.value == "👍" } && tracks[3].keys.any { it.value == "❤️" })
        assertEquals("🔤", tracks[4].keys.first { it.type == KeyType.EMOJI }.label)
    }
}
