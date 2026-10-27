 /*
  * Copyright (C) 2026 ViictorNunes (https://github.com/ViictorNunes/FlagSync).
  * Licensed under the GNU General Public License v3.0.
  */


package com.flag.sync.ui.screens

import android.os.Build
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import com.flag.sync.R
import com.flag.sync.model.AccentPalette
import com.flag.sync.model.AppTheme
import com.flag.sync.ui.components.AppHeader
import com.flag.sync.ui.theme.LocalAccentColors
import com.flag.sync.ui.theme.MonoFamily
import com.flag.sync.utils.LocaleUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    appTheme: AppTheme,
    onThemeChange: (AppTheme) -> Unit,
    accentPalette: AccentPalette,
    onAccentPaletteChange: (AccentPalette) -> Unit,
    isUnlocked: Boolean,
    onUnlockedChange: (Boolean) -> Unit,
    currentLanguage: String,
    onLanguageChange: (String) -> Unit,
    confirmDeletion: Boolean,
    onConfirmDeletionChange: (Boolean) -> Unit,
    autoRenameOverrides: Boolean = false,
    onAutoRenameOverridesChange: (Boolean) -> Unit = {},
    autoCheckMappingUpdates: Boolean = true,
    onAutoCheckMappingUpdatesChange: (Boolean) -> Unit = {},
    mappingUpdateAvailable: Boolean = false,
    isCheckingUpdate: Boolean = false,
    isUpdatingMapping: Boolean = false,
    autoCheckAppUpdates: Boolean = true,
    onAutoCheckAppUpdatesChange: (Boolean) -> Unit = {},
    isCheckingAppUpdate: Boolean = false,
    appUpToDate: Boolean = false,
    onManualAppUpdateCheck: () -> Unit = {},
    appUpdateAvailable: Boolean = false,
    onManualUpdateCheck: () -> Unit = {},
    mappingUpToDate: Boolean = false,
    isAmoledMode: Boolean = false,
    onAmoledModeChange: (Boolean) -> Unit = {},
    useCustomPicker: Boolean = false,
    onUseCustomPickerChange: (Boolean) -> Unit = {},
    customColorHex: String = "#4FD8C9",
    onCustomColorChange: (String) -> Unit = {},
    forceReelQuality: Int = 0,
    onForceReelQualityChange: (Int) -> Unit = {},
    highResImages: Boolean = false,
    onHighResImagesChange: (Boolean) -> Unit = {},
    maxUploadQuality: Boolean = false,
    onMaxUploadQualityChange: (Boolean) -> Unit = {},
    spoofDevice: String = "",
    onSpoofDeviceChange: (String) -> Unit = {},
    onRestartTour: () -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState = rememberLazyListState(),
) {
    val accents = LocalAccentColors.current
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val uriHandler = LocalUriHandler.current
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    var showLanguageSheet by remember { mutableStateOf(false) }
    var showReelQualityDialog by remember { mutableStateOf(false) }
    var showSpoofDeviceDialog by remember { mutableStateOf(false) }

    var secretClickCount by remember { mutableIntStateOf(0) }
    var lastSecretClickTime by remember { mutableLongStateOf(0L) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = paddingValues.calculateBottomPadding() + 140.dp)
        ) {
            item {
                Box(modifier = Modifier.heightIn(min = 160.dp)) {
                    AppHeader(
                        eyebrow = stringResource(R.string.settings_eyebrow),
                        title = stringResource(R.string.settings_title),
                        description = stringResource(R.string.settings_description)
                    )
                }
            }

            // --- SEÇÃO DE APARÊNCIA UNIFICADA PARA ESTABILIDADE ---
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                    // Título Temas
                    Text(
                        text = stringResource(R.string.settings_theme_label),
                        fontFamily = MonoFamily,
                        fontSize = 11.sp,
                        color = accents.textMuted,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // Grade de Temas
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AppTheme.entries.forEach { theme ->
                            ThemeOptionCard(
                                theme = theme,
                                isSelected = appTheme == theme,
                                onSelect = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onThemeChange(theme)
                                    if (theme == AppTheme.LIGHT) {
                                        val currentTime = System.currentTimeMillis()
                                        if (currentTime - lastSecretClickTime > 800) {
                                            secretClickCount = 1
                                        } else {
                                            secretClickCount++
                                        }
                                        lastSecretClickTime = currentTime

                                        if (secretClickCount >= 10) {
                                            val nextState = !isUnlocked
                                            onUnlockedChange(nextState)
                                            secretClickCount = 0

                                            if (!nextState) {
                                                Toast.makeText(context, context.getString(R.string.settings_secret_locked), Toast.LENGTH_LONG).show()
                                            } else {
                                                Toast.makeText(context, context.getString(R.string.settings_secret_unlocked), Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    } else {
                                        secretClickCount = 0
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // AMOLED Switch (Com animação interna estável)
                    AnimatedVisibility(
                        visible = appTheme == AppTheme.DARK,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(12.dp))
                            SettingRow(
                                title = stringResource(R.string.settings_amoled_title),
                                subtitle = stringResource(R.string.settings_amoled_subtitle),
                                checked = isAmoledMode,
                                onCheckedChange = onAmoledModeChange
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Título Paleta
                    Text(
                        text = stringResource(R.string.settings_palette_label),
                        fontFamily = MonoFamily,
                        fontSize = 11.sp,
                        color = accents.textMuted,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    var showPicker by remember { mutableStateOf(false) }
                    val allPalettes = AccentPalette.entries.filter {
                        it != AccentPalette.MATERIAL_YOU || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    }

                    // Grade de Paletas Fixa (2 linhas estáveis)
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        allPalettes.chunked(3).forEach { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowItems.forEach { palette ->
                                    PaletteOptionCard(
                                        palette = palette,
                                        isSelected = accentPalette == palette,
                                        customColor = if (palette == AccentPalette.CUSTOM) customColorHex else null,
                                        onSelect = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            if (palette == AccentPalette.CUSTOM && accentPalette == AccentPalette.CUSTOM) {
                                                showPicker = true
                                            } else {
                                                onAccentPaletteChange(palette)
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                // Preencher espaço se a última linha tiver menos de 3 itens
                                repeat(3 - rowItems.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    if (showPicker) {
                        ColorPickerDialog(
                            initialColor = customColorHex,
                            onDismiss = { showPicker = false },
                            onColorSelected = {
                                onCustomColorChange(it)
                                showPicker = false
                            }
                        )
                    }
                }
            }

            // --- CATEGORIA GERAL ---
            item {
                val currentLangLabel = when {
                    currentLanguage.isEmpty() -> "🌐 " + stringResource(R.string.settings_lang_system) + " (${LocaleUtils.getSystemLanguageName(context)})"
                    currentLanguage.startsWith("pt") -> "🇧🇷 " + stringResource(R.string.settings_lang_pt)
                    currentLanguage.startsWith("es") -> "🇪🇸 " + stringResource(R.string.settings_lang_es)
                    else -> "🇺🇸 " + stringResource(R.string.settings_lang_en)
                }

                ClickableSettingRow(
                    title = stringResource(R.string.settings_language_label),
                    subtitle = currentLangLabel,
                    icon = Icons.Rounded.Language,
                    onClick = { showLanguageSheet = true }
                )
            }

            item {
                SettingRow(
                    title = stringResource(R.string.settings_auto_rename_title),
                    subtitle = stringResource(R.string.settings_auto_rename_subtitle),
                    checked = autoRenameOverrides,
                    onCheckedChange = onAutoRenameOverridesChange,
                    icon = Icons.Rounded.EditNote
                )
            }

            item {
                SettingRow(
                    title = stringResource(R.string.settings_confirm_delete_title),
                    subtitle = stringResource(R.string.settings_confirm_delete_subtitle),
                    checked = confirmDeletion,
                    onCheckedChange = onConfirmDeletionChange,
                    icon = Icons.Rounded.GppMaybe
                )
            }

            item {
                SettingRow(
                    title = stringResource(R.string.settings_custom_picker_title),
                    subtitle = stringResource(R.string.settings_custom_picker_subtitle),
                    checked = useCustomPicker,
                    onCheckedChange = onUseCustomPickerChange,
                    icon = Icons.Rounded.FolderShared
                )
            }

            // --- CATEGORIA QUALIDADE (INSTAGRAM) ---
            item {
                Text(
                    text = stringResource(R.string.settings_quality_label),
                    fontFamily = MonoFamily,
                    fontSize = 11.sp,
                    color = accents.textMuted,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                )
            }

            item {
                val qualityLabel = when (forceReelQuality) {
                    360 -> stringResource(R.string.quality_360)
                    480 -> stringResource(R.string.quality_480)
                    720 -> stringResource(R.string.quality_720)
                    1080 -> stringResource(R.string.quality_1080)
                    Int.MAX_VALUE -> stringResource(R.string.quality_max)
                    else -> stringResource(R.string.quality_auto)
                }

                ClickableSettingRow(
                    title = "${stringResource(R.string.settings_force_reels_quality_title)}: $qualityLabel",
                    subtitle = stringResource(R.string.settings_force_reels_quality_subtitle),
                    icon = Icons.Rounded.Movie,
                    onClick = { showReelQualityDialog = true }
                )
            }

            item {
                SettingRow(
                    title = stringResource(R.string.settings_high_res_images_title),
                    subtitle = stringResource(R.string.settings_high_res_images_subtitle),
                    checked = highResImages,
                    onCheckedChange = onHighResImagesChange,
                    icon = Icons.Rounded.Search
                )
            }

            item {
                SettingRow(
                    title = stringResource(R.string.settings_max_upload_quality_title),
                    subtitle = stringResource(R.string.settings_max_upload_quality_subtitle),
                    checked = maxUploadQuality,
                    onCheckedChange = onMaxUploadQualityChange,
                    icon = Icons.Rounded.Upload
                )
            }

            item {
                val deviceLabel = when (spoofDevice) {
                    "s26" -> stringResource(R.string.spoof_device_s26)
                    "pixel" -> stringResource(R.string.spoof_device_pixel)
                    else -> stringResource(R.string.spoof_device_off)
                }

                ClickableSettingRow(
                    title = "${stringResource(R.string.settings_spoof_device_title)} · $deviceLabel",
                    subtitle = stringResource(R.string.settings_spoof_device_subtitle),
                    icon = Icons.Rounded.Movie,
                    onClick = { showSpoofDeviceDialog = true }
                )
            }

            // --- CATEGORIA APP ---
            item {
                Text(
                    text = stringResource(R.string.settings_app_label),
                    fontFamily = MonoFamily,
                    fontSize = 11.sp,
                    color = accents.textMuted,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                )
            }

            item {
                SettingRow(
                    title = stringResource(R.string.settings_auto_check_updates_title),
                    subtitle = stringResource(R.string.settings_auto_check_updates_subtitle),
                    checked = autoCheckMappingUpdates,
                    onCheckedChange = onAutoCheckMappingUpdatesChange,
                    icon = Icons.Rounded.CloudSync
                )

                AnimatedVisibility(
                    visible = !autoCheckMappingUpdates,
                    enter = slideInVertically { -it } + expandVertically(animationSpec = spring(stiffness = Spring.StiffnessLow)) + fadeIn(),
                    exit = slideOutVertically { -it } + shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessLow)) + fadeOut()
                ) {
                    val btnTitle = when {
                        isUpdatingMapping || isCheckingUpdate -> stringResource(R.string.settings_updating_mapping)
                        mappingUpdateAvailable -> stringResource(R.string.compare_update_available_btn)
                        mappingUpToDate -> stringResource(R.string.compare_update_no_updates)
                        else -> stringResource(R.string.compare_check_updates_btn)
                    }

                    ClickableSettingRow(
                        title = btnTitle,
                        subtitle = "",
                        icon = if (isUpdatingMapping || isCheckingUpdate) Icons.Rounded.Sync else Icons.Rounded.CloudSync,
                        iconColor = if (mappingUpdateAvailable) accents.amber else accents.teal,
                        onClick = {
                            if (!isUpdatingMapping && !isCheckingUpdate) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onManualUpdateCheck()
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                SettingRow(
                    title = stringResource(R.string.settings_auto_check_app_updates_title),
                    subtitle = stringResource(R.string.settings_auto_check_app_updates_subtitle),
                    checked = autoCheckAppUpdates,
                    onCheckedChange = onAutoCheckAppUpdatesChange,
                    icon = Icons.Rounded.SystemUpdate
                )

                AnimatedVisibility(
                    visible = !autoCheckAppUpdates,
                    enter = slideInVertically { -it } + expandVertically(animationSpec = spring(stiffness = Spring.StiffnessLow)) + fadeIn(),
                    exit = slideOutVertically { -it } + shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessLow)) + fadeOut()
                ) {
                    val btnTitle = when {
                        isCheckingAppUpdate -> stringResource(R.string.settings_updating_mapping)
                        appUpdateAvailable -> stringResource(R.string.settings_app_update_available_btn)
                        appUpToDate -> stringResource(R.string.settings_app_update_no_updates)
                        else -> stringResource(R.string.settings_check_app_updates_btn)
                    }

                    ClickableSettingRow(
                        title = btnTitle,
                        subtitle = "",
                        icon = if (isCheckingAppUpdate) Icons.Rounded.Sync else Icons.Rounded.SystemUpdate,
                        iconColor = if (appUpdateAvailable) accents.amber else accents.teal,
                        onClick = {
                            if (!isCheckingAppUpdate) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onManualAppUpdateCheck()
                            }
                        }
                    )
                }
            }

            // --- CATEGORIA COMUNIDADE ---
            item {
                Text(
                    text = stringResource(R.string.settings_community_label),
                    fontFamily = MonoFamily,
                    fontSize = 11.sp,
                    color = accents.textMuted,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                )
            }

            item {
                ClickableSettingRow(
                    title = stringResource(R.string.settings_telegram_title),
                    subtitle = stringResource(R.string.settings_telegram_subtitle),
                    icon = ImageVector.vectorResource(R.drawable.ic_telegram),
                    iconSize = 26.dp,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        uriHandler.openUri("https://t.me/instaflowflagss")
                    }
                )
            }

            item {
                ClickableSettingRow(
                    title = stringResource(R.string.settings_github_title),
                    subtitle = stringResource(R.string.settings_github_subtitle),
                    icon = ImageVector.vectorResource(R.drawable.ic_github),
                    iconSize = 26.dp,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        uriHandler.openUri("https://github.com/ViictorNunes/FlagSync")
                    }
                )
            }

            if (isUnlocked) {
                item {
                    Text(
                        text = "DEBUG",
                        fontFamily = MonoFamily,
                        fontSize = 11.sp,
                        color = accents.teal,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                    )

                    ClickableSettingRow(
                        title = stringResource(R.string.settings_restart_tour_title),
                        subtitle = stringResource(R.string.settings_restart_tour_subtitle),
                        icon = Icons.Rounded.RestartAlt,
                        onClick = onRestartTour
                    )
                }
            }

        }

        if (showLanguageSheet) {
            ModalBottomSheet(
                onDismissRequest = { showLanguageSheet = false },
                sheetState = sheetState,
                dragHandle = { BottomSheetDefaults.DragHandle() },
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                LanguageSheetContent(
                    currentLanguage = currentLanguage,
                    onSelect = { lang ->
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            showLanguageSheet = false
                            onLanguageChange(lang)
                        }
                    }
                )
            }
        }

        if (showReelQualityDialog) {
            ReelQualityDialog(
                current = forceReelQuality,
                onDismiss = { showReelQualityDialog = false },
                onSelect = {
                    onForceReelQualityChange(it)
                    showReelQualityDialog = false
                }
            )
        }

        if (showSpoofDeviceDialog) {
            SpoofDeviceDialog(
                current = spoofDevice,
                onDismiss = { showSpoofDeviceDialog = false },
                onSelect = {
                    onSpoofDeviceChange(it)
                    showSpoofDeviceDialog = false
                    Toast.makeText(context, context.getString(R.string.spoof_device_toast), Toast.LENGTH_LONG).show()
                }
            )
        }
    }
}

@Composable
private fun PaletteOptionCard(
    palette: AccentPalette,
    isSelected: Boolean,
    onSelect: () -> Unit,
    customColor: String? = null,
    modifier: Modifier = Modifier
) {
    val accents = LocalAccentColors.current
    val effectiveColor = if (palette == AccentPalette.CUSTOM && customColor != null) {
        try { Color(android.graphics.Color.parseColor(customColor)) } catch(_: Exception) { accents.teal }
    } else accents.teal

    val borderColor by animateColorAsState(
        targetValue = if (isSelected) effectiveColor else MaterialTheme.colorScheme.outline.copy(alpha = if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) 0.8f else 0.2f),
        label = "borderColor"
    )
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) effectiveColor.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
        label = "bgColor"
    )

    Column(
        modifier = modifier
            .height(82.dp) // Rígido para estabilidade total
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(1.5.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable { onSelect() }
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .height(30.dp)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            if (palette == AccentPalette.CUSTOM) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .shadow(2.dp, androidx.compose.foundation.shape.CircleShape)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .border(1.2.dp, Color.White, androidx.compose.foundation.shape.CircleShape)
                        .background(
                            brush = Brush.sweepGradient(
                                listOf(Color.Red, Color.Magenta, Color.Blue, Color.Cyan, Color.Green, Color.Yellow, Color.Red)
                            )
                        )
                )
            } else if (palette == AccentPalette.MATERIAL_YOU) {
                Icon(
                    imageVector = Icons.Rounded.Palette,
                    contentDescription = null,
                    tint = if (isSelected) effectiveColor else accents.textMuted,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val colors = when (palette) {
                        AccentPalette.DEFAULT -> listOf(com.flag.sync.ui.theme.TealDark, com.flag.sync.ui.theme.AmberDark, com.flag.sync.ui.theme.VioletDark)
                        AccentPalette.VIBRANT -> listOf(com.flag.sync.ui.theme.BlueVibrant, com.flag.sync.ui.theme.GoldVibrant, com.flag.sync.ui.theme.VioletDark)
                        AccentPalette.DEEP -> listOf(com.flag.sync.ui.theme.RedDeepVibrant, com.flag.sync.ui.theme.BlueMidnightVibrant, com.flag.sync.ui.theme.OliveDeepVibrant)
                        AccentPalette.MONOCHROME -> listOf(Color.White, Color(0xFFBDBDBD), Color(0xFF757575))
                        else -> emptyList()
                    }
                    colors.forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .shadow(1.dp, androidx.compose.foundation.shape.CircleShape)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(color)
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(palette.labelRes),
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.2).sp,
            maxLines = 1,
            softWrap = false,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ThemeOptionCard(
    theme: AppTheme,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accents = LocalAccentColors.current

    val borderColor by animateColorAsState(
        targetValue = if (isSelected) accents.teal else MaterialTheme.colorScheme.outline.copy(alpha = if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) 0.8f else 0.2f),
        label = "borderColor"
    )
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) accents.teal.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
        label = "bgColor"
    )
    val borderWidth = 1.5.dp

    val icon = when (theme) {
        AppTheme.LIGHT -> Icons.Rounded.LightMode
        AppTheme.DARK -> Icons.Rounded.DarkMode
    }

    val label = when (theme) {
        AppTheme.LIGHT -> stringResource(R.string.theme_light)
        AppTheme.DARK -> stringResource(R.string.theme_dark)
    }

    Column(
        modifier = modifier
            .height(94.dp) // Rígido para estabilidade total
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(borderWidth, borderColor, RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onSelect() }
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isSelected) accents.teal else accents.textMuted,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun LanguageSheetContent(
    currentLanguage: String,
    onSelect: (String) -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 40.dp, start = 20.dp, end = 20.dp, top = 10.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_language_label),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.padding(bottom = 20.dp)
        )

        LanguageItem(
            label = "🌐 " + stringResource(R.string.settings_lang_system) + " (${LocaleUtils.getSystemLanguageName(context)})",
            isSelected = currentLanguage.isEmpty(),
            onClick = { onSelect("") }
        )
        LanguageItem(
            label = "🇧🇷 " + stringResource(R.string.settings_lang_pt),
            isSelected = currentLanguage.startsWith("pt"),
            onClick = { onSelect("pt") }
        )
        LanguageItem(
            label = "🇪🇸 " + stringResource(R.string.settings_lang_es),
            isSelected = currentLanguage.startsWith("es"),
            onClick = { onSelect("es") }
        )
        LanguageItem(
            label = "🇺🇸 " + stringResource(R.string.settings_lang_en),
            isSelected = currentLanguage.startsWith("en"),
            onClick = { onSelect("en") }
        )
    }
}

