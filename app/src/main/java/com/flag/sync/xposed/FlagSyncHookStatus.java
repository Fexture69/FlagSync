/*
 * Copyright (C) 2026 ViictorNunes (https://github.com/ViictorNunes/FlagSync).
 * Licensed under the GNU General Public License v3.0.
 */

package com.flag.sync.xposed;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import de.robv.android.xposed.XposedBridge;

public final class FlagSyncHookStatus {

    public static volatile boolean isDevOptionsHooked = false;
    public static volatile boolean isHighResHooked = false;
    public static volatile boolean isMaxUploadHooked = false;
    public static volatile boolean isForceReelHooked = false;
    public static volatile boolean isDeviceSpoofed = false;
    public static volatile boolean isReceiverReady = false;

    public static volatile boolean showStatusToast = true;

    private static volatile boolean hasShownToast = false;

    private FlagSyncHookStatus() {}

    public static void showStatusOverlay(Activity activity) {
        if (!showStatusToast || hasShownToast || activity == null) return;
        hasShownToast = true;

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                if (activity.isFinishing() || activity.isDestroyed()) return;

                float d = activity.getResources().getDisplayMetrics().density;
                LinearLayout card = new LinearLayout(activity);
                card.setOrientation(LinearLayout.VERTICAL);

                GradientDrawable bg = new GradientDrawable();
                bg.setColor(Color.parseColor("#F016161E"));
                bg.setCornerRadius(20 * d);
                bg.setStroke((int) Math.max(1.5f * d, 1), Color.parseColor("#554FD8C9"));
                card.setBackground(bg);

                int padH = (int) (18 * d);
                int padV = (int) (14 * d);
                card.setPadding(padH, padV, padH, padV);

                // Title
                TextView header = new TextView(activity);
                header.setText("⚡ FlagSync Xposed");
                header.setTextColor(Color.parseColor("#4FD8C9"));
                header.setTextSize(14.5f);
                header.setTypeface(null, Typeface.BOLD);
                header.setPadding(0, 0, 0, (int) (4 * d));
                card.addView(header);

                // Subtitle hint
                TextView hint = new TextView(activity);
                hint.setText("Long-press Home icon for Developer Options");
                hint.setTextColor(Color.parseColor("#8E8E93"));
                hint.setTextSize(11f);
                hint.setPadding(0, 0, 0, (int) (8 * d));
                card.addView(hint);

                // Items list
                addRow(card, activity, d, "Developer Options", isDevOptionsHooked);
                addRow(card, activity, d, "High-Res Images", isHighResHooked);
                addRow(card, activity, d, "Max Upload Quality", isMaxUploadHooked);

                String reelLabel = getReelQualityLabel();
                addRow(card, activity, d, "Force Reels (" + reelLabel + ")", isForceReelHooked);

                String spoofLabel = getSpoofLabel();
                addRow(card, activity, d, "Device Spoof (" + spoofLabel + ")", isDeviceSpoofed);

                addRow(card, activity, d, "Config Sync Bridge", isReceiverReady);

                Toast toast = new Toast(activity.getApplicationContext());
                toast.setView(card);
                toast.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, (int) (60 * d));
                toast.setDuration(Toast.LENGTH_LONG);
                toast.show();

                XposedBridge.log("FlagSyncXposed: Displayed feature confirmation status card in Instagram");
            } catch (Throwable t) {
                XposedBridge.log("FlagSyncXposed: Could not display status toast: " + t.getMessage());
            }
        }, 1200);
    }

    private static void addRow(LinearLayout parent, Activity activity, float d, String title, boolean active) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, (int) (2 * d), 0, (int) (2 * d));

        TextView icon = new TextView(activity);
        icon.setText(active ? " ✓ " : " ○ ");
        icon.setTextColor(active ? Color.parseColor("#30D158") : Color.parseColor("#8E8E93"));
        icon.setTextSize(12f);
        icon.setTypeface(null, Typeface.BOLD);
        row.addView(icon);

        TextView label = new TextView(activity);
        label.setText(title);
        label.setTextColor(active ? Color.parseColor("#F2F2F7") : Color.parseColor("#8E8E93"));
        label.setTextSize(12f);
        row.addView(label);

        parent.addView(row);
    }

    private static String getReelQualityLabel() {
        int q = InstagramQualityHooks.forceReelQuality;
        if (q == Integer.MAX_VALUE) return "Max";
        if (q > 0) return q + "p";
        return "Auto";
    }

    private static String getSpoofLabel() {
        String s = InstagramQualityHooks.spoofDevice;
        if ("s26".equals(s)) return "S26 Ultra";
        if ("pixel".equals(s)) return "Pixel 11";
        return "Off";
    }
}
