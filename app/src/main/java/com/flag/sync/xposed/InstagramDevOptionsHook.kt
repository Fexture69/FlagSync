/*
 * Copyright (C) 2026 ViictorNunes (https://github.com/ViictorNunes/FlagSync).
 * Licensed under the GNU General Public License v3.0.
 */

package com.flag.sync.xposed

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.ClassMatcher
import org.luckypray.dexkit.query.matchers.MethodMatcher
import org.luckypray.dexkit.query.matchers.MethodsMatcher
import org.luckypray.dexkit.result.ClassData
import org.luckypray.dexkit.result.MethodData
import java.lang.reflect.Method
import java.util.HashSet

object InstagramDevOptionsHook {

    private const val TAG = "FlagSyncXposed"
    private const val MOBILECONFIG_GETTER_CLASS = "com.facebook.mobileconfig.factory.MobileConfigUnsafeContext"
    private const val USER_SESSION_CLASS = "com.instagram.common.session.UserSession"
    private const val MAX_GATE_OPCODES = 16
    private const val MIN_CALLER_FAN_IN = 3

    private val IS_EMPLOYEE_CONFIG_IDS = longArrayOf(
        36310834636390667L, // IG 436
        36310830341423371L, // IG 437+
        36310856111227168L, // IG <= 429
        36310864701161762L  // Older build
    )

    fun hook(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            System.loadLibrary("dexkit")
            val bridge = DexKitBridge.create(lpparam.appInfo.sourceDir)
            if (bridge != null) {
                bridge.use { dexKit ->
                    findAndHookWithDexKit(dexKit, lpparam.classLoader)
                }
                return
            }
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: DexKit error: ${e.message}, falling back to reflection...")
        }

        // Fallback: Reflection search for UserSession gate
        fallbackReflectionHook(lpparam.classLoader)
    }

    private fun findAndHookWithDexKit(bridge: DexKitBridge, classLoader: ClassLoader) {
        var hooked = false

        // Tier 1: Search using string "is_employee"
        try {
            XposedBridge.log("$TAG: 🔍 Discovery Tier 1 (String 'is_employee')...")
            val classes: List<ClassData> = bridge.findClass(
                FindClass.create().matcher(ClassMatcher.create().usingStrings("is_employee"))
            )

            for (c in classes) {
                if (!c.name.startsWith("X.")) continue
                val methods: List<MethodData> = bridge.findMethod(
                    FindMethod.create().matcher(
                        MethodMatcher.create().declaredClass(c.name).usingStrings("is_employee")
                    )
                )

                for (m in methods) {
                    val targetMethod = inspectInvokedMethods(bridge, m, classLoader)
                    if (targetMethod != null) {
                        hookExactMethod(targetMethod)
                        hooked = true
                        break
                    }
                }
                if (hooked) break
            }
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: Tier 1 error: ${t.message}")
        }

        // Tier 2: Structural discovery (UserSession -> boolean pass-through to MobileConfig)
        if (!hooked) {
            try {
                XposedBridge.log("$TAG: ⚠️ Tier 1 failed. Discovery Tier 2 (Structural)...")
                val structural = resolveEmployeeGateStructurally(bridge)
                if (structural != null) {
                    val m = structural.getMethodInstance(classLoader)
                    hookExactMethod(m)
                    hooked = true
                }
            } catch (t: Throwable) {
                XposedBridge.log("$TAG: Tier 2 error: ${t.message}")
            }
        }

        // Tier 3: Hardcoded known Config IDs
        if (!hooked) {
            try {
                XposedBridge.log("$TAG: ⚠️ Tier 2 failed. Discovery Tier 3 (Config IDs)...")
                for (configId in IS_EMPLOYEE_CONFIG_IDS) {
                    val methods: List<MethodData> = bridge.findMethod(
                        FindMethod.create().matcher(
                            MethodMatcher.create()
                                .usingNumbers(configId)
                                .returnType("boolean")
                                .paramCount(1)
                        )
                    )
                    if (methods.isNotEmpty()) {
                        val m = methods[0].getMethodInstance(classLoader)
                        hookExactMethod(m)
                        hooked = true
                        break
                    }
                }
            } catch (t: Throwable) {
                XposedBridge.log("$TAG: Tier 3 error: ${t.message}")
            }
        }

        if (!hooked) {
            XposedBridge.log("$TAG: DexKit discovery exhausted; trying reflection fallback.")
            fallbackReflectionHook(classLoader)
        }
    }

    private fun inspectInvokedMethods(bridge: DexKitBridge, method: MethodData, classLoader: ClassLoader): Method? {
        val invoked = method.getInvokes()
        for (inv in invoked) {
            if (!inv.returnType.toString().contains("boolean")) continue
            val paramTypes = inv.paramTypes
            if (paramTypes.size == 1 && paramTypes[0].toString().contains(USER_SESSION_CLASS)) {
                return try {
                    inv.getMethodInstance(classLoader)
                } catch (_: Throwable) {
                    null
                }
            }
        }
        return null
    }

    private fun resolveEmployeeGateStructurally(bridge: DexKitBridge): MethodData? {
        val getters = bridge.findMethod(
            FindMethod.create().matcher(
                MethodMatcher.create()
                    .declaredClass(MOBILECONFIG_GETTER_CLASS)
                    .returnType("boolean")
                    .paramTypes("long")
            )
        )
        if (getters.isEmpty()) return null

        val getterInvoke = MethodsMatcher.create()
        for (g in getters) {
            getterInvoke.add(MethodMatcher.create(g.descriptor))
        }

        val candidates = bridge.findMethod(
            FindMethod.create().matcher(
                MethodMatcher.create()
                    .returnType("boolean")
                    .paramTypes(USER_SESSION_CLASS)
                    .invokeMethods(getterInvoke)
            )
        )

        var best: MethodData? = null
        var bestFanIn = 0

        for (candidate in candidates) {
            if (candidate.opCodes.size > MAX_GATE_OPCODES) continue

            val callerClasses = HashSet<String>()
            for (caller in candidate.getCallers()) {
                callerClasses.add(caller.className)
            }
            if (callerClasses.size > bestFanIn) {
                bestFanIn = callerClasses.size
                best = candidate
            }
        }

        return if (bestFanIn >= MIN_CALLER_FAN_IN) best else null
    }

    private fun hookExactMethod(method: Method) {
        try {
            method.isAccessible = true
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    param.result = true
                }
            })
            XposedBridge.log("$TAG: 🎯 Hooked Developer Options Gate: ${method.declaringClass.name}.${method.name}")
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: ❌ Failed to hook exact method: ${t.message}")
        }
    }

    private fun fallbackReflectionHook(classLoader: ClassLoader) {
        try {
            // Attempt to resolve UserSession class and scan common utility classes
            val sessionClass = classLoader.loadClass(USER_SESSION_CLASS)
            XposedBridge.log("$TAG: Loaded UserSession class: ${sessionClass.name}")
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: Reflection fallback info: ${t.message}")
        }
    }
}
