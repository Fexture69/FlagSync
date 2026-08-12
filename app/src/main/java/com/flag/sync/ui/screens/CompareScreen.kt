 /*
  * Copyright (C) 2026 ViictorNunes (https://github.com/ViictorNunes/FlagSync).
  * Licensed under the GNU General Public License v3.0.
  */


package com.flag.sync.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flag.sync.MainViewModel
import com.flag.sync.R
import com.flag.sync.logic.BackupConverter
import com.flag.sync.logic.ConvertOutcome
import com.flag.sync.logic.FlagMapper
import com.flag.sync.logic.FlagMapperProvider
import com.flag.sync.logic.JsonDiff
import com.flag.sync.model.*
import com.flag.sync.ui.components.*
import com.flag.sync.ui.theme.LocalAccentColors
import com.flag.sync.ui.theme.MonoFamily
import com.flag.sync.utils.displayNameFromUri
import com.flag.sync.utils.readTextFromUri
import com.flag.sync.utils.readTextFromUri
import com.flag.sync.utils.writeTextToUri
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompareScreen(
    viewModel: MainViewModel,
    listState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
    isDebugMode: Boolean = false,
    confirmDeletion: Boolean = true,
    autoRenameOverrides: Boolean = false,
    onConfirmDeletionChange: (Boolean) -> Unit = {},
    useCustomPicker: Boolean = false,
    onRequestInternalPicker: (PickerTarget) -> Unit = {},
    onSaveFile: (String, String, PickerTarget) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Auto-scroll to top when mode changes
    LaunchedEffect(viewModel.compareMode) {
        listState.animateScrollToItem(0)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        floatingActionButton = {
            val showButton by remember {
                derivedStateOf { listState.firstVisibleItemIndex > 5 }
            }
            AnimatedVisibility(
                visible = showButton,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                FloatingActionButton(
                    onClick = { scope.launch { listState.animateScrollToItem(0) } },
                    containerColor = LocalAccentColors.current.teal,
                    contentColor = Color.White,
                    shape = androidx.compose.foundation.shape.CircleShape,
                    modifier = Modifier.padding(bottom = 80.dp) // Avoid overlap with bottom nav
                ) {
                    Icon(Icons.Rounded.ArrowUpward, contentDescription = "Scroll to top")
                }
            }
        }
    ) { paddingValues ->
        UnifiedCompare(context, viewModel, listState, paddingValues, isDebugMode, confirmDeletion, autoRenameOverrides, onConfirmDeletionChange, useCustomPicker, onRequestInternalPicker, onSaveFile)
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun UnifiedCompare(
    context: android.content.Context,
    vm: MainViewModel,
    listState: androidx.compose.foundation.lazy.LazyListState,
    scaffoldPadding: PaddingValues,
    isDebugMode: Boolean,
    confirmDeletion: Boolean,
    autoRenameOverrides: Boolean,
    onConfirmDeletionChange: (Boolean) -> Unit,
    useCustomPicker: Boolean,
    onRequestInternalPicker: (PickerTarget) -> Unit,
    onSaveFile: (String, String, PickerTarget) -> Unit
) {
    val accents = LocalAccentColors.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    var listAExpanded by remember { mutableStateOf(false) }
    var listBExpanded by remember { mutableStateOf(false) }
    var showModeMenu by remember { mutableStateOf(false) }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var pendingDeletePath by remember { mutableStateOf("") }
    var pendingDeleteSource by remember { mutableStateOf('A') }
    var isFromMovedList by remember { mutableStateOf(false) }
    var dontAskAgainChecked by remember { mutableStateOf(false) }

    var showMovedFlagsSheet by remember { mutableStateOf(false) }
    var showDeletedFlagsSheet by remember { mutableStateOf(false) }
    var showEditedFlagsSheet by remember { mutableStateOf(false) }
    var showBulkDeleteDialog by remember { mutableStateOf(false) }
    var showBulkRestoreDialog by remember { mutableStateOf(false) }
    var showChangelogDialog by remember { mutableStateOf(false) }
    var changelogLimitEnabled by remember { mutableStateOf(true) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editingFlagPath by remember { mutableStateOf("") }
    var editingFlagDisplayName by remember { mutableStateOf("") }
    var editingFlagValue by remember { mutableStateOf<Any?>(null) }
    var editingFlagOriginalValue by remember { mutableStateOf<Any?>(null) }
    var editingFlagSource by remember { mutableStateOf('A') }
    var movedSource by remember { mutableStateOf('A') }
    var deletedSource by remember { mutableStateOf('A') }
    val sheetState = rememberModalBottomSheetState()
    val expandedCategories = remember { mutableStateMapOf<String, Boolean>() }

    var lastUpdateFeedbackTime by remember { mutableLongStateOf(0L) }

    val groupingToggle = @Composable { isEnabled: Boolean, onToggle: () -> Unit, accent: Color ->
        FilterChip(
            selected = isEnabled,
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onToggle()
            },
            label = { Text(stringResource(R.string.compare_group_by_area), fontSize = 11.sp) },
            leadingIcon = { if (isEnabled) Icon(Icons.Rounded.Check, null, modifier = Modifier.size(16.dp)) },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = accent.copy(alpha = 0.2f),
                selectedLabelColor = accent,
                selectedLeadingIconColor = accent
            ),
            border = FilterChipDefaults.filterChipBorder(
                borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                selectedBorderColor = accent.copy(alpha = 0.5f),
                borderWidth = 1.dp,
                selectedBorderWidth = 1.dp,
                enabled = true,
                selected = isEnabled
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.height(40.dp)
        )
    }

    val performDelete = {
        if (pendingDeleteSource == 'A') {
            if (isFromMovedList) vm.removeMovedFlagA(pendingDeletePath)
            else {
                vm.flatA?.get(pendingDeletePath)?.let { vm.addDeletedFlagA(FlatFlag(pendingDeletePath, it)) }
                val newA = (vm.flatA ?: emptyMap()).toMutableMap()
                newA.remove(pendingDeletePath)
                vm.updateFlatA(newA)
            }
        } else {
            if (isFromMovedList) vm.removeMovedFlagB(pendingDeletePath)
            else {
                vm.flatB?.get(pendingDeletePath)?.let { vm.addDeletedFlagB(FlatFlag(pendingDeletePath, it)) }
                val newB = (vm.flatB ?: emptyMap()).toMutableMap()
                newB.remove(pendingDeletePath)
                vm.updateFlatB(newB)
            }
        }
        if (dontAskAgainChecked) onConfirmDeletionChange(false)
        showDeleteDialog = false
        dontAskAgainChecked = false
        isFromMovedList = false
    }

    val performBulkDelete = {
        if (movedSource == 'A') vm.clearMovedFlagsA() else vm.clearMovedFlagsB()
        if (dontAskAgainChecked) onConfirmDeletionChange(false)
        showBulkDeleteDialog = false
    }

    val performBulkRestore = {
        if (deletedSource == 'A') vm.restoreAllDeletedFlagsA() else vm.restoreAllDeletedFlagsB()
        if (dontAskAgainChecked) onConfirmDeletionChange(false)
        showBulkRestoreDialog = false
    }

    val pickMap = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val name = displayNameFromUri(context, it)
            if (!name.lowercase().endsWith(".json")) {
                Toast.makeText(context, context.getString(R.string.error_invalid_extension) + " (.json)", Toast.LENGTH_LONG).show()
                return@rememberLauncherForActivityResult
            }
            try {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                val raw = readTextFromUri(context, it)
                vm.updateMapper(FlagMapper(raw), isDefault = false)
            } catch (e: Exception) { error = context.getString(R.string.error_reading_mapping, e.message ?: "") }
        }
    }

    val pickA = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { pickedUri ->
            val name = displayNameFromUri(context, pickedUri)
            if (!name.lowercase().endsWith(".json") && !name.lowercase().endsWith(".ibackup")) {
                Toast.makeText(context, context.getString(R.string.error_invalid_extension) + " (.json / .ibackup)", Toast.LENGTH_LONG).show()
                return@rememberLauncherForActivityResult
            }
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            scope.launch {
                loadFlatInto(
                    context = context,
                    uri = pickedUri,
                    onName = { vm.updateNameA(it) },
                    onFlat = {
                        vm.updateFlatA(it)
                        vm.updateErrorA(null)
                        error = null
                    },
                    onError = { vm.updateErrorA(it) }
                )
            }
        }
    }
    val pickB = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { pickedUri ->
            val name = displayNameFromUri(context, pickedUri)
            if (!name.lowercase().endsWith(".json") && !name.lowercase().endsWith(".ibackup")) {
                Toast.makeText(context, context.getString(R.string.error_invalid_extension) + " (.json / .ibackup)", Toast.LENGTH_LONG).show()
                return@rememberLauncherForActivityResult
            }
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            scope.launch {
                loadFlatInto(
                    context = context,
                    uri = pickedUri,
                    onName = { vm.updateNameB(it) },
                    onFlat = {
                        vm.updateFlatB(it)
                        vm.updateErrorB(null)
                        error = null
                    },
                    onError = { vm.updateErrorB(it) }
                )
            }
        }
    }
    val saveA = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        vm.flatA?.let { data -> uri?.let { writeTextToUri(context, it, JsonDiff.unflattenAndSerializeCompact(data)) } }
    }
    val saveB = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        vm.flatB?.let { data -> uri?.let { writeTextToUri(context, it, JsonDiff.unflattenAndSerializeCompact(data)) } }
    }

    LaunchedEffect(vm.flatA, vm.compareMode) {
        if (vm.compareMode == CompareMode.SINGLE_CHECK && vm.flatA != null) {
            listAExpanded = true
        }
    }

    LaunchedEffect(vm.flatA, vm.flatB, vm.compareMode) {
        if ((vm.compareMode == CompareMode.COMPARE) && (vm.flatA != null) && (vm.flatB != null)) {
            val res = withContext(Dispatchers.Default) { JsonDiff.compare(vm.flatA!!, vm.flatB!!) }
            vm.updateCompareResult(res)
        } else vm.updateCompareResult(null)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = scaffoldPadding.calculateBottomPadding() + 220.dp)
        ) {
            item(key = "header") { AppHeader(eyebrow = stringResource(R.string.compare_eyebrow), title = stringResource(R.string.compare_title), description = stringResource(R.string.compare_description)) }

            // Global Performance Warning (High Visibility)
            if ((vm.countA >= 800) || (vm.countB >= 800)) {
                item(key = "perf_warning_global") { PerformanceWarning() }
            }

            item(key = "mode_selector") {
                val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
                Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.6f else 0.4f)).border(1.5.dp, if (isDark) accents.teal.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.8f), RoundedCornerShape(16.dp))) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { showModeMenu = true }.padding(vertical = 14.dp, horizontal = 20.dp).fillMaxWidth()) {
                        val modeLabel = when(vm.compareMode) {
                            CompareMode.COMPARE -> stringResource(R.string.compare_mode_two_files)
                            CompareMode.DOUBLE_CHECK -> stringResource(R.string.compare_mode_double_file)
                            CompareMode.SINGLE_CHECK -> stringResource(R.string.compare_mode_single_file)
                        }
                        Text(text = modeLabel, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                        Icon(imageVector = Icons.Rounded.ExpandMore, contentDescription = null, tint = accents.teal, modifier = Modifier.size(24.dp).rotate(if (showModeMenu) 180f else 0f))
                    }
                    DropdownMenu(expanded = showModeMenu, onDismissRequest = { showModeMenu = false }, modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
                        CompareMode.entries.forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(when(mode) { CompareMode.COMPARE -> stringResource(R.string.compare_mode_two_files); CompareMode.DOUBLE_CHECK -> stringResource(R.string.compare_mode_double_file); CompareMode.SINGLE_CHECK -> stringResource(R.string.compare_mode_single_file) }) },
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showModeMenu = false; scope.launch { listState.scrollToItem(0); vm.updateCompareMode(mode) }
                                },
                                leadingIcon = { if (vm.compareMode == mode) Icon(Icons.Rounded.Check, null, tint = if (isDark) accents.teal else accents.accentDark) }
                            )
                        }
                    }
                }
            }

            item(key = "backups") {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                    Text(text = stringResource(R.string.compare_step_upload), fontFamily = MonoFamily, fontSize = 11.sp, color = accents.textMuted, modifier = Modifier.padding(bottom = 12.dp))
                    val twoCardsFraction by animateFloatAsState(targetValue = if (vm.compareMode != CompareMode.SINGLE_CHECK) 1f else 0f, animationSpec = tween(400), label = "twoCardsFraction")
                    Row(
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(12.dp * twoCardsFraction)
                    ) {
                        UploadCard(
                            label = if (vm.compareMode != CompareMode.SINGLE_CHECK) stringResource(R.string.compare_backup_a) else stringResource(R.string.converter_backup_label),
                            icon = Icons.Rounded.Description, fileName = vm.nameA, hint = "backup.json / .ibackup",
                            onClick = {
                                if (vm.nameA != null) Toast.makeText(context, context.getString(R.string.error_file_already_loaded), Toast.LENGTH_SHORT).show()
                                else {
                                    if (useCustomPicker) onRequestInternalPicker(PickerTarget.BACKUP_A) else pickA.launch("*/*")
                                }
                            },
                            onClear = if (vm.nameA != null) { { vm.updateNameA(null); vm.updateFlatA(null); vm.updateErrorA(null); vm.updateCompareResult(null) } } else null,
                            error = vm.errorA, modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                        if (twoCardsFraction > 0.01f) {
                            UploadCard(
                                label = stringResource(R.string.compare_backup_b), icon = Icons.Rounded.Description, fileName = vm.nameB, hint = "backup.json / .ibackup",
                                onClick = {
                                    if (vm.nameB != null) Toast.makeText(context, context.getString(R.string.error_file_already_loaded), Toast.LENGTH_SHORT).show()
                                    else {
                                        if (useCustomPicker) onRequestInternalPicker(PickerTarget.BACKUP_B) else pickB.launch("*/*")
                                    }
                                },
                                onClear = if (vm.nameB != null) { { vm.updateNameB(null); vm.updateFlatB(null); vm.updateErrorB(null); vm.updateCompareResult(null) } } else null,
                                error = if (vm.compareMode == CompareMode.SINGLE_CHECK) null else vm.errorB,
                                modifier = Modifier.weight(twoCardsFraction).fillMaxHeight().graphicsLayer { alpha = twoCardsFraction }
                            )
                        }
                    }
                }
            }

            item(key = "mapping") {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                    UploadCard(
                        label = stringResource(R.string.compare_mapping_label), icon = Icons.AutoMirrored.Rounded.Label,
                        fileName = if (vm.mapper != null) { stringResource(if (vm.isDefaultMapper) R.string.compare_mapping_default_active else R.string.compare_mapping_active, vm.mapper!!.size()) } else null,
                        hint = stringResource(R.string.compare_mapping_hint),
                        onClick = {
                            if (vm.mapper != null) Toast.makeText(context, context.getString(R.string.error_file_already_loaded), Toast.LENGTH_SHORT).show()
                            else {
                                if (useCustomPicker) onRequestInternalPicker(PickerTarget.MAPPING) else pickMap.launch("application/json")
                            }
                        },
                        onClear = if (vm.mapper != null) { { vm.updateMapper(null) } } else null,
                        onRestore = if (vm.mapper == null) { { vm.updateMapper(FlagMapperProvider.get(), isDefault = true) } } else null,
                        modifier = Modifier.fillMaxWidth()
                    )
                    error?.let { Text(it, color = accents.danger, fontSize = 12.5.sp, modifier = Modifier.padding(top = 10.dp).clip(RoundedCornerShape(10.dp)).background(accents.amberDim).padding(10.dp)) }

                    // Intelligent Update Button Logic
                    if (vm.flatA == null && vm.flatB == null) {
                        val isUpdating = vm.isUpdatingMapping || vm.isCheckingUpdate

                        if (vm.mappingUpdateAvailable && vm.autoCheckMappingUpdates) {
                            val accent = accents.amber
                            val btnText = if (vm.isUpdatingMapping) {
                                stringResource(R.string.settings_updating_mapping)
                            } else {
                                stringResource(R.string.compare_update_available_btn)
                            }

                            Surface(
                                onClick = {
                                    if (!isUpdating && vm.mappingUpdateAvailable) {
                                        vm.showUpdateDialog = true
                                    }
                                },
                                color = accent.copy(alpha = 0.12f),
                                contentColor = accent,
                                shape = RoundedCornerShape(16.dp),
                                border = androidx.compose.foundation.BorderStroke(2.dp, accent.copy(alpha = 0.4f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    val infiniteTransition = rememberInfiniteTransition(label = "updateRotation")
                                    val rotation by infiniteTransition.animateFloat(
                                        initialValue = 0f,
                                        targetValue = 360f,
                                        animationSpec = infiniteRepeatable(
                                            animation = tween(1000, easing = LinearEasing),
                                            repeatMode = RepeatMode.Restart
                                        ),
                                        label = "rotation"
                                    )

                                    Icon(
                                        imageVector = if (isUpdating) Icons.Rounded.Sync else if (vm.mappingUpdateAvailable) Icons.Rounded.CloudSync else Icons.Rounded.CloudDownload,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(20.dp)
                                            .then(if (isUpdating) Modifier.rotate(rotation) else Modifier)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        text = btnText,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.sp,
                                        letterSpacing = 0.3.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (vm.compareMode == CompareMode.COMPARE && vm.compareResult != null) {
                item(key = "res_step") { Text(text = stringResource(R.string.compare_step_results), fontFamily = MonoFamily, fontSize = 11.sp, color = accents.textMuted, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) }
                item(key = "res_stats") { Box(modifier = Modifier.padding(horizontal = 20.dp)) { StatsRow(vm.compareResult!!) } }
                if (vm.deletedFlagsA.isNotEmpty() || vm.deletedFlagsB.isNotEmpty() || vm.editedFlagsA.isNotEmpty() || vm.editedFlagsB.isNotEmpty()) {
                    item(key = "res_edit_stats") {
                        Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (vm.deletedFlagsA.isNotEmpty() || vm.deletedFlagsB.isNotEmpty()) {
                                    DeletedStatsRow(deletedA = vm.deletedFlagsA, deletedB = vm.deletedFlagsB, onClickA = { deletedSource = 'A'; showDeletedFlagsSheet = true }, onClickB = { deletedSource = 'B'; showDeletedFlagsSheet = true })
                                }
                                if (vm.editedFlagsA.isNotEmpty() || vm.editedFlagsB.isNotEmpty()) {
                                    EditedStatsRow(editedCountA = vm.editedFlagsA.size, editedCountB = vm.editedFlagsB.size, onClickA = { editingFlagSource = 'A'; showEditedFlagsSheet = true }, onClickB = { editingFlagSource = 'B'; showEditedFlagsSheet = true })
                                }
                            }
                        }
                    }
                }
                if (vm.movedFlagsA.isNotEmpty() || vm.movedFlagsB.isNotEmpty()) {
                    item(key = "res_move") { Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) { MovementStatsRow(movedToA = vm.movedFlagsA, movedToB = vm.movedFlagsB, onClickA = { movedSource = 'A'; showMovedFlagsSheet = true }, onClickB = { movedSource = 'B'; showMovedFlagsSheet = true }) } }
                }
                item(key = "res_spacer") { Spacer(modifier = Modifier.height(12.dp)) }
                item(key = "header_only_a") { BulkActionHeader(title = stringResource(R.string.compare_section_only_a), count = vm.filteredA.size, isExpanded = vm.onlyAExpanded, onExpandClick = { vm.onlyAExpanded = !vm.onlyAExpanded }, accentColor = accents.teal, subtitle = stringResource(R.string.compare_section_only_a_desc), bulkActionText = if (vm.filteredA.isNotEmpty()) stringResource(R.string.compare_action_copy_all_to_b) else null, onBulkActionClick = { vm.copyFlagsToB(vm.filteredA) }) }
                if (vm.onlyAExpanded) {
                    item(key = "search_a") {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            FlagSearchBar(query = vm.searchA, onQueryChange = { vm.updateSearchA(it) }, accentColor = accents.teal, ghostTerm = vm.currentGhostTerm)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                                SearchFilterRow(currentScope = vm.searchScopeA, onScopeChange = { vm.updateSearchScopeA(it) }, accentColor = accents.teal, modifier = Modifier.weight(1f))
                                groupingToggle(vm.isGroupingA, { vm.toggleGroupingA() }, accents.teal)
                            }
                        }
                    }
                    if (vm.isGroupingA) {
                        val grouped = vm.groupedOnlyA
                        grouped.forEach { (category, flags) ->
                            item(key = "cat_a_$category") {
                                CategoryHeader(
                                    title = category,
                                    count = flags.size,
                                    isExpanded = expandedCategories["a_$category"] == true,
                                    onClick = { expandedCategories["a_$category"] = !(expandedCategories["a_$category"] ?: false) },
                                    accentColor = accents.teal,
                                    bulkActionText = stringResource(R.string.compare_action_copy_all_to_b),
                                    onBulkActionClick = { vm.copyFlagsToB(flags) }
                                )
                            }
                            if (expandedCategories["a_$category"] == true) {
                                items(flags, key = { "a_${it.path}" }) { flag ->
                                    Column(modifier = Modifier
                                        .padding(start = 20.dp, end = 16.dp, top = 4.dp, bottom = 4.dp)
                                        .drawBehind {
                                            drawLine(
                                                color = accents.teal.copy(alpha = 0.4f),
                                                start = Offset(4.dp.toPx(), 0f),
                                                end = Offset(4.dp.toPx(), size.height),
                                                strokeWidth = 2.dp.toPx()
                                            )
                                        }
                                        .padding(start = 12.dp)
                                    ) {
                                        ResultCard(
                                            path = flag.path,
                                            rawValue = flag.value,
                                            pushAccent = accents.teal,
                                            mapper = vm.mapper,
                                            highlightQuery = vm.searchA,
                                            highlightScope = vm.searchScopeA,
                                            backgroundColor = MaterialTheme.colorScheme.surface,
                                            onPush = { val newB = (vm.flatB ?: emptyMap()).toMutableMap(); newB[flag.path] = flag.value; vm.updateFlatB(newB); vm.addMovedFlagB(flag, null, false) },
                                            onEdit = {
                                                editingFlagPath = flag.path
                                                editingFlagDisplayName = vm.mapper?.getDisplayName(flag.path) ?: flag.path
                                                editingFlagValue = flag.value
                                                editingFlagOriginalValue = vm.originalValuesA[flag.path]
                                                editingFlagSource = 'A'
                                                showEditDialog = true
                                            },
                                            isEdited = vm.editedFlagsA.contains(flag.path)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        items(vm.filteredA, key = { "a_${it.path}" }) { flag ->
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                                ResultCard(
                                    path = flag.path,
                                    rawValue = flag.value,
                                    pushAccent = accents.teal,
                                    mapper = vm.mapper,
                                    highlightQuery = vm.searchA,
                                    highlightScope = vm.searchScopeA,
                                    onPush = { val newB = (vm.flatB ?: emptyMap()).toMutableMap(); newB[flag.path] = flag.value; vm.updateFlatB(newB); vm.addMovedFlagB(flag, null, false) },
                                    onEdit = {
                                        editingFlagPath = flag.path
                                        editingFlagDisplayName = vm.mapper?.getDisplayName(flag.path) ?: flag.path
                                        editingFlagValue = flag.value
                                        editingFlagOriginalValue = vm.originalValuesA[flag.path]
                                        editingFlagSource = 'A'
                                        showEditDialog = true
                                    },
                                    isEdited = vm.editedFlagsA.contains(flag.path)
                                )
                            }
                        }
                    }
                }
                item(key = "header_only_b") { BulkActionHeader(title = stringResource(R.string.compare_section_only_b), count = vm.filteredB.size, isExpanded = vm.onlyBExpanded, onExpandClick = { vm.onlyBExpanded = !vm.onlyBExpanded }, accentColor = accents.amber, subtitle = stringResource(R.string.compare_section_only_b_desc), bulkActionText = if (vm.filteredB.isNotEmpty()) stringResource(R.string.compare_action_copy_all_to_a) else null, onBulkActionClick = { vm.copyFlagsToA(vm.filteredB) }) }
                if (vm.onlyBExpanded) {
                    item(key = "search_b") {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            FlagSearchBar(query = vm.searchB, onQueryChange = { vm.updateSearchB(it) }, accentColor = accents.amber, ghostTerm = vm.currentGhostTerm)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                                SearchFilterRow(currentScope = vm.searchScopeB, onScopeChange = { vm.updateSearchScopeB(it) }, accentColor = accents.amber, modifier = Modifier.weight(1f))
                                groupingToggle(vm.isGroupingB, { vm.toggleGroupingB() }, accents.amber)
                            }
                        }
                    }
                    if (vm.isGroupingB) {
                        val grouped = vm.groupedOnlyB
                        grouped.forEach { (category, flags) ->
                            item(key = "cat_b_$category") {
                                CategoryHeader(
                                    title = category,
                                    count = flags.size,
                                    isExpanded = expandedCategories["b_$category"] == true,
                                    onClick = { expandedCategories["b_$category"] = !(expandedCategories["b_$category"] ?: false) },
                                    accentColor = accents.amber,
                                    bulkActionText = stringResource(R.string.compare_action_copy_all_to_a),
                                    onBulkActionClick = { vm.copyFlagsToA(flags) }
                                )
                            }
                            if (expandedCategories["b_$category"] == true) {
                                items(flags, key = { "b_${it.path}" }) { flag ->
                                    Column(modifier = Modifier
                                        .padding(start = 20.dp, end = 16.dp, top = 4.dp, bottom = 4.dp)
                                        .drawBehind {
                                            drawLine(
                                                color = accents.amber.copy(alpha = 0.4f),
                                                start = Offset(4.dp.toPx(), 0f),
                                                end = Offset(4.dp.toPx(), size.height),
                                                strokeWidth = 2.dp.toPx()
                                            )
                                        }
                                        .padding(start = 12.dp)
                                    ) {
                                        ResultCard(
                                            path = flag.path,
                                            rawValue = flag.value,
                                            pushAccent = accents.amber,
                                            mapper = vm.mapper,
                                            highlightQuery = vm.searchB,
                                            highlightScope = vm.searchScopeB,
                                            backgroundColor = MaterialTheme.colorScheme.surface,
                                            onPush = { val newA = (vm.flatA ?: emptyMap()).toMutableMap(); newA[flag.path] = flag.value; vm.updateFlatA(newA); vm.addMovedFlagA(flag, null, false) },
                                            onEdit = {
                                                editingFlagPath = flag.path
                                                editingFlagDisplayName = vm.mapper?.getDisplayName(flag.path) ?: flag.path
                                                editingFlagValue = flag.value
                                                editingFlagOriginalValue = vm.originalValuesB[flag.path]
                                                editingFlagSource = 'B'
                                                showEditDialog = true
                                            },
                                            isEdited = vm.editedFlagsB.contains(flag.path)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        items(vm.filteredB, key = { "b_${it.path}" }) { flag ->
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                                ResultCard(
                                    path = flag.path,
                                    rawValue = flag.value,
                                    pushAccent = accents.amber,
                                    mapper = vm.mapper,
                                    highlightQuery = vm.searchB,
                                    highlightScope = vm.searchScopeB,
                                    onPush = { val newA = (vm.flatA ?: emptyMap()).toMutableMap(); newA[flag.path] = flag.value; vm.updateFlatA(newA); vm.addMovedFlagA(flag, null, false) },
                                    onEdit = {
                                        editingFlagPath = flag.path
                                        editingFlagDisplayName = vm.mapper?.getDisplayName(flag.path) ?: flag.path
                                        editingFlagValue = flag.value
                                        editingFlagOriginalValue = vm.originalValuesB[flag.path]
                                        editingFlagSource = 'B'
                                        showEditDialog = true
                                    },
                                    isEdited = vm.editedFlagsB.contains(flag.path)
                                )
                            }
                        }
                    }
                }
                item(key = "header_diffs") { BulkActionHeader(title = stringResource(R.string.compare_section_diffs), count = vm.compareResult!!.diffs.size, isExpanded = vm.diffsExpanded, onExpandClick = { vm.diffsExpanded = !vm.diffsExpanded }, accentColor = accents.violet) }
                if (vm.diffsExpanded) {
                    item(key = "search_diffs") {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            FlagSearchBar(query = vm.searchDiffs, onQueryChange = { vm.updateSearchDiffs(it) }, accentColor = accents.violet, ghostTerm = vm.currentGhostTerm)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                                SearchFilterRow(currentScope = vm.searchScopeA, onScopeChange = { vm.updateSearchScopeA(it) }, accentColor = accents.violet, modifier = Modifier.weight(1f))
                                groupingToggle(vm.isGroupingDiffs, { vm.toggleGroupingDiffs() }, accents.violet)
                            }
                        }
                    }
                    if (vm.isGroupingDiffs) {
                        val grouped = vm.groupedDiffs
                        grouped.forEach { (category, flags) ->
                            item(key = "cat_diff_$category") {
                                CategoryHeader(
                                    title = category,
                                    count = flags.size,
                                    isExpanded = expandedCategories["diff_$category"] == true,
                                    onClick = { expandedCategories["diff_$category"] = !(expandedCategories["diff_$category"] ?: false) },
                                    accentColor = accents.violet,
                                    bulkActionText = stringResource(R.string.compare_action_copy_all_to_b),
                                    bulkActionColor = accents.amber,
                                    onBulkActionClick = {
                                        val flagsToB = flags.map { FlatFlag(it.path, it.valueA) }
                                        vm.copyFlagsToB(flagsToB)
                                    },
                                    secondaryBulkActionText = stringResource(R.string.compare_action_copy_all_to_a),
                                    secondaryBulkActionColor = accents.teal,
                                    onSecondaryBulkActionClick = {
                                        val flagsToA = flags.map { FlatFlag(it.path, it.valueB) }
                                        vm.copyFlagsToA(flagsToA)
                                    }
                                )
                            }
                            if (expandedCategories["diff_$category"] == true) {
                                items(flags, key = { "diff_${it.path}" }) { diff ->
                                    Column(modifier = Modifier
                                        .padding(start = 20.dp, end = 16.dp, top = 4.dp, bottom = 4.dp)
                                        .drawBehind {
                                            drawLine(
                                                color = accents.violet.copy(alpha = 0.4f),
                                                start = Offset(4.dp.toPx(), 0f),
                                                end = Offset(4.dp.toPx(), size.height),
                                                strokeWidth = 2.dp.toPx()
                                            )
                                        }
                                        .padding(start = 12.dp)
                                    ) {
                                        DiffResultCard(
                                            path = diff.path,
                                            valueA = diff.valueA,
                                            valueB = diff.valueB,
                                            mapper = vm.mapper,
                                            highlightQuery = vm.searchDiffs,
                                            highlightScope = vm.searchScopeA,
                                            backgroundColor = MaterialTheme.colorScheme.surface,
                                            onPushA = { val newB = (vm.flatB ?: emptyMap()).toMutableMap(); val original = newB[diff.path]; newB[diff.path] = diff.valueA; vm.updateFlatB(newB); vm.addMovedFlagB(FlatFlag(diff.path, diff.valueA), original, true) },
                                            onPushB = { val newA = (vm.flatA ?: emptyMap()).toMutableMap(); val original = newA[diff.path]; newA[diff.path] = diff.valueB; vm.updateFlatA(newA); vm.addMovedFlagA(FlatFlag(diff.path, diff.valueB), original, true) },
                                            onEditA = {
                                                editingFlagPath = diff.path
                                                editingFlagDisplayName = vm.mapper?.getDisplayName(diff.path) ?: diff.path
                                                editingFlagValue = diff.valueA
                                                editingFlagOriginalValue = vm.originalValuesA[diff.path]
                                                editingFlagSource = 'A'
                                                showEditDialog = true
                                            },
                                            onEditB = {
                                                editingFlagPath = diff.path
                                                editingFlagDisplayName = vm.mapper?.getDisplayName(diff.path) ?: diff.path
                                                editingFlagValue = diff.valueB
                                                editingFlagOriginalValue = vm.originalValuesB[diff.path]
                                                editingFlagSource = 'B'
                                                showEditDialog = true
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    else {
                        items(vm.filteredDiffs, key = { "diff_${it.path}" }) { diff ->
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                                DiffResultCard(
                                    path = diff.path,
                                    valueA = diff.valueA,
                                    valueB = diff.valueB,
                                    mapper = vm.mapper,
                                    highlightQuery = vm.searchDiffs,
                                    highlightScope = vm.searchScopeA,
                                    onPushA = { val newB = (vm.flatB ?: emptyMap()).toMutableMap(); val original = newB[diff.path]; newB[diff.path] = diff.valueA; vm.updateFlatB(newB); vm.addMovedFlagB(FlatFlag(diff.path, diff.valueA), original, true) },
                                    onPushB = { val newA = (vm.flatA ?: emptyMap()).toMutableMap(); val original = newA[diff.path]; newA[diff.path] = diff.valueB; vm.updateFlatA(newA); vm.addMovedFlagA(FlatFlag(diff.path, diff.valueB), original, true) },
                                    onEditA = {
                                        editingFlagPath = diff.path
                                        editingFlagDisplayName = vm.mapper?.getDisplayName(diff.path) ?: diff.path
                                        editingFlagValue = diff.valueA
                                        editingFlagOriginalValue = vm.originalValuesA[diff.path]
                                        editingFlagSource = 'A'
                                        showEditDialog = true
                                    },
                                    onEditB = {
                                        editingFlagPath = diff.path
                                        editingFlagDisplayName = vm.mapper?.getDisplayName(diff.path) ?: diff.path
                                        editingFlagValue = diff.valueB
                                        editingFlagOriginalValue = vm.originalValuesB[diff.path]
                                        editingFlagSource = 'B'
                                        showEditDialog = true
                                    }
                                )
                            }
                        }
                    }
                }
                if (vm.movedFlagsA.isNotEmpty() || vm.movedFlagsB.isNotEmpty() || vm.editedFlagsA.isNotEmpty() || vm.editedFlagsB.isNotEmpty()) {
                    item(key = "downloads") {
                        Row(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (vm.movedFlagsA.isNotEmpty() || vm.editedFlagsA.isNotEmpty()) PrimaryButton(text = stringResource(R.string.compare_download_a), onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                val fileName = if (autoRenameOverrides) "mc_overrides.json" else (vm.nameA ?: "a").substringBeforeLast('.') + ".json"
                                if (useCustomPicker) {
                                    val data = JsonDiff.unflattenAndSerializeCompact(vm.flatA ?: emptyMap())
                                    onSaveFile(data, fileName, PickerTarget.BACKUP_A)
                                } else {
                                    saveA.launch(fileName)
                                }
                            }, accent = accents.teal, modifier = Modifier.weight(1f))
                            if (vm.movedFlagsB.isNotEmpty() || vm.editedFlagsB.isNotEmpty()) PrimaryButton(text = stringResource(R.string.compare_download_b), onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                val fileName = if (autoRenameOverrides) "mc_overrides.json" else (vm.nameB ?: "b").substringBeforeLast('.') + ".json"
                                if (useCustomPicker) {
                                    val data = JsonDiff.unflattenAndSerializeCompact(vm.flatB ?: emptyMap())
                                    onSaveFile(data, fileName, PickerTarget.BACKUP_B)
                                } else {
                                    saveB.launch(fileName)
                                }
                            }, accent = accents.amber, modifier = Modifier.weight(1f))
                        }
                    }
                }
            } else if (vm.compareMode == CompareMode.SINGLE_CHECK && vm.flatA != null) {
                if (vm.deletedFlagsA.isNotEmpty() || vm.editedFlagsA.isNotEmpty()) {
                    item(key = "del_stats_single") {
                        Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (vm.deletedFlagsA.isNotEmpty()) {
                                    DeletedStatsRow(deletedA = vm.deletedFlagsA, deletedB = emptyList(), onClickA = { deletedSource = 'A'; showDeletedFlagsSheet = true }, onClickB = {})
                                }
                                if (vm.editedFlagsA.isNotEmpty()) {
                                    EditedStatsRow(editedCountA = vm.editedFlagsA.size, editedCountB = 0, onClickA = { editingFlagSource = 'A'; showEditedFlagsSheet = true }, onClickB = {})
                                }
                            }
                        }
                    }
                }
                item(key = "header_single") { BulkActionHeader(title = stringResource(R.string.compare_all_flags), count = vm.filteredSingleA.size, isExpanded = listAExpanded, onExpandClick = { listAExpanded = !listAExpanded }, accentColor = accents.teal) }
                if (listAExpanded) {
                    item(key = "search_single") {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            FlagSearchBar(query = vm.searchSingleA, onQueryChange = { vm.updateSearchSingleA(it) }, accentColor = accents.teal, ghostTerm = vm.currentGhostTerm)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                                SearchFilterRow(currentScope = vm.searchScopeA, onScopeChange = { vm.updateSearchScopeA(it) }, accentColor = accents.teal, modifier = Modifier.weight(1f))
                                groupingToggle(vm.isGroupingSingleA, { vm.toggleGroupingSingleA() }, accents.teal)
                            }
                        }
                    }
                    if (vm.isGroupingSingleA) {
                        val grouped = vm.groupedSingleA
                        grouped.forEach { (category, entries) ->
                            item(key = "cat_single_a_$category") {
                                CategoryHeader(title = category, count = entries.size, isExpanded = expandedCategories["single_a_$category"] == true, onClick = { expandedCategories["single_a_$category"] = !(expandedCategories["single_a_$category"] ?: false) }, accentColor = accents.teal)
                            }
                            if (expandedCategories["single_a_$category"] == true) {
                                items(entries, key = { "single_${it.key}" }) { (path, value) ->
                                    Column(modifier = Modifier
                                        .padding(start = 20.dp, end = 16.dp, top = 4.dp, bottom = 4.dp)
                                        .drawBehind {
                                            drawLine(
                                                color = accents.teal.copy(alpha = 0.4f),
                                                start = Offset(4.dp.toPx(), 0f),
                                                end = Offset(4.dp.toPx(), size.height),
                                                strokeWidth = 2.dp.toPx()
                                            )
                                        }
                                        .padding(start = 12.dp)
                                    ) {
                                        ResultCard(
                                            path = path,
                                            rawValue = value,
                                            mapper = vm.mapper,
                                            highlightQuery = vm.searchSingleA,
                                            highlightScope = vm.searchScopeA,
                                            backgroundColor = MaterialTheme.colorScheme.surface,
                                            onDelete = { if (confirmDeletion) { pendingDeletePath = path; pendingDeleteSource = 'A'; showDeleteDialog = true } else { vm.addDeletedFlagA(FlatFlag(path, value)); val newA = (vm.flatA ?: emptyMap()).toMutableMap() ; newA.remove(path); vm.updateFlatA(newA) } },
                                            onEdit = {
                                                editingFlagPath = path
                                                editingFlagDisplayName = vm.mapper?.getDisplayName(path) ?: path
                                                editingFlagValue = value
                                                editingFlagOriginalValue = vm.originalValuesA[path]
                                                editingFlagSource = 'A'
                                                showEditDialog = true
                                            },
                                            isEdited = vm.editedFlagsA.contains(path)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        items(vm.filteredSingleA, key = { "single_${it.key}" }) { (path, value) ->
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                                ResultCard(
                                    path = path,
                                    rawValue = value,
                                    mapper = vm.mapper,
                                    highlightQuery = vm.searchSingleA,
                                    highlightScope = vm.searchScopeA,
                                    onDelete = { if (confirmDeletion) { pendingDeletePath = path; pendingDeleteSource = 'A'; showDeleteDialog = true } else { vm.addDeletedFlagA(FlatFlag(path, value)); val newA = (vm.flatA ?: emptyMap()).toMutableMap(); newA.remove(path); vm.updateFlatA(newA) } },
                                    onEdit = {
                                        editingFlagPath = path
                                        editingFlagDisplayName = vm.mapper?.getDisplayName(path) ?: path
                                        editingFlagValue = value
                                        editingFlagOriginalValue = vm.originalValuesA[path]
                                        editingFlagSource = 'A'
                                        showEditDialog = true
                                    },
                                    isEdited = vm.editedFlagsA.contains(path)
                                )
                            }
                        }
                    }
                }
                if (vm.deletedFlagsA.isNotEmpty() || vm.movedFlagsA.isNotEmpty() || vm.editedFlagsA.isNotEmpty()) {
                    item(key = "dl_single") {
                        Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                            PrimaryButton(text = stringResource(R.string.compare_download_a), onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                val fileName = if (autoRenameOverrides) "mc_overrides.json" else (vm.nameA ?: "a").substringBeforeLast('.') + ".json"
                                if (useCustomPicker) {
                                    val data = JsonDiff.unflattenAndSerializeCompact(vm.flatA ?: emptyMap())
                                    onSaveFile(data, fileName, PickerTarget.BACKUP_A)
                                } else {
                                    saveA.launch(fileName)
                                }
                            }, accent = accents.teal, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            } else if (vm.compareMode == CompareMode.DOUBLE_CHECK) {
                if (vm.deletedFlagsA.isNotEmpty() || vm.deletedFlagsB.isNotEmpty() || vm.editedFlagsA.isNotEmpty() || vm.editedFlagsB.isNotEmpty()) {
                    item(key = "del_stats_double") {
                        Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (vm.deletedFlagsA.isNotEmpty() || vm.deletedFlagsB.isNotEmpty()) {
                                    DeletedStatsRow(deletedA = vm.deletedFlagsA, deletedB = vm.deletedFlagsB, onClickA = { deletedSource = 'A'; showDeletedFlagsSheet = true }, onClickB = { deletedSource = 'B'; showDeletedFlagsSheet = true })
                                }
                                if (vm.editedFlagsA.isNotEmpty() || vm.editedFlagsB.isNotEmpty()) {
                                    EditedStatsRow(editedCountA = vm.editedFlagsA.size, editedCountB = vm.editedFlagsB.size, onClickA = { editingFlagSource = 'A'; showEditedFlagsSheet = true }, onClickB = { editingFlagSource = 'B'; showEditedFlagsSheet = true })
                                }
                            }
                        }
                    }
                }
                if (vm.flatA != null) {
                    item(key = "header_da") { BulkActionHeader(title = stringResource(R.string.compare_all_flags) + " (A)", count = vm.filteredSingleA.size, isExpanded = listAExpanded, onExpandClick = { listAExpanded = !listAExpanded }, accentColor = accents.teal) }
                    if (listAExpanded) {
                        item(key = "search_da") {
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                                FlagSearchBar(query = vm.searchSingleA, onQueryChange = { vm.updateSearchSingleA(it) }, accentColor = accents.teal, ghostTerm = vm.currentGhostTerm)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                                    SearchFilterRow(currentScope = vm.searchScopeA, onScopeChange = { vm.updateSearchScopeA(it) }, accentColor = accents.teal, modifier = Modifier.weight(1f))
                                    groupingToggle(vm.isGroupingSingleA, { vm.toggleGroupingSingleA() }, accents.teal)
                                }
                            }
                        }
                        if (vm.isGroupingSingleA) {
                            val grouped = vm.groupedSingleA
                            grouped.forEach { (category, entries) ->
                                item(key = "cat_da_$category") {
                                    CategoryHeader(
                                        title = category,
                                        count = entries.size,
                                        isExpanded = expandedCategories["da_$category"] == true,
                                        onClick = { expandedCategories["da_$category"] = !(expandedCategories["da_$category"] ?: false) },
                                        accentColor = accents.teal
                                    )
                                }
                                if (expandedCategories["da_$category"] == true) {
                                    items(entries, key = { "da_${it.key}" }) { (path, value) ->
                                        Column(modifier = Modifier
                                            .padding(start = 20.dp, end = 16.dp, top = 4.dp, bottom = 4.dp)
                                            .drawBehind {
                                                drawLine(
                                                    color = accents.teal.copy(alpha = 0.4f),
                                                    start = Offset(4.dp.toPx(), 0f),
                                                    end = Offset(4.dp.toPx(), size.height),
                                                    strokeWidth = 2.dp.toPx()
                                                )
                                            }
                                            .padding(start = 12.dp)
                                        ) {
                                            ResultCard(
                                                path = path,
                                                rawValue = value,
                                                mapper = vm.mapper,
                                                pushAccent = accents.teal,
                                                highlightQuery = vm.searchSingleA,
                                                highlightScope = vm.searchScopeA,
                                                backgroundColor = MaterialTheme.colorScheme.surface,
                                                onDelete = { if (confirmDeletion) { pendingDeletePath = path; pendingDeleteSource = 'A'; showDeleteDialog = true } else { vm.addDeletedFlagA(FlatFlag(path, value)); val newA = (vm.flatA ?: emptyMap()).toMutableMap(); newA.remove(path) ; vm.updateFlatA(newA) } },
                                                onEdit = {
                                                    editingFlagPath = path
                                                    editingFlagDisplayName = vm.mapper?.getDisplayName(path) ?: path
                                                    editingFlagValue = value
                                                    editingFlagOriginalValue = vm.originalValuesA[path]
                                                    editingFlagSource = 'A'
                                                    showEditDialog = true
                                                },
                                                isEdited = vm.editedFlagsA.contains(path)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        else {
                            items(vm.filteredSingleA, key = { "da_${it.key}" }) { (path, value) ->
                                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                                    ResultCard(
                                        path = path,
                                        rawValue = value,
                                        mapper = vm.mapper,
                                        pushAccent = accents.teal,
                                        highlightQuery = vm.searchSingleA,
                                        highlightScope = vm.searchScopeA,
                                        onDelete = { if (confirmDeletion) { pendingDeletePath = path; pendingDeleteSource = 'A'; showDeleteDialog = true } else { vm.addDeletedFlagA(FlatFlag(path, value)); val newA = (vm.flatA ?: emptyMap()).toMutableMap(); newA.remove(path); vm.updateFlatA(newA) } },
                                        onEdit = {
                                            editingFlagPath = path
                                            editingFlagDisplayName = vm.mapper?.getDisplayName(path) ?: path
                                            editingFlagValue = value
                                            editingFlagOriginalValue = vm.originalValuesA[path]
                                            editingFlagSource = 'A'
                                            showEditDialog = true
                                        },
                                        isEdited = vm.editedFlagsA.contains(path)
                                    )
                                }
                            }
                        }
                    }
                }
                if (vm.flatB != null) {
                    item(key = "header_db") { BulkActionHeader(title = stringResource(R.string.compare_all_flags) + " (B)", count = vm.filteredSingleB.size, isExpanded = listBExpanded, onExpandClick = { listBExpanded = !listBExpanded }, accentColor = accents.amber) }
                    if (listBExpanded) {
                        item(key = "search_db") {
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                                FlagSearchBar(query = vm.searchSingleB, onQueryChange = { vm.updateSearchSingleB(it) }, accentColor = accents.amber, ghostTerm = vm.currentGhostTerm)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                                    SearchFilterRow(currentScope = vm.searchScopeB, onScopeChange = { vm.updateSearchScopeB(it) }, accentColor = accents.amber, modifier = Modifier.weight(1f))
                                    groupingToggle(vm.isGroupingSingleB, { vm.toggleGroupingSingleB() }, accents.amber)
                                }
                            }
                        }
                        if (vm.isGroupingSingleB) {
                            val grouped = vm.groupedSingleB
                            grouped.forEach { (category, entries) ->
                                item(key = "cat_db_$category") {
                                    CategoryHeader(
                                        title = category,
                                        count = entries.size,
                                        isExpanded = expandedCategories["db_$category"] == true,
                                        onClick = { expandedCategories["db_$category"] = !(expandedCategories["db_$category"] ?: false) },
                                        accentColor = accents.amber
                                    )
                                }
                                if (expandedCategories["db_$category"] == true) {
                                    items(entries, key = { "db_${it.key}" }) { (path, value) ->
                                        Column(modifier = Modifier
                                            .padding(start = 20.dp, end = 16.dp, top = 4.dp, bottom = 4.dp)
                                            .drawBehind {
                                                drawLine(
                                                    color = accents.amber.copy(alpha = 0.4f),
                                                    start = Offset(4.dp.toPx(), 0f),
                                                    end = Offset(4.dp.toPx(), size.height),
                                                    strokeWidth = 2.dp.toPx()
                                                )
                                            }
                                            .padding(start = 12.dp)
                                        ) {
                                            ResultCard(
                                                path = path,
                                                rawValue = value,
                                                mapper = vm.mapper,
                                                pushAccent = accents.amber,
                                                highlightQuery = vm.searchSingleB,
                                                highlightScope = vm.searchScopeB,
                                                backgroundColor = MaterialTheme.colorScheme.surface,
                                                onDelete = { if (confirmDeletion) { pendingDeletePath = path; pendingDeleteSource = 'B'; showDeleteDialog = true } else { vm.addDeletedFlagB(FlatFlag(path, value)); val newB = (vm.flatB ?: emptyMap()).toMutableMap(); newB.remove(path); vm.updateFlatB(newB) } },
                                                onEdit = {
                                                    editingFlagPath = path
                                                    editingFlagDisplayName = vm.mapper?.getDisplayName(path) ?: path
                                                    editingFlagValue = value
                                                    editingFlagOriginalValue = vm.originalValuesB[path]
                                                    editingFlagSource = 'B'
                                                    showEditDialog = true
                                                },
                                                isEdited = vm.editedFlagsB.contains(path)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        else {
                            items(vm.filteredSingleB, key = { "db_${it.key}" }) { (path, value) ->
                                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                                    ResultCard(
                                        path = path,
                                        rawValue = value,
                                        mapper = vm.mapper,
                                        pushAccent = accents.amber,
                                        highlightQuery = vm.searchSingleB,
                                        highlightScope = vm.searchScopeB,
                                        onDelete = { if (confirmDeletion) { pendingDeletePath = path; pendingDeleteSource = 'B'; showDeleteDialog = true } else { vm.addDeletedFlagB(FlatFlag(path, value)); val newB = (vm.flatB ?: emptyMap()).toMutableMap(); newB.remove(path); vm.updateFlatB(newB) } },
                                        onEdit = {
                                            editingFlagPath = path
                                            editingFlagDisplayName = vm.mapper?.getDisplayName(path) ?: path
                                            editingFlagValue = value
                                            editingFlagOriginalValue = vm.originalValuesB[path]
                                            editingFlagSource = 'B'
                                            showEditDialog = true
                                        },
                                        isEdited = vm.editedFlagsB.contains(path)
                                    )
                                }
                            }
                        }
                    }
                }
                if (isDebugMode && vm.similarity >= 0.5f && vm.flatA != null && vm.flatB != null) {
                    item(key = "changelog_btn") { ChangelogButton(onClick = { showChangelogDialog = true }, accent = accents.teal) }
                }
                if (vm.deletedFlagsA.isNotEmpty() || vm.deletedFlagsB.isNotEmpty() || vm.movedFlagsA.isNotEmpty() || vm.movedFlagsB.isNotEmpty() || vm.editedFlagsA.isNotEmpty() || vm.editedFlagsB.isNotEmpty()) {
                    item(key = "dl_double") {
                        Row(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (vm.deletedFlagsA.isNotEmpty() || vm.movedFlagsA.isNotEmpty() || vm.editedFlagsA.isNotEmpty()) PrimaryButton(text = stringResource(R.string.compare_download_a), onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                val fileName = if (autoRenameOverrides) "mc_overrides.json" else (vm.nameA ?: "a").substringBeforeLast('.') + ".json"
                                if (useCustomPicker) {
                                    val data = JsonDiff.unflattenAndSerializeCompact(vm.flatA ?: emptyMap())
                                    onSaveFile(data, fileName, PickerTarget.BACKUP_A)
                                } else {
                                    saveA.launch(fileName)
                                }
                            }, accent = accents.teal, modifier = Modifier.weight(1f))
                            if (vm.deletedFlagsB.isNotEmpty() || vm.movedFlagsB.isNotEmpty() || vm.editedFlagsB.isNotEmpty()) PrimaryButton(text = stringResource(R.string.compare_download_b), onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                val fileName = if (autoRenameOverrides) "mc_overrides.json" else (vm.nameB ?: "b").substringBeforeLast('.') + ".json"
                                if (useCustomPicker) {
                                    val data = JsonDiff.unflattenAndSerializeCompact(vm.flatB ?: emptyMap())
                                    onSaveFile(data, fileName, PickerTarget.BACKUP_B)
                                } else {
                                    saveB.launch(fileName)
                                }
                            }, accent = accents.amber, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        VerticalScrollbar(state = listState, bottomPadding = 220.dp, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp))

        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false }, title = { Text(stringResource(R.string.delete_confirm_title)) },
                text = { Column { Text(stringResource(R.string.delete_confirm_message)); Spacer(modifier = Modifier.height(16.dp)); Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { dontAskAgainChecked = !dontAskAgainChecked }) { Checkbox(checked = dontAskAgainChecked, onCheckedChange = { dontAskAgainChecked = it }, colors = CheckboxDefaults.colors(checkedColor = accents.teal)); Text(text = stringResource(R.string.delete_dont_show_again), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp)) } } },
                confirmButton = { TextButton(onClick = performDelete) { Text(stringResource(R.string.delete_confirm_action), color = accents.danger, fontWeight = FontWeight.Bold) } },
                dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.delete_confirm_cancel), color = MaterialTheme.colorScheme.onSurfaceVariant) } },
                containerColor = MaterialTheme.colorScheme.surface, titleContentColor = MaterialTheme.colorScheme.onSurface, textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (showBulkDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showBulkDeleteDialog = false }, title = { Text(stringResource(R.string.delete_confirm_title)) },
                text = { Column { Text(stringResource(R.string.delete_confirm_message)); Spacer(modifier = Modifier.height(16.dp)); Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { dontAskAgainChecked = !dontAskAgainChecked }) { Checkbox(checked = dontAskAgainChecked, onCheckedChange = { dontAskAgainChecked = it }, colors = CheckboxDefaults.colors(checkedColor = accents.teal)); Text(text = stringResource(R.string.delete_dont_show_again), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp)) } } },
                confirmButton = { TextButton(onClick = performBulkDelete) { Text(stringResource(R.string.delete_confirm_action), color = accents.danger, fontWeight = FontWeight.Bold) } },
                dismissButton = { TextButton(onClick = { showBulkDeleteDialog = false }) { Text(stringResource(R.string.delete_confirm_cancel), color = MaterialTheme.colorScheme.onSurfaceVariant) } },
                containerColor = MaterialTheme.colorScheme.surface, titleContentColor = MaterialTheme.colorScheme.onSurface, textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (showBulkRestoreDialog) {
            AlertDialog(
                onDismissRequest = { showBulkRestoreDialog = false }, title = { Text(stringResource(R.string.delete_confirm_title)) },
                text = { Column { Text(stringResource(R.string.delete_confirm_message)); Spacer(modifier = Modifier.height(16.dp)); Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { dontAskAgainChecked = !dontAskAgainChecked }) { Checkbox(checked = dontAskAgainChecked, onCheckedChange = { dontAskAgainChecked = it }, colors = CheckboxDefaults.colors(checkedColor = accents.teal)); Text(text = stringResource(R.string.delete_dont_show_again), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp)) } } },
                confirmButton = { TextButton(onClick = performBulkRestore) { Text(stringResource(R.string.delete_confirm_action), color = accents.teal, fontWeight = FontWeight.Bold) } },
                dismissButton = { TextButton(onClick = { showBulkRestoreDialog = false }) { Text(stringResource(R.string.delete_confirm_cancel), color = MaterialTheme.colorScheme.onSurfaceVariant) } },
                containerColor = MaterialTheme.colorScheme.surface, titleContentColor = MaterialTheme.colorScheme.onSurface, textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (showMovedFlagsSheet) {
            val list = if (movedSource == 'A') vm.movedFlagsA else vm.movedFlagsB
            val grouped = if (movedSource == 'A') vm.groupedMovedA else vm.groupedMovedB
            val accent = if (movedSource == 'A') accents.teal else accents.amber
            ModalBottomSheet(onDismissRequest = { showMovedFlagsSheet = false }, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surface, dragHandle = { BottomSheetDefaults.DragHandle() }) {
                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth().padding(end = 16.dp)) {
                        Text(text = stringResource(R.string.compare_moved_title, if (movedSource == 'A') "A" else "B"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp))
                        groupingToggle(vm.isGroupingMoved, { vm.toggleGroupingMoved() }, accent)
                    }
                    if (list.isNotEmpty()) { TextButton(onClick = { if (confirmDeletion) showBulkDeleteDialog = true else { if (movedSource == 'A') vm.clearMovedFlagsA() else vm.clearMovedFlagsB() } }, modifier = Modifier.padding(horizontal = 16.dp), colors = ButtonDefaults.textButtonColors(contentColor = accents.danger)) { Icon(Icons.Rounded.SettingsBackupRestore, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.compare_action_remove_all), fontWeight = FontWeight.Bold) } }
                    
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 450.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (vm.isGroupingMoved) {
                            grouped.forEach { (category, flags) ->
                                item(key = "moved_cat_$category") {
                                    CategoryHeader(
                                        title = category,
                                        count = flags.size,
                                        isExpanded = expandedCategories["moved_$category"] == true,
                                        onClick = { expandedCategories["moved_$category"] = !(expandedCategories["moved_$category"] ?: false) },
                                        accentColor = accent,
                                        bulkActionText = stringResource(R.string.revert_category),
                                        onBulkActionClick = { 
                                            if (movedSource == 'A') vm.revertMovedCategoryA(flags) else vm.revertMovedCategoryB(flags)
                                        }
                                    )
                                }
                                if (expandedCategories["moved_$category"] == true) {
                                    items(flags, key = { "moved_${it.path}" }) { flag ->
                                        ResultCard(path = flag.path, rawValue = flag.pushedValue, mapper = vm.mapper, pushAccent = accent, deleteIcon = Icons.Rounded.SettingsBackupRestore, highlightQuery = if (movedSource == 'A') vm.searchA else vm.searchB, onDelete = { if (confirmDeletion) { pendingDeletePath = flag.path; pendingDeleteSource = movedSource; isFromMovedList = true; showDeleteDialog = true } else { if (movedSource == 'A') vm.removeMovedFlagA(flag.path) else vm.removeMovedFlagB(flag.path) } })
                                    }
                                }
                            }
                        } else {
                            items(list, key = { "moved_${it.path}" }) { flag ->
                                ResultCard(path = flag.path, rawValue = flag.pushedValue, mapper = vm.mapper, pushAccent = accent, deleteIcon = Icons.Rounded.SettingsBackupRestore, highlightQuery = if (movedSource == 'A') vm.searchA else vm.searchB, onDelete = { if (confirmDeletion) { pendingDeletePath = flag.path; pendingDeleteSource = movedSource; isFromMovedList = true; showDeleteDialog = true } else { if (movedSource == 'A') vm.removeMovedFlagA(flag.path) else vm.removeMovedFlagB(flag.path) } })
                            }
                        }
                    }
                }
            }
        }
        if (showDeletedFlagsSheet) {
            val list = if (deletedSource == 'A') vm.deletedFlagsA else vm.deletedFlagsB
            val grouped = if (deletedSource == 'A') vm.groupedDeletedA else vm.groupedDeletedB
            val accent = if (deletedSource == 'A') accents.teal else accents.amber
            ModalBottomSheet(onDismissRequest = { showDeletedFlagsSheet = false }, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surface, dragHandle = { BottomSheetDefaults.DragHandle() }) {
                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth().padding(end = 16.dp)) {
                        Text(text = stringResource(R.string.compare_deleted_title, if (deletedSource == 'A') "A" else "B"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp))
                        groupingToggle(vm.isGroupingDeleted, { vm.toggleGroupingDeleted() }, accent)
                    }
                    if (list.isNotEmpty()) { TextButton(onClick = { if (confirmDeletion) showBulkRestoreDialog = true else { if (deletedSource == 'A') vm.restoreAllDeletedFlagsA() else vm.restoreAllDeletedFlagsB() } }, modifier = Modifier.padding(horizontal = 16.dp), colors = ButtonDefaults.textButtonColors(contentColor = accents.teal)) { Icon(Icons.Rounded.SettingsBackupRestore, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.compare_action_restore_all), fontWeight = FontWeight.Bold) } }
                    
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 450.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (vm.isGroupingDeleted) {
                            grouped.forEach { (category, flags) ->
                                item(key = "del_cat_$category") {
                                    CategoryHeader(
                                        title = category,
                                        count = flags.size,
                                        isExpanded = expandedCategories["del_$category"] == true,
                                        onClick = { expandedCategories["del_$category"] = !(expandedCategories["del_$category"] ?: false) },
                                        accentColor = accent,
                                        bulkActionText = stringResource(R.string.revert_category),
                                        onBulkActionClick = {
                                            if (deletedSource == 'A') vm.restoreDeletedCategoryA(flags) else vm.restoreDeletedCategoryB(flags)
                                        }
                                    )
                                }
                                if (expandedCategories["del_$category"] == true) {
                                    items(flags, key = { "del_${it.path}" }) { flag ->
                                        ResultCard(path = flag.path, rawValue = flag.value, mapper = vm.mapper, pushAccent = accents.danger, deleteIcon = Icons.Rounded.SettingsBackupRestore, highlightQuery = if (deletedSource == 'A') vm.searchA else vm.searchB, onDelete = { if (deletedSource == 'A') vm.removeDeletedFlagA(flag.path) else vm.removeDeletedFlagB(flag.path) })
                                    }
                                }
                            }
                        } else {
                            items(list, key = { "del_${it.path}" }) { flag ->
                                ResultCard(path = flag.path, rawValue = flag.value, mapper = vm.mapper, pushAccent = accents.danger, deleteIcon = Icons.Rounded.SettingsBackupRestore, highlightQuery = if (deletedSource == 'A') vm.searchA else vm.searchB, onDelete = { if (deletedSource == 'A') vm.removeDeletedFlagA(flag.path) else vm.removeDeletedFlagB(flag.path) })
                            }
                        }
                    }
                }
            }
        }
        if (showEditedFlagsSheet) {
            val list = if (editingFlagSource == 'A') vm.getEditedFlagsSummaryA() else vm.getEditedFlagsSummaryB()
            val grouped = if (editingFlagSource == 'A') vm.groupedEditedA else vm.groupedEditedB
            val accent = if (editingFlagSource == 'A') accents.teal else accents.amber
            ModalBottomSheet(onDismissRequest = { showEditedFlagsSheet = false }, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surface, dragHandle = { BottomSheetDefaults.DragHandle() }) {
                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth().padding(end = 16.dp)) {
                        Text(
                            text = stringResource(R.string.edit_dialog_edited_title, if (editingFlagSource == 'A') "A" else "B"),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                        )
                        groupingToggle(vm.isGroupingEdited, { vm.toggleGroupingEdited() }, accent)
                    }

                    if (list.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                if (editingFlagSource == 'A') vm.restoreAllEditsA() else vm.restoreAllEditsB()
                            },
                            modifier = Modifier.padding(horizontal = 16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = accents.teal)
                        ) {
                            Icon(Icons.Rounded.SettingsBackupRestore, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.edit_dialog_restore_all), fontWeight = FontWeight.Bold)
                        }
                    }

                    if (list.isEmpty()) {
                        Text(
                            text = stringResource(R.string.edit_dialog_no_edits),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(20.dp),
                            color = accents.textMuted
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 450.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (vm.isGroupingEdited) {
                                grouped.forEach { (category, flags) ->
                                    item(key = "edit_cat_$category") {
                                        CategoryHeader(
                                            title = category,
                                            count = flags.size,
                                            isExpanded = expandedCategories["edit_$category"] == true,
                                            onClick = { expandedCategories["edit_$category"] = !(expandedCategories["edit_$category"] ?: false) },
                                            accentColor = accent,
                                            bulkActionText = stringResource(R.string.revert_category),
                                            onBulkActionClick = {
                                                if (editingFlagSource == 'A') vm.revertEditedCategoryA(flags) else vm.revertEditedCategoryB(flags)
                                            }
                                        )
                                    }
                                    if (expandedCategories["edit_$category"] == true) {
                                        items(flags, key = { "edit_${it.path}" }) { editedFlag ->
                                            EditedFlagSummaryCard(
                                                edited = editedFlag,
                                                mapper = vm.mapper,
                                                onRevert = {
                                                    if (editingFlagSource == 'A') vm.revertFlagEditA(editedFlag.path)
                                                    else vm.revertFlagEditB(editedFlag.path)
                                                }
                                            )
                                        }
                                    }
                                }
                            } else {
                                items(list, key = { "edit_${it.path}" }) { editedFlag ->
                                    EditedFlagSummaryCard(
                                        edited = editedFlag,
                                        mapper = vm.mapper,
                                        onRevert = {
                                            if (editingFlagSource == 'A') vm.revertFlagEditA(editedFlag.path)
                                            else vm.revertFlagEditB(editedFlag.path)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        if (showChangelogDialog) {
            val changelog = remember(changelogLimitEnabled, vm.flatA, vm.flatB) {
                vm.generateChangelogText(limit = if (changelogLimitEnabled) 15 else -1)
            }
            val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
            AlertDialog(
                onDismissRequest = { showChangelogDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.changelog_title))
                        IconButton(onClick = { changelogLimitEnabled = !changelogLimitEnabled }) {
                            Icon(
                                imageVector = if (changelogLimitEnabled) Icons.Rounded.UnfoldMore else Icons.Rounded.UnfoldLess,
                                contentDescription = "Toggle Limit",
                                tint = accents.teal
                            )
                        }
                    }
                },
                text = {
                    val scrollState = rememberScrollState()
                    Column(modifier = Modifier.heightIn(max = 500.dp).verticalScroll(scrollState)) {
                        if (changelog.unmappedCount > 0) {
                            Surface(
                                modifier = Modifier.padding(bottom = 12.dp).fillMaxWidth(),
                                color = accents.danger.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(14.dp),
                                border = androidx.compose.foundation.BorderStroke(1.5.dp, accents.danger.copy(alpha = 0.4f))
                            ) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Icon(Icons.Rounded.Warning, null, tint = accents.danger, modifier = Modifier.size(20.dp))
                                    Text(text = stringResource(R.string.changelog_unmapped_warning, changelog.unmappedCount), fontSize = 12.sp, color = accents.danger, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Text(text = changelog.text, fontFamily = MonoFamily, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp))
                    }
                },
                confirmButton = { TextButton(onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(changelog.text)); Toast.makeText(context, context.getString(R.string.copy_toast_success), Toast.LENGTH_SHORT).show() }) { Text(stringResource(R.string.changelog_copy), color = accents.teal, fontWeight = FontWeight.Bold) } },
                dismissButton = { TextButton(onClick = { showChangelogDialog = false }) { Text(stringResource(R.string.delete_confirm_cancel), color = MaterialTheme.colorScheme.onSurfaceVariant) } },
                containerColor = MaterialTheme.colorScheme.surface, titleContentColor = MaterialTheme.colorScheme.onSurface, textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (showEditDialog) {
            EditFlagDialog(
                path = editingFlagPath,
                displayName = editingFlagDisplayName,
                currentValue = editingFlagValue,
                mapper = vm.mapper,
                confirmDeletion = confirmDeletion,
                onDismiss = { showEditDialog = false },
                onSave = { newValue ->
                    if (editingFlagSource == 'A') vm.updateFlagValueA(editingFlagPath, newValue)
                    else vm.updateFlagValueB(editingFlagPath, newValue)
                    showEditDialog = false
                },
                sessionOriginalValue = editingFlagOriginalValue
            )
        }
    }
}

@Composable
private fun PerformanceWarning() {
    val accents = LocalAccentColors.current
    Surface(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp).fillMaxWidth(),
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
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = stringResource(R.string.warning_high_flag_count),
                color = accents.danger,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 17.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Justify
            )
        }
    }
}

