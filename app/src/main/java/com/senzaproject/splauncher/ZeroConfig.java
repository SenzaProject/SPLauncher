package com.senzaproject.splauncher;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

/**
 * Pengaturan Layar Nol yang bisa diubah pengguna: daftar widget (urutan, ukuran bebas, setengah/penuh lebar),
 * judul, dan tingkat peredam latar. Disimpan sebagai JSON di SharedPreferences "sp_zero".
 */
final class ZeroConfig {
    static final class Entry {
        String kind;            // jenis widget bawaan, atau LayoutStore.W_EXT
        int wid = -1;           // appWidgetId (widget eksternal)
        String prov;            // ComponentName.flattenToString() (widget eksternal)
        int spanH = 2;          // petunjuk varian tata letak widget bawaan (bukan tinggi sebenarnya)
        int hDp = 160;          // tinggi sebenarnya (dp), bebas diubah
        boolean half;           // setengah lebar (dua widget bersebelahan)

        boolean isExt() { return LayoutStore.W_EXT.equals(kind); }

        LayoutStore.Item toItem() {
            int sw = half ? 2 : 4;
            return isExt() ? LayoutStore.Item.external(wid, prov, sw, spanH) : LayoutStore.Item.widget(kind, sw, spanH);
        }
    }

    static final int H_MIN = 48, H_MAX = 640;

    final ArrayList<Entry> items = new ArrayList<>();
    String title = "Layar Nol";
    boolean showTitle = true;
    /** Peredam latar 0..90 (%). */
    int dim = 45;

    private final Context ctx;
    private final SharedPreferences sp;

    ZeroConfig(Context c) {
        ctx = c.getApplicationContext();
        sp = ctx.getSharedPreferences("sp_zero", Context.MODE_PRIVATE);
        load();
    }

    // ------------------------------------------------------------------ ukuran

    private float screenWidthDp() {
        android.util.DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        return dm.widthPixels / dm.density;
    }

    /** Tinggi bawaan (dp) untuk widget baru, mengikuti rasio sel-nya. */
    int defaultHeightDp(String kind, int spanW, int spanH) {
        if (BuiltinWidgets.SEARCH.equals(kind)) return 56;
        float full = screenWidthDp() - 32f;
        float w = spanW >= 4 ? full : (full - 12f) / 2f;
        int sw = spanW >= 4 ? 4 : 2;
        return clampH(Math.round(w * Math.max(1, spanH) / sw));
    }

    static int clampH(int v) { return U.clamp(v, H_MIN, H_MAX); }

    // ------------------------------------------------------------------ ubah daftar

    void addBuiltin(String kind, int spanW, int spanH) {
        Entry e = new Entry();
        e.kind = kind;
        e.half = spanW > 0 && spanW < 4;
        e.spanH = Math.max(1, spanH);
        e.hDp = defaultHeightDp(kind, e.half ? 2 : 4, e.spanH);
        items.add(e);
        save();
    }

    void addExternal(int wid, String prov, boolean half, int spanH, int hDp) {
        Entry e = new Entry();
        e.kind = LayoutStore.W_EXT;
        e.wid = wid;
        e.prov = prov;
        e.half = half;
        e.spanH = Math.max(1, spanH);
        e.hDp = clampH(hDp);
        items.add(e);
        save();
    }

    // ------------------------------------------------------------------ persistensi

    void defaults() {
        items.clear();
        title = "Layar Nol";
        showTitle = true;
        dim = 45;
        float cw = screenWidthDp() - 32f;
        add(BuiltinWidgets.SEARCH, 1, 56);
        add(BuiltinWidgets.CLOCK_DIGITAL, 2, Math.round(cw * 0.5f));
        add(BuiltinWidgets.DATE, 2, Math.round(cw * 0.5f));
        add(BuiltinWidgets.MONTH, 3, Math.round(cw * 0.8f));
        add(BuiltinWidgets.BATTERY, 2, Math.round(cw * 0.5f));
    }

    private void add(String kind, int spanH, int hDp) {
        Entry e = new Entry();
        e.kind = kind;
        e.spanH = spanH;
        e.hDp = clampH(hDp);
        items.add(e);
    }

    void load() {
        items.clear();
        String s = sp.getString("cfg", null);
        if (s == null) { defaults(); return; }
        try {
            JSONObject o = new JSONObject(s);
            title = o.optString("title", "Layar Nol");
            showTitle = o.optBoolean("show", true);
            dim = U.clamp(o.optInt("dim", 45), 0, 90);
            JSONArray a = o.getJSONArray("items");
            for (int i = 0; i < a.length(); i++) {
                JSONObject j = a.getJSONObject(i);
                Entry e = new Entry();
                e.kind = j.getString("k");
                e.wid = j.optInt("i", -1);
                e.prov = j.optString("p", null);
                e.spanH = Math.max(1, j.optInt("s", 2));
                e.hDp = clampH(j.optInt("h", 160));
                e.half = j.optBoolean("f", false);
                if (e.kind.isEmpty()) continue;
                items.add(e);
            }
        } catch (Throwable t) {
            defaults();
        }
    }

    void save() {
        try {
            JSONObject o = new JSONObject();
            o.put("title", title);
            o.put("show", showTitle);
            o.put("dim", dim);
            JSONArray a = new JSONArray();
            for (Entry e : items) {
                JSONObject j = new JSONObject();
                j.put("k", e.kind);
                if (e.wid >= 0) j.put("i", e.wid);
                if (e.prov != null) j.put("p", e.prov);
                j.put("s", e.spanH);
                j.put("h", e.hDp);
                j.put("f", e.half);
                a.put(j);
            }
            o.put("items", a);
            sp.edit().putString("cfg", o.toString()).apply();
        } catch (Throwable ignored) { }
    }
}
