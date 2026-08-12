 /*
  * Copyright (C) 2026 ViictorNunes (https://github.com/ViictorNunes/FlagSync).
  * Licensed under the GNU General Public License v3.0.
  */


package com.flag.sync.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flag.sync.R
import com.flag.sync.ui.theme.LocalAccentColors
import com.flag.sync.ui.theme.MonoFamily
import com.flag.sync.utils.isLightOrGray

@Composable
fun CategoryHeader(
    title: String,
    count: Int,
    isExpanded: Boolean,
    onClick: () -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier,
    bulkActionText: String? = null,
    onBulkActionClick: (() -> Unit)? = null,
    bulkActionColor: Color? = null,
    secondaryBulkActionText: String? = null,
    onSecondaryBulkActionClick: (() -> Unit)? = null,
    secondaryBulkActionColor: Color? = null
) {
    val accents = LocalAccentColors.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val rotation by animateFloatAsState(targetValue = if (isExpanded) 180f else 0f, label = "rotation")
    
    val categoryIcon = getIconForCategory(title)
    val displayTitle = if (title == "GENERAL") stringResource(R.string.category_general) else title

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable { 
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                onClick() 
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = categoryIcon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(
                        when (title) {
                            "Meta IA" -> 24.dp
                            "Direct" -> 18.dp
                            else -> 20.dp
                        }
                    )
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = displayTitle,
                        fontFamily = MonoFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "($count)",
                        fontSize = 10.sp,
                        color = accents.textMuted,
                        fontFamily = MonoFamily
                    )
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (secondaryBulkActionText != null && onSecondaryBulkActionClick != null) {
                CategoryActionButton(
                    text = secondaryBulkActionText,
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    onClick = onSecondaryBulkActionClick,
                    accentColor = secondaryBulkActionColor ?: accentColor,
                    haptic = haptic
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            if (bulkActionText != null && onBulkActionClick != null) {
                CategoryActionButton(
                    text = bulkActionText,
                    icon = Icons.AutoMirrored.Rounded.ArrowForward,
                    onClick = onBulkActionClick,
                    accentColor = bulkActionColor ?: accentColor,
                    haptic = haptic
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            Icon(
                imageVector = Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(20.dp).rotate(rotation)
            )
        }
    }
}

@Composable
private fun CategoryActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    accentColor: Color,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback
) {
    val isLight = accentColor.isLightOrGray()
    Surface(
        onClick = { 
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
            onClick() 
        },
        color = accentColor.copy(alpha = 0.9f),
        contentColor = if (isLight) Color.Black else Color.White,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isLight) Color.Black.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(12.dp))
            Text(
                text = text.split(" ").last(), // Pegar apenas "A" ou "B"
                fontSize = 9.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}

fun getCategoryColor(title: String, accents: com.flag.sync.ui.theme.AccentColors): Color {
    return when (title) {
        "Stories", "Notification", "Feed", "Plus" -> accents.amber
        "Camera", "Instants", "Live", "Post", "Edits", "Search", "Explore" -> accents.teal
        "Reels", "Clips", "Direct", "Notes", "Profile", "Meta IA", "Threads" -> accents.violet
        else -> accents.textMuted
    }
}

@Composable
private fun getIconForCategory(title: String): ImageVector {
    return when (title) {
        "Feed" -> Icons.Rounded.Home
        "Reels" -> ImageVector.vectorResource(R.drawable.ic_cat_reels)
        "Clips" -> Icons.Rounded.Movie
        "Stories" -> Icons.Rounded.History
        "Instants" -> ImageVector.vectorResource(R.drawable.ic_cat_instants)
        "Camera" -> Icons.Rounded.PhotoCamera
        "Direct" -> ImageVector.vectorResource(R.drawable.ic_cat_direct)
        "Live" -> ImageVector.vectorResource(R.drawable.ic_cat_live)
        "Profile" -> Icons.Rounded.AccountCircle
        "Search" -> ImageVector.vectorResource(R.drawable.ic_cat_explore)
        "Explore" -> Icons.Rounded.Explore
        "Post" -> Icons.Rounded.AddBox
        "Notification" -> ImageVector.vectorResource(R.drawable.ic_cat_notification)
        "Notes" -> ImageVector.vectorResource(R.drawable.ic_cat_notes)
        "Plus" -> ImageVector.vectorResource(R.drawable.ic_cat_aura)
        "Meta IA" -> ImageVector.vectorResource(R.drawable.ic_cat_meta_ai)
        "Threads" -> ImageVector.vectorResource(R.drawable.ic_cat_threads)
        "Edits" -> ImageVector.vectorResource(R.drawable.ic_cat_edits)
        "GENERAL" -> Icons.Rounded.Layers
        else -> Icons.AutoMirrored.Rounded.Label
    }
}
