package com.flowkeyboard.android.model

data class KeyTrack(val id: Int, val name: String, val keys: List<KeyItem>) {
    companion object {
        fun create(order: KeyOrder = KeyOrder.QWERTY): List<KeyTrack> {
            fun characters(text: String) = text.map { KeyItem("char_$it", it.toString()) }
            val letters = if (order == KeyOrder.QWERTY) listOf("qwertyuiop", "asdfghjklzxcvbnm")
                else listOf("abcdefghijklm", "nopqrstuvwxyz")
            return listOf(
                KeyTrack(0, "Numbers and symbols", characters("1234567890.,!?@#-_'")),
                KeyTrack(1, "Letters, first lane", characters(letters[0])),
                KeyTrack(2, "Letters, second lane", characters(letters[1])),
                KeyTrack(3, "Functions", listOf(
                    KeyItem("space", "Space", " ", KeyType.SPACE),
                    KeyItem("backspace", "⌫", "", KeyType.BACKSPACE),
                    KeyItem("enter", "↵", "\n", KeyType.ENTER),
                    KeyItem("shift", "⇧", "", KeyType.SHIFT),
                    KeyItem("mode", "中/EN", "", KeyType.MODE),
                )),
            )
        }
    }
}
