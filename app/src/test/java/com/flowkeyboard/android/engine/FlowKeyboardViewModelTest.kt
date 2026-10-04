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
        every { dictionary.predictNext(any()) } returns flowOf(emptyList())
        coEvery { dictionary.learn(any()) } just Runs
        coEvery { dictionary.record(any()) } just Runs
        coEvery { dictionary.recordTransition(any(), any()) } just Runs
        coEvery { dictionary.learnCompound(any(), any()) } just Runs
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
    @Test fun `typing wo and pressing backspace turns into w, after committing wo first backspace clears associative candidates and second deletes committed character`() = runTest(dispatcher) {
        val woCandidate = DictionaryItem(1, "我", "wo", 100)
        every { dictionary.search("wo") } returns flowOf(listOf(woCandidate))
        every { dictionary.predictNext("我") } returns flowOf(listOf(DictionaryItem(2, "们", "men", 90)))

        runCurrent(); type("wo"); runCurrent()
        assertEquals("wo", viewModel.state.value.composing)
        assertEquals(listOf(woCandidate), viewModel.state.value.candidates)

        // 输入wo时 按下删除键后 应该变成w
        viewModel.dispatch(FlowKeyboardAction.PressKey(KeyItem("delete", "⌫", "", KeyType.BACKSPACE)))
        runCurrent()
        assertEquals("w", viewModel.state.value.composing)
        verify { connection.setComposingText("w", 1) }
        verify(exactly = 0) { connection.deleteSurroundingText(any(), any()) }

        // 继续输入o，然后上屏"我"
        type("o"); runCurrent()
        assertEquals("wo", viewModel.state.value.composing)
        assertEquals(listOf(woCandidate), viewModel.state.value.candidates)

        viewModel.dispatch(FlowKeyboardAction.SelectCandidate(woCandidate))
        runCurrent()
        verify { connection.commitText("我", 1) }
        assertEquals("", viewModel.state.value.composing)
        assertEquals(true, viewModel.state.value.isAssociative)
        assertEquals(listOf("们"), viewModel.state.value.candidates.map { it.word })

        // 点我字上屏时 点击删除键才清空候选（不删掉我字）
        viewModel.dispatch(FlowKeyboardAction.PressKey(KeyItem("delete", "⌫", "", KeyType.BACKSPACE)))
        runCurrent()
        assertEquals(false, viewModel.state.value.isAssociative)
        assertTrue(viewModel.state.value.candidates.isEmpty())
        verify(exactly = 0) { connection.deleteSurroundingText(any(), any()) }

        // 再点击删除才删掉我字
        viewModel.dispatch(FlowKeyboardAction.PressKey(KeyItem("delete", "⌫", "", KeyType.BACKSPACE)))
        runCurrent()
        verify(exactly = 1) { connection.deleteSurroundingText(1, 0) }
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

    @Test fun `selecting partial candidate commits character and preserves remaining pinyin in buffer without leaking pinyin to editor`() = runTest(dispatcher) {
        val jianCandidate = DictionaryItem(10, "键", "jian", 48)
        val jianpanCandidate = DictionaryItem(11, "键盘", "jianpan", 49)
        val panCandidate = DictionaryItem(12, "盘", "pan", 48)
        every { dictionary.search("jianpan") } returns flowOf(listOf(jianpanCandidate, jianCandidate))
        every { dictionary.search("pan") } returns flowOf(listOf(panCandidate))

        runCurrent()
        type("jianpan")
        runCurrent()

        assertEquals("jianpan", viewModel.state.value.composing)
        assertEquals(listOf(jianpanCandidate, jianCandidate), viewModel.state.value.candidates)

        viewModel.dispatch(FlowKeyboardAction.SelectCandidate(jianCandidate))
        runCurrent()

        verify { connection.commitText("键", 1) }
        verify(exactly = 0) { connection.setComposingText("pan", 1) }
        assertEquals("pan", viewModel.state.value.composing)

        viewModel.dispatch(FlowKeyboardAction.SelectCandidate(panCandidate))
        runCurrent()

        verify { connection.commitText("盘", 1) }
        assertEquals("", viewModel.state.value.composing)
    }

    @Test fun `emoji key toggles emoji mode in UI state`() = runTest(dispatcher) {
        assertFalse(viewModel.state.value.emojiMode)
        viewModel.dispatch(FlowKeyboardAction.PressKey(KeyItem("emoji", "🙂", "", KeyType.EMOJI)))
        runCurrent()
        assertTrue(viewModel.state.value.emojiMode)
        viewModel.dispatch(FlowKeyboardAction.PressKey(KeyItem("emoji", "🔤", "", KeyType.EMOJI)))
        runCurrent()
        assertFalse(viewModel.state.value.emojiMode)
    }

    @Test fun `committing candidate triggers predictive candidates which can be committed directly`() = runTest(dispatcher) {
        val assocCandidate = DictionaryItem(99, "世界", "", 50)
        every { dictionary.predictNext("你好") } returns flowOf(listOf(assocCandidate))
        every { dictionary.predictNext("世界") } returns flowOf(emptyList())

        runCurrent()
        type("nihao")
        runCurrent()

        viewModel.dispatch(FlowKeyboardAction.SelectCandidate(candidate))
        runCurrent()

        verify { connection.commitText("你好", 1) }
        assertEquals("", viewModel.state.value.composing)
        assertTrue(viewModel.state.value.isAssociative)
        assertEquals(listOf(assocCandidate), viewModel.state.value.candidates)

        viewModel.dispatch(FlowKeyboardAction.SelectCandidate(assocCandidate))
        runCurrent()

        verify { connection.commitText("世界", 1) }
    }

    @Test fun `candidate expansion toggles and collapses on candidate selection`() = runTest(dispatcher) {
        runCurrent()
        type("nihao")
        runCurrent()

        assertEquals(listOf(candidate), viewModel.state.value.candidates)
        assertFalse(viewModel.state.value.isCandidateExpanded)

        viewModel.dispatch(FlowKeyboardAction.ToggleCandidateExpansion(true))
        runCurrent()
        assertTrue(viewModel.state.value.isCandidateExpanded)

        viewModel.dispatch(FlowKeyboardAction.SelectCandidate(candidate))
        runCurrent()
        assertFalse(viewModel.state.value.isCandidateExpanded)
        verify { connection.commitText("你好", 1) }
    }

    @Test fun `digit key commits raw pinyin plus digit when composing is active`() = runTest(dispatcher) {
        runCurrent()
        type("ni")
        runCurrent()
        assertEquals("ni", viewModel.state.value.composing)

        viewModel.dispatch(FlowKeyboardAction.PressKey(KeyItem("num_1", "1")))
        runCurrent()

        verify { connection.commitText("ni1", 1) }
        assertEquals("", viewModel.state.value.composing)
    }

    @Test fun `digit key commits corresponding candidate when composing`() = runTest(dispatcher) {
        val candidate1 = DictionaryItem(1L, "密码", "mima", 100L)
        val candidate2 = DictionaryItem(2L, "弥漫", "miman", 90L)
        every { dictionary.search("mima") } returns flowOf(listOf(candidate1, candidate2))

        runCurrent()
        type("mima")
        runCurrent()
        assertEquals("mima", viewModel.state.value.composing)

        // Press '2' to select the 2nd candidate
        viewModel.dispatch(FlowKeyboardAction.PressKey(KeyItem("num_2", "2")))
        runCurrent()

        verify { connection.commitText("弥漫", 1) }
        assertEquals("", viewModel.state.value.composing)
    }

    @Test fun `digit key 1 commits first candidate when composing`() = runTest(dispatcher) {
        val candidate1 = DictionaryItem(1L, "密码", "mima", 100L)
        val candidate2 = DictionaryItem(2L, "弥漫", "miman", 90L)
        every { dictionary.search("mima") } returns flowOf(listOf(candidate1, candidate2))

        runCurrent()
        type("mima")
        runCurrent()
        assertEquals("mima", viewModel.state.value.composing)

        // Press '1' to select the 1st candidate
        viewModel.dispatch(FlowKeyboardAction.PressKey(KeyItem("num_1", "1")))
        runCurrent()

        verify { connection.commitText("密码", 1) }
        assertEquals("", viewModel.state.value.composing)
    }

    @Test fun `digit key commits digit directly when not composing`() = runTest(dispatcher) {
        runCurrent()
        viewModel.dispatch(FlowKeyboardAction.PressKey(KeyItem("num_1", "1")))
        runCurrent()

        verify { connection.commitText("1", 1) }
        assertEquals("", viewModel.state.value.composing)
    }

    @Test fun `symbol key toggles symbol mode in UI state`() = runTest(dispatcher) {
        assertFalse(viewModel.state.value.symbolMode)
        viewModel.dispatch(FlowKeyboardAction.PressKey(KeyItem("symbol", "?123", "", KeyType.SYMBOL)))
        runCurrent()
        assertTrue(viewModel.state.value.symbolMode)
        assertFalse(viewModel.state.value.emojiMode)

        viewModel.dispatch(FlowKeyboardAction.PressKey(KeyItem("emoji", "🙂", "", KeyType.EMOJI)))
        runCurrent()
        assertTrue(viewModel.state.value.emojiMode)
        assertFalse(viewModel.state.value.symbolMode)

        viewModel.dispatch(FlowKeyboardAction.PressKey(KeyItem("symbol", "?123", "", KeyType.SYMBOL)))
        runCurrent()
        assertTrue(viewModel.state.value.symbolMode)
        assertFalse(viewModel.state.value.emojiMode)

        viewModel.dispatch(FlowKeyboardAction.PressKey(KeyItem("symbol", "ABC", "", KeyType.SYMBOL)))
        runCurrent()
        assertFalse(viewModel.state.value.symbolMode)
        assertFalse(viewModel.state.value.emojiMode)
    }

    @Test fun `consecutive candidate selections learn Bigram transition and compound phrase`() = runTest(dispatcher) {
        val woCandidate = DictionaryItem(101L, "我", "wo", 50L)
        val ganjueCandidate = DictionaryItem(102L, "感觉", "ganjue", 50L)
        every { dictionary.search("wo") } returns flowOf(listOf(woCandidate))
        every { dictionary.search("ganjue") } returns flowOf(listOf(ganjueCandidate))

        runCurrent()
        type("wo")
        runCurrent()
        viewModel.dispatch(FlowKeyboardAction.SelectCandidate(woCandidate))
        runCurrent()
        verify { connection.commitText("我", 1) }

        type("ganjue")
        runCurrent()
        viewModel.dispatch(FlowKeyboardAction.SelectCandidate(ganjueCandidate))
        runCurrent()
        verify { connection.commitText("感觉", 1) }

        coVerify(atLeast = 1) { dictionary.recordTransition("我", "感觉") }
        coVerify(atLeast = 1) { dictionary.learnCompound("我", "感觉") }
    }

    @Test fun `enter key during composition commits raw pinyin without newline or action`() = runTest(dispatcher) {
        runCurrent()
        type("jianpan")
        runCurrent()
        assertEquals("jianpan", viewModel.state.value.composing)

        viewModel.dispatch(FlowKeyboardAction.PressKey(KeyItem("enter", "↵", "\n", KeyType.ENTER)))
        runCurrent()

        verify { connection.commitText("jianpan", 1) }
        verify { connection.finishComposingText() }
        verify(exactly = 0) { connection.commitText("\n", 1) }
        verify(exactly = 0) { connection.performEditorAction(any()) }
        assertEquals("", viewModel.state.value.composing)
    }

    @Test fun `backspace immediately after partial candidate selection rolls back committed character and restores full pinyin`() = runTest(dispatcher) {
        val jianCandidate = DictionaryItem(10, "键", "jian", 48)
        val jianpanCandidate = DictionaryItem(11, "键盘", "jianpan", 49)
        every { dictionary.search("jianpan") } returns flowOf(listOf(jianpanCandidate, jianCandidate))
        every { dictionary.search("pan") } returns flowOf(emptyList())

        runCurrent()
        type("jianpan")
        runCurrent()
        assertEquals("jianpan", viewModel.state.value.composing)

        viewModel.dispatch(FlowKeyboardAction.SelectCandidate(jianCandidate))
        runCurrent()

        verify { connection.commitText("键", 1) }
        assertEquals("pan", viewModel.state.value.composing)

        viewModel.dispatch(FlowKeyboardAction.PressKey(KeyItem("delete", "⌫", "", KeyType.BACKSPACE)))
        runCurrent()

        verify { connection.deleteSurroundingText(1, 0) }
        verify { connection.setComposingText("jianpan", 1) }
        assertEquals("jianpan", viewModel.state.value.composing)
    }

    @Test fun `backspace after typing extra letters following partial selection first deletes extra letters then rolls back partial selection`() = runTest(dispatcher) {
        val jianCandidate = DictionaryItem(10, "键", "jian", 48)
        val jianpanCandidate = DictionaryItem(11, "键盘", "jianpan", 49)
        every { dictionary.search("jianpan") } returns flowOf(listOf(jianpanCandidate, jianCandidate))
        every { dictionary.search("pan") } returns flowOf(emptyList())
        every { dictionary.search("pang") } returns flowOf(emptyList())

        runCurrent()
        type("jianpan")
        runCurrent()

        viewModel.dispatch(FlowKeyboardAction.SelectCandidate(jianCandidate))
        runCurrent()
        assertEquals("pan", viewModel.state.value.composing)

        type("g")
        runCurrent()
        assertEquals("pang", viewModel.state.value.composing)

        viewModel.dispatch(FlowKeyboardAction.PressKey(KeyItem("delete", "⌫", "", KeyType.BACKSPACE)))
        runCurrent()
        assertEquals("pan", viewModel.state.value.composing)

        viewModel.dispatch(FlowKeyboardAction.PressKey(KeyItem("delete", "⌫", "", KeyType.BACKSPACE)))
        runCurrent()
        verify { connection.deleteSurroundingText(1, 0) }
        verify { connection.setComposingText("jianpan", 1) }
        assertEquals("jianpan", viewModel.state.value.composing)
    }

    @Test fun `consecutive partial selections roll back step by step`() = runTest(dispatcher) {
        val woCandidate = DictionaryItem(20, "我", "wo", 50)
        val shiCandidate = DictionaryItem(21, "是", "shi", 50)
        every { dictionary.search("woshishei") } returns flowOf(listOf(woCandidate))
        every { dictionary.search("shishei") } returns flowOf(listOf(shiCandidate))
        every { dictionary.search("shei") } returns flowOf(emptyList())

        runCurrent()
        type("woshishei")
        runCurrent()

        viewModel.dispatch(FlowKeyboardAction.SelectCandidate(woCandidate))
        runCurrent()
        assertEquals("shishei", viewModel.state.value.composing)

        viewModel.dispatch(FlowKeyboardAction.SelectCandidate(shiCandidate))
        runCurrent()
        assertEquals("shei", viewModel.state.value.composing)

        viewModel.dispatch(FlowKeyboardAction.PressKey(KeyItem("delete", "⌫", "", KeyType.BACKSPACE)))
        runCurrent()
        verify { connection.deleteSurroundingText(1, 0) }
        verify { connection.setComposingText("shishei", 1) }
        assertEquals("shishei", viewModel.state.value.composing)

        viewModel.dispatch(FlowKeyboardAction.PressKey(KeyItem("delete", "⌫", "", KeyType.BACKSPACE)))
        runCurrent()
        verify(exactly = 2) { connection.deleteSurroundingText(1, 0) }
        verify { connection.setComposingText("woshishei", 1) }
        assertEquals("woshishei", viewModel.state.value.composing)
    }
}
