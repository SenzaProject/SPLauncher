package com.senzaproject.splauncher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ImageDecoder;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;

/** Wallpaper bawaan (6 gambar Liquid Glass di res/drawable-nodpi/wp_*.jpg) dan wallpaper kustom dari galeri. */
final class Wallpapers {
    private Wallpapers() {}

    static final int PRESET_COUNT = 6;
    static final String[] NAMES = {"Samudra Kaca", "Senja Pastel", "Laut Senja", "Zamrud", "Malam Ungu", "Fajar"};
    private static final int[] RES = {R.drawable.wp_1, R.drawable.wp_2, R.drawable.wp_3,
            R.drawable.wp_4, R.drawable.wp_5, R.drawable.wp_6};

    static File customFile(Context c) { return new File(c.getFilesDir(), "wallpaper.jpg"); }

    /** Nama wallpaper yang sedang dipakai (untuk ditampilkan di Pengaturan). */
    static String nameOf(int id) {
        return id >= 0 && id < NAMES.length ? NAMES[id] : "Kustom";
    }

    /** Bitmap wallpaper seukuran layar w x h sesuai preferensi. */
    static Bitmap render(Context c, Prefs p, int w, int h) {
        int id = p.wallpaper();
        if (id < 0) {
            Bitmap b = custom(c, w, h);
            if (b != null) return b;
            id = 0;
        }
        return preset(c, id, w, h);
    }

    /** Wallpaper bawaan nomor id, di-crop tengah agar memenuhi w x h. */
    static Bitmap preset(Context c, int id, int w, int h) {
        id = U.clamp(id, 0, RES.length - 1);
        Bitmap src = null;
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inScaled = false;
            src = BitmapFactory.decodeResource(c.getResources(), RES[id], o);
        } catch (Throwable ignored) { }
        if (src == null) {
            Bitmap bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            bm.eraseColor(0xFF1E6FD9);
            return bm;
        }
        Bitmap out = coverCrop(src, w, h);
        if (out != src) src.recycle();
        return out;
    }

    private static Bitmap coverCrop(Bitmap src, int w, int h) {
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        float s = Math.max(w / (float) src.getWidth(), h / (float) src.getHeight());
        Matrix m = new Matrix();
        m.postScale(s, s);
        m.postTranslate((w - src.getWidth() * s) / 2f, (h - src.getHeight() * s) / 2f);
        Canvas cv = new Canvas(out);
        cv.drawBitmap(src, m, new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG));
        return out;
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
            Bitmap out = coverCrop(src, w, h);
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
