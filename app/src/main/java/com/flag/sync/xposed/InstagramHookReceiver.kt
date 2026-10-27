/*
 * Copyright (C) 2026 ViictorNunes (https://github.com/ViictorNunes/FlagSync).
 * Licensed under the GNU General Public License v3.0.
 */

package com.flag.sync.xposed

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import de.robv.android.xposed.XposedBridge
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets

object InstagramHookReceiver {

    const val ACTION_APPLY_CONFIG = "com.flag.sync.ACTION_APPLY_CONFIG"
    const val ACTION_EXPORT_CONFIG = "com.flag.sync.ACTION_EXPORT_CONFIG"
    const val ACTION_CONFIG_EXPORTED = "com.flag.sync.ACTION_CONFIG_EXPORTED"
    const val ACTION_UPDATE_QUALITY_PREF = "com.flag.sync.ACTION_UPDATE_QUALITY_PREF"
    const val ACTION_PING = "com.flag.sync.ACTION_PING"
    const val ACTION_PONG = "com.flag.sync.ACTION_PONG"

    private var isRegistered = false

    fun register(context: Context) {
        if (isRegistered) return

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent?) {
                if (intent == null) return
                val action = intent.action ?: return

                when (action) {
                    ACTION_PING -> {
                        val reply = Intent(ACTION_PONG).apply {
                            setPackage("com.flag.sync")
                            putExtra("package_name", ctx.packageName)
                            putExtra("dev_options_unlocked", true)
                        }
                        ctx.sendBroadcast(reply)
                    }

                    ACTION_APPLY_CONFIG -> {
                        val json = intent.getStringExtra("json_content")
                        if (json.isNullOrEmpty()) return

                        try {
                            // Validate syntax
                            JSONObject(json)

                            val dest = File(ctx.filesDir, "mobileconfig/mc_overrides.json")
                            val parent = dest.parentFile
                            if (parent != null && !parent.exists()) {
                                parent.mkdirs()
                            }

                            // Atomic write
                            val tmp = File(dest.path + ".tmp")
                            FileOutputStream(tmp).use { fos ->
                                fos.write(json.toByteArray(StandardCharsets.UTF_8))
                                fos.fd.sync()
                            }
                            if (dest.exists()) dest.delete()
                            tmp.renameTo(dest)

                            // Clear volatile cache
                            try {
                                ctx.cacheDir?.deleteRecursively()
                            } catch (_: Exception) {}

                            Handler(Looper.getMainLooper()).post {
                                Toast.makeText(
                                    ctx.applicationContext,
                                    "⚡ FlagSync: MobileConfig overrides applied!",
                                    Toast.LENGTH_LONG
                                ).show()
                            }

                            XposedBridge.log("FlagSyncXposed: Successfully applied mc_overrides.json inside ${ctx.packageName}")

                            if (intent.getBooleanExtra("restart", true)) {
                                Handler(Looper.getMainLooper()).postDelayed({
                                    android.os.Process.killProcess(android.os.Process.myPid())
                                }, 500)
                            }
                        } catch (e: Exception) {
                            XposedBridge.log("FlagSyncXposed: Failed to apply config: ${e.message}")
                            Handler(Looper.getMainLooper()).post {
                                Toast.makeText(
                                    ctx.applicationContext,
                                    "FlagSync error: ${e.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }

                    ACTION_EXPORT_CONFIG -> {
                        try {
                            val source = File(ctx.filesDir, "mobileconfig/mc_overrides.json")
                            val fileExists = source.exists() && source.length() > 0
                            val content = if (fileExists) source.readText(StandardCharsets.UTF_8).trim() else "{}"
                            val reply = Intent(ACTION_CONFIG_EXPORTED).apply {
                                setPackage("com.flag.sync")
                                putExtra("json_content", content)
                                putExtra("exists", fileExists)
                            }
                            ctx.sendBroadcast(reply)
                            XposedBridge.log("FlagSyncXposed: Exported mc_overrides.json (exists=$fileExists, size=${content.length}) to FlagSync")
                        } catch (e: Exception) {
                            XposedBridge.log("FlagSyncXposed: Export failed: ${e.message}")
                            val errorReply = Intent(ACTION_CONFIG_EXPORTED).apply {
                                setPackage("com.flag.sync")
                                putExtra("json_content", "{}")
                                putExtra("error", e.message)
                            }
                            ctx.sendBroadcast(errorReply)
                        }
                    }

                    ACTION_UPDATE_QUALITY_PREF -> {
                        val key = intent.getStringExtra("key") ?: return
                        when (key) {
                            "force_reel_quality" -> {
                                val v = intent.getIntExtra("int_val", 0)
                                InstagramQualityHooks.forceReelQuality = v
                                InstagramQualityHooks.saveCache(ctx)
                                XposedBridge.log("FlagSyncXposed: Updated forceReelQuality = $v")
                            }
                            "high_res_images" -> {
                                val v = intent.getBooleanExtra("bool_val", false)
                                InstagramQualityHooks.highResolutionImages = v
                                InstagramQualityHooks.saveCache(ctx)
                                XposedBridge.log("FlagSyncXposed: Updated highResolutionImages = $v")
                            }
                            "max_upload_quality" -> {
                                val v = intent.getBooleanExtra("bool_val", false)
                                InstagramQualityHooks.maxUploadQuality = v
                                InstagramQualityHooks.saveCache(ctx)
                                XposedBridge.log("FlagSyncXposed: Updated maxUploadQuality = $v")
                            }
                            "spoof_device" -> {
                                val v = intent.getStringExtra("str_val") ?: ""
                                InstagramQualityHooks.spoofDevice = v
                                InstagramQualityHooks.saveCache(ctx)
                                InstagramQualityHooks.syncHdrOverrides(ctx)
                                XposedBridge.log("FlagSyncXposed: Updated spoofDevice = $v")
                            }
                            "show_hook_status_toast" -> {
                                val v = intent.getBooleanExtra("bool_val", true)
                                FlagSyncHookStatus.showStatusToast = v
                                InstagramQualityHooks.saveCache(ctx)
                                XposedBridge.log("FlagSyncXposed: Updated showStatusToast = $v")
                            }
                        }
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(ACTION_PING)
            addAction(ACTION_APPLY_CONFIG)
            addAction(ACTION_EXPORT_CONFIG)
            addAction(ACTION_UPDATE_QUALITY_PREF)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
            isRegistered = true
            FlagSyncHookStatus.isReceiverReady = true
            XposedBridge.log("FlagSyncXposed: ✅ Internal hook receiver registered in ${context.packageName}")
        } catch (e: Throwable) {
            XposedBridge.log("FlagSyncXposed: ❌ Error registering receiver: ${e.message}")
        }
    }
}
