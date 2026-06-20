package com.doubleduo.overusecontroller;

import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.provider.Settings;
import android.net.Uri;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.Button;
import android.view.Gravity;
import android.content.Intent;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            try {
                Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivity(i);
            } catch (Throwable ignored) {}
        }

        if (Build.VERSION.SDK_INT >= 23 && !Settings.System.canWrite(this)) {
            try {
                Intent i = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                        Uri.parse("package:" + getPackageName()));
                startActivity(i);
            } catch (Throwable ignored) {}
        }

        OveruseService.start(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        int pad = (int)(20 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);

        TextView v = new TextView(this);
        v.setGravity(Gravity.CENTER);
        v.setTextSize(18f);
        v.setText("Overuse Controller is running.\n\n" +
                "Normal mode:\n" +
                "Grace time: 10 min of SCREEN-ON time\n" +
                "Stage up: every 2 min\n" +
                "Screen-off recovery: every 1 min\n" +
                "Max stage: 5\n\n" +
                "For instant visual check, grant overlay permission and press a test button below.");
        root.addView(v);

        addButton(root, "Test stage 1", 1);
        addButton(root, "Test stage 3", 3);
        addButton(root, "Test stage 5", 5);
        addButton(root, "Reset stage 0", 0);

        setContentView(root);
    }

    private void addButton(LinearLayout root, String label, final int stage) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setOnClickListener(v -> OveruseService.setStageForTest(this, stage));
        root.addView(b, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
    }
}
