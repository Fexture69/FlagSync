 /*
  * Copyright (C) 2026 ViictorNunes (https://github.com/ViictorNunes/FlagSync).
  * Licensed under the GNU General Public License v3.0.
  */


package com.flag.sync.utils

import android.app.LocaleManager
import android.content.Context
import android.content.res.Resources
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.ConfigurationCompat
import androidx.core.os.LocaleListCompat
import java.util.Locale

object LocaleUtils {
    fun setLocale(languageCode: String) {
        val appLocale: LocaleListCompat = if (languageCode.isEmpty()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(languageCode)
        }
        AppCompatDelegate.setApplicationLocales(appLocale)
    }

    fun getCurrentLocale(): String {
        return AppCompatDelegate.getApplicationLocales().toLanguageTags()
    }

    /**
     * Retorna o nome do idioma REAL configurado no sistema operacional do
     * aparelho — e não o idioma que o app está usando no momento (que pode
     * estar sobrescrito pelo usuário nas configurações do app).
     *
     * Precisa de um Context porque, a partir do Android 13 (API 33), o
     * próprio sistema separa "idioma do app" (LocaleManager.applicationLocales)
     * de "idioma do sistema" (LocaleManager.systemLocales). É essa segunda
     * fonte que consultamos aqui — Resources.getSystem().configuration
     * sozinho pode, em certos cenários, refletir o idioma sobrescrito do
     * app em vez do idioma real do aparelho, causando o bug de mostrar
     * "Sistema (Inglês)" quando o celular na verdade está em Português.
     */
    fun getSystemLanguageName(context: Context): String {
        val systemLocale = getRealSystemLocale(context)
        return systemLocale
            .getDisplayLanguage(Locale.getDefault())
            .replaceFirstChar { it.uppercase() }
    }

    private fun getRealSystemLocale(context: Context): Locale {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = context.getSystemService(LocaleManager::class.java)
            val systemLocales = localeManager?.systemLocales
            if (systemLocales != null && !systemLocales.isEmpty) {
                return systemLocales[0] ?: Locale.getDefault()
            }
        }
        val systemLocales = ConfigurationCompat.getLocales(Resources.getSystem().configuration)
        return if (!systemLocales.isEmpty) {
            systemLocales[0] ?: Locale.getDefault()
        } else {
            Locale.getDefault()
        }
    }
}