package com.flowkeyboard.android.engine

import com.flowkeyboard.android.model.DictionaryItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PinyinEngineTest {
    private val engine = PinyinEngine()
    @Test fun `letters compose and uppercase normalizes`() {
        assertTrue(engine.append('N')); assertTrue(engine.append('i'))
        assertEquals("ni", engine.composing.value)
    }
    @Test fun `multi letter and multi syllable matching`() {
        "nihao".forEach(engine::append)
        assertTrue(engine.matches(DictionaryItem(1, "你好", "nihao")))
        assertFalse(engine.matches(DictionaryItem(2, "你", "ni")))
        assertEquals(listOf("ni", "hao"), engine.syllables())
    }
    @Test fun `partial syllable is allowed`() {
        "zhon".forEach(engine::append)
        assertTrue(engine.matches(DictionaryItem(1, "中国", "zhongguo")))
        assertNotNull(engine.syllables())
    }
    @Test fun `backspace updates composing buffer`() {
        "nihao".forEach(engine::append)
        assertTrue(engine.backspace()); assertEquals("niha", engine.text)
        assertEquals("niha", engine.clear()); assertEquals("", engine.text)
    }
    @Test fun `invalid long pinyin degrades without growth or exceptions`() {
        repeat(5000) { engine.append('x') }
        assertEquals(PinyinEngine.MAX_LENGTH, engine.text.length)
        assertFalse(engine.append('a'))
        assertNull(engine.syllables())
        assertFalse(engine.matches(DictionaryItem(1, "你好", "nihao")))
    }
    @Test fun `empty buffer backspace is safe`() {
        repeat(5) { assertFalse(engine.backspace()) }
        assertEquals("", engine.text); assertEquals(emptyList<String>(), engine.syllables())
    }
    @Test fun `apostrophes separate syllables and normalize search`() {
        assertFalse(engine.append('\''))
        "xi'an".forEach(engine::append)
        assertEquals("xian", engine.searchPrefix)
        assertEquals(listOf("xi", "an"), engine.syllables())
        assertTrue(engine.matches(DictionaryItem(1, "西安", "xian")))
    }
    @Test fun `punctuation and digits do not enter composition`() {
        assertFalse(engine.append('1')); assertFalse(engine.append('!')); assertFalse(engine.append('你'))
        assertEquals("", engine.text)
    }
    @Test fun `empty buffer cannot match every dictionary word`() {
        assertFalse(engine.matches(DictionaryItem(1, "你", "ni")))
    }

    @Test fun `consecutive apostrophes are rejected without changing composition`() {
        "ni'".forEach { assertTrue(engine.append(it)) }
        assertFalse(engine.append('\''))
        assertEquals("ni'", engine.text)
        "hao".forEach { assertTrue(engine.append(it)) }
        assertEquals("ni'hao", engine.text)
    }

    @Test fun `backspace removes characters and separator one at a time`() {
        "ni'a".forEach { assertTrue(engine.append(it)) }
        listOf("ni'", "ni", "n", "").forEach { expected ->
            assertTrue(engine.backspace())
            assertEquals(expected, engine.text)
            assertEquals(expected, engine.composing.value)
        }
        assertFalse(engine.backspace())
    }

    @Test fun `xian remains one syllable without explicit separator`() {
        "xian".forEach { assertTrue(engine.append(it)) }
        assertEquals(listOf("xian"), engine.syllables())
    }

    @Test fun `invalid sequences return null without changing buffer`() {
        listOf("zz", "abc", "ni'zz").forEach { invalid ->
            engine.clear()
            invalid.forEach { assertTrue(engine.append(it)) }
            assertNull("Unexpected syllables for $invalid", engine.syllables())
            assertEquals(invalid, engine.text)
        }
    }

    @Test fun `pending separator and incomplete final syllable are safe`() {
        "ni'".forEach { assertTrue(engine.append(it)) }
        assertEquals(listOf("ni"), engine.syllables())
        assertTrue(engine.append('h'))
        assertEquals(listOf("ni", "h"), engine.syllables())
    }

    @Test fun `prefix matching normalizes candidate case and separators`() {
        "NI'H".forEach { assertTrue(engine.append(it)) }
        assertTrue(engine.matches(DictionaryItem(1L, "你好", "NI'HAO")))
        assertTrue(engine.matches(DictionaryItem(2L, "你好", "nihao")))
        assertFalse(engine.matches(DictionaryItem(3L, "你", "ni")))
        assertFalse(engine.matches(DictionaryItem(4L, "你好", "xinihao")))
    }

    @Test fun `rejected characters leave existing buffer unchanged`() {
        "ni".forEach { assertTrue(engine.append(it)) }
        listOf('0', '9', ' ', '-', '.', '\n', '你', 'é').forEach { character ->
            assertFalse(engine.append(character))
            assertEquals("ni", engine.text)
        }
    }

    @Test fun `length cap allows appending after backspace`() {
        repeat(PinyinEngine.MAX_LENGTH) { assertTrue(engine.append('a')) }
        assertFalse(engine.append('b'))
        assertTrue(engine.backspace())
        assertTrue(engine.append('b'))
        assertEquals("a".repeat(PinyinEngine.MAX_LENGTH - 1) + "b", engine.text)
    }

    @Test fun `clear resets all composition views and allows new input`() {
        "ni'hao".forEach { assertTrue(engine.append(it)) }
        assertEquals("ni'hao", engine.clear())
        assertEquals("", engine.text)
        assertEquals("", engine.composing.value)
        assertEquals("", engine.searchPrefix)
        assertEquals(emptyList<String>(), engine.syllables())
        assertFalse(engine.backspace())
        assertEquals("", engine.clear())
        assertTrue(engine.append('a'))
    }
}
