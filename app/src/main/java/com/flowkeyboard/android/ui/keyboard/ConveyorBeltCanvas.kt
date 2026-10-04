package com.flowkeyboard.android.ui.keyboard

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import com.flowkeyboard.android.engine.PhysicsCalculator
import com.flowkeyboard.android.model.BeltOrientation
import com.flowkeyboard.android.model.InputMode
import com.flowkeyboard.android.model.KeyItem
import com.flowkeyboard.android.model.KeyType
import kotlin.math.abs
import kotlin.math.floor
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun ConveyorBeltCanvas(
    state: FlowKeyboardUiState,
    onKey: (KeyItem) -> Unit,
    onTick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = true,
) {
    val coroutineScope = rememberCoroutineScope()
    val vertical = state.settings.orientation == BeltOrientation.VERTICAL
    val tracks = remember(state.settings.keyOrder, state.settings.inputMode, state.emojiMode, state.symbolMode) { state.tracks }
    val density = LocalDensity.current.density
    val spacing = (if (vertical) 52f else 58f) * density
    val keyExtent = spacing - 8f * density
    val functionWeights = remember { floatArrayOf(0.11f, 0.11f, 0.11f, 0.11f, 0.32f, 0.12f, 0.12f) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var offsets by remember(vertical, tracks) { mutableStateOf(List(5) { it * spacing * 0.18f }) }
    var velocities by remember(vertical, tracks) { mutableStateOf(List(5) { 0f }) }
    var draggedLane by remember { mutableIntStateOf(-1) }
    val currentOnKey by rememberUpdatedState(onKey)
    val currentOnTick by rememberUpdatedState(onTick)
    val colors = MaterialTheme.colorScheme
    val labelPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = android.graphics.Typeface.create("sans-serif-medium", 0) } }

    LaunchedEffect(active, state.settings.speed, state.settings.cruising, state.settings.direction, vertical, tracks, density) {
        if (!active) return@LaunchedEffect
        var last = 0L
        while (isActive) {
            withFrameNanos { now ->
                if (last != 0L) {
                    val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f)
                    val baseCruise = if (state.settings.cruising) state.settings.speed * density * state.settings.direction else 0f
                    val previous = offsets
                    offsets = previous.mapIndexed { lane, offset ->
                        if (lane == 4 || lane == draggedLane) offset
                        else {
                            val laneDirection = if (lane % 2 == 1) -1f else 1f
                            PhysicsCalculator.advance(offset, baseCruise * laneDirection + velocities[lane], dt, tracks[lane].keys.size * spacing)
                        }
                    }
                    velocities = velocities.mapIndexed { lane, velocity ->
                        if (lane == 4 || lane == draggedLane) 0f
                        else PhysicsCalculator.dampVelocity(velocity, dt)
                    }
                    if ((0..3).any { floor(previous[it] / spacing) != floor(offsets[it] / spacing) }) currentOnTick()
                }
                last = now
            }
        }
    }

    Canvas(modifier.onSizeChanged { viewport = it }.semantics {
        contentDescription = "Five key lanes with moving character keys and stationary function keys at the bottom."
        val target = (if (vertical) viewport.height else viewport.width) / 2f
        customActions = tracks.take(4).mapIndexedNotNull { lane, track ->
            PhysicsCalculator.keyAt(target, offsets[lane], spacing, track.keys.size)?.let { index ->
                val key = track.keys[index]
                CustomAccessibilityAction("${track.name}: type ${key.label}") { currentOnKey(key); true }
            }
        } + tracks.getOrNull(4)?.keys.orEmpty().map { key ->
            CustomAccessibilityAction("Function: ${key.label}") { currentOnKey(key); true }
        }
    }.pointerInput(active, vertical, tracks, spacing, viewport) {
        if (!active) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val crossAxisSize = (if (vertical) viewport.width else viewport.height).toFloat()
            val lane = floor((if (vertical) down.position.x else down.position.y) / (crossAxisSize / 5f)).toInt().coerceIn(0, 4)
            val track = tracks[lane]
            val axis = if (vertical) down.position.y else down.position.x
            val viewportExtent = (if (vertical) viewport.height else viewport.width).toFloat()

            val pressed = if (lane == 4) {
                val fraction = if (viewportExtent > 0f) (axis / viewportExtent).coerceIn(0f, 0.999f) else 0f
                var cumulative = 0f
                var foundIndex = 0
                for (i in functionWeights.indices) {
                    cumulative += functionWeights[i]
                    if (fraction < cumulative) {
                        foundIndex = i
                        break
                    }
                }
                track.keys.getOrNull(foundIndex)
            } else {
                PhysicsCalculator.keyAt(axis, offsets[lane], spacing, track.keys.size, keyExtent)?.let { track.keys[it] }
            }

            val tracker = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
            var distance = 0f
            var dragging = false
            var cancelled = false
            var repeatJob: Job? = null

            if (pressed?.type == KeyType.BACKSPACE) {
                currentOnKey(pressed)
                repeatJob = coroutineScope.launch {
                    delay(400)
                    var count = 0
                    while (isActive) {
                        currentOnKey(pressed)
                        count++
                        delay(if (count > 15) 35L else 60L)
                    }
                }
            }

            try {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    tracker.addPosition(change.uptimeMillis, change.position)
                    if (change.isConsumed && !dragging) {
                        repeatJob?.cancel()
                        cancelled = true
                        break
                    }
                    if (!change.pressed) {
                        repeatJob?.cancel()
                        if (dragging && lane != 4) {
                            val velocity = tracker.calculateVelocity()
                            velocities = velocities.toMutableList().also {
                                it[lane] = (if (vertical) velocity.y else velocity.x).coerceIn(-PhysicsCalculator.MAX_FLING_VELOCITY, PhysicsCalculator.MAX_FLING_VELOCITY)
                            }
                        } else if (!cancelled && pressed != null && !dragging) {
                            if (pressed.type != KeyType.BACKSPACE) {
                                currentOnKey(pressed)
                            }
                        }
                        change.consume()
                        break
                    }
                    if (lane != 4) {
                        val delta = if (vertical) change.positionChange().y else change.positionChange().x
                        distance += delta
                        if (!dragging && abs(distance) > viewConfiguration.touchSlop) {
                            repeatJob?.cancel()
                            dragging = true; draggedLane = lane
                            velocities = velocities.toMutableList().also { it[lane] = 0f }
                        }
                        if (dragging) {
                            offsets = offsets.toMutableList().also { it[lane] = PhysicsCalculator.wrap(it[lane] + delta, spacing * track.keys.size) }
                            change.consume()
                        }
                    } else if (repeatJob != null) {
                        val currentLane = floor((if (vertical) change.position.x else change.position.y) / (crossAxisSize / 5f)).toInt()
                        if (currentLane != 4 || abs(change.position.x - down.position.x) > 48f * density || abs(change.position.y - down.position.y) > 48f * density) {
                            repeatJob?.cancel()
                            repeatJob = null
                        }
                    }
                }
            } finally {
                repeatJob?.cancel()
                draggedLane = -1
            }
        }
    }) {
        val hasCustomBg = !state.settings.customBackgroundPath.isNullOrBlank()
        if (!hasCustomBg) {
            drawRect(colors.surfaceContainerLow)
        }
        val laneSize = (if (vertical) size.width else size.height) / 5f
        val viewportExtent = if (vertical) size.height else size.width
        tracks.forEachIndexed { lane, track ->
            val crossStart = lane * laneSize
            if (vertical) drawLine(colors.outlineVariant, Offset(crossStart, 0f), Offset(crossStart, size.height), density)
            else drawLine(colors.outlineVariant, Offset(0f, crossStart), Offset(size.width, crossStart), density)

            if (lane == 4) {
                var currentPos = 0f
                val marginAxis = 2.5f * density
                val marginCross = 3f * density
                track.keys.forEachIndexed { index, key ->
                    val weight = functionWeights.getOrElse(index) { 0.14f }
                    val keyLength = viewportExtent * weight
                    val keyStart = currentPos
                    currentPos += keyLength

                    val width = if (vertical) laneSize - 2 * marginCross else keyLength - 2 * marginAxis
                    val height = if (vertical) keyLength - 2 * marginAxis else laneSize - 2 * marginCross
                    val x = if (vertical) crossStart + marginCross else keyStart + marginAxis
                    val y = if (vertical) keyStart + marginAxis else crossStart + marginCross

                    val isShiftActive = key.type == KeyType.SHIFT && state.shifted
                    val isModePinyin = key.type == KeyType.MODE && state.settings.inputMode == InputMode.PINYIN
                    val isEmojiActive = key.type == KeyType.EMOJI && state.emojiMode
                    val isSymbolActive = key.type == KeyType.SYMBOL && state.symbolMode
                    val face = when {
                        key.type == KeyType.SHIFT -> {
                            if (isShiftActive) lerp(colors.surfaceContainerHighest, colors.primary, 0.35f)
                            else lerp(colors.surfaceContainerHighest, colors.primary, 0.16f)
                        }
                        key.type == KeyType.MODE -> {
                            if (isModePinyin) lerp(colors.surfaceContainerHighest, colors.primary, 0.28f)
                            else lerp(colors.surfaceContainerHighest, colors.primary, 0.16f)
                        }
                        key.type == KeyType.SPACE -> {
                            lerp(colors.surfaceContainerHighest, colors.primary, 0.22f)
                        }
                        isEmojiActive || isSymbolActive -> colors.secondaryContainer
                        else -> colors.surfaceContainerHighest
                    }
                    val textColor = when {
                        key.type == KeyType.SHIFT -> if (isShiftActive) colors.primary else colors.onSurface
                        key.type == KeyType.MODE -> if (isModePinyin) colors.primary else colors.onSurface
                        key.type == KeyType.SPACE -> colors.onSurface
                        isEmojiActive || isSymbolActive -> colors.onSecondaryContainer
                        else -> colors.onSurface
                    }
                    val radius = CornerRadius(8f * density)
                    drawRoundRect(face, Offset(x, y), Size(width, height), radius)
                    drawRoundRect(
                        colors.outlineVariant.copy(alpha = 0.35f),
                        Offset(x, y),
                        Size(width, height),
                        radius,
                        style = Stroke(width = 1f * density)
                    )

                    val label = key.label
                    labelPaint.color = textColor.toArgb()
                    labelPaint.isFakeBoldText = (key.type == KeyType.SHIFT && isShiftActive) || (key.type == KeyType.MODE && isModePinyin)
                    labelPaint.textSize = (if (label.length > 2) 11.5f else 17f) * density
                    val baseline = y + height / 2f - (labelPaint.ascent() + labelPaint.descent()) / 2f - density
                    drawContext.canvas.nativeCanvas.drawText(label, x + width / 2f, baseline, labelPaint)
                }
            } else {
                val period = track.keys.size * spacing
                clipRect(
                    left = if (vertical) crossStart else 0f, top = if (vertical) 0f else crossStart,
                    right = if (vertical) crossStart + laneSize else size.width,
                    bottom = if (vertical) size.height else crossStart + laneSize,
                ) {
                    track.keys.forEachIndexed { index, key ->
                        var center = PhysicsCalculator.wrap(offsets[lane] + (index + .5f) * spacing, period) - period
                        while (center < viewportExtent + spacing) {
                            if (center > -spacing) {
                                val width = if (vertical) laneSize - 8f * density else keyExtent
                                val height = if (vertical) keyExtent else laneSize - 8f * density
                                val x = if (vertical) crossStart + 4f * density else center - width / 2f
                                val y = if (vertical) center - height / 2f else crossStart + 4f * density
                                val face = colors.surfaceContainerHighest
                                val radius = CornerRadius(8f * density)
                                drawRoundRect(face, Offset(x, y), Size(width, height), radius)
                                drawRoundRect(
                                    colors.outlineVariant.copy(alpha = 0.35f),
                                    Offset(x, y),
                                    Size(width, height),
                                    radius,
                                    style = Stroke(width = 1f * density)
                                )
                                val label = if (state.shifted && key.type == KeyType.CHARACTER) key.label.uppercase(java.util.Locale.ROOT) else key.label
                                labelPaint.color = colors.onSurface.toArgb()
                                labelPaint.isFakeBoldText = false
                                labelPaint.textSize = (if (label.length > 2) 11f else 17f) * density
                                val baseline = y + height / 2f - (labelPaint.ascent() + labelPaint.descent()) / 2f - density
                                drawContext.canvas.nativeCanvas.drawText(label, x + width / 2f, baseline, labelPaint)
                            }
                            center += period
                        }
                    }
                }
            }
        }
    }
}
