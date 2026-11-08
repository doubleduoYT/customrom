package com.doubleduo.overusecontroller;

import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.WindowManager;

/**
 * Foreground-only usage controller for regular, non-root Android installs.
 *
 * Measures interactive, unlocked screen time. Overlay never receives touches.
 * Does NOT modify global animations, block apps, or request privileged settings.
 */
public final class OveruseService extends Service {
    public static final String PREF_FILE = "overuse_v03";
    public static final String ACTION_START = "com.doubleduo.overusecontroller.START";
    public static final String ACTION_STOP = "com.doubleduo.overusecontroller.STOP";
    public static final String ACTION_PAUSE = "com.doubleduo.overusecontroller.PAUSE";
    public static final String ACTION_RESUME = "com.doubleduo.overusecontroller.RESUME";
    public static final String ACTION_RESET = "com.doubleduo.overusecontroller.RESET";
    public static final String ACTION_PREVIEW = "com.doubleduo.overusecontroller.PREVIEW";
    public static final String ACTION_REFRESH = "com.doubleduo.overusecontroller.REFRESH";
    public static final String EXTRA_STAGE = "stage";
    public static final String KEY_RUNNING = "running";
    public static final String KEY_STAGE = "stage";
    public static final String KEY_ACTIVE_MS = "active_ms";
    public static final String KEY_PAUSED_UNTIL = "paused_until";
    public static final String KEY_GRACE = "grace_minutes";
    public static final String KEY_INTERVAL = "interval_minutes";
    public static final String KEY_RECOVERY = "recovery_minutes";
    public static final String KEY_INTENSITY = "intensity";
    public static final String KEY_COLOR = "effect_color";
    public static final String KEY_MASKS = "effect_masks";

    private static final String CHANNEL = "overuse_controller_v03";
    private static final int FOREGROUND_ID = 2401;
    private static final int MAX_STAGE = 5;
    private static final long MINUTE = 60_000L;
    private static final long PAUSE_MS = 15L * MINUTE;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private WindowManager windowManager;
    private OveruseOverlayView overlay;
    private long lastTick;
    private long activeMs;
    private long recoveryMs;
    private long pausedUntil;
    private long nextStageAt;
    private int stage;
    private int ticks;
    private boolean started;

    public static void send(Context context, String action) {
        send(context, action, -1);
    }

