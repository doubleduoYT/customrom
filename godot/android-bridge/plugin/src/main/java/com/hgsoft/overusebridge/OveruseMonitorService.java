package com.hgsoft.overusebridge;

import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;

import java.util.Random;

/** Android service for the Godot UI; runs only after a user presses Start. */
public final class OveruseMonitorService extends Service {
    private static final int FOREGROUND_ID = 6014;
    private static final String CHANNEL_ID = "overuse_godot_monitor";
    private static final long MINUTE = 60000L;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private WindowManager wm;
    private TintOverlay overlay;
    private boolean alive;
    private int stage;
    private long activeMs;
    private long recoveryMs;
    private long stageAtMs;
    private long pauseUntil;
    private long lastTick;
    private int ticks;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!alive) return;
            long now = SystemClock.elapsedRealtime();
            long delta = Math.max(0L, Math.min(10000L, now - lastTick));
            lastTick = now;
            if (pauseUntil > 0 && System.currentTimeMillis() >= pauseUntil) {
                pauseUntil = 0L;
                refreshOverlay();
            }
            if (pauseUntil == 0L) {
                if (screenInUse()) {
                    activeMs += delta;
                    recoveryMs = 0L;
                    if (stage == 0 && activeMs >= graceMs()) {
                        setStage(1);
                    } else if (stage > 0 && stage < 5 && activeMs >= stageAtMs) {
                        setStage(stage + 1);
                    }
                } else {
                    recoveryMs += delta;
                    if (stage > 0 && recoveryMs >= recoveryIntervalMs()) {
                        recoveryMs -= recoveryIntervalMs();
                        setStage(stage - 1);
                        if (stage == 0) {
                            activeMs = 0L;
                            stageAtMs = graceMs();
                        } else {
                            stageAtMs = activeMs + intervalMs();
                        }
                    }
                }
            }
            if (++ticks % 2 == 0) {
                // Recheck after lock/unlock, app switching, permission changes,
                // and manufacturer overlay restrictions.
                refreshOverlay();
                save(true);
            }
            handler.postDelayed(this, 1000L);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences(OveruseBridge.PREFS, MODE_PRIVATE);
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        stage = Math.max(0, Math.min(5, prefs.getInt("stage", 0)));
        activeMs = Math.max(0L, prefs.getLong("active_ms", 0L));
        stageAtMs = prefs.getLong("stage_at_ms", graceMs());
        pauseUntil = prefs.getLong("pause_until", 0L);
        lastTick = SystemClock.elapsedRealtime();

        createNotificationChannel();
        startForeground(FOREGROUND_ID, notification());
        alive = true;
        save(true);
        refreshOverlay();
        handler.postDelayed(tick, 1000L);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? "START" : intent.getAction();
        if (action == null) action = "START";
        if ("STOP".equals(action)) {
            stage = 0;
            activeMs = 0L;
            recoveryMs = 0L;
            stageAtMs = graceMs();
            pauseUntil = 0L;
            save(false);
            stopSelf();
            return START_NOT_STICKY;
        }
        if ("PAUSE".equals(action)) {
            pauseUntil = System.currentTimeMillis() + 15L * MINUTE;
        } else if ("RESUME".equals(action)) {
            pauseUntil = 0L;
        } else if ("RESET".equals(action)) {
            stage = 0;
            activeMs = 0L;
            recoveryMs = 0L;
            stageAtMs = graceMs();
        } else if ("PREVIEW".equals(action)) {
            stage = Math.max(0, Math.min(5, intent.getIntExtra("stage", 0)));
            stageAtMs = activeMs + intervalMs();
        } else if ("REFRESH".equals(action)) {
            stageAtMs = stage == 0 ? graceMs() : activeMs + intervalMs();
        }
        refreshOverlay();
        save(true);
        notifyUpdated();
        return START_STICKY;
    }

    private boolean screenInUse() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        return pm != null && pm.isInteractive() && (km == null || !km.isKeyguardLocked());
    }

    private long graceMs() { return Math.max(1, prefs.getInt("grace", 10)) * MINUTE; }
    private long intervalMs() { return Math.max(1, prefs.getInt("interval", 2)) * MINUTE; }
    private long recoveryIntervalMs() { return Math.max(1, prefs.getInt("recovery", 1)) * MINUTE; }

    private void setStage(int value) {
        value = Math.max(0, Math.min(5, value));
        if (stage == value) return;
        stage = value;
        stageAtMs = stage == 0 ? graceMs() : activeMs + intervalMs();
        refreshOverlay();
        save(true);
        notifyUpdated();
    }

    private void save(boolean running) {
        prefs.edit()
                .putBoolean("running", running)
                .putInt("stage", stage)
                .putLong("active_ms", activeMs)
                .putLong("stage_at_ms", stageAtMs)
                .putLong("pause_until", pauseUntil)
                .apply();
    }

    private void markOverlay(String state) {
        prefs.edit().putString("overlay_status", state).apply();
    }

    private void refreshOverlay() {
        if (stage <= 0) {
            clearOverlay();
            markOverlay("WAITING_STAGE");
            return;
        }
        if (pauseUntil > System.currentTimeMillis()) {
            clearOverlay();
            markOverlay("PAUSED");
            return;
        }
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            clearOverlay();
            markOverlay("NEED_OVERLAY_PERMISSION");
            return;
        }
        if (!screenInUse()) {
            clearOverlay();
            markOverlay("SCREEN_LOCKED");
            return;
        }
        if (wm == null) {
            clearOverlay();
            markOverlay("NO_WINDOW_MANAGER");
            return;
        }
        try {
            if (overlay == null) {
                TintOverlay candidate = new TintOverlay(this);
                int type = Build.VERSION.SDK_INT >= 26
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
                WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT,
                        type,
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                        PixelFormat.TRANSLUCENT);
                wm.addView(candidate, params);
                overlay = candidate;
                Log.i("OveruseController", "System-wide touch-through overlay attached");
            }
            overlay.invalidate();
            markOverlay("VISIBLE_SYSTEM_OVERLAY");
        } catch (RuntimeException error) {
            Log.e("OveruseController", "Failed to attach system overlay", error);
            clearOverlay();
            markOverlay("ERROR_" + error.getClass().getSimpleName());
        }
    }

    private void clearOverlay() {
        if (overlay == null) return;
        try {
            wm.removeViewImmediate(overlay);
        } catch (RuntimeException ignored) { }
        overlay = null;
    }

    private final class TintOverlay extends View {
        private final Paint paint = new Paint();
        private final Random random = new Random();
        TintOverlay(Context context) {
            super(context);
            setClickable(false);
            setFocusable(false);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int intensity = Math.max(0, Math.min(100, prefs.getInt("strength", 65)));
            if (stage <= 0 || intensity <= 0) return;
            if (prefs.getBoolean("color", true)) {
                int[] palette = {
                        Color.rgb(50, 75, 99),
                        Color.rgb(74, 59, 92),
                        Color.rgb(55, 91, 82),
                        Color.rgb(93, 72, 51)
                };
                int color = palette[random.nextInt(palette.length)];
                int alpha = Math.min(125, Math.round((25 + stage * 26) * intensity / 100f));
                canvas.drawColor(Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color)));
            }
            if (prefs.getBoolean("masks", true)) {
                for (int i = 0; i < stage; i++) {
                    float width = 10f + i * 11f;
                    float x = random.nextFloat() * Math.max(1f, getWidth() - width);
                    paint.setColor(Color.argb(Math.min(18, intensity / 8 + 5), 23, 33, 48));
                    canvas.drawRect(x, 0, x + width, getHeight(), paint);
                }
            }
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                "Overuse Controller", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("화면 사용 시간과 단계 제한 상태");
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) manager.createNotificationChannel(channel);
    }

    private PendingIntent actionIntent(String action, int id) {
        Intent intent = new Intent(this, OveruseMonitorService.class);
        intent.setAction(action);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getService(this, id, intent, flags);
    }

    private Notification notification() {
        boolean paused = pauseUntil > System.currentTimeMillis();
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        return builder.setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("Overuse Controller · " + stage + "/5 단계")
                .setContentText(paused ? "15분 일시정지 중" : "사용시간 " + activeMs / MINUTE + "분")
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(android.R.drawable.ic_media_pause,
                        paused ? "계속" : "일시정지",
                        actionIntent(paused ? "RESUME" : "PAUSE", 10))
                .addAction(android.R.drawable.ic_menu_close_clear_cancel,
                        "종료", actionIntent("STOP", 11))
                .build();
    }

    private void notifyUpdated() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(FOREGROUND_ID, notification());
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onDestroy() {
        alive = false;
        handler.removeCallbacksAndMessages(null);
        clearOverlay();
        save(false);
        markOverlay("SERVICE_STOPPED");
        stopForeground(true);
        super.onDestroy();
    }
}
