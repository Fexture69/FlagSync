 /*
  * Copyright (C) 2026 ViictorNunes (https://github.com/ViictorNunes/FlagSync).
  * Licensed under the GNU General Public License v3.0.
  */


package com.flag.sync.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flag.sync.R
import com.flag.sync.ui.theme.LocalAccentColors
import com.flag.sync.ui.theme.MonoFamily
import com.flag.sync.ui.theme.TextMuted

@Composable
fun UploadCard(
    label: String,
    icon: ImageVector,
    fileName: String?,
    hint: String,
    onClick: () -> Unit,
    onClear: (() -> Unit)? = null,
    onRestore: (() -> Unit)? = null,
    error: String? = null,
    modifier: Modifier = Modifier
) {
    val filled = fileName != null
    val hasError = error != null
    val accents = LocalAccentColors.current
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 150.dp)
            .then(
                if (filled || hasError) {
                    Modifier.shadow(
                        elevation = 4.dp,
                        shape = RoundedCornerShape(14.dp),
                        spotColor = if (hasError) accents.danger.copy(alpha = 0.5f) else accents.teal.copy(alpha = 0.3f)
                    )
                } else Modifier
            )
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (hasError) accents.danger.copy(alpha = 0.12f)
                else if (filled) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.surface
            )
            .border(
                width = 2.dp,
                color = if (hasError) accents.danger
                else if (filled) accents.teal
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.8f),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable { 
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick() 
            }
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (hasError) {
            Box(
                modifier = Modifier
                    .padding(bottom = 14.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(accents.danger)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = (error ?: stringResource(R.string.upload_error_not_backup)).uppercase(),
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = MonoFamily,
                    textAlign = TextAlign.Center,
                    letterSpacing = 0.5.sp
                )
            }
        }

        Icon(
            imageVector = if (hasError) Icons.Rounded.Warning else icon,
            contentDescription = null,
            tint = if (hasError) accents.danger else if (filled) MaterialTheme.colorScheme.onSurface else accents.textMuted,
            modifier = Modifier.size(28.dp)
        )
        Text(
            text = label.uppercase(),
            fontFamily = MonoFamily,
            fontSize = 11.sp,
            letterSpacing = 1.sp,
            color = TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp, bottom = 6.dp)
        )
        Text(
            text = (fileName?.substringBeforeLast('.') ?: stringResource(R.string.upload_tap_to_choose)),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (!filled) {
            Text(
                text = hint,
                fontSize = 12.5.sp,
                color = TextMuted,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )

            if (onRestore != null) {
                Spacer(modifier = Modifier.size(12.dp))
                Surface(
                    onClick = { 
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onRestore() 
                    },
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = stringResource(R.string.upload_restore_mapping),
                            fontFamily = MonoFamily,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
        if (filled && onClear != null) {
            Spacer(modifier = Modifier.size(16.dp))
            Surface(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClear()
                },
                color = accents.danger.copy(alpha = 0.1f),
                contentColor = accents.danger,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, accents.danger.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = stringResource(R.string.upload_remove_file),
                        fontFamily = MonoFamily,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

        }
    }
}
