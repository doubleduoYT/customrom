package com.hgsoft.overusebridge;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * OPT-IN ROOT-ONLY CPUFreq cap. Does not affect unrooted devices.
 *
 * Only sysfs CPUFreq policy scaling_max_freq is touched. The original values are
 * stored before any write. A detached su watchdog restores original values after
 * 75s without the foreground service heartbeat (e.g. process killed).
 * Actual support varies across Android kernels and Magisk configurations.
 *
 * IMPORTANT: Even a mild CPU clock cap affects all processes on the device.
 */
final class RootCpuLimiter {
    private static final String TAG = "OveruseCpuRoot";
    private static final int[] CAPS = {100, 95, 90, 85, 80, 75};
    private final SharedPreferences prefs;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private final AtomicBoolean restoreQueued = new AtomicBoolean(false);
    private final File script;
    private final File backup;
    private final File heartbeat;
    private long lastHeartbeatElapsed;
    private long lastAppliedElapsed;
    private int requestedCap = 100;
    private Process watchdog;

    RootCpuLimiter(Context context, SharedPreferences prefs) {
        this.prefs = prefs;
        File dir = context.getFilesDir();
        script = new File(dir, "overuse_cpu_limit.sh");
        backup = new File(dir, "overuse_cpu_originals.txt");
        heartbeat = new File(dir, "overuse_cpu_heartbeat.txt");
        try (InputStream in = context.getAssets().open("cpu_limit.sh");
             FileOutputStream out = new FileOutputStream(script)) {
            byte[] buffer = new byte[4096];
            int length;
            while ((length = in.read(buffer)) > -1) {
                out.write(buffer, 0, length);
            }
        } catch (IOException error) {
            setStatus("ERROR_SCRIPT_NOT_PACKAGED");
            Log.e(TAG, "CPUFreq helper asset unavailable", error);
        }
        if (backup.isFile()) {
            // Recover a prior killed service before any new limit is applied.
            restore();
        }
    }

    void update(boolean running, boolean paused, boolean screenActive, int stage) {
        if (!prefs.getBoolean("cpu_root_enabled", false)) {
            if (backup.isFile() || requestedCap < 100) restore();
            else setStatus("ROOT_DISABLED");
            return;
        }
        if (!running || paused || !screenActive || stage <= 0) {
            if (backup.isFile() || requestedCap < 100) restore();
            else setStatus("ROOT_READY_WAITING_STAGE");
            return;
        }
        int cap = CAPS[Math.max(0, Math.min(5, stage))];
        if (cap == 100) return;

        long now = SystemClock.elapsedRealtime();
        if (now - lastHeartbeatElapsed > 4000L) {
            try (FileWriter writer = new FileWriter(heartbeat, false)) {
                writer.write(Long.toString(System.currentTimeMillis() / 1000L));
                writer.write("\n");
                lastHeartbeatElapsed = now;
            } catch (IOException error) {
                setStatus("ERROR_HEARTBEAT_WRITE");
                restore();
                return;
            }
        }
        if ((cap == requestedCap && now - lastAppliedElapsed < 12000L) || !busy.compareAndSet(false, true)) {
            return;
        }
        requestedCap = cap;
        lastAppliedElapsed = now;
        setStatus("ROOT_APPLYING_" + cap);
        executor.execute(() -> {
            try {
                if (!script.isFile()) {
                    setStatus("ERROR_SCRIPT_NOT_PACKAGED");
                    return;
                }
                RootResult result = runRoot("apply", cap);
                setStatus(result.message);
                if (result.ok && backup.isFile() && (watchdog == null || !watchdog.isAlive())) {
                    // It runs independently from the Android app process.
                    watchdog = new ProcessBuilder("su", "-c",
                            "sh " + quoted(script) + " watch " + quoted(backup) +
                            " 0 " + quoted(heartbeat)).redirectErrorStream(true).start();
                }
            } catch (Exception error) {
                setStatus("ERROR_ROOT_" + error.getClass().getSimpleName());
                Log.e(TAG, "Unable to set CPU cap", error);
            } finally {
                busy.set(false);
            }
        });
    }

    void restore() {
        requestedCap = 100;
        if (!backup.isFile() && !busy.get()) {
            setStatus(prefs.getBoolean("cpu_root_enabled", false) ?
                    "ROOT_READY_WAITING_STAGE" : "ROOT_DISABLED");
            return;
        }
        // Queue even when a root apply is in-flight so STOP always restores.
        if (!restoreQueued.compareAndSet(false, true)) return;
        setStatus("RESTORING_CPU_CLOCKS");
        executor.execute(() -> {
            try {
                if (!backup.isFile()) {
                    setStatus("RESTORED");
                } else {
                    RootResult result = runRoot("restore", 100);
                    setStatus(result.message);
                    if (!result.ok) Log.w(TAG, "CPUFreq restore failed: " + result.message);
                }
            } catch (Exception error) {
                setStatus("ERROR_RESTORE_" + error.getClass().getSimpleName());
                Log.e(TAG, "Unable to restore CPU ceilings", error);
            } finally {
                busy.set(false);
                restoreQueued.set(false);
            }
        });
    }

    void stop() {
        // Restoration remains queued even after service is destroyed.
        restore();
        executor.shutdown();
    }

    private void setStatus(String value) {
        prefs.edit().putString("cpu_root_status", value).apply();
    }

    private static String quoted(File path) {
        return "'" + path.getAbsolutePath().replace("'", "'\\''") + "'";
    }

    private RootResult runRoot(String command, int percentage) throws Exception {
        String shell = "sh " + quoted(script) + " " + command + " " +
                quoted(backup) + " " + percentage + " " + quoted(heartbeat);
        Process p = new ProcessBuilder("su", "-c", shell).redirectErrorStream(true).start();
        if (!p.waitFor(20L, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            return new RootResult(false, "ERROR_ROOT_TIMEOUT");
        }
        String lastLine = "";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) lastLine = line.trim();
            }
        }
        boolean success = p.exitValue() == 0 &&
                (lastLine.startsWith("CPU_LIMIT_") ||
                 lastLine.startsWith("PARTIAL_CPU_LIMIT_") ||
                 lastLine.equals("RESTORED"));
        if (success) return new RootResult(true, lastLine);
        return new RootResult(false, lastLine.startsWith("ERROR_") ?
                lastLine : "ERROR_SU_DENIED_OR_UNSUPPORTED");
    }

    private static final class RootResult {
        final boolean ok;
        final String message;
        RootResult(boolean ok, String message) {
            this.ok = ok;
            this.message = message;
        }
    }
}
