package com.hgsoft.overusebridge;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Process;
import android.provider.Telephony;
import android.telecom.TelecomManager;

import java.util.Locale;

/**
 * Permission-gated selected-app detector.
 *
 * If usage access is missing, the active package cannot be resolved, or the
 * package is protected, this class denies intervention. It never persists an
 * app-opening history or any foreground package name.
 */
final class ForegroundAppMonitor {
    private static final long INITIAL_SCAN_MS = 12L * 60L * 60L * 1000L;
    private final Context context;
    private final SharedPreferences prefs;
    private final UsageStatsManager usage;
    private long lastQueryMs;
    private String foreground = "";
    private String status = "SELECTIVE_DISABLED";

    ForegroundAppMonitor(Context context, SharedPreferences prefs) {
        this.context = context.getApplicationContext();
        this.prefs = prefs;
        this.usage = (UsageStatsManager) this.context.getSystemService(Context.USAGE_STATS_SERVICE);
    }

    boolean hasAccess() {
        AppOpsManager ops = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        return ops != null && ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(), context.getPackageName()) == AppOpsManager.MODE_ALLOWED;
    }

    String status() { return status; }

    boolean allowIntervention() {
        if (!prefs.getBoolean("selective_enabled", false)) {
            status = "SELECTIVE_DISABLED";
            return true; // legacy global effect remains unchanged unless user opts in
        }
        if (!hasAccess() || usage == null) {
            status = "USAGE_ACCESS_REQUIRED";
            return false;
        }
        String targets = AppSelectionPolicy.normalizedTargets(
                prefs.getString("selected_packages", ""));
        if (targets.isEmpty()) {
            status = "NO_SELECTED_APPS";
            return false;
        }
        String current = findForeground();
        if (current.isEmpty()) {
            status = "FOREGROUND_UNKNOWN";
            return false;
        }

        String dialer = "";
        String sms = "";
        try {
            TelecomManager telecom = (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
            if (telecom != null && telecom.getDefaultDialerPackage() != null)
                dialer = telecom.getDefaultDialerPackage().toLowerCase(Locale.ROOT);
            String defaultSms = Telephony.Sms.getDefaultSmsPackage(context);
            if (defaultSms != null) sms = defaultSms.toLowerCase(Locale.ROOT);
        } catch (SecurityException ignored) {
            // The static protection list still applies; unknown role details are not assumed.
        }

        if (AppSelectionPolicy.isProtected(current, context.getPackageName(), dialer, sms)) {
            status = "PROTECTED_APP";
            return false;
        }
        boolean allowed = AppSelectionPolicy.canIntervene(
                current, targets, context.getPackageName(), dialer, sms);
        status = allowed ? "TARGET_ACTIVE" : "NOT_SELECTED";
        return allowed;
    }

    private String findForeground() {
        long now = System.currentTimeMillis();
        long begin = lastQueryMs == 0L
                ? now - INITIAL_SCAN_MS
                : Math.max(0L, lastQueryMs - 1500L);
        try {
            UsageEvents events = usage.queryEvents(begin, now + 1L);
            if (events == null) {
                foreground = "";
                lastQueryMs = 0L;
                return "";
            }
            UsageEvents.Event event = new UsageEvents.Event();
            while (events.hasNextEvent()) {
                events.getNextEvent(event);
                int type = event.getEventType();
                String pkg = event.getPackageName();
                if (pkg == null) continue;
                if (type == UsageEvents.Event.ACTIVITY_RESUMED
                        || type == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    foreground = pkg.toLowerCase(Locale.ROOT);
                } else if ((type == UsageEvents.Event.ACTIVITY_PAUSED
                        || type == UsageEvents.Event.MOVE_TO_BACKGROUND)
                        && foreground.equalsIgnoreCase(pkg)) {
                    foreground = "";
                }
            }
            lastQueryMs = now;
            return foreground;
        } catch (RuntimeException ignored) {
            // On vendor ROMs / user-locked profiles, disable intervention safely.
            lastQueryMs = 0L;
            foreground = "";
            return "";
        }
    }
}
