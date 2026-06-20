package com.doubleduo.overusecontroller;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.view.WindowManager;

public class OveruseService extends Service {
    private static final String CHANNEL_ID = "overuse_mitigation";
    private static final int NOTIFICATION_ID = 2401;
    private static final int MAX_STAGE = 5;
    private static final long GRACE_MS = 10L * 60L * 1000L;
    private static final long STAGE_UP_INTERVAL_MS = 2L * 60L * 1000L;
    private static final long RECOVERY_INTERVAL_MS = 1L * 60L * 1000L;
    private static final long VISUAL_TICK_MS = 500L;

    private final Handler handler = new Handler();
    private WindowManager windowManager;
    private OveruseOverlayView overlayView;
    private boolean overlayAdded;
    private boolean screenOn = true;
    private long screenOnAccumulatedMs = 0L;
    private long screenOffAccumulatedMs = 0L;
    private long lastTickTimeMs;
    private long nextStageUpAtMs = GRACE_MS;
    private int stage = 0;

    public static void start(Context context) {
        Intent i = new Intent(context, OveruseService.class);
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i);
        else context.startService(i);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createChannelIfNeeded();
        startForeground(NOTIFICATION_ID, buildNotification("Overuse mitigation running", "Current stage: 0"));
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        registerScreenReceiver();
        lastTickTimeMs = System.currentTimeMillis();
        handler.post(tickRunnable);
        handler.post(visualRunnable);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    private final Runnable tickRunnable = new Runnable() {
        @Override public void run() {
            long now = System.currentTimeMillis();
            long delta = Math.max(0, now - lastTickTimeMs);
            lastTickTimeMs = now;

            if (screenOn) {
                screenOnAccumulatedMs += delta;
                screenOffAccumulatedMs = 0L;
                if (stage == 0 && screenOnAccumulatedMs >= GRACE_MS) {
                    setStage(1, true);
                    nextStageUpAtMs = screenOnAccumulatedMs + STAGE_UP_INTERVAL_MS;
                } else if (stage > 0 && stage < MAX_STAGE && screenOnAccumulatedMs >= nextStageUpAtMs) {
                    setStage(stage + 1, true);
                    nextStageUpAtMs = screenOnAccumulatedMs + STAGE_UP_INTERVAL_MS;
                }
            } else {
                screenOffAccumulatedMs += delta;
                if (stage > 0 && screenOffAccumulatedMs >= RECOVERY_INTERVAL_MS) {
                    screenOffAccumulatedMs = 0L;
                    setStage(stage - 1, false);
                    if (stage == 0) {
                        screenOnAccumulatedMs = 0L;
                        nextStageUpAtMs = GRACE_MS;
                    }
                }
            }
            handler.postDelayed(this, 1000L);
        }
    };

    private final Runnable visualRunnable = new Runnable() {
        @Override public void run() {
            if (overlayView != null) overlayView.tickVisuals();
            handler.postDelayed(this, VISUAL_TICK_MS);
        }
    };

    private void setStage(int newStage, boolean increased) {
        int clamped = Math.max(0, Math.min(MAX_STAGE, newStage));
        if (clamped == stage) return;
        stage = clamped;
        Settings.System.putInt(getContentResolver(), "overuse_mitigation_stage", stage);
        applyOverlayStage();
        applyAnimationScaleForStage();
        String title = increased ? "단계가 높아졌습니다" : "단계가 낮아졌습니다";
        String text = "현재 " + stage + "단계입니다";
        notifyEvent(title, text);
    }

    private void applyOverlayStage() {
        if (stage <= 0) {
            if (overlayView != null) overlayView.setStage(0);
            return;
        }
        ensureOverlay();
        overlayView.setStage(stage);
    }

    private void ensureOverlay() {
        if (overlayAdded || windowManager == null) return;
        overlayView = new OveruseOverlayView(this);
        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        try {
            windowManager.addView(overlayView, lp);
            overlayAdded = true;
        } catch (Throwable ignored) {
            overlayAdded = false;
        }
    }

    private void applyAnimationScaleForStage() {
        float scale;
        switch (stage) {
            case 0: scale = 1.0f; break;
            case 1: scale = 1.15f; break;
            case 2: scale = 1.35f; break;
            case 3: scale = 1.65f; break;
            case 4: scale = 2.0f; break;
            default: scale = 2.5f; break;
        }
        try {
            Settings.Global.putFloat(getContentResolver(), Settings.Global.WINDOW_ANIMATION_SCALE, scale);
            Settings.Global.putFloat(getContentResolver(), Settings.Global.TRANSITION_ANIMATION_SCALE, scale);
            Settings.Global.putFloat(getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, scale);
        } catch (Throwable ignored) {
            // Some ROMs may block WRITE_SETTINGS for non-platform signed apps.
        }
    }

    private void registerScreenReceiver() {
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (Intent.ACTION_SCREEN_ON.equals(action)) {
                    screenOn = true;
                    lastTickTimeMs = System.currentTimeMillis();
                } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                    screenOn = false;
                    screenOffAccumulatedMs = 0L;
                    lastTickTimeMs = System.currentTimeMillis();
                }
            }
        }, f);
    }

    private void createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Overuse Mitigation", NotificationManager.IMPORTANCE_DEFAULT);
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    private Notification buildNotification(String title, String text) {
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return b.setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    private void notifyEvent(String title, String text) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIFICATION_ID + 1, buildNotification(title, text));
        startForeground(NOTIFICATION_ID, buildNotification("Overuse mitigation active", "Current stage: " + stage));
    }
}
