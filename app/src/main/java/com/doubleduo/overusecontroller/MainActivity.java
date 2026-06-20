package com.doubleduo.overusecontroller;

import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.provider.Settings;
import android.net.Uri;
import android.widget.TextView;
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
        TextView v = new TextView(this);
        v.setGravity(Gravity.CENTER);
        v.setTextSize(18f);
        v.setText("Overuse Controller is running.\n\n" +
                "Grace time: 10 min\n" +
                "Stage up: every 2 min\n" +
                "Screen-off recovery: every 1 min\n" +
                "Max stage: 5\n\n" +
                "If overlay or animation effects do not work, grant overlay and modify-system-settings permissions.");
        setContentView(v);
    }
}
