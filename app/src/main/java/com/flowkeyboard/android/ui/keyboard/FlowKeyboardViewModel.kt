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
    private data class LocalState(
        val composing: String = "",
        val shifted: Boolean = false,
        val strokes: Int = 0,
        val privateInput: Boolean = false,
        val emojiMode: Boolean = false,
        val symbolMode: Boolean = false,
        val associativeWord: String = "",
        val lastCommittedWord: String = "",
        val isCandidateExpanded: Boolean = false,
    )
    private data class Matches(val prefix: String = "", val items: List<DictionaryItem> = emptyList(), val isAssociative: Boolean = false)
    private data class QueuedAction(val action: FlowKeyboardAction, val session: Long)
    private data class CandidateSelectionStep(
        val committedWord: String,
        val pinyinBefore: String,
        val remainingPinyin: String,
    )
    private val selectionHistory = mutableListOf<CandidateSelectionStep>()
    private val local = MutableStateFlow(LocalState())
    private val settingsState = settingsRepository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, KeyboardSettings())
    private val matches = combine(pinyin.composing, local.map { it.associativeWord }.distinctUntilChanged()) { text, associativeWord ->
        text to associativeWord
    }.flatMapLatest { (text, associativeWord) ->
        if (text.isNotEmpty()) {
            dictionary.search(text).map { Matches(text, it, isAssociative = false) }
        } else if (associativeWord.isNotEmpty()) {
            dictionary.predictNext(associativeWord).map { Matches("", it, isAssociative = true) }
        } else {
            flowOf(Matches("", emptyList(), isAssociative = false))
        }
    }
    val state: StateFlow<FlowKeyboardUiState> = combine(settingsState, local, matches, dictionary.summary) { settings, current, candidates, summary ->
        val isAssociative = current.composing.isEmpty() && candidates.isAssociative && candidates.items.isNotEmpty()
        val effectiveCandidates = if (current.composing.isNotEmpty()) {
            candidates.items.takeIf { candidates.prefix == current.composing && !candidates.isAssociative } ?: emptyList()
        } else if (candidates.isAssociative) {
            candidates.items
        } else emptyList()
        FlowKeyboardUiState(
            settings = if (current.privateInput) settings.copy(inputMode = InputMode.ENGLISH) else settings,
            composing = current.composing,
            candidates = effectiveCandidates,
            shifted = current.shifted,
            sessionKeystrokes = current.strokes,
            summary = summary,
            privateInput = current.privateInput,
            emojiMode = current.emojiMode,
            symbolMode = current.symbolMode,
            isAssociative = isAssociative,
            isCandidateExpanded = current.isCandidateExpanded && effectiveCandidates.isNotEmpty(),
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
        selectionHistory.clear()
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
                local.update { it.copy(isCandidateExpanded = false) }
                if (local.value.composing.isNotEmpty() && action.candidate in state.value.candidates) {
                    countStroke(); commitCandidate(action.candidate)
                    feedback.keyPress(state.value.settings)
                } else if (state.value.isAssociative && action.candidate in state.value.candidates) {
                    countStroke()
                    val prev = local.value.lastCommittedWord
                    val word = action.candidate.word
                    commit(word)
                    local.update { it.copy(associativeWord = word, lastCommittedWord = word) }
                    if (prev.isNotEmpty() && !local.value.privateInput) {
                        persistenceScope.launch {
                            dictionary.recordTransition(prev, word)
                            dictionary.learnCompound(prev, word)
                        }
                    }
                    if (!local.value.privateInput) persistenceScope.launch { dictionary.learn(action.candidate) }
                    feedback.keyPress(state.value.settings)
                }
            }
            is FlowKeyboardAction.ToggleCandidateExpansion -> {
                local.update { it.copy(isCandidateExpanded = action.expanded ?: !it.isCandidateExpanded) }
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

    private var composingInEditor = false

    private suspend fun press(key: KeyItem) {
        val input = handler ?: return
        countStroke()
        feedback.keyPress(state.value.settings)
        when (key.type) {
            KeyType.SHIFT -> local.update { it.copy(shifted = !it.shifted) }
            KeyType.MODE -> toggleMode()
            KeyType.EMOJI -> local.update { it.copy(emojiMode = !it.emojiMode, symbolMode = false) }
            KeyType.SYMBOL -> local.update { it.copy(symbolMode = !it.symbolMode, emojiMode = false) }
            KeyType.BACKSPACE -> {
                val lastStep = selectionHistory.lastOrNull()
                if (lastStep != null && pinyin.text == lastStep.remainingPinyin) {
                    selectionHistory.removeAt(selectionHistory.lastIndex)
                    handler?.deleteBeforeCursor(lastStep.committedWord.length)
                    characters = (characters - lastStep.committedWord.codePointCount(0, lastStep.committedWord.length)).coerceAtLeast(0)
                    pinyin.set(lastStep.pinyinBefore)
                    composingInEditor = true
                    input.compose(pinyin.text)
                    syncComposition()
                } else if (pinyin.backspace()) {
                    if (composingInEditor) {
                        input.compose(pinyin.text)
                        if (pinyin.text.isEmpty()) {
                            input.finishComposition()
                            composingInEditor = false
                        }
                    } else if (pinyin.text.isEmpty()) {
                        composingInEditor = false
                    }
                    syncComposition()
                } else if (local.value.associativeWord.isNotEmpty()) {
                    local.update { it.copy(associativeWord = "", lastCommittedWord = "", isCandidateExpanded = false) }
                } else {
                    selectionHistory.clear()
                    local.update { it.copy(associativeWord = "", lastCommittedWord = "", isCandidateExpanded = false) }
                    input.backspace()
                }
            }
            KeyType.SPACE -> {
                if (pinyin.text.isNotEmpty()) commitTopCandidate()
                else {
                    selectionHistory.clear()
                    local.update { it.copy(associativeWord = "", lastCommittedWord = "") }
                    commit(" ")
                }
            }
            KeyType.ENTER -> {
                if (pinyin.text.isNotEmpty()) {
                    val raw = pinyin.text
                    commit(raw)
                    handler?.finishComposition()
                    composingInEditor = false
                    pinyin.clear()
                    syncComposition()
                    selectionHistory.clear()
                    local.update { it.copy(associativeWord = "", lastCommittedWord = "", isCandidateExpanded = false) }
                    return
                }
                selectionHistory.clear()
                local.update { it.copy(associativeWord = "", lastCommittedWord = "") }
                input.enter()
            }
            KeyType.CHARACTER -> {
                if (!local.value.privateInput && settingsState.value.inputMode == InputMode.PINYIN && key.value.length == 1 &&
                    (key.value[0] in 'a'..'z' || key.value == "'") && !local.value.emojiMode && !local.value.symbolMode) {
                    local.update { it.copy(associativeWord = "") }
                    if (pinyin.append(key.value[0])) {
                        if (composingInEditor || pinyin.text.length == 1) {
                            composingInEditor = true
                            input.compose(pinyin.text)
                        }
                        syncComposition()
                    }
                } else if (!local.value.privateInput && settingsState.value.inputMode == InputMode.PINYIN &&
                    pinyin.text.isNotEmpty() && key.value.length == 1 && key.value[0] in '1'..'9' &&
                    !local.value.emojiMode && !local.value.symbolMode &&
                    commitNthCandidate(key.value[0] - '1')) {
                    // Candidate selected and committed
                } else {
                    if (pinyin.text.isNotEmpty()) {
                        val raw = pinyin.text
                        val digitOrChar = if (local.value.shifted) key.value.uppercase(java.util.Locale.ROOT) else key.value
                        commit(raw + digitOrChar)
                        pinyin.clear()
                        selectionHistory.clear()
                        syncComposition()
                        composingInEditor = false
                        local.update { it.copy(associativeWord = "", lastCommittedWord = "", isCandidateExpanded = false) }
                    } else {
                        selectionHistory.clear()
                        local.update { it.copy(associativeWord = "", lastCommittedWord = "") }
                        commit(if (local.value.shifted) key.value.uppercase(java.util.Locale.ROOT) else key.value)
                    }
                }
            }
        }
    }

    private suspend fun toggleMode() {
        if (local.value.privateInput) return
        if (pinyin.text.isNotEmpty() && !commitTopCandidate()) return
        selectionHistory.clear()
        updateSettings { it.copy(inputMode = if (it.inputMode == InputMode.ENGLISH) InputMode.PINYIN else InputMode.ENGLISH) }
    }

    private suspend fun commitTopCandidate(): Boolean {
        val originalSession = session
        val raw = pinyin.text
        val candidate = state.value.candidates.takeIf { state.value.composing == raw }?.firstOrNull()
            ?: dictionary.search(raw).first().firstOrNull()
        if (originalSession != session || raw != pinyin.text) return false
        return commitCandidate(candidate)
    }

    private suspend fun commitNthCandidate(index: Int): Boolean {
        val originalSession = session
        val raw = pinyin.text
        if (raw.isEmpty()) return false
        val currentMatches = state.value.candidates.takeIf { state.value.composing == raw }
        val candidate = currentMatches?.getOrNull(index) ?: dictionary.search(raw).first().getOrNull(index)
        if (originalSession != session || raw != pinyin.text) return false
        return if (candidate != null) {
            local.update { it.copy(isCandidateExpanded = false) }
            commitCandidate(candidate)
        } else false
    }

    private fun commitCandidate(candidate: DictionaryItem?): Boolean {
        val raw = pinyin.text
        if (raw.isEmpty()) return false
        val word = candidate?.word ?: raw
        val cleanCandidatePinyin = candidate?.pinyin?.lowercase(java.util.Locale.ROOT)?.replace("'", "") ?: ""
        val cleanRaw = raw.lowercase(java.util.Locale.ROOT).replace("'", "")

        val isPartial = candidate != null && cleanCandidatePinyin.isNotEmpty() &&
            cleanRaw.startsWith(cleanCandidatePinyin) && cleanRaw.length > cleanCandidatePinyin.length

        if (isPartial) {
            val previousPinyin = raw
            if (commit(word)) {
                handler?.finishComposition()
                composingInEditor = false
                val remaining = if (raw.startsWith(cleanCandidatePinyin)) {
                    raw.substring(cleanCandidatePinyin.length).trimStart('\'')
                } else {
                    cleanRaw.substring(cleanCandidatePinyin.length)
                }
                pinyin.set(remaining)
                selectionHistory.add(
                    CandidateSelectionStep(
                        committedWord = word,
                        pinyinBefore = previousPinyin,
                        remainingPinyin = remaining
                    )
                )
                syncComposition()
                if (!local.value.privateInput) persistenceScope.launch { dictionary.learn(candidate) }
                return true
            }
            return false
        } else {
            if (commit(word)) {
                handler?.finishComposition()
                composingInEditor = false
                pinyin.clear()
                selectionHistory.clear()
                val prev = local.value.lastCommittedWord
                local.update { it.copy(composing = "", associativeWord = word, lastCommittedWord = word) }
                if (!local.value.privateInput) {
                    persistenceScope.launch {
                        if (candidate != null) dictionary.learn(candidate)
                        if (prev.isNotEmpty() && prev != word) {
                            dictionary.recordTransition(prev, word)
                            dictionary.learnCompound(prev, word)
                        }
                    }
                }
                return true
            }
            return false
        }
    }

    private fun commit(text: String): Boolean {
        val success = handler?.commit(text) == true
        if (success) characters += text.codePointCount(0, text.length)
        return success
    }
    private fun syncComposition() { local.update { it.copy(composing = pinyin.text) } }
    fun resetComposition() { session++; handler?.finishComposition(); composingInEditor = false; pinyin.clear(); selectionHistory.clear(); local.update { it.copy(composing = "", associativeWord = "", lastCommittedWord = "") } }
    private fun countStroke() {
        if (startedAt == 0L) { startedAt = System.currentTimeMillis(); startedElapsed = SystemClock.elapsedRealtime() }
        local.update { it.copy(strokes = it.strokes + 1) }
    }

    fun finishSession() {
        session++
        // finishComposingText leaves the raw span committed when the editor closes.
        characters += pinyin.text.codePointCount(0, pinyin.text.length)
        handler?.finishComposition()
        composingInEditor = false
        selectionHistory.clear()
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
