package com.hgsoft.overusebridge;

/** Platform-independent, opt-in blur step calculation. */
final class BlurPolicy {
    private BlurPolicy() { }

    static int radiusDp(int stage, int maxRadiusDp) {
        int safeStage = Math.max(0, Math.min(5, stage));
        if (safeStage == 0) return 0;
        int safeMax = Math.max(5, Math.min(40, maxRadiusDp));
        return Math.round(safeMax * safeStage / 5.0f);
    }
}