@Composable
private fun LanguageItem(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val accents = LocalAccentColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) accents.teal.copy(alpha = 0.1f) else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) accents.teal else MaterialTheme.colorScheme.onSurface
        )
        if (isSelected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = accents.teal,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun ClickableSettingRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconSize: androidx.compose.ui.unit.Dp = 20.dp,
    iconColor: Color? = null,
    onClick: () -> Unit
) {
    val accents = LocalAccentColors.current
    val haptic = LocalHapticFeedback.current
    val tint = iconColor ?: accents.teal
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 82.dp) // Maintain consistent row height
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(tint.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
        }
        Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Normal
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    color = accents.textMuted,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Justify,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector? = null
) {
    val accents = LocalAccentColors.current
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 82.dp) // Trava de altura para evitar saltos
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .padding(end = 16.dp)
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(accents.textMuted.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = accents.textMuted, modifier = Modifier.size(18.dp))
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = subtitle,
                color = accents.textMuted,
                fontSize = 12.sp,
                textAlign = TextAlign.Justify,
                lineHeight = 16.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        val useDarkThumb = accents.teal.luminance() > 0.5f
        Switch(
            checked = checked,
            onCheckedChange = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onCheckedChange(it)
            },
            thumbContent = {
                Icon(
                    imageVector = if (checked) Icons.Rounded.Check else Icons.Rounded.Close,
                    contentDescription = null,
                    modifier = Modifier.size(SwitchDefaults.IconSize)
                )
            },
            colors = SwitchDefaults.colors(
                checkedThumbColor = if (useDarkThumb) accents.accentDark else Color.White,
                checkedTrackColor = accents.teal,
                checkedIconColor = if (useDarkThumb) accents.teal else accents.teal,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = accents.textMuted,
                uncheckedTrackColor = Color.Transparent,
                uncheckedIconColor = MaterialTheme.colorScheme.surface,
                uncheckedBorderColor = MaterialTheme.colorScheme.outline
            )
        )
    }
}

