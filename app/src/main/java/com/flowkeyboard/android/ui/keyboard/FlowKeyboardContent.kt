package com.flowkeyboard.android.ui.keyboard

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.flowkeyboard.android.model.BeltOrientation
import com.flowkeyboard.android.model.KeyItem
import com.flowkeyboard.android.model.KeyType
import java.io.File

@Composable
fun FlowKeyboardContent(
    state: FlowKeyboardUiState,
    onAction: (FlowKeyboardAction) -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = true
) {
    val customBitmap = remember(state.settings.customBackgroundPath) {
        state.settings.customBackgroundPath?.let { path ->
            val file = File(path)
            if (file.exists() && file.isFile) {
                try {
                    BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
                } catch (e: Exception) {
                    null
                }
            } else null
        }
    }
    val hasCustomBg = customBitmap != null

    Box(modifier = modifier.fillMaxWidth()) {
        if (customBitmap != null) {
            Image(
                bitmap = customBitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
            val overlayAlpha = (1f - state.settings.backgroundBrightness).coerceIn(0f, 0.95f)
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = overlayAlpha))
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = if (hasCustomBg) Color.Transparent else MaterialTheme.colorScheme.surface
        ) {
            Column(Modifier.navigationBarsPadding()) {
                CandidateBar(
                    composing = state.composing,
                    candidates = state.candidates,
                    onCandidate = { onAction(FlowKeyboardAction.SelectCandidate(it)) },
                    isAssociative = state.isAssociative,
                    expanded = state.isCandidateExpanded,
                    orientation = state.settings.orientation,
                    hasCustomBackground = hasCustomBg,
                    onToggleExpand = { onAction(FlowKeyboardAction.ToggleCandidateExpansion()) },
                    onQuickKey = { onAction(FlowKeyboardAction.PressKey(KeyItem(id = it, label = it, value = it, type = KeyType.CHARACTER))) },
                    onToggleOrientation = { onAction(FlowKeyboardAction.ToggleOrientation) }
                )
                Box(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                    ConveyorBeltCanvas(
                        state = state,
                        onKey = { onAction(FlowKeyboardAction.PressKey(it)) },
                        onTick = { onAction(FlowKeyboardAction.BeltTick) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (state.settings.orientation == BeltOrientation.VERTICAL) 288.dp else 224.dp),
                        active = active && !state.isCandidateExpanded
                    )
                    if (state.isCandidateExpanded && state.candidates.isNotEmpty()) {
                        ExpandedCandidatePanel(
                            candidates = state.candidates,
                            composing = state.composing,
                            onSelect = { onAction(FlowKeyboardAction.SelectCandidate(it)) },
                            onClose = { onAction(FlowKeyboardAction.ToggleCandidateExpansion(false)) },
                            modifier = Modifier.matchParentSize()
                        )
                    }
                }
            }
        }
    }
}
