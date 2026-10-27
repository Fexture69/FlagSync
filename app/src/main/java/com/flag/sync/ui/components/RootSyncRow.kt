/*
 * Copyright (C) 2026 ViictorNunes (https://github.com/ViictorNunes/FlagSync).
 * Licensed under the GNU General Public License v3.0.
 */

package com.flag.sync.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Publish
import androidx.compose.material.icons.rounded.RestartAlt
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
fun RootSyncRow(
    isSingleMode: Boolean,
    onPullA: () -> Unit,
    onPullB: (() -> Unit)?,
    onApplyA: (() -> Unit)?,
    onApplyB: (() -> Unit)?,
    accentA: Color,
    accentB: Color,
    modifier: Modifier = Modifier
) {
    val accents = LocalAccentColors.current
    val haptic = LocalHapticFeedback.current
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(top = 10.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Pull Instagram to A
        Surface(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onPullA()
            },
            shape = RoundedCornerShape(12.dp),
            color = accentA.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, accentA.copy(alpha = 0.35f)),
            modifier = Modifier.height(38.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Bolt,
                    contentDescription = null,
                    tint = accentA,
                    modifier = Modifier.size(17.dp)
                )
                Text(
                    text = if (isSingleMode) stringResource(R.string.root_sync_pull_single)
                    else stringResource(R.string.root_sync_pull_a),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentA
                )
            }
        }

        // Pull Instagram to B (if double/compare mode)
        if (!isSingleMode && onPullB != null) {
            Surface(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onPullB()
                },
                shape = RoundedCornerShape(12.dp),
                color = accentB.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, accentB.copy(alpha = 0.35f)),
                modifier = Modifier.height(38.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Bolt,
                        contentDescription = null,
                        tint = accentB,
                        modifier = Modifier.size(17.dp)
                    )
                    Text(
                        text = stringResource(R.string.root_sync_pull_b),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentB
                    )
                }
            }
        }

        // Apply A to Instagram (if loaded)
        if (onApplyA != null) {
            Surface(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onApplyA()
                },
                shape = RoundedCornerShape(12.dp),
                color = accents.teal.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, accents.teal.copy(alpha = 0.5f)),
                modifier = Modifier.height(38.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Publish,
                        contentDescription = null,
                        tint = accents.teal,
                        modifier = Modifier.size(17.dp)
                    )
                    Text(
                        text = if (isSingleMode) stringResource(R.string.root_sync_apply_single)
                        else stringResource(R.string.root_sync_apply_a),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = accents.teal
                    )
                }
            }
        }

        // Apply B to Instagram (if loaded in double mode)
        if (!isSingleMode && onApplyB != null) {
            Surface(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onApplyB()
                },
                shape = RoundedCornerShape(12.dp),
                color = accents.amber.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, accents.amber.copy(alpha = 0.5f)),
                modifier = Modifier.height(38.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Publish,
                        contentDescription = null,
                        tint = accents.amber,
                        modifier = Modifier.size(17.dp)
                    )
                    Text(
                        text = stringResource(R.string.root_sync_apply_b),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = accents.amber
                    )
                }
            }
        }
    }
}
