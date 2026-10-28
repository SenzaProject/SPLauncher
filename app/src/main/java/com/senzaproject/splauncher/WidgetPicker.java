package com.senzaproject.splauncher;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Galeri widget: lembar kaca dari bawah berisi widget bawaan SPLauncher (pratinjau langsung)
 * dan widget dari aplikasi lain (AppWidgetProvider), dikelompokkan per aplikasi.
 */
final class WidgetPicker extends FrameLayout {
    interface Host {
        void onPickBuiltin(BuiltinWidgets.Spec s);
        void onPickExternal(AppWidgetProviderInfo info);
        void onPickerClosed(WidgetPicker p);
        boolean reduceMotion();
    }

    private final Host host;
    private final Prefs prefs;
    private final boolean dark;
    private final ExtWidgets.Metrics m;
    private final Executor exec;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final GlassLayout sheet;
    private final int fg, fg2;
    private final int pvW, pvH;
    private boolean closing;

    WidgetPicker(Context c, Host host, Prefs prefs, boolean dark, ExtWidgets.Metrics m, int screenW, int screenH,
                 int insTop, int insBottom, Executor exec) {
        super(c);
        this.host = host;
        this.prefs = prefs;
        this.dark = dark;
        this.m = m;
        this.exec = exec;
        fg = dark ? 0xFFFFFFFF : 0xFF111114;
        fg2 = dark ? 0xB3FFFFFF : 0x99000000;
        pvW = U.dp(c, 128);
        pvH = U.dp(c, 100);

        setClickable(true);
        setBackgroundColor(0x73000000);
        setOnClickListener(new OnClickListener() { @Override public void onClick(View v) { dismiss(true); } });

        sheet = new GlassLayout(c, 30, dark ? 0.62f : 0.70f, dark);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setClickable(true);
        int sw = Math.min(screenW - U.dp(c, 24), U.dp(c, 520));
        int sheetH = Math.min(screenH - insTop - U.dp(c, 40), Math.round(screenH * 0.82f));
        LayoutParams lp = new LayoutParams(sw, sheetH, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        lp.bottomMargin = Math.max(insBottom, U.dp(c, 8)) + U.dp(c, 8);
        addView(sheet, lp);

        // header
        FrameLayout head = new FrameLayout(c);
        TextView title = new TextView(c);
        title.setText("Widget");
        title.setTextColor(fg);
        title.setTextSize(19f);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        head.addView(title, new LayoutParams(-2, -2, Gravity.CENTER));
        TextView close = new TextView(c);
        close.setText("Tutup");
        close.setTextColor(0xFF0A84FF);
        close.setTextSize(16f);
        close.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        close.setGravity(Gravity.CENTER);
        close.setPadding(U.dp(c, 18), 0, U.dp(c, 18), 0);
        close.setOnClickListener(new OnClickListener() { @Override public void onClick(View v) { dismiss(true); } });
        head.addView(close, new LayoutParams(-2, -1, Gravity.END));
        sheet.addView(head, new LinearLayout.LayoutParams(-1, U.dp(c, 56)));
        View d = new View(c);
        d.setBackgroundColor(dark ? 0x26FFFFFF : 0x1F000000);
        sheet.addView(d, new LinearLayout.LayoutParams(-1, 1));

        ScrollView sv = new ScrollView(c);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(OVER_SCROLL_NEVER);
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, 0, 0, U.dp(c, 12));
        sv.addView(col, new ScrollView.LayoutParams(-1, -2));
        sheet.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));

        buildBuiltin(c, col);
        buildExternal(c, col);

        if (!host.reduceMotion()) {
            setAlpha(0f);
            sheet.setTranslationY(screenH * 0.25f);
            animate().alpha(1f).setDuration(200).start();
            sheet.animate().translationY(0f).setDuration(280).setInterpolator(new android.view.animation.DecelerateInterpolator(1.8f)).start();
        }
    }

    void dismiss(boolean animate) {
        if (closing) return;
        closing = true;
        final WidgetPicker self = this;
        if (animate && !host.reduceMotion()) {
            animate().alpha(0f).setDuration(170).start();
            sheet.animate().translationY(getHeight() * 0.2f).setDuration(190).withEndAction(new Runnable() {
                @Override public void run() { host.onPickerClosed(self); }
            }).start();
        } else {
            host.onPickerClosed(this);
        }
    }

    // ------------------------------------------------------------------ bagian

    private void section(Context c, LinearLayout col, String text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextColor(fg2);
        t.setTextSize(13f);
        t.setAllCaps(true);
        t.setLetterSpacing(0.04f);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setPadding(U.dp(c, 18), U.dp(c, 16), U.dp(c, 18), U.dp(c, 4));
        col.addView(t, new LinearLayout.LayoutParams(-1, -2));
    }

    private void appHeader(Context c, LinearLayout col, Drawable icon, String label) {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(U.dp(c, 18), U.dp(c, 14), U.dp(c, 18), U.dp(c, 4));
        if (icon != null) {
            ImageView iv = new ImageView(c);
            iv.setImageDrawable(icon);
            r.addView(iv, new LinearLayout.LayoutParams(U.dp(c, 24), U.dp(c, 24)));
        }
        TextView t = new TextView(c);
        t.setText(label);
        t.setTextColor(fg);
        t.setTextSize(15f);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setSingleLine(true);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
        tl.leftMargin = icon != null ? U.dp(c, 10) : 0;
        r.addView(t, tl);
        col.addView(r, new LinearLayout.LayoutParams(-1, -2));
    }

    private View row(Context c, FrameLayout preview, String title, String sub, final Runnable pick) {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(U.dp(c, 18), U.dp(c, 8), U.dp(c, 18), U.dp(c, 8));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(dark ? 0x1AFFFFFF : 0x12000000);
        bg.setCornerRadius(U.dpf(c, 16));
        preview.setBackground(bg);
        r.addView(preview, new LinearLayout.LayoutParams(pvW, pvH));

        LinearLayout tx = new LinearLayout(c);
        tx.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(c);
        t.setText(title);
        t.setTextColor(fg);
        t.setTextSize(16f);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tx.addView(t, new LinearLayout.LayoutParams(-1, -2));
        TextView s = new TextView(c);
        s.setText(sub);
        s.setTextColor(fg2);
        s.setTextSize(13f);
        s.setMaxLines(2);
        s.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tx.addView(s, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, -2, 1f);
        tl.leftMargin = U.dp(c, 14);
        r.addView(tx, tl);

        TextView plus = new TextView(c);
        plus.setText("+");
        plus.setTextColor(0xFFFFFFFF);
        plus.setTextSize(22f);
        plus.setGravity(Gravity.CENTER);
        plus.setIncludeFontPadding(false);
        GradientDrawable pb = new GradientDrawable();
        pb.setShape(GradientDrawable.OVAL);
        pb.setColor(0xFF0A84FF);
        plus.setBackground(pb);
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(U.dp(c, 32), U.dp(c, 32));
        pl.leftMargin = U.dp(c, 10);
        r.addView(plus, pl);

        r.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) { dismiss(false); pick.run(); }
        });
        r.setOnTouchListener(new OnTouchListener() {
            @Override public boolean onTouch(View v, android.view.MotionEvent e) {
                int a = e.getActionMasked();
                if (a == android.view.MotionEvent.ACTION_DOWN) v.setAlpha(0.6f);
                else if (a == android.view.MotionEvent.ACTION_UP || a == android.view.MotionEvent.ACTION_CANCEL) v.setAlpha(1f);
                return false;
            }
        });
        return r;
    }

    // ------------------------------------------------------------------ widget bawaan

    private void buildBuiltin(Context c, LinearLayout col) {
        section(c, col, "SPLauncher");
        for (final BuiltinWidgets.Spec s : BuiltinWidgets.CATALOG) {
            LayoutStore.Item it = LayoutStore.Item.widget(s.kind, s.widthFor(m.cols), s.heightFor(m.rows));
            BuiltinWidgets.Base v = BuiltinWidgets.create(c, it, prefs, dark);
            int wpx = m.widthPx(it.spanW);
            int hpx = it.wkind.equals(BuiltinWidgets.SEARCH) ? U.dp(c, 56) : m.heightPx(it.spanH);
            float pad = U.dpf(c, 8);
            float sc = Math.min((pvW - 2 * pad) / wpx, (pvH - 2 * pad) / hpx);
            FrameLayout holder = new FrameLayout(c);
            LayoutParams lp = new LayoutParams(wpx, hpx, Gravity.CENTER);
            v.setScaleX(sc);
            v.setScaleY(sc);
            holder.addView(v, lp);
            String size = BuiltinWidgets.sizeLabel(it.spanW, s.kind.equals(BuiltinWidgets.SEARCH) ? 1 : it.spanH);
            col.addView(row(c, holder, s.title, size + " · " + s.desc, new Runnable() {
                @Override public void run() { host.onPickBuiltin(s); }
            }), new LinearLayout.LayoutParams(-1, -2));
        }
    }

    // ------------------------------------------------------------------ widget aplikasi lain

    private void buildExternal(final Context c, LinearLayout col) {
        List<AppWidgetProviderInfo> all = new ArrayList<>();
        try {
            List<AppWidgetProviderInfo> l = AppWidgetManager.getInstance(c).getInstalledProviders();
            if (l != null) all.addAll(l);
        } catch (Throwable ignored) { }
        final PackageManager pm = c.getPackageManager();
        LinkedHashMap<String, List<AppWidgetProviderInfo>> groups = new LinkedHashMap<>();
        final Map<String, String> labels = new java.util.HashMap<>();
        for (AppWidgetProviderInfo i : all) {
            if ((i.widgetCategory & AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN) == 0) continue;
            String pkg = i.provider.getPackageName();
            if (pkg.equals(c.getPackageName())) continue;
            List<AppWidgetProviderInfo> g = groups.get(pkg);
            if (g == null) {
                g = new ArrayList<>();
                groups.put(pkg, g);
                String lb = pkg;
                try { lb = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString(); } catch (Throwable ignored) { }
                labels.put(pkg, lb);
            }
            g.add(i);
        }
        section(c, col, "Dari aplikasi lain");
        if (groups.isEmpty()) {
            TextView t = new TextView(c);
            t.setText("Belum ada widget dari aplikasi lain yang tersedia.");
            t.setTextColor(fg2);
            t.setTextSize(14f);
            t.setPadding(U.dp(c, 18), U.dp(c, 6), U.dp(c, 18), U.dp(c, 12));
            col.addView(t, new LinearLayout.LayoutParams(-1, -2));
            return;
        }
        ArrayList<String> pkgs = new ArrayList<>(groups.keySet());
        Collections.sort(pkgs, new Comparator<String>() {
            @Override public int compare(String a, String b) { return labels.get(a).compareToIgnoreCase(labels.get(b)); }
        });
        for (String pkg : pkgs) {
            Drawable icon = null;
            try { icon = pm.getApplicationIcon(pkg); } catch (Throwable ignored) { }
            appHeader(c, col, icon, labels.get(pkg));
            for (final AppWidgetProviderInfo info : groups.get(pkg)) {
                FrameLayout holder = new FrameLayout(c);
                final ImageView iv = new ImageView(c);
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                int p = U.dp(c, 6);
                iv.setPadding(p, p, p, p);
                holder.addView(iv, new LayoutParams(-1, -1));
                loadPreview(c, info, iv);
                CharSequence lbl = null;
                try { lbl = info.loadLabel(pm); } catch (Throwable ignored) { }
                int[] span = ExtWidgets.spanFor(info, m);
                String sub = BuiltinWidgets.sizeLabel(span[0], span[1]);
                col.addView(row(c, holder, lbl == null ? pkg : lbl.toString(), sub, new Runnable() {
                    @Override public void run() { host.onPickExternal(info); }
                }), new LinearLayout.LayoutParams(-1, -2));
            }
        }
    }

    private void loadPreview(final Context c, final AppWidgetProviderInfo info, final ImageView iv) {
        try {
            exec.execute(new Runnable() {
                @Override public void run() {
                    Drawable d = null;
                    try { d = info.loadPreviewImage(c, 0); } catch (Throwable ignored) { }
                    if (d == null) {
                        try { d = info.loadIcon(c, 0); } catch (Throwable ignored) { }
                    }
                    final Drawable fd = d;
                    main.post(new Runnable() {
                        @Override public void run() { if (fd != null && !closing) iv.setImageDrawable(fd); }
                    });
                }
            });
        } catch (Throwable ignored) { }
    }

    /** Kosongkan referensi (dipanggil Activity saat menghapus). */
    void release() { main.removeCallbacksAndMessages(null); }
}
