 /*
  * Copyright (C) 2026 ViictorNunes (https://github.com/ViictorNunes/FlagSync).
  * Licensed under the GNU General Public License v3.0.
  */


package com.flag.sync.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CompareArrows
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.flag.sync.R
import com.flag.sync.ui.theme.LocalAccentColors

@Composable
fun FeatureTourOverlay(
    step: Int,
    onNext: () -> Unit,
    onDismiss: () -> Unit
) {
    val accents = LocalAccentColors.current
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    
    val screenWidthPx = with(density) { config.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { config.screenHeightDp.dp.toPx() }
    
    val tourData = listOf(
        TourStep(
            titleRes = R.string.tour_welcome_title,
            descRes = R.string.tour_welcome_desc,
            icon = Icons.Rounded.Celebration,
            accent = accents.teal,
            focusRect = null // Screen center
        ),
        TourStep(
            titleRes = R.string.tour_compare_title,
            descRes = R.string.tour_compare_desc,
            icon = Icons.AutoMirrored.Rounded.CompareArrows,
            accent = accents.teal,
            focusRect = Rect(
                offset = Offset(with(density) { 12.dp.toPx() }, with(density) { 100.dp.toPx() }),
                size = Size(screenWidthPx - with(density) { 24.dp.toPx() }, with(density) { 480.dp.toPx() })
            )
        ),
        TourStep(
            titleRes = R.string.tour_drag_title,
            descRes = R.string.tour_drag_desc,
            icon = Icons.Rounded.TouchApp,
            accent = accents.amber,
            focusRect = Rect(
                offset = Offset(
                    x = (screenWidthPx - with(density) { 260.dp.toPx() }) / 2, 
                    y = screenHeightPx - with(density) { (32 + 68 + 2).dp.toPx() } // Descendo: subtraindo menos para o destaque cair mais perto do fundo
                ),
                size = Size(with(density) { 260.dp.toPx() }, with(density) { 72.dp.toPx() }) // Altura menor (72dp) para ficar justo à pílula (68dp)
            )
        ),
        TourStep(
            titleRes = R.string.tour_convert_title,
            descRes = R.string.tour_convert_desc,
            icon = Icons.Rounded.SwapHoriz,
            accent = accents.violet,
            focusRect = Rect(
                offset = Offset(with(density) { 12.dp.toPx() }, with(density) { 100.dp.toPx() }),
                size = Size(screenWidthPx - with(density) { 24.dp.toPx() }, with(density) { 520.dp.toPx() })
            )
        ),
        TourStep(
            titleRes = R.string.tour_settings_title,
            descRes = R.string.tour_settings_desc,
            icon = Icons.Rounded.Settings,
            accent = accents.teal,
            focusRect = Rect(
                offset = Offset(with(density) { 12.dp.toPx() }, with(density) { 100.dp.toPx() }),
                size = Size(screenWidthPx - with(density) { 24.dp.toPx() }, with(density) { 480.dp.toPx() })
            )
        )
    )

    if (step < tourData.size) {
        val currentStep = tourData[step]

        // Spotlight animation
        val targetRect = currentStep.focusRect ?: Rect(
            offset = Offset(screenWidthPx / 2, screenHeightPx / 2),
            size = Size(0f, 0f)
        )

        val animatedTopLeft by animateOffsetAsState(
            targetValue = targetRect.topLeft,
            animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow),
            label = "spotlightTopLeft"
        )
        val animatedSize by animateSizeAsState(
            targetValue = targetRect.size,
            animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow),
            label = "spotlightSize"
        )
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(9999f)
                .pointerInput(Unit) {
                    detectTapGestures { }
                }
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(alpha = 0.99f)
            ) {
                drawRect(color = Color.Black.copy(alpha = 0.85f))
                
                if (currentStep.focusRect != null) {
                    drawRoundRect(
                        color = Color.Transparent,
                        topLeft = animatedTopLeft,
                        size = animatedSize,
                        cornerRadius = CornerRadius(40.dp.toPx()),
                        blendMode = BlendMode.Clear
                    )
                }
            }

            // Information Card
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                contentAlignment = if (currentStep.focusRect != null && currentStep.focusRect.top < screenHeightPx / 2) Alignment.BottomCenter else Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = if (currentStep.focusRect != null && currentStep.focusRect.top < screenHeightPx / 2) 80.dp else 0.dp),
                    shape = RoundedCornerShape(32.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                ) {
                    AnimatedContent(
                        targetState = currentStep,
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(400)) + slideInHorizontally { it / 2 })
                                .togetherWith(fadeOut(animationSpec = tween(400)) + slideOutHorizontally { -it / 2 })
                        },
                        label = "tourContent"
                    ) { stepData ->
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(stepData.accent.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = stepData.icon,
                                    contentDescription = null,
                                    tint = stepData.accent,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = stringResource(stepData.titleRes),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = stringResource(stepData.descRes),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 20.sp
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Progress Indicators
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    tourData.indices.forEach { index ->
                                        val isSelected = index == step
                                        val width by animateDpAsState(if (isSelected) 16.dp else 6.dp)
                                        Box(
                                            modifier = Modifier
                                                .size(width, 6.dp)
                                                .clip(RoundedCornerShape(999.dp))
                                                .background(if (isSelected) stepData.accent else accents.textMuted.copy(alpha = 0.3f))
                                        )
                                    }
                                }

                                Button(
                                    onClick = { if (step == tourData.size - 1) onDismiss() else onNext() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = stepData.accent,
                                        contentColor = if (stepData.accent.luminance() > 0.5f) Color.Black else Color.White
                                    ),
                                    shape = RoundedCornerShape(14.dp),
                                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        text = if (step == tourData.size - 1) stringResource(R.string.tour_finish) else stringResource(R.string.tour_next),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class TourStep(
    val titleRes: Int,
    val descRes: Int,
    val icon: ImageVector,
    val accent: Color,
    val focusRect: Rect?
)
