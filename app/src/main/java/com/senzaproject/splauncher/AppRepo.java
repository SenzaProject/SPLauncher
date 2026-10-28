package com.senzaproject.splauncher;

import android.app.ActivityOptions;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Process;
import android.os.UserHandle;
import android.view.View;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

/** Daftar aplikasi terpasang (via LauncherApps). */
final class AppRepo {
    static final class AppInfo {
        final String key, pkg, cls, label;
        final ComponentName cn;
        final UserHandle user;
        final LauncherActivityInfo lai;
        final long installTime;
        final int category;
        final boolean system;

        AppInfo(LauncherActivityInfo l) {
            lai = l;
            cn = l.getComponentName();
            pkg = cn.getPackageName();
            cls = cn.getClassName();
            key = pkg + "/" + cls;
            user = l.getUser();
            CharSequence cs = l.getLabel();
            label = cs == null ? pkg : cs.toString();
            ApplicationInfo ai = l.getApplicationInfo();
            installTime = l.getFirstInstallTime();
            category = ai.category;
            system = (ai.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
        }
    }

    private final Context ctx;
    final LauncherApps la;
    private ArrayList<AppInfo> apps = new ArrayList<>();
    private HashMap<String, AppInfo> byKey = new HashMap<>();

    AppRepo(Context c) {
        ctx = c.getApplicationContext();
        la = ctx.getSystemService(LauncherApps.class);
    }

    void reload() {
        ArrayList<AppInfo> list = new ArrayList<>();
        HashMap<String, AppInfo> map = new HashMap<>();
        try {
            List<LauncherActivityInfo> l = la.getActivityList(null, Process.myUserHandle());
            for (LauncherActivityInfo i : l) {
                AppInfo a = new AppInfo(i);
                if (map.containsKey(a.key)) continue;
                list.add(a);
                map.put(a.key, a);
            }
        } catch (Throwable ignored) { }
        Collections.sort(list, new Comparator<AppInfo>() {
            @Override public int compare(AppInfo x, AppInfo y) {
                return x.label.toLowerCase(Locale.ROOT).compareTo(y.label.toLowerCase(Locale.ROOT));
            }
        });
        apps = list;
        byKey = map;
        try {
            if (IconPacks.rebuild(ctx, list)) IconFactory.clear();
        } catch (Throwable ignored) { }
    }

    List<AppInfo> all() { return apps; }
    AppInfo get(String key) { return byKey.get(key); }

    AppInfo findByPackage(String pkg) {
        for (AppInfo a : apps) if (a.pkg.equals(pkg)) return a;
        return null;
    }

    boolean launch(AppInfo a, View src) {
        try {
            Bundle opts = null;
            Rect r = null;
            if (src != null) {
                int[] loc = new int[2];
                src.getLocationOnScreen(loc);
                r = new Rect(loc[0], loc[1], loc[0] + src.getWidth(), loc[1] + src.getHeight());
            }
            int tr = new Prefs(ctx).transition();
            if (tr == Prefs.TR_NONE) {
                opts = ActivityOptions.makeCustomAnimation(ctx, 0, 0).toBundle();
            } else if (tr == Prefs.TR_ZOOM) {
                opts = ActivityOptions.makeCustomAnimation(ctx, R.anim.sp_zoom_in, R.anim.sp_hold).toBundle();
            } else if (tr == Prefs.TR_FADE) {
                opts = ActivityOptions.makeCustomAnimation(ctx, R.anim.sp_fade_in, R.anim.sp_fade_out).toBundle();
            } else if (src != null) {
                opts = ActivityOptions.makeScaleUpAnimation(src, 0, 0, src.getWidth(), src.getHeight()).toBundle();
            }
            la.startMainActivity(a.cn, a.user, r, opts);
            return true;
        } catch (Throwable t) {
            Toast.makeText(ctx, "Tidak dapat membuka " + a.label, Toast.LENGTH_SHORT).show();
            return false;
        }
    }
}
