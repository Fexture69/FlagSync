/*
 * Copyright (C) 2026 ViictorNunes (https://github.com/ViictorNunes/FlagSync).
 * Licensed under the GNU General Public License v3.0.
 */

package com.flag.sync.xposed

import android.app.Application
import android.content.Context
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import org.luckypray.dexkit.DexKitBridge

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

        XposedBridge.log("$TAG: 🚀 Initializing FlagSync in ${lpparam.packageName} (process: ${lpparam.processName})")

        // 1. Hook Application.attach to initialize quality settings and device spoofing BEFORE Instagram reads device info
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Application",
                lpparam.classLoader,
                "attach",
                Context::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val context = param.args[0] as? Context ?: return
                        try {
                            InstagramQualityHooks.initFromPrefs(context)
                            InstagramQualityHooks.applyDeviceSpoof(lpparam.classLoader)
                            InstagramQualityHooks.syncHdrOverrides(context)
                        } catch (t: Throwable) {
                            XposedBridge.log("$TAG: ❌ Application.attach hook error: ${t.message}")
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: ❌ Failed to hook Application.attach: ${t.message}")
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

        // 3. Run DexKit scans for Developer Options unlocker and Quality Hooks
        try {
            System.loadLibrary("dexkit")
            val bridge = DexKitBridge.create(lpparam.appInfo.sourceDir)
            if (bridge != null) {
                bridge.use { dexKit ->
                    InstagramDevOptionsHook.findAndHookDynamicMethod(dexKit, lpparam.classLoader)
                    InstagramQualityHooks.installDexKitHooks(dexKit, lpparam.classLoader)
                }
            } else {
                InstagramDevOptionsHook.fallbackHook(lpparam.classLoader)
            }
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: ❌ DexKit initialization error: ${t.message}")
            InstagramDevOptionsHook.fallbackHook(lpparam.classLoader)
        }
    }
}
