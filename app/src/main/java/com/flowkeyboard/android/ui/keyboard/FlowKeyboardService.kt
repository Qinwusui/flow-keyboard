package com.flowkeyboard.android.ui.keyboard

import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import com.flowkeyboard.android.model.InputMode
import com.flowkeyboard.android.model.KeyItem
import com.flowkeyboard.android.model.KeyType
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.flowkeyboard.android.engine.KeyboardActionHandler
import com.flowkeyboard.android.ui.theme.FlowKeyboardTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.koin.core.component.KoinComponent

class FlowKeyboardService : InputMethodService(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner, KoinComponent {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore = ViewModelStore()
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry
    private val visible = MutableStateFlow(false)
    private lateinit var keyboard: FlowKeyboardViewModel

    override fun onCreate() {
        super.onCreate()
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        keyboard = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return getKoin().get<FlowKeyboardViewModel>() as T
            }
        })[FlowKeyboardViewModel::class.java]
    }

    override fun onConfigureWindow(win: android.view.Window, isFullscreen: Boolean, isCandidatesOnly: Boolean) {
        super.onConfigureWindow(win, isFullscreen, isCandidatesOnly)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(win, false)
        win.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            win.isNavigationBarContrastEnforced = false
        }
    }

    override fun onCreateInputView(): View {
        window?.window?.let { win ->
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(win, false)
            win.navigationBarColor = android.graphics.Color.TRANSPARENT
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                win.isNavigationBarContrastEnforced = false
            }
        }
        window?.window?.decorView?.apply {
            setViewTreeLifecycleOwner(this@FlowKeyboardService)
            setViewTreeViewModelStoreOwner(this@FlowKeyboardService)
            setViewTreeSavedStateRegistryOwner(this@FlowKeyboardService)
        }
        return ComposeView(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setViewTreeLifecycleOwner(this@FlowKeyboardService)
            setViewTreeViewModelStoreOwner(this@FlowKeyboardService)
            setViewTreeSavedStateRegistryOwner(this@FlowKeyboardService)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
            setContent {
                val state by keyboard.state.collectAsState()
                val active by visible.collectAsState()
                FlowKeyboardTheme(skin = state.settings.skin) { FlowKeyboardContent(state, keyboard::dispatch, active = active) }
            }
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        keyboard.attach(KeyboardActionHandler({ currentInputConnection }, { currentInputEditorInfo }), isPrivate(info))
        visible.value = true
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        visible.value = false
        keyboard.finishSession()
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        super.onFinishInputView(finishingInput)
    }

    override fun onFinishInput() {
        keyboard.finishSession()
        super.onFinishInput()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (event == null) return super.onKeyDown(keyCode, event)
        val composing = keyboard.state.value.composing
        if (composing.isNotEmpty()) {
            when (keyCode) {
                in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z -> {
                    val char = ('a'.code + (keyCode - KeyEvent.KEYCODE_A)).toChar()
                    keyboard.dispatch(FlowKeyboardAction.PressKey(KeyItem(id = "phys_$char", label = char.toString(), value = char.toString(), type = KeyType.CHARACTER)))
                    return true
                }
                in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_9 -> {
                    val digit = ('1'.code + (keyCode - KeyEvent.KEYCODE_1)).toChar()
                    keyboard.dispatch(FlowKeyboardAction.PressKey(KeyItem(id = "phys_$digit", label = digit.toString(), value = digit.toString(), type = KeyType.CHARACTER)))
                    return true
                }
                KeyEvent.KEYCODE_SPACE -> {
                    keyboard.dispatch(FlowKeyboardAction.PressKey(KeyItem(id = "space", label = "Space", value = " ", type = KeyType.SPACE)))
                    return true
                }
                KeyEvent.KEYCODE_DEL -> {
                    keyboard.dispatch(FlowKeyboardAction.PressKey(KeyItem(id = "backspace", label = "⌫", value = "", type = KeyType.BACKSPACE)))
                    return true
                }
                KeyEvent.KEYCODE_ENTER -> {
                    keyboard.dispatch(FlowKeyboardAction.PressKey(KeyItem(id = "enter", label = "↵", value = "\n", type = KeyType.ENTER)))
                    return true
                }
                KeyEvent.KEYCODE_APOSTROPHE -> {
                    keyboard.dispatch(FlowKeyboardAction.PressKey(KeyItem(id = "phys_quote", label = "'", value = "'", type = KeyType.CHARACTER)))
                    return true
                }
            }
        } else if (keyboard.state.value.settings.inputMode == InputMode.PINYIN) {
            if (keyCode in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z) {
                val char = ('a'.code + (keyCode - KeyEvent.KEYCODE_A)).toChar()
                keyboard.dispatch(FlowKeyboardAction.PressKey(KeyItem(id = "phys_$char", label = char.toString(), value = char.toString(), type = KeyType.CHARACTER)))
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        if (keyboard.state.value.composing.isNotEmpty() && candidatesStart >= 0 &&
            (newSelStart < candidatesStart || newSelStart > candidatesEnd || newSelEnd > candidatesEnd))
            keyboard.resetComposition()
    }

    override fun onWindowHidden() {
        visible.value = false
        super.onWindowHidden()
    }

    private fun isPrivate(info: EditorInfo?): Boolean {
        if (info == null) return false
        val variation = info.inputType and InputType.TYPE_MASK_VARIATION
        val inputClass = info.inputType and InputType.TYPE_MASK_CLASS
        return (info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0 ||
            (inputClass == InputType.TYPE_CLASS_TEXT && variation in setOf(InputType.TYPE_TEXT_VARIATION_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)) ||
            (inputClass == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
    }

    override fun onDestroy() {
        visible.value = false
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
        super.onDestroy()
    }
}