@Composable
private fun ColorPickerDialog(
    initialColor: String,
    onDismiss: () -> Unit,
    onColorSelected: (String) -> Unit
) {
    var hsv by remember {
        val hsvOut = FloatArray(3)
        // initialColor comes from persisted prefs; fall back to a safe default if it was
        // ever saved in an invalid format, instead of crashing on dialog open.
        val safeColor = try {
            android.graphics.Color.parseColor(initialColor)
        } catch (_: Exception) {
            android.graphics.Color.parseColor("#4FD8C9")
        }
        android.graphics.Color.colorToHSV(safeColor, hsvOut)
        mutableStateOf(Triple(hsvOut[0], hsvOut[1], hsvOut[2]))
    }

    var hexText by remember { mutableStateOf(initialColor.removePrefix("#")) }
    val accents = LocalAccentColors.current

    fun updateHexFromHsv(h: Float, s: Float, v: Float) {
        val colorInt = android.graphics.Color.HSVToColor(floatArrayOf(h, s, v))
        hexText = String.format("%06X", (0xFFFFFF and colorInt))
    }

    // Only a well-formed 6-digit hex can be confirmed; this is what prevents an invalid
    // value from ever being persisted as customColorHex (which would crash the dialog
    // on its next open, since the initial color is parsed unconditionally).
    val isHexValid = hexText.length == 6 && hexText.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        confirmButton = {
            Button(
                onClick = { if (isHexValid) onColorSelected("#" + hexText.uppercase()) },
                enabled = isHexValid,
                colors = ButtonDefaults.buttonColors(
                    containerColor = accents.teal,
                    contentColor = if (accents.teal.luminance() > 0.5f) Color.Black else Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("OK", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.edit_dialog_cancel))
            }
        },
        title = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val currentSelectedColor = Color.hsv(hsv.first, hsv.second, hsv.third)

                    fun deriveColor(h: Float, s: Float, v: Float, hueShift: Float): Color {
                        val hsvOut = floatArrayOf((h + hueShift) % 360f, s, v)
                        return Color(android.graphics.Color.HSVToColor(hsvOut))
                    }

                    val color2 = deriveColor(hsv.first, hsv.second, hsv.third, 180f)
                    val color3 = deriveColor(hsv.first, hsv.second, hsv.third, 30f)

                    listOf(currentSelectedColor, color2, color3).forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(color)
                                .border(2.dp, Color.White.copy(alpha = 0.9f), androidx.compose.foundation.shape.CircleShape)
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.Black)
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                var svSize by remember { mutableStateOf(IntSize.Zero) }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.hsv(hsv.first, 1f, 1f))
                        .onSizeChanged { svSize = it }
                        .pointerInput(Unit) {
                            detectDragGestures { change, _ ->
                                val s = (change.position.x / svSize.width).coerceIn(0f, 1f)
                                val v = 1f - (change.position.y / svSize.height).coerceIn(0f, 1f)
                                hsv = Triple(hsv.first, s, v)
                                updateHexFromHsv(hsv.first, s, v)
                            }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                val s = (offset.x / svSize.width).coerceIn(0f, 1f)
                                val v = 1f - (offset.y / svSize.height).coerceIn(0f, 1f)
                                hsv = Triple(hsv.first, s, v)
                                updateHexFromHsv(hsv.first, s, v)
                            }
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawRect(
                            brush = Brush.linearGradient(
                                colors = listOf(Color.White, Color.Transparent),
                                start = Offset.Zero,
                                end = Offset(size.width, 0f)
                            )
                        )
                        drawRect(
                            brush = Brush.linearGradient(
                                colors = listOf(Color.Transparent, Color.Black),
                                start = Offset.Zero,
                                end = Offset(0f, size.height)
                            )
                        )

                        val posX = hsv.second * size.width
                        val posY = (1f - hsv.third) * size.height

                        val cursorRadius = 8.dp.toPx()
                        val clampedX = posX.coerceIn(cursorRadius, size.width - cursorRadius)
                        val clampedY = posY.coerceIn(cursorRadius, size.height - cursorRadius)

                        drawCircle(
                            color = Color.White,
                            radius = cursorRadius,
                            center = Offset(clampedX, clampedY),
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                var hueSize by remember { mutableStateOf(IntSize.Zero) }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .onSizeChanged { hueSize = it }
                        .pointerInput(Unit) {
                            detectDragGestures { change, _ ->
                                val h = (change.position.x / hueSize.width).coerceIn(0f, 1f) * 360f
                                hsv = Triple(h, hsv.second, hsv.third)
                                updateHexFromHsv(h, hsv.second, hsv.third)
                            }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                val h = (offset.x / hueSize.width).coerceIn(0f, 1f) * 360f
                                hsv = Triple(h, hsv.second, hsv.third)
                                updateHexFromHsv(h, hsv.second, hsv.third)
                            }
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val colors = listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)
                        drawRect(
                            brush = Brush.linearGradient(
                                colors = colors,
                                start = Offset.Zero,
                                end = Offset(size.width, 0f)
                            )
                        )

                        val posX = (hsv.first / 360f) * size.width
                        val cursorRadius = 10.dp.toPx()
                        val clampedX = posX.coerceIn(cursorRadius, size.width - cursorRadius)

                        drawCircle(
                            color = Color.White,
                            radius = cursorRadius,
                            center = Offset(clampedX, size.height / 2),
                            style = Stroke(width = 3.dp.toPx())
                        )
                        drawCircle(
                            color = Color.hsv(hsv.first, 1f, 1f),
                            radius = 7.dp.toPx(),
                            center = Offset(clampedX, size.height / 2)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = hexText,
                    onValueChange = {
                        if (it.length <= 6) {
                            hexText = it
                            try {
                                if (it.length == 6) {
                                    val colorInt = android.graphics.Color.parseColor("#$it")
                                    val out = FloatArray(3)
                                    android.graphics.Color.colorToHSV(colorInt, out)
                                    hsv = Triple(out[0], out[1], out[2])
                                }
                            } catch (_: Exception) {}
                        }
                    },
                    label = { Text("Hex Code", color = Color.Gray) },
                    prefix = { Text("#", color = Color.White) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFamily, color = Color.White),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.DarkGray,
                        unfocusedBorderColor = Color.DarkGray,
                        focusedContainerColor = Color(0xFF1A1A1A),
                        unfocusedContainerColor = Color(0xFF1A1A1A)
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                val selectedColor = Color.hsv(hsv.first, hsv.second, hsv.third)
                val isRisky = selectedColor.luminance() > 0.85f || selectedColor.luminance() < 0.15f

                AnimatedVisibility(
                    visible = isRisky,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Row(
                        modifier = Modifier
                            .padding(top = 16.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Red.copy(alpha = 0.15f))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Warning,
                            contentDescription = null,
                            tint = Color.Red,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(R.string.settings_custom_color_warning),
                            color = Color.White,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun ReelQualityDialog(
    current: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    val accents = LocalAccentColors.current
    val options = listOf(
        0 to stringResource(R.string.quality_auto),
        360 to stringResource(R.string.quality_360),
        480 to stringResource(R.string.quality_480),
        720 to stringResource(R.string.quality_720),
        1080 to stringResource(R.string.quality_1080),
        Int.MAX_VALUE to stringResource(R.string.quality_max)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.settings_force_reels_quality_title),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                options.forEach { (value, label) ->
                    val isSelected = current == value
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelect(value) }
                            .padding(vertical = 10.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { onSelect(value) },
                            colors = RadioButtonDefaults.colors(selectedColor = accents.teal)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.edit_dialog_cancel))
            }
        },
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
private fun SpoofDeviceDialog(
    current: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    val accents = LocalAccentColors.current
    val options = listOf(
        "" to stringResource(R.string.spoof_device_off),
        "s26" to stringResource(R.string.spoof_device_s26),
        "pixel" to stringResource(R.string.spoof_device_pixel)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.settings_spoof_device_title),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                options.forEach { (value, label) ->
                    val isSelected = current == value
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelect(value) }
                            .padding(vertical = 10.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { onSelect(value) },
                            colors = RadioButtonDefaults.colors(selectedColor = accents.teal)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.edit_dialog_cancel))
            }
        },
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.surface
    )
}
