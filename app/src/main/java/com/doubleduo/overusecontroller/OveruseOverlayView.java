package com.doubleduo.overusecontroller;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.view.View;
import java.util.Random;

public class OveruseOverlayView extends FrameLayout {
    private final View colorLayer;
    private final ImageView[] blockers = new ImageView[5];
    private final Random random = new Random();
    private int stage = 0;

    public OveruseOverlayView(Context context) {
        super(context);
        setWillNotDraw(false);
        colorLayer = new View(context);
        addView(colorLayer, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        for (int i = 0; i < blockers.length; i++) {
            ImageView image = new ImageView(context);
            image.setImageResource(R.drawable.overuse_mask);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setAlpha(0f);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER);
            addView(image, lp);
            blockers[i] = image;
        }
    }

    public void setStage(int newStage) {
        stage = Math.max(0, Math.min(5, newStage));
        updateVisuals();
    }

    public void tickVisuals() {
        if (stage <= 0) {
            colorLayer.setBackgroundColor(Color.TRANSPARENT);
            for (ImageView blocker : blockers) blocker.setAlpha(0f);
            return;
        }
        updateVisuals();
    }

    private void updateVisuals() {
        int alpha = Math.min(190, 20 + stage * 30);
        int r = random.nextInt(256);
        int g = random.nextInt(256);
        int b = random.nextInt(256);
        colorLayer.setBackgroundColor(Color.argb(alpha, r, g, b));

        for (int i = 0; i < blockers.length; i++) {
            ImageView blocker = blockers[i];
            if (i < stage) {
                blocker.setAlpha(Math.min(0.85f, 0.18f + stage * 0.12f));
                float offset = (stage * 3f) * (i + 1);
                blocker.setTranslationX((random.nextBoolean() ? 1 : -1) * offset);
                blocker.setTranslationY((random.nextBoolean() ? 1 : -1) * offset);
                blocker.setRotation((random.nextFloat() - 0.5f) * stage * 2.5f);
                blocker.setScaleX(1f + stage * 0.015f);
                blocker.setScaleY(1f + stage * 0.015f);
            } else {
                blocker.setAlpha(0f);
            }
        }
    }
}
