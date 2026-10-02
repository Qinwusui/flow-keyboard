package com.flowkeyboard.android.engine

import android.app.Application
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import com.flowkeyboard.android.model.KeyItem
import com.flowkeyboard.android.model.KeyType
import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import io.mockk.verifySequence
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class KeyboardActionHandlerTest {
    private val connection = mockk<InputConnection>(relaxed = true)
    // imeOptions is a public field, so configure it directly on the MockK instance.
    private val info = mockk<EditorInfo>(relaxed = true).apply { imeOptions = EditorInfo.IME_ACTION_NONE }
    private val handler = KeyboardActionHandler({ connection }, { info })

    @Test fun `character is committed at cursor`() {
        every { connection.commitText("a", 1) } returns true
        assertTrue(handler.handle(KeyItem("a", "a")))
        verify(exactly = 1) { connection.commitText("a", 1) }
    }
    @Test fun `backspace deletes one preceding character`() {
        handler.handle(KeyItem("delete", "⌫", "", KeyType.BACKSPACE))
        verify { connection.deleteSurroundingText(1, 0) }
    }
    @Test fun `backspace deletes selected text`() {
        every { connection.getSelectedText(0) } returns "selected"
        handler.backspace()
        verify { connection.commitText("", 1) }
        verify(exactly = 0) { connection.deleteSurroundingText(any(), any()) }
    }
    @Test fun `backspace preserves surrogate pair integrity`() {
        every { connection.getTextBeforeCursor(2, 0) } returns "😀"
        handler.backspace()
        verify { connection.deleteSurroundingText(2, 0) }
    }
    @Test fun `space commits literal space`() {
        handler.handle(KeyItem("space", "Space", " ", KeyType.SPACE))
        verify { connection.commitText(" ", 1) }
    }
    @Test fun `enter in multiline field inserts newline`() {
        info.imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_ENTER_ACTION
        handler.enter()
        verify { connection.commitText("\n", 1) }
        verify(exactly = 0) { connection.performEditorAction(any()) }
    }
    @Test fun `enter performs requested editor action`() {
        info.imeOptions = EditorInfo.IME_ACTION_SEARCH
        every { connection.performEditorAction(EditorInfo.IME_ACTION_SEARCH) } returns true
        assertTrue(handler.enter())
        verify { connection.performEditorAction(EditorInfo.IME_ACTION_SEARCH) }
        verify(exactly = 0) { connection.commitText(any(), any()) }
    }
    @Test fun `failed editor action falls back to newline`() {
        info.imeOptions = EditorInfo.IME_ACTION_GO
        handler.enter()
        verify { connection.commitText("\n", 1) }
    }
    @Test fun `composition uses editor composing span`() {
        handler.compose("ni"); handler.finishComposition()
        verifyOrder { connection.setComposingText("ni", 1); connection.finishComposingText() }
    }
    @Test fun `missing connection is safe`() {
        val detached = KeyboardActionHandler({ null })
        assertFalse(detached.commit("a")); assertFalse(detached.backspace()); assertFalse(detached.enter())
        detached.cancelComposition()
    }

    @Test fun `commit calls commitText and returns its result`() {
        every { connection.commitText("你好", 1) } returns true
        assertTrue(handler.commit("你好"))
        verifySequence { connection.commitText("你好", 1) }
    }

    @Test fun `commit propagates editor rejection`() {
        every { connection.commitText("text", 1) } returns false
        assertFalse(handler.commit("text"))
        verifySequence { connection.commitText("text", 1) }
    }

    @Test fun `compose calls setComposingText and returns its result`() {
        every { connection.setComposingText("nihao", 1) } returns true
        assertTrue(handler.compose("nihao"))
        verifySequence { connection.setComposingText("nihao", 1) }
    }

    @Test fun `compose propagates editor rejection`() {
        every { connection.setComposingText("ni", 1) } returns false
        assertFalse(handler.compose("ni"))
        verifySequence { connection.setComposingText("ni", 1) }
    }

    @Test fun `finish composition finishes editor span`() {
        handler.finishComposition()
        verifySequence { connection.finishComposingText() }
    }

    @Test fun `cancel composition clears text before finishing span`() {
        handler.cancelComposition()
        verifySequence {
            connection.setComposingText("", 1)
            connection.finishComposingText()
        }
    }

    @Test fun `backspace handles empty selection and normal text`() {
        every { connection.getSelectedText(0) } returns ""
        every { connection.getTextBeforeCursor(2, 0) } returns "你"
        every { connection.deleteSurroundingText(1, 0) } returns true
        assertTrue(handler.backspace())
        verifySequence {
            connection.getSelectedText(0)
            connection.getTextBeforeCursor(2, 0)
            connection.deleteSurroundingText(1, 0)
        }
    }

    @Test fun `unpaired surrogate deletes only one code unit`() {
        every { connection.getSelectedText(0) } returns null
        every { connection.getTextBeforeCursor(2, 0) } returns "a\uDE00"
        handler.backspace()
        verifySequence {
            connection.getSelectedText(0)
            connection.getTextBeforeCursor(2, 0)
            connection.deleteSurroundingText(1, 0)
        }
    }

    @Test fun `unavailable cursor text falls back to single character deletion`() {
        every { connection.getSelectedText(0) } returns null
        every { connection.getTextBeforeCursor(2, 0) } returns null
        handler.backspace()
        verifySequence {
            connection.getSelectedText(0)
            connection.getTextBeforeCursor(2, 0)
            connection.deleteSurroundingText(1, 0)
        }
    }

    @Test fun `backspace propagates selection deletion failure`() {
        every { connection.getSelectedText(0) } returns "selected"
        every { connection.commitText("", 1) } returns false
        assertFalse(handler.backspace())
        verifySequence {
            connection.getSelectedText(0)
            connection.commitText("", 1)
        }
    }

    @Test fun `enter dispatches all valid actions without committing newline`() {
        every { connection.performEditorAction(any()) } returns true
        listOf(
            EditorInfo.IME_ACTION_GO, EditorInfo.IME_ACTION_SEARCH, EditorInfo.IME_ACTION_SEND,
            EditorInfo.IME_ACTION_NEXT, EditorInfo.IME_ACTION_DONE, EditorInfo.IME_ACTION_PREVIOUS,
        ).forEach { action ->
            info.imeOptions = action
            assertTrue(handler.enter())
            verify(exactly = 1) { connection.performEditorAction(action) }
        }
        verify(exactly = 0) { connection.commitText(any(), any()) }
    }

    @Test fun `enter masks unrelated ime flags`() {
        info.imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        every { connection.performEditorAction(EditorInfo.IME_ACTION_DONE) } returns true
        assertTrue(handler.enter())
        verifySequence { connection.performEditorAction(EditorInfo.IME_ACTION_DONE) }
    }

    @Test fun `none and unspecified actions insert newline`() {
        every { connection.commitText("\n", 1) } returns true
        listOf(EditorInfo.IME_ACTION_NONE, EditorInfo.IME_ACTION_UNSPECIFIED).forEach {
            info.imeOptions = it
            assertTrue(handler.enter())
        }
        verify(exactly = 2) { connection.commitText("\n", 1) }
        verify(exactly = 0) { connection.performEditorAction(any()) }
    }

    @Test fun `missing editor info inserts newline`() {
        every { connection.commitText("\n", 1) } returns true
        val withoutInfo = KeyboardActionHandler({ connection }, { null })
        assertTrue(withoutInfo.enter())
        verifySequence { connection.commitText("\n", 1) }
    }

    @Test fun `failed action attempts newline after the action`() {
        info.imeOptions = EditorInfo.IME_ACTION_DONE
        every { connection.performEditorAction(EditorInfo.IME_ACTION_DONE) } returns false
        every { connection.commitText("\n", 1) } returns true
        assertTrue(handler.enter())
        verifySequence {
            connection.performEditorAction(EditorInfo.IME_ACTION_DONE)
            connection.commitText("\n", 1)
        }
    }

    @Test fun `failed newline commit returns false`() {
        info.imeOptions = EditorInfo.IME_ACTION_NONE
        every { connection.commitText("\n", 1) } returns false
        assertFalse(handler.enter())
        verifySequence { connection.commitText("\n", 1) }
    }

    @Test fun `character uses key value rather than label`() {
        every { connection.commitText("a", 1) } returns true
        assertTrue(handler.handle(KeyItem("a", "A", "a", KeyType.CHARACTER)))
        verifySequence { connection.commitText("a", 1) }
    }

    @Test fun `enter key routes to editor action`() {
        info.imeOptions = EditorInfo.IME_ACTION_DONE
        every { connection.performEditorAction(EditorInfo.IME_ACTION_DONE) } returns true
        assertTrue(handler.handle(KeyItem("enter", "Enter", type = KeyType.ENTER)))
        verifySequence { connection.performEditorAction(EditorInfo.IME_ACTION_DONE) }
    }

    @Test fun `shift and mode do not edit text`() {
        assertFalse(handler.handle(KeyItem("shift", "Shift", type = KeyType.SHIFT)))
        assertFalse(handler.handle(KeyItem("mode", "Mode", type = KeyType.MODE)))
        verify { connection wasNot Called }
    }
}
