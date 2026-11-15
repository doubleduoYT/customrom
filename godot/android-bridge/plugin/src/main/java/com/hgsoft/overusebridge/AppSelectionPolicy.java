package com.hgsoft.overusebridge;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Pure, offline testable package-target allowlist. Defaults to NO intervention. */
final class AppSelectionPolicy {
    private static final Set<String> ALWAYS_PROTECTED = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "android", "com.android.systemui", "com.android.settings", "com.google.android.settings",
            "com.android.phone", "com.android.server.telecom", "com.android.dialer",
            "com.android.incallui", "com.android.emergency", "com.google.android.dialer",
            "com.google.android.contacts", "com.android.contacts", "com.android.messaging",
            "com.google.android.apps.messaging", "com.samsung.android.dialer",
            "com.samsung.android.incallui", "com.samsung.android.messaging",
            "com.google.android.apps.maps", "com.android.launcher3",
            "com.google.android.apps.nexuslauncher", "com.sec.android.app.launcher")));

    private AppSelectionPolicy() { }

    static String normalizedTargets(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        Set<String> targets = new java.util.LinkedHashSet<>();
        for (String item : raw.split("[,;\\n\\r]+")) {
            String pkg = item.trim().toLowerCase(Locale.ROOT);
            if (pkg.length() < 120 && pkg.matches("[a-z][a-z0-9_]*(\\.[a-z0-9_]+)+")) {
                targets.add(pkg);
            }
            if (targets.size() >= 30) break;
        }
        return String.join(",", targets);
    }

    static boolean canIntervene(String foreground, String targets, String ownPackage,
                                String defaultDialer, String defaultSms) {
        if (foreground == null || foreground.isEmpty()) return false;
        String pkg = foreground.trim().toLowerCase(Locale.ROOT);
        if (pkg.equals(ownPackage) || pkg.equals(defaultDialer) || pkg.equals(defaultSms)
                || ALWAYS_PROTECTED.contains(pkg)) return false;
        String cleaned = normalizedTargets(targets);
        if (cleaned.isEmpty()) return false;
        return Arrays.asList(cleaned.split(",")).contains(pkg);
    }

    static boolean isProtected(String foreground, String ownPackage, String defaultDialer, String defaultSms) {
        if (foreground == null || foreground.isEmpty()) return true;
        String pkg = foreground.trim().toLowerCase(Locale.ROOT);
        return pkg.equals(ownPackage) || pkg.equals(defaultDialer) || pkg.equals(defaultSms)
                || ALWAYS_PROTECTED.contains(pkg);
    }
}
