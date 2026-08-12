 /*
  * Copyright (C) 2026 ViictorNunes (https://github.com/ViictorNunes/FlagSync).
  * Licensed under the GNU General Public License v3.0.
  */


package com.flag.sync.logic

import android.util.Log
import com.flag.sync.R
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

private const val TAG = "BackupConverter"

sealed class ConvertOutcome {
    data class Success(
        val data: Map<String, Any?>,
        val detectedFormat: String,
        val flagCount: Int,
    ) : ConvertOutcome()

    data class Error(val resId: Int, val arg: String? = null) : ConvertOutcome()
}

object BackupConverter {

    fun convert(fileName: String, rawText: String): ConvertOutcome {
        val trimmed = rawText.trimStart()

        if (rawText.startsWith("bplist")) {
            return ConvertOutcome.Error(R.string.converter_error_bplist)
        }

        try {
            val parsed = JsonDiff.parse(trimmed)
            if (parsed is Map<*, *>) {
                @Suppress("UNCHECKED_CAST")
                val clean = stripBackupMetadata(parsed as Map<String, Any?>)
                return ConvertOutcome.Success(clean, "JSON", clean.size)
            }
        } catch (e: Exception) {
            // Not valid JSON; falls through to the XML/plist attempt below.
            Log.d(TAG, "Input is not JSON, trying plist XML", e)
        }

        if (trimmed.startsWith("<?xml") || trimmed.contains("<plist")) {
            return try {
                val parsed = parsePlistXmlToMap(trimmed)
                val clean = stripBackupMetadata(parsed)
                ConvertOutcome.Success(clean, "Plist XML", clean.size)
            } catch (e: Exception) {
                ConvertOutcome.Error(R.string.converter_error_xml, e.message)
            }
        }

        return ConvertOutcome.Error(R.string.converter_error_generic, fileName)
    }

    private fun stripBackupMetadata(map: Map<String, Any?>): Map<String, Any?> {
        val clean = LinkedHashMap(map)
        clean.remove("manifest_version")
        clean.remove("info")
        clean.remove("_flag_sync_info")

        val backup = clean["backup"]
        if (backup is Map<*, *>) {
            @Suppress("UNCHECKED_CAST")
            val backupMap = LinkedHashMap(backup as Map<String, Any?>)
            backupMap.remove("manifest_version")
            backupMap.remove("info")
            backupMap.remove("_flag_sync_info")
            return backupMap
        }
        return clean
    }

    private fun parsePlistXmlToMap(xml: String): LinkedHashMap<String, Any?> {
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xml))
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "dict") {
                return parseDictToMap(parser)
            }
            event = parser.next()
        }
        throw IllegalArgumentException("Não encontrei um <dict> raiz no plist.")
    }

    private fun parseDictToMap(parser: XmlPullParser): LinkedHashMap<String, Any?> {
        val map = LinkedHashMap<String, Any?>()
        var event = parser.next()
        var pendingKey: String? = null
        while (!(event == XmlPullParser.END_TAG && parser.name == "dict")) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "key" -> pendingKey = parser.nextText()
                    else -> {
                        val value = parseValue(parser)
                        if (pendingKey != null) {
                            map[pendingKey] = value
                            pendingKey = null
                        }
                    }
                }
            }
            event = parser.next()
        }
        return map
    }

    private fun parseValue(parser: XmlPullParser): Any? {
        return when (parser.name) {
            "dict" -> parseDictToMap(parser)
            "array" -> parseArray(parser)
            "string", "date", "data" -> parser.nextText()
            "integer" -> parser.nextText().trim().toLongOrNull() ?: 0L
            "real" -> parser.nextText().trim().toDoubleOrNull() ?: 0.0
            "true" -> true.also { skipToEndTag(parser, "true") }
            "false" -> false.also { skipToEndTag(parser, "false") }
            else -> null
        }
    }

    private fun parseArray(parser: XmlPullParser): org.json.JSONArray {
        val arr = org.json.JSONArray()
        var event = parser.next()
        while (!(event == XmlPullParser.END_TAG && parser.name == "array")) {
            if (event == XmlPullParser.START_TAG) {
                arr.put(parseValue(parser))
            }
            event = parser.next()
        }
        return arr
    }

    private fun skipToEndTag(parser: XmlPullParser, tag: String) {
        var event = parser.eventType
        while (!(event == XmlPullParser.END_TAG && parser.name == tag)) {
            event = parser.next()
        }
    }
}