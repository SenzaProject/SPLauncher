package com.senzaproject.splauncher;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

/**
 * Lembar layar penuh yang turun dari atas (Pusat Notifikasi / Control Center).
 * progress 0 = tertutup, 1 = terbuka penuh. Dapat diikuti jari saat ditarik dari Home,
 * dan ditutup dengan geser ke atas.
 */
abstract class TopSheet extends FrameLayout {
    interface Host {
        void onSheetClosed(TopSheet s);
        boolean reduceMotion();
    }

    protected final Host host;
    /** Latar kaca layar penuh (alpha mengikuti progress). */
    protected final View scrim;
    /** Konten yang meluncur. */
    protected final FrameLayout body;

    private float progress;
    private ValueAnimator anim;
    private boolean closing;
    private final int slop;
    private float downX, downY;
    private boolean dragging;
    private VelocityTracker vt;

    TopSheet(Context c, Host host, float scrimTint) {
        super(c);
        this.host = host;
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
        setClickable(true);
        scrim = new View(c);
        scrim.setBackground(new Glass.GlassDrawable(scrim, 0, scrimTint, true));
        addView(scrim, new LayoutParams(-1, -1));
        body = new FrameLayout(c);
        body.setClipChildren(false);
        addView(body, new LayoutParams(-1, -1));
        applyProgress();
    }

    /** Boleh ditutup dengan geser ke atas yang dimulai pada (x, y)? Subclass dapat membatasi. */
    protected boolean canDragClose(float x, float y) { return true; }

    /** Ketuk area kosong menutup lembar? */
    protected boolean closeOnBackgroundTap() { return true; }

    float progress() { return progress; }
    boolean isClosing() { return closing; }

    void setProgress(float p) {
        progress = U.clampf(p, 0f, 1f);
        applyProgress();
    }

    private void applyProgress() {
        int h = getHeight() > 0 ? getHeight() : getResources().getDisplayMetrics().heightPixels;
        body.setTranslationY(-(1f - progress) * h);
        scrim.setAlpha(progress);
        setVisibility(progress <= 0.001f && closing ? View.INVISIBLE : View.VISIBLE);
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        applyProgress();
    }

    void animateTo(final float target, float velocityPxPerSec) {
        if (anim != null) anim.cancel();
        closing = target <= 0f;
        if (host.reduceMotion()) {
            setProgress(target);
            if (closing) host.onSheetClosed(this); else onOpened();
            return;
        }
        float from = progress;
        long dur = (long) (120 + 230 * Math.abs(target - from));
        anim = ValueAnimator.ofFloat(from, target);
        anim.setDuration(dur);
        anim.setInterpolator(new DecelerateInterpolator(1.6f));
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) { setProgress((Float) a.getAnimatedValue()); }
        });
        anim.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;
            @Override public void onAnimationCancel(Animator a) { cancelled = true; }
            @Override public void onAnimationEnd(Animator a) {
                if (cancelled) return;
                setProgress(target);
                if (target <= 0f) host.onSheetClosed(TopSheet.this); else onOpened();
            }
        });
        anim.start();
    }

    void open() { animateTo(1f, 0f); }
    void close() { animateTo(0f, 0f); }

    /** Dipanggil setelah lembar terbuka penuh. */
    protected void onOpened() { }

    /** Dipanggil saat lembar dihapus dari layar. */
    void release() {
        if (anim != null) anim.cancel();
    }

    // ------------------------------------------------------------------ geser ke atas untuk menutup

    @Override public boolean onInterceptTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX(); downY = e.getY(); dragging = false;
                if (vt != null) vt.recycle();
                vt = VelocityTracker.obtain();
                vt.addMovement(e);
                break;
            case MotionEvent.ACTION_MOVE:
                if (vt != null) vt.addMovement(e);
                float dy = e.getY() - downY, dx = e.getX() - downX;
                if (!dragging && dy < -slop * 1.5f && Math.abs(dy) > Math.abs(dx) * 1.3f && canDragClose(downX, downY)) {
                    dragging = true;
                    if (anim != null) anim.cancel();
                    return true;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (vt != null) { vt.recycle(); vt = null; }
                break;
        }
        return false;
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (vt == null) { vt = VelocityTracker.obtain(); }
        vt.addMovement(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX(); downY = e.getY();
                return true;
            case MotionEvent.ACTION_MOVE: {
                float dy = e.getY() - downY;
                if (dragging) {
                    int h = Math.max(1, getHeight());
                    setProgress(1f + dy / (h * 0.55f));
                } else if (dy < -slop * 1.5f && canDragClose(downX, downY)) {
                    dragging = true;
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                vt.computeCurrentVelocity(1000);
                float vy = vt.getYVelocity();
                vt.recycle(); vt = null;
                if (dragging) {
                    dragging = false;
                    boolean shut = progress < 0.72f || vy < -900f;
                    if (e.getActionMasked() == MotionEvent.ACTION_CANCEL) shut = progress < 0.5f;
                    animateTo(shut ? 0f : 1f, vy);
                } else if (e.getActionMasked() == MotionEvent.ACTION_UP && closeOnBackgroundTap()
                        && Math.abs(e.getY() - downY) < slop && Math.abs(e.getX() - downX) < slop) {
                    close();
                }
                return true;
            }
        }
        return true;
    }
}
