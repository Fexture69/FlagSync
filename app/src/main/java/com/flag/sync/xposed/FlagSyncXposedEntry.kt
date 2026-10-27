/*
 * Copyright (C) 2026 ViictorNunes (https://github.com/ViictorNunes/FlagSync).
 * Licensed under the GNU General Public License v3.0.
 */

package com.flag.sync.xposed

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Bundle
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import org.luckypray.dexkit.DexKitBridge

class FlagSyncXposedEntry : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        const val TAG = "FlagSyncXposed"
        val SUPPORTED_PACKAGES = setOf(
            "com.instagram.android",
            "com.instagram.lite",
            "paw.instagram.android"
        )
        var modulePath: String? = null
        var moduleLibDir: String? = null
    }

    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        modulePath = startupParam.modulePath
        try {
            val abi = if (Build.SUPPORTED_ABIS.isNotEmpty()) Build.SUPPORTED_ABIS[0] else "arm64-v8a"
            val abiFolder = when {
                abi.equals("arm64-v8a", true) -> "arm64"
                abi.startsWith("arm", true) -> "arm"
                abi.equals("x86_64", true) -> "x86_64"
                abi.equals("x86", true) -> "x86"
                else -> abi
            }
            val baseDir = modulePath?.substringBeforeLast('/')
            moduleLibDir = "$baseDir/lib/$abiFolder"
            XposedBridge.log("$TAG: Zygote initialized (modulePath=$modulePath, moduleLibDir=$moduleLibDir)")
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: Error initializing Zygote paths: ${t.message}")
        }
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (!SUPPORTED_PACKAGES.contains(lpparam.packageName)) return

        // Skip non-main background processes (like :mqtt, :videoplayer)
        if (lpparam.processName != null && lpparam.processName != lpparam.packageName) {
            return
        }

        XposedBridge.log("$TAG: 🚀 Initializing FlagSync in main process of ${lpparam.packageName}")

        // 1. Hook Application.attach
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
                            XposedBridge.log("$TAG: ❌ Application.attach before hook error: ${t.message}")
                        }
                    }

                    override fun afterHookedMethod(param: MethodHookParam) {
                        val context = param.args[0] as? Context ?: return
                        // Launch DexKit scan and hook installation in a dedicated background thread
                        Thread {
                            try {
                                val loaded = DexKitLoader.load(context, modulePath, moduleLibDir)
                                if (loaded) {
                                    val bridge = DexKitBridge.create(lpparam.appInfo.sourceDir)
                                    if (bridge != null) {
                                        bridge.use { dexKit ->
                                            InstagramDevOptionsHook.findAndHookDynamicMethod(dexKit, lpparam.classLoader)
                                            InstagramQualityHooks.installDexKitHooks(dexKit, lpparam.classLoader)
                                        }
                                    } else {
                                        XposedBridge.log("$TAG: ⚠️ DexKitBridge.create returned null")
                                        InstagramDevOptionsHook.fallbackHook(lpparam.classLoader)
                                    }
                                } else {
                                    XposedBridge.log("$TAG: ⚠️ DexKitLoader could not load native lib, using fallback")
                                    InstagramDevOptionsHook.fallbackHook(lpparam.classLoader)
                                }
                            } catch (t: Throwable) {
                                XposedBridge.log("$TAG: ❌ Hook install error: ${t.message}")
                                InstagramDevOptionsHook.fallbackHook(lpparam.classLoader)
                            }
                        }.apply {
                            priority = Thread.NORM_PRIORITY - 1
                            name = "FlagSync-Init"
                            start()
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: ❌ Failed to hook Application.attach: ${t.message}")
        }

        // 2. Hook Application.onCreate to register receiver and status toast listener
        try {
            XposedHelpers.findAndHookMethod(
                Application::class.java,
                "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val app = param.thisObject as? Application ?: return
                        InstagramHookReceiver.register(app)

                        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
                            override fun onActivityResumed(activity: Activity) {
                                FlagSyncHookStatus.showStatusOverlay(activity)
                            }
                            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
                            override fun onActivityStarted(activity: Activity) {}
                            override fun onActivityPaused(activity: Activity) {}
                            override fun onActivityStopped(activity: Activity) {}
                            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
                            override fun onActivityDestroyed(activity: Activity) {}
                        })
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: ❌ Failed to hook Application.onCreate: ${t.message}")
        }
    }
}
