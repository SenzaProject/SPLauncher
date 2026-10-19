package com.senzaproject.splauncher;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;
import android.animation.ValueAnimator;

import java.util.List;

/** Ikon aplikasi / folder dengan label, gaya squircle iOS, badge hapus pada mode edit. */
final class IconView extends View implements ItemHolder {
    LayoutStore.Item item;
    AppRepo.AppInfo app;
    float lastDownX, lastDownY;

    private Bitmap icon;
    private final Bitmap[] mini = new Bitmap[9];
    private String label = "";
    private String shownLabel = "";
    private boolean showLabel = true;
    private boolean folder;
    private int iconSize;
    private boolean dark;
    private boolean edit;
    private boolean merge;
    private boolean reduceMotion;
    private ObjectAnimator wiggle;
    private final TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint badgeTp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF rf = new RectF();
    private final float gap, badgeR;
    private Prefs prefs;
    private int bindToken;
    private float labelSize, lineH;
    private int glassOffX, glassOffY;
    private int notifCount;

    IconView(Context c) {
        super(c);
        gap = U.dpf(c, 5);
        badgeR = U.dpf(c, 10);
        tp.setColor(0xFFFFFFFF);
        tp.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        tp.setTextAlign(Paint.Align.CENTER);
        tp.setShadowLayer(U.dpf(c, 2.5f), 0, U.dpf(c, 1f), 0x99000000);
        badgeTp.setColor(0xFFFFFFFF);
        badgeTp.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        badgeTp.setTextAlign(Paint.Align.CENTER);
        setClickable(true);
        setLayerType(LAYER_TYPE_NONE, null);
    }

    @Override public LayoutStore.Item getItem() { return item; }

    int iconSize() { return iconSize; }

    private void common(Prefs p, int size, boolean labelOn, boolean isDark) {
        prefs = p;
        iconSize = size;
        showLabel = labelOn;
        dark = isDark;
        reduceMotion = p.reduceMotion();
        labelSize = U.sp(getContext(), 11f) * U.clampf(p.iconScale() / 100f, 0.9f, 1.15f);
        tp.setTextSize(labelSize);
        lineH = tp.getFontSpacing();
        bindToken++;
        if (getWidth() > 0) fitLabel();
    }

    /** Offset backdrop kaca (dipakai proxy seret yang digeser dengan translasi). */
    void setGlassOffset(int x, int y) { glassOffX = x; glassOffY = y; invalidate(); }

    /** Jumlah notifikasi aktif untuk badge merah (0 = tanpa badge). */
    void setBadge(int n) {
        if (n == notifCount) return;
        notifCount = n;
        invalidate();
    }

    void bindApp(AppRepo.AppInfo a, Prefs p, int size, boolean labelOn, boolean isDark) {
        folder = false;
        app = a;
        item = LayoutStore.Item.app(a.key);
        label = a.label;
        common(p, size, labelOn, isDark);
        icon = IconFactory.peek(a, size, p, isDark);
        final int token = bindToken;
        if (icon == null) {
            IconFactory.load(a, size, p, isDark, new IconFactory.Callback() {
                @Override public void onIcon(Bitmap b) { if (token == bindToken) { icon = b; invalidate(); } }
            });
        }
        setContentDescription(label);
        requestLayout();
        invalidate();
    }

    /** Ikon aplikasi untuk item yang sudah ada di model (menjaga referensi item). */
    void bindItemApp(LayoutStore.Item it, AppRepo.AppInfo a, Prefs p, int size, boolean labelOn, boolean isDark) {
        bindApp(a, p, size, labelOn, isDark);
        item = it;
    }

    void bindFolder(LayoutStore.Item f, List<AppRepo.AppInfo> kids, Prefs p, int size, boolean labelOn, boolean isDark) {
        folder = true;
        app = null;
        item = f;
        label = f.title;
        common(p, size, labelOn, isDark);
        Glass.watch(this);
        int ms = Math.max(8, Math.round(size * 0.22f));
        final int token = bindToken;
        for (int i = 0; i < mini.length; i++) {
            mini[i] = null;
            if (i < kids.size()) {
                final int idx = i;
                AppRepo.AppInfo k = kids.get(i);
                mini[i] = IconFactory.peek(k, ms, p, isDark);
                if (mini[i] == null) {
                    IconFactory.load(k, ms, p, isDark, new IconFactory.Callback() {
                        @Override public void onIcon(Bitmap b) {
                            if (token == bindToken) { mini[idx] = b; invalidate(); }
                        }
                    });
                }
            }
        }
        setContentDescription("Folder " + label);
        requestLayout();
        invalidate();
    }

    boolean isFolder() { return folder; }

    /** Warna label (mis. gelap di panel terang). */
    void setLabelColor(int color, boolean shadow) {
        tp.setColor(color);
        if (shadow) tp.setShadowLayer(U.dpf(getContext(), 2.5f), 0, U.dpf(getContext(), 1f), 0x99000000);
        else tp.clearShadowLayer();
        invalidate();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (edit) startWiggle();
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (wiggle != null) wiggle.cancel();
    }

    @Override protected void onMeasure(int wSpec, int hSpec) {
        int dw = iconSize + Math.round(U.dpf(getContext(), 16));
        int dh = iconSize + Math.round(U.dpf(getContext(), 8))
                + (showLabel ? Math.round(gap + lineH) : 0);
        setMeasuredDimension(resolveSize(dw, wSpec), resolveSize(dh, hSpec));
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        fitLabel();
    }

