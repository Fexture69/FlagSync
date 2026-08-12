 /*
  * Copyright (C) 2026 ViictorNunes (https://github.com/ViictorNunes/FlagSync).
  * Licensed under the GNU General Public License v3.0.
  */


package com.flag.sync

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flag.sync.logic.AppUpdateProvider
import com.flag.sync.logic.FlagMapper
import com.flag.sync.logic.FlagMapperProvider
import com.flag.sync.model.*
import com.flag.sync.ui.components.BottomNavBar
import com.flag.sync.ui.components.FeatureTourOverlay
import com.flag.sync.ui.screens.CompareScreen
import com.flag.sync.ui.screens.ConverterScreen
import com.flag.sync.ui.screens.SettingsScreen
import com.flag.sync.ui.theme.FlagSyncTheme
import com.flag.sync.utils.LocaleUtils
import com.flag.sync.utils.readTextFromUri
import java.io.File
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FlagSyncApp(mainViewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("flag_sync_prefs", Context.MODE_PRIVATE) }

    var appTheme by remember {
        val savedName = prefs.getString("app_theme", null)
        val isDarkSystem = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val defaultTheme = if (isDarkSystem) AppTheme.DARK else AppTheme.LIGHT

        if (savedName == null) {
            mutableStateOf(defaultTheme)
        } else if (savedName == "MATERIAL_YOU" || savedName == "AMOLED") {
            // Migration
            if (savedName == "AMOLED") prefs.edit().putBoolean("is_amoled_mode", true).apply()

            prefs.edit()
                .putString("app_theme", defaultTheme.name)
                .putString("accent_palette", if (savedName == "MATERIAL_YOU") AccentPalette.MATERIAL_YOU.name else AccentPalette.DEFAULT.name)
                .apply()
            mutableStateOf(defaultTheme)
        } else {
            mutableStateOf(AppTheme.entries.find { it.name == savedName } ?: defaultTheme)
        }
    }
    var accentPalette by remember {
        val savedPalette = prefs.getString("accent_palette", AccentPalette.DEFAULT.name)
        mutableStateOf(AccentPalette.entries.find { it.name == savedPalette } ?: AccentPalette.DEFAULT)
    }
    var isSecurityUnlocked by remember {
        mutableStateOf(prefs.getBoolean("is_security_unlocked", false))
    }
    var currentLanguage by remember {
        mutableStateOf(prefs.getString("app_lang", "") ?: "")
    }
    var confirmDeletion by remember {
        mutableStateOf(prefs.getBoolean("confirm_deletion", true))
    }
    var autoRenameOverrides by remember {
        mutableStateOf(prefs.getBoolean("auto_rename_overrides", false))
    }
    var autoCheckMappingUpdates by remember {
        mutableStateOf(prefs.getBoolean("auto_check_updates", true))
    }
    var autoCheckAppUpdates by remember {
        mutableStateOf(prefs.getBoolean("auto_check_app_updates", true))
    }
    var isAmoledMode by remember {
        mutableStateOf(prefs.getBoolean("is_amoled_mode", false))
    }
    var useCustomFilePicker by remember {
        mutableStateOf(prefs.getBoolean("use_custom_picker", false))
    }
    var customColorHex by remember {
        mutableStateOf(prefs.getString("custom_color_hex", "#4FD8C9") ?: "#4FD8C9")
    }

    // Feature Tour State
    var isTourSeen by remember { mutableStateOf(prefs.getBoolean("tour_seen", false)) }
    var isTourActive by remember { mutableStateOf(!isTourSeen) }
    var currentTourStep by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        try {
            // FlagMapperProvider.init() already runs off the Main
            // thread, so no artificial delay is needed here anymore.
            FlagMapperProvider.init(context.applicationContext)
            mainViewModel.updateMapper(FlagMapperProvider.get(), isDefault = true)

            if (autoCheckMappingUpdates) {
                mainViewModel.checkMappingUpdate(context.applicationContext)
            }
            if (autoCheckAppUpdates) {
                mainViewModel.checkAppUpdate(context.applicationContext) { available ->
                    if (available) {
                        mainViewModel.showAppUpdateDialog = true
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("FlagSyncApp", "Failed to initialize mapper/updates", e)
        }
    }

    LaunchedEffect(currentLanguage) {
        if (currentLanguage != LocaleUtils.getCurrentLocale()) {
            LocaleUtils.setLocale(currentLanguage)
        }
    }

    FlagSyncTheme(
        appTheme = appTheme,
        accentPalette = accentPalette,
        customColorHex = customColorHex,
        isAmoled = isAmoledMode
    ) {
        // App surface with theme background
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val destinations = NavDestination.entries
            val pagerState = rememberPagerState(pageCount = { destinations.size })
            val scope = rememberCoroutineScope()

            val compareListState = rememberLazyListState()
            val converterListState = rememberLazyListState()
            val settingsListState = rememberLazyListState()

            // Sync Pager and Scroll with Tour steps
            LaunchedEffect(currentTourStep, isTourActive) {
                if (isTourActive) {
                    when (currentTourStep) {
                        1 -> {
                            // Scroll to top of compare screen for spotlight
                            pagerState.animateScrollToPage(0)
                            compareListState.scrollToItem(0)
                        }
                        2 -> {
                            // Keeps on page 0 to show navigation after comparison
                            pagerState.animateScrollToPage(0)
                        }
                        3 -> {
                            // Switch to converter screen
                            pagerState.animateScrollToPage(1)
                            converterListState.scrollToItem(0)
                        }
                        4 -> {
                            // Switch to settings screen
                            pagerState.animateScrollToPage(2)
                            settingsListState.scrollToItem(0)
                        }
                    }
                }
            }

            val currentDestination by remember {
                derivedStateOf { destinations[pagerState.currentPage] }
            }
            val haptic = LocalHapticFeedback.current

            LaunchedEffect(pagerState.currentPage) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }

            var showInternalPicker by remember { mutableStateOf(false) }
            var showInternalSavePicker by remember { mutableStateOf(false) }
            var showPickerPermissionDialog by remember { mutableStateOf(false) }
            var pickerTarget by remember { mutableStateOf(PickerTarget.BACKUP_A) }

            var pendingSaveData by remember { mutableStateOf<String?>(null) }
            var suggestedSaveName by remember { mutableStateOf("") }

            Box(modifier = Modifier.fillMaxSize()) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = true,
                ) { page ->
                    when (destinations[page]) {
                        NavDestination.COMPARE -> {
                            val onConfirmDeletionChangeA = remember {
                                { value: Boolean ->
                                    confirmDeletion = value
                                    prefs.edit().putBoolean("confirm_deletion", value).apply()
                                }
                            }
                            val onRequestInternalPickerA = remember {
                                { target: PickerTarget ->
                                    pickerTarget = target
                                    showInternalPicker = true
                                }
                            }
                            val onSaveFileA = remember {
                                { data: String, name: String, target: PickerTarget ->
                                    pendingSaveData = data
                                    suggestedSaveName = name
                                    pickerTarget = target
                                    showInternalSavePicker = true
                                }
                            }
                            CompareScreen(
                                viewModel = mainViewModel,
                                listState = compareListState,
                                isDebugMode = isSecurityUnlocked,
                                confirmDeletion = confirmDeletion,
                                autoRenameOverrides = autoRenameOverrides,
                                onConfirmDeletionChange = onConfirmDeletionChangeA,
                                useCustomPicker = useCustomFilePicker,
                                onRequestInternalPicker = onRequestInternalPickerA,
                                onSaveFile = onSaveFileA
                            )
                        }
                        NavDestination.CONVERTER -> {
                            val onCleanDataChangedC = remember { { it: Map<String, Any?>? -> mainViewModel.cleanDataConv = it } }
                            val onFileNameChangedC = remember { { it: String? -> mainViewModel.fileNameConv = it } }
                            val onStatusChangedC = remember { { it: String? -> mainViewModel.statusConv = it } }
                            val onRequestInternalPickerC = remember {
                                { target: PickerTarget ->
                                    pickerTarget = target
                                    showInternalPicker = true
                                }
                            }
                            val onSaveFileC = remember {
                                { data: String, name: String ->
                                    pendingSaveData = data
                                    suggestedSaveName = name
                                    pickerTarget = PickerTarget.CONVERTED
                                    showInternalSavePicker = true
                                }
                            }
                            ConverterScreen(
                                autoRenameOverrides = autoRenameOverrides,
                                cleanData = mainViewModel.cleanDataConv, onCleanDataChanged = onCleanDataChangedC,
                                fileName = mainViewModel.fileNameConv, onFileNameChanged = onFileNameChangedC,
                                status = mainViewModel.statusConv, onStatusChanged = onStatusChangedC,
                                useCustomPicker = useCustomFilePicker,
                                onRequestInternalPicker = onRequestInternalPickerC,
                                onSaveFile = onSaveFileC,
                                listState = converterListState
                            )
                        }
                        NavDestination.SETTINGS -> {
                            val onThemeChangeS = remember {
                                { value: AppTheme ->
                                    appTheme = value
                                    prefs.edit().putString("app_theme", value.name).apply()
                                }
                            }
                            val onAccentPaletteChangeS = remember {
                                { value: AccentPalette ->
                                    accentPalette = value
                                    prefs.edit().putString("accent_palette", value.name).apply()
                                }
                            }
                            val onUnlockedChangeS = remember {
                                { value: Boolean ->
                                    isSecurityUnlocked = value
                                    prefs.edit().putBoolean("is_security_unlocked", value).apply()
                                }
                            }
                            val onLanguageChangeS = remember {
                                { value: String ->
                                    currentLanguage = value
                                    prefs.edit().putString("app_lang", value).apply()
                                    LocaleUtils.setLocale(value)
                                }
                            }
                            val onConfirmDeletionChangeS = remember {
                                { value: Boolean ->
                                    confirmDeletion = value
                                    prefs.edit().putBoolean("confirm_deletion", value).apply()
                                }
                            }
                            val onAutoRenameOverridesChangeS = remember {
                                { value: Boolean ->
                                    autoRenameOverrides = value
                                    prefs.edit().putBoolean("auto_rename_overrides", value).apply()
                                }
                            }
                            val onCustomColorChangeS = remember {
                                { value: String ->
                                    customColorHex = value
                                    prefs.edit().putString("custom_color_hex", value).apply()
                                }
                            }
                            val onAutoCheckMappingUpdatesChangeS = remember {
                                { value: Boolean ->
                                    autoCheckMappingUpdates = value
                                    mainViewModel.updateAutoCheckUpdates(value)
                                    prefs.edit().putBoolean("auto_check_updates", value).apply()
                                    if (value) {
                                        mainViewModel.checkMappingUpdate(context.applicationContext)
                                    }
                                }
                            }
                            val onManualUpdateCheckS = remember {
                                {
                                    if (mainViewModel.mappingUpdateAvailable) {
                                        mainViewModel.showUpdateDialog = true
                                    } else {
                                        Toast.makeText(context, context.getString(R.string.settings_updating_mapping), Toast.LENGTH_SHORT).show()
                                        mainViewModel.checkMappingUpdate(context.applicationContext) { available ->
                                            if (available) {
                                                mainViewModel.showUpdateDialog = true
                                            } else {
                                                Toast.makeText(context, context.getString(R.string.compare_update_no_updates), Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                            }
                            val onAutoCheckAppUpdatesChangeS = remember {
                                { value: Boolean ->
                                    autoCheckAppUpdates = value
                                    mainViewModel.updateAutoCheckAppUpdates(value)
                                    prefs.edit().putBoolean("auto_check_app_updates", value).apply()
                                    if (value) {
                                        mainViewModel.checkAppUpdate(context.applicationContext)
                                    }
                                }
                            }
                            val onManualAppUpdateCheckS = remember {
                                {
                                    if (mainViewModel.appUpdateInfo != null) {
                                        mainViewModel.showAppUpdateDialog = true
                                    } else {
                                        Toast.makeText(context, context.getString(R.string.settings_updating_mapping), Toast.LENGTH_SHORT).show()
                                        mainViewModel.checkAppUpdate(context.applicationContext) { available ->
                                            if (available) {
                                                mainViewModel.showAppUpdateDialog = true
                                            } else {
                                                Toast.makeText(context, context.getString(R.string.settings_app_update_no_updates), Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                            }
                            val onAmoledModeChangeS = remember {
                                { value: Boolean ->
                                    isAmoledMode = value
                                    prefs.edit().putBoolean("is_amoled_mode", value).apply()
                                }
                            }
                            val onUseCustomPickerChangeS = remember {
                                { value: Boolean ->
                                    if (value) {
                                        val hasPermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                                            android.os.Environment.isExternalStorageManager()
                                        } else {
                                            androidx.core.content.ContextCompat.checkSelfPermission(
                                                context,
                                                android.Manifest.permission.READ_EXTERNAL_STORAGE
                                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                        }

                                        if (!hasPermission) {
                                            showPickerPermissionDialog = true
                                        }
                                    }
                                    useCustomFilePicker = value
                                    prefs.edit().putBoolean("use_custom_picker", value).apply()
                                }
                            }
                            val onRestartTourS = remember {
                                {
                                    currentTourStep = 0
                                    isTourActive = true
                                }
                            }

                            SettingsScreen(
                                appTheme = appTheme,
                                onThemeChange = onThemeChangeS,
                                accentPalette = accentPalette,
                                onAccentPaletteChange = onAccentPaletteChangeS,
                                isUnlocked = isSecurityUnlocked,
                                onUnlockedChange = onUnlockedChangeS,
                                currentLanguage = currentLanguage,
                                onLanguageChange = onLanguageChangeS,
                                confirmDeletion = confirmDeletion,
                                onConfirmDeletionChange = onConfirmDeletionChangeS,
                                autoRenameOverrides = autoRenameOverrides,
                                onAutoRenameOverridesChange = onAutoRenameOverridesChangeS,
                                customColorHex = customColorHex,
                                onCustomColorChange = onCustomColorChangeS,
                                autoCheckMappingUpdates = autoCheckMappingUpdates,
                                onAutoCheckMappingUpdatesChange = onAutoCheckMappingUpdatesChangeS,
                                mappingUpdateAvailable = mainViewModel.mappingUpdateAvailable,
                                isCheckingUpdate = mainViewModel.isCheckingUpdate,
                                isUpdatingMapping = mainViewModel.isUpdatingMapping,
                                onManualUpdateCheck = onManualUpdateCheckS,
                                autoCheckAppUpdates = autoCheckAppUpdates,
                                onAutoCheckAppUpdatesChange = onAutoCheckAppUpdatesChangeS,
                                isCheckingAppUpdate = mainViewModel.isCheckingAppUpdate,
                                appUpdateAvailable = mainViewModel.appUpdateInfo != null,
                                onManualAppUpdateCheck = onManualAppUpdateCheckS,
                                isAmoledMode = isAmoledMode,
                                onAmoledModeChange = onAmoledModeChangeS,
                                useCustomPicker = useCustomFilePicker,
                                onUseCustomPickerChange = onUseCustomPickerChangeS,
                                onRestartTour = onRestartTourS,
                                listState = settingsListState
                            )
                        }
                    }
                }

                BottomNavBar(
                    current = currentDestination,
                    onSelect = { dest ->
                        scope.launch {
                            pagerState.animateScrollToPage(destinations.indexOf(dest))
                        }
                    },
                    isMaterialYou = accentPalette == AccentPalette.MATERIAL_YOU,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 32.dp)
                )

                AnimatedVisibility(
                    visible = showInternalPicker || showInternalSavePicker,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                ) {
                    com.flag.sync.ui.components.InternalFilePicker(
                        onDismiss = {
                            showInternalPicker = false
                            showInternalSavePicker = false
                        },
                        saveMode = showInternalSavePicker,
                        suggestedFileName = suggestedSaveName,
                        onSaveFile = { folder, name ->
                            showInternalSavePicker = false
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val file = File(folder, name)
                                    file.writeText(pendingSaveData ?: "")
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Arquivo salvo com sucesso!", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Erro ao salvar: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        },
                        allowedExtensions = when {
                            pickerTarget == PickerTarget.MAPPING -> setOf("json")
                            pickerTarget == PickerTarget.CONVERTED && !showInternalSavePicker -> setOf("ibackup")
                            showInternalSavePicker -> setOf("json")
                            else -> setOf("json", "ibackup")
                        },
                        onFilePicked = { file ->
                            showInternalPicker = false
                            scope.launch {
                                mainViewModel.loadFileFromUri(
                                    context = context,
                                    uri = android.net.Uri.fromFile(file),
                                    onName = { name ->
                                        when (pickerTarget) {
                                            PickerTarget.BACKUP_A -> mainViewModel.updateNameA(name)
                                            PickerTarget.BACKUP_B -> mainViewModel.updateNameB(name)
                                            PickerTarget.CONVERTED -> mainViewModel.fileNameConv = name
                                            PickerTarget.MAPPING -> {}
                                        }
                                    },
                                    onFlat = { data ->
                                        when (pickerTarget) {
                                            PickerTarget.BACKUP_A -> {
                                                mainViewModel.updateFlatA(data)
                                                mainViewModel.updateErrorA(null)
                                            }
                                            PickerTarget.BACKUP_B -> {
                                                mainViewModel.updateFlatB(data)
                                                mainViewModel.updateErrorB(null)
                                            }
                                            PickerTarget.CONVERTED -> {
                                                mainViewModel.cleanDataConv = data
                                                mainViewModel.statusConv = context.getString(R.string.converter_status_success)
                                            }
                                            PickerTarget.MAPPING -> {
                                                scope.launch {
                                                    try {
                                                        val raw = file.readText()
                                                        mainViewModel.updateMapper(FlagMapper(raw))
                                                    } catch (e: Exception) {
                                                        android.util.Log.e("FlagSyncApp", "Failed to load mapping", e)
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    onError = { err ->
                                        Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                    }
                                )
                            }
                        }
                    )
                }

                if (isTourActive) {
                    FeatureTourOverlay(
                        step = currentTourStep,
                        onNext = { currentTourStep++ },
                        onDismiss = {
                            isTourActive = false
                            isTourSeen = true
                            prefs.edit().putBoolean("tour_seen", true).apply()
                            scope.launch {
                                pagerState.animateScrollToPage(0)
                            }
                        }
                    )
                }

                if (mainViewModel.showUpdateDialog) {
                    var isDownloading by remember { mutableStateOf(false) }
                    var downloadProgress by remember { mutableFloatStateOf(0f) }

                    AlertDialog(
                        onDismissRequest = { if (!isDownloading) mainViewModel.showUpdateDialog = false },
                        title = { Text(stringResource(R.string.settings_update_dialog_title)) },
                        text = {
                            Column {
                                if (isDownloading) {
                                    Text("Baixando mapeamento...", fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    LinearProgressIndicator(
                                        progress = { downloadProgress },
                                        modifier = Modifier.fillMaxWidth(),
                                        color = com.flag.sync.ui.theme.LocalAccentColors.current.teal,
                                        trackColor = com.flag.sync.ui.theme.LocalAccentColors.current.teal.copy(alpha = 0.1f)
                                    )
                                    Text(
                                        text = "${(downloadProgress * 100).roundToInt()}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.align(Alignment.End).padding(top = 4.dp)
                                    )
                                } else {
                                    Text(stringResource(R.string.settings_update_dialog_message))
                                }
                            }
                        },
                        confirmButton = {
                            if (!isDownloading) {
                                val accents = com.flag.sync.ui.theme.LocalAccentColors.current
                                TextButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        isDownloading = true
                                        mainViewModel.isUpdatingMapping = true
                                        Toast.makeText(context, context.getString(R.string.settings_updating_mapping), Toast.LENGTH_SHORT).show()
                                        scope.launch {
                                            val res = FlagMapperProvider.updateFromNetwork(context.applicationContext) { progress ->
                                                downloadProgress = progress
                                            }
                                            mainViewModel.isUpdatingMapping = false
                                            isDownloading = false
                                            res.onSuccess {
                                                // Auto-restart
                                                val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                                                intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                                context.startActivity(intent)
                                                Runtime.getRuntime().exit(0)
                                            }.onFailure {
                                                Toast.makeText(context, context.getString(R.string.settings_update_mapping_error, it.message), Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                ) {
                                    Text(stringResource(R.string.settings_update_dialog_confirm), color = accents.teal, fontWeight = FontWeight.Bold)
                                }
                            }
                        },
                        dismissButton = {
                            if (!isDownloading) {
                                TextButton(onClick = { mainViewModel.showUpdateDialog = false }) {
                                    Text(stringResource(R.string.edit_dialog_cancel))
                                }
                            }
                        }
                    )
                }

                if (mainViewModel.showAppUpdateDialog && mainViewModel.appUpdateInfo != null) {
                    val info = mainViewModel.appUpdateInfo!!
                    var isDownloading by remember { mutableStateOf(false) }
                    var downloadProgress by remember { mutableFloatStateOf(0f) }

                    AlertDialog(
                        onDismissRequest = { if (!isDownloading) mainViewModel.showAppUpdateDialog = false },
                        title = { Text(stringResource(R.string.settings_app_update_dialog_title)) },
                        text = {
                            Column {
                                if (isDownloading) {
                                    Text("Baixando atualização...", fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    LinearProgressIndicator(
                                        progress = { downloadProgress },
                                        modifier = Modifier.fillMaxWidth(),
                                        color = com.flag.sync.ui.theme.LocalAccentColors.current.teal,
                                        trackColor = com.flag.sync.ui.theme.LocalAccentColors.current.teal.copy(alpha = 0.1f)
                                    )
                                    Text(
                                        text = "${(downloadProgress * 100).roundToInt()}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.align(Alignment.End).padding(top = 4.dp)
                                    )
                                } else {
                                    Text(stringResource(R.string.settings_app_update_dialog_message, info.versionName))
                                    if (info.changelog.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            val accents = com.flag.sync.ui.theme.LocalAccentColors.current
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Rounded.FormatListBulleted,
                                                contentDescription = null,
                                                tint = accents.teal,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Novidades:",
                                                fontWeight = FontWeight.Black,
                                                fontSize = 12.sp,
                                                color = accents.teal
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Surface(
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = info.changelog,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(12.dp),
                                                lineHeight = 16.sp
                                            )
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            if (!isDownloading) {
                                val accents = com.flag.sync.ui.theme.LocalAccentColors.current
                                TextButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        isDownloading = true
                                        scope.launch {
                                            val res = AppUpdateProvider.downloadAndInstall(context.applicationContext, info.downloadUrl) { progress ->
                                                downloadProgress = progress
                                            }
                                            isDownloading = false
                                            if (res.isSuccess) {
                                                mainViewModel.showAppUpdateDialog = false
                                            } else {
                                                Toast.makeText(context, "Erro ao baixar: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                ) {
                                    Text(stringResource(R.string.settings_app_update_dialog_confirm), color = accents.teal, fontWeight = FontWeight.Bold)
                                }
                            }
                        },
                        dismissButton = {
                            if (!isDownloading) {
                                TextButton(onClick = { mainViewModel.showAppUpdateDialog = false }) {
                                    Text(stringResource(R.string.edit_dialog_cancel))
                                }
                            }
                        }
                    )
                }

                if (showPickerPermissionDialog) {
                    AlertDialog(
                        onDismissRequest = {
                            showPickerPermissionDialog = false
                            useCustomFilePicker = false
                            prefs.edit().putBoolean("use_custom_picker", false).apply()
                        },
                        title = { Text(stringResource(R.string.settings_picker_permission_title)) },
                        text = { Text(stringResource(R.string.settings_picker_permission_message)) },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showPickerPermissionDialog = false
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
                                }
                            ) {
                                Text(stringResource(R.string.settings_picker_permission_confirm), color = com.flag.sync.ui.theme.LocalAccentColors.current.teal, fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    showPickerPermissionDialog = false
                                    useCustomFilePicker = false
                                    prefs.edit().putBoolean("use_custom_picker", false).apply()
                                }
                            ) {
                                Text(stringResource(R.string.edit_dialog_cancel))
                            }
                        }
                    )
                }
            }
        }
    }
}