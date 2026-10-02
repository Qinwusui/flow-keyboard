package com.flowkeyboard.android.model

enum class BeltOrientation { HORIZONTAL, VERTICAL }
enum class KeyOrder { QWERTY, ALPHABETICAL }
enum class InputMode { ENGLISH, PINYIN }

data class KeyboardSettings(
    val speed: Float = 64f,
    val direction: Int = 1,
    val cruising: Boolean = true,
    val orientation: BeltOrientation = BeltOrientation.HORIZONTAL,
    val keyOrder: KeyOrder = KeyOrder.QWERTY,
    val inputMode: InputMode = InputMode.ENGLISH,
    val soundEnabled: Boolean = false,
    val hapticsEnabled: Boolean = true,
) {
    fun normalized() = copy(speed = if (speed.isFinite()) speed.coerceIn(0f, 240f) else 64f,
        direction = if (direction < 0) -1 else 1)
}
