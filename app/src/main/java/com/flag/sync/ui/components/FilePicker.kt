 /*
  * Copyright (C) 2026 ViictorNunes (https://github.com/ViictorNunes/FlagSync).
  * Licensed under the GNU General Public License v3.0.
  */


package com.flag.sync.ui.components

import android.os.Environment
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flag.sync.R
import com.flag.sync.ui.theme.LocalAccentColors
import com.flag.sync.ui.theme.MonoFamily
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun InternalFilePicker(
    onFilePicked: (File) -> Unit,
    onDismiss: () -> Unit,
    saveMode: Boolean = false,
    suggestedFileName: String = "",
    onSaveFile: (File, String) -> Unit = { _, _ -> },
    allowedExtensions: Set<String>? = null
) {
    val accents = LocalAccentColors.current
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("flag_sync_prefs", android.content.Context.MODE_PRIVATE) }

    val internalStorage = Environment.getExternalStorageDirectory()

    val hasPermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    // Remember last position
    var currentDir by remember {
        val lastPath = prefs.getString("last_picker_path", null)
        val dir = if (lastPath != null) File(lastPath) else internalStorage
        mutableStateOf(if (dir.exists() && dir.isDirectory) dir else internalStorage)
    }

    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }

    var fileName by remember(suggestedFileName) { mutableStateOf(suggestedFileName.substringBeforeLast('.')) }
    var showOverwriteDialog by remember { mutableStateOf(false) }
    var pendingSaveFile by remember { mutableStateOf<File?>(null) }

    val files = remember(currentDir, searchQuery, saveMode, allowedExtensions) {
        if (!hasPermission) return@remember emptyList<File>()
        val list = currentDir.listFiles()?.toList() ?: emptyList()
        val filtered = list.filter {
            (it.isDirectory || (allowedExtensions != null && it.extension.lowercase() in allowedExtensions)) &&
                    it.name.contains(searchQuery, ignoreCase = true) &&
                    !it.name.startsWith(".")
        }
        val (dirs, nonDirs) = filtered.partition { it.isDirectory }
        dirs.sortedBy { it.name.lowercase() } + nonDirs.sortedByDescending { it.lastModified() }
    }

    // Save path when it changes
    LaunchedEffect(currentDir) {
        prefs.edit().putString("last_picker_path", currentDir.absolutePath).apply()
    }

    BackHandler(enabled = true) {
        if (isSearching) {
            isSearching = false
            searchQuery = ""
        } else if (currentDir.absolutePath != internalStorage.absolutePath && currentDir.parentFile != null) {
            currentDir = currentDir.parentFile!!
        } else {
            onDismiss()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header covering status bar
            Surface(
                color = MaterialTheme.colorScheme.background,
                tonalElevation = 3.dp
            ) {
                Column(modifier = Modifier.statusBarsPadding()) {
                    AnimatedContent(targetState = isSearching, label = "FilePickerHeader") { searching ->
                        if (searching) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { isSearching = false; searchQuery = "" }) {
                                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, tint = accents.teal)
                                }
                                Box(modifier = Modifier.weight(1f).padding(horizontal = 8.dp), contentAlignment = Alignment.CenterStart) {
                                    if (searchQuery.isEmpty()) {
                                        Text(stringResource(R.string.file_picker_search_hint), color = accents.textMuted.copy(alpha = 0.5f))
                                    }
                                    BasicTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        singleLine = true,
                                        cursorBrush = SolidColor(accents.teal),
                                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    IconButton(onClick = onDismiss, modifier = Modifier.padding(end = 8.dp)) {
                                        Icon(Icons.Rounded.Close, null, tint = accents.textMuted)
                                    }
                                    Column {
                                        Text(
                                            text = if (saveMode) stringResource(R.string.file_picker_save_backup) else stringResource(R.string.file_picker_select_file),
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Black,
                                            fontFamily = MonoFamily
                                        )
                                        Text(
                                            text = currentDir.absolutePath.replace(internalStorage.absolutePath, "Storage"),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = accents.textMuted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                IconButton(onClick = { isSearching = true }) {
                                    Icon(Icons.Outlined.Search, null, tint = accents.textMuted)
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
                }
            }

            if (!hasPermission) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Rounded.Storage,
                            contentDescription = null,
                            tint = accents.textMuted.copy(alpha = 0.3f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.file_picker_permission_denied),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            color = accents.textMuted,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                                    try {
                                        val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                                        intent.data = android.net.Uri.parse("package:${context.packageName}")
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                        val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                        context.startActivity(intent)
                                    }
                                } else {
                                    val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                    intent.data = android.net.Uri.parse("package:${context.packageName}")
                                    context.startActivity(intent)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = accents.teal),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(stringResource(R.string.settings_picker_permission_confirm), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = if (saveMode) 160.dp else 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (currentDir.absolutePath != internalStorage.absolutePath) {
                            item {
                                FilePickerRow(
                                    name = stringResource(R.string.file_picker_parent_dir),
                                    icon = Icons.Rounded.ArrowUpward,
                                    detail = null,
                                    accent = accents.textMuted,
                                    onClick = { currentDir = currentDir.parentFile ?: internalStorage }
                                )
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.05f))
                            }
                        }

                        if (files.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().height(300.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = if (searchQuery.isEmpty()) Icons.Rounded.FolderOff else Icons.Rounded.SearchOff,
                                            contentDescription = null,
                                            tint = accents.textMuted.copy(alpha = 0.2f),
                                            modifier = Modifier.size(64.dp)
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = if (searchQuery.isEmpty()) stringResource(R.string.file_picker_no_files) else stringResource(R.string.file_picker_no_results),
                                            color = accents.textMuted,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                        }

                        items(files, key = { it.absolutePath }) { file ->
                            val isDir = file.isDirectory
                            val detail = if (!isDir) {
                                val size = file.length()
                                val locale = Locale.getDefault()
                                val sizeStr = if (size > 1024 * 1024) String.format(locale, "%.1f MB", size / (1024f * 1024f))
                                else String.format(locale, "%d KB", size / 1024)
                                val date = SimpleDateFormat("dd/MM/yy", locale).format(Date(file.lastModified()))
                                "$sizeStr · $date"
                            } else null

                            FilePickerRow(
                                name = file.name,
                                icon = if (isDir) Icons.Rounded.Folder else Icons.AutoMirrored.Outlined.InsertDriveFile,
                                detail = detail,
                                accent = if (isDir) accents.amber else accents.teal,
                                onClick = {
                                    if (isDir) currentDir = file
                                    else if (!saveMode) onFilePicked(file)
                                    else {
                                        fileName = file.name.substringBeforeLast('.')
                                    }
                                }
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.05f))
                        }
                    }

                    if (saveMode) {
                        Surface(
                            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 8.dp,
                            shadowElevation = 16.dp
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .navigationBarsPadding()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = stringResource(R.string.file_picker_filename_label),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = accents.textMuted,
                                        fontWeight = FontWeight.Bold
                                    )
                                    OutlinedTextField(
                                        value = fileName,
                                        onValueChange = { fileName = it },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = MonoFamily),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = accents.teal,
                                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                            cursorColor = accents.teal
                                        )
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = onDismiss,
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(stringResource(R.string.edit_dialog_cancel))
                                    }
                                    Button(
                                        onClick = { 
                                            val finalName = if (fileName.lowercase().endsWith(".json")) fileName else "$fileName.json"
                                            val targetFile = File(currentDir, finalName)
                                            if (targetFile.exists()) {
                                                pendingSaveFile = targetFile
                                                showOverwriteDialog = true
                                            } else {
                                                onSaveFile(currentDir, finalName)
                                            }
                                        },
                                        modifier = Modifier.weight(1.5f),
                                        colors = ButtonDefaults.buttonColors(containerColor = accents.teal),
                                        shape = RoundedCornerShape(12.dp),
                                        enabled = fileName.isNotBlank()
                                    ) {
                                        Icon(Icons.Rounded.Save, null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = stringResource(R.string.file_picker_save_button),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showOverwriteDialog) {
            AlertDialog(
                onDismissRequest = { showOverwriteDialog = false },
                title = { Text(stringResource(R.string.file_picker_overwrite_title)) },
                text = { Text(stringResource(R.string.file_picker_overwrite_message, pendingSaveFile?.name ?: "")) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showOverwriteDialog = false
                            pendingSaveFile?.let { onSaveFile(currentDir, it.name) }
                        }
                    ) {
                        Text(stringResource(R.string.file_picker_overwrite_confirm), color = accents.danger, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showOverwriteDialog = false }) {
                        Text(stringResource(R.string.edit_dialog_cancel))
                    }
                }
            )
        }
    }
}

@Composable
private fun FilePickerRow(
    name: String,
    icon: ImageVector,
    detail: String?,
    accent: Color,
    onClick: () -> Unit
) {
    val accents = LocalAccentColors.current
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(accent.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (detail == null) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (detail != null) {
                    Text(text = detail, fontSize = 12.sp, color = accents.textMuted)
                }
            }

            if (detail == null && !name.contains("(")) {
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = accents.textMuted.copy(alpha = 0.3f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}