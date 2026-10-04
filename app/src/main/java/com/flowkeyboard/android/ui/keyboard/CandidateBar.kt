package com.flowkeyboard.android.ui.keyboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowkeyboard.android.model.BeltOrientation
import com.flowkeyboard.android.model.DictionaryItem

@Composable
fun CandidateBar(
    composing: String,
    candidates: List<DictionaryItem>,
    onCandidate: (DictionaryItem) -> Unit,
    modifier: Modifier = Modifier,
    isAssociative: Boolean = false,
    expanded: Boolean = false,
    orientation: BeltOrientation = BeltOrientation.HORIZONTAL,
    hasCustomBackground: Boolean = false,
    onToggleExpand: () -> Unit = {},
    onQuickKey: (String) -> Unit = {},
    onToggleOrientation: () -> Unit = {},
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .background(
                if (hasCustomBackground) MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.85f)
                else MaterialTheme.colorScheme.surfaceContainerLow
            )
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (composing.isNotEmpty() || (isAssociative && candidates.isNotEmpty())) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Composing / Associative Badge
                Surface(
                    color = if (isAssociative) {
                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.9f)
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f)
                    },
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .widthIn(max = 110.dp)
                ) {
                    Text(
                        text = if (isAssociative) "✨ 联想" else composing,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isAssociative) {
                            MaterialTheme.colorScheme.onTertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Dedicated active segmentation button (主动分词按键)
                if (!isAssociative && composing.isNotEmpty()) {
                    val canSegment = !composing.endsWith("'")
                    Surface(
                        onClick = { if (canSegment) onQuickKey("'") },
                        enabled = canSegment,
                        shape = RoundedCornerShape(6.dp),
                        color = if (canSegment) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.35f)
                        },
                        modifier = Modifier.padding(end = 6.dp)
                    ) {
                        Text(
                            text = "' 分词",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (canSegment) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            }
                        )
                    }
                }

                // Divider line between badge and candidates
                Box(
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .height(18.dp)
                        .width(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                )

                // Candidate horizontal list
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (candidates.isEmpty()) {
                        item {
                            Text(
                                "无匹配 · 按空格输入拼音",
                                modifier = Modifier.padding(horizontal = 6.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                    itemsIndexed(candidates, key = { _, item -> item.id }) { index, candidate ->
                        val isFirst = index == 0
                        val containerColor = if (isFirst) {
                            lerp(
                                MaterialTheme.colorScheme.surfaceContainerHighest,
                                MaterialTheme.colorScheme.primary,
                                0.24f
                            )
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f)
                        }
                        val contentColor = if (isFirst) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                        val fontWeight = if (isFirst) FontWeight.Bold else FontWeight.Normal

                        Surface(
                            onClick = { onCandidate(candidate) },
                            modifier = Modifier.height(34.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = containerColor,
                            contentColor = contentColor,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = if (isFirst) 12.dp else 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                if (!isAssociative && index < 9) {
                                    Text(
                                        text = "${index + 1}.",
                                        fontSize = 11.sp,
                                        fontWeight = if (isFirst) FontWeight.Medium else FontWeight.Normal,
                                        color = contentColor.copy(alpha = if (isFirst) 0.65f else 0.45f),
                                        modifier = Modifier.padding(end = 3.dp)
                                    )
                                }
                                Text(
                                    text = candidate.word,
                                    fontSize = if (isFirst) 16.5.sp else 16.sp,
                                    fontWeight = fontWeight,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Expand / Collapse Chevron Button
                if (candidates.isNotEmpty()) {
                    Surface(
                        onClick = onToggleExpand,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f),
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .size(30.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            ChevronIcon(
                                expanded = expanded,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        } else {
            // Idle State: Info and quick punctuation
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                    ) {
                        Text(
                            text = "传送带",
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        onClick = onToggleOrientation,
                        shape = RoundedCornerShape(6.dp),
                        color = if (orientation == BeltOrientation.VERTICAL) {
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f)
                        },
                    ) {
                        Text(
                            text = if (orientation == BeltOrientation.VERTICAL) "▥ 纵向" else "▤ 横向",
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (orientation == BeltOrientation.VERTICAL) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("，", "。", "！", "？", "～").forEach { p ->
                        Surface(
                            onClick = { onQuickKey(p) },
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = p,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bottom hairline divider
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.6.dp)
                .align(Alignment.BottomCenter)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        )
    }
}

@Composable
private fun ChevronIcon(
    expanded: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "chevron_rotation"
    )
    Canvas(modifier = modifier.size(12.dp).rotate(rotation)) {
        val strokeWidth = 1.8.dp.toPx()
        val w = size.width
        val h = size.height
        drawLine(
            color = color,
            start = Offset(w * 0.15f, h * 0.35f),
            end = Offset(w * 0.5f, h * 0.7f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(w * 0.5f, h * 0.7f),
            end = Offset(w * 0.85f, h * 0.35f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}
