package com.senzaproject.splauncher;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

/**
 * Pembungkus widget di halaman Home (bawaan maupun AppWidgetHostView). Menangani tekan-lama (menu / drag),
 * mode edit (isi widget tidak interaktif, badge hapus, bergoyang), dan snapshot untuk proxy seret.
 */
final class WidgetCell extends FrameLayout implements ItemHolder {
    interface Host {
        void onWidgetClick(WidgetCell c);
        void onWidgetLongClick(WidgetCell c);
        boolean isEditMode();
        boolean reduceMotion();
    }

    final LayoutStore.Item item;
    final View content;
    private final Host host;
    private final int insetX, insetY;
    private final int slop;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float badgeR;
    private boolean edit, fired, moved;
    private float downX, downY;
    float lastDownX, lastDownY;
    private ObjectAnimator wiggle;
    private final Runnable longRun = new Runnable() {
        @Override public void run() {
            if (moved) return;
            fired = true;
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            host.onWidgetLongClick(WidgetCell.this);
        }
    };

    WidgetCell(Context c, LayoutStore.Item item, View content, Host host, int insetX, int insetY) {
        super(c);
        this.item = item;
        this.content = content;
        this.host = host;
        this.insetX = insetX;
        this.insetY = insetY;
        this.slop = ViewConfiguration.get(c).getScaledTouchSlop();
        this.badgeR = U.dpf(c, 10);
        setClipChildren(false);
        setClipToPadding(false);
        setPadding(insetX, insetY, insetX, insetY);
        addView(content, new LayoutParams(-1, -1));
        setWillNotDraw(false);
        setContentDescription(item.isExternal() ? "Widget" : BuiltinWidgets.titleOf(item.wkind));
    }

    @Override public LayoutStore.Item getItem() { return item; }

    boolean isEdit() { return edit; }

    void setEdit(boolean on) {
        if (edit == on) return;
        edit = on;
        if (on) startWiggle();
        else { if (wiggle != null) wiggle.cancel(); setRotation(0f); }
        invalidate();
    }

    private void startWiggle() {
        if (host.reduceMotion()) return;
        if (wiggle != null) wiggle.cancel();
        wiggle = ObjectAnimator.ofFloat(this, View.ROTATION, -0.7f, 0.7f);
        wiggle.setDuration(130 + (long) (Math.random() * 70));
        wiggle.setRepeatCount(ValueAnimator.INFINITE);
        wiggle.setRepeatMode(ValueAnimator.REVERSE);
        wiggle.setStartDelay((long) (Math.random() * 90));
        wiggle.start();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (edit) startWiggle();
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (wiggle != null) wiggle.cancel();
        removeCallbacks(longRun);
    }

    // ------------------------------------------------------------------ sentuhan

    @Override public boolean onInterceptTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                fired = false;
                moved = false;
                downX = e.getX();
                downY = e.getY();
                lastDownX = downX;
                lastDownY = downY;
                removeCallbacks(longRun);
                postDelayed(longRun, ViewConfiguration.getLongPressTimeout());
                return host.isEditMode();   // mode edit: isi widget tidak menerima sentuhan
            case MotionEvent.ACTION_MOVE:
                if (fired) return true;
                if (Math.abs(e.getX() - downX) > slop || Math.abs(e.getY() - downY) > slop) {
                    moved = true;
                    removeCallbacks(longRun);
                }
                return false;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(longRun);
                return fired;
        }
        return false;
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX();
                downY = e.getY();
                lastDownX = downX;
                lastDownY = downY;
                if (!edit) animate().cancel();
                if (!edit) animate().scaleX(0.97f).scaleY(0.97f).setDuration(90).start();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (Math.abs(e.getX() - downX) > slop || Math.abs(e.getY() - downY) > slop) {
                    moved = true;
                    removeCallbacks(longRun);
                    if (!edit) animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                }
                return true;
            case MotionEvent.ACTION_UP:
                removeCallbacks(longRun);
                animate().scaleX(1f).scaleY(1f).setDuration(140).start();
                if (!fired && !moved) host.onWidgetClick(this);
                fired = false;
                return true;
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(longRun);
                animate().scaleX(1f).scaleY(1f).setDuration(140).start();
                fired = false;
                return true;
        }
        return true;
    }

    // ------------------------------------------------------------------ badge hapus

    @Override protected void dispatchDraw(Canvas c) {
        super.dispatchDraw(c);
        if (!edit) return;
        float bx = insetX + U.dpf(getContext(), 2), by = insetY + U.dpf(getContext(), 2);
        paint.setColor(0xE6808086);
        c.drawCircle(bx, by, badgeR, paint);
        paint.setColor(0xFFFFFFFF);
        paint.setStrokeWidth(U.dpf(getContext(), 2));
        paint.setStrokeCap(Paint.Cap.ROUND);
        c.drawLine(bx - badgeR * 0.45f, by, bx + badgeR * 0.45f, by, paint);
    }

    boolean badgeHit(float x, float y) {
        if (!edit) return false;
        float bx = insetX + U.dpf(getContext(), 2), by = insetY + U.dpf(getContext(), 2);
        float r = U.dpf(getContext(), 20);
        float dx = x - bx, dy = y - by;
        return dx * dx + dy * dy <= r * r;
    }

    /** Gambar widget (tanpa rotasi goyang) untuk proxy seret. */
    Bitmap snapshot() {
        int w = Math.max(1, getWidth()), h = Math.max(1, getHeight());
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        boolean was = edit;
        edit = false;
        try { draw(c); } catch (Throwable ignored) { }
        edit = was;
        return b;
    }
}
