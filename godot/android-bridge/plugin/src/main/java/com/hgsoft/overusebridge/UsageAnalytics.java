package com.hgsoft.overusebridge;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Pure-Java, per-day and per-selected-app aggregate usage metrics.
 * No activity timeline, message contents or screen pixels are stored.
 * "Exit" means an observed target -> known other foreground app transition,
 * not proof the user quit voluntarily or that this program caused it.
 */
final class UsageAnalytics {
    static final long REENTRY_WINDOW_MS = 5L * 60L * 1000L;
    private static final long MAX_DAY_MS = 24L * 60L * 60L * 1000L;
    private static final int MAX_APPS = 30;

    static final class Stats {
        long activeMs;
        long stageActiveMs;
        int exits;
        int reentries;
    }

    private String dayKey = "";
    private final LinkedHashMap<String, Stats> perApp = new LinkedHashMap<>();
    private String previousTarget = "";
    private String lastExitedTarget = "";
    private long lastExitUptime = -1L;

    void startDay(String key) {
        if (key == null || !key.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) return;
        if (!key.equals(dayKey)) {
            dayKey = key;
            perApp.clear();
            endObservation();
        }
    }

    void clear() {
        perApp.clear();
        endObservation();
    }

    void endObservation() {
        previousTarget = "";
        lastExitedTarget = "";
        lastExitUptime = -1L;
    }

    void observe(String packageName, boolean foregroundKnown, boolean screenActive,
                 boolean paused, boolean collecting, boolean atInterventionStage,
                 long uptimeMs, long deltaMs) {
        if (!collecting || paused || !screenActive || !foregroundKnown) {
            endObservation();
            return;
        }

        String target = packageName == null ? "" : packageName.trim().toLowerCase(Locale.ROOT);
        if (!target.isEmpty() && !validPackage(target)) {
            endObservation();
            return;
        }

        // Check reentry before registering the next exit. This also handles
        // switching from one selected app to another and back again.
        if (!target.isEmpty() && !target.equals(previousTarget)
                && target.equals(lastExitedTarget) && lastExitUptime >= 0L
                && uptimeMs >= lastExitUptime
                && uptimeMs - lastExitUptime <= REENTRY_WINDOW_MS) {
            Stats again = statsFor(target);
            if (again != null) again.reentries = Math.min(10000, again.reentries + 1);
            lastExitedTarget = "";
            lastExitUptime = -1L;
        }
        // Only known transitions away from a target count as inferred exits.
        if (!previousTarget.isEmpty() && !previousTarget.equals(target)) {
            Stats previous = statsFor(previousTarget);
            if (previous != null) previous.exits = Math.min(10000, previous.exits + 1);
            lastExitedTarget = previousTarget;
            lastExitUptime = uptimeMs;
        }
        if (lastExitUptime >= 0L && uptimeMs - lastExitUptime > REENTRY_WINDOW_MS) {
            lastExitedTarget = "";
            lastExitUptime = -1L;
        }
        if (!target.isEmpty()) {
            Stats current = statsFor(target);
            if (current != null) {
                long delta = Math.max(0L, Math.min(10000L, deltaMs));
                current.activeMs = Math.min(MAX_DAY_MS, current.activeMs + delta);
                if (atInterventionStage)
                    current.stageActiveMs = Math.min(MAX_DAY_MS, current.stageActiveMs + delta);
            }
        }
        previousTarget = target;
    }

    private Stats statsFor(String packageName) {
        if (!validPackage(packageName)) return null;
        Stats value = perApp.get(packageName);
        if (value == null && perApp.size() < MAX_APPS) {
            value = new Stats();
            perApp.put(packageName, value);
        }
        return value;
    }

    private static boolean validPackage(String s) {
        return s != null && s.length() < 120
                && s.matches("[a-z][a-z0-9_]*(\\.[a-z0-9_]+)+");
    }

    /** Storage format: local day header and a maximum of 30 package aggregate rows. */
    String encode() {
        StringBuilder out = new StringBuilder(dayKey);
        for (Map.Entry<String, Stats> e : perApp.entrySet()) {
            Stats x = e.getValue();
            out.append('\n').append(e.getKey()).append('\t').append(x.activeMs)
                    .append('\t').append(x.stageActiveMs).append('\t')
                    .append(x.exits).append('\t').append(x.reentries);
        }
        return out.toString();
    }

    static UsageAnalytics decode(String raw) {
        UsageAnalytics analytics = new UsageAnalytics();
        if (raw == null || raw.length() > 12000) return analytics;
        String[] lines = raw.split("\\n");
        if (lines.length == 0) return analytics;
        analytics.startDay(lines[0]);
        for (int i = 1; i < Math.min(lines.length, MAX_APPS + 1); i++) {
            String[] fields = lines[i].split("\\t");
            if (fields.length != 5 || !validPackage(fields[0])) continue;
            try {
                Stats x = analytics.statsFor(fields[0]);
                if (x == null) continue;
                x.activeMs = clampLong(Long.parseLong(fields[1]));
                x.stageActiveMs = Math.min(x.activeMs, clampLong(Long.parseLong(fields[2])));
                x.exits = clampInt(Integer.parseInt(fields[3]));
                x.reentries = Math.min(x.exits, clampInt(Integer.parseInt(fields[4])));
            } catch (NumberFormatException ignored) {
                analytics.perApp.remove(fields[0]);
            }
        }
        analytics.endObservation(); // Never restore old foreground transitions.
        return analytics;
    }

    private static long clampLong(long value) {
        return Math.max(0L, Math.min(MAX_DAY_MS, value));
    }

    private static int clampInt(int value) {
        return Math.max(0, Math.min(10000, value));
    }

    String report() {
        StringBuilder out = new StringBuilder("package\tactive_ms\tstage_active_ms\tobserved_exits\treentries_within_5min");
        for (Map.Entry<String, Stats> e : perApp.entrySet()) {
            Stats x = e.getValue();
            out.append('\n').append(e.getKey()).append('\t').append(x.activeMs)
                    .append('\t').append(x.stageActiveMs).append('\t')
                    .append(x.exits).append('\t').append(x.reentries);
        }
        return out.toString();
    }

    String summary() {
        long active = 0L;
        long inStage = 0L;
        int exits = 0;
        int returns = 0;
        for (Stats s : perApp.values()) {
            active += s.activeMs;
            inStage += s.stageActiveMs;
            exits += s.exits;
            returns += s.reentries;
        }
        return "대상 앱 " + (active / 60000L) + "분 · 1~5단계 사용 "
                + (inStage / 60000L) + "분 · 이탈 추정 " + exits
                + "회 · 5분 내 재진입 " + returns + "회";
    }

    int recordedApps() { return perApp.size(); }
}
