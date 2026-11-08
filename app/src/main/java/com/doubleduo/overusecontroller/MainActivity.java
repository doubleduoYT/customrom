package com.doubleduo.overusecontroller;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

/** Accessible, dependency-free dashboard for regular APK installs. */
public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(10, 16, 29);
    private static final int CARD = Color.rgb(22, 32, 50);
    private static final int CARD_INNER = Color.rgb(30, 44, 65);
    private static final int BLUE = Color.rgb(106, 174, 255);
    private static final int WHITE = Color.rgb(241, 246, 255);
    private static final int MUTED = Color.rgb(165, 181, 203);
    private static final int AMBER = Color.rgb(255, 186, 100);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private TextView stageText;
    private TextView statusText;
    private TextView activeText;
    private TextView nextText;
    private TextView permissionText;
    private ProgressBar progress;
    private Button mainButton;
    private Button pauseButton;

    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            refreshDashboard();
            handler.postDelayed(this, 2000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(OveruseService.PREF_FILE, MODE_PRIVATE);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(0);
        buildScreen();
        requestNotifications();
    }

    @Override protected void onResume() {
        super.onResume();
        refreshDashboard();
        handler.removeCallbacks(refresh);
        handler.postDelayed(refresh, 1000L);
    }

    @Override protected void onPause() {
        handler.removeCallbacks(refresh);
        super.onPause();
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        scroll.setClipToPadding(false);

        LinearLayout root = vertical();
        root.setPadding(dp(18), dp(22), dp(18), dp(28));
        if (Build.VERSION.SDK_INT >= 35) {
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                Insets system = insets.getInsets(WindowInsets.Type.systemBars());
                v.setPadding(dp(18) + system.left, dp(22) + system.top,
                        dp(18) + system.right, dp(28) + system.bottom);
                return insets;
            });
        }
        scroll.addView(root);
        setContentView(scroll);

        TextView eyebrow = text("HGSoft   /   DIGITAL WELLBEING", 11, BLUE, true);
        eyebrow.setLetterSpacing(0.13f);
        root.addView(eyebrow, wrapSpace(0, 13));

        root.addView(text("Overuse", 34, WHITE, true));
        root.addView(text("Controller", 34, WHITE, true), wrapSpace(0, 4));
        root.addView(text("방해하지 않고, 사용 습관을 부드럽게 조절해.", 13, MUTED, false),
                wrapSpace(0, 23));

        LinearLayout dashboard = card(CARD);
        root.addView(dashboard, wrapSpace(0, 15));
        statusText = text("●  보호 중지", 13, MUTED, true);
        dashboard.addView(statusText, wrapSpace(0, 17));

        stageText = text("0 / 5", 47, WHITE, true);
        dashboard.addView(stageText, wrapSpace(0, 0));
        dashboard.addView(text("CURRENT OVERUSE STAGE", 11, MUTED, false),
                wrapSpace(0, 16));
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(5);
        progress.setProgress(0);
        progress.setProgressTintList(ColorStateList.valueOf(BLUE));
        progress.setProgressBackgroundTintList(ColorStateList.valueOf(CARD_INNER));
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(9));
        progressParams.bottomMargin = dp(16);
        dashboard.addView(progress, progressParams);

        activeText = text("누적 사용 0분", 16, WHITE, true);
        dashboard.addView(activeText, wrapSpace(0, 7));
        nextText = text("첫 단계까지 10분", 13, MUTED, false);
        dashboard.addView(nextText);

        mainButton = button("보호 시작", BLUE, BG);
        root.addView(mainButton, wrapSpace(0, 9));
        mainButton.setOnClickListener(v -> {
            if (prefs.getBoolean(OveruseService.KEY_RUNNING, false)) {
                safeAction(OveruseService.ACTION_STOP, -1);
            } else if (ensureOverlayPermission()) {
                safeAction(OveruseService.ACTION_START, -1);
            }
        });

        pauseButton = button("15분 일시정지", CARD_INNER, WHITE);
        root.addView(pauseButton, wrapSpace(0, 23));
        pauseButton.setOnClickListener(v -> {
            boolean paused = prefs.getLong(OveruseService.KEY_PAUSED_UNTIL, 0L)
                    > System.currentTimeMillis();
            safeAction(paused ? OveruseService.ACTION_RESUME :
                    OveruseService.ACTION_PAUSE, -1);
        });

        section(root, "단계 설정", "시간을 직접 조정할 수 있어");
        addSeek(root, "첫 단계까지", OveruseService.KEY_GRACE, 10, 1, 30, "분");
        addSeek(root, "단계 상승 간격", OveruseService.KEY_INTERVAL, 2, 1, 15, "분");
        addSeek(root, "회복 간격", OveruseService.KEY_RECOVERY, 1, 1, 10, "분");

        section(root, "화면 효과", "모든 오버레이는 터치를 통과해");
        LinearLayout effects = card(CARD);
        root.addView(effects, wrapSpace(0, 10));
        addSwitch(effects, "부드러운 색상 오버레이", OveruseService.KEY_COLOR, true);
        addSwitch(effects, "화면 가림 마스크", OveruseService.KEY_MASKS, true);
        addIntensity(effects);

        section(root, "효과 테스트", "테스트 후 초기화하면 누적 시간도 초기화돼");
        LinearLayout previews = new LinearLayout(this);
        previews.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(previews, wrapSpace(0, 10));
        addPreview(previews, "1단계", 1);
        addPreview(previews, "3단계", 3);
        addPreview(previews, "5단계", 5);
        Button reset = button("단계 및 사용 시간 초기화", CARD_INNER, WHITE);
        root.addView(reset, wrapSpace(10, 11));
        reset.setOnClickListener(v -> safeAction(OveruseService.ACTION_RESET, -1));

        section(root, "권한 및 안전", "루팅 · TWRP 설치 없이 실행");
        LinearLayout safety = card(CARD);
        root.addView(safety, wrapSpace(0, 10));
        permissionText = text("", 13, MUTED, false);
        safety.addView(permissionText, wrapSpace(0, 11));
        Button permissionButton = button("오버레이 권한 설정 열기", CARD_INNER, WHITE);
        safety.addView(permissionButton, wrapSpace(0, 9));
        permissionButton.setOnClickListener(v -> openOverlaySettings());
        safety.addView(text("긴급전화 및 다른 앱의 터치는 차단하지 않아. "
                        + "언제든 알림에서 일시정지하거나 종료할 수 있어. "
                        + "실제 화면의 FPS나 시스템 애니메이션 설정은 변경하지 않아.",
                12, MUTED, false), wrapSpace(13, 0));

        root.addView(text("Overuse Controller  v0.3.0   ·   HGSoft",
                12, MUTED, false), wrapSpace(25, 4));
        refreshDashboard();
    }

    private void section(LinearLayout root, String title, String sub) {
        root.addView(text(title, 19, WHITE, true), wrapSpace(4, 5));
        root.addView(text(sub, 12, MUTED, false), wrapSpace(0, 1));
    }

    private void addSeek(LinearLayout root, String label, String key,
                         int fallback, int min, int max, String unit) {
        LinearLayout container = card(CARD);
        root.addView(container, wrapSpace(0, 10));
        TextView value = text("", 14, WHITE, true);
        int current = clamp(prefs.getInt(key, fallback), min, max);
        value.setText(label + "   ·   " + current + unit);
        container.addView(value, wrapSpace(0, 7));

        SeekBar bar = new SeekBar(this);
        bar.setMax(max - min);
        bar.setProgress(current - min);
        bar.setProgressTintList(ColorStateList.valueOf(BLUE));
        bar.setThumbTintList(ColorStateList.valueOf(BLUE));
        container.addView(bar);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar b, int progress, boolean user) {
                value.setText(label + "   ·   " + (min + progress) + unit);
            }
            @Override public void onStartTrackingTouch(SeekBar b) { }
            @Override public void onStopTrackingTouch(SeekBar b) {
                prefs.edit().putInt(key, min + b.getProgress()).apply();
                applyPreferences();
            }
        });
    }

    private void addSwitch(LinearLayout container, String label, String key, boolean fallback) {
        Switch toggle = new Switch(this);
        toggle.setText(label);
        toggle.setTextColor(WHITE);
        toggle.setTextSize(14);
        toggle.setPadding(0, dp(9), 0, dp(9));
        toggle.setChecked(prefs.getBoolean(key, fallback));
        toggle.setThumbTintList(new ColorStateList(
                new int[][]{ new int[]{android.R.attr.state_checked}, new int[]{} },
                new int[]{BLUE, MUTED}));
        container.addView(toggle);
        toggle.setOnCheckedChangeListener((v, checked) -> {
            prefs.edit().putBoolean(key, checked).apply();
            applyPreferences();
        });
    }

    private void addIntensity(LinearLayout container) {
        TextView label = text("", 14, WHITE, true);
        int current = clamp(prefs.getInt(OveruseService.KEY_INTENSITY, 65), 0, 100);
        label.setText("효과 강도   ·   " + current + "%");
        container.addView(label, wrapSpace(15, 7));
        SeekBar intensity = new SeekBar(this);
        intensity.setMax(100);
        intensity.setProgress(current);
        intensity.setProgressTintList(ColorStateList.valueOf(BLUE));
        intensity.setThumbTintList(ColorStateList.valueOf(BLUE));
        container.addView(intensity);
        intensity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar b, int value, boolean fromUser) {
                label.setText("효과 강도   ·   " + value + "%");
            }
            @Override public void onStartTrackingTouch(SeekBar b) { }
            @Override public void onStopTrackingTouch(SeekBar b) {
                prefs.edit().putInt(OveruseService.KEY_INTENSITY, b.getProgress()).apply();
                applyPreferences();
            }
        });
    }

    private void addPreview(LinearLayout row, String label, int stage) {
        Button b = button(label, CARD_INNER, WHITE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(48), 1);
        lp.rightMargin = dp(7);
        row.addView(b, lp);
        b.setOnClickListener(v -> {
            if (ensureOverlayPermission()) {
                safeAction(OveruseService.ACTION_PREVIEW, stage);
            }
        });
    }

    private void applyPreferences() {
        if (prefs.getBoolean(OveruseService.KEY_RUNNING, false)) {
            safeAction(OveruseService.ACTION_REFRESH, -1);
        }
    }

    private void safeAction(String action, int stage) {
        try {
            OveruseService.send(this, action, stage);
            handler.postDelayed(this::refreshDashboard, 250L);
        } catch (RuntimeException error) {
            Toast.makeText(this,
                    "서비스를 실행하지 못했어: " + error.getClass().getSimpleName(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private boolean ensureOverlayPermission() {
        if (Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this)) return true;
        Toast.makeText(this, "다른 앱 위에 표시 권한을 먼저 허용해 줘.",
                Toast.LENGTH_LONG).show();
        openOverlaySettings();
        return false;
    }

    private void openOverlaySettings() {
        if (Build.VERSION.SDK_INT >= 23) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } catch (RuntimeException e) {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            }
        }
    }

    private void requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 100);
        }
    }

    private void refreshDashboard() {
        if (prefs == null || stageText == null) return;
        boolean running = prefs.getBoolean(OveruseService.KEY_RUNNING, false);
        int stage = clamp(prefs.getInt(OveruseService.KEY_STAGE, 0), 0, 5);
        long elapsed = Math.max(0L, prefs.getLong(OveruseService.KEY_ACTIVE_MS, 0L));
        long pausedUntil = prefs.getLong(OveruseService.KEY_PAUSED_UNTIL, 0L);
        boolean paused = running && pausedUntil > System.currentTimeMillis();
        long minutes = elapsed / 60000L;

        stageText.setText(stage + " / 5");
        stageText.setTextColor(stage >= 4 ? AMBER : WHITE);
        progress.setProgress(stage);
        activeText.setText(String.format(Locale.KOREA,
                "누적 사용  %d시간 %02d분", minutes / 60, minutes % 60));
        statusText.setText(paused ? "●  15분 일시정지 중" :
                running ? "●  보호 실행 중" : "●  보호 중지");
        statusText.setTextColor(paused ? AMBER : running ? BLUE : MUTED);

        int grace = clamp(prefs.getInt(OveruseService.KEY_GRACE, 10), 1, 30);
        int interval = clamp(prefs.getInt(OveruseService.KEY_INTERVAL, 2), 1, 15);
        long until = (stage == 0 ? grace * 60000L :
                (grace + stage * interval) * 60000L) - elapsed;
        nextText.setText(stage >= 5 ? "최고 단계에 도달했어" :
                paused ? "일시정지 중에는 사용 시간이 늘지 않아" :
                "다음 단계까지 약 " + Math.max(0L, (until + 59999L) / 60000L) + "분");
        mainButton.setText(running ? "보호 종료" : "보호 시작");
        pauseButton.setEnabled(running);
        pauseButton.setText(paused ? "보호 계속하기" : "15분 일시정지");
        if (permissionText != null) {
            boolean allowed = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this);
            permissionText.setText(allowed ?
                    "✓ 다른 앱 위에 표시 권한 허용됨" :
                    "! 오버레이 권한 필요 · 아래 버튼으로 허용해 줘");
            permissionText.setTextColor(allowed ? BLUE : AMBER);
        }
    }

    private LinearLayout vertical() {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        return column;
    }

    private LinearLayout card(int color) {
        LinearLayout layout = vertical();
        layout.setPadding(dp(18), dp(18), dp(18), dp(18));
        layout.setBackground(round(color, 20));
        return layout;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private Button button(String label, int background, int foreground) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setTextColor(foreground);
        button.setBackground(round(background, 14));
        button.setMinHeight(dp(48));
        button.setPadding(dp(12), 0, dp(12), 0);
        return button;
    }

    private TextView text(String content, float size, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(content);
        v.setTextSize(size);
        v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }

    private LinearLayout.LayoutParams wrapSpace(int topDp, int bottomDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(topDp);
        lp.bottomMargin = dp(bottomDp);
        return lp;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
