package com.hgsoft.overusebridge;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import org.godotengine.godot.Godot;
import org.godotengine.godot.plugin.GodotPlugin;
import org.godotengine.godot.plugin.UsedByGodot;

/** Godot Android plugin v2; native permission, service and overlay bridge. */
public class OveruseBridge extends GodotPlugin {
    public static final String PREFS = "overuse_godot_native";
    public OveruseBridge(Godot godot) {
        super(godot);
    }

    @Override
    public String getPluginName() {
        return "OveruseBridge";
    }

    private SharedPreferences prefs() {
        return getActivity().getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    @UsedByGodot
    public boolean hasOverlayPermission() {
        Activity activity = getActivity();
        return activity != null && (Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(activity));
    }

    @UsedByGodot
    public void requestOverlayPermission() {
        Activity activity = getActivity();
        if (activity == null || Build.VERSION.SDK_INT < 23) return;
        activity.runOnUiThread(() -> {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + activity.getPackageName()));
            try {
                activity.startActivity(intent);
            } catch (Exception exception) {
                activity.startActivity(new Intent(Settings.ACTION_SETTINGS));
            }
        });
    }

    private void send(String action, int stage) {
        Activity activity = getActivity();
        if (activity == null) return;
        Intent intent = new Intent(activity, OveruseMonitorService.class);
        intent.setAction(action);
        intent.putExtra("stage", stage);
        try {
            if (Build.VERSION.SDK_INT >= 26 && (action.equals("START") || action.equals("PREVIEW"))) {
                activity.startForegroundService(intent);
            } else {
                activity.startService(intent);
            }
        } catch (RuntimeException error) {
            prefs().edit().putString("overlay_status",
                    "START_ERROR_" + error.getClass().getSimpleName()).apply();
            android.util.Log.e("OveruseController", "Native monitor action rejected: " + action, error);
        }
    }

    @UsedByGodot public void startMonitor() { send("START", -1); }
    @UsedByGodot public void stopMonitor() { send("STOP", -1); }
    @UsedByGodot public void pauseMonitor() { send("PAUSE", -1); }
    @UsedByGodot public void resumeMonitor() { send("RESUME", -1); }
    @UsedByGodot public void resetMonitor() { send("RESET", -1); }
    @UsedByGodot public void previewStage(int stage) { send("PREVIEW", stage); }

    @UsedByGodot
    public void setConfig(int grace, int interval, int recovery, int strength, boolean color, boolean masks) {
        prefs().edit()
                .putInt("grace", Math.max(1, Math.min(30, grace)))
                .putInt("interval", Math.max(1, Math.min(15, interval)))
                .putInt("recovery", Math.max(1, Math.min(10, recovery)))
                .putInt("strength", Math.max(0, Math.min(100, strength)))
                .putBoolean("color", color)
                .putBoolean("masks", masks)
                .apply();
        if (isRunning()) send("REFRESH", -1);
    }

    @UsedByGodot
    public void setRootCpuEnabled(boolean enabled) {
        prefs().edit().putBoolean("cpu_root_enabled", enabled).apply();
        if (isRunning()) send("PERFORMANCE", -1);
    }

    @UsedByGodot
    public String getPerformanceStatus() {
        if (!prefs().getBoolean("cpu_root_enabled", false)) return "ROOT_DISABLED";
        return prefs().getString("cpu_root_status", "ROOT_READY_WAITING_STAGE");
    }

    @UsedByGodot
    public String getOverlayStatus() {
        SharedPreferences p = prefs();
        if (!p.getBoolean("running", false))
            return p.getString("overlay_status", "SERVICE_STOPPED");
        return p.getString("overlay_status", "SERVICE_STARTING");
    }

    @UsedByGodot
    public String getBridgeVersion() {
        return "0.6.0-android-cpufreq";
    }

    @UsedByGodot public boolean isRunning() { return prefs().getBoolean("running", false); }
    @UsedByGodot public int getStage() { return prefs().getInt("stage", 0); }
    @UsedByGodot public long getActiveMs() { return prefs().getLong("active_ms", 0L); }
    @UsedByGodot public long getNextStageMs() { return prefs().getLong("stage_at_ms", 600000L); }
    @UsedByGodot public long getPausedUntil() { return prefs().getLong("pause_until", 0L); }
}
