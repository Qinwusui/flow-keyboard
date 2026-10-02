package com.flowkeyboard.android.ui.keyboard

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowkeyboard.android.data.repository.DictionaryRepository
import com.flowkeyboard.android.data.repository.SettingsRepository
import com.flowkeyboard.android.engine.FeedbackEngine
import com.flowkeyboard.android.engine.KeyboardActionHandler
import com.flowkeyboard.android.engine.PinyinEngine
import com.flowkeyboard.android.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

@OptIn(ExperimentalCoroutinesApi::class)
class FlowKeyboardViewModel(
    private val settingsRepository: SettingsRepository,
    private val dictionary: DictionaryRepository,
    private val pinyin: PinyinEngine,
    private val feedback: FeedbackEngine,
    private val persistenceScope: CoroutineScope,
) : ViewModel() {
    private data class LocalState(val composing: String = "", val shifted: Boolean = false, val strokes: Int = 0, val privateInput: Boolean = false)
    private data class Matches(val prefix: String = "", val items: List<DictionaryItem> = emptyList())
    private data class QueuedAction(val action: FlowKeyboardAction, val session: Long)
    private val local = MutableStateFlow(LocalState())
    private val settingsState = settingsRepository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, KeyboardSettings())
    private val matches = pinyin.composing.flatMapLatest { text ->
        dictionary.search(text).map { Matches(text, it) }
    }
    val state: StateFlow<FlowKeyboardUiState> = combine(settingsState, local, matches, dictionary.summary) { settings, current, candidates, summary ->
        FlowKeyboardUiState(
            settings = if (current.privateInput) settings.copy(inputMode = InputMode.ENGLISH) else settings,
            composing = current.composing,
            candidates = candidates.items.takeIf { candidates.prefix == current.composing } ?: emptyList(),
            shifted = current.shifted, sessionKeystrokes = current.strokes, summary = summary, privateInput = current.privateInput,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, FlowKeyboardUiState())

    private var handler: KeyboardActionHandler? = null
    private var startedAt = 0L
    private var startedElapsed = 0L
    private var characters = 0
    private var session = 0L
    private val actions = Channel<QueuedAction>(Channel.UNLIMITED)

    init {
        viewModelScope.launch {
            for (queued in actions) {
                if (queued.session != session && (queued.action is FlowKeyboardAction.PressKey || queued.action is FlowKeyboardAction.SelectCandidate)) continue
                handleAction(queued.action)
                yield()
            }
        }
    }

    fun attach(handler: KeyboardActionHandler, privateInput: Boolean = false) {
        finishSession()
        this.handler = handler
        pinyin.clear()
        local.value = LocalState(privateInput = privateInput)
    }

    fun dispatch(action: FlowKeyboardAction) {
        if (action == FlowKeyboardAction.BeltTick) feedback.beltTick(settingsState.value)
        else actions.trySend(QueuedAction(action, session))
    }

    private suspend fun handleAction(action: FlowKeyboardAction) {
        when (action) {
            is FlowKeyboardAction.PressKey -> press(action.key)
            is FlowKeyboardAction.SelectCandidate -> {
                if (local.value.composing.isNotEmpty() && action.candidate in state.value.candidates) {
                    countStroke(); commitCandidate(action.candidate)
                    feedback.keyPress(state.value.settings)
                }
            }
            is FlowKeyboardAction.SetSpeed -> updateSettings { it.copy(speed = action.speed) }
            FlowKeyboardAction.ReverseDirection -> updateSettings { it.copy(direction = -it.direction) }
            FlowKeyboardAction.ToggleCruise -> updateSettings { it.copy(cruising = !it.cruising) }
            FlowKeyboardAction.ToggleOrientation -> updateSettings {
                it.copy(orientation = if (it.orientation == BeltOrientation.HORIZONTAL) BeltOrientation.VERTICAL else BeltOrientation.HORIZONTAL)
            }
            FlowKeyboardAction.ToggleInputMode -> toggleMode()
            FlowKeyboardAction.BeltTick -> feedback.beltTick(state.value.settings)
        }
    }

    private suspend fun updateSettings(transform: (KeyboardSettings) -> KeyboardSettings) {
        settingsRepository.update(transform)
    }

    private suspend fun press(key: KeyItem) {
        val input = handler ?: return
        countStroke()
        feedback.keyPress(state.value.settings)
        when (key.type) {
            KeyType.SHIFT -> local.update { it.copy(shifted = !it.shifted) }
            KeyType.MODE -> toggleMode()
            KeyType.BACKSPACE -> {
                if (pinyin.backspace()) {
                    input.compose(pinyin.text)
                    if (pinyin.text.isEmpty()) input.finishComposition()
                    syncComposition()
                } else input.backspace()
            }
            KeyType.SPACE -> {
                if (pinyin.text.isNotEmpty()) commitTopCandidate()
                else commit(" ")
            }
            KeyType.ENTER -> {
                if (pinyin.text.isNotEmpty() && !commitTopCandidate()) return
                input.enter()
            }
            KeyType.CHARACTER -> {
                if (!local.value.privateInput && settingsState.value.inputMode == InputMode.PINYIN && key.value.length == 1 &&
                    (key.value[0] in 'a'..'z' || key.value == "'")) {
                    if (pinyin.append(key.value[0])) { input.compose(pinyin.text); syncComposition() }
                } else {
                    if (pinyin.text.isNotEmpty() && !commitTopCandidate()) return
                    commit(if (local.value.shifted) key.value.uppercase(java.util.Locale.ROOT) else key.value)
                }
            }
        }
    }

    private suspend fun toggleMode() {
        if (local.value.privateInput) return
        if (pinyin.text.isNotEmpty() && !commitTopCandidate()) return
        updateSettings { it.copy(inputMode = if (it.inputMode == InputMode.ENGLISH) InputMode.PINYIN else InputMode.ENGLISH) }
    }

    private suspend fun commitTopCandidate(): Boolean {
        val originalSession = session
        val raw = pinyin.text
        val candidate = dictionary.search(raw).first().firstOrNull()
        if (originalSession != session || raw != pinyin.text) return false
        return commitCandidate(candidate)
    }

    private fun commitCandidate(candidate: DictionaryItem?): Boolean {
        val raw = pinyin.text
        if (raw.isEmpty()) return false
        if (commit(candidate?.word ?: raw)) {
            handler?.finishComposition()
            pinyin.clear(); syncComposition()
            if (candidate != null && !local.value.privateInput) persistenceScope.launch { dictionary.learn(candidate) }
            return true
        }
        return false
    }

    private fun commit(text: String): Boolean {
        val success = handler?.commit(text) == true
        if (success) characters += text.codePointCount(0, text.length)
        return success
    }
    private fun syncComposition() { local.update { it.copy(composing = pinyin.text) } }
    fun resetComposition() { session++; handler?.finishComposition(); pinyin.clear(); syncComposition() }
    private fun countStroke() {
        if (startedAt == 0L) { startedAt = System.currentTimeMillis(); startedElapsed = SystemClock.elapsedRealtime() }
        local.update { it.copy(strokes = it.strokes + 1) }
    }

    fun finishSession() {
        session++
        // finishComposingText leaves the raw span committed when the editor closes.
        characters += pinyin.text.codePointCount(0, pinyin.text.length)
        handler?.finishComposition()
        if (local.value.strokes > 0 && !local.value.privateInput) {
            val stat = TypingStat(startedAtMillis = startedAt, durationMillis = (SystemClock.elapsedRealtime() - startedElapsed).coerceAtLeast(1),
                keystrokes = local.value.strokes, committedCharacters = characters)
            persistenceScope.launch { dictionary.record(stat) }
        }
        pinyin.clear(); characters = 0; startedAt = 0; startedElapsed = 0
        local.value = LocalState()
        handler = null
    }

    override fun onCleared() { finishSession(); actions.close(); feedback.close() }
}
