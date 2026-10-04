package com.flowkeyboard.android.model

enum class BeltOrientation { HORIZONTAL, VERTICAL }
enum class KeyOrder { QWERTY, ALPHABETICAL }
enum class InputMode { ENGLISH, PINYIN }

enum class KeyboardSkin(
    val displayName: String,
    val description: String,
    val previewPrimary: Long,
    val previewKeycap: Long,
    val previewBackground: Long,
) {
    SYSTEM("系统跟随", "随系统深浅模式动态适配", 0xFF0284C7, 0xFF334155, 0xFF0F172A),
    OBSIDIAN_PURE("纯粹黑曜", "极简暗夜纯黑与天蓝光韵", 0xFF38BDF8, 0xFF21262D, 0xFF0B0E14),
    GLACIER_CLEAN("冰川素白", "北欧冰川清透白与群青蓝", 0xFF0284C7, 0xFFFFFFFF, 0xFFF1F5F9),
    CYBER_AURORA("极光赛博", "深邃暗夜与高饱和霓虹青", 0xFF00F0FF, 0xFF192536, 0xFF0B1118),
    DUSK_SUNSET("落日暖霞", "暮色暖棕与暮光珊瑚橙", 0xFFFB923C, 0xFF2B211E, 0xFF15100E),
    MORANDI_MINT("薄荷幽境", "莫兰迪墨绿与清爽薄荷绿", 0xFF34D399, 0xFF1B2B24, 0xFF0D1612),
    SAKURA_BLOSSOM("樱落初雪", "温润初雪白与落樱胭粉", 0xFFE11D48, 0xFFFFFFFF, 0xFFFFF1F4),
    IRIS_TWILIGHT("鸢尾夜紫", "神秘深邃夜紫与流光鸢尾", 0xFFA855F7, 0xFF231C38, 0xFF100C1B),
}

data class KeyboardSettings(
    val speed: Float = 64f,
    val direction: Int = 1,
    val cruising: Boolean = true,
    val orientation: BeltOrientation = BeltOrientation.HORIZONTAL,
    val keyOrder: KeyOrder = KeyOrder.QWERTY,
    val inputMode: InputMode = InputMode.PINYIN,
    val soundEnabled: Boolean = false,
    val hapticsEnabled: Boolean = true,
    val vibrationLevel: Float = 0.5f,
    val skin: KeyboardSkin = KeyboardSkin.SYSTEM,
    val customBackgroundPath: String? = null,
    val backgroundBrightness: Float = 0.65f,
) {
    fun normalized() = copy(
        speed = if (speed.isFinite()) speed.coerceIn(0f, 240f) else 64f,
        direction = if (direction < 0) -1 else 1,
        vibrationLevel = if (vibrationLevel.isFinite()) vibrationLevel.coerceIn(0f, 1f) else 0.5f,
        backgroundBrightness = if (backgroundBrightness.isFinite()) backgroundBrightness.coerceIn(0.1f, 1f) else 0.65f,
    )
}

