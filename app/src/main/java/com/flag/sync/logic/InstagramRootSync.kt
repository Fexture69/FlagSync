/*
 * Copyright (C) 2026 ViictorNunes (https://github.com/ViictorNunes/FlagSync).
 * Licensed under the GNU General Public License v3.0.
 */

package com.flag.sync.logic

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object InstagramRootSync {

    const val DEFAULT_IG_PACKAGE = "com.instagram.android"

    data class RootResult<T>(
        val isSuccess: Boolean,
        val data: T? = null,
        val errorMessage: String? = null
    )

    /**
     * Finds the installed Instagram package on the device.
     */
    suspend fun getInstalledInstagramPackage(): String = withContext(Dispatchers.IO) {
        val candidates = listOf(
            "com.instagram.android",
            "com.instagram.lite",
            "paw.instagram.android"
        )
        for (pkg in candidates) {
            try {
                val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "if [ -d \"/data/data/$pkg\" ]; then echo 1; fi"))
                val out = p.inputStream.bufferedReader().readText().trim()
                p.waitFor()
                if (out == "1") return@withContext pkg
            } catch (_: Exception) {}
        }
        DEFAULT_IG_PACKAGE
    }

    /**
     * Checks whether root (su) is available and functional.
     */
    suspend fun isRootAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            exitCode == 0 && output.contains("uid=0")
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Converts a flat FlagSync map into a strictly valid, flat Instagram mc_overrides.json format.
     * Guarantees:
     * 1. Keys are purely numerical with trailing colon: "<id>:" (strips embedded names).
     * 2. Values are strictly arrays of param strings: ["0: : true", "1: : 50"].
     * 3. "_qe_overrides_": [] is present so Instagram's C++ MobileConfig parser does not fail.
     * 4. Dots in values or keys never create unintended nested JSON objects.
     */
    fun formatFlagsForInstagram(flat: Map<String, Any?>): String {
        val root = JSONObject()
        var hasQeOverrides = false

        for ((key, value) in flat) {
            if (key == "_qe_overrides_") {
                hasQeOverrides = true
                when (value) {
                    is JSONArray -> root.put("_qe_overrides_", value)
                    is List<*> -> {
                        val arr = JSONArray()
                        value.forEach { arr.put(it) }
                        root.put("_qe_overrides_", arr)
                    }
                    else -> root.put("_qe_overrides_", JSONArray())
                }
                continue
            }

            // Extract pure numerical ID
            val bare = key.removeSuffix(":")
            val colonIndex = bare.indexOf(':')
            val pureId = (if (colonIndex >= 0) bare.substring(0, colonIndex) else bare).trim()

            // Skip invalid non-numeric entries
            if (pureId.isEmpty() || !pureId.all { it.isDigit() }) continue

            val normalizedKey = "$pureId:"

            when (value) {
                is JSONArray -> root.put(normalizedKey, value)
                is List<*> -> {
                    val arr = JSONArray()
                    value.forEach { arr.put(it) }
                    root.put(normalizedKey, arr)
                }
                is Map<*, *> -> {
                    val arr = JSONArray()
                    for ((pk, pv) in value) {
                        val pStr = when (pv) {
                            true -> "true"
                            false -> "false"
                            else -> pv.toString()
                        }
                        arr.put("$pk: : $pStr")
                    }
                    root.put(normalizedKey, arr)
                }
                else -> {
                    val arr = JSONArray()
                    arr.put(value.toString())
                    root.put(normalizedKey, arr)
                }
            }
        }

        if (!hasQeOverrides) {
            root.put("_qe_overrides_", JSONArray())
        }

        return root.toString()
    }

    /**
     * Attempts to pull the config without requiring root using the Xposed broadcast bridge.
     */
    private suspend fun pullConfigViaBroadcast(context: Context, pkg: String): String? = withContext(Dispatchers.Main) {
        val deferred = CompletableDeferred<String?>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                val json = intent?.getStringExtra("json_content")
                if (!deferred.isCompleted) {
                    deferred.complete(json)
                }
            }
        }
        val filter = IntentFilter("com.flag.sync.ACTION_CONFIG_EXPORTED").apply {
            addAction("ps.reso.instaeclipse.ACTION_SEND_CONFIG")
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
        } catch (_: Exception) {
            return@withContext null
        }

        try {
            val exportIntent = Intent("com.flag.sync.ACTION_EXPORT_CONFIG").apply {
                setPackage(pkg)
            }
            context.sendBroadcast(exportIntent)

            val ieIntent = Intent("ps.reso.instaeclipse.ACTION_EXPORT_CONFIG").apply {
                setPackage(pkg)
            }
            context.sendBroadcast(ieIntent)

            withTimeoutOrNull(1500L) {
                deferred.await()
            }
        } catch (_: Exception) {
            null
        } finally {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {}
        }
    }

    /**
     * Attempts to push the config directly into Instagram without root via the Xposed broadcast bridge.
     */
    private suspend fun pushConfigViaBroadcast(context: Context, pkg: String, json: String, restart: Boolean): Boolean = withContext(Dispatchers.Main) {
        val deferred = CompletableDeferred<Boolean>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (!deferred.isCompleted) {
                    deferred.complete(intent?.getBooleanExtra("success", false) ?: true)
                }
            }
        }
        val filter = IntentFilter("com.flag.sync.ACTION_CONFIG_APPLIED")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
        } catch (_: Exception) {
            return@withContext false
        }

        try {
            val exportIntent = Intent("com.flag.sync.ACTION_APPLY_CONFIG").apply {
                setPackage(pkg)
                putExtra("json_content", json)
                putExtra("restart", restart)
            }
            context.sendBroadcast(exportIntent)

            val ieIntent = Intent("ps.reso.instaeclipse.ACTION_IMPORT_CONFIG").apply {
                setPackage(pkg)
                putExtra("json_content", json)
            }
            context.sendBroadcast(ieIntent)

            withTimeoutOrNull(1200L) {
                deferred.await()
            } ?: false
        } catch (_: Exception) {
            false
        } finally {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {}
        }
    }

    /**
     * Pulls the live mc_overrides.json from Instagram into a raw JSON string.
     * Uses 2 tiers:
     * 1. Xposed broadcast bridge (instant, no root needed if Instagram is running)
     * 2. Root su file copy (if broadcast times out or Instagram isn't running)
     * If no overrides file exists on disk yet, returns a clean empty JSON ("{}").
     */
    suspend fun pullConfig(context: Context): RootResult<String> = withContext(Dispatchers.IO) {
        val pkg = getInstalledInstagramPackage()

        // Tier 1: Try broadcast from hooked Instagram process
        try {
            val broadcastConfig = pullConfigViaBroadcast(context, pkg)
            if (broadcastConfig != null && broadcastConfig.isNotBlank()) {
                try {
                    JSONObject(broadcastConfig)
                    return@withContext RootResult(true, broadcastConfig, null)
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}

        // Tier 2: Root su copy
        val tempFile = File(context.cacheDir, "ig_mc_pulled.json")
        try {
            val configPath = "/data/data/$pkg/files/mobileconfig/mc_overrides.json"

            // Check if mc_overrides.json exists in Instagram
            val checkCmd = "if [ -f \"$configPath\" ]; then echo \"EXISTS\"; else echo \"NOT_FOUND\"; fi"
            val checkProc = Runtime.getRuntime().exec(arrayOf("su", "-c", checkCmd))
            val checkOut = checkProc.inputStream.bufferedReader().readText().trim()
            checkProc.waitFor()

            if (checkOut != "EXISTS") {
                // If mc_overrides.json does not exist yet in Instagram, return clean empty state
                return@withContext RootResult(true, "{}", null)
            }

            // Copy to app's cache directory and make it readable
            val copyCmd = "cp \"$configPath\" \"${tempFile.absolutePath}\" && chmod 666 \"${tempFile.absolutePath}\""
            val copyProc = Runtime.getRuntime().exec(arrayOf("su", "-c", copyCmd))
            copyProc.waitFor()

            if (!tempFile.exists() || tempFile.length() == 0L) {
                return@withContext RootResult(false, null, "Failed to copy mc_overrides.json from Instagram.")
            }

            val rawJson = tempFile.readText(Charsets.UTF_8).trim()
            tempFile.delete()

            // Validate JSON
            try {
                JSONObject(rawJson)
            } catch (e: Exception) {
                return@withContext RootResult(false, null, "Instagram config is corrupted: ${e.message}")
            }

            RootResult(true, rawJson, null)
        } catch (e: Exception) {
            tempFile.delete()
            RootResult(false, null, e.message ?: "Failed to pull config via root")
        }
    }

    /**
     * Pushes a JSON config string into Instagram's mc_overrides.json.
     * Uses a 2-tier strategy:
     * 1. Xposed broadcast bridge (instant memory & disk apply if Instagram is running with FlagSync hook)
     * 2. Direct Root persistence with safe UID/GID detection, permission repair (chmod 664 / 775),
     *    SELinux context synchronization, and reliable app relaunch.
     */
    suspend fun pushConfig(
        context: Context,
        jsonContent: String,
        autoRestart: Boolean = true
    ): RootResult<Unit> = withContext(Dispatchers.IO) {
        val tempFile = File(context.cacheDir, "ig_mc_push.json")
        try {
            // Validate JSON syntax before doing anything
            JSONObject(jsonContent)

            val pkg = getInstalledInstagramPackage()

            // Tier 1: Try broadcast to hooked Instagram process
            val broadcastSuccess = try {
                pushConfigViaBroadcast(context, pkg, jsonContent, autoRestart)
            } catch (_: Exception) {
                false
            }

            // Tier 2: Check if root is available and persist directly to disk
            val rootAvailable = isRootAvailable()

            if (!rootAvailable) {
                return@withContext if (broadcastSuccess) {
                    RootResult(true, Unit, null)
                } else {
                    RootResult(
                        false,
                        null,
                        "Root access is not available and Instagram hook receiver did not respond. Please grant root access or ensure Instagram is running with FlagSync Xposed module enabled."
                    )
                }
            }

            tempFile.writeText(jsonContent, Charsets.UTF_8)
            tempFile.setReadable(true, false)

            val dir = "/data/data/$pkg/files/mobileconfig"
            val target = "$dir/mc_overrides.json"

            // Get Instagram UID safely from PackageManager
            val igUid = try {
                context.packageManager.getApplicationInfo(pkg, 0).uid
            } catch (_: Exception) {
                10000
            }

            val script = buildString {
                append("mkdir -p \"$dir\"\n")
                append("cp \"${tempFile.absolutePath}\" \"$target\"\n")
                append("IG_UID=\$(stat -c '%u' \"/data/data/$pkg\" 2>/dev/null || echo $igUid)\n")
                append("if [ -z \"\$IG_UID\" ]; then IG_UID=$igUid; fi\n")
                append("chown -R \$IG_UID:\$IG_UID \"$dir\"\n")
                append("chmod 775 \"$dir\"\n")
                append("chmod 664 \"$target\"\n")
                // Restore SELinux contexts so untrusted_app can read
                append("restorecon -RF \"$dir\" 2>/dev/null || true\n")
                append("chcon -R --reference=\"/data/data/$pkg/files\" \"$dir\" 2>/dev/null || true\n")
                // Clear stale cache
                append("rm -rf \"/data/data/$pkg/cache/\"* 2>/dev/null || true\n")
                // Cold restart Instagram if requested
                if (autoRestart) {
                    append("am force-stop \"$pkg\"\n")
                    append("sleep 1\n")
                    append("(monkey -p \"$pkg\" -c android.intent.category.LAUNCHER 1 2>/dev/null || am start -n \"$pkg/com.instagram.mainactivity.MainActivity\" 2>/dev/null || true)\n")
                }
            }

            val process = Runtime.getRuntime().exec("su")
            process.outputStream.bufferedWriter().use { writer ->
                writer.write(script)
                writer.write("\nexit\n")
                writer.flush()
            }
            val exitCode = process.waitFor()
            tempFile.delete()

            if (exitCode == 0 || broadcastSuccess) {
                RootResult(true, Unit, null)
            } else {
                val err = process.errorStream.bufferedReader().readText()
                RootResult(false, null, "Root error (code $exitCode): $err")
            }
        } catch (e: Exception) {
            tempFile.delete()
            RootResult(false, null, e.message ?: "Failed to push config via root")
        }
    }

    /**
     * Resets Instagram's overrides by removing mc_overrides.json and optionally restarting Instagram.
     */
    suspend fun clearConfig(autoRestart: Boolean = true): RootResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val pkg = getInstalledInstagramPackage()
            val configPath = "/data/data/$pkg/files/mobileconfig/mc_overrides.json"
            val script = buildString {
                append("rm -f \"$configPath\"\n")
                append("rm -rf \"/data/data/$pkg/cache/\"* 2>/dev/null || true\n")
                if (autoRestart) {
                    append("am force-stop \"$pkg\"\n")
                    append("sleep 1\n")
                    append("(monkey -p \"$pkg\" -c android.intent.category.LAUNCHER 1 2>/dev/null || am start -n \"$pkg/com.instagram.mainactivity.MainActivity\" 2>/dev/null || true)\n")
                }
            }
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", script))
            val exitCode = process.waitFor()
            if (exitCode == 0) {
                RootResult(true, Unit, null)
            } else {
                RootResult(false, null, "Failed to remove config (code $exitCode)")
            }
        } catch (e: Exception) {
            RootResult(false, null, e.message ?: "Failed to clear config via root")
        }
    }

    /**
     * Forces Instagram to stop and relaunches it cleanly.
     */
    suspend fun restartInstagram(): RootResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val pkg = getInstalledInstagramPackage()
            val script = buildString {
                append("am force-stop \"$pkg\"\n")
                append("sleep 1\n")
                append("(monkey -p \"$pkg\" -c android.intent.category.LAUNCHER 1 2>/dev/null || am start -n \"$pkg/com.instagram.mainactivity.MainActivity\" 2>/dev/null || true)\n")
            }
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", script))
            process.waitFor()
            RootResult(true, Unit, null)
        } catch (e: Exception) {
            RootResult(false, null, e.message)
        }
    }
}
