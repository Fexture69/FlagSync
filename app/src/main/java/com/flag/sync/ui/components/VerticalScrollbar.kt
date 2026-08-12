 /*
  * Copyright (C) 2026 ViictorNunes (https://github.com/ViictorNunes/FlagSync).
  * Licensed under the GNU General Public License v3.0.
  */


package com.flag.sync.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun VerticalScrollbar(
    state: LazyListState,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp
) {
    val scope = rememberCoroutineScope()
    var isDragging by remember { mutableStateOf(false) }

    val scrollbarAlpha by animateFloatAsState(
        targetValue = if (state.isScrollInProgress || isDragging) 0.8f else 0f,
        animationSpec = tween(durationMillis = if (state.isScrollInProgress || isDragging) 150 else 500),
        label = "scrollbarAlpha"
    )

    val thumbColor = MaterialTheme.colorScheme.primary.copy(alpha = if (isDragging) 0.9f else 0.7f)
    val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    val trackBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)

    // State to smooth thumb position and avoid "jumps" during fast scrolls
    var targetThumbOffset by remember { mutableStateOf(0f) }
    val animatedThumbOffset by animateFloatAsState(
        targetValue = targetThumbOffset,
        animationSpec = spring(stiffness = Spring.StiffnessHigh, visibilityThreshold = 0.5f),
        label = "thumbOffset"
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .statusBarsPadding() // Removes edge-to-edge at the top (clock/notifications)
            .navigationBarsPadding() // Remove edge-to-edge na base (barra de gestos)
            .width(28.dp)
            .pointerInput(state) {
                detectDragGestures(
                    onDragStart = { isDragging = true },
                    onDragEnd = { isDragging = false },
                    onDragCancel = { isDragging = false },
                    onDrag = { change, _ ->
                        val layoutInfo = state.layoutInfo
                        val visibleItems = layoutInfo.visibleItemsInfo
                        val totalItems = layoutInfo.totalItemsCount
                        if (totalItems > 0 && visibleItems.isNotEmpty()) {
                            val viewportHeight = size.height.toFloat() - bottomPadding.toPx()
                            if (viewportHeight > 0) {
                                val averageItemSize = visibleItems.map { it.size }.average().toFloat()
                                val estimatedTotalHeight = totalItems * averageItemSize
                                if (estimatedTotalHeight > 0f && averageItemSize > 0f) {
                                    val dragY = change.position.y.coerceIn(0f, viewportHeight)
                                    val scrollPercentage = dragY / viewportHeight
                                    val targetScrollPx = (scrollPercentage * estimatedTotalHeight)
                                        .coerceIn(0f, estimatedTotalHeight)

                                    val targetIndex = (targetScrollPx / averageItemSize)
                                        .toInt()
                                        .coerceIn(0, totalItems - 1)
                                    val targetOffset = (targetScrollPx - targetIndex * averageItemSize)
                                        .toInt()
                                        .coerceAtLeast(0)

                                    scope.launch {
                                        state.scrollToItem(targetIndex, targetOffset)
                                    }
                                }
                            }
                        }
                        change.consume()
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier
            .fillMaxHeight()
            .width(6.dp)
            .align(Alignment.CenterEnd)
            .alpha(scrollbarAlpha)
        ) {
            val layoutInfo = state.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo

            if (visibleItems.isNotEmpty()) {
                val totalItems = layoutInfo.totalItemsCount
                val viewportHeight = size.height - bottomPadding.toPx()

                if (viewportHeight > 0) {
                    drawRoundRect(
                        color = trackColor,
                        topLeft = Offset(x = 0f, y = 0f),
                        size = Size(width = size.width, height = viewportHeight),
                        cornerRadius = CornerRadius(999f, 999f)
                    )

                    drawRoundRect(
                        color = trackBorderColor,
                        topLeft = Offset(x = 0f, y = 0f),
                        size = Size(width = size.width, height = viewportHeight),
                        cornerRadius = CornerRadius(999f, 999f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                    )

                    val averageItemSize = visibleItems.map { it.size }.average().toFloat()
                    val estimatedTotalHeight = totalItems * averageItemSize

                    if (estimatedTotalHeight > viewportHeight) {
                        val thumbHeight = (viewportHeight / estimatedTotalHeight * viewportHeight)
                            .coerceIn(40.dp.toPx(), viewportHeight / 2)

                        val lastItem = visibleItems.last()
                        val isAtEnd = lastItem.index == totalItems - 1 &&
                                (lastItem.offset + lastItem.size) <= viewportHeight.toInt() + 10 // Margem de erro para o fim

                        val thumbOffset = when {
                            state.firstVisibleItemIndex == 0 && state.firstVisibleItemScrollOffset <= 5 -> 0f // Margem para o topo
                            isAtEnd -> viewportHeight - thumbHeight
                            else -> {
                                val scrollOffset = (state.firstVisibleItemIndex * averageItemSize) + state.firstVisibleItemScrollOffset
                                (scrollOffset / estimatedTotalHeight * viewportHeight)
                                    .coerceIn(0f, viewportHeight - thumbHeight)
                            }
                        }

                        targetThumbOffset = thumbOffset

                        drawRoundRect(
                            color = thumbColor,
                            topLeft = Offset(x = 0f, y = animatedThumbOffset),
                            size = Size(width = size.width, height = thumbHeight),
                            cornerRadius = CornerRadius(999f, 999f)
                        )
                    }
                }
            }
        }
    }
}