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
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.CompoundButton;
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

/** Pengaturan SP Launcher + bagian Tentang. */
public class SettingsActivity extends Activity {
    static final String DEVELOPER = "SenzaProject";
    static final String EMAIL = "senzaproject98@gmail.com";
    static final String SUPPORT_URL = "https://t.me/senzaproject";

    private static final int REQ_ROLE = 11, REQ_IMAGE = 12;

    private Prefs prefs;
    private boolean dark;
    private int bg, card, fg, sub, line, accent;
    private LinearLayout page;
    private TextView defaultStatus, packStatus, notifStatus, writeStatus, dndStatus;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new Prefs(this);
        dark = prefs.isDark(this);
        bg = dark ? 0xFF000000 : 0xFFF2F2F7;
        card = dark ? 0xFF1C1C1E : 0xFFFFFFFF;
        fg = dark ? 0xFFFFFFFF : 0xFF111114;
        sub = dark ? 0xFF98989F : 0xFF6C6C70;
        line = dark ? 0xFF38383A : 0xFFE5E5EA;
        accent = 0xFF0A84FF;
        getWindow().getDecorView().setBackgroundColor(bg);
        getWindow().setDecorFitsSystemWindows(false);
        build();
    }

    @Override protected void onResume() {
        super.onResume();
        refreshDefault();
        refreshAccess();
    }

    private int dp(float v) { return U.dp(this, v); }

    private void build() {
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(16), dp(8), dp(16), dp(32));
        sv.addView(page, new ScrollView.LayoutParams(-1, -2));
        setContentView(sv);
        sv.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override public WindowInsets onApplyWindowInsets(View v, WindowInsets ins) {
                android.graphics.Insets i = ins.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                v.setPadding(i.left, i.top, i.right, i.bottom);
                return WindowInsets.CONSUMED;
            }
        });

        TextView title = text("Pengaturan", 32, fg, true);
        title.setPadding(dp(4), dp(16), 0, dp(4));
        page.addView(title);
        TextView subt = text("SP Launcher", 14, sub, false);
        subt.setPadding(dp(4), 0, 0, dp(12));
        page.addView(subt);

        // --- Launcher default
        LinearLayout c0 = card();
        defaultStatus = text("", 14, sub, false);
        defaultStatus.setPadding(dp(16), dp(14), dp(16), 0);
        c0.addView(defaultStatus);
        c0.addView(action("Jadikan Launcher Default", new Runnable() { @Override public void run() { requestDefault(); } }, accent));
        page.addView(c0);

        // --- Tampilan
        section("TAMPILAN");
        LinearLayout c1 = card();
        c1.addView(segmented("Tema", "theme", new String[]{"Sistem", "Terang", "Gelap"}, new int[]{0, 1, 2}, prefs.theme()));
        c1.addView(divider());
        c1.addView(segmented("Gaya Ikon", "iconStyle", new String[]{"Normal", "Gelap", "Kaca", "Warna"}, new int[]{0, 1, 2, 3}, prefs.iconStyle()));
        c1.addView(divider());
        c1.addView(tintRow());
        c1.addView(divider());
        c1.addView(iconPackRow());
        c1.addView(divider());
        c1.addView(segmented("Kolom Ikon", "cols", new String[]{"4", "5", "6"}, new int[]{4, 5, 6}, prefs.cols()));
        c1.addView(divider());
        c1.addView(seek("Ukuran Ikon", "iconScale", 70, 130, prefs.iconScale(), "%"));
        c1.addView(divider());
        c1.addView(toggle("Tampilkan Label", "labels", prefs.labels()));
        c1.addView(divider());
        c1.addView(toggle("Tampilkan Dock", "dock", prefs.showDock()));
        page.addView(c1);

        // --- Liquid Glass
        section("LIQUID GLASS");
        LinearLayout c2 = card();
        c2.addView(seek("Intensitas Blur", "blur", 0, 100, prefs.blur(), "%"));
        c2.addView(divider());
        c2.addView(seek("Kepekatan Panel", "glassOpacity", 10, 80, prefs.glassOpacity(), "%"));
        c2.addView(divider());
        c2.addView(toggle("Kurangi Transparansi", "reduceTransp", prefs.reduceTransparency()));
        c2.addView(divider());
        c2.addView(toggle("Kurangi Gerakan", "reduceMotion", prefs.reduceMotion()));
        c2.addView(divider());
        c2.addView(toggle("Umpan Balik Getar", "haptics", prefs.haptics()));
        page.addView(c2);

        // --- Wallpaper
        section("WALLPAPER");
        LinearLayout c3 = card();
        c3.addView(wallpaperRow());
        c3.addView(divider());
        c3.addView(action("Pilih dari Galeri…", new Runnable() { @Override public void run() { pickImage(); } }, accent));
        page.addView(c3);

        // --- Home Screen
        section("HOME SCREEN");
        LinearLayout c4 = card();
        c4.addView(toggle("Tambah Aplikasi Baru ke Home", "autoAdd", prefs.autoAdd()));
        c4.addView(divider());
        c4.addView(action("Reset Susunan Home Screen", new Runnable() { @Override public void run() { confirmReset(); } }, 0xFFFF453A));
        page.addView(c4);

        // --- Widget, notifikasi, Control Center
        section("WIDGET, NOTIFIKASI & CONTROL CENTER");
        LinearLayout c5 = card();
        c5.addView(toggle("Gestur Tarik dari Atas", "topGestures", prefs.topGestures()));
        c5.addView(note("Tarik ke bawah dari bagian atas Home: sisi kiri/tengah membuka Pusat Notifikasi, sisi kanan "
                + "membuka Control Center. Tarik dari bagian tengah layar untuk pencarian."));
        c5.addView(divider());
        notifStatus = new TextView(this);
        c5.addView(statusRow("Akses Notifikasi", notifStatus, new Runnable() {
            @Override public void run() { open(NotificationCenter.accessSettingsIntent()); }
        }));
        c5.addView(divider());
        c5.addView(toggle("Badge Notifikasi pada Ikon", "notifBadges", prefs.notifBadges()));
        c5.addView(divider());
        writeStatus = new TextView(this);
        c5.addView(statusRow("Ubah Setelan Sistem (kecerahan)", writeStatus, new Runnable() {
            @Override public void run() {
                open(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + getPackageName())));
            }
        }));
        c5.addView(divider());
        dndStatus = new TextView(this);
        c5.addView(statusRow("Akses Jangan Ganggu (Fokus)", dndStatus, new Runnable() {
            @Override public void run() { open(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)); }
        }));
        c5.addView(note("Android tidak mengizinkan aplikasi biasa menyalakan/mematikan Wi-Fi, Bluetooth, data seluler, "
                + "atau mode pesawat secara langsung. Ubin tersebut menampilkan status dan membuka panel sistem."));
        page.addView(c5);

        // --- Tentang
        section("TENTANG");
        page.addView(aboutCard());
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
        g.setCornerRadius(dp(14));
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
        final int w = dp(64), h = dp(112);
        final ImageView[] views = new ImageView[Wallpapers.PRESET_COUNT];
        for (int i = 0; i < Wallpapers.PRESET_COUNT; i++) {
            final int id = i;
            ImageView iv = new ImageView(this);
            iv.setImageBitmap(Wallpapers.preset(i, w, h));
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
        names.addView(text("SP Launcher", 20, fg, true));
        names.addView(text("Launcher bergaya Liquid Glass", 13.5f, sub, false));
        head.addView(names);
        c.addView(head);

        String ver = "1.1.0", build = "2";
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
                i.putExtra(Intent.EXTRA_SUBJECT, "SP Launcher - Masukan");
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
        defaultStatus.setText(held ? "SP Launcher sudah menjadi launcher default." :
                "SP Launcher belum menjadi launcher default. Jadikan default agar tombol Home membuka SP Launcher.");
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
