/*
 * Copyright (C) 2026 ViictorNunes (https://github.com/ViictorNunes/FlagSync).
 * Licensed under the GNU General Public License v3.0.
 */

package com.flag.sync.xposed

import android.app.Application
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class FlagSyncXposedEntry : IXposedHookLoadPackage {

    companion object {
        const val TAG = "FlagSyncXposed"
        val SUPPORTED_PACKAGES = setOf(
            "com.instagram.android",
            "com.instagram.lite",
            "paw.instagram.android"
        )
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (!SUPPORTED_PACKAGES.contains(lpparam.packageName)) return

        XposedBridge.log("$TAG: 🚀 Initializing FlagSync Xposed Hook in ${lpparam.packageName} (process: ${lpparam.processName})")

        // 1. Hook and unlock Developer Options inside Instagram
        try {
            InstagramDevOptionsHook.hook(lpparam)
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: ❌ Failed to initialize Developer Options Hook: ${t.message}")
        }

        // 2. Hook Application.onCreate to register the internal receiver inside Instagram process
        try {
            XposedHelpers.findAndHookMethod(
                Application::class.java,
                "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val app = param.thisObject as? Application ?: return
                        InstagramHookReceiver.register(app)
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: ❌ Failed to hook Application.onCreate: ${t.message}")
        }
    }
}
