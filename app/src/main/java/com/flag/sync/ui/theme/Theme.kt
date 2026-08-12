 /*
  * Copyright (C) 2026 ViictorNunes (https://github.com/ViictorNunes/FlagSync).
  * Licensed under the GNU General Public License v3.0.
  */


package com.flag.sync.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.flag.sync.model.AppTheme
import com.flag.sync.model.AccentPalette
import com.flag.sync.utils.getHighContrastContentColor

@Immutable
data class AccentColors(
    val teal: Color,
    val tealDim: Color,
    val amber: Color,
    val amberDim: Color,
    val violet: Color,
    val violetDim: Color,
    val success: Color,
    val danger: Color,
    val accentDark: Color,
    val textMuted: Color,
)

val LocalAccentColors = staticCompositionLocalOf {
    AccentColors(
        teal = TealDark,
        tealDim = TealDimDark,
        amber = AmberDark,
        amberDim = AmberDimDark,
        violet = VioletDark,
        violetDim = VioletDimDark,
        success = SuccessDark,
        danger = DangerDark,
        accentDark = TealLight,
        textMuted = TextMutedDark
    )
}

private val DarkColors = darkColorScheme(
    primary = TealDark,
    onPrimary = Color.Black,
    secondary = AmberDark,
    onSecondary = Color.Black,
    tertiary = VioletDark,
    onTertiary = Color.Black,
    background = BgDeep,
    surface = BgPanel,
    surfaceVariant = BgPanel2,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    outline = BorderColor,
    error = DangerDark
)

private val LightColors = lightColorScheme(
    primary = TealLight,
    onPrimary = Color.White,
    secondary = AmberLight,
    onSecondary = Color.White,
    tertiary = VioletLight,
    onTertiary = Color.White,
    background = BgDeepLight,
    surface = SurfaceWhite,
    surfaceVariant = BgPanel2Light,
    onBackground = TextPrimaryLight,
    onSurface = TextPrimaryLight,
    outline = BorderColorLight,
    error = DangerLight
)

