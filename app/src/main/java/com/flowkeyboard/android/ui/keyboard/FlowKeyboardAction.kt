package com.flowkeyboard.android.ui.keyboard

import com.flowkeyboard.android.model.*

sealed interface FlowKeyboardAction {
    data class PressKey(val key: KeyItem) : FlowKeyboardAction
    data class SelectCandidate(val candidate: DictionaryItem) : FlowKeyboardAction
    data class SetSpeed(val speed: Float) : FlowKeyboardAction
    data object ReverseDirection : FlowKeyboardAction
    data object ToggleCruise : FlowKeyboardAction
    data object ToggleOrientation : FlowKeyboardAction
    data object ToggleInputMode : FlowKeyboardAction
    data object BeltTick : FlowKeyboardAction
}
