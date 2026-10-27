/*
 * Copyright (C) 2026 ViictorNunes (https://github.com/ViictorNunes/FlagSync).
 * Licensed under the GNU General Public License v3.0.
 */

package com.flag.sync.xposed;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.query.matchers.MethodsMatcher;
import org.luckypray.dexkit.result.ClassData;
import org.luckypray.dexkit.result.MethodData;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class InstagramDevOptionsHook {

    private static final String TAG = "FlagSyncXposed";
    private static final String MOBILECONFIG_GETTER_CLASS = "com.facebook.mobileconfig.factory.MobileConfigUnsafeContext";
    private static final String USER_SESSION_CLASS = "com.instagram.common.session.UserSession";

    private static final int MAX_GATE_OPCODES = 16;
    private static final int MIN_CALLER_FAN_IN = 3;

    private static final long[] IS_EMPLOYEE_CONFIG_IDS = {
            36310834636390667L, // IG 436 (LX/02mx;->A00, 0x8100830000010b)
            36310830341423371L, // IG 437+ (LX/5sG;->A00, 0x8100820000010b)
            36310856111227168L, // IG <= 429 (LX/02il;->A00, 0x81008800000120)
            36310864701161762L  // older observed build
    };

    private static volatile boolean isHooked = false;

    public static void hook(XC_LoadPackage.LoadPackageParam lpparam) {
        if (isHooked) return;

        try {
            System.loadLibrary("dexkit");
            DexKitBridge bridge = DexKitBridge.create(lpparam.appInfo.sourceDir);
            if (bridge != null) {
                try {
                    findAndHookDynamicMethod(bridge, lpparam.classLoader);
                } finally {
                    try {
                        bridge.close();
                    } catch (Throwable ignored) {}
                }
                return;
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": DexKit error: " + t.getMessage() + ", falling back...");
        }

        fallbackHook(lpparam.classLoader);
    }

    public static void findAndHookDynamicMethod(DexKitBridge bridge, ClassLoader classLoader) {
        boolean found = false;

        // Tier 1: Search using string "is_employee"
        try {
            XposedBridge.log(TAG + ": 🔍 Discovery Tier 1 (String 'is_employee')...");
            List<ClassData> classes = bridge.findClass(FindClass.create()
                    .matcher(ClassMatcher.create().usingStrings("is_employee")));

            if (classes != null && !classes.isEmpty()) {
                for (ClassData classData : classes) {
                    String className = classData.getName();
                    if (!className.startsWith("X.")) continue;

                    List<MethodData> methods = bridge.findMethod(FindMethod.create()
                            .matcher(MethodMatcher.create()
                                    .declaredClass(className)
                                    .usingStrings("is_employee")));

                    if (methods != null) {
                        for (MethodData method : methods) {
                            if (inspectInvokedMethods(bridge, method, classLoader)) {
                                found = true;
                                break;
                            }
                        }
                    }
                    if (found) break;
                }
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Tier 1 error: " + t.getMessage());
        }

        // Tier 2: Structural discovery (UserSession -> boolean pass-through to MobileConfig)
        if (!found) {
            try {
                XposedBridge.log(TAG + ": ⚠️ Tier 1 failed. Discovery Tier 2 (Structural)...");
                MethodData structural = resolveEmployeeGateStructurally(bridge);
                if (structural != null) {
                    try {
                        Method targetMethod = structural.getMethodInstance(classLoader);
                        hookExactMethod(targetMethod);
                        XposedBridge.log(TAG + ": 🎯 Found via structural match: "
                                + structural.getClassName() + "." + structural.getName());
                        found = true;
                    } catch (Throwable e) {
                        XposedBridge.log(TAG + ": ❌ Structural match failed to bind: " + e.getMessage());
                    }
                }
            } catch (Throwable t) {
                XposedBridge.log(TAG + ": Tier 2 error: " + t.getMessage());
            }
        }

        // Tier 3: Hardcoded known Config IDs
        if (!found) {
            try {
                XposedBridge.log(TAG + ": ⚠️ Tier 2 failed. Discovery Tier 3 (Config IDs)...");
                for (long configId : IS_EMPLOYEE_CONFIG_IDS) {
                    List<MethodData> idMethods = bridge.findMethod(FindMethod.create()
                            .matcher(MethodMatcher.create()
                                    .usingNumbers(configId)
                                    .returnType("boolean")
                                    .paramCount(1)));

                    if (idMethods != null && !idMethods.isEmpty()) {
                        String targetClass = idMethods.get(0).getClassName();
                        XposedBridge.log(TAG + ": 🎯 Found via Config ID " + configId + " in: " + targetClass);
                        hookAllBooleanMethodsInClass(bridge, targetClass, classLoader);
                        found = true;
                        break;
                    }
                }
            } catch (Throwable t) {
                XposedBridge.log(TAG + ": Tier 3 error: " + t.getMessage());
            }
        }

        if (!found) {
            XposedBridge.log(TAG + ": ❌ All DexKit tiers exhausted, trying fallback...");
            fallbackHook(classLoader);
        }
    }

    private static MethodData resolveEmployeeGateStructurally(DexKitBridge bridge) {
        try {
            List<MethodData> getters = bridge.findMethod(FindMethod.create()
                    .matcher(MethodMatcher.create()
                            .declaredClass(MOBILECONFIG_GETTER_CLASS)
                            .returnType("boolean")
                            .paramTypes("long")));
            if (getters == null || getters.isEmpty()) return null;

            MethodsMatcher getterInvoke = MethodsMatcher.create();
            for (MethodData getter : getters) {
                getterInvoke.add(MethodMatcher.create(getter.getDescriptor()));
            }

            List<MethodData> candidates = bridge.findMethod(FindMethod.create()
                    .matcher(MethodMatcher.create()
                            .returnType("boolean")
                            .paramTypes(USER_SESSION_CLASS)
                            .invokeMethods(getterInvoke)));

            if (candidates == null) return null;

            MethodData best = null;
            int bestFanIn = 0;
            for (MethodData candidate : candidates) {
                if (candidate.getOpCodes().size() > MAX_GATE_OPCODES) continue;

                Set<String> callerClasses = new HashSet<>();
                List<MethodData> callers = candidate.getCallers();
                if (callers != null) {
                    for (MethodData caller : callers) {
                        callerClasses.add(caller.getClassName());
                    }
                }
                if (callerClasses.size() > bestFanIn) {
                    bestFanIn = callerClasses.size();
                    best = candidate;
                }
            }
            return bestFanIn >= MIN_CALLER_FAN_IN ? best : null;
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Structural discovery error: " + t.getMessage());
            return null;
        }
    }

    private static boolean inspectInvokedMethods(DexKitBridge bridge, MethodData method, ClassLoader classLoader) {
        try {
            List<MethodData> invokedMethods = method.getInvokes();
            if (invokedMethods == null || invokedMethods.isEmpty()) return false;

            for (MethodData invokedMethod : invokedMethods) {
                String returnType = String.valueOf(invokedMethod.getReturnType());
                if (!returnType.contains("boolean")) continue;

                List<String> paramTypes = new ArrayList<>();
                for (Object param : invokedMethod.getParamTypes()) {
                    paramTypes.add(String.valueOf(param));
                }

                if (paramTypes.size() == 1 && paramTypes.get(0).contains(USER_SESSION_CLASS)) {
                    String targetClass = invokedMethod.getClassName();
                    XposedBridge.log(TAG + ": 📦 Hooking via String detection: " + targetClass);
                    hookAllBooleanMethodsInClass(bridge, targetClass, classLoader);
                    return true;
                }
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Error inspecting invoked methods: " + t.getMessage());
        }
        return false;
    }

    private static void hookAllBooleanMethodsInClass(DexKitBridge bridge, String className, ClassLoader classLoader) {
        try {
            List<MethodData> methods = bridge.findMethod(FindMethod.create()
                    .matcher(MethodMatcher.create().declaredClass(className)));

            if (methods == null) return;

            for (MethodData method : methods) {
                String returnType = String.valueOf(method.getReturnType());
                List<String> paramTypes = new ArrayList<>();
                for (Object param : method.getParamTypes()) {
                    paramTypes.add(String.valueOf(param));
                }

                if (returnType.contains("boolean") && paramTypes.size() == 1 && paramTypes.get(0).contains(USER_SESSION_CLASS)) {
                    try {
                        Method targetMethod = method.getMethodInstance(classLoader);
                        hookExactMethod(targetMethod);
                    } catch (Throwable t) {
                        XposedBridge.log(TAG + ": Failed to hook " + method.getName() + ": " + t.getMessage());
                    }
                }
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Error while hooking class: " + className + " -> " + t.getMessage());
        }
    }

    private static void hookExactMethod(Method m) {
        try {
            m.setAccessible(true);
            XposedBridge.hookMethod(m, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    param.setResult(true);
                }
            });
            isHooked = true;
            FlagSyncHookStatus.isDevOptionsHooked = true;
            XposedBridge.log(TAG + ": ✅ Developer Options unlocked via: " + m.getDeclaringClass().getName() + "." + m.getName());
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": ❌ Failed to hook exact method: " + t.getMessage());
        }
    }

    public static void fallbackHook(ClassLoader classLoader) {
        try {
            Class<?> sessionClass = classLoader.loadClass(USER_SESSION_CLASS);
            XposedBridge.log(TAG + ": UserSession class loaded: " + sessionClass.getName());
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Fallback reflection: " + t.getMessage());
        }
    }
}
