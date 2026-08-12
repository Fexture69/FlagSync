 /*
  * Copyright (C) 2026 ViictorNunes (https://github.com/ViictorNunes/FlagSync).
  * Licensed under the GNU General Public License v3.0.
  */


package com.flag.sync.utils

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

fun readTextFromUri(context: Context, uri: Uri): String {
    return context.contentResolver.openInputStream(uri)?.use { stream ->
        stream.bufferedReader(Charsets.UTF_8).readText()
    } ?: throw IllegalStateException("Não foi possível abrir o arquivo selecionado.")
}

fun displayNameFromUri(context: Context, uri: Uri): String {
    return DocumentFile.fromSingleUri(context, uri)?.name ?: uri.lastPathSegment ?: "arquivo"
}

fun writeTextToUri(context: Context, uri: Uri, content: String) {
    context.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
        stream.write(content.toByteArray(Charsets.UTF_8))
    } ?: throw IllegalStateException("Não foi possível salvar o arquivo.")
}