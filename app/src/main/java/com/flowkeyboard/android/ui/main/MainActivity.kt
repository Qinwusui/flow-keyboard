package com.flowkeyboard.android.ui.main

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import com.flowkeyboard.android.R
import com.flowkeyboard.android.engine.KeyboardActionHandler
import com.flowkeyboard.android.model.KeyOrder
import com.flowkeyboard.android.ui.keyboard.FlowKeyboardContent
import com.flowkeyboard.android.ui.keyboard.FlowKeyboardViewModel
import com.flowkeyboard.android.ui.theme.FlowKeyboardTheme
import org.koin.androidx.compose.koinViewModel
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { FlowKeyboardTheme { MainScreen() } }
    }
}

@Composable
private fun MainScreen(main: MainViewModel = koinViewModel(), keyboard: FlowKeyboardViewModel = koinViewModel()) {
    val state by main.state.collectAsStateWithLifecycle()
    val keyboardState by keyboard.state.collectAsStateWithLifecycle()
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val context = LocalContext.current
    val inputMethods = remember { context.getSystemService(InputMethodManager::class.java) }
    var enabled by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        enabled = inputMethods.enabledInputMethodList.any { it.packageName == context.packageName }
        selected = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.startsWith(context.packageName + "/") == true
    }
    DisposableEffect(keyboard) { onDispose { keyboard.finishSession() } }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(Modifier.fillMaxSize().safeDrawingPadding(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Column {
                    Text("FlowKeyboard", style = MaterialTheme.typography.headlineLarge)
                    Text(stringResource(R.string.app_tagline), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
                    Text("A keyboard in motion", Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
            item {
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Make it your keyboard", style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(if (enabled) R.string.enabled else R.string.not_enabled))
                        Button(onClick = { context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }, Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.enable_keyboard))
                        }
                        OutlinedButton(onClick = { inputMethods.showInputMethodPicker() }, Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.select_keyboard))
                        }
                        Text(stringResource(if (selected) R.string.active else R.string.not_active), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Try the conveyor", style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.typing_instructions), style = MaterialTheme.typography.bodySmall)
                    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
                    val background = MaterialTheme.colorScheme.surfaceContainer.toArgb()
                    AndroidView(factory = { viewContext ->
                        EditText(viewContext).apply {
                            hint = viewContext.getString(R.string.sandbox_hint)
                            setTextColor(textColor); setHintTextColor(textColor and 0x00ffffff or 0x88000000.toInt())
                            setBackgroundColor(background); textSize = 18f
                            setPadding(24, 16, 24, 16)
                            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                            imeOptions = EditorInfo.IME_ACTION_NONE or EditorInfo.IME_FLAG_NO_ENTER_ACTION
                            showSoftInputOnFocus = false
                            val info = EditorInfo()
                            val connection = onCreateInputConnection(info)
                            keyboard.attach(KeyboardActionHandler({ connection }, { info }))
                            requestFocus()
                        }
                    }, modifier = Modifier.fillMaxWidth().height(100.dp))
                    FlowKeyboardContent(keyboardState, keyboard::dispatch, active = lifecycleState.isAtLeast(Lifecycle.State.RESUMED))
                    Text("${keyboardState.sessionKeystrokes} taps this session", style = MaterialTheme.typography.labelMedium)
                }
            }
            item {
                Card {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.settings), style = MaterialTheme.typography.titleLarge)
                        SettingSwitch(stringResource(R.string.sound), state.settings.soundEnabled) { value -> main.updateSettings { it.copy(soundEnabled = value) } }
                        SettingSwitch(stringResource(R.string.haptics), state.settings.hapticsEnabled) { value -> main.updateSettings { it.copy(hapticsEnabled = value) } }
                        SettingSwitch(stringResource(R.string.alphabetical), state.settings.keyOrder == KeyOrder.ALPHABETICAL) { value ->
                            main.updateSettings { it.copy(keyOrder = if (value) KeyOrder.ALPHABETICAL else KeyOrder.QWERTY) }
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Typing statistics", style = MaterialTheme.typography.titleLarge)
                    Text("${state.summary.keystrokes} saved keystrokes · ${String.format(Locale.ROOT, "%.1f", state.summary.wordsPerMinute)} WPM")
                    Text("Sessions are saved when the keyboard closes. WPM uses five committed characters per word. Password fields do not contribute to learning or statistics.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