@Composable
private fun ChangelogButton(onClick: () -> Unit, accent: Color) {
    Surface(
        onClick = onClick, color = accent.copy(alpha = 0.08f), contentColor = accent, shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.5.dp, accent.copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(Icons.Rounded.HistoryEdu, null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Text(text = stringResource(R.string.compare_action_changelog), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, letterSpacing = 0.5.sp)
        }
    }
}

private suspend fun loadFlatInto(context: android.content.Context, uri: Uri, onName: (String) -> Unit, onFlat: (Map<String, Any?>) -> Unit, onError: (String) -> Unit) {
    val name = displayNameFromUri(context, uri); onName(name)
    try {
        val raw = readTextFromUri(context, uri)
        val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val outcome = BackupConverter.convert(name, raw)
            when (outcome) {
                is ConvertOutcome.Success -> JsonDiff.flatten(outcome.data)
                is ConvertOutcome.Error -> {
                    // Fallback to direct parse if convert failed (e.g. simple JSON)
                    try {
                        val parsed = JsonDiff.parse(raw)
                        if (parsed is List<*>) null else JsonDiff.flatten(parsed)
                    } catch (_: Exception) { null }
                }
            }
        }
        if (result != null) {
            onFlat(result)
        } else {
            onError(context.getString(R.string.error_mapping_as_backup))
        }
    } catch (e: Exception) {
        onError(e.message ?: "Unknown error")
    }
}

