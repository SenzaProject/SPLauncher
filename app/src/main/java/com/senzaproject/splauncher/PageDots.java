package com.senzaproject.splauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Indikator halaman (titik) + penanda App Library (kotak kecil). */
final class PageDots extends View {
    private int count = 1;
    private float pos = 0f;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rf = new RectF();
    private final float r, gap;

    PageDots(Context c) {
        super(c);
        r = U.dpf(c, 3.2f);
        gap = U.dpf(c, 9);
    }

    /** count = jumlah halaman termasuk library (halaman terakhir). */
    void set(int count, float pos) {
        this.count = Math.max(1, count);
        this.pos = pos;
        invalidate();
    }

    @Override protected void onMeasure(int w, int h) {
        setMeasuredDimension(resolveSize(Math.round(count * (2 * r + gap)), w), resolveSize(Math.round(r * 4), h));
    }

    @Override protected void onDraw(Canvas c) {
        float total = count * 2 * r + (count - 1) * gap;
        float x = (getWidth() - total) / 2f + r;
        float y = getHeight() / 2f;
        for (int i = 0; i < count; i++) {
            float d = Math.min(1f, Math.abs(pos - i));
            p.setColor(U.alpha(0xFFFFFFFF, 0.38f + 0.62f * (1f - d)));
            if (i == count - 1) {
                rf.set(x - r, y - r, x + r, y + r);
                c.drawRoundRect(rf, r * 0.5f, r * 0.5f, p);
            } else {
                c.drawCircle(x, y, r, p);
            }
            x += 2 * r + gap;
        }
    }
}
