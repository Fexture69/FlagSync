# Keep Xposed entry point and hook classes
-keep class com.flag.sync.xposed.** { *; }
-keepclassmembers class com.flag.sync.xposed.** { *; }

# Keep DexKit symbols & JNI
-keep class org.luckypray.dexkit.** { *; }
-dontwarn org.luckypray.dexkit.**

# Keep models & JSON data
-keep class com.flag.sync.model.** { *; }
-keep class com.flag.sync.logic.** { *; }

# Xposed API
-keep class de.robv.android.xposed.** { *; }
-dontwarn de.robv.android.xposed.**
