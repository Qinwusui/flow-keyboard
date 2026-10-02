package com.flowkeyboard.android.ui.keyboard

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.flowkeyboard.android.model.BeltOrientation

@Composable
fun FlowKeyboardContent(state: FlowKeyboardUiState, onAction: (FlowKeyboardAction) -> Unit, modifier: Modifier = Modifier, active: Boolean = true) {
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
        Column {
            CandidateBar(state.composing, state.candidates, { onAction(FlowKeyboardAction.SelectCandidate(it)) })
            ConveyorBeltCanvas(state, { onAction(FlowKeyboardAction.PressKey(it)) }, { onAction(FlowKeyboardAction.BeltTick) },
                Modifier.fillMaxWidth().height(if (state.settings.orientation == BeltOrientation.VERTICAL) 288.dp else 224.dp).padding(horizontal = 6.dp), active)
            FlowControlBar(state.settings, onAction)
        }
    }
}
