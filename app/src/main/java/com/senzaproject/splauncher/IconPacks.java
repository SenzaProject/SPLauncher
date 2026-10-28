package com.senzaproject.splauncher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.AlarmClock;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Paket ikon.
 *
 * 1) "iOS 27" (bawaan): aplikasi sistem inti (Telepon, Pesan, Kamera, Pengaturan, Jam, ...) digambar ulang
 *    sebagai squircle gradien dengan glyph putih ala iOS 27. Aplikasi lain tetap memakai ikon aslinya yang
 *    dipotong squircle + sorot kaca. Semua glyph digambar sendiri (res/drawable/sym_*.xml), bukan aset Apple.
 * 2) Paket ikon pihak ketiga standar ADW/Nova (appfilter.xml) yang terpasang di perangkat.
 * 3) "none": ikon asli aplikasi (hanya dipotong squircle).
 */
final class IconPacks {
    private IconPacks() {}

    static final String BUILTIN = "ios27", NONE = "none";

    /** Gaya satu peran: gradien latar atas->bawah, warna glyph, dan skala glyph terhadap ikon. */
    static final class Role {
        final String name;
        final int res, top, bottom, glyph;
        final float scale;
        Role(String name, int res, int top, int bottom, int glyph, float scale) {
            this.name = name; this.res = res; this.top = top; this.bottom = bottom; this.glyph = glyph; this.scale = scale;
        }
    }

    private static final LinkedHashMap<String, Role> ROLES = new LinkedHashMap<>();

    private static void role(String n, int res, int top, int bottom, int glyph, float scale) {
        ROLES.put(n, new Role(n, res, top, bottom, glyph, scale));
    }

    static {
        role("phone", R.drawable.sym_phone, 0xFF6DEB82, 0xFF1FB84A, 0xFFFFFFFF, 0.58f);
        role("messages", R.drawable.sym_messages, 0xFF6DEB82, 0xFF1FB84A, 0xFFFFFFFF, 0.58f);
        role("camera", R.drawable.sym_camera, 0xFFB9B9C0, 0xFF5A5A60, 0xFFFFFFFF, 0.58f);
        role("settings", R.drawable.sym_gear, 0xFFB4B4BA, 0xFF5E5E64, 0xFFFFFFFF, 0.62f);
        role("clock", R.drawable.sym_clock, 0xFF3A3A3C, 0xFF0A0A0C, 0xFFFFFFFF, 0.62f);
        role("calculator", R.drawable.sym_calculator, 0xFFFFB43D, 0xFFFF8A00, 0xFFFFFFFF, 0.58f);
        role("gallery", R.drawable.sym_photo, 0xFF6FD0FF, 0xFF2E7BFF, 0xFFFFFFFF, 0.60f);
        role("mail", R.drawable.sym_mail, 0xFF62BBFF, 0xFF1A7DFF, 0xFFFFFFFF, 0.60f);
        role("calendar", R.drawable.sym_calendar, 0xFFFFFFFF, 0xFFEDEDF2, 0xFFFF3B30, 0.60f);
        role("notes", R.drawable.sym_notes, 0xFFFFE066, 0xFFFFC107, 0xFFFFFFFF, 0.56f);
        role("files", R.drawable.sym_folder, 0xFF58B4FF, 0xFF1673F5, 0xFFFFFFFF, 0.60f);
        role("contacts", R.drawable.sym_person, 0xFFC2C2C8, 0xFF8A8A91, 0xFFFFFFFF, 0.60f);
        role("compass", R.drawable.sym_compass, 0xFF3A3A3C, 0xFF0A0A0C, 0xFFFFFFFF, 0.62f);
        role("weather", R.drawable.sym_weather, 0xFF5CC2FF, 0xFF2A78E8, 0xFFFFFFFF, 0.62f);
        role("recorder", R.drawable.sym_recorder, 0xFF3A3A3C, 0xFF0A0A0C, 0xFFFF453A, 0.56f);
        role("music", R.drawable.sym_music, 0xFFFF7A93, 0xFFFF2D55, 0xFFFFFFFF, 0.58f);
        role("downloads", R.drawable.sym_download, 0xFF5CC2FF, 0xFF1C74FF, 0xFFFFFFFF, 0.56f);
    }

