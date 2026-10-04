package com.flowkeyboard.android.engine

import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import com.flowkeyboard.android.model.KeyItem
import com.flowkeyboard.android.model.KeyType

class KeyboardActionHandler(
    private val connection: () -> InputConnection?,
    private val editorInfo: () -> EditorInfo? = { null },
) {
    fun commit(text: String): Boolean = connection()?.commitText(text, 1) ?: false
    fun compose(text: String): Boolean = connection()?.setComposingText(text, 1) ?: false
    fun finishComposition() { connection()?.finishComposingText() }
    fun cancelComposition() { compose(""); finishComposition() }

    fun backspace(): Boolean {
        val input = connection() ?: return false
        if (!input.getSelectedText(0).isNullOrEmpty()) return input.commitText("", 1)
        val before = input.getTextBeforeCursor(2, 0)
        val count = if (before != null && before.length >= 2 && Character.isSurrogatePair(before[before.length - 2], before.last())) 2 else 1
        return input.deleteSurroundingText(count, 0)
    }

    fun deleteBeforeCursor(charCount: Int): Boolean {
        val input = connection() ?: return false
        if (charCount <= 0) return true
        if (!input.getSelectedText(0).isNullOrEmpty()) input.commitText("", 1)
        return input.deleteSurroundingText(charCount, 0)
    }

    fun enter(): Boolean {
        val info = editorInfo()
        val action = info?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
        val allowsAction = info != null && (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0
        if (allowsAction && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            if (connection()?.performEditorAction(action) == true) return true
        }
        return commit("\n")
    }

    fun handle(key: KeyItem): Boolean = when (key.type) {
        KeyType.CHARACTER, KeyType.SPACE -> commit(key.value)
        KeyType.BACKSPACE -> backspace()
        KeyType.ENTER -> enter()
        KeyType.SHIFT, KeyType.MODE, KeyType.EMOJI, KeyType.SYMBOL -> false
    }
}
