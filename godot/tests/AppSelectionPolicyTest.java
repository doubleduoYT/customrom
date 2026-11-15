package com.hgsoft.overusebridge;

public final class AppSelectionPolicyTest {
    private static int count = 0;
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
        count++;
    }
    public static void main(String[] args) {
        String own = "com.hgsoft.overusecontroller.godot";
        String selected = "com.example.game,com.example.social";
        check(AppSelectionPolicy.canIntervene("com.example.game", selected, own, "", ""),
                "selected app should be allowed");
        check(!AppSelectionPolicy.canIntervene("com.example.learning", selected, own, "", ""),
                "unselected app must be excluded");
        check(!AppSelectionPolicy.canIntervene("com.android.dialer", "com.android.dialer", own, "", ""),
                "built-in dialer must be protected even if explicitly selected");
        check(!AppSelectionPolicy.canIntervene("com.example.dialer", "com.example.dialer", own,
                "com.example.dialer", ""), "default dialer role must be protected");
        check(!AppSelectionPolicy.canIntervene("com.example.sms", "com.example.sms", own,
                "", "com.example.sms"), "default SMS app must be protected");
        check(!AppSelectionPolicy.canIntervene("com.android.settings", "com.android.settings", own,
                "", ""), "Android Settings must be protected");
        check(!AppSelectionPolicy.canIntervene("com.google.android.apps.maps",
                "com.google.android.apps.maps", own, "", ""), "Maps must be protected");
        check(!AppSelectionPolicy.canIntervene(own, own, own, "", ""), "controller must be protected");
        check(!AppSelectionPolicy.canIntervene("", selected, own, "", ""),
                "unknown foreground must never be affected");
        check(!AppSelectionPolicy.canIntervene(null, selected, own, "", ""),
                "null foreground must never be affected");
        check(!AppSelectionPolicy.canIntervene("com.example.game", "", own, "", ""),
                "empty allowlist must never affect apps");
        check(AppSelectionPolicy.normalizedTargets(" com.example.game ; com.example.game, com.example.social ")
                .equals("com.example.game,com.example.social"), "target normalization/deduplication");
        check(AppSelectionPolicy.normalizedTargets("hello,com.example.game,?,com.foo.*")
                .equals("com.example.game"), "invalid package patterns are discarded");
        check(AppSelectionPolicy.canIntervene("COM.EXAMPLE.GAME", selected, own, "", ""),
                "case normalization");
        System.out.println("PASS: " + count + " app selection safety checks");
    }
}
