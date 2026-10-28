package com.senzaproject.splauncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.appwidget.AppWidgetHost;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** Pengaturan SPLauncher + bagian Tentang. */
public class SettingsActivity extends Activity {
    static final String DEVELOPER = "SenzaProject";
    static final String EMAIL = "senzaproject98@gmail.com";
    static final String SUPPORT_URL = "https://t.me/senzaproject";

    private static final int REQ_ROLE = 11, REQ_IMAGE = 12;

    private Prefs prefs;
    private boolean dark;
    private int bg, card, fg, sub, line, accent;
    private LinearLayout page;
    private TextView defaultStatus, packStatus, notifStatus, writeStatus, dndStatus, a11yStatus, usageStatus;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new Prefs(this);
        dark = prefs.isDark(this);
        bg = dark ? 0xFF070C18 : 0xFFE6EFFB;
        card = dark ? 0xCC172238 : 0xE6FFFFFF;
        fg = dark ? 0xFFFFFFFF : 0xFF111827;
        sub = dark ? 0xFF9AA6BD : 0xFF5F6B80;
        line = dark ? 0xFF2A3550 : 0xFFDDE6F2;
        accent = 0xFF0A84FF;
        getWindow().getDecorView().setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                dark ? new int[]{0xFF0E1A33, 0xFF050913} : new int[]{0xFFEEF5FF, 0xFFD3E3F8}));
        getWindow().setDecorFitsSystemWindows(false);
        build();
    }

    @Override protected void onResume() {
        super.onResume();
        refreshDefault();
        refreshAccess();
    }

    private int dp(float v) { return U.dp(this, v); }

    // ---------------------------------------------------------------- navigasi halaman

    private static String resumePage = "hub";

    private LinearLayout root, bar;
    private ScrollView sv;
    private TextView barTitle;
    private String currentPage = "hub";
    private final ArrayList<String> stack = new ArrayList<>();

    private void build() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        setContentView(root);
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override public WindowInsets onApplyWindowInsets(View v, WindowInsets ins) {
                android.graphics.Insets i = ins.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                v.setPadding(i.left, i.top, i.right, i.bottom);
                return WindowInsets.CONSUMED;
            }
        });

        // bilah atas: "‹ Kembali   Judul halaman"
        bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(14), dp(6), dp(16), dp(6));
        TextView back = text("\u2039 Kembali", 17, accent, false);
        back.setPadding(dp(4), dp(10), dp(16), dp(10));
        back.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { onBackPressed(); } });
        bar.addView(back);
        barTitle = text("", 17, fg, true);
        bar.addView(barTitle);
        root.addView(bar, new LinearLayout.LayoutParams(-1, -2));

        sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(16), dp(8), dp(16), dp(32));
        sv.addView(page, new ScrollView.LayoutParams(-1, -2));
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));

        String start = resumePage;
        resumePage = "hub";
        showPage(start);
    }

    private String titleOf(String id) {
        if ("hub".equals(id)) return "Pengaturan Launcher";
        if ("main".equals(id)) return "Tampilan Utama";
        if ("iconswidget".equals(id)) return "Ikon & Widget";
        if ("glass".equals(id)) return "Efek Liquid Glass";
        if ("theme".equals(id)) return "Tema";
        if ("wallpaper".equals(id)) return "Wallpaper";
        if ("transition".equals(id)) return "Animasi Transisi";
        if ("layout".equals(id)) return "Tata Letak Ikon";
        if ("widgetplace".equals(id)) return "Tata Letak Widget";
        if ("drawer".equals(id)) return "Laci Aplikasi";
        if ("notif".equals(id)) return "Notifikasi";
        if ("gestures".equals(id)) return "Gestur";
        if ("security".equals(id)) return "Kunci Layar";
        if ("help".equals(id)) return "Bantuan & Umpan Balik";
        if ("about".equals(id)) return "Tentang SPLauncher";
        if ("display".equals(id)) return "Pengaturan Lanjutan";
        return "Pengaturan Launcher";
    }

    private Runnable go(final String id) {
        return new Runnable() { @Override public void run() { showPage(id); } };
    }

    private void showPage(String id) { showPage(id, true); }

    private void showPage(String id, boolean push) {
        final int keepY = id.equals(currentPage) ? sv.getScrollY() : 0;
        if (push && !id.equals(currentPage)) stack.add(currentPage);
        currentPage = id;
        page.removeAllViews();
        barTitle.setText(titleOf(id));
        if ("hub".equals(id)) pageHub();
        else if ("main".equals(id)) pageMain();
        else if ("iconswidget".equals(id)) pageIconsWidgets();
        else if ("glass".equals(id)) pageGlass();
        else if ("theme".equals(id)) pageTheme();
        else if ("wallpaper".equals(id)) pageWallpaper();
        else if ("transition".equals(id)) pageTransition();
        else if ("layout".equals(id)) pageLayout();
        else if ("widgetplace".equals(id)) pageWidgetPlace();
        else if ("drawer".equals(id)) pageDrawer();
        else if ("display".equals(id)) pageDisplay();
        else if ("gestures".equals(id)) pageGestures();
        else if ("notif".equals(id)) pageNotif();
        else if ("security".equals(id)) pageSecurity();
        else if ("help".equals(id)) pageHelp();
        else if ("about".equals(id)) pageAbout();
        else pageHub();
        sv.scrollTo(0, 0);
        if (keepY > 0) sv.post(new Runnable() { @Override public void run() { sv.scrollTo(0, keepY); } });
        refreshAccess();
        refreshDefault();
    }

    @Override public void onBackPressed() {
        if (!stack.isEmpty()) {
            showPage(stack.remove(stack.size() - 1), false);
        } else if (!"hub".equals(currentPage)) {
            showPage("hub", false);
        } else {
            super.onBackPressed();
        }
    }

    private String versionName() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (Throwable t) { return "1.0.0 (Beta Test)"; }
    }

    private String themeName() {
        switch (prefs.theme()) {
            case Prefs.THEME_LIGHT: return "Terang";
            case Prefs.THEME_DARK: return "Gelap";
            case Prefs.THEME_SCHEDULE: return "Jadwal";
            default: return "Default";
        }
    }

    private int rowsShown() { return prefs.rowsPref() == 0 ? 6 : prefs.rowsPref(); }

    // ---------------------------------------------------------------- halaman

    /** Halaman utama "Pengaturan Launcher" (sesuai desain). */
    private void pageHub() {
        LinearLayout head = card();
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(14), dp(14), dp(14), dp(14));
        ImageView icon = new ImageView(this);
        try { icon.setImageDrawable(getPackageManager().getApplicationIcon(getPackageName())); } catch (Throwable ignored) { }
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(58), dp(58));
        ilp.setMarginEnd(dp(14));
        head.addView(icon, ilp);
        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        names.addView(text("SPLauncher", 18, fg, true));
        names.addView(text("Atur tampilan, ikon, dan gaya launcher sesuai keinginanmu.", 13, sub, false));
        head.addView(names, new LinearLayout.LayoutParams(0, -2, 1f));
        head.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { showPage("about"); } });
        addCard(head);

        addCard(defaultLauncherCard());

        LinearLayout g1 = card();
        g1.addView(navRow(R.drawable.sym_photo, 0xFF4C8DFF, "Tampilan Utama", null, go("main")));
        g1.addView(divider());
        g1.addView(navRow(R.drawable.sym_calendar, 0xFF7B6CF6, "Ikon & Widget", null, go("iconswidget")));
        g1.addView(divider());
        g1.addView(navRow(R.drawable.sym_brightness, 0xFF2D9CFF, "Efek Liquid Glass", null, go("glass")));
        addCard(g1);

        section("PERSONALISASI");
        LinearLayout g2 = card();
        g2.addView(valueRow(R.drawable.sym_moon, 0xFF7B6CF6, "Tema", themeName(), go("theme")));
        g2.addView(divider());
        g2.addView(valueRow(R.drawable.sym_photo, 0xFF3A7BD5, "Wallpaper", Wallpapers.nameOf(prefs.wallpaper()), go("wallpaper")));
        g2.addView(divider());
        g2.addView(valueRow(R.drawable.sym_play, 0xFF2D9CFF, "Animasi Transisi", Prefs.TRANSITION_NAMES[prefs.transition()], go("transition")));
        g2.addView(divider());
        g2.addView(valueRow(R.drawable.sym_calculator, 0xFF4C8DFF, "Tata Letak Ikon", prefs.cols() + " x " + rowsShown(), go("layout")));
        g2.addView(divider());
        g2.addView(valueRow(R.drawable.sym_folder, 0xFF3478F6, "Laci Aplikasi", Prefs.DRAWER_NAMES[prefs.drawerMode()], go("drawer")));
        addCard(g2);

        section("FITUR LAINNYA");
        LinearLayout g3 = card();
        g3.addView(switchRow(R.drawable.sym_bell, 0xFFFF453A, "Notifikasi", "notifBadges", prefs.notifBadges(), go("notif")));
        g3.addView(divider());
        g3.addView(valueRow(R.drawable.sym_next, 0xFF5E8BFF, "Gestur", "", go("gestures")));
        g3.addView(divider());
        g3.addView(valueRow(R.drawable.sym_lock, 0xFF3A7BD5, "Kunci Layar", "", go("security")));
        g3.addView(divider());
        g3.addView(valueRow(R.drawable.sym_messages, 0xFF34C759, "Bantuan & Umpan Balik", "", go("help")));
        addCard(g3);
    }

    /** "Tampilan Utama" = panel Opsi Tampilan Launcher. */
    private void pageMain() {
        TextView intro = text("Sesuaikan tampilan launcher sesuai gaya dan preferensimu.", 13.5f, sub, false);
        intro.setPadding(dp(4), dp(4), dp(4), dp(14));
        page.addView(intro);

        // 1. Layout Ikon
        TextView v1 = text(prefs.cols() + " x " + rowsShown(), 14.5f, sub, false);
        addCard(optionCard(thumbBox(0xFFE3EEFF, 0xFFC4DAF7, centered(new GridPreview(this, prefs.cols(), rowsShown()), 44)),
                "Layout Ikon", v1, null, chevron(), go("layout")));

        // 2. Ukuran Ikon (5 tingkat)
        ImageView cam = new ImageView(this);
        cam.setImageResource(R.drawable.ios_kamera);
        TextView v2 = text(Prefs.ICON_STEP_NAMES[prefs.iconStep()], 14.5f, sub, false);
        addCard(optionCard(thumbBox(0xFF9CC9FF, 0xFF4C8DFF, centered(cam, 38)),
                "Ukuran Ikon", v2, sizeSteps(), null, null));

        // 3. Efek Liquid Glass (toggle)
        boolean on = prefs.glassMode() != Glass.MODE_SOLID;
        final TextView v3 = text(on ? "Aktif" : "Nonaktif", 14.5f, sub, false);
        Switch sw = new Switch(this);
        sw.setChecked(on);
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean isOn) {
                prefs.put("glassMode", isOn ? Glass.MODE_BLUR : Glass.MODE_SOLID);
                v3.setText(isOn ? "Aktif" : "Nonaktif");
            }
        });
        addCard(optionCard(thumbBox(0xFF6FB1FF, 0xFF2F6FE0, new Thumb(this, 4)),
                "Efek Liquid Glass", v3, null, sw, null));

        // 4. Animasi Transisi
        TextView v4 = text(Prefs.TRANSITION_NAMES[prefs.transition()], 14.5f, sub, false);
        addCard(optionCard(thumbBox(0xFF6FB1FF, 0xFF2F6FE0, new Thumb(this, 3)),
                "Animasi Transisi", v4, null, chevron(), go("transition")));

        // 5. Tata Letak Widget
        TextView v5 = text(Prefs.WIDGET_PLACE_NAMES[prefs.widgetPlace()], 14.5f, sub, false);
        addCard(optionCard(thumbBox(0xFF6FB1FF, 0xFF2F6FE0, new Thumb(this, prefs.widgetPlace())),
                "Tata Letak Widget", v5, null, chevron(), go("widgetplace")));

        // 6. Laci Aplikasi
        TextView v6 = text(Prefs.DRAWER_NAMES[prefs.drawerMode()], 14.5f, sub, false);
        addCard(optionCard(thumbBox(0xFF6FB1FF, 0xFF2F6FE0, new Thumb(this, 2)),
                "Laci Aplikasi", v6, null, chevron(), go("drawer")));

        LinearLayout adv = card();
        adv.addView(navRow(R.drawable.sym_gear, 0xFF5E6B8C, "Pengaturan Lanjutan",
                "Label, dock, bilah status, launcher default", go("display")));
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-1, -2);
        alp.topMargin = dp(10);
        page.addView(adv, alp);
    }

    private void pageIconsWidgets() {
        section("GAYA IKON");
        LinearLayout g = card();
        boolean klasik = IconPacks.NONE.equals(prefs.iconPack());
        boolean mini = !klasik && prefs.iconStyle() == Prefs.STYLE_TINTED;
        boolean ios = !klasik && !mini && IconPacks.BUILTIN.equals(prefs.iconPack());
        g.addView(cardsRow(
                cardChoice(iconPreview(R.drawable.ios_kamera, 0), "iOS 27 (Liquid Glass)", ios, setIconLook(IconPacks.BUILTIN, Prefs.STYLE_NORMAL)),
                cardChoice(iconPreview(R.drawable.sym_phone, 0xFF34C759), "Klasik", klasik, setIconLook(IconPacks.NONE, Prefs.STYLE_NORMAL)),
                cardChoice(iconPreview(R.drawable.sym_gear, 0xFF5E6B8C), "Minimalis", mini, setIconLook(IconPacks.BUILTIN, Prefs.STYLE_TINTED))));
        g.addView(divider());
        g.addView(segmented("Gaya Lanjutan", "iconStyle", new String[]{"Normal", "Gelap", "Kaca", "Warna"}, new int[]{0, 1, 2, 3}, prefs.iconStyle()));
        g.addView(divider());
        g.addView(tintRow());
        g.addView(divider());
        g.addView(iconPackRow());
        page.addView(g);

        section("BENTUK & UKURAN");
        LinearLayout c1 = card();
        c1.addView(seek("Ukuran Ikon", "iconScale", 70, 130, prefs.iconScale(), "%"));
        c1.addView(divider());
        c1.addView(segmented("Bentuk Ikon", "iconShape", new String[]{"Squircle", "Bulat", "Kotak"}, new int[]{0, 1, 2}, prefs.iconShape()));
        c1.addView(divider());
        c1.addView(toggle("Tampilkan Label", "labels", prefs.labels()));
        c1.addView(divider());
        c1.addView(seek("Ukuran Teks Label", "labelSize", 9, 14, prefs.labelSize(), " pt"));
        page.addView(c1);

        section("WIDGET");
        LinearLayout w = card();
        w.addView(valueRow(R.drawable.sym_calendar, 0xFF7B6CF6, "Tata Letak Widget", Prefs.WIDGET_PLACE_NAMES[prefs.widgetPlace()], go("widgetplace")));
        w.addView(divider());
        w.addView(valueRow(R.drawable.sym_calculator, 0xFF4C8DFF, "Tata Letak Ikon", prefs.cols() + " x " + rowsShown(), go("layout")));
        w.addView(note("Tambah widget: tekan lama area kosong di Home lalu pilih Tambah Widget, atau tombol \"+\" saat mode edit. "
                + "Ukuran widget dapat diubah dari menu tekan-lama."));
        page.addView(w);
    }

    private void pageGlass() {
        addCard(glassCard());

        section("FOLDER & KATEGORI");
        LinearLayout f = card();
        f.addView(toggle("Folder Transparan", "transparentFolders", prefs.transparentFolders()));
        f.addView(note("Panel folder dan kategori memakai kaca bening. Matikan untuk panel yang lebih pekat."));
        page.addView(f);
    }

    private void pageTheme() {
        section("MODE TAMPILAN");
        LinearLayout t = card();
        int th = prefs.theme();
        t.addView(cardsRow(
                cardChoice(themePreview(0xFFE8F1FF, 0xFFB9D3F5), "Terang", th == Prefs.THEME_LIGHT, applyTheme(Prefs.THEME_LIGHT)),
                cardChoice(themePreview(0xFF24262E, 0xFF0A0B10), "Gelap", th == Prefs.THEME_DARK, applyTheme(Prefs.THEME_DARK)),
                cardChoice(themePreview(0xFFD9E7FA, 0xFF12151C), "Default", th == Prefs.THEME_SYSTEM, applyTheme(Prefs.THEME_SYSTEM)),
                cardChoice(themePreview(0xFFFFD9A0, 0xFF1B2A55), "Jadwal", th == Prefs.THEME_SCHEDULE, applyTheme(Prefs.THEME_SCHEDULE))));
        t.addView(note("Default mengikuti mode gelap sistem. Jadwal beralih otomatis ke tampilan gelap pada jam yang kamu atur."));
        page.addView(t);

        if (th == Prefs.THEME_SCHEDULE) {
            section("JADWAL MODE GELAP");
            LinearLayout s = card();
            s.addView(timeRow("Mulai gelap", "schedStart", prefs.schedStart()));
            s.addView(divider());
            s.addView(timeRow("Selesai gelap", "schedEnd", prefs.schedEnd()));
            page.addView(s);
        }
    }

    private View timeRow(String label, final String key, final int minutes) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(12), dp(16), dp(12));
        row.setMinimumHeight(dp(50));
        row.addView(text(label, 16, fg, false), new LinearLayout.LayoutParams(0, -2, 1f));
        row.addView(text(String.format(java.util.Locale.ROOT, "%02d.%02d", minutes / 60, minutes % 60), 15, accent, false));
        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                new android.app.TimePickerDialog(SettingsActivity.this, new android.app.TimePickerDialog.OnTimeSetListener() {
                    @Override public void onTimeSet(android.widget.TimePicker tp, int h, int m) {
                        prefs.put(key, h * 60 + m);
                        resumePage = currentPage;
                        recreate();
                    }
                }, minutes / 60, minutes % 60, true).show();
            }
        });
        return row;
    }

    private void pageWallpaper() {
        section("WALLPAPER BAWAAN");
        LinearLayout w = card();
        w.addView(wallpaperRow());
        w.addView(divider());
        w.addView(action("Pilih dari Galeri\u2026", new Runnable() { @Override public void run() { pickImage(); } }, accent));
        page.addView(w);
        page.addView(note("Wallpaper aktif: " + Wallpapers.nameOf(prefs.wallpaper())));
    }

    private void pageTransition() {
        section("GAYA ANIMASI");
        LinearLayout c = card();
        c.addView(radioRows("transition",
                Prefs.TRANSITION_NAMES,
                new String[]{"Membesar dari ikon dengan pantulan lembut", "Zoom dari tengah layar", "Memudar tanpa gerakan skala", "Tanpa animasi apa pun"},
                new int[]{0, 1, 2, 3}, prefs.transition()));
        page.addView(c);

        section("GERAKAN");
        LinearLayout m = card();
        m.addView(toggle("Kurangi Gerakan", "reduceMotion", prefs.reduceMotion()));
        m.addView(note("Berlaku untuk animasi membuka aplikasi, folder, menu, dan pencarian."));
        page.addView(m);
    }

    private void pageDrawer() {
        section("CARA MEMBUKA LACI APLIKASI");
        LinearLayout c = card();
        c.addView(radioRows("drawerMode",
                Prefs.DRAWER_NAMES,
                new String[]{"Geser ke atas di area Home untuk membuka daftar semua aplikasi A\u2013Z",
                        "Hanya lewat halaman App Library di ujung kanan Home"},
                new int[]{0, 1}, prefs.drawerMode()));
        page.addView(c);
        page.addView(note("Gestur geser ke atas dari Dock tetap membuka Pengalih Aplikasi."));
    }

    private void pageWidgetPlace() {
        section("POSISI WIDGET BARU");
        LinearLayout c = card();
        c.addView(radioRows("widgetPlace",
                Prefs.WIDGET_PLACE_NAMES,
                new String[]{"Widget baru ditaruh di bagian atas halaman Home", "Widget baru ditaruh di bagian bawah halaman Home"},
                new int[]{0, 1}, prefs.widgetPlace()));
        page.addView(c);
        page.addView(note("Berlaku untuk widget yang ditambahkan setelah ini. Posisi widget yang sudah ada tidak berubah."));
    }

    private void pageHelp() {
        LinearLayout c = card();
        c.addView(info("Kirim Masukan", "Email", new Runnable() { @Override public void run() { sendFeedback(); } }));
        c.addView(divider());
        c.addView(info("Channel Telegram", DEVELOPER, new Runnable() { @Override public void run() { openTelegram(); } }));
        c.addView(divider());
        c.addView(info("Kebijakan Privasi", "Lihat", new Runnable() { @Override public void run() { showPrivacy(); } }));
        c.addView(divider());
        c.addView(info("Syarat & Ketentuan", "Lihat", new Runnable() { @Override public void run() { showTerms(); } }));
        addCard(c);

        LinearLayout a = card();
        a.addView(navRow(R.drawable.sym_globe, 0xFF4A90E2, "Tentang SPLauncher", "Versi, pengembang, informasi", go("about")));
        addCard(a);

        LinearLayout s = card();
        s.addView(supportButton());
        page.addView(s);
    }

    private void sendFeedback() {
        Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + EMAIL));
        i.putExtra(Intent.EXTRA_SUBJECT, "SPLauncher - Masukan");
        try { startActivity(i); } catch (Throwable t) { Toast.makeText(this, EMAIL, Toast.LENGTH_LONG).show(); }
    }

    private void openTelegram() {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(SUPPORT_URL))); }
        catch (Throwable e) { Toast.makeText(this, SUPPORT_URL, Toast.LENGTH_LONG).show(); }
    }

    private void showPrivacy() {
        showText("Kebijakan Privasi", "SPLauncher menyimpan pengaturan, susunan Home, dan pratinjau jendela hanya di perangkat ini. "
                + "Aplikasi tidak mengirim data pribadi Anda ke server mana pun. Layanan Aksesibilitas hanya dipakai untuk "
                + "mencatat aplikasi yang dibuka dan mengambil pratinjau, bukan membaca isi layar.");
    }

    private void showTerms() {
        showText("Syarat & Ketentuan", "SPLauncher versi Beta Test disediakan apa adanya tanpa jaminan. "
                + "Gunakan dengan risiko sendiri dan laporkan masalah kepada pengembang.");
    }

    /** Toggle Efek Liquid Glass (mati = Padat), mode Blur/Kaca Cair, dan kekuatan efek. */
    private View glassCard() {
        LinearLayout c = card();
        boolean on = prefs.glassMode() != Glass.MODE_SOLID;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(10), dp(16), dp(10));
        row.setMinimumHeight(dp(54));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(text("Efek Liquid Glass", 16, fg, false));
        col.addView(text(on ? "Aktif" : "Nonaktif (panel padat)", 12.5f, sub, false));
        row.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
        Switch sw = new Switch(this);
        sw.setChecked(on);
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean isOn) {
                prefs.put("glassMode", isOn ? Glass.MODE_BLUR : Glass.MODE_SOLID);
                showPage(currentPage);
            }
        });
        row.addView(sw);
        c.addView(row);
        if (on) {
            c.addView(divider());
            c.addView(segmented("Mode Kaca", "glassMode", new String[]{"Blur", "Kaca Cair (ringan)"}, new int[]{0, 1}, prefs.glassMode()));
            c.addView(divider());
            c.addView(seek("Kekuatan Efek (mode Blur)", "blur", 0, 100, prefs.blur(), "%"));
            c.addView(divider());
            c.addView(seek("Kepekatan Panel", "glassOpacity", 10, 80, prefs.glassOpacity(), "%"));
        }
        return c;
    }

    private void pageDisplay() {
        section("LAYAR UTAMA");
        LinearLayout c2 = card();
        c2.addView(segmented("Kolom Ikon", "cols", new String[]{"4", "5", "6"}, new int[]{4, 5, 6}, prefs.cols()));
        c2.addView(divider());
        c2.addView(segmented("Jumlah Baris", "rowsPref", new String[]{"Otomatis", "5", "6", "7"}, new int[]{0, 5, 6, 7}, prefs.rowsPref()));
        c2.addView(divider());
        c2.addView(toggle("Titik Halaman", "pageDots", prefs.pageDots()));
        c2.addView(divider());
        c2.addView(toggle("Sembunyikan Bilah Status", "hideStatusBar", prefs.hideStatusBar()));
        c2.addView(divider());
        c2.addView(seek("Redupkan Wallpaper", "wallDim", 0, 60, prefs.wallDim(), "%"));
        page.addView(c2);

        section("DOCK");
        LinearLayout c3 = card();
        c3.addView(toggle("Tampilkan Dock", "dock", prefs.showDock()));
        c3.addView(divider());
        c3.addView(seek("Kelengkungan Dock", "dockRadius", 14, 40, prefs.dockRadius(), " dp"));
        c3.addView(divider());
        c3.addView(seek("Kepekatan Dock (0 = ikuti panel)", "dockOpacity", 0, 80, prefs.dockOpacity(), "%"));
        page.addView(c3);

        section("ANIMASI & UMPAN BALIK");
        LinearLayout c4 = card();
        c4.addView(toggle("Kurangi Gerakan", "reduceMotion", prefs.reduceMotion()));
        c4.addView(divider());
        c4.addView(toggle("Umpan Balik Getar", "haptics", prefs.haptics()));
        page.addView(c4);

        appsBasics();
    }

    /** Kartu status + tombol "Jadikan Launcher Default" (dipakai di halaman utama dan Pengaturan Lanjutan). */
    private LinearLayout defaultLauncherCard() {
        LinearLayout c0 = card();
        defaultStatus = text("", 14, sub, false);
        defaultStatus.setPadding(dp(16), dp(14), dp(16), 0);
        c0.addView(defaultStatus);
        c0.addView(action("Jadikan Launcher Default", new Runnable() { @Override public void run() { requestDefault(); } }, accent));
        return c0;
    }

    /** Launcher default + pengaturan Home Screen (ditaruh di Pengaturan Lanjutan). */
    private void appsBasics() {
        page.addView(defaultLauncherCard());

        section("HOME SCREEN");
        LinearLayout c4 = card();
        c4.addView(toggle("Tambah Aplikasi Baru ke Home", "autoAdd", prefs.autoAdd()));
        c4.addView(divider());
        c4.addView(action("Reset Susunan Home Screen", new Runnable() { @Override public void run() { confirmReset(); } }, 0xFFFF453A));
        page.addView(c4);
    }

    /** Pengalih Aplikasi (Recents iOS 27); bagian dari Gestur & Navigasi. */
    private void switcherSection() {
        section("PENGALIH APLIKASI (RECENTS iOS 27)");
        LinearLayout c9 = card();
        a11yStatus = new TextView(this);
        c9.addView(statusRow("Layanan Aksesibilitas", a11yStatus, new Runnable() {
            @Override public void run() { open(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); }
        }));
        c9.addView(divider());
        usageStatus = new TextView(this);
        c9.addView(statusRow("Akses Penggunaan (cadangan)", usageStatus, new Runnable() {
            @Override public void run() { open(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)); }
        }));
        c9.addView(note("Layanan Aksesibilitas dipakai untuk mencatat aplikasi yang dibuka, mengalihkan layar Recents bawaan, "
                + "dan mengambil pratinjau. Layanan ini tidak membaca isi layar dan tidak mengirim data apa pun. "
                + "Akses Penggunaan hanya cadangan untuk daftar aplikasi terbaru (tanpa pratinjau)."));
        c9.addView(divider());
        c9.addView(toggle("Ganti Recents Sistem", "switcherOnRecents", prefs.switcherOnRecents()));
        c9.addView(divider());
        c9.addView(toggle("Geser ke Atas dari Dock", "dockSwipeSwitcher", prefs.dockSwipeSwitcher()));
        c9.addView(divider());
        c9.addView(toggle("Pratinjau Jendela (snapshot)", "switcherSnapshots", prefs.switcherSnapshots()));
        c9.addView(divider());
        c9.addView(toggle("Tampilkan Nama Aplikasi", "switcherLabels", prefs.switcherLabels()));
        c9.addView(divider());
        c9.addView(toggle("Tombol Hapus Semua", "switcherClearAll", prefs.switcherClearAll()));
        c9.addView(divider());
        c9.addView(toggle("Hentikan Aplikasi Saat Digeser Tutup", "switcherKill", prefs.switcherKill()));
        c9.addView(divider());
        c9.addView(segmented("Gaya Kartu", "switcherStyle", new String[]{"Berjajar", "Tumpukan"}, new int[]{0, 1}, prefs.switcherStyle()));
        c9.addView(divider());
        c9.addView(seek("Ukuran Kartu", "switcherCardSize", 50, 80, prefs.switcherCardSize(), "%"));
        c9.addView(divider());
        c9.addView(action("Coba Pengalih Aplikasi", new Runnable() { @Override public void run() { testSwitcher(); } }, accent));
        page.addView(c9);
    }

    private void pageGestures() {
        section("HOME SCREEN");
        LinearLayout c8 = card();
        c8.addView(segmented("Ketuk Dua Kali di Home", "doubleTap", new String[]{"Mati", "Kunci", "Cari", "Recents"},
                new int[]{0, 1, 2, 3}, prefs.doubleTap()));
        c8.addView(note("\"Kunci\" mengunci layar lewat layanan Aksesibilitas (lihat Aplikasi > Pengalih Aplikasi). "
                + "Setelah terkunci, bangunkan layar dengan fitur ketuk dua kali bawaan perangkat."));
        c8.addView(divider());
        c8.addView(toggle("Tarik ke Bawah untuk Pencarian", "pullSearch", prefs.pullSearch()));
        page.addView(c8);

        section("TARIK DARI ATAS");
        LinearLayout c5 = card();
        c5.addView(toggle("Gestur Tarik dari Atas", "topGestures", prefs.topGestures()));
        c5.addView(divider());
        c5.addView(toggle("Zona Atas Gaya iOS 27", "topZones27", prefs.topZones27()));
        c5.addView(note("Mode iOS 27: sudut kiri = Pusat Notifikasi, tengah = Cari, sudut kanan = Control Center. "
                + "Jika dimatikan: kiri/tengah = Notifikasi, kanan = Control Center."));
        page.addView(c5);

        switcherSection();
    }

    private void pageNotif() {
        LinearLayout c5 = card();
        notifStatus = new TextView(this);
        c5.addView(statusRow("Akses Notifikasi", notifStatus, new Runnable() {
            @Override public void run() { open(NotificationCenter.accessSettingsIntent()); }
        }));
        c5.addView(divider());
        c5.addView(toggle("Badge Notifikasi pada Ikon", "notifBadges", prefs.notifBadges()));
        page.addView(c5);

        section("CONTROL CENTER");
        LinearLayout c6 = card();
        writeStatus = new TextView(this);
        c6.addView(statusRow("Ubah Setelan Sistem (kecerahan)", writeStatus, new Runnable() {
            @Override public void run() {
                open(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + getPackageName())));
            }
        }));
        c6.addView(divider());
        dndStatus = new TextView(this);
        c6.addView(statusRow("Akses Jangan Ganggu (Fokus)", dndStatus, new Runnable() {
            @Override public void run() { open(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)); }
        }));
        c6.addView(note("Android tidak mengizinkan aplikasi biasa menyalakan/mematikan Wi-Fi, Bluetooth, data seluler, "
                + "atau mode pesawat secara langsung. Ubin tersebut menampilkan status dan membuka panel sistem."));
        page.addView(c6);
    }

    private void pageSecurity() {
        section("KUNCI LAYAR");
        LinearLayout k = card();
        a11yStatus = new TextView(this);
        k.addView(statusRow("Layanan Aksesibilitas", a11yStatus, new Runnable() {
            @Override public void run() { open(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); }
        }));
        k.addView(note("Dipakai agar ketuk dua kali di Home bisa mengunci layar (atur di Gestur). "
                + "Setelah terkunci, bangunkan layar dengan tombol daya atau fitur ketuk dua kali bawaan perangkat."));
        page.addView(k);

        section("SUSUNAN HOME");
        LinearLayout c = card();
        c.addView(toggle("Kunci Susunan Home", "lockLayout", prefs.lockLayout()));
        c.addView(note("Saat dikunci, ikon tidak bisa digeser/dihapus dan mode edit dinonaktifkan."));
        page.addView(c);

        section("PRIVASI");
        LinearLayout p = card();
        p.addView(action("Hapus Pratinjau Tersimpan", new Runnable() {
            @Override public void run() {
                Recents.clearSnapshots(SettingsActivity.this);
                Toast.makeText(SettingsActivity.this, "Pratinjau dihapus", Toast.LENGTH_SHORT).show();
            }
        }, 0xFFFF453A));
        p.addView(note("Pratinjau jendela disimpan hanya di perangkat ini. Aplikasi yang memblokir tangkapan layar (mis. perbankan) "
                + "tampil sebagai kartu ikon."));
        page.addView(p);
    }

    private void pageLayout() {
        section("TATA LETAK IKON");
        LinearLayout c = card();
        c.addView(cardsRow(
                layoutChoice(4, 6, "4 \u00D7 6 (Standar)"),
                layoutChoice(5, 6, "5 \u00D7 6 (Padat)"),
                layoutChoice(6, 7, "6 \u00D7 7 (Maksimal)")));
        page.addView(c);

        section("WIDGET");
        LinearLayout w = card();
        w.addView(note("Tambah widget: tekan lama area kosong di Home lalu pilih Tambah Widget, atau tombol \"+\" saat mode edit. "
                + "Ukuran widget dapat diubah dari menu tekan-lama."));
        page.addView(w);

        section("ANIMASI");
        LinearLayout a = card();
        a.addView(toggle("Kurangi Gerakan", "reduceMotion", prefs.reduceMotion()));
        a.addView(divider());
        a.addView(toggle("Titik Halaman", "pageDots", prefs.pageDots()));
        page.addView(a);
    }

    private void pageAbout() {
        LinearLayout c = card();
        c.addView(info("Pengembang", DEVELOPER, null));
        c.addView(divider());
        c.addView(info("Channel Telegram", DEVELOPER, new Runnable() {
            @Override public void run() {
                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(SUPPORT_URL))); }
                catch (Throwable e) { Toast.makeText(SettingsActivity.this, SUPPORT_URL, Toast.LENGTH_LONG).show(); }
            }
        }));
        c.addView(divider());
        c.addView(info("Dukungan", "Hubungi Kami", new Runnable() {
            @Override public void run() {
                Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + EMAIL));
                i.putExtra(Intent.EXTRA_SUBJECT, "SPLauncher - Masukan");
                try { startActivity(i); } catch (Throwable t) { Toast.makeText(SettingsActivity.this, EMAIL, Toast.LENGTH_LONG).show(); }
            }
        }));
        c.addView(divider());
        c.addView(info("Kebijakan Privasi", "Lihat", new Runnable() {
            @Override public void run() {
                showText("Kebijakan Privasi", "SPLauncher menyimpan pengaturan, susunan Home, dan pratinjau jendela hanya di perangkat ini. "
                        + "Aplikasi tidak mengirim data pribadi Anda ke server mana pun. Layanan Aksesibilitas hanya dipakai untuk "
                        + "mencatat aplikasi yang dibuka dan mengambil pratinjau, bukan membaca isi layar.");
            }
        }));
        c.addView(divider());
        c.addView(info("Syarat & Ketentuan", "Lihat", new Runnable() {
            @Override public void run() {
                showText("Syarat & Ketentuan", "SPLauncher versi Beta Test disediakan apa adanya tanpa jaminan. "
                        + "Gunakan dengan risiko sendiri dan laporkan masalah kepada pengembang.");
            }
        }));
        page.addView(c);

        section("DETAIL");
        page.addView(aboutCard());
    }

    private void showText(String title, String msg) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(msg).setPositiveButton("Tutup", null).show();
    }

    // ---------------------------------------------------------------- komponen gaya baru

    private View glyphBox(int res, int color, int boxDp) {
        ImageView iv = new ImageView(this);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{U.lerpColor(color, 0xFFFFFFFF, 0.28f), color});
        g.setCornerRadius(dp(boxDp * 0.28f));
        iv.setBackground(g);
        int pad = dp(boxDp * 0.22f);
        iv.setPadding(pad, pad, pad, pad);
        Drawable d = getDrawable(res);
        if (d != null) {
            d = d.mutate();
            d.setTint(0xFFFFFFFF);
            iv.setImageDrawable(d);
        }
        return iv;
    }

    private View navRow(int res, int color, String title, String subtitle, final Runnable onClick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(10), dp(12), dp(10));
        row.setMinimumHeight(dp(62));
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(38), dp(38));
        ilp.setMarginEnd(dp(14));
        row.addView(glyphBox(res, color, 38), ilp);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(text(title, 16, fg, false));
        if (subtitle != null) col.addView(text(subtitle, 12.5f, sub, false));
        row.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
        row.addView(text("\u203A", 24, sub, false));
        row.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { onClick.run(); } });
        return row;
    }

    private View cardsRow(View... items) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setPadding(dp(10), dp(12), dp(10), dp(12));
        for (View v : items) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
            lp.setMargins(dp(5), 0, dp(5), 0);
            r.addView(v, lp);
        }
        return r;
    }

    /** Kartu pilihan: pratinjau + label; yang terpilih diberi garis aksen. */
    private View cardChoice(View preview, String label, boolean selected, final Runnable onClick) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        FrameLayout box = new FrameLayout(this);
        GradientDrawable bd = new GradientDrawable();
        bd.setCornerRadius(dp(16));
        bd.setColor(Color.TRANSPARENT);
        bd.setStroke(dp(selected ? 2.5f : 1f), selected ? accent : line);
        box.setBackground(bd);
        box.setPadding(dp(5), dp(5), dp(5), dp(5));
        box.addView(preview, new FrameLayout.LayoutParams(-1, dp(62)));
        col.addView(box, new LinearLayout.LayoutParams(-1, -2));
        TextView t = text(label, 12.5f, selected ? accent : fg, selected);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(6), 0, 0);
        col.addView(t, new LinearLayout.LayoutParams(-1, -2));
        col.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { onClick.run(); } });
        return col;
    }

    private View themePreview(int top, int bottom) {
        View v = new View(this);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{top, bottom});
        g.setCornerRadius(dp(12));
        v.setBackground(g);
        return v;
    }

    /** Pratinjau ikon: sprite PNG bila res adalah ios_*, atau kotak berwarna + glyph bila color != 0. */
    private View iconPreview(int res, int color) {
        FrameLayout f = new FrameLayout(this);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(12));
        g.setColor(dark ? 0x22FFFFFF : 0x12000000);
        f.setBackground(g);
        View ic;
        if (color == 0) {
            ImageView iv = new ImageView(this);
            iv.setImageResource(res);
            ic = iv;
        } else {
            ic = glyphBox(res, color, 44);
        }
        f.addView(ic, new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.CENTER));
        return f;
    }

    private Runnable applyTheme(final int value) {
        return new Runnable() {
            @Override public void run() {
                prefs.put("theme", value);
                resumePage = currentPage;
                recreate();
            }
        };
    }

    private Runnable setIconLook(final String pack, final int style) {
        return new Runnable() {
            @Override public void run() {
                prefs.put("iconPack", pack);
                prefs.put("iconStyle", style);
                IconPacks.forget();
                showPage(currentPage);
            }
        };
    }

    private View layoutChoice(final int cols, final int rows, String label) {
        boolean sel = prefs.cols() == cols && (prefs.rowsPref() == rows || (rows == 6 && prefs.rowsPref() == 0));
        return cardChoice(new GridPreview(this, cols, rows), label, sel, new Runnable() {
            @Override public void run() {
                prefs.put("cols", cols);
                prefs.put("rowsPref", rows);
                showPage(currentPage);
            }
        });
    }

    /** Pratinjau grid ikon (titik) untuk pilihan tata letak. */
    private final class GridPreview extends View {
        private final int cols, rows;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        GridPreview(Context c, int cols, int rows) {
            super(c);
            this.cols = cols;
            this.rows = rows;
            p.setColor(U.alpha(accent, 0.75f));
        }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            float cw = w / (cols + 1), ch = h / (rows + 1);
            float r = Math.min(cw, ch) * 0.34f;
            for (int i = 0; i < cols; i++)
                for (int j = 0; j < rows; j++) c.drawCircle(cw * (i + 1), ch * (j + 1), r, p);
        }
    }

    // ---------------------------------------------------------------- komponen desain baru

    private void addCard(View c) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(10);
        page.addView(c, lp);
    }

    private TextView chevron() { return text("\u203A", 24, sub, false); }

    /** Baris dengan nilai di kanan: "Tema        Default  ›". */
    private View valueRow(int res, int color, String title, String value, final Runnable onClick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(8), dp(12), dp(8));
        row.setMinimumHeight(dp(52));
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(32), dp(32));
        ilp.setMarginEnd(dp(14));
        row.addView(glyphBox(res, color, 32), ilp);
        row.addView(text(title, 16, fg, false), new LinearLayout.LayoutParams(0, -2, 1f));
        if (value != null && !value.isEmpty()) {
            TextView v = text(value, 14.5f, sub, false);
            v.setPadding(dp(8), 0, dp(4), 0);
            row.addView(v);
        }
        row.addView(chevron());
        row.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { onClick.run(); } });
        return row;
    }

    /** Baris dengan saklar di kanan; ketuk bagian lain baris membuka halaman terkait. */
    private View switchRow(int res, int color, String title, final String key, boolean value, final Runnable onClick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(8), dp(14), dp(8));
        row.setMinimumHeight(dp(52));
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(32), dp(32));
        ilp.setMarginEnd(dp(14));
        row.addView(glyphBox(res, color, 32), ilp);
        row.addView(text(title, 16, fg, false), new LinearLayout.LayoutParams(0, -2, 1f));
        Switch sw = new Switch(this);
        sw.setChecked(value);
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean on) { prefs.put(key, on); }
        });
        row.addView(sw);
        row.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { onClick.run(); } });
        return row;
    }

    /** Kartu opsi: gambar kecil di kiri, judul + nilai di tengah, panah/saklar di kanan. */
    private LinearLayout optionCard(View thumb, String title, TextView value, View extra, View trailing, final Runnable onClick) {
        LinearLayout c = card();
        c.setOrientation(LinearLayout.HORIZONTAL);
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setPadding(dp(12), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(dp(68), dp(68));
        tl.setMarginEnd(dp(14));
        c.addView(thumb, tl);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(text(title, 16, fg, true));
        value.setPadding(0, dp(2), 0, 0);
        col.addView(value);
        if (extra != null) col.addView(extra);
        c.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
        if (trailing != null) c.addView(trailing);
        if (onClick != null) c.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { onClick.run(); } });
        return c;
    }

    private View thumbBox(int c1, int c2, View inner) {
        FrameLayout f = new FrameLayout(this);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{c1, c2});
        g.setCornerRadius(dp(16));
        f.setBackground(g);
        f.addView(inner, new FrameLayout.LayoutParams(-1, -1));
        return f;
    }

    private View centered(View v, int sizeDp) {
        FrameLayout f = new FrameLayout(this);
        f.addView(v, new FrameLayout.LayoutParams(dp(sizeDp), dp(sizeDp), Gravity.CENTER));
        return f;
    }

    /** Lima kotak yang membesar untuk memilih Ukuran Ikon. */
    private View sizeSteps() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        int cur = prefs.iconStep();
        for (int i = 0; i < Prefs.ICON_STEPS.length; i++) {
            final int idx = i;
            FrameLayout cell = new FrameLayout(this);
            View sq = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dp(4 + i));
            g.setColor(i == cur ? accent : line);
            sq.setBackground(g);
            int sz = 12 + i * 3;
            cell.addView(sq, new FrameLayout.LayoutParams(dp(sz), dp(sz), Gravity.CENTER));
            cell.setContentDescription(Prefs.ICON_STEP_NAMES[i]);
            cell.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    prefs.put("iconScale", Prefs.ICON_STEPS[idx]);
                    showPage(currentPage, false);
                }
            });
            r.addView(cell, new LinearLayout.LayoutParams(dp(38), dp(36)));
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        r.setLayoutParams(lp);
        return r;
    }

    /** Daftar pilihan satu-dari-banyak dengan tanda centang. */
    private View radioRows(final String key, String[] names, String[] descs, final int[] values, int current) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < names.length; i++) {
            if (i > 0) box.addView(divider());
            final int idx = i;
            boolean sel = values[i] == current;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(16), dp(12), dp(16), dp(12));
            row.setMinimumHeight(dp(56));
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.addView(text(names[i], 16, sel ? accent : fg, sel));
            if (descs != null && descs[i] != null) col.addView(text(descs[i], 12.5f, sub, false));
            row.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
            if (sel) row.addView(text("\u2713", 20, accent, true));
            row.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    prefs.put(key, values[idx]);
                    showPage(currentPage, false);
                }
            });
            box.addView(row);
        }
        return box;
    }

    /** Gambar kecil (vektor) untuk kartu opsi: 0 widget atas, 1 widget bawah, 2 laci aplikasi, 3 transisi, 4 kaca. */
    private final class Thumb extends View {
        private final int kind;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.RectF rf = new android.graphics.RectF();

        Thumb(Context c, int kind) {
            super(c);
            this.kind = kind;
        }

        private void block(Canvas c, float l, float t, float r, float b, int color, float radius) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(color);
            rf.set(l, t, r, b);
            c.drawRoundRect(rf, radius, radius, p);
        }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), rad = w * 0.09f;
            int white = 0xE6FFFFFF, soft = 0x99FFFFFF;
            if (kind == 0 || kind == 1) {
                boolean top = kind == 0;
                float y0 = top ? h * 0.14f : h * 0.56f, y1 = top ? h * 0.44f : h * 0.86f;
                block(c, w * 0.12f, y0, w * 0.47f, y1, white, rad);
                block(c, w * 0.53f, y0, w * 0.88f, y1, white, rad);
                float ra = top ? 0.64f : 0.2f, rb = top ? 0.84f : 0.4f;
                p.setStyle(Paint.Style.FILL);
                p.setColor(soft);
                for (int i = 0; i < 4; i++) {
                    float cx = w * (0.19f + 0.205f * i);
                    c.drawCircle(cx, h * ra, w * 0.055f, p);
                    c.drawCircle(cx, h * rb, w * 0.055f, p);
                }
            } else if (kind == 2) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(w * 0.045f);
                p.setStrokeCap(Paint.Cap.ROUND);
                p.setColor(white);
                c.drawLine(w * 0.40f, h * 0.20f, w * 0.50f, h * 0.11f, p);
                c.drawLine(w * 0.50f, h * 0.11f, w * 0.60f, h * 0.20f, p);
                for (int r = 0; r < 3; r++) {
                    for (int k = 0; k < 3; k++) {
                        float l = w * (0.18f + 0.24f * k), t = h * (0.30f + 0.22f * r);
                        block(c, l, t, l + w * 0.18f, t + h * 0.16f, soft, rad * 0.7f);
                    }
                }
            } else if (kind == 3) {
                block(c, w * 0.14f, h * 0.18f, w * 0.66f, h * 0.70f, 0x66FFFFFF, rad * 1.3f);
                block(c, w * 0.34f, h * 0.30f, w * 0.86f, h * 0.82f, 0xB3FFFFFF, rad * 1.3f);
            } else {
                p.setStyle(Paint.Style.FILL);
                p.setColor(0x55FFFFFF);
                c.drawCircle(w * 0.38f, h * 0.42f, w * 0.30f, p);
                p.setColor(0x88FFFFFF);
                c.drawCircle(w * 0.62f, h * 0.60f, w * 0.24f, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(w * 0.03f);
                p.setColor(0xCCFFFFFF);
                c.drawCircle(w * 0.62f, h * 0.60f, w * 0.24f, p);
            }
        }
    }

    // ---------------------------------------------------------------- komponen

    private TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        return t;
    }

    private void section(String s) {
        TextView t = text(s, 12.5f, sub, false);
        t.setPadding(dp(16), dp(22), 0, dp(6));
        page.addView(t);
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setColor(card);
        g.setCornerRadius(dp(18));
        c.setBackground(g);
        return c;
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(line);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 1);
        lp.setMarginStart(dp(16));
        v.setLayoutParams(lp);
        return v;
    }

    private View action(String label, final Runnable r, int color) {
        TextView t = text(label, 16, color == 0 ? fg : color, false);
        t.setPadding(dp(16), 0, dp(16), 0);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setMinHeight(dp(50));
        t.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { r.run(); } });
        return t;
    }

    private View toggle(String label, final String key, boolean value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), 0, dp(16), 0);
        row.setMinimumHeight(dp(50));
        TextView t = text(label, 16, fg, false);
        row.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        Switch sw = new Switch(this);
        sw.setChecked(value);
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean on) { prefs.put(key, on); }
        });
        row.addView(sw);
        return row;
    }

    private View seek(String label, final String key, final int min, int max, int value, final String unit) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(10), dp(16), dp(6));
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.addView(text(label, 16, fg, false), new LinearLayout.LayoutParams(0, -2, 1f));
        final TextView val = text(value + unit, 15, sub, false);
        top.addView(val);
        box.addView(top);
        SeekBar sb = new SeekBar(this);
        sb.setMax(max - min);
        sb.setProgress(value - min);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean user) { val.setText((p + min) + unit); }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) { prefs.put(key, s.getProgress() + min); }
        });
        box.addView(sb, new LinearLayout.LayoutParams(-1, -2));
        return box;
    }

    private View segmented(String label, final String key, String[] names, final int[] values, int current) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(12), dp(16), dp(12));
        box.addView(text(label, 16, fg, false));
        final LinearLayout seg = new LinearLayout(this);
        seg.setOrientation(LinearLayout.HORIZONTAL);
        GradientDrawable sg = new GradientDrawable();
        sg.setColor(dark ? 0xFF2C2C2E : 0xFFE9E9EE);
        sg.setCornerRadius(dp(10));
        seg.setBackground(sg);
        seg.setPadding(dp(2), dp(2), dp(2), dp(2));
        final TextView[] tvs = new TextView[names.length];
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            TextView t = text(names[i], 14, fg, false);
            t.setGravity(Gravity.CENTER);
            t.setMinHeight(dp(34));
            tvs[i] = t;
            t.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    prefs.put(key, values[idx]);
                    paintSeg(tvs, idx);
                }
            });
            seg.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        int sel = 0;
        for (int i = 0; i < values.length; i++) if (values[i] == current) sel = i;
        paintSeg(tvs, sel);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(8);
        box.addView(seg, lp);
        return box;
    }

    private void paintSeg(TextView[] tvs, int sel) {
        for (int i = 0; i < tvs.length; i++) {
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dp(8));
            g.setColor(i == sel ? (dark ? 0xFF636366 : 0xFFFFFFFF) : Color.TRANSPARENT);
            tvs[i].setBackground(g);
            tvs[i].setTypeface(Typeface.create(i == sel ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        }
    }

    private View tintRow() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(12), dp(16), dp(12));
        box.addView(text("Warna Ikon (gaya Warna)", 16, fg, false));
        final LinearLayout rowL = new LinearLayout(this);
        rowL.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
        rp.topMargin = dp(10);
        box.addView(rowL, rp);
        final View[] dots = new View[Prefs.TINTS.length];
        for (int i = 0; i < Prefs.TINTS.length; i++) {
            final int idx = i;
            View d = new View(this);
            dots[i] = d;
            d.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { prefs.put("tint", idx); paintTints(dots); }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(34), dp(34));
            lp.setMarginEnd(dp(12));
            rowL.addView(d, lp);
        }
        paintTints(dots);
        return box;
    }

    private void paintTints(View[] dots) {
        int sel = prefs.tintIndex();
        for (int i = 0; i < dots.length; i++) {
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(Prefs.TINTS[i]);
            if (i == sel) g.setStroke(dp(3), fg);
            dots[i].setBackground(g);
        }
    }

    private View wallpaperRow() {
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(12), dp(12), dp(12), dp(12));
        final int w = dp(78), h = dp(138);
        final ImageView[] views = new ImageView[Wallpapers.PRESET_COUNT];
        for (int i = 0; i < Wallpapers.PRESET_COUNT; i++) {
            final int id = i;
            ImageView iv = new ImageView(this);
            iv.setImageBitmap(Wallpapers.preset(this, i, w, h));
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setClipToOutline(true);
            iv.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override public void getOutline(View v, android.graphics.Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(12)); }
            });
            iv.setContentDescription(Wallpapers.NAMES[i]);
            iv.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    prefs.put("wallpaper", id);
                    prefs.bumpWallpaper();
                    for (int k = 0; k < views.length; k++) views[k].setAlpha(k == id ? 1f : 0.55f);
                }
            });
            iv.setAlpha(prefs.wallpaper() == i ? 1f : 0.55f);
            views[i] = iv;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
            lp.setMarginEnd(dp(10));
            row.addView(iv, lp);
        }
        hs.addView(row);
        return hs;
    }

    private View aboutCard() {
        LinearLayout c = card();
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(16), dp(16), dp(16), dp(12));
        ImageView icon = new ImageView(this);
        try { icon.setImageDrawable(getPackageManager().getApplicationIcon(getPackageName())); } catch (Throwable ignored) { }
        head.addView(icon, new LinearLayout.LayoutParams(dp(56), dp(56)));
        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        names.setPadding(dp(14), 0, 0, 0);
        names.addView(text("SPLauncher", 20, fg, true));
        names.addView(text("Launcher bergaya Liquid Glass", 13.5f, sub, false));
        head.addView(names);
        c.addView(head);

        String ver = "1.0.0 (Beta Test)", build = "4";
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            ver = pi.versionName;
            build = String.valueOf(pi.getLongVersionCode());
        } catch (Throwable ignored) { }
        c.addView(divider());
        c.addView(info("Developer", DEVELOPER, null));
        c.addView(divider());
        c.addView(info("Versi Aplikasi", ver + " (build " + build + ")", null));
        c.addView(divider());
        c.addView(info("Email Developer", EMAIL, new Runnable() {
            @Override public void run() {
                Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + EMAIL));
                i.putExtra(Intent.EXTRA_SUBJECT, "SPLauncher - Masukan");
                try { startActivity(i); } catch (Throwable t) { Toast.makeText(SettingsActivity.this, EMAIL, Toast.LENGTH_LONG).show(); }
            }
        }));
        c.addView(divider());
        c.addView(info("Android Minimum", "Android 14 (API 34)", null));
        c.addView(divider());
        TextView notes = text("Keterangan versi " + ver + "\n"
                + "\u2022 Widget: jam analog & digital, tanggal, kalender, baterai, pencarian, plus widget dari aplikasi lain\n"
                + "\u2022 Control Center: kecerahan, volume, senter, kunci rotasi, Fokus, media, dan pintasan\n"
                + "\u2022 Pusat Notifikasi dengan kartu geser-untuk-hapus dan badge pada ikon\n"
                + "\u2022 Paket ikon bergaya iOS 27 (bawaan) dan dukungan paket ikon pihak ketiga\n"
                + "\u2022 Perbaikan panel folder/kategori yang tampak setengah berwarna\n\n"
                + "Segera hadir: backup & restore susunan Home.", 13.5f, sub, false);
        notes.setPadding(dp(16), dp(12), dp(16), dp(12));
        c.addView(notes);
        c.addView(supportButton());
        return c;
    }

    /** Tombol "Support me" yang membuka Telegram SenzaProject. */
    private View supportButton() {
        TextView t = text("Support me", 16, 0xFFFFFFFF, true);
        t.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setColor(accent);
        g.setCornerRadius(dp(12));
        t.setBackground(g);
        t.setContentDescription("Support me, buka Telegram SenzaProject");
        t.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(SUPPORT_URL)));
                } catch (Throwable e) {
                    Toast.makeText(SettingsActivity.this, SUPPORT_URL, Toast.LENGTH_LONG).show();
                }
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(48));
        lp.setMargins(dp(16), dp(4), dp(16), dp(16));
        t.setLayoutParams(lp);
        return t;
    }

    /** Baris pilihan paket ikon: tampilkan pilihan sekarang, ketuk untuk memilih. */
    private View iconPackRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(12), dp(16), dp(12));
        row.setMinimumHeight(dp(50));
        row.addView(text("Paket Ikon", 16, fg, false), new LinearLayout.LayoutParams(0, -2, 1f));
        packStatus = text(IconPacks.labelOf(this, prefs.iconPack()), 15, accent, false);
        packStatus.setGravity(Gravity.END);
        row.addView(packStatus, new LinearLayout.LayoutParams(0, -2, 1.4f));
        row.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { chooseIconPack(); } });
        return row;
    }

    private void chooseIconPack() {
        final ArrayList<String> ids = new ArrayList<>();
        final ArrayList<String> names = new ArrayList<>();
        ids.add(IconPacks.BUILTIN); names.add(IconPacks.labelOf(this, IconPacks.BUILTIN));
        ids.add(IconPacks.NONE); names.add(IconPacks.labelOf(this, IconPacks.NONE));
        List<IconPacks.PackInfo> installed = IconPacks.installed(this);
        for (IconPacks.PackInfo pi : installed) { ids.add(pi.pkg); names.add(pi.label); }
        int sel = Math.max(0, ids.indexOf(prefs.iconPack()));
        new AlertDialog.Builder(this)
                .setTitle("Paket Ikon")
                .setSingleChoiceItems(names.toArray(new String[0]), sel, new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int which) {
                        prefs.put("iconPack", ids.get(which));
                        IconPacks.forget();
                        packStatus.setText(names.get(which));
                        d.dismiss();
                    }
                })
                .setNegativeButton("Batal", null)
                .show();
        if (installed.isEmpty()) {
            Toast.makeText(this, "Paket ikon pihak ketiga (format ADW/Nova) yang terpasang akan muncul di daftar.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private TextView note(String s) {
        TextView t = text(s, 12.5f, sub, false);
        t.setPadding(dp(16), 0, dp(16), dp(12));
        return t;
    }

    /** Baris dengan status "Aktif"/"Nonaktif" di kanan; ketuk membuka pengaturan sistem terkait. */
    private View statusRow(String label, TextView status, final Runnable onClick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(12), dp(16), dp(12));
        row.setMinimumHeight(dp(50));
        row.addView(text(label, 16, fg, false), new LinearLayout.LayoutParams(0, -2, 1f));
        status.setTextSize(15f);
        status.setGravity(Gravity.END);
        row.addView(status, new LinearLayout.LayoutParams(-2, -2));
        row.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { onClick.run(); } });
        return row;
    }

    private void testSwitcher() {
        Recents.requestOpen();
        open(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .setClass(this, LauncherActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private void open(Intent i) {
        try { startActivity(i); } catch (Throwable t) { Toast.makeText(this, "Tidak dapat membuka pengaturan", Toast.LENGTH_SHORT).show(); }
    }

    private void setStatus(TextView t, boolean on) {
        if (t == null) return;
        t.setText(on ? "Aktif" : "Nonaktif");
        t.setTextColor(on ? 0xFF30D158 : sub);
    }

    private void refreshAccess() {
        setStatus(notifStatus, NotificationService.isEnabled(this));
        boolean w = false;
        try { w = Settings.System.canWrite(this); } catch (Throwable ignored) { }
        setStatus(writeStatus, w);
        boolean d = false;
        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            d = nm != null && nm.isNotificationPolicyAccessGranted();
        } catch (Throwable ignored) { }
        setStatus(dndStatus, d);
        setStatus(a11yStatus, SPAccessibilityService.isEnabled(this));
        setStatus(usageStatus, Recents.usageGranted(this));
        if (packStatus != null) packStatus.setText(IconPacks.labelOf(this, prefs.iconPack()));
    }

    private View info(String k, String v, final Runnable onClick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(12), dp(16), dp(12));
        row.addView(text(k, 16, fg, false), new LinearLayout.LayoutParams(0, -2, 1f));
        TextView t = text(v, 15, onClick != null ? accent : sub, false);
        t.setGravity(Gravity.END);
        t.setTextIsSelectable(onClick == null);
        row.addView(t, new LinearLayout.LayoutParams(0, -2, 1.4f));
        if (onClick != null) row.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View x) { onClick.run(); } });
        return row;
    }

    // ---------------------------------------------------------------- aksi

    private void refreshDefault() {
        if (defaultStatus == null) return;
        boolean held = false;
        try { held = getSystemService(RoleManager.class).isRoleHeld(RoleManager.ROLE_HOME); } catch (Throwable ignored) { }
        defaultStatus.setText(held ? "SPLauncher sudah menjadi launcher default." :
                "SPLauncher belum menjadi launcher default. Jadikan default agar tombol Home membuka SPLauncher.");
    }

    private void requestDefault() {
        try {
            RoleManager rm = getSystemService(RoleManager.class);
            if (rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), REQ_ROLE);
                return;
            }
        } catch (Throwable ignored) { }
        try { startActivity(new Intent(Settings.ACTION_HOME_SETTINGS)); }
        catch (Throwable t) { Toast.makeText(this, "Buka Pengaturan > Aplikasi > Aplikasi default > Aplikasi layar utama", Toast.LENGTH_LONG).show(); }
    }

    private void pickImage() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        try { startActivityForResult(Intent.createChooser(i, "Pilih wallpaper"), REQ_IMAGE); }
        catch (Throwable t) { Toast.makeText(this, "Tidak ada aplikasi galeri", Toast.LENGTH_SHORT).show(); }
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_ROLE) refreshDefault();
        if (req == REQ_IMAGE && res == RESULT_OK && data != null && data.getData() != null) {
            final Uri u = data.getData();
            final Context ctx = getApplicationContext();
            new Thread(new Runnable() {
                @Override public void run() {
                    final boolean ok = Wallpapers.saveCustom(ctx, u);
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (ok) {
                                prefs.put("wallpaper", -1);
                                prefs.bumpWallpaper();
                                Toast.makeText(SettingsActivity.this, "Wallpaper diganti", Toast.LENGTH_SHORT).show();
                                resumePage = currentPage;
                                recreate();
                            } else {
                                Toast.makeText(SettingsActivity.this, "Gagal memuat gambar", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                }
            }).start();
        }
    }

    private void confirmReset() {
        new AlertDialog.Builder(this)
                .setTitle("Reset Home Screen?")
                .setMessage("Susunan ikon, folder, widget, dan Dock dikembalikan ke awal. Aplikasi Anda tidak dihapus.")
                .setNegativeButton("Batal", null)
                .setPositiveButton("Reset", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        LayoutStore st = new LayoutStore(SettingsActivity.this);
                        if (st.load()) {
                            try {
                                AppWidgetHost host = new AppWidgetHost(SettingsActivity.this, LauncherActivity.HOST_ID);
                                for (LayoutStore.Item ew : st.externalWidgets()) host.deleteAppWidgetId(ew.wid);
                            } catch (Throwable ignored) { }
                        }
                        st.reset();
                        prefs.bumpLayout();
                        Toast.makeText(SettingsActivity.this, "Susunan Home Screen direset", Toast.LENGTH_SHORT).show();
                    }
                }).show();
    }
}