@Composable
private fun DeletedStatsRow(deletedA: List<FlatFlag>, deletedB: List<FlatFlag>, onClickA: () -> Unit, onClickB: () -> Unit) {
    val accents = LocalAccentColors.current
    Row(modifier = Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(accents.danger.copy(alpha = 0.1f)).border(1.dp, accents.danger.copy(alpha = 0.2f), RoundedCornerShape(12.dp)).padding(vertical = 8.dp, horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceAround) {
        if (deletedA.isNotEmpty()) SmallStat(stringResource(R.string.compare_stats_deleted_a), deletedA.size.toString(), accents.danger, onClickA)
        if (deletedB.isNotEmpty()) SmallStat(stringResource(R.string.compare_stats_deleted_b), deletedB.size.toString(), accents.danger, onClickB)
    }
}

@Composable
private fun EditedStatsRow(editedCountA: Int, editedCountB: Int, onClickA: () -> Unit, onClickB: () -> Unit) {
    val accents = LocalAccentColors.current
    Row(modifier = Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(accents.violet.copy(alpha = 0.1f)).border(1.dp, accents.violet.copy(alpha = 0.2f), RoundedCornerShape(12.dp)).padding(vertical = 8.dp, horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceAround) {
        if (editedCountA > 0) SmallStat(stringResource(R.string.compare_stats_edited_a), editedCountA.toString(), accents.violet, onClickA)
        if (editedCountB > 0) SmallStat(stringResource(R.string.compare_stats_edited_b), editedCountB.toString(), accents.violet, onClickB)
    }
}

@Composable
private fun MovementStatsRow(movedToA: List<MovedFlag>, movedToB: List<MovedFlag>, onClickA: () -> Unit, onClickB: () -> Unit) {
    val accents = LocalAccentColors.current
    Row(modifier = Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(accents.teal.copy(alpha = 0.1f)).border(1.dp, accents.teal.copy(alpha = 0.2f), RoundedCornerShape(12.dp)).padding(vertical = 8.dp, horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceAround) {
        if (movedToA.isNotEmpty()) SmallStat(stringResource(R.string.compare_stats_moved_to_a), movedToA.size.toString(), accents.teal, onClickA)
        if (movedToB.isNotEmpty()) SmallStat(stringResource(R.string.compare_stats_moved_to_b), movedToB.size.toString(), accents.amber, onClickB)
    }
}

@Composable
private fun SmallStat(label: String, value: String, accent: Color, onClick: () -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onClick() }.padding(horizontal = 8.dp, vertical = 4.dp)) {
        Text(text = value, fontFamily = MonoFamily, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = accent)
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = label, fontSize = 10.sp, color = LocalAccentColors.current.textMuted)
    }
}

