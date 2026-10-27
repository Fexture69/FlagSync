/*
 * Copyright (C) 2026 ViictorNunes (https://github.com/ViictorNunes/FlagSync).
 * Licensed under the GNU General Public License v3.0.
 */

package com.flag.sync.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.flag.sync.logic.FlagMapper
import com.flag.sync.ui.theme.LocalAccentColors
import com.flag.sync.ui.theme.MonoFamily

enum class ExceptMatchMode {
    CONTAINS, EXACT, STARTS_WITH
}

@Composable
fun DeleteAllExceptDialog(
    flags: Map<String, Any?>?,
    mapper: FlagMapper?,
    activeSearchQuery: String,
    targetSource: Char,
    onDismiss: () -> Unit,
    onConfirm: (predicate: (path: String, value: Any?) -> Boolean) -> Unit
) {
    val accents = LocalAccentColors.current
    val haptic = LocalHapticFeedback.current

    var inputText by remember { mutableStateOf(activeSearchQuery) }
    var matchMode by remember { mutableStateOf(ExceptMatchMode.CONTAINS) }
    var includeMapping by remember { mutableStateOf(true) }

    val totalCount = flags?.size ?: 0

    val tokens = remember(inputText) {
        inputText.split(',', '\n')
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
    }

    val predicate: (String, Any?) -> Boolean = remember(tokens, matchMode, includeMapping, mapper) {
        { path, _ ->
            if (tokens.isEmpty()) {
                false
            } else {
                val normPath = path.lowercase()
                val normDisplay = if (includeMapping) (mapper?.getDisplayName(path)?.lowercase() ?: "") else ""
                tokens.any { token ->
                    when (matchMode) {
                        ExceptMatchMode.CONTAINS -> {
                            normPath.contains(token) || (normDisplay.isNotEmpty() && normDisplay.contains(token))
                        }
                        ExceptMatchMode.EXACT -> {
                            normPath == token || normDisplay == token
                        }
                        ExceptMatchMode.STARTS_WITH -> {
                            normPath.startsWith(token) || (normDisplay.isNotEmpty() && normDisplay.startsWith(token))
                        }
                    }
                }
            }
        }
    }

    val keepCount = remember(predicate, flags) {
        if (tokens.isEmpty()) 0
        else flags?.count { predicate(it.key, it.value) } ?: 0
    }

    val deleteCount = remember(totalCount, keepCount) {
        totalCount - keepCount
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.FilterAlt,
                    contentDescription = null,
                    tint = accents.amber,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "${stringResource(R.string.delete_all_except_title)} ($targetSource)",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = stringResource(R.string.delete_all_except_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Input field
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        label = { Text(stringResource(R.string.delete_all_except_input_label), fontSize = 12.sp) },
                        placeholder = { Text(stringResource(R.string.delete_all_except_input_placeholder)) },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        trailingIcon = {
                            if (inputText.isNotEmpty()) {
                                IconButton(onClick = { inputText = "" }) {
                                    Icon(Icons.Rounded.Close, null, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    )

                    if (activeSearchQuery.isNotBlank() && inputText != activeSearchQuery) {
                        Surface(
                            onClick = { inputText = activeSearchQuery },
                            shape = RoundedCornerShape(8.dp),
                            color = accents.teal.copy(alpha = 0.12f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, accents.teal.copy(alpha = 0.3f)),
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Rounded.Search, null, tint = accents.teal, modifier = Modifier.size(14.dp))
                                Text(
                                    text = "${stringResource(R.string.delete_all_except_use_search)} (\"$activeSearchQuery\")",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = accents.teal
                                )
                            }
                        }
                    }
                }

                // Match mode selector
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.delete_all_except_match_mode),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = MonoFamily,
                        color = accents.textMuted
                    )

                    val modeScroll = rememberScrollState()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(modeScroll),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ExceptMatchMode.entries.forEach { mode ->
                            val isSelected = matchMode == mode
                            val label = when (mode) {
                                ExceptMatchMode.CONTAINS -> stringResource(R.string.delete_all_except_match_contains)
                                ExceptMatchMode.EXACT -> stringResource(R.string.delete_all_except_match_exact)
                                ExceptMatchMode.STARTS_WITH -> stringResource(R.string.delete_all_except_match_starts_with)
                            }

                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    matchMode = mode
                                },
                                label = { Text(label, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                shape = RoundedCornerShape(10.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = accents.amber.copy(alpha = 0.2f),
                                    selectedLabelColor = accents.amber,
                                    selectedLeadingIconColor = accents.amber
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                    selectedBorderColor = accents.amber.copy(alpha = 0.6f),
                                    borderWidth = 1.dp,
                                    selectedBorderWidth = 1.dp,
                                    enabled = true,
                                    selected = isSelected
                                )
                            )
                        }
                    }
                }

                // Match against mapping checkbox
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { includeMapping = !includeMapping }
                        .padding(vertical = 4.dp)
                ) {
                    Checkbox(
                        checked = includeMapping,
                        onCheckedChange = { includeMapping = it },
                        colors = CheckboxDefaults.colors(checkedColor = accents.teal)
                    )
                    Text(
                        text = stringResource(R.string.delete_all_except_include_mapping),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }

                // Live Preview Counter
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$keepCount",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = accents.teal,
                                fontFamily = MonoFamily
                            )
                            Text(
                                text = stringResource(R.string.delete_all_except_preview_keep, keepCount).substringBefore(':'),
                                fontSize = 11.sp,
                                color = accents.textMuted
                            )
                        }

                        VerticalDivider(modifier = Modifier.height(30.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$deleteCount",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = accents.danger,
                                fontFamily = MonoFamily
                            )
                            Text(
                                text = stringResource(R.string.delete_all_except_preview_delete, deleteCount).substringBefore(':'),
                                fontSize = 11.sp,
                                color = accents.textMuted
                            )
                        }
                    }
                }

                if (tokens.isNotEmpty() && keepCount == 0) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = accents.danger.copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, accents.danger.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.WarningAmber, null, tint = accents.danger, modifier = Modifier.size(18.dp))
                            Text(
                                text = stringResource(R.string.delete_all_except_empty_warning, totalCount),
                                fontSize = 11.5.sp,
                                color = accents.danger,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = tokens.isNotEmpty(),
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onConfirm(predicate)
                }
            ) {
                Text(
                    text = stringResource(R.string.delete_all_except_confirm, keepCount, deleteCount),
                    fontWeight = FontWeight.Bold,
                    color = if (tokens.isNotEmpty()) accents.danger else accents.textMuted
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.delete_confirm_cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
