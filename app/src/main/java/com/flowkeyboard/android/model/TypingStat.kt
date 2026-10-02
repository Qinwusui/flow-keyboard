package com.flowkeyboard.android.model

data class TypingStat(
    val id: Long = 0,
    val startedAtMillis: Long,
    val durationMillis: Long,
    val keystrokes: Int,
    val committedCharacters: Int,
) {
    val wordsPerMinute: Double
        get() = if (durationMillis <= 0 || committedCharacters <= 0) 0.0
            else committedCharacters / 5.0 * 60_000.0 / durationMillis
}

data class TypingSummary(val keystrokes: Long = 0, val characters: Long = 0, val durationMillis: Long = 0) {
    val wordsPerMinute: Double get() = if (durationMillis <= 0) 0.0 else characters / 5.0 * 60_000.0 / durationMillis
}
