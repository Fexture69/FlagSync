/*
 * Copyright (C) 2026 ViictorNunes (https://github.com/ViictorNunes/FlagSync).
 * Licensed under the GNU General Public License v3.0.
 */

package com.flag.sync.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flag.sync.R
import com.flag.sync.ui.theme.LocalAccentColors

@Composable
fun FlagActionRow(
    accentColor: Color,
    onAddFlag: () -> Unit,
    onDeleteAllExcept: () -> Unit,
    onDeleteAll: () -> Unit,
    flagsCount: Int,
    modifier: Modifier = Modifier
) {
    val accents = LocalAccentColors.current
    val haptic = LocalHapticFeedback.current
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(top = 8.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Add Flag Action Chip
        Surface(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onAddFlag()
            },
            shape = RoundedCornerShape(12.dp),
            color = accentColor.copy(alpha = 0.15f),
            border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.4f)),
            modifier = Modifier.height(36.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = stringResource(R.string.flag_action_add_single),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
            }
        }

        // Delete All Except... Action Chip
        Surface(
            onClick = {
                if (flagsCount > 0) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onDeleteAllExcept()
                }
            },
            enabled = flagsCount > 0,
            shape = RoundedCornerShape(12.dp),
            color = if (flagsCount > 0) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (flagsCount > 0) MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
            ),
            modifier = Modifier.height(36.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.FilterAlt,
                    contentDescription = null,
                    tint = if (flagsCount > 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = stringResource(R.string.flag_action_delete_all_except),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (flagsCount > 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                )
            }
        }

        // Delete All Flags Action Chip
        Surface(
            onClick = {
                if (flagsCount > 0) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onDeleteAll()
                }
            },
            enabled = flagsCount > 0,
            shape = RoundedCornerShape(12.dp),
            color = if (flagsCount > 0) accents.danger.copy(alpha = 0.12f)
            else accents.danger.copy(alpha = 0.04f),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (flagsCount > 0) accents.danger.copy(alpha = 0.35f)
                else accents.danger.copy(alpha = 0.1f)
            ),
            modifier = Modifier.height(36.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteSweep,
                    contentDescription = null,
                    tint = if (flagsCount > 0) accents.danger else accents.danger.copy(alpha = 0.38f),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = stringResource(R.string.flag_action_delete_all),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (flagsCount > 0) accents.danger else accents.danger.copy(alpha = 0.38f)
                )
            }
        }
    }
}
