package com.flowkeyboard.android.ui.keyboard

import com.flowkeyboard.android.model.*

data class FlowKeyboardUiState(
    val settings: KeyboardSettings = KeyboardSettings(),
    val composing: String = "",
    val candidates: List<DictionaryItem> = emptyList(),
    val shifted: Boolean = false,
    val sessionKeystrokes: Int = 0,
    val summary: TypingSummary = TypingSummary(),
    val privateInput: Boolean = false,
) {
    val tracks: List<KeyTrack> get() = KeyTrack.create(settings.keyOrder)
}
