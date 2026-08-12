 /*
  * Copyright (C) 2026 ViictorNunes (https://github.com/ViictorNunes/FlagSync).
  * Licensed under the GNU General Public License v3.0.
  */


package com.flag.sync.logic

import com.flag.sync.model.CompareResult
import com.flag.sync.model.FlagDiff
import com.flag.sync.model.FlatFlag
import org.json.JSONArray
import org.json.JSONObject

object JsonDiff {

    fun parse(json: String): Any? {
        val trimmed = json.trim()
        if (trimmed.isEmpty()) return null
        return try {
            val (value, endIndex) = parseValue(trimmed, 0)
            val rest = trimmed.substring(endIndex).trim()
            if (rest.isNotEmpty()) {
                throw IllegalArgumentException(
                    "JSON contains unexpected content after main value at position $endIndex: \"$rest\""
                )
            }
            value
        } catch (e: Exception) {
            try {
                val fallback = if (trimmed.startsWith("{")) JSONObject(trimmed) else JSONArray(trimmed)
                normalizeOrgJson(fallback)
            } catch (fallbackError: Exception) {
                throw IllegalArgumentException(
                    "Failed to parse JSON. Manual parser: ${e.message}; fallback org.json: ${fallbackError.message}",
                    fallbackError
                )
            }
        }
    }

