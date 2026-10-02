package com.flowkeyboard.android.ui.keyboard

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import androidx.compose.material3.MaterialTheme
import com.flowkeyboard.android.engine.PhysicsCalculator
import com.flowkeyboard.android.model.BeltOrientation
import com.flowkeyboard.android.model.KeyItem
import kotlin.math.abs
import kotlin.math.floor
import kotlinx.coroutines.isActive

@Composable
fun ConveyorBeltCanvas(
    state: FlowKeyboardUiState,
    onKey: (KeyItem) -> Unit,
    onTick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = true,
) {
    val vertical = state.settings.orientation == BeltOrientation.VERTICAL
    val tracks = remember(state.settings.keyOrder) { state.tracks }
    val density = LocalDensity.current.density
    val spacing = (if (vertical) 62f else 70f) * density
    val keyExtent = spacing - 10f * density
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var offsets by remember(vertical, tracks) { mutableStateOf(List(4) { it * spacing * 0.18f }) }
    var velocities by remember(vertical, tracks) { mutableStateOf(List(4) { 0f }) }
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
                    val cruise = if (state.settings.cruising) state.settings.speed * density * state.settings.direction else 0f
                    val previous = offsets
                    offsets = previous.mapIndexed { lane, offset ->
                        if (lane == draggedLane) offset else PhysicsCalculator.advance(offset, cruise + velocities[lane], dt, tracks[lane].keys.size * spacing)
                    }
                    velocities = velocities.mapIndexed { lane, velocity -> if (lane == draggedLane) 0f else PhysicsCalculator.dampVelocity(velocity, dt) }
                    if (previous.indices.any { floor(previous[it] / spacing) != floor(offsets[it] / spacing) }) currentOnTick()
                }
                last = now
            }
        }
    }

    Canvas(modifier.onSizeChanged { viewport = it }.semantics {
        contentDescription = "Four moving key lanes. Tap a passing key at the target line, or drag a lane."
        val target = (if (vertical) viewport.height else viewport.width) / 2f
        customActions = tracks.mapIndexedNotNull { lane, track ->
            PhysicsCalculator.keyAt(target, offsets[lane], spacing, track.keys.size)?.let { index ->
                val key = track.keys[index]
                CustomAccessibilityAction("${track.name}: type ${key.label}") { currentOnKey(key); true }
            }
        }
    }.pointerInput(active, vertical, tracks, spacing, viewport) {
        if (!active) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val crossAxisSize = (if (vertical) viewport.width else viewport.height).toFloat()
            val lane = floor((if (vertical) down.position.x else down.position.y) / (crossAxisSize / 4f)).toInt().coerceIn(0, 3)
            val track = tracks[lane]
            val axis = if (vertical) down.position.y else down.position.x
            // Capture the key at finger-down, so motion during a tap cannot change the input.
            val pressed = PhysicsCalculator.keyAt(axis, offsets[lane], spacing, track.keys.size, keyExtent)?.let { track.keys[it] }
            val tracker = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
            var distance = 0f
            var dragging = false
            var cancelled = false
            try {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    tracker.addPosition(change.uptimeMillis, change.position)
                    if (change.isConsumed && !dragging) { cancelled = true; break }
                    if (!change.pressed) {
                        if (dragging) {
                            val velocity = tracker.calculateVelocity()
                            velocities = velocities.toMutableList().also {
                                it[lane] = (if (vertical) velocity.y else velocity.x).coerceIn(-PhysicsCalculator.MAX_FLING_VELOCITY, PhysicsCalculator.MAX_FLING_VELOCITY)
                            }
                        } else if (!cancelled && pressed != null) currentOnKey(pressed)
                        change.consume()
                        break
                    }
                    val delta = if (vertical) change.positionChange().y else change.positionChange().x
                    distance += delta
                    if (!dragging && abs(distance) > viewConfiguration.touchSlop) {
                        dragging = true; draggedLane = lane
                        velocities = velocities.toMutableList().also { it[lane] = 0f }
                    }
                    if (dragging) {
                        offsets = offsets.toMutableList().also { it[lane] = PhysicsCalculator.wrap(it[lane] + delta, spacing * track.keys.size) }
                        change.consume()
                    }
                }
            } finally { draggedLane = -1 }
        }
    }) {
        drawRoundRect(colors.surfaceContainerLow, cornerRadius = CornerRadius(16f * density))
        val laneSize = (if (vertical) size.width else size.height) / 4f
        val viewportExtent = if (vertical) size.height else size.width
        val target = viewportExtent / 2f
        tracks.forEachIndexed { lane, track ->
            val crossStart = lane * laneSize
            if (vertical) drawLine(colors.outlineVariant, Offset(crossStart, 0f), Offset(crossStart, size.height), density)
            else drawLine(colors.outlineVariant, Offset(0f, crossStart), Offset(size.width, crossStart), density)
            val period = track.keys.size * spacing
            clipRect(
                left = if (vertical) crossStart else 0f, top = if (vertical) 0f else crossStart,
                right = if (vertical) crossStart + laneSize else size.width,
                bottom = if (vertical) size.height else crossStart + laneSize,
            ) {
                // Repeating tread marks give the belt an independent moving surface.
                val tread = PhysicsCalculator.wrap(offsets[lane], 18f * density)
                var mark = tread - 18f * density
                while (mark < viewportExtent) {
                    if (vertical) drawLine(colors.outlineVariant.copy(alpha = .35f), Offset(crossStart + 3f * density, mark), Offset(crossStart + laneSize - 3f * density, mark), density)
                    else drawLine(colors.outlineVariant.copy(alpha = .35f), Offset(mark, crossStart + 3f * density), Offset(mark, crossStart + laneSize - 3f * density), density)
                    mark += 18f * density
                }
                track.keys.forEachIndexed { index, key ->
                    var center = PhysicsCalculator.wrap(offsets[lane] + (index + .5f) * spacing, period) - period
                    while (center < viewportExtent + spacing) {
                        if (center > -spacing) {
                            val width = if (vertical) laneSize - 12f * density else keyExtent
                            val height = if (vertical) keyExtent else laneSize - 12f * density
                            val x = if (vertical) crossStart + 6f * density else center - width / 2f
                            val y = if (vertical) center - height / 2f else crossStart + 5f * density
                            val hit = abs(center - target) <= keyExtent / 2f
                            val face = if (hit) colors.primaryContainer else colors.surfaceContainerHighest
                            val radius = CornerRadius(9f * density)
                            drawRoundRect(Color.Black.copy(alpha = .18f), Offset(x, y + 4f * density), Size(width, height), radius)
                            drawRoundRect(Brush.verticalGradient(listOf(face, face.copy(alpha = .84f)), y, y + height), Offset(x, y), Size(width, height - 2f * density), radius)
                            drawLine(colors.surface.copy(alpha = .7f), Offset(x + 9f * density, y + 2f * density), Offset(x + width - 9f * density, y + 2f * density), density)
                            val label = if (state.shifted && key.type == com.flowkeyboard.android.model.KeyType.CHARACTER) key.label.uppercase(java.util.Locale.ROOT) else key.label
                            labelPaint.color = (if (hit) colors.onPrimaryContainer else colors.onSurface).toArgb()
                            labelPaint.textSize = (if (label.length > 2) 12f else 21f) * density
                            val baseline = y + height / 2f - (labelPaint.ascent() + labelPaint.descent()) / 2f - density
                            drawContext.canvas.nativeCanvas.drawText(label, x + width / 2f, baseline, labelPaint)
                        }
                        center += period
                    }
                }
            }
        }
        val targetColor = colors.primary.copy(alpha = .75f)
        if (vertical) {
            drawLine(targetColor, Offset(0f, target), Offset(size.width, target), 2f * density)
            drawCircle(colors.primary, 4f * density, Offset(4f * density, target))
            drawCircle(colors.primary, 4f * density, Offset(size.width - 4f * density, target))
        } else {
            drawLine(targetColor, Offset(target, 0f), Offset(target, size.height), 2f * density)
            drawCircle(colors.primary, 4f * density, Offset(target, 4f * density))
            drawCircle(colors.primary, 4f * density, Offset(target, size.height - 4f * density))
        }
    }
}
