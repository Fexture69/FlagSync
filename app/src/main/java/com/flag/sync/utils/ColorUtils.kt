 /*
  * Copyright (C) 2026 ViictorNunes (https://github.com/ViictorNunes/FlagSync).
  * Licensed under the GNU General Public License v3.0.
  */


package com.flag.sync.utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import android.graphics.Color as AndroidColor

/**
 * Detecta se uma cor é "clara ou cinza" para aplicar estilos premium (ícones escuros + sombras internas).
 * Útil para adaptar o Material You do usuário se ele for próximo ao branco/cinza.
 */
fun Color.isLightOrGray(): Boolean {
    val lum = this.luminance()
    
    // Convert to HSV to check saturation
    val hsv = FloatArray(3)
    AndroidColor.colorToHSV(this.toArgb(), hsv)
    val saturation = hsv[1]

    return lum > 0.65f || saturation < 0.15f
}

/**
 * Returns a high-contrast content color (White or Black) for a given background.
 */
fun getHighContrastContentColor(backgroundColor: Color): Color {
    return if (backgroundColor.luminance() > 0.5f) Color.Black else Color.White
}
