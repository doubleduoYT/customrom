package com.doubleduo.overusecontroller;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.util.Random;

/**
 * Touch-through screen overlay. The effect is intentionally subtle:
 * no fast flashes, no interaction interception, and no system-wide animation changes.
 */
public final class OveruseOverlayView extends FrameLayout {
    private final View tint;
    private final ImageView[] masks = new ImageView[5];
    private final Random random = new Random();
    private int stage;
    private int intensity = 65;
    private boolean colorEnabled = true;
    private boolean masksEnabled = true;

    public OveruseOverlayView(Context context) {
        super(context);
        setClickable(false);
        setFocusable(false);

        tint = new View(context);
        tint.setClickable(false);
        addView(tint, new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        for (int i = 0; i < masks.length; i++) {
            ImageView mask = new ImageView(context);
            mask.setImageResource(R.drawable.overuse_mask);
            mask.setScaleType(ImageView.ScaleType.FIT_CENTER);
            mask.setAlpha(0f);
            mask.setClickable(false);
            addView(mask, new LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER));
            masks[i] = mask;
        }
        clear();
    }

    public void configure(int newStage, int newIntensity, boolean showColor, boolean showMasks) {
        stage = Math.max(0, Math.min(5, newStage));
        intensity = Math.max(0, Math.min(100, newIntensity));
        colorEnabled = showColor;
        masksEnabled = showMasks;
        updateVisuals();
    }

    public void updateVisuals() {
        if (stage == 0 || intensity == 0) {
            clear();
            return;
        }

        if (colorEnabled) {
            // Muted colors change every two seconds, not rapid high-contrast flashes.
            final int[] palette = {
                    Color.rgb(41, 69, 95), Color.rgb(73, 53, 91),
                    Color.rgb(43, 81, 80), Color.rgb(87, 70, 47),
                    Color.rgb(56, 64, 85)
            };
            int base = palette[random.nextInt(palette.length)];
            int alpha = Math.min(105, Math.round((stage * 15f + 5f) * intensity / 100f));
            tint.setBackgroundColor(Color.argb(alpha,
                    Color.red(base), Color.green(base), Color.blue(base)));
        } else {
            tint.setBackgroundColor(Color.TRANSPARENT);
        }

        for (int i = 0; i < masks.length; i++) {
            ImageView mask = masks[i];
            if (masksEnabled && i < stage) {
                float opacity = Math.min(0.12f,
                        (0.035f + stage * 0.02f) * (intensity / 100f));
                mask.setAlpha(opacity);
                float distance = (i + 1) * stage * 2.0f;
                mask.setTranslationX(random.nextBoolean() ? distance : -distance);
                mask.setTranslationY(random.nextBoolean() ? distance : -distance);
                mask.setRotation((random.nextFloat() - 0.5f) * stage * 1.7f);
            } else {
                mask.setAlpha(0f);
            }
        }
    }

    public void clear() {
        tint.setBackgroundColor(Color.TRANSPARENT);
        for (ImageView mask : masks) mask.setAlpha(0f);
    }
}
