 /*
  * Copyright (C) 2026 ViictorNunes (https://github.com/ViictorNunes/FlagSync).
  * Licensed under the GNU General Public License v3.0.
  */


package com.flag.sync.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flag.sync.R
import com.flag.sync.logic.BackupConverter
import com.flag.sync.logic.ConvertOutcome
import com.flag.sync.logic.JsonDiff
import com.flag.sync.ui.components.AppHeader
import com.flag.sync.ui.components.PrimaryButton
import com.flag.sync.ui.components.UploadCard
import com.flag.sync.ui.components.VerticalScrollbar
import com.flag.sync.ui.theme.LocalAccentColors
import com.flag.sync.ui.theme.MonoFamily
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.flag.sync.utils.displayNameFromUri
import com.flag.sync.utils.readTextFromUri
import com.flag.sync.utils.writeTextToUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ConverterScreen(
    cleanData: Map<String, Any?>?, onCleanDataChanged: (Map<String, Any?>?) -> Unit,
    fileName: String?, onFileNameChanged: (String?) -> Unit,
    status: String?, onStatusChanged: (String?) -> Unit,
    autoRenameOverrides: Boolean = false,
    useCustomPicker: Boolean = false,
    onRequestInternalPicker: (com.flag.sync.model.PickerTarget) -> Unit = {},
    onSaveFile: (String, String) -> Unit = { _, _ -> },
    listState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
) {
    val context = LocalContext.current
    val accents = LocalAccentColors.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        errorMsg = null
        onCleanDataChanged(null)
        val name = displayNameFromUri(context, uri)
        
        if (!name.lowercase().endsWith(".ibackup")) {
            Toast.makeText(context, context.getString(R.string.error_invalid_extension) + " (.ibackup)", Toast.LENGTH_LONG).show()
            return@rememberLauncherForActivityResult
        }

        onFileNameChanged(name)
        onStatusChanged(context.getString(R.string.converter_status_reading))

        scope.launch {
            try {
                val raw = readTextFromUri(context, uri)
                val outcome = withContext(Dispatchers.Default) {
                    BackupConverter.convert(name, raw)
                }
                when (outcome) {
                    is ConvertOutcome.Success -> {
                        val flattened = withContext(Dispatchers.Default) {
                            JsonDiff.flatten(outcome.data)
                        }
                        onCleanDataChanged(flattened)
                        onStatusChanged(context.getString(R.string.converter_status_success))
                    }
                    is ConvertOutcome.Error -> {
                        errorMsg = context.getString(outcome.resId, outcome.arg ?: "")
                        onStatusChanged(context.getString(R.string.converter_status_not_recognized))
                    }
                }
            } catch (e: Exception) {
                errorMsg = context.getString(R.string.converter_status_failed) + ": ${e.message}"
                onStatusChanged(context.getString(R.string.converter_status_failed))
            }
        }
    }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        val flatData = cleanData ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val jsonText = withContext(Dispatchers.Default) {
                JsonDiff.unflattenAndSerializeCompact(flatData)
            }
            writeTextToUri(context, uri, jsonText)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = paddingValues.calculateBottomPadding() + 200.dp)
            ) {
                item {
                    AppHeader(
                        eyebrow = stringResource(R.string.converter_eyebrow),
                        title = stringResource(R.string.converter_title),
                        description = stringResource(R.string.converter_description)
                    )
                }

                item {
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = accents.danger.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, accents.danger.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Warning,
                                    contentDescription = null,
                                    tint = accents.danger,
                                    modifier = Modifier.size(28.dp)
                                )
                                Text(
                                    text = stringResource(R.string.converter_legal_warning),
                                    color = accents.danger,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 17.sp,
                                    textAlign = TextAlign.Justify
                                )
                            }
                        }
                    }
                }

                item {
                    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                        Text(
                            text = stringResource(R.string.converter_step_upload),
                            fontFamily = MonoFamily,
                            fontSize = 11.sp,
                            color = accents.textMuted,
                            modifier = Modifier.padding(top = 12.dp, bottom = 10.dp)
                        )
                        UploadCard(
                            label = stringResource(R.string.converter_backup_label),
                            icon = Icons.Rounded.Sync,
                            fileName = fileName,
                            hint = status ?: ".ibackup",
                            onClick = {
                                if (fileName != null) {
                                    Toast.makeText(context, context.getString(R.string.error_file_already_loaded), Toast.LENGTH_SHORT).show()
                                } else {
                                    if (useCustomPicker) onRequestInternalPicker(com.flag.sync.model.PickerTarget.CONVERTED) else pickLauncher.launch("*/*")
                                }
                            },
                            error = errorMsg,
                            onClear = if (fileName != null) {
                                {
                                    onFileNameChanged(null); onStatusChanged(null); onCleanDataChanged(null); errorMsg = null
                                }
                            } else null
                        )

                        if (cleanData != null) {
                            Column(modifier = Modifier.padding(top = 18.dp)) {

                                PrimaryButton(
                                    text = stringResource(R.string.converter_download_btn),
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        val suggested = if (autoRenameOverrides) "mc_overrides.json" else (fileName ?: "backup").substringBeforeLast('.') + ".json"
                                        if (useCustomPicker) {
                                            val data = JsonDiff.unflattenAndSerializeCompact(cleanData ?: emptyMap())
                                            onSaveFile(data, suggested)
                                        } else {
                                            saveLauncher.launch(suggested)
                                        }
                                    },
                                    accent = accents.violet,
                                    modifier = Modifier.padding(top = 14.dp)
                                )
                            }
                        }
                    }
                }
            }

            VerticalScrollbar(
                state = listState,
                bottomPadding = 180.dp,
                modifier = Modifier
                    .align(androidx.compose.ui.Alignment.CenterEnd)
                    .padding(end = 4.dp)
            )
        }
    }
}

