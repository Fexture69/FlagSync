/*
 * Copyright (C) 2026 ViictorNunes (https://github.com/ViictorNunes/FlagSync).
 * Licensed under the GNU General Public License v3.0.
 */

package com.flag.sync.xposed;

import android.content.Context;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;
import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.MethodData;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class InstagramQualityHooks {

    private static final String TAG = "FlagSyncXposed";

    // Feature Flags (in-memory)
    public static volatile int forceReelQuality = 0; // 0 = Auto, 360, 480, 720, 1080, Integer.MAX_VALUE = Max
    public static volatile boolean highResolutionImages = false;
    public static volatile boolean maxUploadQuality = false;
    public static volatile String spoofDevice = ""; // "", "pixel", "s26"

    private static final String CACHE_FILE = "flagsync_quality_cache.json";
    private static final String HDR_MARKER = "mobileconfig/flagsync_hdr_marker";

    public static final String PIXEL = "pixel";
    public static final String S26 = "s26";

    /** Developer options for HDR video (MobileConfig overrides, mc_overrides.json format). */
    static final String HDR_OVERRIDES = "{\"66119:\":[\"0: : true\"],\"110796:\":[\"92: : true\"],"
            + "\"74463:\":[\"4: : true\",\"20: : false\"],\"86550:\":[\"9: : true\"],\"77821:\":[\"42: : true\"],"
            + "\"107732:\":[\"12: : true\"],\"124768:\":[\"32: : true\",\"12: : true\"],"
            + "\"102818:\":[\"2: : true\",\"6: : false\",\"1: : true\",\"0: : true\",\"3: : true\"],"
            + "\"50233:\":[\"11: : true\"],\"120903:\":[\"0: : true\"],\"124767:\":[\"31: : true\",\"13: : true\"],"
            + "\"64571:\":[\"12: : true\"],\"78165:\":[\"381: : false\",\"245: : true\",\"167: : false\",\"110: : true\",\"97: : false\"],"
            + "\"68437:\":[\"5: : false\"]}";

    private static final String[] DICT_CLASS_CANDIDATES = {
            "com.instagram.feed.media.Media",             // IG 442+
            "com.instagram.feed.media.LiveTreeMediaDict"  // IG <=441
    };
    private static final String[] VIDEO_VERSION_CLASS_CANDIDATES = {
            "com.instagram.api.schemas.ImmutablePandoVideoVersion",      // IG 442+
            "com.instagram.model.mediasize.ImmutablePandoVideoVersion"   // IG <=441
    };
    private static final int HEIGHT_HASH = "height".hashCode();

    private static final class Profile {
        final String manufacturer, brand, model, device, product, fingerprint, buildId, socManufacturer, socModel, platform;
        final Set<String> features;

        Profile(String manufacturer, String brand, String model, String device, String product, String fingerprint,
                String buildId, String socManufacturer, String socModel, String platform, String... features) {
            this.manufacturer = manufacturer;
            this.brand = brand;
            this.model = model;
            this.device = device;
            this.product = product;
            this.fingerprint = fingerprint;
            this.buildId = buildId;
            this.socManufacturer = socManufacturer;
            this.socModel = socModel;
            this.platform = platform;
            this.features = new HashSet<>(Arrays.asList(features));
        }
    }

    private static final Profile PIXEL_11_PRO_XL = new Profile("Google", "google", "Pixel 11 Pro XL", "kodiak", "kodiak",
            "google/kodiak/kodiak:17/CD1A.260714.001.A9/15938155:user/release-keys", "CD1A.260714.001.A9",
            "Google", "Tensor G6", "malibu",
            "com.google.android.feature.PIXEL_EXPERIENCE",
            "com.google.android.feature.PIXEL_2026_EXPERIENCE",
            "com.google.android.feature.GOOGLE_BUILD",
            "com.google.android.feature.GOOGLE_EXPERIENCE");

    private static final Profile GALAXY_S26_ULTRA = new Profile("samsung", "samsung", "SM-S948B", null, null,
            null, null, "QTI", "SM8850", null);

    private static final Pattern PRODUCT_PROP = Pattern.compile("ro\\.product\\.(?:[a-z_]+\\.)?(model|brand|manufacturer|device|name)");
    private static final Pattern FINGERPRINT_PROP = Pattern.compile("ro\\.(?:[a-z_]+\\.)?build\\.fingerprint");

    // --- INITIALIZATION & CACHE ---

    public static void initFromPrefs(Context context) {
        // 1. Try local cache in Instagram filesDir
        try {
            File cacheFile = new File(context.getFilesDir(), CACHE_FILE);
            if (cacheFile.exists()) {
                String jsonStr = new String(Files.readAllBytes(cacheFile.toPath()), StandardCharsets.UTF_8);
                JSONObject obj = new JSONObject(jsonStr);
                forceReelQuality = obj.optInt("forceReelQuality", 0);
                highResolutionImages = obj.optBoolean("highResolutionImages", false);
                maxUploadQuality = obj.optBoolean("maxUploadQuality", false);
                spoofDevice = obj.optString("spoofDevice", "");
                XposedBridge.log(TAG + ": Loaded quality settings from local cache: reels=" + forceReelQuality
                        + ", highRes=" + highResolutionImages + ", maxUpload=" + maxUploadQuality + ", spoof=" + spoofDevice);
                return;
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Error reading quality cache: " + t.getMessage());
        }

        // 2. Fallback to XSharedPreferences
        try {
            XSharedPreferences xPrefs = new XSharedPreferences("com.flag.sync", "flag_sync_prefs");
            xPrefs.makeWorldReadable();
            xPrefs.reload();
            if (xPrefs.hasFileChanged() || xPrefs.getAll().size() > 0) {
                forceReelQuality = xPrefs.getInt("force_reel_quality", 0);
                highResolutionImages = xPrefs.getBoolean("high_res_images", false);
                maxUploadQuality = xPrefs.getBoolean("max_upload_quality", false);
                spoofDevice = xPrefs.getString("spoof_device", "");
                XposedBridge.log(TAG + ": Loaded quality settings from XSharedPreferences");
                saveCache(context);
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Error reading XSharedPreferences: " + t.getMessage());
        }
    }

    public static void saveCache(Context context) {
        try {
            File cacheFile = new File(context.getFilesDir(), CACHE_FILE);
            JSONObject obj = new JSONObject();
            obj.put("forceReelQuality", forceReelQuality);
            obj.put("highResolutionImages", highResolutionImages);
            obj.put("maxUploadQuality", maxUploadQuality);
            obj.put("spoofDevice", spoofDevice != null ? spoofDevice : "");

            File tmp = new File(cacheFile.getPath() + ".tmp");
            try (FileOutputStream fos = new FileOutputStream(tmp)) {
                fos.write(obj.toString().getBytes(StandardCharsets.UTF_8));
                fos.getFD().sync();
            }
            if (cacheFile.exists()) cacheFile.delete();
            tmp.renameTo(cacheFile);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Failed to save quality cache: " + t.getMessage());
        }
    }

    // --- DEVICE SPOOFING ---

    private static Profile profile() {
        if (PIXEL.equals(spoofDevice)) return PIXEL_11_PRO_XL;
        if (S26.equals(spoofDevice)) return GALAXY_S26_ULTRA;
        return null;
    }

    public static void applyDeviceSpoof(ClassLoader classLoader) {
        Profile p = profile();
        if (p == null) return;
        try {
            setField(Build.class, "MANUFACTURER", p.manufacturer);
            setField(Build.class, "BRAND", p.brand);
            setField(Build.class, "MODEL", p.model);
            setField(Build.class, "DEVICE", p.device);
            setField(Build.class, "PRODUCT", p.product);
            setField(Build.class, "HARDWARE", p.device);
            setField(Build.class, "BOARD", p.device);
            setField(Build.class, "FINGERPRINT", p.fingerprint);
            setField(Build.class, "ID", p.buildId);
            setField(Build.class, "DISPLAY", p.buildId);
            setField(Build.class, "SOC_MANUFACTURER", p.socManufacturer);
            setField(Build.class, "SOC_MODEL", p.socModel);

            XC_MethodHook propsHook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    String v = prop(p, (String) param.args[0]);
                    if (v != null) param.setResult(v);
                }
            };
            Class<?> sp = XposedHelpers.findClass("android.os.SystemProperties", classLoader);
            XposedHelpers.findAndHookMethod(sp, "get", String.class, propsHook);
            XposedHelpers.findAndHookMethod(sp, "get", String.class, String.class, propsHook);

            if (!p.features.isEmpty()) {
                XC_MethodHook featureHook = new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (p.features.contains(param.args[0])) param.setResult(true);
                    }
                };
                Class<?> pm = XposedHelpers.findClass("android.app.ApplicationPackageManager", classLoader);
                XposedHelpers.findAndHookMethod(pm, "hasSystemFeature", String.class, featureHook);
                XposedHelpers.findAndHookMethod(pm, "hasSystemFeature", String.class, int.class, featureHook);
            }
            XposedBridge.log(TAG + ": ✅ Spoofed device: " + p.manufacturer + " " + p.model);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": ❌ Device spoof error: " + t.getMessage());
        }
    }

    private static void setField(Class<?> c, String field, String value) {
        if (value == null) return;
        try {
            XposedHelpers.setStaticObjectField(c, field, value);
        } catch (Throwable ignored) {}
    }

    private static String prop(Profile p, String key) {
        if (key == null) return null;
        java.util.regex.Matcher m = PRODUCT_PROP.matcher(key);
        if (m.matches()) {
            switch (m.group(1)) {
                case "model": return p.model;
                case "brand": return p.brand;
                case "manufacturer": return p.manufacturer;
                case "device": return p.device;
                case "name": return p.product;
            }
        }
        if (FINGERPRINT_PROP.matcher(key).matches()) return p.fingerprint;
        switch (key) {
            case "ro.soc.manufacturer": return p.socManufacturer;
            case "ro.soc.model": return p.socModel;
            case "ro.board.platform": return p.platform;
            case "ro.build.id": return p.buildId;
            default: return null;
        }
    }

    // --- HDR OVERRIDES SYNC ---

    public static void syncHdrOverrides(Context context) {
        try {
            File marker = new File(context.getFilesDir(), HDR_MARKER);
            boolean on = profile() != null;
            if (on == marker.exists()) return;

            File file = new File(context.getFilesDir(), "mobileconfig/mc_overrides.json");
            String json = file.exists() ? new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8) : "{}";
            String updated = on ? mergeHdr(json, HDR_OVERRIDES) : removeHdr(json, HDR_OVERRIDES);

            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();

            File tmp = new File(file.getPath() + ".tmp");
            try (FileOutputStream fos = new FileOutputStream(tmp)) {
                fos.write(updated.getBytes(StandardCharsets.UTF_8));
                fos.getFD().sync();
            }
            if (file.exists()) file.delete();
            tmp.renameTo(file);

            if (on) {
                File markerParent = marker.getParentFile();
                if (markerParent != null && !markerParent.exists()) markerParent.mkdirs();
                try (FileOutputStream fos = new FileOutputStream(marker)) {
                    fos.write("1".getBytes(StandardCharsets.UTF_8));
                }
            } else {
                marker.delete();
            }
            XposedBridge.log(TAG + ": HDR developer options " + (on ? "applied" : "reverted"));
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": ❌ HDR overrides sync error: " + t.getMessage());
        }
    }

    private static String index(String entry) {
        int i = entry.indexOf(':');
        return i < 0 ? entry : entry.substring(0, i);
    }

    static String mergeHdr(String json, String add) throws Exception {
        JSONObject out = new JSONObject(json);
        JSONObject in = new JSONObject(add);
        for (Iterator<String> it = in.keys(); it.hasNext(); ) {
            String config = it.next();
            JSONArray adds = in.getJSONArray(config);
            JSONArray old = out.optJSONArray(config);
            JSONArray merged = new JSONArray();
            Set<String> replaced = new HashSet<>();
            for (int i = 0; i < adds.length(); i++) replaced.add(index(adds.getString(i)));
            if (old != null) {
                for (int i = 0; i < old.length(); i++) {
                    if (!replaced.contains(index(old.getString(i)))) merged.put(old.getString(i));
                }
            }
            for (int i = 0; i < adds.length(); i++) merged.put(adds.getString(i));
            out.put(config, merged);
        }
        return out.toString();
    }

    static String removeHdr(String json, String add) throws Exception {
        JSONObject out = new JSONObject(json);
        JSONObject in = new JSONObject(add);
        for (Iterator<String> it = in.keys(); it.hasNext(); ) {
            String config = it.next();
            JSONArray old = out.optJSONArray(config);
            if (old == null) continue;
            Set<String> drop = new HashSet<>();
            JSONArray adds = in.getJSONArray(config);
            for (int i = 0; i < adds.length(); i++) drop.add(adds.getString(i));
            JSONArray kept = new JSONArray();
            for (int i = 0; i < old.length(); i++) {
                if (!drop.contains(old.getString(i))) kept.put(old.getString(i));
            }
            if (kept.length() == 0) out.remove(config);
            else out.put(config, kept);
        }
        return out.toString();
    }

    // --- DEXKIT HOOKS ---

    public static void installDexKitHooks(DexKitBridge bridge, ClassLoader classLoader) {
        installHighResImageHook(bridge, classLoader);
        installMaxUploadQualityHook(bridge, classLoader);
        installForceReelQualityHook(bridge, classLoader);
    }

    private static void installHighResImageHook(DexKitBridge bridge, ClassLoader classLoader) {
        try {
            List<MethodData> pickers = bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create()
                    .returnType("com.instagram.model.mediasize.ExtendedImageUrl")
                    .paramTypes("java.lang.Integer", "java.util.List", "int")));

            if (pickers == null || pickers.isEmpty()) return;

            int hooked = 0;
            for (MethodData md : pickers) {
                try {
                    Method m = md.getMethodInstance(classLoader);
                    Class<?> resultType = m.getReturnType();
                    Method width = resultType.getMethod("getWidth");
                    Method height = resultType.getMethod("getHeight");

                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam p) throws Throwable {
                            if (!highResolutionImages) return;
                            if (p.hasThrowable() || !(p.args[1] instanceof List)) return;

                            Object best = null;
                            long maxArea = 0;
                            for (Object candidate : (List<?>) p.args[1]) {
                                if (!resultType.isInstance(candidate)) continue;
                                Object w = width.invoke(candidate);
                                Object h = height.invoke(candidate);
                                if (!(w instanceof Number) || !(h instanceof Number)) continue;
                                long x = ((Number) w).longValue();
                                long y = ((Number) h).longValue();
                                if (x <= 0 || y <= 0 || x > 65536 || y > 65536) continue;
                                if (x * y > maxArea) {
                                    maxArea = x * y;
                                    best = candidate;
                                }
                            }
                            if (best != null) p.setResult(best);
                        }
                    });
                    hooked++;
                } catch (Throwable ignored) {}
            }
            if (hooked > 0) {
                XposedBridge.log(TAG + ": ✅ Hooked " + hooked + " High-Resolution Image picker(s)");
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": ❌ High-Res image hook error: " + t.getMessage());
        }
    }

    private static void installMaxUploadQualityHook(DexKitBridge bridge, ClassLoader classLoader) {
        try {
            List<MethodData> results = bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create()
                    .usingEqStrings(List.of("high_quality_media_upload"))
                    .returnType("boolean")
                    .paramCount(1)));

            if (results == null || results.isEmpty()) return;

            int hooked = 0;
            for (MethodData md : results) {
                try {
                    Method m = md.getMethodInstance(classLoader);
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (!maxUploadQuality) return;
                            param.setResult(Boolean.TRUE);
                        }
                    });
                    hooked++;
                } catch (Throwable ignored) {}
            }
            if (hooked > 0) {
                XposedBridge.log(TAG + ": ✅ Hooked " + hooked + " Max Upload Quality getter(s)");
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": ❌ Max upload quality hook error: " + t.getMessage());
        }
    }

    private static void installForceReelQualityHook(DexKitBridge bridge, ClassLoader classLoader) {
        try {
            Method videoVersionsGetter = null;
            for (String dictClass : DICT_CLASS_CANDIDATES) {
                try {
                    List<MethodData> results = bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create()
                            .declaredClass(dictClass)
                            .paramCount(0)
                            .usingEqStrings(List.of("video_versions"))));

                    if (results != null) {
                        for (MethodData md : results) {
                            Method m = md.getMethodInstance(classLoader);
                            if (m.getReturnType() == List.class) {
                                videoVersionsGetter = m;
                                break;
                            }
                        }
                    }
                    if (videoVersionsGetter != null) break;
                } catch (Throwable ignored) {}
            }

            String heightGetterName = null;
            for (String versionClass : VIDEO_VERSION_CLASS_CANDIDATES) {
                try {
                    List<MethodData> results = bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create()
                            .declaredClass(versionClass)
                            .paramCount(0)
                            .returnType("java.lang.Integer")
                            .usingNumbers((long) HEIGHT_HASH)));

                    if (results != null && !results.isEmpty()) {
                        heightGetterName = results.get(0).getName();
                        break;
                    }
                } catch (Throwable ignored) {}
            }

            if (videoVersionsGetter == null || heightGetterName == null) {
                XposedBridge.log(TAG + ": ⚠️ Force Reel Quality hook members not found (getter=" + videoVersionsGetter + ", height=" + heightGetterName + ")");
                return;
            }

            videoVersionsGetter.setAccessible(true);
            final String finalHeightGetter = heightGetterName;

            XposedBridge.hookMethod(videoVersionsGetter, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (forceReelQuality <= 0) return;
                    try {
                        Object result = param.getResult();
                        if (!(result instanceof List)) return;
                        List<?> versions = (List<?>) result;
                        if (versions.size() <= 1) return;

                        Object chosen = pickBestQuality(versions, forceReelQuality, finalHeightGetter);
                        if (chosen != null) {
                            param.setResult(Collections.singletonList(chosen));
                        }
                    } catch (Throwable t) {
                        XposedBridge.log(TAG + ": ❌ ForceReelQuality error: " + t.getMessage());
                    }
                }
            });
            XposedBridge.log(TAG + ": ✅ Hooked Force Reel Quality on " + videoVersionsGetter.getDeclaringClass().getName());
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": ❌ Force Reel Quality install error: " + t.getMessage());
        }
    }

    private static Object pickBestQuality(List<?> items, int desired, String heightGetterName) {
        Object best = null;
        int bestDelta = Integer.MAX_VALUE;
        int bestHeight = -1;
        for (Object item : items) {
            if (item == null) continue;
            try {
                Method m = item.getClass().getMethod(heightGetterName);
                Object hObj = m.invoke(item);
                if (!(hObj instanceof Integer)) continue;
                int h = (Integer) hObj;
                if (h <= 0) continue;

                if (desired == Integer.MAX_VALUE) {
                    if (h > bestHeight) {
                        bestHeight = h;
                        best = item;
                    }
                } else {
                    int delta = Math.abs(h - desired);
                    if (delta < bestDelta) {
                        bestDelta = delta;
                        best = item;
                    }
                }
            } catch (Throwable ignored) {}
        }
        return best;
    }
}
