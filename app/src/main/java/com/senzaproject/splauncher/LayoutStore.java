package com.senzaproject.splauncher;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.provider.MediaStore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** Model susunan Home Screen: halaman, Dock, folder. Disimpan sebagai JSON. */
final class LayoutStore {
    static final int APP = 0, FOLDER = 1, WIDGET = 2;
    /** Jenis widget bawaan; selain ini berarti widget eksternal (AppWidgetProvider) dengan wid/wprov. */
    static final String W_EXT = "ext";

    static final class Item {
        int type;
        String key;                 // APP: "pkg/cls"
        String title = "Folder";    // FOLDER
        ArrayList<String> kids = new ArrayList<>();
        // WIDGET
        String wkind;               // "clock_analog", "clock_digital", "date", "month", "battery", "search", atau W_EXT
        int spanW = 1, spanH = 1;   // ukuran dalam sel grid
        int wid = -1;               // appWidgetId (widget eksternal)
        String wprov;               // ComponentName.flattenToString() (widget eksternal)

        static Item app(String key) { Item i = new Item(); i.type = APP; i.key = key; return i; }
        static Item folder(String title) { Item i = new Item(); i.type = FOLDER; i.title = title; return i; }
        static Item widget(String kind, int sw, int sh) {
            Item i = new Item(); i.type = WIDGET; i.wkind = kind; i.spanW = sw; i.spanH = sh; return i;
        }
        static Item external(int appWidgetId, String provider, int sw, int sh) {
            Item i = widget(W_EXT, sw, sh); i.wid = appWidgetId; i.wprov = provider; return i;
        }
        boolean isWidget() { return type == WIDGET; }
        boolean isExternal() { return type == WIDGET && W_EXT.equals(wkind); }
    }

    final ArrayList<ArrayList<Item>> pages = new ArrayList<>();
    final ArrayList<Item> dock = new ArrayList<>();
    final HashSet<String> known = new HashSet<>();
    boolean loaded;

    private final SharedPreferences sp;

    LayoutStore(Context c) { sp = c.getApplicationContext().getSharedPreferences("sp_layout", Context.MODE_PRIVATE); }

    // ---------- persistensi ----------

    boolean load() {
        pages.clear(); dock.clear(); known.clear();
        String s = sp.getString("layout", null);
        if (s == null) return false;
        try {
            JSONObject o = new JSONObject(s);
            JSONArray pa = o.getJSONArray("pages");
            for (int i = 0; i < pa.length(); i++) pages.add(readList(pa.getJSONArray(i)));
            dock.addAll(readList(o.getJSONArray("dock")));
            JSONArray k = o.optJSONArray("known");
            if (k != null) for (int i = 0; i < k.length(); i++) known.add(k.getString(i));
            loaded = true;
            return true;
        } catch (Throwable t) {
            pages.clear(); dock.clear(); known.clear();
            return false;
        }
    }

    void save() {
        try {
            JSONObject o = new JSONObject();
            JSONArray pa = new JSONArray();
            for (ArrayList<Item> p : pages) pa.put(writeList(p));
            o.put("pages", pa);
            o.put("dock", writeList(dock));
            JSONArray k = new JSONArray();
            for (String s : known) k.put(s);
            o.put("known", k);
            sp.edit().putString("layout", o.toString()).apply();
        } catch (Throwable ignored) { }
    }

    void reset() { sp.edit().remove("layout").apply(); pages.clear(); dock.clear(); known.clear(); loaded = false; }