@Composable
private fun StatsRow(result: CompareResult) {
    val accents = LocalAccentColors.current; val neutralColor = MaterialTheme.colorScheme.onSurface
    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.outline)) {
        StatCell(stringResource(R.string.compare_stats_total_a), result.totalA.toString(), neutralColor, Modifier.weight(1f))
        StatCell(stringResource(R.string.compare_stats_total_b), result.totalB.toString(), neutralColor, Modifier.weight(1f))
        StatCell(stringResource(R.string.compare_stats_only_a), result.onlyInA.size.toString(), accents.teal, Modifier.weight(1f))
        StatCell(stringResource(R.string.compare_stats_only_b), result.onlyInB.size.toString(), accents.amber, Modifier.weight(1f))
    }
}

@Composable
private fun StatCell(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    val accents = LocalAccentColors.current
    Column(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant).padding(vertical = 16.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, fontFamily = MonoFamily, fontSize = 18.sp, color = accent)
        Text(text = label, fontSize = 10.sp, color = accents.textMuted, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun SearchFilterRow(currentScope: SearchScope, onScopeChange: (SearchScope) -> Unit, accentColor: Color, modifier: Modifier = Modifier) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    Box(
        modifier = modifier
            .widthIn(max = 180.dp)
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isDark) Color.Black.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.08f))
            .padding(4.dp)
    ) {
        val indicatorAlignment by animateAlignmentAsState(
            if (currentScope == SearchScope.FLAGS) Alignment.CenterStart else Alignment.CenterEnd
        )

        // Background indicator (the "quadrado" from the image)
        Box(
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .fillMaxHeight()
                .align(indicatorAlignment)
                .clip(RoundedCornerShape(10.dp))
                .background(accentColor)
        )

        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onScopeChange(SearchScope.FLAGS) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.search_scope_flags),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (currentScope == SearchScope.FLAGS) {
                        if (accentColor.luminance() > 0.5f) Color.Black else Color.White
                    } else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onScopeChange(SearchScope.SUBFLAGS) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.search_scope_subflags),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (currentScope == SearchScope.SUBFLAGS) {
                        if (accentColor.luminance() > 0.5f) Color.Black else Color.White
                    } else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun animateAlignmentAsState(
    targetAlignment: Alignment,
): State<Alignment> {
    val horizontal by animateFloatAsState(if (targetAlignment == Alignment.CenterStart) -1f else 1f, label = "horizontal")
    return remember {
        derivedStateOf { BiasAlignment(horizontal, 0f) }
    }
}

