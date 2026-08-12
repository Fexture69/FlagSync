 /*
  * Copyright (C) 2026 ViictorNunes (https://github.com/ViictorNunes/FlagSync).
  * Licensed under the GNU General Public License v3.0.
  */


package com.flag.sync.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flag.sync.R
import com.flag.sync.logic.JsonDiff
import com.flag.sync.ui.theme.LocalAccentColors
import com.flag.sync.ui.theme.MonoFamily
import com.flag.sync.utils.getHighContrastContentColor
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import org.json.JSONArray

@Composable
fun EditFlagDialog(
    path: String,
    displayName: String,
    currentValue: Any?,
    mapper: com.flag.sync.logic.FlagMapper?,
    confirmDeletion: Boolean,
    onDismiss: () -> Unit,
    onSave: (Any?) -> Unit,
    sessionOriginalValue: Any? = null
) {
    val accents = LocalAccentColors.current
    val haptic = LocalHapticFeedback.current
    var editedValue by remember(currentValue) { mutableStateOf(currentValue) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var pendingDeleteIndex by remember { mutableIntStateOf(-1) }

    var showSubflagTextEditDialog by remember { mutableStateOf(false) }
    var editingSubflagIndex by remember { mutableIntStateOf(-1) }
    var tempSubflagValue by remember { mutableStateOf("") }
    
    val isBoolean = remember(editedValue) {
        val s = editedValue.toString().lowercase().trim()
        s == "true" || s == "false" || editedValue is Boolean
    }
    
    val isArray = editedValue is JSONArray

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = displayName.replace("_", " "),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                if (isBoolean) {
                    val boolValue = editedValue.toString().toBoolean()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .clickable { editedValue = (!boolValue).toString() }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (boolValue) stringResource(R.string.edit_dialog_enabled) else stringResource(R.string.edit_dialog_disabled),
                            fontWeight = FontWeight.Bold,
                            color = if (boolValue) accents.success else accents.danger
                        )
                        val useDarkThumb = accents.success.luminance() > 0.5f
                        Switch(
                            checked = boolValue,
                            onCheckedChange = { 
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                editedValue = it.toString() 
                            },
                            thumbContent = {
                                Icon(
                                    imageVector = if (boolValue) Icons.Rounded.Check else Icons.Rounded.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = if (useDarkThumb) accents.accentDark else Color.White,
                                checkedTrackColor = accents.success,
                                checkedIconColor = if (useDarkThumb) accents.success else accents.success,
                                uncheckedThumbColor = accents.textMuted,
                                uncheckedTrackColor = Color.Transparent,
                                uncheckedIconColor = MaterialTheme.colorScheme.surface,
                                uncheckedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )
                    }
                } else if (isArray) {
                    val arr = editedValue as JSONArray
                    
                    // Helper data class for parsed subflags
                    data class SubFlagEntry(
                        val index: Int,
                        val label: String,
                        val value: String,
                        val isBoolean: Boolean,
                        val rawItem: String
                    )

                    val entries = remember(editedValue) {
                        val list = mutableListOf<SubFlagEntry>()
                        for (i in 0 until arr.length()) {
                            val raw = arr.optString(i) ?: ""
                            val parts = raw.split(":")
                            if (parts.size >= 2) {
                                val id = parts[0].trim()
                                val valStr = parts.last().trim()
                                val isBool = valStr.lowercase() == "true" || valStr.lowercase() == "false"
                                val label = mapper?.getParamLabel(path, id) ?: "#$id"
                                list.add(SubFlagEntry(i, label, valStr, isBool, raw))
                            } else {
                                list.add(SubFlagEntry(i, "", raw, false, raw))
                            }
                        }
                        list
                    }
                    
                    Text(
                        text = stringResource(R.string.edit_dialog_subflags_label, entries.size),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = accents.textMuted,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
                    ) {
                        itemsIndexed(entries) { _, entry ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    if (entry.label.isNotEmpty()) {
                                        Text(
                                            text = entry.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (!entry.isBoolean) {
                                        Text(
                                            text = entry.value,
                                            fontFamily = MonoFamily,
                                            fontSize = 12.sp,
                                            color = accents.textMuted
                                        )
                                    } else {
                                        Text(
                                            text = entry.value.uppercase(),
                                            fontFamily = MonoFamily,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (entry.value.toBoolean()) accents.success else accents.danger
                                        )
                                    }
                                }
                                
                                if (entry.isBoolean) {
                                    val isChecked = entry.value.toBoolean()
                                    val useDarkThumb = accents.success.luminance() > 0.5f
                                    Switch(
                                        checked = isChecked,
                                        onCheckedChange = { checked ->
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            val newArr = JSONArray()
                                            for (i in 0 until arr.length()) {
                                                if (i == entry.index) {
                                                    val raw = arr.getString(i)
                                                    val parts = raw.split(":").toMutableList()
                                                    parts[parts.size - 1] = " $checked"
                                                    newArr.put(parts.joinToString(":"))
                                                } else {
                                                    newArr.put(arr.get(i))
                                                }
                                            }
                                            editedValue = newArr
                                        },
                                        modifier = Modifier.scale(0.8f),
                                        thumbContent = {
                                            Icon(
                                                imageVector = if (isChecked) Icons.Rounded.Check else Icons.Rounded.Close,
                                                contentDescription = null,
                                                modifier = Modifier.size(SwitchDefaults.IconSize)
                                            )
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = if (useDarkThumb) accents.accentDark else Color.White,
                                            checkedTrackColor = accents.success,
                                            checkedIconColor = if (useDarkThumb) accents.success else accents.success,
                                            uncheckedThumbColor = accents.textMuted,
                                            uncheckedTrackColor = Color.Transparent,
                                            uncheckedIconColor = MaterialTheme.colorScheme.surface,
                                            uncheckedBorderColor = MaterialTheme.colorScheme.outline
                                        )
                                    )
                                }
else {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(accents.teal.copy(alpha = 0.1f))
                                            .clickable {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                editingSubflagIndex = entry.index
                                                tempSubflagValue = entry.value
                                                showSubflagTextEditDialog = true
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Edit,
                                            contentDescription = stringResource(R.string.edit_action_description),
                                            tint = accents.teal,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                
                                Spacer(modifier = Modifier.width(8.dp))

                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(accents.danger.copy(alpha = 0.1f))
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            if (confirmDeletion) {
                                                pendingDeleteIndex = entry.index
                                                showDeleteConfirm = true
                                            } else {
                                                val newArr = JSONArray()
                                                for (i in 0 until arr.length()) {
                                                    if (i != entry.index) newArr.put(arr.get(i))
                                                }
                                                editedValue = newArr
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = stringResource(R.string.edit_dialog_remove_subflag),
                                        tint = accents.danger,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            if (entry.index < arr.length() - 1) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                                )
                            }
                        }

                        // Removed Subflags Section (using session baseline)
                        val baselineValue = sessionOriginalValue ?: currentValue
                        if (baselineValue is JSONArray) {
                            // Extract IDs of currently active subflags
                            val currentIds = mutableSetOf<String>()
                            for (i in 0 until arr.length()) {
                                val raw = arr.optString(i) ?: ""
                                val id = raw.split(":").getOrNull(0)?.trim() ?: ""
                                if (id.isNotEmpty()) currentIds.add(id)
                            }
                            
                            val removedItems = mutableListOf<String>()
                            for (i in 0 until baselineValue.length()) {
                                val rawItem = baselineValue.getString(i)
                                val id = rawItem.split(":").getOrNull(0)?.trim() ?: ""
                                // Only consider it removed if the ID is missing from current list
                                if (id.isNotEmpty() && !currentIds.contains(id)) {
                                    removedItems.add(rawItem)
                                }
                            }

                            if (removedItems.isNotEmpty()) {
                                item {
                                    Box(modifier = Modifier.fillMaxWidth().background(accents.danger.copy(alpha = 0.05f)).padding(horizontal = 12.dp, vertical = 8.dp)) {
                                        Text(
                                            text = stringResource(R.string.edit_dialog_removed_section_title, removedItems.size),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = accents.danger
                                        )
                                    }
                                }

                                itemsIndexed(removedItems) { index, rawItem ->
                                    val parts = rawItem.split(":")
                                    val id = parts.getOrNull(0)?.trim() ?: ""
                                    val label = mapper?.getParamLabel(path, id) ?: "#$id"
                                    val value = parts.lastOrNull()?.trim() ?: ""

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(text = label, style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
                                            Text(text = value, fontFamily = MonoFamily, fontSize = 11.sp, color = accents.textMuted.copy(alpha = 0.6f))
                                        }
                                        
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(accents.teal.copy(alpha = 0.1f))
                                                .clickable {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    val newArr = JSONArray()
                                                    for (i in 0 until arr.length()) newArr.put(arr.get(i))
                                                    newArr.put(rawItem)
                                                    editedValue = newArr
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Add,
                                                contentDescription = stringResource(R.string.edit_dialog_restore_subflag),
                                                tint = accents.teal,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    if (index < removedItems.size - 1) {
                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.05f))
                                    }
                                }
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = editedValue.toString(),
                        onValueChange = { editedValue = it },
                        label = { Text(stringResource(R.string.edit_dialog_value_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFamily)
                    )
                }
                
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(editedValue) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = accents.teal,
                    contentColor = getHighContrastContentColor(accents.teal)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.edit_dialog_save), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.edit_dialog_cancel),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.delete_subflag_confirm_title)) },
            text = { Text(stringResource(R.string.delete_subflag_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val newArr = JSONArray()
                        val arr = editedValue as JSONArray
                        for (i in 0 until arr.length()) {
                            if (i != pendingDeleteIndex) newArr.put(arr.get(i))
                        }
                        editedValue = newArr
                        showDeleteConfirm = false
                    }
                ) {
                    Text(stringResource(R.string.delete_confirm_action), color = accents.danger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.edit_dialog_cancel))
                }
            }
        )
    }

    if (showSubflagTextEditDialog) {
        val arr = editedValue as JSONArray
        val raw = arr.optString(editingSubflagIndex)
        val id = raw.split(":").getOrNull(0)?.trim() ?: ""
        val label = mapper?.getParamLabel(path, id) ?: "#$id"

        AlertDialog(
            onDismissRequest = { showSubflagTextEditDialog = false },
            title = { Text(text = label, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = tempSubflagValue,
                    onValueChange = { tempSubflagValue = it },
                    label = { Text(stringResource(R.string.edit_dialog_value_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFamily)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newArr = JSONArray()
                        for (i in 0 until editingSubflagIndex) {
                            newArr.put(arr.get(i))
                        }
                        val parts = arr.getString(editingSubflagIndex).split(":").toMutableList()
                        parts[parts.size - 1] = " $tempSubflagValue"
                        newArr.put(parts.joinToString(":"))
                        for (i in editingSubflagIndex + 1 until arr.length()) {
                            newArr.put(arr.get(i))
                        }
                        editedValue = newArr
                        showSubflagTextEditDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accents.teal,
                        contentColor = getHighContrastContentColor(accents.teal)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.edit_dialog_save), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSubflagTextEditDialog = false }) {
                    Text(
                        text = stringResource(R.string.edit_dialog_cancel),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