@Composable
fun FlagSyncTheme(
    appTheme: AppTheme = AppTheme.DARK,
    accentPalette: AccentPalette = AccentPalette.DEFAULT,
    customColorHex: String = "#4FD8C9",
    isAmoled: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val isDark = when (appTheme) {
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
    }

    val dynamicColors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else null

    val baseColors = if (accentPalette == AccentPalette.MATERIAL_YOU && dynamicColors != null) {
        dynamicColors
    } else {
        (if (isDark) DarkColors else LightColors)
    }

    val colors = if (isDark && isAmoled) {
        baseColors.copy(
            background = Color.Black,
            surface = Color(0xFF121212),
            surfaceVariant = Color(0xFF1E1E1E),
            outline = Color(0xFF333333)
        )
    } else baseColors

    val accentColors = when {
        accentPalette == AccentPalette.MATERIAL_YOU && dynamicColors != null -> {
            AccentColors(
                teal = dynamicColors.primary,
                tealDim = dynamicColors.primaryContainer,
                amber = dynamicColors.secondary,
                amberDim = dynamicColors.secondaryContainer,
                violet = dynamicColors.tertiary,
                violetDim = dynamicColors.tertiaryContainer,
                success = if (isDark) SuccessDark else SuccessLight,
                danger = if (isDark) DangerDark else DangerLight,
                accentDark = if (isDark) {
                    dynamicColors.onPrimary 
                } else {
                    // Derive a dark, high-contrast version of primary for light surfaces
                    Color(android.graphics.Color.HSVToColor(FloatArray(3).apply {
                        android.graphics.Color.colorToHSV(dynamicColors.primary.toArgb(), this)
                        this[2] = minOf(this[2], 0.35f) // Force dark
                        this[1] = maxOf(this[1], 0.4f)  // Ensure visible saturation
                    }))
                },
                textMuted = if (isDark) TextMutedDark else TextMutedLight
            )
        }
        accentPalette == AccentPalette.VIBRANT -> {
            if (isDark) {
                AccentColors(
                    teal = BlueVibrant,
                    tealDim = BlueDimVibrant,
                    amber = GoldVibrant,
                    amberDim = GoldDimVibrant,
                    violet = VioletDark, 
                    violetDim = VioletDimDark,
                    success = SuccessDark,
                    danger = DangerDark,
                    accentDark = BlueMidnight,
                    textMuted = TextMutedDark
                )
            } else {
                AccentColors(
                    teal = BlueVibrantLight,
                    tealDim = BlueDimVibrantLight,
                    amber = GoldVibrantLight,
                    amberDim = GoldDimVibrantLight,
                    violet = VioletLight, 
                    violetDim = VioletDimLight,
                    success = SuccessLight,
                    danger = DangerLight,
                    accentDark = BlueMidnight,
                    textMuted = TextMutedLight
                )
            }
        }
        accentPalette == AccentPalette.DEEP -> {
            if (isDark) {
                AccentColors(
                    teal = RedDeepVibrant,
                    tealDim = RedDimDeepVibrant,
                    amber = BlueMidnightVibrant,
                    amberDim = BlueDimMidnightVibrant,
                    violet = OliveDeepVibrant,
                    violetDim = OliveDimDeepVibrant,
                    success = SuccessDark,
                    danger = DangerDark,
                    accentDark = RedDeep,
                    textMuted = TextMutedDark
                )
            } else {
                AccentColors(
                    teal = RedDeepLight,
                    tealDim = RedDimDeepLight,
                    amber = BlueMidnightLight,
                    amberDim = BlueDimMidnightLight,
                    violet = OliveDeepLight,
                    violetDim = OliveDeepLight,
                    success = SuccessLight,
                    danger = DangerLight,
                    accentDark = RedDeep,
                    textMuted = TextMutedLight
                )
            }
        }
        accentPalette == AccentPalette.MONOCHROME -> {
            if (isDark) {
                AccentColors(
                    teal = Color.White,
                    tealDim = Color(0xFF2C2C2C),
                    amber = Color(0xFFBDBDBD),
                    amberDim = Color(0xFF2C2C2C),
                    violet = Color(0xFF757575),
                    violetDim = Color(0xFF2C2C2C),
                    success = SuccessDark,
                    danger = DangerDark,
                    accentDark = Color.Black,
                    textMuted = TextMutedDark
                )
            } else {
                AccentColors(
                    teal = GrayLight,
                    tealDim = GrayDimLight,
                    amber = GrayMedium,
                    amberDim = GrayDimLight,
                    violet = Color.Black,
                    violetDim = GrayDimLight,
                    success = SuccessLight,
                    danger = DangerLight,
                    accentDark = Color.Black,
                    textMuted = TextMutedLight
                )
            }
        }
        accentPalette == AccentPalette.CUSTOM -> {
            val rawBase = try { Color(android.graphics.Color.parseColor(customColorHex)) } catch (_: Exception) { TealDark }
            val base = when {
                isDark && rawBase.luminance() < 0.1f -> rawBase.copy(alpha = 1f).let { 
                    Color(android.graphics.Color.HSVToColor(FloatArray(3).apply {
                        android.graphics.Color.colorToHSV(it.toArgb(), this)
                        this[2] = maxOf(this[2], 0.25f)
                    }))
                }
                !isDark && rawBase.luminance() > 0.9f -> rawBase.copy(alpha = 1f).let {
                    Color(android.graphics.Color.HSVToColor(FloatArray(3).apply {
                        android.graphics.Color.colorToHSV(it.toArgb(), this)
                        this[2] = minOf(this[2], 0.85f)
                    }))
                }
                else -> rawBase
            }
            val dim = base.copy(alpha = 0.15f)
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(base.toArgb(), hsv)
            val isGrayscale = hsv[1] < 0.05f

            val complementary = if (isGrayscale) {
                if (isDark) Color.Gray else Color.DarkGray
            } else {
                Color(android.graphics.Color.HSVToColor(FloatArray(3).apply {
                    android.graphics.Color.colorToHSV(base.toArgb(), this)
                    this[0] = (this[0] + 180) % 360
                }))
            }
            val analogous = if (isGrayscale) {
                if (isDark) Color.LightGray else Color.Gray
            } else {
                Color(android.graphics.Color.HSVToColor(FloatArray(3).apply {
                    android.graphics.Color.colorToHSV(base.toArgb(), this)
                    this[0] = (this[0] + 30) % 360
                }))
            }
            val accentDarkColor = Color(android.graphics.Color.HSVToColor(FloatArray(3).apply {
                android.graphics.Color.colorToHSV(base.toArgb(), this)
                this[2] = minOf(this[2], 0.35f)
                this[1] = maxOf(this[1], 0.4f)
            }))

            AccentColors(
                teal = base,
                tealDim = dim,
                amber = complementary,
                amberDim = complementary.copy(alpha = 0.15f),
                violet = analogous,
                violetDim = analogous.copy(alpha = 0.15f),
                success = SuccessDark,
                danger = DangerDark,
                accentDark = accentDarkColor,
                textMuted = if (isDark) TextMutedDark else TextMutedLight
            )
        }
        else -> { // DEFAULT
            if (isDark) {
                AccentColors(
                    teal = TealDark,
                    tealDim = TealDimDark,
                    amber = AmberDark,
                    amberDim = AmberDimDark,
                    violet = VioletDark,
                    violetDim = VioletDimDark,
                    success = SuccessDark,
                    danger = DangerDark,
                    accentDark = TealDeep,
                    textMuted = TextMutedDark
                )
            } else {
                AccentColors(
                    teal = TealLight,
                    tealDim = TealDimLight,
                    amber = AmberLight,
                    amberDim = AmberDimLight,
                    violet = VioletLight,
                    violetDim = VioletDimLight,
                    success = SuccessLight,
                    danger = DangerLight,
                    accentDark = TealDeep,
                    textMuted = TextMutedLight
                )
            }
        }
    }

    val finalColors = colors.copy(
        primary = accentColors.teal,
        onPrimary = getHighContrastContentColor(accentColors.teal),
        secondary = accentColors.amber,
        onSecondary = getHighContrastContentColor(accentColors.amber),
        tertiary = accentColors.violet,
        onTertiary = getHighContrastContentColor(accentColors.violet)
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        findActivity(context)?.let { activity ->
            val window = activity.window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !isDark
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
        }
    }

    CompositionLocalProvider(LocalAccentColors provides accentColors) {
        MaterialTheme(
            colorScheme = finalColors,
            typography = FlagSyncTypography,
            content = content
        )
    }
}

private fun findActivity(context: Context): Activity? {
    var currentContext = context
    while (currentContext is ContextWrapper) {
        if (currentContext is Activity) return currentContext
        currentContext = currentContext.baseContext
    }
    return null
}
