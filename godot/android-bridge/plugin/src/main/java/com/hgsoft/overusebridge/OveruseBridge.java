package com.hgsoft.overusebridge;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.telecom.TelecomManager;
import android.provider.Telephony;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Date;
import java.text.SimpleDateFormat;
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
    public void setConfig(int grace, int interval, int recovery, int strength, boolean color, boolean masks, boolean smartRecovery) {
        prefs().edit()
                .putInt("grace", Math.max(1, Math.min(30, grace)))
                .putInt("interval", Math.max(1, Math.min(15, interval)))
                .putInt("recovery", Math.max(1, Math.min(10, recovery)))
                .putInt("strength", Math.max(0, Math.min(100, strength)))
                .putBoolean("color", color)
                .putBoolean("masks", masks)
                .putBoolean("smart_recovery", smartRecovery)
                .apply();
        if (isRunning()) send("REFRESH", -1);
    }


    /**
     * Tab-delimited launcher app lines (package<TAB>label), sorted by label.
     * No QUERY_ALL_PACKAGES / no usage history. Protected apps are omitted.
     */
    @UsedByGodot
    public String getLaunchableApps() {
        Activity activity = getActivity();
        if (activity == null) return "";
        try {
            PackageManager pm = activity.getPackageManager();
            Intent launcher = new Intent(Intent.ACTION_MAIN);
            launcher.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> found = pm.queryIntentActivities(launcher, 0);
            String defaultDialer = "";
            String defaultSms = "";
            try {
                TelecomManager telecom = (TelecomManager) activity.getSystemService(Context.TELECOM_SERVICE);
                if (telecom != null && telecom.getDefaultDialerPackage() != null)
                    defaultDialer = telecom.getDefaultDialerPackage();
                String sms = Telephony.Sms.getDefaultSmsPackage(activity);
                if (sms != null) defaultSms = sms;
            } catch (SecurityException ignored) { }
            Set<String> seen = new HashSet<>();
            List<String[]> rows = new ArrayList<>();
            for (ResolveInfo item : found) {
                if (item.activityInfo == null) continue;
                String pkg = item.activityInfo.packageName.toLowerCase(Locale.ROOT);
                if (!seen.add(pkg) || AppSelectionPolicy.isProtected(
                        pkg, activity.getPackageName(), defaultDialer, defaultSms)) continue;
                CharSequence labelValue = item.loadLabel(pm);
                String label = labelValue == null ? pkg : labelValue.toString();
                label = label.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ').trim();
                if (label.isEmpty()) label = pkg;
                rows.add(new String[]{pkg, label});
            }
            rows.sort((a, b) -> a[1].compareToIgnoreCase(b[1]));
            StringBuilder output = new StringBuilder();
            for (int i = 0; i < Math.min(300, rows.size()); i++) {
                if (output.length() != 0) output.append('\n');
                output.append(rows.get(i)[0]).append('\t').append(rows.get(i)[1]);
            }
            return output.toString();
        } catch (RuntimeException error) {
            android.util.Log.w("OveruseController", "Launcher app picker unavailable", error);
            return "";
        }
    }

    @UsedByGodot
    public void setBlurConfig(boolean enabled, int maxRadiusDp) {
        prefs().edit()
                .putBoolean("blur_enabled", enabled)
                .putInt("blur_max_dp", Math.max(5, Math.min(40, maxRadiusDp)))
                .apply();
        if (isRunning()) send("REFRESH", -1);
    }

    @UsedByGodot
    public String getBlurStatus() {
        if (!prefs().getBoolean("blur_enabled", false)) return "BLUR_OFF";
        if (Build.VERSION.SDK_INT < 31) return "BLUR_REQUIRES_ANDROID_12";
        return prefs().getString("blur_status", "BLUR_WAITING_STAGE");
    }

    @UsedByGodot
    public boolean hasUsageAccess() {
        Activity activity = getActivity();
        if (activity == null) return false;
        android.app.AppOpsManager ops = (android.app.AppOpsManager)
                activity.getSystemService(Context.APP_OPS_SERVICE);
        return ops != null && ops.checkOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), activity.getPackageName())
                == android.app.AppOpsManager.MODE_ALLOWED;
    }

    @UsedByGodot
    public void requestUsageAccess() {
        Activity activity = getActivity();
        if (activity == null) return;
        activity.runOnUiThread(() -> {
            try {
                activity.startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
            } catch (RuntimeException error) {
                activity.startActivity(new Intent(Settings.ACTION_SETTINGS));
            }
        });
    }

    @UsedByGodot
    public void setSelectiveConfig(boolean enabled, String packages) {
        SharedPreferences p = prefs();
        String targets = AppSelectionPolicy.normalizedTargets(packages);
        boolean policyChanged = p.getBoolean("selective_enabled", false) != enabled
                || !p.getString("selected_packages", "").equals(targets);
        p.edit()
                .putBoolean("selective_enabled", enabled)
                .putString("selected_packages", targets)
                .apply();
        // Old all-device usage must never carry over to a new target list.
        // RESET preserves the daily reporting counters, but resets stage/partial rest.
        if (isRunning()) send(policyChanged ? "RESET" : "REFRESH", -1);
    }

    @UsedByGodot
    public String getSelectionStatus() {
        if (!prefs().getBoolean("selective_enabled", false)) return "SELECTIVE_DISABLED";
        if (!hasUsageAccess()) return "USAGE_ACCESS_REQUIRED";
        return prefs().getString("selection_status", "WAITING_FOR_FOREGROUND");
    }

    @UsedByGodot
    public void setAnalyticsEnabled(boolean enabled) {
        SharedPreferences p = prefs();
        if (p.getBoolean("analytics_enabled", false) == enabled) return;
        p.edit().putBoolean("analytics_enabled", enabled).apply();
        if (isRunning()) send("ANALYTICS_CONFIG", -1);
    }

    private UsageAnalytics todayAnalytics() {
        UsageAnalytics result = UsageAnalytics.decode(prefs().getString("analytics_data", ""));
        result.startDay(new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()));
        return result;
    }

    @UsedByGodot
    public String getAnalyticsSummary() {
        String summary = todayAnalytics().summary();
        return (prefs().getBoolean("analytics_enabled", false) ? "" : "기록 중지 · ") + summary;
    }

    @UsedByGodot
    public String getAnalyticsReport() {
        return todayAnalytics().report();
    }

    @UsedByGodot
    public void clearAnalytics() {
        prefs().edit().remove("analytics_data").apply();
        if (isRunning()) send("CLEAR_ANALYTICS", -1);
    }

    /**
     * Share is only initiated by an explicit user press.
     * Includes selected package names; nothing is uploaded automatically.
     */
    @UsedByGodot
    public void shareAnalytics() {
        Activity activity = getActivity();
        if (activity == null) return;
        UsageAnalytics stats = todayAnalytics();
        if (stats.recordedApps() == 0) return;
        String day = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        String report = "Overuse Controller local usage estimate · " + day + "\n" + stats.report();
        activity.runOnUiThread(() -> {
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/plain");
            share.putExtra(Intent.EXTRA_TEXT, report);
            activity.startActivity(Intent.createChooser(share, "사용기록 내보내기"));
        });
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
        return "0.8.3-android-optin-analytics-preview";
    }

    @UsedByGodot public boolean isRunning() { return prefs().getBoolean("running", false); }
    @UsedByGodot public int getStage() { return prefs().getInt("stage", 0); }
    @UsedByGodot public long getActiveMs() { return prefs().getLong("active_ms", 0L); }
    @UsedByGodot public long getTodayActiveMs() { return prefs().getLong("today_active_ms", 0L); }
    @UsedByGodot public long getTodayRestMs() { return prefs().getLong("today_rest_ms", 0L); }
    @UsedByGodot public long getTodayTargetMs() { return prefs().getLong("today_target_ms", 0L); }
    @UsedByGodot public long getTodayOtherAppMs() { return prefs().getLong("today_other_app_ms", 0L); }
    @UsedByGodot public long getNextStageMs() { return prefs().getLong("stage_at_ms", 600000L); }
    @UsedByGodot public long getPausedUntil() { return prefs().getLong("pause_until", 0L); }
}
