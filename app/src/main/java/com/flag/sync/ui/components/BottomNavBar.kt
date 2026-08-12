 /*
  * Copyright (C) 2026 ViictorNunes (https://github.com/ViictorNunes/FlagSync).
  * Licensed under the GNU General Public License v3.0.
  */


package com.flag.sync.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.material.icons.automirrored.rounded.CompareArrows
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import com.flag.sync.model.NavDestination
import com.flag.sync.ui.theme.LocalAccentColors
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private data class NavIconSpec(val destination: NavDestination, val icon: ImageVector)

private val navIcons = listOf(
    NavIconSpec(NavDestination.COMPARE, Icons.AutoMirrored.Rounded.CompareArrows),
    NavIconSpec(NavDestination.CONVERTER, Icons.Rounded.SwapHoriz),
    NavIconSpec(NavDestination.SETTINGS, Icons.Rounded.Settings)
)

@Composable
fun BottomNavBar(
    current: NavDestination,
    onSelect: (NavDestination) -> Unit,
    isMaterialYou: Boolean = false,
    modifier: Modifier = Modifier
) {
    val destinations = NavDestination.entries
    val selectedIndex = destinations.indexOf(current)
    val accents = LocalAccentColors.current
    val haptic = LocalHapticFeedback.current

    val totalWidth = 248.dp 
    val internalPadding = 4.dp
    val contentWidth = totalWidth - (internalPadding * 2) 
    val pillHeight = 68.dp
    val primaryColor = MaterialTheme.colorScheme.primary
    
    val isPrimaryWhite = primaryColor.luminance() > 0.9f
    val targetPillColor = if (isMaterialYou && isPrimaryWhite) Color(0xFFE0E0E0) else primaryColor
    
    // High speed theme transition (250ms)
    val themeAnimSpec = tween<Color>(durationMillis = 250)
    
    val pillColor by animateColorAsState(targetValue = targetPillColor, animationSpec = themeAnimSpec, label = "pillColor")
    val useDarkContent = targetPillColor.luminance() > 0.5f

    var isDragging by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    
    val itemWidth = contentWidth / destinations.size // 80dp
    val itemWidthPx = remember(density) { with(density) { itemWidth.toPx() } }
    
    val indexAnimatable = remember { androidx.compose.animation.core.Animatable(selectedIndex.toFloat()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(selectedIndex, isDragging) {
        if (!isDragging) {
            indexAnimatable.animateTo(
                targetValue = selectedIndex.toFloat(),
                animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow)
            )
        }
    }

    Box(
        modifier = modifier
            .width(totalWidth)
            .shadow(
                elevation = 18.dp, 
                shape = RoundedCornerShape(999.dp), 
                spotColor = Color.Black.copy(alpha = if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) 0.1f else 0.5f)
            )
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.98f))
            .border(
                1.dp, 
                MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), 
                RoundedCornerShape(999.dp)
            )
            .pointerInput(itemWidthPx) {
                detectHorizontalDragGestures(
                    onDragStart = { isDragging = true },
                    onDragEnd = {
                        isDragging = false
                        val nearestIndex = indexAnimatable.value.roundToInt()
                            .coerceIn(0, destinations.size - 1)
                        onSelect(destinations[nearestIndex])
                    },
                    onDragCancel = { isDragging = false },
                    onHorizontalDrag = { _, dragAmount ->
                        val dragInIndexUnits = dragAmount / itemWidthPx
                        val newIndex = (indexAnimatable.value + dragInIndexUnits)
                            .coerceIn(0f, (destinations.size - 1).toFloat())
                        
                        val oldActiveIndex = indexAnimatable.value.roundToInt()
                        val newActiveIndex = newIndex.roundToInt()
                        
                        if (oldActiveIndex != newActiveIndex) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }

                        scope.launch { indexAnimatable.snapTo(newIndex) }
                    }
                )
            }
            .padding(internalPadding)
    ) {
        // --- INDICATOR PILL (Restored with Stability Fixes) ---
        Box(
            modifier = Modifier
                .offset { 
                    // Use stable index-based positioning to prevent jumping
                    IntOffset((indexAnimatable.value * itemWidthPx).roundToInt(), 0) 
                }
                .width(itemWidth)
                .height(pillHeight)
                .clip(RoundedCornerShape(999.dp))
                .background(pillColor)
        )

        Row(
            modifier = Modifier.width(contentWidth),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val activeIndex = indexAnimatable.value.roundToInt()
                .coerceIn(0, destinations.size - 1)

            navIcons.forEachIndexed { index, spec ->
                val isEffectivelyActive = index == activeIndex
                val targetColor = if (isEffectivelyActive) {
                    if (useDarkContent) accents.accentDark else Color.White
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                }
                
                val contentColor by animateColorAsState(targetValue = targetColor, animationSpec = themeAnimSpec, label = "navItemContent")
                val scale by animateFloatAsState(
                    targetValue = if (isEffectivelyActive) 1.05f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                    label = "navItemScale"
                )
                val rotation by animateFloatAsState(
                    targetValue = if (isEffectivelyActive && spec.destination == NavDestination.SETTINGS) 360f else 0f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
                    label = "navItemRotation"
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .width(itemWidth)
                        .height(pillHeight)
                        .clip(RoundedCornerShape(999.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { 
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSelect(spec.destination) 
                        }
                        .scale(scale)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isEffectivelyActive && !useDarkContent) {
                            Icon(
                                imageVector = spec.icon,
                                contentDescription = null,
                                tint = Color.Black.copy(alpha = 0.15f),
                                modifier = Modifier
                                    .size(28.dp)
                                    .offset(y = 0.5.dp)
                                    .rotate(rotation)
                            )
                        }
                        
                        Icon(
                            imageVector = spec.icon,
                            contentDescription = stringResource(spec.destination.labelRes),
                            tint = contentColor,
                            modifier = Modifier
                                .size(28.dp)
                                .rotate(rotation)
                        )
                    }

                    Text(
                        text = stringResource(spec.destination.labelRes),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Black,
                        color = contentColor,
                        style = TextStyle(
                            shadow = if (isEffectivelyActive) Shadow(
                                color = Color.Black.copy(alpha = if (useDarkContent) 0.08f else 0.25f),
                                offset = Offset(0f, 0.5f),
                                blurRadius = 1.5f
                            ) else null
                        )
                    )
                }
            }
        }
    }
}
