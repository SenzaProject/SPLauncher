package com.senzaproject.splauncher;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

/** Preferensi launcher (disimpan lokal). */
final class Prefs {
    static final int THEME_SYSTEM = 0, THEME_LIGHT = 1, THEME_DARK = 2;
    static final int STYLE_NORMAL = 0, STYLE_DARK = 1, STYLE_GLASS = 2, STYLE_TINTED = 3;
    static final int[] TINTS = {0xFF4F8CFF, 0xFFA66BFF, 0xFF34C98E, 0xFFFF6FA5, 0xFFFF9F45, 0xFF9AA0AE};
    static final String[] TINT_NAMES = {"Biru", "Ungu", "Hijau", "Pink", "Oranye", "Abu"};

    private final SharedPreferences sp;

    Prefs(Context c) { sp = c.getApplicationContext().getSharedPreferences("sp_launcher", Context.MODE_PRIVATE); }

    int theme() { return sp.getInt("theme", THEME_SYSTEM); }
    int iconStyle() { return sp.getInt("iconStyle", STYLE_NORMAL); }
    int cols() { return U.clamp(sp.getInt("cols", 4), 4, 6); }
    int iconScale() { return U.clamp(sp.getInt("iconScale", 100), 70, 130); }
    boolean labels() { return sp.getBoolean("labels", true); }
    boolean showDock() { return sp.getBoolean("dock", true); }
    int blur() { return U.clamp(sp.getInt("blur", 60), 0, 100); }
    int glassOpacity() { return U.clamp(sp.getInt("glassOpacity", 35), 10, 80); }
    boolean reduceTransparency() { return sp.getBoolean("reduceTransp", false); }
    boolean reduceMotion() { return sp.getBoolean("reduceMotion", false); }
    boolean haptics() { return sp.getBoolean("haptics", true); }
    boolean autoAdd() { return sp.getBoolean("autoAdd", true); }
    int wallpaper() { return sp.getInt("wallpaper", 0); } // 0..5 preset, -1 = kustom
    int tint() { return TINTS[U.clamp(sp.getInt("tint", 0), 0, TINTS.length - 1)]; }
    int tintIndex() { return U.clamp(sp.getInt("tint", 0), 0, TINTS.length - 1); }
    int wallpaperRev() { return sp.getInt("wallpaperRev", 0); }
    int layoutRev() { return sp.getInt("layoutRev", 0); }
    void bumpLayout() { put("layoutRev", layoutRev() + 1); }

    void put(String k, int v) { sp.edit().putInt(k, v).apply(); }
    void put(String k, boolean v) { sp.edit().putBoolean(k, v).apply(); }
    void bumpWallpaper() { put("wallpaperRev", wallpaperRev() + 1); }

    boolean isDark(Context c) {
        int t = theme();
        if (t == THEME_LIGHT) return false;
        if (t == THEME_DARK) return true;
        return (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    /** Tanda tangan nilai untuk mendeteksi perubahan. */
    String signature(Context c) {
        return theme() + "|" + iconStyle() + "|" + cols() + "|" + iconScale() + "|" + labels() + "|" + showDock()
                + "|" + blur() + "|" + glassOpacity() + "|" + reduceTransparency() + "|" + reduceMotion()
                + "|" + wallpaper() + "|" + tintIndex() + "|" + wallpaperRev() + "|" + layoutRev() + "|" + isDark(c);
    }
}
