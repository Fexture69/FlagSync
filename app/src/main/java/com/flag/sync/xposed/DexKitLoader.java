/*
 * Copyright (C) 2026 ViictorNunes (https://github.com/ViictorNunes/FlagSync).
 * Licensed under the GNU General Public License v3.0.
 */

package com.flag.sync.xposed;

import android.content.Context;
import android.os.Build;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import de.robv.android.xposed.XposedBridge;

public final class DexKitLoader {

    private static final String TAG = "FlagSyncXposed";
    private static volatile boolean loaded = false;

    private DexKitLoader() {}

    public static synchronized boolean load(Context context, String modulePath, String moduleLibDir) {
        if (loaded) return true;

        // 1. Try from module's native library directory (provided by Xposed framework)
        if (moduleLibDir != null) {
            File directLib = new File(moduleLibDir, "libdexkit.so");
            if (directLib.exists()) {
                try {
                    System.load(directLib.getAbsolutePath());
                    loaded = true;
                    XposedBridge.log(TAG + ": ✅ Loaded libdexkit.so directly from: " + directLib.getAbsolutePath());
                    return true;
                } catch (Throwable t) {
                    XposedBridge.log(TAG + ": ⚠️ Direct load from moduleLibDir failed: " + t.getMessage());
                }
            }
        }

        // 2. Try System.loadLibrary (works if module APK was installed with legacy JNI bridge)
        try {
            System.loadLibrary("dexkit");
            loaded = true;
            XposedBridge.log(TAG + ": ✅ Loaded libdexkit.so via System.loadLibrary");
            return true;
        } catch (Throwable ignored) {}

        // 3. Fallback: Extract libdexkit.so from FlagSync module APK into Instagram's local cache
        if (modulePath != null && context != null) {
            try {
                File cachedLib = new File(context.getCacheDir(), "libdexkit.so");
                if (extractLibrary(modulePath, cachedLib)) {
                    System.load(cachedLib.getAbsolutePath());
                    loaded = true;
                    XposedBridge.log(TAG + ": ✅ Loaded libdexkit.so from extracted cache: " + cachedLib.getAbsolutePath());
                    return true;
                }
            } catch (Throwable t) {
                XposedBridge.log(TAG + ": ❌ Cache extraction load failed: " + t.getMessage());
            }
        }

        XposedBridge.log(TAG + ": ❌ All libdexkit.so load strategies exhausted");
        return false;
    }

    private static boolean extractLibrary(String apkPath, File target) {
        try (ZipFile zip = new ZipFile(apkPath)) {
            String primaryAbi = Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "arm64-v8a";
            String[] candidates = new String[] {
                    "lib/" + primaryAbi + "/libdexkit.so",
                    "lib/arm64-v8a/libdexkit.so",
                    "lib/armeabi-v7a/libdexkit.so",
                    "lib/x86_64/libdexkit.so",
                    "lib/x86/libdexkit.so"
            };

            ZipEntry chosenEntry = null;
            for (String c : candidates) {
                chosenEntry = zip.getEntry(c);
                if (chosenEntry != null) break;
            }

            if (chosenEntry == null) {
                XposedBridge.log(TAG + ": ❌ libdexkit.so not found inside module APK: " + apkPath);
                return false;
            }

            File parent = target.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();

            try (InputStream in = zip.getInputStream(chosenEntry);
                 FileOutputStream out = new FileOutputStream(target)) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
                out.getFD().sync();
            }

            target.setReadable(true, false);
            target.setExecutable(true, false);
            return true;
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Error extracting library: " + t.getMessage());
            return false;
        }
    }
}
