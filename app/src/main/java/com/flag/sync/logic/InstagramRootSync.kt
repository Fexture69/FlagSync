/*
 * Copyright (C) 2026 ViictorNunes (https://github.com/ViictorNunes/FlagSync).
 * Licensed under the GNU General Public License v3.0.
 */

package com.flag.sync.logic

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

object InstagramRootSync {

    const val IG_PACKAGE = "com.instagram.android"
    const val IG_CONFIG_PATH = "/data/data/$IG_PACKAGE/files/mobileconfig/mc_overrides.json"

    data class RootResult<T>(
        val isSuccess: Boolean,
        val data: T? = null,
        val errorMessage: String? = null
    )

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
     * Pulls the live mc_overrides.json from Instagram into a raw JSON string.
     */
    suspend fun pullConfig(context: Context): RootResult<String> = withContext(Dispatchers.IO) {
        val tempFile = File(context.cacheDir, "ig_mc_pulled.json")
        try {
            // Check if mc_overrides.json exists in Instagram
            val checkCmd = "if [ -f \"$IG_CONFIG_PATH\" ]; then echo \"EXISTS\"; else echo \"NOT_FOUND\"; fi"
            val checkProc = Runtime.getRuntime().exec(arrayOf("su", "-c", checkCmd))
            val checkOut = checkProc.inputStream.bufferedReader().readText().trim()
            checkProc.waitFor()

            if (checkOut != "EXISTS") {
                return@withContext RootResult(false, null, "mc_overrides.json not found in Instagram.")
            }

            // Copy to app's cache directory and make it readable
            val copyCmd = "cp \"$IG_CONFIG_PATH\" \"${tempFile.absolutePath}\" && chmod 666 \"${tempFile.absolutePath}\""
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
     * Pushes a JSON config string into Instagram's mc_overrides.json, fixes permissions, and restarts Instagram.
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

            tempFile.writeText(jsonContent, Charsets.UTF_8)

            val dir = "/data/data/$IG_PACKAGE/files/mobileconfig"
            val target = "$dir/mc_overrides.json"

            val script = buildString {
                append("mkdir -p \"$dir\"\n")
                append("cp \"${tempFile.absolutePath}\" \"$target\"\n")
                append("IG_UID=\$(stat -c '%u' /data/data/$IG_PACKAGE 2>/dev/null || echo 10000)\n")
                append("chown \$IG_UID:\$IG_UID \"$target\"\n")
                append("chmod 600 \"$target\"\n")
                if (autoRestart) {
                    append("am force-stop $IG_PACKAGE\n")
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

            if (exitCode == 0) {
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
            val script = buildString {
                append("rm -f \"$IG_CONFIG_PATH\"\n")
                if (autoRestart) {
                    append("am force-stop $IG_PACKAGE\n")
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
     * Forces Instagram to stop via am force-stop.
     */
    suspend fun restartInstagram(): RootResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "am force-stop $IG_PACKAGE"))
            process.waitFor()
            RootResult(true, Unit, null)
        } catch (e: Exception) {
            RootResult(false, null, e.message)
        }
    }
}
