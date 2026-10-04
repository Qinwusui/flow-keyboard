package com.flowkeyboard.android.ui.keyboard

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.flowkeyboard.android.model.*

@Composable
fun FlowControlBar(settings: KeyboardSettings, onAction: (FlowKeyboardAction) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        TextButton(onClick = { onAction(FlowKeyboardAction.ToggleCruise) }, modifier = Modifier.semantics { contentDescription = if (settings.cruising) "Pause conveyor" else "Start conveyor" }) {
            Text(if (settings.cruising) "Ⅱ Pause" else "▶ Flow")
        }
        TextButton(onClick = { onAction(FlowKeyboardAction.ReverseDirection) }) { Text(if (settings.direction > 0) "↔ Forward" else "↔ Reverse") }
        TextButton(onClick = { onAction(FlowKeyboardAction.ToggleOrientation) }) { Text(if (settings.orientation == BeltOrientation.HORIZONTAL) "▥ Vertical" else "▤ Horizontal") }
        TextButton(onClick = { onAction(FlowKeyboardAction.ToggleInputMode) }) { Text(if (settings.inputMode == InputMode.PINYIN) "拼音" else "EN") }
    }
}