    public static void send(Context context, String action, int stage) {
        Intent intent = new Intent(context, OveruseService.class);
        intent.setAction(action);
        if (stage >= 0) intent.putExtra(EXTRA_STAGE, stage);
        if (Build.VERSION.SDK_INT >= 26 &&
                (ACTION_START.equals(action) || ACTION_PREVIEW.equals(action))) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences(PREF_FILE, MODE_PRIVATE);
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        stage = clamp(prefs.getInt(KEY_STAGE, 0), 0, MAX_STAGE);
        activeMs = Math.max(0L, prefs.getLong(KEY_ACTIVE_MS, 0L));
        pausedUntil = prefs.getLong(KEY_PAUSED_UNTIL, 0L);
        nextStageAt = getNextStageAt();
        lastTick = SystemClock.elapsedRealtime();
        createChannel();
        startForeground(FOREGROUND_ID, makeNotification());
        started = true;
        handler.postDelayed(tick, 1000L);
        handler.postDelayed(visualTick, 2000L);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (action == null) action = ACTION_START;
        if (ACTION_STOP.equals(action)) {
            stage = 0;
            activeMs = 0L;
            recoveryMs = 0L;
            pausedUntil = 0L;
            save(false);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_PAUSE.equals(action)) {
            pausedUntil = System.currentTimeMillis() + PAUSE_MS;
            refreshOverlay();
        } else if (ACTION_RESUME.equals(action)) {
            pausedUntil = 0L;
            refreshOverlay();
        } else if (ACTION_RESET.equals(action)) {
            stage = 0;
            activeMs = 0L;
            recoveryMs = 0L;
            nextStageAt = getNextStageAt();
            refreshOverlay();
        } else if (ACTION_PREVIEW.equals(action)) {
            stage = clamp(intent.getIntExtra(EXTRA_STAGE, 0), 0, MAX_STAGE);
            if (stage == 0) {
                activeMs = 0L;
                nextStageAt = getNextStageAt();
            } else {
                nextStageAt = activeMs + intervalMs();
            }
            refreshOverlay();
        } else if (ACTION_REFRESH.equals(action) || ACTION_START.equals(action)) {
            nextStageAt = Math.max(activeMs + 1000L, getNextStageAt());
            refreshOverlay();
        }
        save(true);
        updateForeground();
        return START_STICKY;
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!started) return;
            long now = SystemClock.elapsedRealtime();
            long delta = Math.min(10_000L, Math.max(0L, now - lastTick));
            lastTick = now;
            if (isPaused()) {
                // While paused, neither escalation nor recovery consumes time.
            } else if (isScreenBeingUsed()) {
                activeMs += delta;
                recoveryMs = 0L;
                if (stage == 0 && activeMs >= graceMs()) {
                    changeStage(1);
                    nextStageAt = activeMs + intervalMs();
                } else if (stage > 0 && stage < MAX_STAGE && activeMs >= nextStageAt) {
                    changeStage(stage + 1);
                    nextStageAt = activeMs + intervalMs();
                }
            } else {
                recoveryMs += delta;
                if (stage > 0 && recoveryMs >= recoveryIntervalMs()) {
                    recoveryMs -= recoveryIntervalMs();
                    changeStage(stage - 1);
                    if (stage == 0) {
                        activeMs = 0L;
                        nextStageAt = graceMs();
                    }
                }
            }
            if (++ticks % 5 == 0) save(true);
            handler.postDelayed(this, 1000L);
        }
    };

    private final Runnable visualTick = new Runnable() {
        @Override public void run() {
            if (!started) return;
            if (overlay != null && !isPaused()) overlay.updateVisuals();
            handler.postDelayed(this, 2000L);
        }
    };

    private boolean isScreenBeingUsed() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        return pm != null && pm.isInteractive() &&
                (km == null || !km.isKeyguardLocked());
    }

    private boolean isPaused() {
        if (pausedUntil == 0L) return false;
        if (System.currentTimeMillis() < pausedUntil) return true;
        pausedUntil = 0L;
        refreshOverlay();
        updateForeground();
        save(true);
        return false;
    }

    private long graceMs() {
        return clamp(prefs.getInt(KEY_GRACE, 10), 1, 120) * MINUTE;
    }

    private long intervalMs() {
        return clamp(prefs.getInt(KEY_INTERVAL, 2), 1, 30) * MINUTE;
    }

    private long recoveryIntervalMs() {
        return clamp(prefs.getInt(KEY_RECOVERY, 1), 1, 30) * MINUTE;
    }

    private long getNextStageAt() {
        long grace = graceMs();
        if (stage <= 0) return grace;
        long interval = intervalMs();
        return Math.max(activeMs + 1000L, grace + stage * interval);
    }

    private void changeStage(int newStage) {
        newStage = clamp(newStage, 0, MAX_STAGE);
        if (stage == newStage) return;
        stage = newStage;
        refreshOverlay();
        updateForeground();
        save(true);
    }

    private void refreshOverlay() {
        if (stage == 0 || pausedUntil > System.currentTimeMillis() ||
                (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this))) {
            removeOverlay();
            return;
        }
        try {
            if (overlay == null) {
                overlay = new OveruseOverlayView(this);
                int windowType = Build.VERSION.SDK_INT >= 26
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
                WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT,
                        windowType,
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                        PixelFormat.TRANSLUCENT);
                if (windowManager != null) windowManager.addView(overlay, params);
            }
            if (overlay != null) overlay.configure(
                    stage, clamp(prefs.getInt(KEY_INTENSITY, 65), 0, 100),
                    prefs.getBoolean(KEY_COLOR, true),
                    prefs.getBoolean(KEY_MASKS, true));
        } catch (RuntimeException e) {
            removeOverlay();
        }
    }

    private void removeOverlay() {
        if (overlay != null) {
            try {
                if (windowManager != null) windowManager.removeViewImmediate(overlay);
            } catch (RuntimeException ignored) { }
            overlay = null;
        }
    }

    private void save(boolean running) {
        prefs.edit().putBoolean(KEY_RUNNING, running)
                .putInt(KEY_STAGE, stage)
                .putLong(KEY_ACTIVE_MS, activeMs)
                .putLong(KEY_PAUSED_UNTIL, pausedUntil)
                .apply();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL, "Overuse Controller", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("사용 시간과 단계, 일시정지를 표시합니다");
            NotificationManager manager = (NotificationManager)
                    getSystemService(NOTIFICATION_SERVICE);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private PendingIntent serviceAction(String action, int requestCode) {
        Intent intent = new Intent(this, OveruseService.class);
        intent.setAction(action);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getService(this, requestCode, intent, flags);
    }

    private Notification makeNotification() {
        boolean paused = pausedUntil > System.currentTimeMillis();
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent launch = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), flags);

        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);
        builder.setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(paused ? "Overuse Controller · 일시정지" :
                        "Overuse Controller · " + stage + "/5 단계")
                .setContentText(paused ? "15분 휴식 중 · 탭하여 설정 열기" :
                        "화면 사용 시간 " + formatMinutes(activeMs))
                .setContentIntent(launch)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .addAction(android.R.drawable.ic_media_pause,
                        paused ? "계속하기" : "15분 쉬기",
                        serviceAction(paused ? ACTION_RESUME : ACTION_PAUSE, 1))
                .addAction(android.R.drawable.ic_menu_close_clear_cancel,
                        "종료", serviceAction(ACTION_STOP, 2));
        return builder.build();
    }

    private String formatMinutes(long ms) {
        long minutes = ms / MINUTE;
        return (minutes / 60) + "시간 " + (minutes % 60) + "분";
    }

    private void updateForeground() {
        if (started) {
            try {
                NotificationManager manager = (NotificationManager)
                        getSystemService(NOTIFICATION_SERVICE);
                if (manager != null) manager.notify(FOREGROUND_ID, makeNotification());
            } catch (RuntimeException ignored) { }
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        started = false;
        handler.removeCallbacksAndMessages(null);
        removeOverlay();
        stopForeground(true);
        super.onDestroy();
    }
}
