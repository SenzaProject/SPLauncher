package com.senzaproject.splauncher;

import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

/**
 * Pendengar notifikasi: sumber data Pusat Notifikasi, badge ikon, dan kontrol media di Control Center.
 * Pengguna harus mengaktifkan "Akses notifikasi" untuk SP Launcher di Pengaturan Android.
 */
public class NotificationService extends NotificationListenerService {

    interface Listener { void onNotificationsChanged(); }

    private static volatile NotificationService instance;
    private static volatile List<StatusBarNotification> snapshot = Collections.emptyList();
    private static final ArrayList<Listener> LISTENERS = new ArrayList<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    static ComponentName component(Context c) { return new ComponentName(c, NotificationService.class); }

    /** Apakah pengguna sudah memberi akses notifikasi (belum tentu sedang terhubung). */
    static boolean isEnabled(Context c) {
        try {
            String flat = Settings.Secure.getString(c.getContentResolver(), "enabled_notification_listeners");
            if (flat == null) return false;
            String me = component(c).flattenToString();
            for (String s : flat.split(":")) if (s.equals(me)) return true;
        } catch (Throwable ignored) { }
        return false;
    }

    /** Minta sistem menghubungkan ulang layanan bila akses sudah aktif tetapi layanan terputus. */
    static void ensureBound(Context c) {
        if (instance == null && isEnabled(c)) {
            try { NotificationListenerService.requestRebind(component(c)); } catch (Throwable ignored) { }
        }
    }

    static boolean connected() { return instance != null; }

    static List<StatusBarNotification> active() { return snapshot; }

    static void addListener(Listener l) { if (!LISTENERS.contains(l)) LISTENERS.add(l); }
    static void removeListener(Listener l) { LISTENERS.remove(l); }

    /** Notifikasi yang layak ditampilkan sebagai kartu (tanpa ringkasan grup / layanan latar / media). */
    static boolean showable(StatusBarNotification sb, String selfPkg) {
        Notification n = sb.getNotification();
        if (n == null) return false;
        if (selfPkg != null && selfPkg.equals(sb.getPackageName())) return false;
        if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return false;
        if ((n.flags & Notification.FLAG_FOREGROUND_SERVICE) != 0) return false;
        if (Notification.CATEGORY_TRANSPORT.equals(n.category)) return false;
        return true;
    }

    /** Daftar kartu, terbaru di atas. */
    static List<StatusBarNotification> cards(String selfPkg) {
        ArrayList<StatusBarNotification> out = new ArrayList<>();
        for (StatusBarNotification s : snapshot) if (showable(s, selfPkg)) out.add(s);
        Collections.sort(out, new Comparator<StatusBarNotification>() {
            @Override public int compare(StatusBarNotification a, StatusBarNotification b) {
                return Long.compare(b.getPostTime(), a.getPostTime());
            }
        });
        return out;
    }

    /** Jumlah notifikasi aktif per paket (untuk badge); tidak menghitung yang sedang berjalan/ongoing. */
    static HashMap<String, Integer> counts(String selfPkg) {
        HashMap<String, Integer> m = new HashMap<>();
        for (StatusBarNotification s : snapshot) {
            if (!showable(s, selfPkg) || s.isOngoing()) continue;
            Integer c = m.get(s.getPackageName());
            m.put(s.getPackageName(), c == null ? 1 : c + 1);
        }
        return m;
    }

    static void dismiss(String key) {
        NotificationService s = instance;
        if (s == null) return;
        try { s.cancelNotification(key); } catch (Throwable ignored) { }
    }

    static void dismissAll() {
        NotificationService s = instance;
        if (s == null) return;
        try { s.cancelAllNotifications(); } catch (Throwable ignored) { }
    }

    private static void fire() {
        MAIN.post(new Runnable() {
            @Override public void run() {
                for (Listener l : new ArrayList<>(LISTENERS)) l.onNotificationsChanged();
            }
        });
    }

    private void refresh() {
        List<StatusBarNotification> l = Collections.emptyList();
        try {
            StatusBarNotification[] a = getActiveNotifications();
            if (a != null) {
                ArrayList<StatusBarNotification> t = new ArrayList<>(a.length);
                Collections.addAll(t, a);
                l = t;
            }
        } catch (Throwable ignored) { }
        snapshot = l;
        fire();
    }

    @Override public void onListenerConnected() {
        instance = this;
        refresh();
    }

    @Override public void onListenerDisconnected() {
        if (instance == this) instance = null;
        snapshot = Collections.emptyList();
        fire();
    }

    @Override public void onNotificationPosted(StatusBarNotification sbn) { refresh(); }

    @Override public void onNotificationRemoved(StatusBarNotification sbn) { refresh(); }
}
