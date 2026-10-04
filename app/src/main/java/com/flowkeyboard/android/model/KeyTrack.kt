package com.flowkeyboard.android.model

data class KeyTrack(val id: Int, val name: String, val keys: List<KeyItem>) {
    companion object {
        fun create(
            order: KeyOrder = KeyOrder.QWERTY,
            inputMode: InputMode = InputMode.PINYIN,
            emojiMode: Boolean = false,
            symbolMode: Boolean = false,
        ): List<KeyTrack> {
            fun characters(text: String, idPrefix: String = "char") =
                text.mapIndexed { index, char -> KeyItem("${idPrefix}_$index", char.toString()) }
            fun emojiList(emojis: List<String>, idPrefix: String = "emoji") =
                emojis.mapIndexed { index, emoji -> KeyItem("${idPrefix}_$index", emoji) }

            val emojiToggleLabel = if (emojiMode) "🔤" else "🙂"
            val symbolToggleLabel = if (symbolMode) "ABC" else "?123"

            val functionKeys = listOf(
                KeyItem("shift", "⇧", "", KeyType.SHIFT),
                KeyItem("mode", "中/EN", "", KeyType.MODE),
                KeyItem("symbol", symbolToggleLabel, "", KeyType.SYMBOL),
                KeyItem("emoji", emojiToggleLabel, "", KeyType.EMOJI),
                KeyItem("space", "Space", " ", KeyType.SPACE),
                KeyItem("backspace", "⌫", "", KeyType.BACKSPACE),
                KeyItem("enter", "↵", "\n", KeyType.ENTER),
            )

            if (symbolMode) {
                val mathSymbols = "1234567890+-×÷=%~^<>"
                val chinesePunctuation = "，。？！、：；……——“”‘’（）【】《》"
                val englishPunctuation = ",.?!:;/\\-_@#$&*'`\"()[]{}"
                val specialSymbols = "①②③④⑤⑥⑦⑧⑨⑩￥$€£¢℃℉★☆◆◇▲▼√×±°"
                return listOf(
                    KeyTrack(0, "Numbers and math", characters(mathSymbols, "math")),
                    KeyTrack(1, "Chinese punctuation", characters(chinesePunctuation, "cn_punct")),
                    KeyTrack(2, "English and code symbols", characters(englishPunctuation, "en_punct")),
                    KeyTrack(3, "Special symbols and numbers", characters(specialSymbols, "spec")),
                    KeyTrack(4, "Functions", functionKeys),
                )
            }

            if (emojiMode) {
                val numberSymbols = "1234567890+-*/%="
                val symbols = if (inputMode == InputMode.PINYIN) {
                    "，。？！、：；……~@#￥%&*（）"
                } else {
                    ".,!?@#-_'~`$%^&*()+=\\/|<>{}[]"
                }
                val facialEmojis = listOf(
                    "😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂", "🙂", "🙃", "😉", "😊", "😇", "🥰", "😍", "🤩",
                    "😘", "😗", "😚", "😙", "😋", "😛", "😜", "🤪", "😝", "🤗", "🤭", "🫢", "🫣", "🤫", "🤔", "🫡",
                    "🤐", "🤨", "😐", "😑", "😶", "🫥", "😏", "😒", "🙄", "😬", "😮‍💨", "🤥", "😌", "😔", "😪", "🤤",
                    "😴", "😷", "🤒", "🤕", "🤢", "🤮", "🤧", "🥵", "🥶", "🥴", "😵", "😵‍💫", "🤯", "🤠", "🥳", "🥸",
                    "😎", "🤓", "🧐", "😕", "🫤", "😟", "🙁", "😮", "😯", "😲", "😳", "🥺", "🥹", "😦", "😧", "😨",
                    "😰", "😥", "😢", "😭", "😱", "😖", "😣", "😞", "😓", "😩", "😫", "🥱", "😤", "😡", "😠", "🤬"
                )
                val objectEmojis = listOf(
                    "👍", "👎", "👌", "🤌", "✌️", "🤞", "🫰", "🤟", "🤘", "🤙", "👈", "👉", "👆", "👇", "☝️", "✋",
                    "🤚", "🖐️", "🖖", "👋", "👏", "🙌", "👐", "🤲", "🤝", "🙏", "💪", "❤️", "🧡", "💛", "💚", "💙",
                    "💜", "🖤", "🤍", "🤎", "💔", "❤️‍🔥", "❤️‍🩹", "❣️", "💕", "💞", "💓", "💗", "💖", "💘", "💝", "🎉",
                    "🎊", "✨", "🌟", "⭐", "🔥", "💥", "💯", "💢", "💨", "💫", "💬", "💭", "☕", "🍺", "🍻", "🥂",
                    "🍷", "🍸", "🍹", "🎂", "🍰", "🍕", "🍔", "🍟", "🍿", "🎵", "🎶", "🎈", "🎁", "📱", "💻", "💡"
                )
                return listOf(
                    KeyTrack(0, "Numbers", characters(numberSymbols, "num")),
                    KeyTrack(1, "Common punctuation", characters(symbols, "sym")),
                    KeyTrack(2, "Facial emojis", emojiList(facialEmojis, "face")),
                    KeyTrack(3, "Hand and object emojis", emojiList(objectEmojis, "obj")),
                    KeyTrack(4, "Functions", functionKeys),
                )
            }

            val letters = if (order == KeyOrder.QWERTY) {
                listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
            } else {
                listOf("abcdefghi", "jklmnopqr", "stuvwxyz")
            }
            val row0NumberSymbols = "1234567890'+-*/.@#%="

            return listOf(
                KeyTrack(0, "Numbers and symbols", characters(row0NumberSymbols, "num")),
                KeyTrack(1, "Letters, first lane", characters(letters[0], "row0")),
                KeyTrack(2, "Letters, second lane", characters(letters[1], "row1")),
                KeyTrack(3, "Letters, third lane", characters(letters[2], "row2")),
                KeyTrack(4, "Functions", functionKeys),
            )
        }
    }
}
