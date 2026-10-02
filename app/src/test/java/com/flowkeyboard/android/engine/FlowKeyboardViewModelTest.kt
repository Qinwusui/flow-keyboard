package com.flowkeyboard.android.engine

import android.app.Application
import android.view.inputmethod.InputConnection
import androidx.lifecycle.ViewModelStore
import com.flowkeyboard.android.data.repository.DictionaryRepository
import com.flowkeyboard.android.data.repository.SettingsRepository
import com.flowkeyboard.android.model.*
import com.flowkeyboard.android.ui.keyboard.*
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class FlowKeyboardViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val persistentScope = CoroutineScope(SupervisorJob() + dispatcher)
    private val viewModels = ViewModelStore()
    private val settingsFlow = MutableStateFlow(KeyboardSettings(inputMode = InputMode.PINYIN))
    private val settings = object : SettingsRepository {
        override val settings = settingsFlow
        override suspend fun update(transform: (KeyboardSettings) -> KeyboardSettings) { settingsFlow.update(transform) }
    }
    private val dictionary = mockk<DictionaryRepository>()
    private val feedback = mockk<FeedbackEngine>(relaxed = true)
    private val connection = mockk<InputConnection>(relaxed = true)
    private val candidate = DictionaryItem(1, "你好", "nihao", 50)
    private lateinit var viewModel: FlowKeyboardViewModel
    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { dictionary.summary } returns flowOf(TypingSummary())
        every { dictionary.search(any()) } answers { flowOf(if (firstArg<String>() == "nihao") listOf(candidate) else emptyList()) }
        coEvery { dictionary.learn(any()) } just Runs
        coEvery { dictionary.record(any()) } just Runs
        every { connection.commitText(any(), 1) } returns true
        every { connection.setComposingText(any(), 1) } returns true
        viewModel = FlowKeyboardViewModel(settings, dictionary, PinyinEngine(), feedback, persistentScope)
        viewModels.put("keyboard", viewModel)
        viewModel.attach(KeyboardActionHandler({ connection }))
    }
    @After fun tearDown() {
        viewModels.clear(); persistentScope.cancel(); Dispatchers.resetMain()
    }
    private fun type(text: String) { text.forEach { viewModel.dispatch(FlowKeyboardAction.PressKey(KeyItem(it.toString(), it.toString()))) } }
    @Test fun `space commits first candidate and learns selection`() = runTest(dispatcher) {
        runCurrent(); type("nihao"); runCurrent()
        assertEquals(listOf(candidate), viewModel.state.value.candidates)
        viewModel.dispatch(FlowKeyboardAction.PressKey(KeyItem("space", "Space", " ", KeyType.SPACE)))
        runCurrent()
        verify { connection.commitText("你好", 1) }
        coVerify(exactly = 1) { dictionary.learn(candidate) }
        assertEquals("", viewModel.state.value.composing)
    }
    @Test fun `composition backspace does not delete surrounding committed text`() = runTest(dispatcher) {
        runCurrent(); type("ni"); runCurrent()
        viewModel.dispatch(FlowKeyboardAction.PressKey(KeyItem("delete", "⌫", "", KeyType.BACKSPACE)))
        runCurrent()
        assertEquals("n", viewModel.state.value.composing)
        verify { connection.setComposingText("n", 1) }
        verify(exactly = 0) { connection.deleteSurroundingText(any(), any()) }
    }
    @Test fun `unknown pinyin commits raw text on space`() = runTest(dispatcher) {
        runCurrent(); type("xxxx"); runCurrent()
        viewModel.dispatch(FlowKeyboardAction.PressKey(KeyItem("space", "Space", " ", KeyType.SPACE)))
        runCurrent(); verify { connection.commitText("xxxx", 1) }
        coVerify(exactly = 0) { dictionary.learn(any()) }
    }
    @Test fun `private fields force English and suppress statistics and learning`() = runTest(dispatcher) {
        viewModel.attach(KeyboardActionHandler({ connection }), privateInput = true)
        runCurrent(); type("nihao"); runCurrent(); viewModel.finishSession(); runCurrent()
        verify { connection.commitText("n", 1) }
        verify(exactly = 0) { connection.setComposingText(any(), any()) }
        coVerify(exactly = 0) { dictionary.learn(any()) }
        coVerify(exactly = 0) { dictionary.record(any()) }
    }
    @Test fun `completed session records strokes and committed character count only once`() = runTest(dispatcher) {
        settingsFlow.value = KeyboardSettings(); runCurrent()
        type("hello"); runCurrent(); viewModel.finishSession(); viewModel.finishSession(); runCurrent()
        coVerify(exactly = 1) { dictionary.record(match { it.keystrokes == 5 && it.committedCharacters == 5 && it.durationMillis > 0 }) }
    }
    @Test fun `changing cursor clears buffer without deleting text`() = runTest(dispatcher) {
        runCurrent(); type("ni"); runCurrent(); viewModel.resetComposition(); runCurrent()
        assertEquals("", viewModel.state.value.composing)
        verify { connection.finishComposingText() }
        verify(exactly = 0) { connection.deleteSurroundingText(any(), any()) }
    }

    @Test fun `immediate space waits for current dictionary result`() = runTest(dispatcher) {
        every { dictionary.search("nihao") } returns flow { delay(50); emit(listOf(candidate)) }
        runCurrent(); type("nihao")
        viewModel.dispatch(FlowKeyboardAction.PressKey(KeyItem("space", "Space", " ", KeyType.SPACE)))
        runCurrent(); advanceTimeBy(50); runCurrent()
        verify { connection.commitText("你好", 1) }
        verify(exactly = 0) { connection.commitText("nihao", 1) }
        assertEquals("", viewModel.state.value.composing)
    }

    @Test fun `late candidate result cannot type into a different editor`() = runTest(dispatcher) {
        every { dictionary.search("nihao") } returns flow { delay(50); emit(listOf(candidate)) }
        runCurrent(); type("nihao")
        viewModel.dispatch(FlowKeyboardAction.PressKey(KeyItem("space", "Space", " ", KeyType.SPACE)))
        runCurrent()
        val other = mockk<InputConnection>(relaxed = true)
        viewModel.attach(KeyboardActionHandler({ other }))
        advanceTimeBy(50); runCurrent()
        verify(exactly = 0) { other.commitText(any(), any()) }
    }
}