    /**
     * Sprite ikon iOS 27 (res/drawable-nodpi/ios_*.png, dipotong dari lembar ikon yang diberikan pengguna) per peran.
     * Peran tanpa sprite (kalender: tanggal harus dinamis; unduhan) tetap digambar dengan glyph bawaan.
     */
    private static final HashMap<String, Integer> SPRITE = new HashMap<>();
    static {
        SPRITE.put("phone", R.drawable.ios_telepon);
        SPRITE.put("messages", R.drawable.ios_pesan);
        SPRITE.put("camera", R.drawable.ios_kamera);
        SPRITE.put("settings", R.drawable.ios_pengaturan);
        SPRITE.put("clock", R.drawable.ios_jam);
        SPRITE.put("calculator", R.drawable.ios_kalkulator);
        SPRITE.put("gallery", R.drawable.ios_foto);
        SPRITE.put("mail", R.drawable.ios_mail);
        SPRITE.put("notes", R.drawable.ios_catatan);
        SPRITE.put("files", R.drawable.ios_file);
        SPRITE.put("contacts", R.drawable.ios_kontak);
        SPRITE.put("compass", R.drawable.ios_kompas);
        SPRITE.put("weather", R.drawable.ios_cuaca);
        SPRITE.put("recorder", R.drawable.ios_memo_suara);
        SPRITE.put("music", R.drawable.ios_musik);
    }

    static boolean hasSprite(Role r) { return r != null && SPRITE.containsKey(r.name); }

    /** Aplikasi merek pihak ketiga: ikonnya dipertahankan agar tidak membingungkan pengguna. */
    private static final HashSet<String> BRAND = new HashSet<>(Arrays.asList(
            "com.google.android.apps.photos", "com.android.chrome", "com.google.android.gm",
            "com.google.android.apps.maps", "com.google.android.youtube", "com.google.android.apps.youtube.music",
            "com.google.android.apps.docs", "com.android.vending", "com.spotify.music", "com.google.android.music",
            "com.google.android.apps.messaging", "com.google.android.googlequicksearchbox", "com.senzaproject.splauncher"));

    private static Context app;
    private static volatile HashMap<String, String> roleByKey = new HashMap<>();

    static void init(Context c) { if (app == null) app = c.getApplicationContext(); }

    static Context context() { return app; }

    static Role roleFor(AppRepo.AppInfo a) {
        String n = roleByKey.get(a.key);
        return n == null ? null : ROLES.get(n);
    }

    // ------------------------------------------------------------------ deteksi peran

    private static final class Hit { String pkg, cls; }

    private static Hit hit(PackageManager pm, Intent i) {
        try {
            ResolveInfo ri = pm.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY);
            if (ri != null && ri.activityInfo != null && !"android".equals(ri.activityInfo.packageName)) {
                Hit h = new Hit();
                h.pkg = ri.activityInfo.packageName; h.cls = ri.activityInfo.name;
                return h;
            }
            // belum ada default: ambil aplikasi sistem pertama yang menangani intent ini
            List<ResolveInfo> l = pm.queryIntentActivities(i, PackageManager.MATCH_DEFAULT_ONLY);
            for (ResolveInfo r : l) {
                if (r.activityInfo == null) continue;
                ApplicationInfo ai = r.activityInfo.applicationInfo;
                if (ai != null && (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0 && !BRAND.contains(ai.packageName)) {
                    Hit h = new Hit();
                    h.pkg = r.activityInfo.packageName; h.cls = r.activityInfo.name;
                    return h;
                }
            }
        } catch (Throwable ignored) { }
        return null;
    }

