package com.flowkeyboard.android.ui.main

import com.flowkeyboard.android.model.KeyboardSettings
import com.flowkeyboard.android.model.TypingSummary

data class MainUiState(val settings: KeyboardSettings = KeyboardSettings(), val summary: TypingSummary = TypingSummary())
