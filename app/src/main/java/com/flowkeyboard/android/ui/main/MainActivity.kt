package com.flowkeyboard.android.ui.main

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import com.flowkeyboard.android.R
import com.flowkeyboard.android.engine.KeyboardActionHandler
import com.flowkeyboard.android.model.BeltOrientation
import com.flowkeyboard.android.model.InputMode
import com.flowkeyboard.android.model.KeyOrder
import com.flowkeyboard.android.model.KeyboardSkin
import com.flowkeyboard.android.ui.keyboard.FlowKeyboardContent
import com.flowkeyboard.android.ui.keyboard.FlowKeyboardViewModel
import com.flowkeyboard.android.ui.theme.FlowKeyboardTheme
import org.koin.androidx.compose.koinViewModel
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val main: MainViewModel = koinViewModel()
            val state by main.state.collectAsStateWithLifecycle()
            FlowKeyboardTheme(skin = state.settings.skin) {
                MainScreen(main = main)
            }
        }
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

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val originalBitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                if (originalBitmap != null) {
                    val maxDim = 1920
                    val width = originalBitmap.width
                    val height = originalBitmap.height
                    val scaledBitmap = if (width > maxDim || height > maxDim) {
                        val ratio = minOf(maxDim.toFloat() / width, maxDim.toFloat() / height)
                        Bitmap.createScaledBitmap(originalBitmap, (width * ratio).toInt(), (height * ratio).toInt(), true)
                    } else originalBitmap

                    val oldPath = state.settings.customBackgroundPath
                    val targetFile = File(context.filesDir, "custom_bg_${System.currentTimeMillis()}.jpg")
                    val fos = FileOutputStream(targetFile)
                    scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
                    fos.flush()
                    fos.close()
                    if (scaledBitmap != originalBitmap) {
                        scaledBitmap.recycle()
                    }
                    originalBitmap.recycle()

                    oldPath?.let { File(it).delete() }
                    main.updateSettings { it.copy(customBackgroundPath = targetFile.absolutePath) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

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
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("键盘皮肤", style = MaterialTheme.typography.titleLarge)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                            ) {
                                Text(
                                    text = state.settings.skin.displayName,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        Text(
                            "个性化预置配色方案，全键盘与候选栏即时换肤",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            items(KeyboardSkin.entries) { skin ->
                                SkinCard(
                                    skin = skin,
                                    isSelected = state.settings.skin == skin,
                                    onClick = { main.updateSettings { it.copy(skin = skin) } }
                                )
                            }
                        }
                    }
                }
            }
            item {
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("自定义背景", style = MaterialTheme.typography.titleLarge)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (state.settings.customBackgroundPath != null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                            ) {
                                Text(
                                    text = if (state.settings.customBackgroundPath != null) "已启用" else "未设置",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (state.settings.customBackgroundPath != null) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        Text(
                            "支持从相册选择壁纸作为键盘背景，并可自由调节透明度与暗度。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { photoPickerLauncher.launch("image/*") },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (state.settings.customBackgroundPath != null) "更换背景图片" else "选择背景图片")
                            }
                            if (state.settings.customBackgroundPath != null) {
                                OutlinedButton(
                                    onClick = {
                                        state.settings.customBackgroundPath?.let { File(it).delete() }
                                        main.updateSettings { it.copy(customBackgroundPath = null) }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("清除背景")
                                }
                            }
                        }
                        if (state.settings.customBackgroundPath != null) {
                            var previewBrightness by remember { mutableStateOf<Float?>(null) }
                            val currentBrightness = previewBrightness ?: state.settings.backgroundBrightness
                            Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("背景亮度", style = MaterialTheme.typography.bodyMedium)
                                    Text("${(currentBrightness * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium)
                                }
                                Slider(
                                    value = currentBrightness,
                                    onValueChange = { previewBrightness = it },
                                    valueRange = 0.1f..1.0f,
                                    onValueChangeFinished = {
                                        previewBrightness?.let { b ->
                                            main.updateSettings { it.copy(backgroundBrightness = b) }
                                        }
                                        previewBrightness = null
                                    },
                                    modifier = Modifier.fillMaxWidth().height(36.dp),
                                )
                                Text(
                                    "提示：降低亮度可让传送带按键与候选字更清晰醒目。",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }
            item {
                Card {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.settings), style = MaterialTheme.typography.titleLarge)
                        SettingSwitch(stringResource(R.string.sound), state.settings.soundEnabled) { value -> main.updateSettings { it.copy(soundEnabled = value) } }
                        SettingSwitch(stringResource(R.string.haptics), state.settings.hapticsEnabled) { value -> main.updateSettings { it.copy(hapticsEnabled = value) } }
                        if (state.settings.hapticsEnabled) {
                            var previewLevel by remember { mutableStateOf<Float?>(null) }
                            val currentLevel = previewLevel ?: state.settings.vibrationLevel
                            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(stringResource(R.string.vibration_level), style = MaterialTheme.typography.bodyMedium)
                                    Text("${(currentLevel * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium)
                                }
                                Slider(
                                    value = currentLevel,
                                    onValueChange = {
                                        previewLevel = it
                                        main.previewVibration(it)
                                    },
                                    valueRange = 0f..1f,
                                    onValueChangeFinished = {
                                        previewLevel?.let { lvl ->
                                            main.updateSettings { it.copy(vibrationLevel = lvl) }
                                        }
                                        previewLevel = null
                                    },
                                    modifier = Modifier.fillMaxWidth().height(36.dp),
                                )
                            }
                        }
                        SettingSwitch(stringResource(R.string.pinyin_mode), state.settings.inputMode == InputMode.PINYIN) { value ->
                            main.updateSettings { it.copy(inputMode = if (value) InputMode.PINYIN else InputMode.ENGLISH) }
                        }
                        SettingSwitch(stringResource(R.string.alphabetical), state.settings.keyOrder == KeyOrder.ALPHABETICAL) { value ->
                            main.updateSettings { it.copy(keyOrder = if (value) KeyOrder.ALPHABETICAL else KeyOrder.QWERTY) }
                        }
                        SettingSwitch(stringResource(R.string.conveyor_cruising), state.settings.cruising) { value ->
                            main.updateSettings { it.copy(cruising = value) }
                        }
                        SettingSwitch(stringResource(R.string.vertical_orientation), state.settings.orientation == BeltOrientation.VERTICAL) { value ->
                            main.updateSettings { it.copy(orientation = if (value) BeltOrientation.VERTICAL else BeltOrientation.HORIZONTAL) }
                        }
                        var previewSpeed by remember { mutableStateOf<Float?>(null) }
                        val currentSpeed = previewSpeed ?: state.settings.speed
                        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.conveyor_speed), style = MaterialTheme.typography.bodyMedium)
                                Text("${currentSpeed.roundToInt()} dp/s", style = MaterialTheme.typography.labelMedium)
                            }
                            Slider(
                                value = currentSpeed,
                                onValueChange = { previewSpeed = it },
                                valueRange = 20f..240f,
                                onValueChangeFinished = {
                                    previewSpeed?.let { spd -> main.updateSettings { it.copy(speed = spd) } }
                                    previewSpeed = null
                                },
                                modifier = Modifier.fillMaxWidth().height(36.dp),
                            )
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

@Composable
private fun SkinCard(
    skin: KeyboardSkin,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(borderWidth, borderColor),
        modifier = Modifier.width(116.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Miniature keyboard preview swatch
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .background(Color(skin.previewBackground), RoundedCornerShape(8.dp))
                    .padding(5.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Lane 1
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.5.dp)
                    ) {
                        repeat(4) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(13.dp)
                                    .background(Color(skin.previewKeycap), RoundedCornerShape(3.dp))
                            )
                        }
                    }
                    // Lane 2
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.5.dp)
                    ) {
                        repeat(4) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(13.dp)
                                    .background(Color(skin.previewKeycap), RoundedCornerShape(3.dp))
                            )
                        }
                    }
                    // Bottom lane (highlight key + space key)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(13.dp)
                                .background(Color(skin.previewPrimary), RoundedCornerShape(3.dp))
                        )
                        Box(
                            modifier = Modifier
                                .weight(2.5f)
                                .height(13.dp)
                                .background(Color(skin.previewKeycap), RoundedCornerShape(3.dp))
                        )
                    }
                }
            }

            // Skin Name
            Text(
                text = skin.displayName,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Description / Status
            Text(
                text = if (isSelected) "● 使用中" else skin.description,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
