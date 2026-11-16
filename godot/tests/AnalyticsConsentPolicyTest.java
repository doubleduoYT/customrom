package com.hgsoft.overusebridge;

public final class AnalyticsConsentPolicyTest {
    private static int passed = 0;
    private static void check(boolean ok, String name) {
        if (!ok) throw new AssertionError(name);
        passed++;
    }
    public static void main(String[] args) {
        check(AnalyticsConsentPolicy.canRecord(true,true,true,true,true,false),
                "only complete opt-in and known foreground permits collection");
        check(!AnalyticsConsentPolicy.canRecord(false,true,true,true,true,false),
                "disabled collection must never save selected-app time");
        check(!AnalyticsConsentPolicy.canRecord(true,false,true,true,true,false),
                "global intervention mode is not permission to record app history");
        check(!AnalyticsConsentPolicy.canRecord(true,true,false,true,true,false),
                "usage access revoked");
        check(!AnalyticsConsentPolicy.canRecord(true,true,true,false,true,false),
                "locked screen cannot create app-level time");
        check(!AnalyticsConsentPolicy.canRecord(true,true,true,true,false,false),
                "unknown foreground app cannot create off-target time");
        check(!AnalyticsConsentPolicy.canRecord(true,true,true,true,true,true),
                "paused monitor cannot create app-level time");
        System.out.println("PASS: " + passed + " analytics consent checks");
    }
}