    private static Intent selector(String category) { return Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, category); }

    private static void assign(HashMap<String, String> out, List<AppRepo.AppInfo> apps, String role, Hit h, String[] hints) {
        if (h == null || BRAND.contains(h.pkg)) return;
        ArrayList<AppRepo.AppInfo> in = new ArrayList<>();
        for (AppRepo.AppInfo a : apps) if (a.pkg.equals(h.pkg)) in.add(a);
        if (in.isEmpty()) return;
        AppRepo.AppInfo pick = null;
        if (in.size() == 1) {
            pick = in.get(0);
        } else {
            for (AppRepo.AppInfo a : in) if (a.cls.equals(h.cls)) pick = a;
            for (int k = 0; pick == null && k < in.size(); k++) {
                String lc = in.get(k).cls.toLowerCase(java.util.Locale.ROOT);
                for (String hint : hints) if (lc.contains(hint)) { pick = in.get(k); break; }
            }
        }
        if (pick != null && pick.system && !out.containsKey(pick.key)) out.put(pick.key, role);
    }

    /** Hitung ulang peran tiap aplikasi. @return true bila hasilnya berubah (cache ikon perlu dibuang). */
    static boolean rebuild(Context c, List<AppRepo.AppInfo> apps) {
        init(c);
        PackageManager pm = c.getPackageManager();
        HashMap<String, String> m = new HashMap<>();
        assign(m, apps, "phone", hit(pm, new Intent(Intent.ACTION_DIAL)), new String[]{"dial", "twelvekey", "phone", "call"});
        assign(m, apps, "messages", hit(pm, new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))), new String[]{"messag", "mms", "sms", "conversation"});
        assign(m, apps, "camera", hit(pm, new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)), new String[]{"camera"});
        assign(m, apps, "settings", hit(pm, new Intent(Settings.ACTION_SETTINGS)), new String[]{"settings"});
        assign(m, apps, "clock", hit(pm, new Intent(AlarmClock.ACTION_SHOW_ALARMS)), new String[]{"clock", "alarm"});
        assign(m, apps, "contacts", hit(pm, new Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI)), new String[]{"contact", "people"});
        assign(m, apps, "gallery", hit(pm, selector(Intent.CATEGORY_APP_GALLERY)), new String[]{"gallery", "photo"});
        assign(m, apps, "calculator", hit(pm, selector(Intent.CATEGORY_APP_CALCULATOR)), new String[]{"calc"});
        assign(m, apps, "calendar", hit(pm, selector(Intent.CATEGORY_APP_CALENDAR)), new String[]{"calendar", "agenda"});
        assign(m, apps, "files", hit(pm, selector(Intent.CATEGORY_APP_FILES)), new String[]{"file", "document", "manager"});
        assign(m, apps, "music", hit(pm, selector(Intent.CATEGORY_APP_MUSIC)), new String[]{"music", "player"});
        assign(m, apps, "mail", hit(pm, selector(Intent.CATEGORY_APP_EMAIL)), new String[]{"mail"});
        assign(m, apps, "weather", hit(pm, selector(Intent.CATEGORY_APP_WEATHER)), new String[]{"weather"});
        assign(m, apps, "notes", hit(pm, new Intent(Intent.ACTION_CREATE_NOTE)), new String[]{"note"});
        for (AppRepo.AppInfo a : apps) {
            if (!a.system || m.containsKey(a.key) || BRAND.contains(a.pkg)) continue;
            String p = a.pkg.toLowerCase(java.util.Locale.ROOT);
            if (p.contains("compass")) m.put(a.key, "compass");
            else if (p.contains("recorder")) m.put(a.key, "recorder");
            else if (p.equals("com.android.providers.downloads.ui")) m.put(a.key, "downloads");
        }
        boolean changed = !m.equals(roleByKey);
        roleByKey = m;
        return changed;
    }

    // ------------------------------------------------------------------ ikon bawaan "iOS 27"

    /** Gambar ikon peran: squircle gradien + glyph putih dengan bayangan lembut. */
    static Bitmap drawRole(Role r, int size) {
        Integer spriteRes = SPRITE.get(r.name);
        if (spriteRes != null && app != null) {
            try {
                Drawable sd = app.getDrawable(spriteRes);
                if (sd != null) {
                    Bitmap sb = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
                    sd.setBounds(0, 0, size, size);
                    sd.draw(new Canvas(sb));
                    return sb;
                }
            } catch (Throwable ignored) { }
        }
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        Path sq = IconFactory.squircle(size);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, 0, size, r.top, r.bottom, Shader.TileMode.CLAMP));
        cv.save();
        cv.clipPath(sq);
        cv.drawRect(0, 0, size, size, p);
        cv.restore();
        if (app == null) return bmp;
        Drawable g = app.getDrawable(r.res);
        if (g == null) return bmp;
        g = g.mutate();
        int gs = Math.max(4, Math.round(size * r.scale));
        int off = (size - gs) / 2;
        Bitmap mask = Bitmap.createBitmap(gs, gs, Bitmap.Config.ARGB_8888);
        g.setTint(0xFFFFFFFF);
        g.setBounds(0, 0, gs, gs);
        g.draw(new Canvas(mask));
        // bayangan glyph
        Paint blur = new Paint(Paint.ANTI_ALIAS_FLAG);
        blur.setMaskFilter(new BlurMaskFilter(Math.max(1f, size * 0.018f), BlurMaskFilter.Blur.NORMAL));
        int[] o = new int[2];
        Bitmap sh = mask.extractAlpha(blur, o);
        Paint sp = new Paint(Paint.FILTER_BITMAP_FLAG);
        sp.setColor(0x47000000);
        cv.drawBitmap(sh, off + o[0], off + o[1] + size * 0.02f, sp);
        sh.recycle();
        Paint gp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        gp.setColorFilter(new PorterDuffColorFilter(r.glyph, PorterDuff.Mode.SRC_IN));
        cv.drawBitmap(mask, off, off, gp);
        mask.recycle();
        return bmp;
    }

    // ------------------------------------------------------------------ paket pihak ketiga (appfilter.xml)

    static final class PackInfo {
        final String pkg, label;
        PackInfo(String pkg, String label) { this.pkg = pkg; this.label = label; }
    }

    private static final String[] PACK_ACTIONS = {
            "org.adw.launcher.THEMES", "com.novalauncher.THEME", "com.anddoes.launcher.THEME",
            "com.fede.launcher.THEME_ICONPACK", "com.gau.go.launcherex.theme", "com.dlto.atom.launcher.THEME"};

    static List<PackInfo> installed(Context c) {
        PackageManager pm = c.getPackageManager();
        LinkedHashMap<String, PackInfo> out = new LinkedHashMap<>();
        for (String act : PACK_ACTIONS) {
            try {
                for (ResolveInfo ri : pm.queryIntentActivities(new Intent(act), 0)) {
                    String pkg = ri.activityInfo.packageName;
                    if (out.containsKey(pkg)) continue;
                    CharSequence l = ri.activityInfo.applicationInfo.loadLabel(pm);
                    out.put(pkg, new PackInfo(pkg, l == null ? pkg : l.toString()));
                }
            } catch (Throwable ignored) { }
        }
        return new ArrayList<>(out.values());
    }

    static String labelOf(Context c, String pack) {
        if (BUILTIN.equals(pack)) return "iOS 27 (bawaan SP)";
        if (NONE.equals(pack)) return "Ikon asli aplikasi";
        try {
            PackageManager pm = c.getPackageManager();
            return pm.getApplicationInfo(pack, 0).loadLabel(pm).toString();
        } catch (Throwable t) { return pack; }
    }

    private static final HashMap<String, External> EXT = new HashMap<>();

    /** Paket eksternal (di-cache); null bila tidak bisa dimuat. */
    static synchronized External external(Context c, String pkg) {
        if (EXT.containsKey(pkg)) return EXT.get(pkg);
        External e = null;
        try { e = new External(c.getApplicationContext(), pkg); } catch (Throwable ignored) { }
        EXT.put(pkg, e);
        return e;
    }

    static synchronized void forget() { EXT.clear(); }

    static final class External {
        private final String pkg;
        private final Resources res;
        private final HashMap<String, String> map = new HashMap<>();
        private final ArrayList<String> back = new ArrayList<>();
        private String mask, upon;
        private float scale = 1f;

        External(Context c, String pkg) throws Exception {
            this.pkg = pkg;
            res = c.getPackageManager().getResourcesForApplication(pkg);
            XmlPullParser x;
            int id = res.getIdentifier("appfilter", "xml", pkg);
            InputStream in = null;
            if (id != 0) {
                x = res.getXml(id);
            } else {
                in = res.getAssets().open("appfilter.xml");
                x = Xml.newPullParser();
                x.setInput(in, "UTF-8");
            }
            try {
                int ev = x.getEventType();
                while (ev != XmlPullParser.END_DOCUMENT) {
                    if (ev == XmlPullParser.START_TAG) readTag(x);
                    ev = x.next();
                }
            } finally {
                if (in != null) try { in.close(); } catch (Throwable ignored) { }
            }
        }

        private void readTag(XmlPullParser x) {
            String t = x.getName();
            if ("item".equals(t)) {
                String comp = x.getAttributeValue(null, "component");
                String dr = x.getAttributeValue(null, "drawable");
                if (comp == null || dr == null) return;
                comp = comp.trim();
                if (comp.startsWith("ComponentInfo{") && comp.endsWith("}")) comp = comp.substring(14, comp.length() - 1);
                int sl = comp.indexOf('/');
                if (sl <= 0) return;
                String cp = comp.substring(0, sl), cl = comp.substring(sl + 1);
                if (cl.startsWith(".")) cl = cp + cl;
                if (!map.containsKey(cp + "/" + cl)) map.put(cp + "/" + cl, dr);
            } else if ("iconback".equals(t)) {
                for (int i = 0; i < x.getAttributeCount(); i++) back.add(x.getAttributeValue(i));
            } else if ("iconmask".equals(t)) {
                if (x.getAttributeCount() > 0) mask = x.getAttributeValue(0);
            } else if ("iconupon".equals(t)) {
                if (x.getAttributeCount() > 0) upon = x.getAttributeValue(0);
            } else if ("scale".equals(t)) {
                try { scale = Float.parseFloat(x.getAttributeValue(null, "factor")); } catch (Throwable ignored) { }
            }
        }

        private Drawable drawable(String name) {
            if (name == null) return null;
            try {
                int id = res.getIdentifier(name, "drawable", pkg);
                if (id == 0) id = res.getIdentifier(name, "mipmap", pkg);
                if (id == 0) return null;
                return res.getDrawable(id, null);
            } catch (Throwable t) { return null; }
        }

        /** Ikon pack untuk komponen aplikasi; null bila tidak ada pemetaan. */
        Drawable iconFor(AppRepo.AppInfo a) { return drawable(map.get(a.key)); }

        boolean hasTemplate() { return !back.isEmpty() || mask != null || upon != null; }

        /** Bungkus ikon aplikasi yang tidak ada di pack dengan iconback / iconmask / iconupon milik pack. */
        Bitmap themed(Drawable appIcon, int size) {
            if (!hasTemplate() || appIcon == null) return null;
            Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(out);
            Drawable bk = back.isEmpty() ? null : drawable(back.get(0));
            if (bk != null) { bk.setBounds(0, 0, size, size); bk.draw(c); }
            Bitmap ic = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas ic2 = new Canvas(ic);
            float s = scale > 0.2f && scale <= 1.5f ? scale : 1f;
            int inset = Math.round(size * (1f - s) / 2f);
            appIcon.setBounds(inset, inset, size - inset, size - inset);
            appIcon.draw(ic2);
            Drawable mk = drawable(mask);
            if (mk != null) {
                Bitmap mb = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
                mk.setBounds(0, 0, size, size);
                mk.draw(new Canvas(mb));
                Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
                ic2.drawBitmap(mb, 0, 0, p);
                mb.recycle();
            }
            c.drawBitmap(ic, 0, 0, null);
            ic.recycle();
            Drawable up = drawable(upon);
            if (up != null) { up.setBounds(0, 0, size, size); up.draw(c); }
            return out;
        }
    }
}
