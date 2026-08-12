 /*
  * Copyright (C) 2026 ViictorNunes (https://github.com/ViictorNunes/FlagSync).
  * Licensed under the GNU General Public License v3.0.
  */


package com.flag.sync.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flag.sync.ui.theme.LocalAccentColors
import com.flag.sync.ui.theme.MonoFamily
import com.flag.sync.utils.isLightOrGray

/**
 * Cabeçalho reutilizável para seções expansíveis com suporte a ações em massa.
 */
@Composable
fun BulkActionHeader(
    title: String,
    count: Int,
    isExpanded: Boolean,
    onExpandClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color? = null,
    subtitle: String? = null,
    bulkActionText: String? = null,
    onBulkActionClick: (() -> Unit)? = null,
) {
    val accents = LocalAccentColors.current
    val color = accentColor ?: accents.teal
    val rotation by animateFloatAsState(targetValue = if (isExpanded) 90f else 0f, label = "arrowRotation")

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.25f)),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .clickable { onExpandClick() }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = color,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(rotation)
            )

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(color.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = count.toString(),
                            fontFamily = MonoFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = color
                        )
                    }
                }
                subtitle?.let {
                    Text(
                        text = it,
                        fontSize = 11.sp,
                        color = accents.textMuted,
                        textAlign = TextAlign.Justify,
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            if (bulkActionText != null && onBulkActionClick != null) {
                val isLight = color.isLightOrGray()
                Surface(
                    onClick = { onBulkActionClick() },
                    color = color,
                    contentColor = if (isLight) Color.Black else Color.White,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .then(
                            if (isLight) {
                                Modifier.shadow(
                                    elevation = 8.dp,
                                    spotColor = Color.White.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                            } else Modifier
                        ),
                    tonalElevation = 2.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isLight) Color.Black.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = bulkActionText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}