    private fun normalizeOrgJson(value: Any?): Any? {
        return when (value) {
            is JSONObject -> {
                val map = LinkedHashMap<String, Any?>()
                val keys = value.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    map[key] = normalizeOrgJson(value.get(key))
                }
                map
            }
            is JSONArray -> {
                val list = mutableListOf<Any?>()
                for (i in 0 until value.length()) {
                    list.add(normalizeOrgJson(value.get(i)))
                }
                list
            }
            JSONObject.NULL -> null
            else -> value
        }
    }

    private fun parseValue(json: String, start: Int): Pair<Any?, Int> {
        var i = skipWhitespace(json, start)
        if (i >= json.length) return null to i
        return when (val c = json[i]) {
            '{' -> parseObject(json, i)
            '[' -> parseArray(json, i)
            '"' -> parseString(json, i)
            't' -> if (json.startsWith("true", i)) true to i + 4 else invalidToken(json, i)
            'f' -> if (json.startsWith("false", i)) false to i + 5 else invalidToken(json, i)
            'n' -> if (json.startsWith("null", i)) null to i + 4 else invalidToken(json, i)
            '-' -> parseNumber(json, i)
            in '0'..'9' -> parseNumber(json, i)
            // Any other char is not a valid JSON token start. Throwing here (instead of
            // falling through to parseNumber) prevents parseObject/parseArray from looping
            // forever on malformed input, since parseNumber would otherwise return the same
            // index with zero characters consumed.
            else -> invalidToken(json, i)
        }
    }

    private fun invalidToken(json: String, i: Int): Nothing {
        throw IllegalArgumentException("Unexpected character '${json[i]}' at position $i")
    }

    private fun parseObject(json: String, start: Int): Pair<LinkedHashMap<String, Any?>, Int> {
        val map = LinkedHashMap<String, Any?>()
        var i = skipWhitespace(json, start + 1)
        if (i < json.length && json[i] == '}') return map to i + 1

        while (i < json.length) {
            val (key, nextI) = parseString(json, i)
            i = skipWhitespace(json, nextI)
            if (i < json.length && json[i] == ':') i = skipWhitespace(json, i + 1)
            val (value, afterValue) = parseValue(json, i)
            map[key as String] = value
            i = skipWhitespace(json, afterValue)
            if (i < json.length && json[i] == '}') return map to i + 1
            if (i < json.length && json[i] == ',') i = skipWhitespace(json, i + 1)
        }
        return map to i
    }

    private fun parseArray(json: String, start: Int): Pair<List<Any?>, Int> {
        val list = mutableListOf<Any?>()
        var i = skipWhitespace(json, start + 1)
        if (i < json.length && json[i] == ']') return list to i + 1
        while (i < json.length) {
            val (value, nextI) = parseValue(json, i)
            list.add(value)
            i = skipWhitespace(json, nextI)
            if (i < json.length && json[i] == ']') return list to i + 1
            if (i < json.length && json[i] == ',') i = skipWhitespace(json, i + 1)
        }
        return list to i
    }

    private fun parseString(json: String, start: Int): Pair<String, Int> {
        val sb = StringBuilder()
        var i = start + 1
        while (i < json.length && json[i] != '"') {
            if (json[i] == '\\' && i + 1 < json.length) {
                when (val escaped = json[i + 1]) {
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    '\\' -> sb.append('\\')
                    '"' -> sb.append('"')
                    else -> sb.append(escaped)
                }
                i += 2
            } else {
                sb.append(json[i])
                i++
            }
        }
        return sb.toString() to i + 1
    }

    private fun parseNumber(json: String, start: Int): Pair<Any?, Int> {
        var i = start
        while (i < json.length && (json[i].isDigit() || json[i] == '.' || json[i] == '-' || json[i] == 'e' || json[i] == 'E' || json[i] == '+')) i++
        val s = json.substring(start, i)
        val num = s.toLongOrNull() ?: s.toDoubleOrNull() ?: s
        return num to i
    }

    private fun skipWhitespace(json: String, start: Int): Int {
        var i = start
        while (i < json.length && json[i].isWhitespace()) i++
        return i
    }

    fun flatten(json: Any?): LinkedHashMap<String, Any?> {
        val out = LinkedHashMap<String, Any?>()
        flattenValueInto(json, "", out)
        return out
    }

    private fun flattenValueInto(value: Any?, prefix: String, out: LinkedHashMap<String, Any?>) {
        when (value) {
            is Map<*, *> -> {
                if (value.isEmpty() && prefix.isNotEmpty()) {
                    out[prefix] = value
                    return
                }
                for ((key, v) in value) {
                    val childPrefix = if (prefix.isEmpty()) key.toString() else "$prefix.$key"
                    flattenValueInto(v, childPrefix, out)
                }
            }
            is List<*> -> {
                val arr = JSONArray()
                value.forEach { arr.put(it) }
                out[prefix] = arr
            }
            is JSONArray -> {
                out[prefix] = value
            }
            is JSONObject -> {
                val keys = value.keys()
                val keyList = mutableListOf<String>()
                while (keys.hasNext()) keyList.add(keys.next())
                for (key in keyList) {
                    val childPrefix = if (prefix.isEmpty()) key else "$prefix.$key"
                    flattenValueInto(value.get(key), childPrefix, out)
                }
            }
            JSONObject.NULL, null -> out[prefix] = null
            else -> out[prefix] = value
        }
    }

    /**
     * Extracts the pure ID from a flag path. Handles keys in
     * "id:flag_name" and "id:" formats, normalizing to the pure ID.
     */
    fun normalizedFlagId(path: String): String {
        val bare = path.removeSuffix(":")
        val colonIndex = bare.indexOf(':')
        return if (colonIndex >= 0) bare.substring(0, colonIndex) else bare
    }

    fun compare(flatA: Map<String, Any?>, flatB: Map<String, Any?>): CompareResult {
        val onlyA = mutableListOf<FlatFlag>()
        val onlyB = mutableListOf<FlatFlag>()
        val diffs = mutableListOf<FlagDiff>()

        val indexA = LinkedHashMap<String, Pair<String, Any?>>()
        for ((path, value) in flatA) indexA[normalizedFlagId(path)] = path to value
        val indexB = LinkedHashMap<String, Pair<String, Any?>>()
        for ((path, value) in flatB) indexB[normalizedFlagId(path)] = path to value

        for ((id, entryA) in indexA) {
            val (pathA, valueA) = entryA
            val entryB = indexB[id]
            if (entryB == null) {
                onlyA.add(FlatFlag(pathA, valueA))
            } else {
                val (_, valueB) = entryB
                if (toCompactJsonString(valueA) != toCompactJsonString(valueB)) {
                    diffs.add(FlagDiff(pathA, valueA, valueB))
                }
            }
        }

        for ((id, entryB) in indexB) {
            val (pathB, valueB) = entryB
            if (!indexA.containsKey(id)) {
                onlyB.add(FlatFlag(pathB, valueB))
            }
        }

        // Calculate top-level count (ignoring subflags in total displayed)
        val topCountA = flatA.keys.map { it.substringBefore('.') }.distinct().size
        val topCountB = flatB.keys.map { it.substringBefore('.') }.distinct().size

        return CompareResult(
            onlyInA = onlyA,
            onlyInB = onlyB,
            diffs = diffs,
            totalA = topCountA,
            totalB = topCountB
        )
    }

    fun unflattenToMap(flat: Map<String, Any?>): LinkedHashMap<String, Any?> {
        val root = LinkedHashMap<String, Any?>()
        for ((path, value) in flat) {
            setPathInMap(root, path, value)
        }
        return root
    }

    private fun setPathInMap(root: MutableMap<String, Any?>, path: String, value: Any?) {
        val parts = path.split(".")
        var current = root
        for (i in 0 until parts.size - 1) {
            val key = parts[i]
            val next = current[key]
            if (next is MutableMap<*, *>) {
                @Suppress("UNCHECKED_CAST")
                current = next as MutableMap<String, Any?>
            } else {
                val newMap = LinkedHashMap<String, Any?>()
                current[key] = newMap
                current = newMap
            }
        }
        current[parts.last()] = value
    }

    fun toJsonString(value: Any?, indent: Int = 0): String {
        val sb = StringBuilder()
        val space = "  "
        val currentIndent = space.repeat(indent)
        val nextIndent = space.repeat(indent + 1)

        when (value) {
            is Map<*, *> -> {
                if (value.isEmpty()) return "{}"
                sb.append("{\n")
                val entries = value.entries.toList()
                entries.forEachIndexed { i, entry ->
                    sb.append(nextIndent)
                    sb.append("\"").append(entry.key).append("\": ")
                    sb.append(toJsonString(entry.value, indent + 1))
                    if (i < entries.size - 1) sb.append(",")
                    sb.append("\n")
                }
                sb.append(currentIndent).append("}")
            }
            is JSONArray -> {
                if (value.length() == 0) return "[]"
                sb.append("[\n")
                for (i in 0 until value.length()) {
                    sb.append(nextIndent)
                    sb.append(toJsonString(value.get(i), indent + 1))
                    if (i < value.length() - 1) sb.append(",")
                    sb.append("\n")
                }
                sb.append(currentIndent).append("]")
            }
            is String -> sb.append(escapeJsonString(value))
            null, JSONObject.NULL -> sb.append("null")
            else -> sb.append(value.toString())
        }
        return sb.toString()
    }

    /**
     * Serializes to compact JSON preserving order (LinkedHashMap).
     */
    fun toCompactJsonString(value: Any?): String {
        val sb = StringBuilder()
        when (value) {
            is Map<*, *> -> {
                sb.append("{")
                val entries = value.entries.toList()
                entries.forEachIndexed { i, entry ->
                    sb.append("\"").append(entry.key).append("\":")
                    sb.append(toCompactJsonString(entry.value))
                    if (i < entries.size - 1) sb.append(",")
                }
                sb.append("}")
            }
            is JSONArray -> {
                sb.append("[")
                for (i in 0 until value.length()) {
                    sb.append(toCompactJsonString(value.get(i)))
                    if (i < value.length() - 1) sb.append(",")
                }
                sb.append("]")
            }
            is String -> sb.append(escapeJsonString(value))
            null, JSONObject.NULL -> sb.append("null")
            else -> sb.append(value.toString())
        }
        return sb.toString()
    }

    fun getFormattedSubFlags(path: String, value: Any?, mapper: FlagMapper?): List<Pair<String, String>> {
        if (value !is JSONArray) return emptyList()
        val list = mutableListOf<Pair<String, String>>()
        for (i in 0 until value.length()) {
            val item = value.optString(i) ?: continue
            val parts = item.split(":")
            if (parts.size >= 2) {
                val idx = parts[0].trim()
                val valStr = parts.last().trim()
                val label = mapper?.getParamLabel(path, idx) ?: "#$idx"
                list.add(label to valStr)
            } else {
                list.add("" to item)
            }
        }
        return list
    }

    fun formatValue(path: String, value: Any?, mapper: FlagMapper?): String {
        if (value is JSONArray) {
            val subFlags = getFormattedSubFlags(path, value, mapper)
            if (subFlags.isNotEmpty()) {
                return subFlags.joinToString(" · ") { (lbl, v) -> if (lbl.isEmpty()) v else "$lbl: $v" }
            }
        }
        return value.toString()
    }

    @Deprecated(
        message = "Does not guarantee key order. Use unflattenToMap() + toJsonString().",
        replaceWith = ReplaceWith("unflattenToMap(flat)")
    )
    fun unflatten(flat: Map<String, Any?>): JSONObject {
        val orderedJson = toJsonString(unflattenToMap(flat))
        return JSONObject(orderedJson)
    }

    /**
     * Serializes flat map without expanding dots into sub-objects.
     * Necessary to keep Instagram keys at the same level.
     */
    fun serializeFlatCompact(flat: Map<String, Any?>): String {
        return toCompactJsonString(flat)
    }

    fun unflattenAndSerialize(flat: Map<String, Any?>): String {
        return toJsonString(unflattenToMap(flat))
    }

    /**
     * unflatten + compact serialize (for export).
     */
    fun unflattenAndSerializeCompact(flat: Map<String, Any?>): String {
        return toCompactJsonString(unflattenToMap(flat))
    }

    private fun escapeJsonString(s: String): String {
        val sb = StringBuilder()
        sb.append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> {
                    if (c.code < 32) {
                        sb.append(String.format("\\u%04x", c.code))
                    } else {
                        sb.append(c)
                    }
                }
            }
        }
        sb.append('"')
        return sb.toString()
    }
}