    /** Muat label ke lebar view: perkecil font sampai 84% dulu, baru dipotong dengan "…". */
    private void fitLabel() {
        float avail = Math.max(10, getWidth() - U.dpf(getContext(), 2));
        tp.setTextSize(labelSize);
        float tw = tp.measureText(label);
        if (tw > avail && tw > 0f) tp.setTextSize(labelSize * Math.max(0.84f, avail / tw));
        shownLabel = TextUtils.ellipsize(label, tp, avail, TextUtils.TruncateAt.END).toString();
    }

    private float iconTop() {
        float contentH = iconSize + (showLabel ? gap + lineH : 0);
        return (getHeight() - contentH) / 2f;
    }

    @Override protected void onDraw(Canvas c) {
        float left = (getWidth() - iconSize) / 2f;
        float top = iconTop();
        if (merge) {
            paint.setColor(0x40FFFFFF);
            float e = U.dpf(getContext(), 5);
            rf.set(left - e, top - e, left + iconSize + e, top + iconSize + e);
            c.drawRoundRect(rf, iconSize * 0.3f, iconSize * 0.3f, paint);
        }
        if (folder) {
            Glass.paint(c, this, left, top, left + iconSize, top + iconSize, iconSize * 0.27f,
                    prefs != null ? Math.max(0.22f, prefs.glassOpacity() / 100f * 0.8f) : 0.28f, false,
                    glassOffX, glassOffY);
            float pad = iconSize * 0.12f;
            float ms = iconSize * 0.22f;
            float gp = (iconSize - 2 * pad - 3 * ms) / 2f;
            for (int i = 0; i < 9; i++) {
                Bitmap b = mini[i];
                if (b == null) continue;
                float x = left + pad + (i % 3) * (ms + gp);
                float y = top + pad + (i / 3) * (ms + gp);
                rf.set(x, y, x + ms, y + ms);
                c.drawBitmap(b, null, rf, paint);
            }
        } else if (icon != null) {
            c.drawBitmap(icon, left, top, paint);
        } else {
            paint.setColor(0x33FFFFFF);
            rf.set(left, top, left + iconSize, top + iconSize);
            c.drawRoundRect(rf, iconSize * 0.24f, iconSize * 0.24f, paint);
        }
        if (showLabel) {
            float y = top + iconSize + gap - tp.ascent();
            c.drawText(shownLabel, getWidth() / 2f, y, tp);
        }
        if (notifCount > 0 && !edit) drawBadge(c, left + iconSize, top);
        if (edit && !merge) {
            float bx = left + U.dpf(getContext(), 2), by = top + U.dpf(getContext(), 2);
            paint.setColor(0xE6808086);
            c.drawCircle(bx, by, badgeR, paint);
            paint.setColor(0xFFFFFFFF);
            paint.setStrokeWidth(U.dpf(getContext(), 2));
            paint.setStrokeCap(Paint.Cap.ROUND);
            c.drawLine(bx - badgeR * 0.45f, by, bx + badgeR * 0.45f, by, paint);
        }
    }

    /** Badge notifikasi merah gaya iOS di pojok kanan-atas ikon. */
    private void drawBadge(Canvas c, float right, float top) {
        String t = notifCount > 99 ? "99+" : String.valueOf(notifCount);
        float h = U.dpf(getContext(), 18);
        float pad = U.dpf(getContext(), 5.5f);
        badgeTp.setTextSize(U.sp(getContext(), 11f));
        float w = Math.max(h, badgeTp.measureText(t) + 2 * pad);
        float cx = right - h * 0.18f, cy = top + h * 0.18f;
        rf.set(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f);
        paint.setColor(0xFFFF3B30);
        c.drawRoundRect(rf, h / 2f, h / 2f, paint);
        c.drawText(t, cx, cy - (badgeTp.ascent() + badgeTp.descent()) / 2f, badgeTp);
    }

    boolean badgeHit(float x, float y) {
        if (!edit) return false;
        float left = (getWidth() - iconSize) / 2f;
        float top = iconTop();
        float bx = left + U.dpf(getContext(), 2), by = top + U.dpf(getContext(), 2);
        float r = U.dpf(getContext(), 18);
        float dx = x - bx, dy = y - by;
        return dx * dx + dy * dy <= r * r;
    }

    /** Pusat ikon (bukan label) dalam koordinat lokal view. */
    float iconCenterX() { return getWidth() / 2f; }
    float iconCenterY() { return iconTop() + iconSize / 2f; }

    @Override public boolean onTouchEvent(android.view.MotionEvent e) {
        if (e.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
            lastDownX = e.getX();
            lastDownY = e.getY();
        }
        return super.onTouchEvent(e);
    }

    @Override public void setPressed(boolean pressed) {
        boolean was = isPressed();
        super.setPressed(pressed);
        if (was != pressed && !edit) {
            animate().cancel();
            animate().scaleX(pressed ? 0.9f : 1f).scaleY(pressed ? 0.9f : 1f).setDuration(pressed ? 90 : 160).start();
        }
    }

    void setEdit(boolean on) {
        if (edit == on) return;
        edit = on;
        if (on) startWiggle();
        else {
            if (wiggle != null) wiggle.cancel();
            setRotation(0f);
        }
        invalidate();
    }

    private void startWiggle() {
        if (reduceMotion) return;
        if (wiggle != null) wiggle.cancel();
        wiggle = ObjectAnimator.ofFloat(this, View.ROTATION, -1.8f, 1.8f);
        wiggle.setDuration(110 + (long) (Math.random() * 70));
        wiggle.setRepeatCount(ValueAnimator.INFINITE);
        wiggle.setRepeatMode(ValueAnimator.REVERSE);
        wiggle.setStartDelay((long) (Math.random() * 90));
        wiggle.start();
    }

    void setMerge(boolean on) {
        if (merge == on) return;
        merge = on;
        animate().scaleX(on ? 1.12f : 1f).scaleY(on ? 1.12f : 1f).setDuration(160).start();
        invalidate();
    }
}
