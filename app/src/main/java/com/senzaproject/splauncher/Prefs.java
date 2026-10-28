package com.senzaproject.splauncher;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

/** Preferensi launcher (disimpan lokal). */
final class Prefs {
    static final int THEME_SYSTEM = 0, THEME_LIGHT = 1, THEME_DARK = 2, THEME_SCHEDULE = 3;
    /** Animasi Transisi: Liquid Glass, Zoom, Pudar, Tanpa. */
    static final int TR_LIQUID = 0, TR_ZOOM = 1, TR_FADE = 2, TR_NONE = 3;
    static final String[] TRANSITION_NAMES = {"Liquid Glass", "Zoom", "Pudar", "Tanpa Animasi"};
    /** Laci Aplikasi: 0 = geser ke atas di Home, 1 = hanya halaman App Library. */
    static final String[] DRAWER_NAMES = {"Geser ke atas", "Halaman App Library"};
    static final String[] WIDGET_PLACE_NAMES = {"Atas", "Bawah"};
    /** Ukuran Ikon: 5 tingkat (nilai iconScale %). */
    static final int[] ICON_STEPS = {78, 90, 100, 115, 130};
    static final String[] ICON_STEP_NAMES = {"Sangat Kecil", "Kecil", "Normal", "Besar", "Sangat Besar"};
    static final int STYLE_NORMAL = 0, STYLE_DARK = 1, STYLE_GLASS = 2, STYLE_TINTED = 3;
    static final int[] TINTS = {0xFF4F8CFF, 0xFFA66BFF, 0xFF34C98E, 0xFFFF6FA5, 0xFFFF9F45, 0xFF9AA0AE};
    static final String[] TINT_NAMES = {"Biru", "Ungu", "Hijau", "Pink", "Oranye", "Abu"};

    private final SharedPreferences sp;

    Prefs(Context c) { sp = c.getApplicationContext().getSharedPreferences("sp_launcher", Context.MODE_PRIVATE); }

    int theme() { return sp.getInt("theme", THEME_SYSTEM); }
    int iconStyle() { return sp.getInt("iconStyle", STYLE_NORMAL); }
    int cols() { return U.clamp(sp.getInt("cols", 4), 4, 6); }
    int iconScale() { return U.clamp(sp.getInt("iconScale", 100), 70, 130); }
    int transition() { return U.clamp(sp.getInt("transition", TR_LIQUID), 0, 3); }
    int drawerMode() { return U.clamp(sp.getInt("drawerMode", 0), 0, 1); }
    int widgetPlace() { return U.clamp(sp.getInt("widgetPlace", 0), 0, 1); }   // 0 atas, 1 bawah
    int schedStart() { return U.clamp(sp.getInt("schedStart", 19 * 60), 0, 1439); } // menit sejak 00:00, mulai gelap
    int schedEnd() { return U.clamp(sp.getInt("schedEnd", 6 * 60), 0, 1439); }      // menit sejak 00:00, selesai gelap
    boolean transparentFolders() { return sp.getBoolean("transparentFolders", true); }
    /** Layar Nol: halaman widget di kiri Home pertama (geser ke kanan dari Home pertama). */
    boolean zeroScreen() { return sp.getBoolean("zeroScreen", true); }
    /** Nama tingkat Ukuran Ikon terdekat dengan nilai iconScale sekarang. */
    int iconStep() {
        int best = 0;
        for (int i = 1; i < ICON_STEPS.length; i++)
            if (Math.abs(ICON_STEPS[i] - iconScale()) < Math.abs(ICON_STEPS[best] - iconScale())) best = i;
        return best;
    }
    boolean labels() { return sp.getBoolean("labels", true); }
    boolean showDock() { return sp.getBoolean("dock", true); }
    int blur() { return U.clamp(sp.getInt("blur", 60), 0, 100); }
    /** 0 = Blur penuh, 1 = Kaca Cair (ringan, tanpa sampling backdrop), 2 = Padat. "Kurangi Transparansi" lama = Padat. */
    int glassMode() {
        if (!sp.contains("glassMode") && sp.getBoolean("reduceTransp", false)) return Glass.MODE_SOLID; // migrasi dari v1.2.0
        return U.clamp(sp.getInt("glassMode", 0), 0, 2);
    }
    int glassOpacity() { return U.clamp(sp.getInt("glassOpacity", 35), 10, 80); }
    boolean reduceTransparency() { return glassMode() == Glass.MODE_SOLID; }
    boolean reduceMotion() { return sp.getBoolean("reduceMotion", false); }
    boolean haptics() { return sp.getBoolean("haptics", true); }
    boolean autoAdd() { return sp.getBoolean("autoAdd", true); }
    String iconPack() { return sp.getString("iconPack", IconPacks.BUILTIN); }
    boolean topGestures() { return sp.getBoolean("topGestures", true); }
    /** Zona tarik dari atas gaya iOS 27: kiri = Pusat Notifikasi, tengah = Cari, kanan = Control Center. */
    boolean topZones27() { return sp.getBoolean("topZones27", true); }
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
    int switcherStyle() { return U.clamp(sp.getInt("switcherStyle", 1), 0, 1); }    // 0 berjajar, 1 tumpukan kartu
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
        if (t == THEME_SCHEDULE) {
            java.util.Calendar cal = java.util.Calendar.getInstance();
            int now = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE);
            int a = schedStart(), b = schedEnd();
            if (a == b) return false;
            return a < b ? (now >= a && now < b) : (now >= a || now < b);
        }
        return (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    /** Tanda tangan nilai untuk mendeteksi perubahan. */
    String signature(Context c) {
        return theme() + "|" + iconStyle() + "|" + cols() + "|" + iconScale() + "|" + labels() + "|" + showDock()
                + "|" + blur() + "|" + glassMode() + "|" + glassOpacity() + "|" + reduceTransparency() + "|" + reduceMotion()
                + "|" + wallpaper() + "|" + tintIndex() + "|" + wallpaperRev() + "|" + layoutRev() + "|" + isDark(c)
                + "|" + iconPack() + "|" + notifBadges()
                + "|" + iconShape() + "|" + labelSize() + "|" + rowsPref() + "|" + dockRadius() + "|" + dockOpacity()
                + "|" + pageDots() + "|" + hideStatusBar() + "|" + wallDim()
                + "|" + transition() + "|" + drawerMode() + "|" + widgetPlace() + "|" + transparentFolders() + "|" + zeroScreen();
    }
}
