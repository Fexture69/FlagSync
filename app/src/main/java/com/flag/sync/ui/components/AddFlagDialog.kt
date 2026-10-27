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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flag.sync.R
import com.flag.sync.ui.theme.LocalAccentColors
import com.flag.sync.ui.theme.MonoFamily
import org.json.JSONArray

enum class AddFlagType {
    BOOLEAN, STRING, NUMBER, ARRAY
}

@Composable
fun AddFlagDialog(
    existingFlags: Map<String, Any?>?,
    targetSource: Char,
    onDismiss: () -> Unit,
    onSave: (path: String, value: Any?) -> Unit
) {
    val accents = LocalAccentColors.current
    val haptic = LocalHapticFeedback.current

    var flagPath by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(AddFlagType.BOOLEAN) }

    var boolValue by remember { mutableStateOf(true) }
    var stringValue by remember { mutableStateOf("") }
    var numberValue by remember { mutableStateOf("1") }
    var arrayValue by remember { mutableStateOf("[\"1:true\"]") }

    val trimmedPath = flagPath.trim()
    val flagExists = remember(trimmedPath, existingFlags) {
        trimmedPath.isNotEmpty() && (existingFlags?.containsKey(trimmedPath) == true)
    }

    val isNumberValid = remember(numberValue) {
        numberValue.toLongOrNull() != null || numberValue.toDoubleOrNull() != null
    }

    val isArrayValid = remember(arrayValue) {
        try {
            JSONArray(arrayValue.trim())
            true
        } catch (_: Exception) {
            false
        }
    }

    val canSave = remember(trimmedPath, selectedType, isNumberValid, isArrayValid) {
        trimmedPath.isNotEmpty() && when (selectedType) {
            AddFlagType.BOOLEAN -> true
            AddFlagType.STRING -> true
            AddFlagType.NUMBER -> isNumberValid
            AddFlagType.ARRAY -> isArrayValid
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    tint = accents.teal,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "${stringResource(R.string.add_flag_title)} ($targetSource)",
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
                // Flag Key / Path Input
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedTextField(
                        value = flagPath,
                        onValueChange = { flagPath = it },
                        label = { Text(stringResource(R.string.add_flag_path_label)) },
                        placeholder = { Text(stringResource(R.string.add_flag_path_placeholder)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        trailingIcon = {
                            if (flagPath.isNotEmpty()) {
                                IconButton(onClick = { flagPath = "" }) {
                                    Icon(Icons.Rounded.Close, null, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    )

                    if (flagExists) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = accents.amber.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, accents.amber.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.WarningAmber,
                                    null,
                                    tint = accents.amber,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = stringResource(R.string.add_flag_exists_warning),
                                    fontSize = 11.sp,
                                    color = accents.amber,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // Value Type Selector
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.add_flag_type_label),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = MonoFamily,
                        color = accents.textMuted
                    )

                    val typeScroll = rememberScrollState()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(typeScroll),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AddFlagType.entries.forEach { type ->
                            val isSelected = selectedType == type
                            val label = when (type) {
                                AddFlagType.BOOLEAN -> stringResource(R.string.add_flag_type_boolean)
                                AddFlagType.STRING -> stringResource(R.string.add_flag_type_string)
                                AddFlagType.NUMBER -> stringResource(R.string.add_flag_type_number)
                                AddFlagType.ARRAY -> stringResource(R.string.add_flag_type_array)
                            }

                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    selectedType = type
                                },
                                label = { Text(label, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                leadingIcon = {
                                    if (isSelected) Icon(Icons.Rounded.Check, null, modifier = Modifier.size(14.dp))
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = accents.teal.copy(alpha = 0.2f),
                                    selectedLabelColor = accents.teal,
                                    selectedLeadingIconColor = accents.teal
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                    selectedBorderColor = accents.teal.copy(alpha = 0.6f),
                                    borderWidth = 1.dp,
                                    selectedBorderWidth = 1.dp,
                                    enabled = true,
                                    selected = isSelected
                                )
                            )
                        }
                    }
                }

                // Value Editor Section based on Type
                when (selectedType) {
                    AddFlagType.BOOLEAN -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                .border(
                                    1.dp,
                                    if (boolValue) accents.success.copy(alpha = 0.4f) else accents.danger.copy(alpha = 0.4f),
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    boolValue = !boolValue
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = if (boolValue) stringResource(R.string.edit_dialog_enabled) else stringResource(R.string.edit_dialog_disabled),
                                    fontWeight = FontWeight.Bold,
                                    color = if (boolValue) accents.success else accents.danger,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "value: $boolValue",
                                    fontFamily = MonoFamily,
                                    fontSize = 11.sp,
                                    color = accents.textMuted
                                )
                            }
                            Switch(
                                checked = boolValue,
                                onCheckedChange = { boolValue = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = accents.success,
                                    uncheckedThumbColor = Color.White,
                                    uncheckedTrackColor = accents.danger
                                )
                            )
                        }
                    }

                    AddFlagType.STRING -> {
                        OutlinedTextField(
                            value = stringValue,
                            onValueChange = { stringValue = it },
                            label = { Text(stringResource(R.string.add_flag_value_label)) },
                            placeholder = { Text(stringResource(R.string.add_flag_value_placeholder)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            trailingIcon = {
                                if (stringValue.isNotEmpty()) {
                                    IconButton(onClick = { stringValue = "" }) {
                                        Icon(Icons.Rounded.Close, null, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        )
                    }

                    AddFlagType.NUMBER -> {
                        OutlinedTextField(
                            value = numberValue,
                            onValueChange = { numberValue = it },
                            label = { Text(stringResource(R.string.add_flag_value_label)) },
                            placeholder = { Text("0") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            isError = !isNumberValid,
                            supportingText = {
                                if (!isNumberValid) {
                                    Text(
                                        text = stringResource(R.string.add_flag_error_invalid_number),
                                        color = accents.danger,
                                        fontSize = 11.sp
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    AddFlagType.ARRAY -> {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(
                                value = arrayValue,
                                onValueChange = { arrayValue = it },
                                label = { Text(stringResource(R.string.add_flag_value_label)) },
                                placeholder = { Text("[\"1:true\", \"2:false\"]") },
                                minLines = 3,
                                maxLines = 5,
                                isError = !isArrayValid,
                                supportingText = {
                                    if (!isArrayValid) {
                                        Text(
                                            text = stringResource(R.string.add_flag_error_invalid_json),
                                            color = accents.danger,
                                            fontSize = 11.sp
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )

                            // Quick Templates
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                SuggestionChip(
                                    onClick = { arrayValue = "[\"1:true\"]" },
                                    label = { Text("[\"1:true\"]", fontFamily = MonoFamily, fontSize = 10.sp) }
                                )
                                SuggestionChip(
                                    onClick = { arrayValue = "[\"1:true\", \"2:false\"]" },
                                    label = { Text("[\"1:true\", \"2:false\"]", fontFamily = MonoFamily, fontSize = 10.sp) }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    val value: Any? = when (selectedType) {
                        AddFlagType.BOOLEAN -> boolValue
                        AddFlagType.STRING -> stringValue
                        AddFlagType.NUMBER -> numberValue.toLongOrNull() ?: numberValue.toDoubleOrNull() ?: numberValue
                        AddFlagType.ARRAY -> try { JSONArray(arrayValue.trim()) } catch (_: Exception) { arrayValue }
                    }
                    onSave(trimmedPath, value)
                }
            ) {
                Text(
                    text = stringResource(R.string.add_flag_confirm),
                    fontWeight = FontWeight.Bold,
                    color = if (canSave) accents.teal else accents.textMuted
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
