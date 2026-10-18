package com.senzaproject.splauncher;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/** Ikon kecil yang digambar lewat kode. */
final class Glyphs {
    private Glyphs() {}

    static Drawable search(final int color, final float strokePx) {
        return new Drawable() {
            private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override public void draw(Canvas c) {
                Rect b = getBounds();
                float s = Math.min(b.width(), b.height());
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(strokePx);
                p.setStrokeCap(Paint.Cap.ROUND);
                p.setColor(color);
                float cx = b.left + s * 0.43f, cy = b.top + s * 0.43f, r = s * 0.28f;
                c.drawCircle(cx, cy, r, p);
                c.drawLine(cx + r * 0.72f, cy + r * 0.72f, b.left + s * 0.88f, b.top + s * 0.88f, p);
            }
            @Override public void setAlpha(int a) {}
            @Override public void setColorFilter(ColorFilter f) {}
            @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
        };
    }
}
