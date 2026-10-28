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
    String iconPack() { return sp.getString("iconPack", IconPacks.BUILTIN); }
    boolean topGestures() { return sp.getBoolean("topGestures", true); }
    boolean notifBadges() { return sp.getBoolean("notifBadges", true); }
    int wallpaper() { return sp.getInt("wallpaper", 0); } // 0..5 preset, -1 = kustom
    int tint() { return TINTS[U.clamp(sp.getInt("tint", 0), 0, TINTS.length - 1)]; }
    int tintIndex() { return U.clamp(sp.getInt("tint", 0), 0, TINTS.length - 1); }
    // --- kustomisasi tambahan (v1.2.0)
    int iconShape() { return U.clamp(sp.getInt("iconShape", 0), 0, 2); }          // 0 squircle, 1 bulat, 2 kotak
    int labelSize() { return U.clamp(sp.getInt("labelSize", 11), 9, 14); }
    int rowsPref() { return U.clamp(sp.getInt("rowsPref", 0), 0, 8); }            // 0 = otomatis
    int dockRadius() { return U.clamp(sp.getInt("dockRadius", 30), 14, 40); }
    int dockOpacity() { return U.clamp(sp.getInt("dockOpacity", 0), 0, 80); }     // 0 = ikuti kepekatan panel
    boolean pageDots() { return sp.getBoolean("pageDots", true); }
    boolean hideStatusBar() { return sp.getBoolean("hideStatusBar", false); }
    boolean lockLayout() { return sp.getBoolean("lockLayout", false); }
    int wallDim() { return U.clamp(sp.getInt("wallDim", 0), 0, 60); }
    boolean pullSearch() { return sp.getBoolean("pullSearch", true); }
    // --- gestur & Pengalih Aplikasi
    int doubleTap() { return U.clamp(sp.getInt("doubleTap", 1), 0, 3); }          // 0 mati, 1 kunci layar, 2 cari, 3 pengalih
    boolean switcherOnRecents() { return sp.getBoolean("switcherOnRecents", true); }
    boolean dockSwipeSwitcher() { return sp.getBoolean("dockSwipeSwitcher", true); }
    boolean switcherSnapshots() { return sp.getBoolean("switcherSnapshots", true); }
    boolean switcherLabels() { return sp.getBoolean("switcherLabels", true); }
    boolean switcherClearAll() { return sp.getBoolean("switcherClearAll", true); }
    boolean switcherKill() { return sp.getBoolean("switcherKill", true); }
    int switcherCardSize() { return U.clamp(sp.getInt("switcherCardSize", 62), 50, 80); }
    int wallpaperRev() { return sp.getInt("wallpaperRev", 0); }
    int layoutRev() { return sp.getInt("layoutRev", 0); }
    void bumpLayout() { put("layoutRev", layoutRev() + 1); }

    void put(String k, int v) { sp.edit().putInt(k, v).apply(); }
    void put(String k, boolean v) { sp.edit().putBoolean(k, v).apply(); }
    void put(String k, String v) { sp.edit().putString(k, v).apply(); }
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
                + "|" + wallpaper() + "|" + tintIndex() + "|" + wallpaperRev() + "|" + layoutRev() + "|" + isDark(c)
                + "|" + iconPack() + "|" + notifBadges()
                + "|" + iconShape() + "|" + labelSize() + "|" + rowsPref() + "|" + dockRadius() + "|" + dockOpacity()
                + "|" + pageDots() + "|" + hideStatusBar() + "|" + wallDim();
    }
}
