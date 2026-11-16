package com.hgsoft.overusebridge;

public final class BlurPolicyTest {
    private static int cases = 0;
    private static void check(boolean pass, String label) {
        cases++;
        if (!pass) throw new AssertionError(label);
    }

    public static void main(String[] args) {
        check(BlurPolicy.radiusDp(0, 26) == 0, "no blur below first stage");
        check(BlurPolicy.radiusDp(5, 26) == 26, "stage 5 uses chosen maximum");
        check(BlurPolicy.radiusDp(5, 99) == 40, "radius clamps at 40 dp");
        check(BlurPolicy.radiusDp(5, -1) == 5, "invalid radius clamps safely");
        check(BlurPolicy.radiusDp(-10, 25) == 0, "stage lower-bound clamp");
        check(BlurPolicy.radiusDp(10, 25) == 25, "stage upper-bound clamp");
        int old = 0;
        for (int stage = 1; stage <= 5; stage++) {
            int current = BlurPolicy.radiusDp(stage, 30);
            check(current > old, "blur must increase each stage");
            old = current;
        }
        System.out.println("PASS: " + cases + " blur policy checks");
    }
}
