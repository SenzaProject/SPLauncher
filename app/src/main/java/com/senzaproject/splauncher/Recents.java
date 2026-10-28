package com.senzaproject.splauncher;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Process;
import android.os.SystemClock;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Daftar aplikasi terbaru untuk Pengalih Aplikasi (App Switcher).
 * Sumber utama: layanan Aksesibilitas (urutan jendela depan). Cadangan: Akses Penggunaan (UsageStats).
 * Pratinjau (snapshot) disimpan lokal di cache aplikasi dan tidak pernah dikirim ke mana pun.
 */
final class Recents {
    private Recents() {}

    private static final String PREF = "sp_recents";
    private static final String KEY_LRU = "lru";
    private static final int MAX = 16;

    private static final ArrayList<String> LRU = new ArrayList<>();
    private static boolean loaded;
    private static long openRequestAt;

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    private static synchronized void ensureLoaded(Context c) {
        if (loaded) return;
        loaded = true;
        String s = sp(c).getString(KEY_LRU, "");
        if (s == null || s.isEmpty()) return;
        for (String p : s.split(",")) if (!p.isEmpty() && !LRU.contains(p)) LRU.add(p);
    }

    private static void persist(Context c) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < LRU.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(LRU.get(i));
        }
        sp(c).edit().putString(KEY_LRU, sb.toString()).apply();
    }

    static synchronized void load(Context c) { ensureLoaded(c); }

    /** Aplikasi {@code pkg} baru saja berada di depan. */
    static synchronized void touch(Context c, String pkg) {
        ensureLoaded(c);
        if (pkg == null || pkg.isEmpty() || pkg.equals(c.getPackageName())) return;
        if (!LRU.isEmpty() && LRU.get(0).equals(pkg)) return;
        LRU.remove(pkg);
        LRU.add(0, pkg);
        while (LRU.size() > MAX) LRU.remove(LRU.size() - 1);
        sp(c).edit().remove("d_" + pkg).apply();
        persist(c);
    }

    static synchronized void remove(Context c, String pkg) {
        ensureLoaded(c);
        LRU.remove(pkg);
        persist(c);
        sp(c).edit().putLong("d_" + pkg, System.currentTimeMillis()).apply();
        File f = snapFile(c, pkg);
        if (f.exists()) //noinspection ResultOfMethodCallIgnored
            f.delete();
    }

    static synchronized void clear(Context c) {
        ensureLoaded(c);
        SharedPreferences.Editor e = sp(c).edit();
        long now = System.currentTimeMillis();
        for (String p : LRU) e.putLong("d_" + p, now);
        e.apply();
        LRU.clear();
        persist(c);
        clearSnapshots(c);
    }

    /** Daftar paket terbaru, yang paling baru di depan (belum disaring terhadap aplikasi yang dapat dibuka). */
    static synchronized List<String> packages(Context c) {
        ensureLoaded(c);
        ArrayList<String> out = new ArrayList<>(LRU);
        if (usageGranted(c)) {
            for (String p : fromUsageStats(c)) {
                if (!out.contains(p) && !p.equals(c.getPackageName())) out.add(p);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ permintaan buka dari Aksesibilitas

    static void requestOpen() { openRequestAt = SystemClock.uptimeMillis(); }

    static boolean consumeOpenRequest() {
        long t = openRequestAt;
        openRequestAt = 0;
        return t != 0 && SystemClock.uptimeMillis() - t < 3000;
    }

    // ------------------------------------------------------------------ Akses Penggunaan (cadangan)

    static boolean usageGranted(Context c) {
        try {
            AppOpsManager ops = (AppOpsManager) c.getSystemService(Context.APP_OPS_SERVICE);
            if (ops == null) return false;
            int mode = ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.getPackageName());
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Throwable t) {
            return false;
        }
    }

    private static List<String> fromUsageStats(Context c) {
        final HashMap<String, Long> last = new HashMap<>();
        try {
            UsageStatsManager um = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
            if (um == null) return Collections.emptyList();
            long now = System.currentTimeMillis();
            UsageEvents ev = um.queryEvents(now - 6L * 3600L * 1000L, now);
            UsageEvents.Event e = new UsageEvents.Event();
            while (ev.hasNextEvent()) {
                ev.getNextEvent(e);
                if (e.getEventType() != UsageEvents.Event.ACTIVITY_RESUMED) continue;
                String p = e.getPackageName();
                if (p == null) continue;
                Long prev = last.get(p);
                if (prev == null || prev < e.getTimeStamp()) last.put(p, e.getTimeStamp());
            }
        } catch (Throwable t) {
            return Collections.emptyList();
        }
        SharedPreferences s = sp(c);
        ArrayList<Map.Entry<String, Long>> es = new ArrayList<>();
        for (Map.Entry<String, Long> en : last.entrySet()) {
            long dismissed = s.getLong("d_" + en.getKey(), 0L);
            if (en.getValue() > dismissed) es.add(en);
        }
        Collections.sort(es, new Comparator<Map.Entry<String, Long>>() {
            @Override public int compare(Map.Entry<String, Long> a, Map.Entry<String, Long> b) {
                return Long.compare(b.getValue(), a.getValue());
            }
        });
        ArrayList<String> out = new ArrayList<>();
        for (Map.Entry<String, Long> en : es) out.add(en.getKey());
        return out;
    }

    // ------------------------------------------------------------------ pratinjau

    private static File snapDir(Context c) {
        File d = new File(c.getCacheDir(), "recents");
        if (!d.exists()) //noinspection ResultOfMethodCallIgnored
            d.mkdirs();
        return d;
    }

    static File snapFile(Context c, String pkg) {
        return new File(snapDir(c), pkg.replaceAll("[^A-Za-z0-9._]", "_") + ".jpg");
    }

    static Bitmap snapshot(Context c, String pkg) {
        File f = snapFile(c, pkg);
        if (!f.exists()) return null;
        try { return BitmapFactory.decodeFile(f.getAbsolutePath()); } catch (Throwable t) { return null; }
    }

    /** Simpan {@code full} (layar penuh) sebagai JPEG kecil. Dipanggil dari thread latar. */
    static void saveSnapshot(Context c, String pkg, Bitmap full) {
        FileOutputStream os = null;
        try {
            int w = 420;
            int h = Math.max(1, Math.round(w * (full.getHeight() / (float) full.getWidth())));
            Bitmap small = Bitmap.createScaledBitmap(full, w, h, true);
            os = new FileOutputStream(snapFile(c, pkg));
            small.compress(Bitmap.CompressFormat.JPEG, 72, os);
            if (small != full) small.recycle();
        } catch (Throwable ignored) {
        } finally {
            try { if (os != null) os.close(); } catch (Throwable ignored) { }
        }
    }

    static void clearSnapshots(Context c) {
        File[] fs = snapDir(c).listFiles();
        if (fs == null) return;
        for (File f : fs) //noinspection ResultOfMethodCallIgnored
            f.delete();
    }
}
