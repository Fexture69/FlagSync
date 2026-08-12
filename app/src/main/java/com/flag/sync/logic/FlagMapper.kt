 /*
  * Copyright (C) 2026 ViictorNunes (https://github.com/ViictorNunes/FlagSync).
  * Licensed under the GNU General Public License v3.0.
  */


package com.flag.sync.logic

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "FlagMapper"

class FlagMapper(rawJson: String) {

    data class MappingEntry(val name: String, val params: Map<String, String>)

    private val mapping = mutableMapOf<String, MappingEntry>()

    init {
        try {
            val parsed = if (rawJson.startsWith("[")) JSONArray(rawJson) else JSONObject(rawJson)
            if (parsed is JSONArray) {
                // Optimized loop with local references
                val count = parsed.length()
                for (i in 0 until count) {
                    val entry = parsed.optString(i) ?: continue
                    val firstColon = entry.indexOf(':')
                    if (firstColon == -1) continue

                    val id = entry.substring(0, firstColon).trim()
                    if (id.isEmpty()) continue

                    // Further split efficiently
                    val remainder = entry.substring(firstColon + 1)
                    val parts = remainder.split(":")
                    if (parts.isEmpty()) continue

                    val name = parts[0]
                    val params = if (parts.size > 1) {
                        val pMap = mutableMapOf<String, String>()
                        for (j in 1 until parts.size - 1 step 2) {
                            pMap[parts[j]] = parts[j + 1]
                        }
                        pMap
                    } else emptyMap()

                    mapping[id] = MappingEntry(name, params)
                }
            } else if (parsed is JSONObject) {
                for (key in parsed.keys()) {
                    val bare = key.removeSuffix(":")
                    val value = parsed.get(key)
                    val label = extractLabel(value) ?: value.toString()
                    mapping[bare] = MappingEntry(label, emptyMap())
                }
            }
        } catch (e: Exception) {
            // Mapping stays empty; log so a bad mapping file is diagnosable in the field.
            Log.w(TAG, "Failed to parse mapping JSON", e)
        }
    }

    private fun extractLabel(v: Any?): String? {
        if (v is String) return v
        if (v is JSONObject) {
            return v.optString("name").takeIf { it.isNotEmpty() }
                ?: v.optString("label").takeIf { it.isNotEmpty() }
                ?: v.optString("title").takeIf { it.isNotEmpty() }
        }
        return null
    }

    /**
     * Extracts ID and embedded name (e.g., "id:flag_name").
     */
    private fun splitPath(path: String): Pair<String, String?> {
        val bare = path.removeSuffix(":")
        val colonIndex = bare.indexOf(':')
        return if (colonIndex >= 0) {
            val id = bare.substring(0, colonIndex)
            val embeddedName = bare.substring(colonIndex + 1)
            id to embeddedName.ifEmpty { null }
        } else {
            bare to null
        }
    }

    fun getDisplayName(path: String): String {
        val (id, embeddedName) = splitPath(path)
        return mapping[id]?.name ?: embeddedName ?: path
    }

    fun getParamLabel(path: String, index: String): String {
        val (id, _) = splitPath(path)
        return mapping[id]?.params?.get(index) ?: "#$index"
    }

    fun size() = mapping.size
}