    private static ArrayList<Item> readList(JSONArray a) throws Exception {
        ArrayList<Item> l = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.getJSONObject(i);
            if (o.getInt("t") == WIDGET) {
                Item w = Item.widget(o.optString("k", ""), Math.max(1, o.optInt("w", 2)), Math.max(1, o.optInt("h", 2)));
                w.wid = o.optInt("i", -1);
                w.wprov = o.optString("p", null);
                if (w.wkind.isEmpty()) continue;
                l.add(w);
            } else if (o.getInt("t") == FOLDER) {
                Item f = Item.folder(o.optString("n", "Folder"));
                JSONArray c = o.getJSONArray("c");
                for (int j = 0; j < c.length(); j++) f.kids.add(c.getString(j));
                l.add(f);
            } else {
                l.add(Item.app(o.getString("k")));
            }
        }
        return l;
    }

    private static JSONArray writeList(List<Item> l) throws Exception {
        JSONArray a = new JSONArray();
        for (Item it : l) {
            JSONObject o = new JSONObject();
            o.put("t", it.type);
            if (it.type == WIDGET) {
                o.put("k", it.wkind);
                o.put("w", it.spanW);
                o.put("h", it.spanH);
                if (it.wid >= 0) o.put("i", it.wid);
                if (it.wprov != null) o.put("p", it.wprov);
            } else if (it.type == FOLDER) {
                o.put("n", it.title);
                JSONArray c = new JSONArray();
                for (String k : it.kids) c.put(k);
                o.put("c", c);
            } else {
                o.put("k", it.key);
            }
            a.put(o);
        }
        return a;
    }

    // ---------- inisialisasi & sinkronisasi ----------

    private static final String[] PRIORITY = {
            "com.senzaproject.splauncher", "com.android.settings", "com.google.android.apps.photos",
            "com.google.android.apps.nbu.files", "com.google.android.deskclock", "com.android.deskclock",
            "com.google.android.calculator", "com.android.calculator2", "com.google.android.calendar",
            "com.google.android.apps.maps", "com.android.vending", "com.android.chrome",
            "com.google.android.youtube", "com.google.android.gm", "com.whatsapp", "com.instagram.android",
            "com.facebook.katana", "org.telegram.messenger", "com.zhiliaoapp.musically",
            "com.google.android.contacts", "com.android.contacts", "com.spotify.music",
            "com.shopee.id", "com.tokopedia.tkpd", "com.gojek.app", "com.grabtaxi.passenger",
    };

    private static String resolvePkg(PackageManager pm, Intent i) {
        try {
            ResolveInfo ri = pm.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY);
            if (ri != null && ri.activityInfo != null && !"android".equals(ri.activityInfo.packageName)) {
                return ri.activityInfo.packageName;
            }
        } catch (Throwable ignored) { }
        return null;
    }

    private void createInitial(Context c, AppRepo repo, int cap, int dockCap) {
        PackageManager pm = c.getPackageManager();
        ArrayList<String> dockPkgs = new ArrayList<>();
        String[] cand = {
                resolvePkg(pm, new Intent(Intent.ACTION_DIAL)),
                resolvePkg(pm, new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))),
                resolvePkg(pm, new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))),
                resolvePkg(pm, new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)),
        };
        for (String p : cand) if (p != null && !dockPkgs.contains(p)) dockPkgs.add(p);
        HashSet<String> used = new HashSet<>();
        for (String p : dockPkgs) {
            if (dock.size() >= dockCap) break;
            AppRepo.AppInfo a = repo.findByPackage(p);
            if (a != null && used.add(a.key)) dock.add(Item.app(a.key));
        }
        ArrayList<Item> first = new ArrayList<>();
        int target = Math.min(cap, 16);
        for (String p : PRIORITY) {
            if (first.size() >= target) break;
            AppRepo.AppInfo a = repo.findByPackage(p);
            if (a != null && used.add(a.key)) first.add(Item.app(a.key));
        }
        int fill = Math.min(target, 12);
        for (AppRepo.AppInfo a : repo.all()) {
            if (first.size() >= fill) break;
            if (used.add(a.key)) first.add(Item.app(a.key));
        }
        pages.add(first);
        for (AppRepo.AppInfo a : repo.all()) known.add(a.key);
        loaded = true;
    }

    /** Muat dari penyimpanan atau buat awal; lalu sinkronkan dengan aplikasi terpasang. */
    void ensure(Context c, AppRepo repo, Prefs prefs, int cols, int rows, int dockCap) {
        if (!loaded && !load()) createInitial(c, repo, cols * rows, dockCap);
        sync(repo, prefs);
        normalize(cols, rows, dockCap);
        save();
    }

    private void sync(AppRepo repo, Prefs prefs) {
        // buang aplikasi yang sudah tidak ada
        cleanList(dock, repo);
        for (ArrayList<Item> p : pages) cleanList(p, repo);
        // tambahkan aplikasi baru
        HashSet<String> placed = new HashSet<>();
        collectKeys(dock, placed);
        for (ArrayList<Item> p : pages) collectKeys(p, placed);
        for (AppRepo.AppInfo a : repo.all()) {
            if (known.contains(a.key)) continue;
            known.add(a.key);
            if (prefs.autoAdd() && !placed.contains(a.key)) {
                if (pages.isEmpty()) pages.add(new ArrayList<Item>());
                pages.get(pages.size() - 1).add(Item.app(a.key));
            }
        }
        if (pages.isEmpty()) pages.add(new ArrayList<Item>());
    }

    private static void collectKeys(List<Item> l, HashSet<String> out) {
        for (Item it : l) {
            if (it.type == APP) out.add(it.key);
            else if (it.type == FOLDER) out.addAll(it.kids);
        }
    }

    private static void cleanList(ArrayList<Item> l, AppRepo repo) {
        for (int i = l.size() - 1; i >= 0; i--) {
            Item it = l.get(i);
            if (it.type == WIDGET) continue;
            if (it.type == APP) {
                if (repo.get(it.key) == null) l.remove(i);
            } else {
                for (int j = it.kids.size() - 1; j >= 0; j--) if (repo.get(it.kids.get(j)) == null) it.kids.remove(j);
                if (it.kids.isEmpty()) l.remove(i);
                else if (it.kids.size() == 1) l.set(i, Item.app(it.kids.get(0)));
            }
        }
    }

    /** Apakah daftar item muat pada grid cols x rows (memperhitungkan ukuran widget)? */
    static boolean pageFits(List<Item> items, int cols, int rows) {
        int n = items.size();
        int[] sw = new int[n], sh = new int[n];
        for (int i = 0; i < n; i++) { sw[i] = items.get(i).spanW; sh[i] = items.get(i).spanH; }
        return GridPacker.fits(cols, rows, sw, sh);
    }

    /**
     * Pastikan susunan halaman/dock muat; limpahan dipindah ke awal halaman berikutnya.
     * Widget tidak boleh berada di Dock (dipindah ke halaman pertama).
     */
    void normalize(int cols, int rows, int dockCap) {
        cols = Math.max(1, cols); rows = Math.max(1, rows);
        for (int i = dock.size() - 1; i >= 0; i--) {
            if (dock.get(i).type == WIDGET) {
                Item w = dock.remove(i);
                if (pages.isEmpty()) pages.add(new ArrayList<Item>());
                pages.get(0).add(0, w);
            }
        }
        while (dock.size() > dockCap) {
            Item ov = dock.remove(dock.size() - 1);
            if (pages.isEmpty()) pages.add(new ArrayList<Item>());
            pages.get(0).add(0, ov);
        }
        for (int i = 0; i < pages.size(); i++) {
            ArrayList<Item> p = pages.get(i);
            while (p.size() > 1 && !pageFits(p, cols, rows)) {
                Item ov = p.remove(p.size() - 1);
                if (i + 1 >= pages.size()) pages.add(new ArrayList<Item>());
                pages.get(i + 1).add(0, ov);
            }
        }
        // hapus halaman kosong (sisakan minimal satu)
        for (int i = pages.size() - 1; i >= 0 && pages.size() > 1; i--) {
            if (pages.get(i).isEmpty()) pages.remove(i);
        }
        if (pages.isEmpty()) pages.add(new ArrayList<Item>());
    }

    // ---------- operasi ----------

    boolean isOnHome(String key) {
        HashSet<String> s = new HashSet<>();
        collectKeys(dock, s);
        for (ArrayList<Item> p : pages) collectKeys(p, s);
        return s.contains(key);
    }

    /** Hapus item dari daftar mana pun (tidak menghapus anak folder). */
    boolean removeItem(Item it) {
        if (dock.remove(it)) return true;
        for (ArrayList<Item> p : pages) if (p.remove(it)) return true;
        return false;
    }

    /** Keluarkan aplikasi dari folder; taruh di halaman yang sama setelah folder. */
    void ejectFromFolder(Item folder, String key) {
        folder.kids.remove(key);
        ArrayList<Item> host = null;
        int idx = -1;
        if (dock.contains(folder)) { host = pages.isEmpty() ? null : pages.get(0); idx = 0; }
        for (ArrayList<Item> p : pages) { int i = p.indexOf(folder); if (i >= 0) { host = p; idx = i + 1; break; } }
        if (host == null) { if (pages.isEmpty()) pages.add(new ArrayList<Item>()); host = pages.get(0); idx = host.size(); }
        host.add(Math.min(idx, host.size()), Item.app(key));
        if (folder.kids.isEmpty()) removeItem(folder);
        else if (folder.kids.size() == 1) {
            String last = folder.kids.get(0);
            ArrayList<Item> l = dock.contains(folder) ? dock : null;
            if (l == null) for (ArrayList<Item> p : pages) if (p.contains(folder)) { l = p; break; }
            if (l != null) l.set(l.indexOf(folder), Item.app(last));
        }
    }

    void addToHome(String key) {
        if (isOnHome(key)) return;
        if (pages.isEmpty()) pages.add(new ArrayList<Item>());
        pages.get(pages.size() - 1).add(Item.app(key));
    }

    /**
     * Tempatkan widget: coba halaman pilihan dulu, lalu halaman lain, terakhir buat halaman baru.
     * @return indeks halaman tempat widget ditaruh
     */
    int addWidget(Item w, int preferred, int cols, int rows) { return addWidget(w, preferred, cols, rows, false); }

    /** Seperti di atas; top = true menaruh widget di bagian atas halaman (setelah widget yang sudah ada di atas). */
    int addWidget(Item w, int preferred, int cols, int rows, boolean top) {
        if (pages.isEmpty()) pages.add(new ArrayList<Item>());
        int n = pages.size();
        int start = U.clamp(preferred, 0, n - 1);
        for (int k = 0; k < n; k++) {
            int pg = (start + k) % n;
            ArrayList<Item> p = pages.get(pg);
            int at = p.size();
            if (top) {
                at = 0;
                while (at < p.size() && p.get(at).type == WIDGET) at++;
            }
            ArrayList<Item> test = new ArrayList<>(p);
            test.add(at, w);
            if (pageFits(test, cols, rows)) { p.add(at, w); return pg; }
        }
        ArrayList<Item> np = new ArrayList<>();
        np.add(w);
        pages.add(np);
        return pages.size() - 1;
    }

    /** Ganti ukuran widget; bila halaman jadi tidak muat, limpahan dipindah oleh normalize(). */
    void resizeWidget(Item w, int sw, int sh) { w.spanW = Math.max(1, sw); w.spanH = Math.max(1, sh); }

    /** Semua widget eksternal (untuk membersihkan appWidgetId yatim). */
    ArrayList<Item> externalWidgets() {
        ArrayList<Item> out = new ArrayList<>();
        for (ArrayList<Item> p : pages) for (Item it : p) if (it.isExternal()) out.add(it);
        return out;
    }
}
