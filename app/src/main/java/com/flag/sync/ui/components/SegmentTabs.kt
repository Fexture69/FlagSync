 /*
  * Copyright (C) 2026 ViictorNunes (https://github.com/ViictorNunes/FlagSync).
  * Licensed under the GNU General Public License v3.0.
  */


package com.flag.sync.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Sub-abas em formato de pílula, igual ao `.mode-tabs` do HTML
 * (ex: "Comparar dois arquivos" / "Verificar um arquivo só").
 * Versão corrigida para evitar expansão vertical infinita e saltos de layout.
 */
@Composable
fun SegmentTabs(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val count = options.size
    if (count == 0) return

    val padding = 6.dp

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(999.dp))
    ) {
        val totalWidth = maxWidth
        val availableWidth = totalWidth - (padding * 2)
        val indicatorWidth = availableWidth / count
        
        val offsetDp by animateDpAsState(
            targetValue = padding + (indicatorWidth * selectedIndex),
            label = "indicatorOffset"
        )

        // Sliding indicator (Pill)
        Box(
            modifier = Modifier
                .offset(x = offsetDp)
                .padding(vertical = padding)
                .width(indicatorWidth)
                .height(42.dp) // Fixed height to maintain image proportion
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.primary)
        )

        // Row com os textos
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
        ) {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                val textColor by animateColorAsState(
                    targetValue = if (selected) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    label = "textColor"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onSelect(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        style = TextStyle(
                            shadow = if (selected) Shadow(
                                color = Color.Black.copy(alpha = 0.35f),
                                offset = Offset(0f, 2f),
                                blurRadius = 4f
                            ) else null
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
