package com.flowkeyboard.android.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowkeyboard.android.data.repository.DictionaryRepository
import com.flowkeyboard.android.data.repository.SettingsRepository
import com.flowkeyboard.android.engine.FeedbackEngine
import com.flowkeyboard.android.model.KeyboardSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(
    private val settings: SettingsRepository,
    dictionary: DictionaryRepository,
    private val feedback: FeedbackEngine,
) : ViewModel() {
    val state = combine(settings.settings, dictionary.summary, ::MainUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())
    fun updateSettings(transform: (KeyboardSettings) -> KeyboardSettings) { viewModelScope.launch { settings.update(transform) } }
    fun previewVibration(level: Float) { feedback.previewVibration(level) }
    override fun onCleared() {
        super.onCleared()
        feedback.close()
    }
}
