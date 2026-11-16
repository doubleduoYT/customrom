package com.hgsoft.overusebridge;

/**
 * Single, testable privacy boundary for optional Android app-level sampling.
 * The legacy overall monitor's active/rest counters are separate from this.
 */
final class AnalyticsConsentPolicy {
    private AnalyticsConsentPolicy() { }

    static boolean canRecord(boolean optedIn, boolean selectiveMode,
                             boolean usageAccess, boolean screenActive,
                             boolean foregroundKnown, boolean paused) {
        return optedIn && selectiveMode && usageAccess
                && screenActive && foregroundKnown && !paused;
    }
}
