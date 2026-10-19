package com.senzaproject.splauncher;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewParent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Efek Liquid Glass: backdrop wallpaper yang diburamkan + tint + sorot tepi + sheen.
 * Panel mengambil potongan wallpaper-blur sesuai posisinya di jendela sehingga terlihat
 * seperti kaca buram sungguhan di atas wallpaper.
 *
 * Perbaikan v1.1: posisi view untuk backdrop dihitung dari posisi LAYOUT (tanpa skala/rotasi/translasi
 * animasi). Sebelumnya posisi diambil saat view masih diskalakan 0.3x (animasi buka folder), lalu
 * display list tidak digambar ulang setelah animasi selesai, sehingga bitmap backdrop hanya menutup
 * sebagian lebar panel (panel tampak setengah berwarna). Backdrop juga kini dipakai lewat BitmapShader
 * mode CLAMP sehingga tepi panel selalu tertutup walau posisi bergeser beberapa piksel.
 */
final class Glass {
    private Glass() {}

    static Bitmap blurred;
    static int winW = 1, winH = 1;
    static boolean reduce;

    private static final Set<View> HOSTS = Collections.newSetFromMap(new WeakHashMap<View, Boolean>());
    private static final Paint BACK = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private static final Paint FILL = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint STROKE = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Path PATH = new Path();
    private static final RectF RF = new RectF();
    private static final Matrix MAT = new Matrix();
    private static final int[] LOC = new int[2];

    static {
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(1.35f);
        BACK.setColorFilter(new ColorMatrixColorFilter(cm));
        STROKE.setStyle(Paint.Style.STROKE);
    }

    static void watch(View v) { HOSTS.add(v); }

    /** Dipanggil saat posisi panel di jendela berubah (mis. saat halaman digeser). */
    static void invalidateAll() {
        for (View v : new ArrayList<>(HOSTS)) {
            if (v.isAttachedToWindow()) v.invalidate();
        }
    }

    /**
     * Posisi pojok kiri-atas view di jendela, dihitung dari left/top layout dikurangi scroll induk.
     * Sengaja mengabaikan transformasi (skala/translasi/rotasi) agar hasilnya stabil selama animasi.
     */
    static void locate(View v, int[] out) {
        float x = 0f, y = 0f;
        View cur = v;
        for (int guard = 0; guard < 64 && cur != null; guard++) {
            x += cur.getLeft();
            y += cur.getTop();
            ViewParent p = cur.getParent();
            if (!(p instanceof View)) break;
            View pv = (View) p;
            x -= pv.getScrollX();
            y -= pv.getScrollY();
            cur = pv;
        }
        out[0] = Math.round(x);
        out[1] = Math.round(y);
    }

    static void setBackdrop(Bitmap full, int blurPct, boolean reduceTransparency) {
        reduce = reduceTransparency;
        winW = full.getWidth();
        winH = full.getHeight();
        int sw = Math.max(32, winW / 12);
        int sh = Math.max(32, Math.round(sw * (winH / (float) winW)));
        Bitmap small = Bitmap.createScaledBitmap(full, sw, sh, true);
        int[] px = new int[sw * sh];
        small.getPixels(px, 0, sw, 0, 0, sw, sh);
        int r = Math.round(1 + blurPct / 100f * 7f);
        Blur.blur(px, sw, sh, r);
        Bitmap out = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888);
        out.setPixels(px, 0, sw, 0, 0, sw, sh);
        if (small != full) small.recycle();
        blurred = out;
        invalidateAll();
    }

    /** Gambar kaca pada area (l,t,r,b) koordinat lokal host. */
    static void paint(Canvas c, View host, float l, float t, float r, float b, float radius, float tint, boolean dark) {
        paint(c, host, l, t, r, b, radius, tint, dark, 0, 0);
    }

    /**
     * @param offX tambahan geser backdrop (piksel jendela); dipakai untuk "proxy" seret yang
     *             dipindah dengan translasi sehingga posisi layout-nya bukan posisi tampilnya.
     */
    static void paint(Canvas c, View host, float l, float t, float r, float b, float radius, float tint, boolean dark,
                      int offX, int offY) {
        float w = r - l, h = b - t;
        if (w <= 0 || h <= 0) return;
        float density = host.getResources().getDisplayMetrics().density;
        float rad = Math.min(radius, Math.min(w, h) / 2f);
        RF.set(l, t, r, b);
        PATH.reset();
        PATH.addRoundRect(RF, rad, rad, Path.Direction.CW);
        int save = c.save();
        c.clipPath(PATH);
        Bitmap bd = blurred;
        boolean solid = reduce || bd == null;
        if (!solid) {
            locate(host, LOC);
            // bitmap kecil -> ruang jendela (skala), lalu ruang jendela -> ruang lokal host (geser)
            MAT.setScale(winW / (float) bd.getWidth(), winH / (float) bd.getHeight());
            MAT.postTranslate(-(LOC[0] + offX), -(LOC[1] + offY));
            BitmapShader sh = new BitmapShader(bd, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            sh.setFilterMode(BitmapShader.FILTER_MODE_LINEAR);
            sh.setLocalMatrix(MAT);
            BACK.setShader(sh);
            c.drawRect(RF, BACK);
            BACK.setShader(null);
            FILL.setShader(null);
            FILL.setColor(U.alpha(dark ? 0xFF000000 : 0xFFFFFFFF, tint));
            c.drawRect(RF, FILL);
            // sheen atas
            FILL.setShader(new LinearGradient(0, t, 0, t + h * 0.55f,
                    U.alpha(0xFFFFFFFF, dark ? 0.14f : 0.30f), 0x00FFFFFF, Shader.TileMode.CLAMP));
            c.drawRect(RF, FILL);
            FILL.setShader(null);
        } else {
            FILL.setShader(null);
            FILL.setColor(dark ? 0xF52C2C2E : 0xF5F2F2F7);
            c.drawRect(RF, FILL);
        }
        c.restoreToCount(save);
        // sorot tepi (specular rim)
        float sw = Math.max(1f, density * 1.0f);
        RF.inset(sw / 2f, sw / 2f);
        STROKE.setStrokeWidth(sw);
        STROKE.setShader(new LinearGradient(l, t, r, b,
                new int[]{U.alpha(0xFFFFFFFF, solid ? 0.35f : 0.80f), U.alpha(0xFFFFFFFF, 0.10f),
                        U.alpha(0xFFFFFFFF, 0.10f), U.alpha(0xFFFFFFFF, solid ? 0.25f : 0.50f)},
                new float[]{0f, 0.38f, 0.62f, 1f}, Shader.TileMode.CLAMP));
        c.drawRoundRect(RF, Math.max(0f, rad - sw / 2f), Math.max(0f, rad - sw / 2f), STROKE);
        STROKE.setShader(null);
    }

    /** Drawable latar kaca untuk view. */
    static final class GlassDrawable extends Drawable {
        private final View host;
        private float radius;
        private float tint;
        private boolean dark;

        GlassDrawable(View host, float radiusPx, float tint, boolean dark) {
            this.host = host;
            this.radius = radiusPx;
            this.tint = tint;
            this.dark = dark;
            watch(host);
        }

        void set(float radiusPx, float tint, boolean dark) {
            this.radius = radiusPx; this.tint = tint; this.dark = dark; invalidateSelf();
        }

        @Override public void draw(Canvas canvas) {
            Rect b = getBounds();
            paint(canvas, host, b.left, b.top, b.right, b.bottom, radius, tint, dark);
        }
        @Override public void setAlpha(int alpha) {}
        @Override public void setColorFilter(ColorFilter colorFilter) {}
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
