package com.hgsoft.overusebridge;

public final class UsageAnalyticsTest {
    private static int passed = 0;
    private static void check(boolean ok, String label) {
        if (!ok) throw new AssertionError(label);
        passed++;
    }

    public static void main(String[] args) {
        UsageAnalytics a = new UsageAnalytics();
        a.startDay("2026-11-16");
        String game = "com.example.game";
        a.observe(game, true, true, false, false, false, 1000, 1000);
        check(a.recordedApps() == 0, "disabled collection creates no records");

        a.observe(game, true, true, false, true, false, 2000, 1000);
        a.observe(game, true, true, false, true, true, 3000, 1000);
        check(a.report().contains(game + "\t2000\t1000\t0\t0"),
                "only selected app time and 1-5 stage time are counted");

        a.observe("", true, true, false, true, true, 4000, 1000);
        check(a.report().contains(game + "\t2000\t1000\t1\t0"),
                "known target to other-app transition creates one inferred exit");
        a.observe("", true, true, false, true, false, 5000, 1000);
        check(a.report().contains(game + "\t2000\t1000\t1\t0"),
                "remaining in other app never double-counts exit");
        a.observe(game, true, true, false, true, false, 6000, 1000);
        check(a.report().contains(game + "\t3000\t1000\t1\t1"),
                "return to the same target within 5 minutes counts reentry");

        a.observe(null, false, true, false, true, false, 7000, 1000);
        a.observe(game, true, true, false, true, false, 8000, 1000);
        check(a.report().contains(game + "\t4000\t1000\t1\t1"),
                "unknown foreground must not create an exit or reentry");
        a.observe("", true, false, false, true, false, 9000, 1000);
        a.observe(game, true, true, false, true, false, 10000, 1000);
        check(a.report().contains(game + "\t5000\t1000\t1\t1"),
                "screen-off does not count a voluntary app exit");

        a.observe("", true, true, true, true, true, 11000, 1000);
        a.observe(game, true, true, false, true, false, 12000, 1000);
        check(a.report().contains(game + "\t6000\t1000\t1\t1"),
                "pause clears transition state and excludes paused time");

        a.observe("", true, true, false, true, false, 13000, 1000);
        a.observe(game, true, true, false, true, false,
                13000 + UsageAnalytics.REENTRY_WINDOW_MS + 1, 1000);
        check(a.report().contains(game + "\t7000\t1000\t2\t1"),
                "reentry after five minutes does not count");

        String stored = a.encode();
        UsageAnalytics restored = UsageAnalytics.decode(stored);
        check(restored.report().equals(a.report()), "local aggregate records survive restart");
        restored.observe("", true, true, false, true, false, 900000, 1000);
        check(restored.report().contains(game + "\t7000\t1000\t2\t1"),
                "restart never invents an exit");
        restored.startDay("2026-11-17");
        check(restored.recordedApps() == 0, "new day deletes old daily aggregates");
        restored.observe(game, true, true, false, true, true, 1000000, 12000);
        check(restored.report().contains(game + "\t10000\t10000"),
                "single interval is capped at 10 seconds");
        restored.clear();
        check(restored.recordedApps() == 0, "clear removes all aggregate records");
        check(UsageAnalytics.decode("corrupt\ncom.example.game\tX\tY\tZ\tQ").recordedApps() == 0,
                "malformed stored statistics are ignored");

        UsageAnalytics switching = new UsageAnalytics();
        switching.startDay("2026-11-16");
        switching.observe("com.example.alpha", true, true, false, true, false, 100, 1000);
        switching.observe("com.example.beta", true, true, false, true, false, 200, 1000);
        switching.observe("com.example.alpha", true, true, false, true, false, 300, 1000);
        check(switching.report().contains("com.example.alpha\t2000\t0\t1\t1"),
                "A -> B -> A within five minutes counts the reentry to A");

        System.out.println("PASS: " + passed + " opt-in analytics regression checks");
    }
}
