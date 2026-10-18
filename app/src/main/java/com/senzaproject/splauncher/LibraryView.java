package com.senzaproject.splauncher;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import android.content.pm.ApplicationInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Halaman App Library: kolom cari + kartu kategori. */
final class LibraryView extends ScrollView {
    interface Host {
        void launch(AppRepo.AppInfo a, View v);
        void longPress(AppRepo.AppInfo a, View v);
        void openCategory(String title, List<AppRepo.AppInfo> apps, View anchor);
        void openSearch();
    }

    private final LinearLayout content;

    LibraryView(Context c) {
        super(c);
        setVerticalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
        content = new LinearLayout(c);
        content.setOrientation(LinearLayout.VERTICAL);
        addView(content, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    static String category(AppRepo.AppInfo a) {
        switch (a.category) {
            case ApplicationInfo.CATEGORY_SOCIAL: return "Sosial";
            case ApplicationInfo.CATEGORY_GAME: return "Permainan";
            case ApplicationInfo.CATEGORY_AUDIO:
            case ApplicationInfo.CATEGORY_VIDEO:
            case ApplicationInfo.CATEGORY_IMAGE: return "Hiburan";
            case ApplicationInfo.CATEGORY_PRODUCTIVITY: return "Produktivitas";
            case ApplicationInfo.CATEGORY_NEWS: return "Berita & Info";
            case ApplicationInfo.CATEGORY_MAPS: return "Perjalanan";
            default: return a.system ? "Utilitas" : "Lainnya";
        }
    }

    void rebuild(List<AppRepo.AppInfo> apps, final Prefs p, final boolean dark, int topInset, int bottomInset,
                 int screenW, final Host host) {
        final Context c = getContext();
        content.removeAllViews();
        int side = U.dp(c, 18);
        content.setPadding(side, topInset + U.dp(c, 14), side, bottomInset + U.dp(c, 28));

        // kolom cari
        GlassLayout search = new GlassLayout(c, 22, dark ? 0.40f : 0.45f, dark);
        search.setOrientation(LinearLayout.HORIZONTAL);
        search.setGravity(Gravity.CENTER_VERTICAL);
        search.setElevation(0);
        View glyph = new View(c);
        glyph.setBackground(Glyphs.search(0xCCFFFFFF, U.dpf(c, 2f)));
        search.addView(glyph, new LinearLayout.LayoutParams(U.dp(c, 18), U.dp(c, 18)));
        ((LinearLayout.LayoutParams) glyph.getLayoutParams()).setMarginStart(U.dp(c, 14));
        TextView hint = new TextView(c);
        hint.setText("App Library");
        hint.setTextColor(0xCCFFFFFF);
        hint.setTextSize(16f);
        hint.setPadding(U.dp(c, 10), 0, 0, 0);
        search.addView(hint, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        search.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { host.openSearch(); } });
        content.addView(search, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, U.dp(c, 44)));

        // kelompokkan
        LinkedHashMap<String, ArrayList<AppRepo.AppInfo>> groups = new LinkedHashMap<>();
        ArrayList<AppRepo.AppInfo> recent = new ArrayList<>(apps);
        Collections.sort(recent, new Comparator<AppRepo.AppInfo>() {
            @Override public int compare(AppRepo.AppInfo a, AppRepo.AppInfo b) { return Long.compare(b.installTime, a.installTime); }
        });
        ArrayList<AppRepo.AppInfo> rec = new ArrayList<>();
        for (AppRepo.AppInfo a : recent) { if (rec.size() >= 4) break; rec.add(a); }
        String[] order = {"Sosial", "Hiburan", "Permainan", "Produktivitas", "Perjalanan", "Berita & Info", "Utilitas", "Lainnya"};
        if (!rec.isEmpty()) groups.put("Baru Ditambahkan", rec);
        for (String o : order) groups.put(o, new ArrayList<AppRepo.AppInfo>());
        for (AppRepo.AppInfo a : apps) groups.get(category(a)).add(a);

        int gap = U.dp(c, 16);
        int cellW = (screenW - 2 * side - gap) / 2;
        LinearLayout row = null;
        int n = 0;
        for (Map.Entry<String, ArrayList<AppRepo.AppInfo>> e : groups.entrySet()) {
            if (e.getValue().isEmpty()) continue;
            if (n % 2 == 0) {
                row = new LinearLayout(c);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rp.topMargin = U.dp(c, 18);
                content.addView(row, rp);
            }
            View cell = buildCell(c, e.getKey(), e.getValue(), cellW, p, dark, host);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(cellW, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (n % 2 == 1) cp.setMarginStart(gap);
            row.addView(cell, cp);
            n++;
        }
    }

    private View buildCell(final Context c, final String title, final List<AppRepo.AppInfo> list, int cellW,
                           final Prefs p, boolean dark, final Host host) {
        LinearLayout cell = new LinearLayout(c);
        cell.setOrientation(LinearLayout.VERTICAL);
        final GlassLayout card = new GlassLayout(c, 24, dark ? 0.32f : 0.38f, dark);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setElevation(0);
        int pad = U.dp(c, 12);
        card.setPadding(pad, pad, pad, pad);
        int ig = U.dp(c, 8);
        int mini = (cellW - 2 * pad - ig) / 2;
        for (int r = 0; r < 2; r++) {
            LinearLayout line = new LinearLayout(c);
            line.setOrientation(LinearLayout.HORIZONTAL);
            for (int k = 0; k < 2; k++) {
                int idx = r * 2 + k;
                View v;
                if (idx == 3 && list.size() > 4) {
                    TextView more = new TextView(c);
                    more.setText("+" + (list.size() - 3));
                    more.setTextColor(0xFFFFFFFF);
                    more.setTextSize(15f);
                    more.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                    more.setGravity(Gravity.CENTER);
                    more.setBackground(new Glass.GlassDrawable(more, mini * 0.24f, 0.30f, false));
                    more.setOnClickListener(new View.OnClickListener() {
                        @Override public void onClick(View x) { host.openCategory(title, list, card); }
                    });
                    v = more;
                } else if (idx < list.size()) {
                    final AppRepo.AppInfo a = list.get(idx);
                    IconView iv = new IconView(c);
                    iv.bindApp(a, p, mini - U.dp(c, 4), false, dark);
                    iv.setOnClickListener(new View.OnClickListener() {
                        @Override public void onClick(View x) { host.launch(a, x); }
                    });
                    iv.setOnLongClickListener(new View.OnLongClickListener() {
                        @Override public boolean onLongClick(View x) { host.longPress(a, x); return true; }
                    });
                    v = iv;
                } else {
                    v = new View(c);
                }
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(mini, mini);
                if (k == 1) lp.setMarginStart(ig);
                line.addView(v, lp);
            }
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (r == 1) rp.topMargin = ig;
            card.addView(line, rp);
        }
        card.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View x) { host.openCategory(title, list, card); }
        });
        cell.addView(card, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView t = new TextView(c);
        t.setText(title);
        t.setTextColor(0xE6FFFFFF);
        t.setTextSize(12.5f);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setGravity(Gravity.CENTER);
        t.setShadowLayer(U.dpf(c, 2f), 0, U.dpf(c, 1f), 0x99000000);
        t.setPadding(0, U.dp(c, 6), 0, 0);
        t.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View x) { host.openCategory(title, list, card); }
        });
        cell.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return cell;
    }
}
