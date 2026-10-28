package com.senzaproject.splauncher;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Membuat ikon bergaya iOS (squircle) dengan 4 gaya: Normal, Gelap, Kaca, Warna. */
final class IconFactory {
    private IconFactory() {}

    interface Callback { void onIcon(Bitmap b); }

    private static final class BitmapCache extends LruCache<String, Bitmap> {
        BitmapCache(int max) { super(max); }
        @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount(); }
    }

    private static final BitmapCache CACHE = new BitmapCache(48 * 1024 * 1024);
    private static final ExecutorService EXEC = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static volatile int shapeId = 0;
    private static volatile double shapeN = 5.0;

    /** Bentuk ikon: 0 squircle iOS, 1 bulat, 2 kotak membulat. */
    static void setShape(int id) {
        if (id == shapeId) return;
        shapeId = id;
        shapeN = id == 1 ? 2.0 : (id == 2 ? 10.0 : 5.0);
        CACHE.evictAll();
    }

    static String cacheKey(AppRepo.AppInfo a, int size, int style, int tint, boolean dark, String pack) {
        return a.key + "|s" + shapeId + "|" + size + "|" + style + "|" + (style == Prefs.STYLE_TINTED ? tint : 0) + "|"
                + (style == Prefs.STYLE_DARK ? dark : false) + "|" + pack;
    }

    /** Buang seluruh cache ikon (mis. setelah paket ikon / peran aplikasi berubah). */
    static void clear() { CACHE.evictAll(); }

    static Bitmap peek(AppRepo.AppInfo a, int size, Prefs p, boolean dark) {
        return CACHE.get(cacheKey(a, size, p.iconStyle(), p.tint(), dark, p.iconPack()));
    }

    static void load(final AppRepo.AppInfo a, final int size, final Prefs p, final boolean dark, final Callback cb) {
        final int style = p.iconStyle();
        final int tint = p.tint();
        final String pack = p.iconPack();
        final String key = cacheKey(a, size, style, tint, dark, pack);
        Bitmap hit = CACHE.get(key);
        if (hit != null) { cb.onIcon(hit); return; }
        EXEC.execute(new Runnable() {
            @Override public void run() {
                Bitmap b = null;
                try { b = make(a, size, style, tint, dark, pack); } catch (Throwable ignored) { }
                if (b == null) return;
                CACHE.put(key, b);
                final Bitmap fb = b;
                MAIN.post(new Runnable() { @Override public void run() { cb.onIcon(fb); } });
            }
        });
    }

    static Path squircle(float size) {
        Path path = new Path();
        float half = size / 2f;
        final double n = shapeN;
        final int steps = 96;
        for (int i = 0; i <= steps; i++) {
            double t = 2 * Math.PI * i / steps;
            double c = Math.cos(t), s = Math.sin(t);
            float x = (float) (Math.signum(c) * Math.pow(Math.abs(c), 2.0 / n)) * half + half;
            float y = (float) (Math.signum(s) * Math.pow(Math.abs(s), 2.0 / n)) * half + half;
            if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
        }
        path.close();
        return path;
    }

    private static Bitmap make(AppRepo.AppInfo a, int size, int style, int tint, boolean dark, String pack) {
        Drawable d = a.lai.getIcon(0);
        if (d == null) return null;
        Path sq = squircle(size);
        boolean ios = IconPacks.BUILTIN.equals(pack);

        // 1) paket pihak ketiga (appfilter.xml)
        if (!ios && !IconPacks.NONE.equals(pack)) {
            IconPacks.External ext = IconPacks.external(ctx(), pack);
            if (ext != null) {
                Drawable pd = ext.iconFor(a);
                Bitmap base = null;
                if (pd != null) {
                    base = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
                    pd.setBounds(0, 0, size, size);
                    pd.draw(new Canvas(base));
                } else {
                    base = ext.themed(d, size);
                }
                if (base != null) return finish(base, sq, size, style, true, tint, false, false);
            }
        }

        // 2) ikon bawaan iOS 27 untuk aplikasi sistem inti
        if (ios) {
            IconPacks.Role role = IconPacks.roleFor(a);
            if (role != null) {
                Bitmap base = IconPacks.drawRole(role, size);
                boolean sprite = IconPacks.hasSprite(role); // sprite sudah punya sorot kaca sendiri
                return finish(base, sq, size, style, true, tint, !sprite, !sprite);
            }
        }

        // 3) ikon asli aplikasi, dipotong squircle
        Bitmap base = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(base);
        cv.save();
        cv.clipPath(sq);
        if (style == Prefs.STYLE_TINTED && d instanceof AdaptiveIconDrawable
                && ((AdaptiveIconDrawable) d).getMonochrome() != null) {
            // ikon bertema (monochrome): latar warna gelap, simbol warna terang
            int bgc = U.lerpColor(0xFF000000, tint, 0.38f);
            int fgc = U.lerpColor(0xFFFFFFFF, tint, 0.35f);
            cv.drawColor(bgc);
            Drawable mono = ((AdaptiveIconDrawable) d).getMonochrome().mutate();
            mono.setTint(fgc);
            int pad = (int) (size * 0.25f);
            mono.setBounds(new Rect(-pad, -pad, size + pad, size + pad));
            mono.draw(cv);
            cv.restore();
            return finish(base, sq, size, style, false, tint, ios, true);
        }
        if (d instanceof AdaptiveIconDrawable) {
            AdaptiveIconDrawable ad = (AdaptiveIconDrawable) d;
            int pad = (int) (size * 0.25f);
            Rect r = new Rect(-pad, -pad, size + pad, size + pad);
            Drawable bg = ad.getBackground(), fg = ad.getForeground();
            if (bg != null) { bg.setBounds(r); bg.draw(cv); }
            if (fg != null) { fg.setBounds(r); fg.draw(cv); }
        } else {
            cv.drawColor(dark && style == Prefs.STYLE_DARK ? 0xFF3A3A3C : 0xFFF2F2F7);
            int inset = Math.round(size * 0.14f);
            d.setBounds(inset, inset, size - inset, size - inset);
            d.draw(cv);
        }
        cv.restore();
        return finish(base, sq, size, style, true, tint, ios, true);
    }

    private static android.content.Context ctx() { return IconPacks.context(); }

    /** Terapkan gaya (gelap/kaca/warna) + sorot tepi pada ikon dasar. */
    private static Bitmap finish(Bitmap base, Path sq, int size, int style, boolean colorize, int tint,
                                 boolean gloss, boolean rimOn) {
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        float rim = 0.12f;
        switch (style) {
            case Prefs.STYLE_DARK: {
                ColorMatrix cm = new ColorMatrix(new float[]{
                        0.55f, 0, 0, 0, 0,
                        0, 0.55f, 0, 0, 0,
                        0, 0, 0.55f, 0, 0,
                        0, 0, 0, 1, 0});
                p.setColorFilter(new ColorMatrixColorFilter(cm));
                cv.drawBitmap(base, 0, 0, p);
                break;
            }
            case Prefs.STYLE_GLASS: {
                // dasar kaca bening + ikon pudar
                cv.save();
                cv.clipPath(sq);
                Paint g = new Paint(Paint.ANTI_ALIAS_FLAG);
                g.setShader(new LinearGradient(0, 0, size, size, 0x99FFFFFF, 0x4DFFFFFF, Shader.TileMode.CLAMP));
                cv.drawRect(0, 0, size, size, g);
                cv.restore();
                ColorMatrix cm = new ColorMatrix();
                cm.setSaturation(0.9f);
                p.setColorFilter(new ColorMatrixColorFilter(cm));
                p.setAlpha(238);
                cv.drawBitmap(base, 0, 0, p);
                rim = 0.6f;
                break;
            }
            case Prefs.STYLE_TINTED: {
                if (colorize) {
                    ColorMatrix cm = new ColorMatrix();
                    cm.setSaturation(0f);
                    p.setColorFilter(new ColorMatrixColorFilter(cm));
                    cv.drawBitmap(base, 0, 0, p);
                    cv.save();
                    cv.clipPath(sq);
                    // warnai area hasil grayscale dengan tint (multiply-ish)
                    cv.drawColor(U.alpha(tint, 0.72f), android.graphics.PorterDuff.Mode.SRC_ATOP);
                    cv.restore();
                } else {
                    cv.drawBitmap(base, 0, 0, p);
                }
                rim = 0.3f;
                break;
            }
            default:
                cv.drawBitmap(base, 0, 0, p);
        }
        base.recycle();
        if (gloss) {
            // kilau kaca lembut di separuh atas ikon (gaya iOS 27)
            cv.save();
            cv.clipPath(sq);
            Paint gl = new Paint(Paint.ANTI_ALIAS_FLAG);
            gl.setShader(new LinearGradient(0, 0, 0, size * 0.55f, U.alpha(0xFFFFFFFF, 0.24f), 0x00FFFFFF, Shader.TileMode.CLAMP));
            cv.drawRect(0, 0, size, size * 0.55f, gl);
            Paint sh = new Paint(Paint.ANTI_ALIAS_FLAG);
            sh.setShader(new LinearGradient(0, size * 0.72f, 0, size, 0x00000000, U.alpha(0xFF000000, 0.10f), Shader.TileMode.CLAMP));
            cv.drawRect(0, size * 0.72f, size, size, sh);
            cv.restore();
        }
        if (!rimOn) return out;
        // sorot tepi
        Paint s = new Paint(Paint.ANTI_ALIAS_FLAG);
        s.setStyle(Paint.Style.STROKE);
        s.setStrokeWidth(Math.max(1f, size / 56f));
        s.setShader(new LinearGradient(0, 0, size, size,
                new int[]{U.alpha(0xFFFFFFFF, rim * 1.6f), U.alpha(0xFFFFFFFF, 0f), U.alpha(0xFFFFFFFF, rim)},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        cv.save();
        cv.clipPath(sq);
        cv.drawPath(sq, s);
        cv.restore();
        return out;
    }
}
