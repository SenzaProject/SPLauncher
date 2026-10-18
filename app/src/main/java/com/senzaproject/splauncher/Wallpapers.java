package com.senzaproject.splauncher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ImageDecoder;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;

/** Wallpaper bawaan (gradien) dan wallpaper kustom dari galeri. */
final class Wallpapers {
    private Wallpapers() {}

    static final int PRESET_COUNT = 6;
    static final String[] NAMES = {"Samudra", "Senja", "Hutan", "Lavender", "Grafit", "Pagi"};
    // [warna awal, tengah, akhir, aksen blob]
    private static final int[][] PAL = {
            {0xFF0B1F4B, 0xFF1E6FD9, 0xFF7CC8FF, 0xFF5AE0FF},
            {0xFF2B1055, 0xFFD4418E, 0xFFFFB36B, 0xFFFF7AA8},
            {0xFF0B3B2E, 0xFF1B8A5A, 0xFFA7E8A0, 0xFF5CE6B8},
            {0xFF3A1C71, 0xFF6A5ACD, 0xFFD7B8FF, 0xFFB39BFF},
            {0xFF101014, 0xFF2B2B33, 0xFF5A5A68, 0xFF7A7AA0},
            {0xFFFFB6A3, 0xFFFFD9A0, 0xFF9AD7FF, 0xFFFFF0C2},
    };

    static File customFile(Context c) { return new File(c.getFilesDir(), "wallpaper.jpg"); }

    /** Bitmap wallpaper seukuran layar w x h sesuai preferensi. */
    static Bitmap render(Context c, Prefs p, int w, int h) {
        int id = p.wallpaper();
        if (id < 0) {
            Bitmap b = custom(c, w, h);
            if (b != null) return b;
            id = 0;
        }
        return preset(id, w, h);
    }

    static Bitmap preset(int id, int w, int h) {
        int[] pal = PAL[U.clamp(id, 0, PAL.length - 1)];
        Bitmap bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bm);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, w * 0.55f, h, new int[]{pal[0], pal[1], pal[2]}, new float[]{0f, 0.55f, 1f},
                Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, w, h, p);
        float[][] blobs = {{0.15f, 0.22f, 0.85f}, {0.9f, 0.5f, 0.7f}, {0.25f, 0.88f, 0.9f}, {0.8f, 0.05f, 0.5f}};
        int[] cols = {pal[3], pal[2], pal[1], pal[3]};
        for (int i = 0; i < blobs.length; i++) {
            float cx = blobs[i][0] * w, cy = blobs[i][1] * h, r = blobs[i][2] * w;
            p.setShader(new RadialGradient(cx, cy, r, U.alpha(cols[i], 0.55f), U.alpha(cols[i], 0f), Shader.TileMode.CLAMP));
            cv.drawCircle(cx, cy, r, p);
        }
        return bm;
    }

    /** Muat wallpaper kustom (crop tengah) atau null jika tidak ada. */
    static Bitmap custom(Context c, int w, int h) {
        File f = customFile(c);
        if (!f.exists()) return null;
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getPath(), o);
            if (o.outWidth <= 0 || o.outHeight <= 0) return null;
            int sample = 1;
            while (o.outWidth / (sample * 2) >= w && o.outHeight / (sample * 2) >= h) sample *= 2;
            o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            Bitmap src = BitmapFactory.decodeFile(f.getPath(), o);
            if (src == null) return null;
            Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            float s = Math.max(w / (float) src.getWidth(), h / (float) src.getHeight());
            Matrix m = new Matrix();
            m.postScale(s, s);
            m.postTranslate((w - src.getWidth() * s) / 2f, (h - src.getHeight() * s) / 2f);
            Canvas cv = new Canvas(out);
            cv.drawBitmap(src, m, new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG));
            src.recycle();
            return out;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Salin gambar pilihan pengguna ke penyimpanan internal (maks 2560px). */
    static boolean saveCustom(Context c, Uri uri) {
        try {
            ImageDecoder.Source src = ImageDecoder.createSource(c.getContentResolver(), uri);
            Bitmap bm = ImageDecoder.decodeBitmap(src, (decoder, info, s) -> {
                int mx = Math.max(info.getSize().getWidth(), info.getSize().getHeight());
                int sample = 1;
                while (mx / (sample * 2) >= 2560) sample *= 2;
                decoder.setTargetSampleSize(sample);
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
            });
            try (FileOutputStream fo = new FileOutputStream(customFile(c))) {
                bm.compress(Bitmap.CompressFormat.JPEG, 92, fo);
            }
            bm.recycle();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
