package com.senzaproject.splauncher;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;

public class LayoutStoreTest {
    static void check(boolean c, String m) { if (!c) throw new AssertionError(m); }

    static LayoutStore make() throws Exception {
        Field uf = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); uf.setAccessible(true);
        sun.misc.Unsafe u = (sun.misc.Unsafe) uf.get(null);
        LayoutStore s = (LayoutStore) u.allocateInstance(LayoutStore.class);
        set(s, "pages", new ArrayList<ArrayList<LayoutStore.Item>>());
        set(s, "dock", new ArrayList<LayoutStore.Item>());
        set(s, "known", new HashSet<String>());
        return s;
    }
    static void set(Object o, String f, Object v) throws Exception {
        Field fl = LayoutStore.class.getDeclaredField(f); fl.setAccessible(true); fl.set(o, v);
    }
    static ArrayList<LayoutStore.Item> apps(int n) {
        ArrayList<LayoutStore.Item> l = new ArrayList<>();
        for (int i = 0; i < n; i++) l.add(LayoutStore.Item.app("a" + i));
        return l;
    }

    public static void main(String[] x) throws Exception {
        // 1. 25 ikon pada grid 4x6 -> 24 + 1 di halaman 2
        LayoutStore s = make();
        s.pages.add(apps(25));
        s.normalize(4, 6, 4);
        check(s.pages.size() == 2 && s.pages.get(0).size() == 24 && s.pages.get(1).size() == 1, "overflow ikon");

        // 2. widget 4x2 + 17 ikon -> 16 ikon muat (8 sel widget + 16 = 24)
        s = make();
        ArrayList<LayoutStore.Item> p = new ArrayList<>();
        p.add(LayoutStore.Item.widget("clock_digital", 4, 2));
        p.addAll(apps(17));
        s.pages.add(p);
        s.normalize(4, 6, 4);
        check(s.pages.size() == 2, "halaman 2 dibuat");
        check(s.pages.get(0).size() == 17 && s.pages.get(1).size() == 1, "17 item di hal.1 (widget + 16 ikon): " + s.pages.get(0).size());
        check(s.pages.get(0).get(0).isWidget(), "widget tetap di awal");

        // 3. addWidget pada halaman penuh -> pindah ke halaman lain / baru
        s = make();
        s.pages.add(apps(24));
        int pg = s.addWidget(LayoutStore.Item.widget("month", 4, 4), 0, 4, 6);
        check(pg == 1 && s.pages.size() == 2, "halaman baru untuk widget: " + pg);
        // halaman 2 punya ruang: widget kedua masuk di sana
        pg = s.addWidget(LayoutStore.Item.widget("clock_analog", 2, 2), 0, 4, 6);
        check(pg == 1, "widget kedua muat di hal.2: " + pg);

        // 4. widget di Dock dipindah ke halaman pertama
        s = make();
        s.pages.add(apps(3));
        s.dock.add(LayoutStore.Item.widget("battery", 2, 2));
        s.dock.add(LayoutStore.Item.app("d1"));
        s.normalize(4, 6, 4);
        check(s.dock.size() == 1 && s.pages.get(0).get(0).isWidget(), "widget keluar dari dock");

        // 5. resize jadi besar -> limpahan ke halaman berikutnya
        s = make();
        ArrayList<LayoutStore.Item> q = new ArrayList<>();
        LayoutStore.Item w = LayoutStore.Item.widget("clock_analog", 2, 2);
        q.add(w);
        q.addAll(apps(20));
        s.pages.add(q);
        s.normalize(4, 6, 4);
        check(s.pages.size() == 1, "20 ikon + 2x2 muat (24 sel): " + s.pages.size());
        s.resizeWidget(w, 4, 4);
        s.normalize(4, 6, 4);
        check(s.pages.size() == 2 && LayoutStore.pageFits(s.pages.get(0), 4, 6), "limpahan setelah resize");

        // 6. kolom berubah 4 -> 3: widget 4 lebar tetap terpasang (di-clamp)
        s = make();
        ArrayList<LayoutStore.Item> r = new ArrayList<>();
        r.add(LayoutStore.Item.widget("search", 4, 1));
        r.addAll(apps(5));
        s.pages.add(r);
        s.normalize(3, 6, 3);
        check(s.pages.size() == 1 && s.pages.get(0).size() == 6, "clamp lebar widget");

        // 7. halaman kosong dihapus, minimal satu tersisa
        s = make();
        s.pages.add(new ArrayList<LayoutStore.Item>());
        s.pages.add(apps(2));
        s.pages.add(new ArrayList<LayoutStore.Item>());
        s.normalize(4, 6, 4);
        check(s.pages.size() == 1 && s.pages.get(0).size() == 2, "halaman kosong dibuang");

        // 8. widget eksternal helper
        LayoutStore.Item e = LayoutStore.Item.external(42, "a.b/c.D", 4, 2);
        check(e.isExternal() && e.wid == 42 && e.isWidget(), "external item");
        s = make(); s.pages.add(new ArrayList<LayoutStore.Item>(java.util.Arrays.asList(e, LayoutStore.Item.app("z"))));
        check(s.externalWidgets().size() == 1, "externalWidgets");
        System.out.println("LayoutStore OK");
    }
}
