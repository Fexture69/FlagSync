 /*
  * Copyright (C) 2026 ViictorNunes (https://github.com/ViictorNunes/FlagSync).
  * Licensed under the GNU General Public License v3.0.
  */


package com.flag.sync.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// O HTML usa Space Grotesk (corpo) e JetBrains Mono (labels/valores).
// Here we use the default system families; to use the original fonts,
// baixe os .ttf e coloque em res/font, depois troque FontFamily.Default.
val MonoFamily = FontFamily.Monospace

val FlagSyncTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        letterSpacing = (-0.2).sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelSmall = TextStyle(
        fontFamily = MonoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 1.sp
    )
)
