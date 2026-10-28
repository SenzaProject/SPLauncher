package com.senzaproject.splauncher;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.app.role.RoleManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.hardware.HardwareBuffer;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Display;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Layanan Aksesibilitas SP Launcher. Dipakai untuk tiga hal, semuanya opsional:
 * 1) mengunci layar (ketuk dua kali di Home),
 * 2) mencatat urutan aplikasi yang sedang dibuka untuk Pengalih Aplikasi iOS 27,
 * 3) mengalihkan layar Recents bawaan sistem ke Pengalih Aplikasi SP Launcher.
 * Layanan ini TIDAK membaca isi layar (canRetrieveWindowContent=false) dan tidak mengirim data keluar.
 */
public class SPAccessibilityService extends AccessibilityService {
    static volatile SPAccessibilityService instance;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService bg = Executors.newSingleThreadExecutor();
    private Prefs prefs;
    private String fgPkg = "";
    private boolean fgEligible;
    private long lastShot, lastRecentsAt;
    private final Runnable shotRun = new Runnable() {
        @Override public void run() { shoot(fgPkg); }
    };

    // ------------------------------------------------------------------ API statis

    static boolean isEnabled(Context c) {
        try {
            String s = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (s == null) return false;
            String me = new ComponentName(c, SPAccessibilityService.class).flattenToString();
            String meShort = new ComponentName(c, SPAccessibilityService.class).flattenToShortString();
            for (String part : s.split(":")) {
                if (part.equalsIgnoreCase(me) || part.equalsIgnoreCase(meShort)) return true;
            }
        } catch (Throwable ignored) { }
        return false;
    }

    /** Kunci layar. Bila layanan belum aktif, arahkan pengguna ke pengaturan Aksesibilitas. */
    static boolean lockScreen(Context c) {
        SPAccessibilityService s = instance;
        if (s == null) {
            Toast.makeText(c, "Aktifkan layanan Aksesibilitas \"SP Launcher\" agar ketuk dua kali dapat mengunci layar",
                    Toast.LENGTH_LONG).show();
            try {
                c.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Throwable ignored) { }
            return false;
        }
        return s.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
    }

    // ------------------------------------------------------------------ siklus hidup

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        prefs = new Prefs(this);
        Recents.load(this);
    }

    @Override public boolean onUnbind(Intent intent) {
        instance = null;
        return super.onUnbind(intent);
    }

    @Override public void onDestroy() {
        instance = null;
        main.removeCallbacks(shotRun);
        bg.shutdown();
        super.onDestroy();
    }

    @Override public void onInterrupt() { }

    // ------------------------------------------------------------------ peristiwa

    @Override public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e == null || e.getPackageName() == null) return;
        if (prefs == null) prefs = new Prefs(this);
        String pkg = e.getPackageName().toString();
        CharSequence cn = e.getClassName();
        String cls = cn == null ? "" : cn.toString();
        int type = e.getEventType();
        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (!pkg.equals(getPackageName()) && isRecentsWindow(cls)) {
                // jangan potret layar Recents sebagai pratinjau aplikasi sebelumnya
                main.removeCallbacks(shotRun);
                fgEligible = false;
                if (prefs.switcherOnRecents()) openOurSwitcher();
                return;
            }
            onWindow(pkg, cls);
        } else if (type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            if (fgEligible && pkg.equals(fgPkg) && prefs.switcherSnapshots()
                    && SystemClock.uptimeMillis() - lastShot > 6000L) {
                main.removeCallbacks(shotRun);
                main.postDelayed(shotRun, 500L);
            }
        }
    }

    private static boolean isRecentsWindow(String cls) {
        return cls.toLowerCase(Locale.ROOT).contains("recents");
    }

    private boolean isDefaultHome() {
        try {
            return getSystemService(RoleManager.class).isRoleHeld(RoleManager.ROLE_HOME);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Layar Recents sistem muncul: kembali ke Home lalu buka Pengalih Aplikasi milik kita. */
    private void openOurSwitcher() {
        long now = SystemClock.uptimeMillis();
        if (now - lastRecentsAt < 900L) return;
        if (!isDefaultHome()) return;
        lastRecentsAt = now;
        Recents.requestOpen();
        performGlobalAction(GLOBAL_ACTION_HOME);
    }

    private void onWindow(String pkg, String cls) {
        fgPkg = pkg;
        fgEligible = false;
        if (pkg.equals(getPackageName()) || pkg.equals("com.android.systemui")) return;
        if (cls.startsWith("android.inputmethodservice") || cls.startsWith("android.view.")) return;
        if (isImePackage(pkg) || isHomePackage(pkg)) return;
        try {
            if (getPackageManager().getLaunchIntentForPackage(pkg) == null) return;
        } catch (Throwable t) {
            return;
        }
        fgEligible = true;
        Recents.touch(this, pkg);
        if (prefs.switcherSnapshots()) {
            main.removeCallbacks(shotRun);
            main.postDelayed(shotRun, 1300L);
        }
    }

    private boolean isImePackage(String pkg) {
        try {
            String ime = Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
            if (ime == null) return false;
            ComponentName cn = ComponentName.unflattenFromString(ime);
            return cn != null && pkg.equals(cn.getPackageName());
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean isHomePackage(String pkg) {
        try {
            Intent h = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            List<ResolveInfo> l = getPackageManager().queryIntentActivities(h, PackageManager.MATCH_ALL);
            for (ResolveInfo ri : l) {
                if (ri.activityInfo != null && pkg.equals(ri.activityInfo.packageName)) return true;
            }
        } catch (Throwable ignored) { }
        return false;
    }

    // ------------------------------------------------------------------ pratinjau

    private void shoot(final String pkg) {
        if (pkg == null || !pkg.equals(fgPkg) || !fgEligible) return;
        if (prefs == null || !prefs.switcherSnapshots()) return;
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            if (pm != null && !pm.isInteractive()) return;
            if (km != null && km.isKeyguardLocked()) return;
        } catch (Throwable ignored) { }
        lastShot = SystemClock.uptimeMillis();
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, bg, new TakeScreenshotCallback() {
                @Override public void onSuccess(ScreenshotResult r) {
                    HardwareBuffer hb = null;
                    try {
                        hb = r.getHardwareBuffer();
                        Bitmap hw = Bitmap.wrapHardwareBuffer(hb, r.getColorSpace());
                        if (hw == null) return;
                        Bitmap sw = hw.copy(Bitmap.Config.ARGB_8888, false);
                        hw.recycle();
                        if (sw != null && pkg.equals(fgPkg)) {
                            Recents.saveSnapshot(SPAccessibilityService.this, pkg, sw);
                            sw.recycle();
                        }
                    } catch (Throwable ignored) {
                    } finally {
                        try { if (hb != null) hb.close(); } catch (Throwable ignored) { }
                    }
                }

                @Override public void onFailure(int errorCode) { }
            });
        } catch (Throwable ignored) { }
    }
}
