package com.senzaproject.splauncher;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Layar Nol: halaman widget di sebelah kiri Home pertama. Muncul saat Home pertama digeser ke kanan,
 * mengikuti jari, dan ditutup dengan geser ke kiri, tombol Kembali, atau tombol Home.
 * progress 0 = tertutup, 1 = terbuka penuh.
 */
final class ZeroScreen extends FrameLayout {
    interface Host {
        void onZeroClosed(ZeroScreen z);
        boolean reduceMotion();
        /** Bilah pencarian di Layar Nol diketuk. */
        void onZeroSearch();
    }

    private final Host host;
    private final View scrim;
    private final FrameLayout body;
    private final int slop;

    private float progress;
    private ValueAnimator anim;
    private boolean closing;
    private float downX, downY;
    private boolean dragging;
    private VelocityTracker vt;

    ZeroScreen(Context c, Prefs prefs, boolean dark, int insTop, int insBottom, Host host) {
        super(c);
        this.host = host;
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
        setClickable(true);

        // Latar OPAK (wallpaper diburamkan + peredam). Sebelumnya hanya kaca semi-transparan sehingga ikon Home
        // dan Dock di belakangnya ikut terlihat menembus widget.
        final boolean darkTheme = dark;
        final Paint bgPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
        final Rect dst = new Rect();
        scrim = new View(c) {
            @Override protected void onDraw(Canvas cv) {
                Bitmap bg = Glass.blurred;
                if (bg != null && !bg.isRecycled()) {
                    dst.set(0, 0, getWidth(), getHeight());
                    cv.drawBitmap(bg, null, dst, bgPaint);
                } else {
                    cv.drawColor(darkTheme ? 0xFF101014 : 0xFF8E8E93);
                }
                cv.drawColor(darkTheme ? 0x8C000000 : 0x40000000);
            }
        };
        scrim.setWillNotDraw(false);
        addView(scrim, new LayoutParams(-1, -1));

        body = new FrameLayout(c);
        body.setClipChildren(false);
        addView(body, new LayoutParams(-1, -1));

        ScrollView sv = new ScrollView(c);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.setClipChildren(false);
        sv.setClipToPadding(false);
        body.addView(sv, new LayoutParams(-1, -1));

        int side = U.dp(c, 16);
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        col.setPadding(side, insTop + U.dp(c, 14), side, insBottom + U.dp(c, 28));
        sv.addView(col, new ScrollView.LayoutParams(-1, -2));

        int fg = dark ? 0xFFFFFFFF : 0xFF111114;
        TextView title = new TextView(c);
        title.setText("Layar Nol");
        title.setTextColor(fg);
        title.setTextSize(30f);
        title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        title.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        title.setPadding(U.dp(c, 4), 0, 0, U.dp(c, 12));
        col.addView(title, new LinearLayout.LayoutParams(-1, -2));

        int cw = c.getResources().getDisplayMetrics().widthPixels - 2 * side;
        int gap = U.dp(c, 12);

        addWidget(c, col, prefs, dark, BuiltinWidgets.SEARCH, 4, 1, U.dp(c, 56), gap).setOnClickListener(
                new View.OnClickListener() { @Override public void onClick(View v) { ZeroScreen.this.host.onZeroSearch(); } });
        addWidget(c, col, prefs, dark, BuiltinWidgets.CLOCK_DIGITAL, 4, 2, Math.round(cw * 0.5f), gap);
        addWidget(c, col, prefs, dark, BuiltinWidgets.DATE, 4, 2, Math.round(cw * 0.5f), gap);
        addWidget(c, col, prefs, dark, BuiltinWidgets.MONTH, 4, 3, Math.round(cw * 0.8f), gap);
        addWidget(c, col, prefs, dark, BuiltinWidgets.BATTERY, 4, 2, Math.round(cw * 0.5f), 0);

        applyProgress();
    }

    private static View addWidget(Context c, LinearLayout col, Prefs prefs, boolean dark, String kind,
                                  int sw, int sh, int heightPx, int bottomGap) {
        BuiltinWidgets.Base w = BuiltinWidgets.create(c, LayoutStore.Item.widget(kind, sw, sh), prefs, dark);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, heightPx);
        lp.bottomMargin = bottomGap;
        col.addView(w, lp);
        return w;
    }

    float progress() { return progress; }

    void setProgress(float p) {
        progress = U.clampf(p, 0f, 1f);
        applyProgress();
    }

    private void applyProgress() {
        int w = getWidth() > 0 ? getWidth() : getResources().getDisplayMetrics().widthPixels;
        body.setTranslationX(-(1f - progress) * w);
        scrim.setAlpha(progress);
        setVisibility(progress <= 0.001f && closing ? View.INVISIBLE : View.VISIBLE);
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        applyProgress();
    }

    void animateTo(final float target) {
        if (anim != null) anim.cancel();
        closing = target <= 0f;
        if (host.reduceMotion()) {
            setProgress(target);
            if (closing) host.onZeroClosed(this);
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
                if (target <= 0f) host.onZeroClosed(ZeroScreen.this);
            }
        });
        anim.start();
    }

    void close() { animateTo(0f); }

    /** Dipanggil saat panel dihapus dari layar. */
    void release() {
        if (anim != null) anim.cancel();
    }

    // ------------------------------------------------------------------ geser ke kiri untuk menutup

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
                float dx = e.getX() - downX, dy = e.getY() - downY;
                if (!dragging && dx < -slop * 1.5f && Math.abs(dx) > Math.abs(dy) * 1.3f) {
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
        if (vt == null) vt = VelocityTracker.obtain();
        vt.addMovement(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX(); downY = e.getY();
                return true;
            case MotionEvent.ACTION_MOVE: {
                float dx = e.getX() - downX, dy = e.getY() - downY;
                if (dragging) {
                    int w = Math.max(1, getWidth());
                    setProgress(1f + dx / (w * 0.9f));
                } else if (dx < -slop * 1.5f && Math.abs(dx) > Math.abs(dy) * 1.3f) {
                    dragging = true;
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                vt.computeCurrentVelocity(1000);
                float vx = vt.getXVelocity();
                vt.recycle(); vt = null;
                if (dragging) {
                    dragging = false;
                    boolean shut = progress < 0.70f || vx < -900f;
                    if (e.getActionMasked() == MotionEvent.ACTION_CANCEL) shut = progress < 0.5f;
                    animateTo(shut ? 0f : 1f);
                }
                return true;
            }
        }
        return true;
    }
}