@Composable
private fun FlagSearchBar(query: String, onQueryChange: (String) -> Unit, accentColor: Color, ghostTerm: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = query, onValueChange = onQueryChange,
        placeholder = { AnimatedContent(targetState = ghostTerm, transitionSpec = { (slideInVertically { it } + fadeIn()).togetherWith(slideOutVertically { -it } + fadeOut()) }, label = "ghostText") { term -> Text(text = "${stringResource(R.string.compare_search_placeholder)} $term", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) } },
        leadingIcon = { Icon(imageVector = Icons.Rounded.Search, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp)) },
        trailingIcon = { if (query.isNotEmpty()) { IconButton(onClick = { onQueryChange("") }) { Icon(imageVector = Icons.Rounded.Close, contentDescription = "Limpar pesquisa", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(20.dp)) } } },
        modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(999.dp),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accentColor, unfocusedBorderColor = accentColor.copy(alpha = 0.7f), focusedLeadingIconColor = accentColor, unfocusedLeadingIconColor = accentColor.copy(alpha = 0.7f)),
        singleLine = true, textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp)
    )
}

@Composable
private fun EditedFlagSummaryCard(
    edited: EditedFlag,
    mapper: FlagMapper?,
    onRevert: () -> Unit
) {
    val accents = LocalAccentColors.current
    val displayName = remember(edited.path, mapper) { mapper?.getDisplayName(edited.path) ?: edited.path }
    val cleanName = remember(displayName) { displayName.replace("_", " ") }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = cleanName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
                }

                IconButton(
                    onClick = onRevert,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SettingsBackupRestore,
                        contentDescription = stringResource(R.string.edit_dialog_revert_flag),
                        tint = accents.teal,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Diff analysis
            if (edited.originalValue is org.json.JSONArray && edited.currentValue is org.json.JSONArray) {
                val origArr = edited.originalValue
                val currArr = edited.currentValue

                // Parse items into Map<ID, Value> to detect changes
                fun parseToMap(arr: org.json.JSONArray): Map<String, String> {
                    val map = mutableMapOf<String, String>()
                    for (i in 0 until arr.length()) {
                        val raw = arr.optString(i) ?: continue
                        val parts = raw.split(":")
                        if (parts.size >= 2) {
                            val id = parts[0].trim()
                            val v = parts.last().trim()
                            map[id] = v
                        } else {
                            map["raw_$i"] = raw
                        }
                    }
                    return map
                }

                val origMap = parseToMap(origArr)
                val currMap = parseToMap(currArr)

                val allIds = (origMap.keys + currMap.keys).distinct()
                val modifiedEntries = allIds.filter { origMap[it] != currMap[it] }

                if (modifiedEntries.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        modifiedEntries.forEach { id ->
                            val valA = origMap[id]
                            val valB = currMap[id]
                            val label = if (id.startsWith("raw_")) "" else mapper?.getParamLabel(edited.path, id) ?: "#$id"

                            Column {
                                if (label.isNotEmpty()) {
                                    Text(text = label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (valA != null && valB != null) {
                                        StatusText(valA, accents, isOld = true)
                                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, modifier = Modifier.size(12.dp).padding(horizontal = 4.dp), tint = accents.textMuted)
                                        StatusText(valB, accents)
                                    } else if (valA != null) {
                                        StatusText(valA, accents, isOld = true)
                                        Spacer(Modifier.width(8.dp))
                                        Surface(
                                            color = accents.danger.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(6.dp),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, accents.danger.copy(alpha = 0.3f))
                                        ) {
                                            Text(
                                                text = stringResource(R.string.changelog_status_removed).uppercase(),
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Black,
                                                color = accents.danger,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    } else if (valB != null) {
                                        StatusText(valB, accents)
                                        Spacer(Modifier.width(8.dp))
                                        Surface(
                                            color = accents.teal.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(6.dp),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, accents.teal.copy(alpha = 0.3f))
                                        ) {
                                            Text(
                                                text = stringResource(R.string.changelog_status_added).uppercase(),
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Black,
                                                color = accents.teal,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Simple value diff
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusText(edited.originalValue.toString(), accents, isOld = true)
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, modifier = Modifier.size(14.dp).padding(horizontal = 6.dp), tint = accents.textMuted)
                    StatusText(edited.currentValue.toString(), accents)
                }
            }
        }
    }
}

@Composable
private fun StatusText(value: String, accents: com.flag.sync.ui.theme.AccentColors, isOld: Boolean = false) {
    val isTrue = value.lowercase().trim() == "true"
    val isFalse = value.lowercase().trim() == "false"

    val color = when {
        isTrue -> accents.success
        isFalse -> accents.danger
        else -> accents.textMuted
    }

    Text(
        text = value,
        fontFamily = MonoFamily,
        fontSize = 11.sp,
        fontWeight = if (isOld) FontWeight.Normal else FontWeight.Bold,
        color = if (isOld) color.copy(alpha = 0.6f) else color,
        style = if (isOld) MaterialTheme.typography.bodySmall.copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough) else MaterialTheme.typography.bodySmall
    )
}