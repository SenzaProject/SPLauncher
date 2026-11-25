package com.senzaproject.splauncher;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
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
        /** Tampilan widget aplikasi lain (AppWidgetHostView) untuk Layar Nol. */
        View createExternal(LayoutStore.Item it);
        /** Tombol "Tambah Widget" di mode ubah. */
        void onZeroAdd();
        /** Widget aplikasi lain dihapus dari Layar Nol: lepaskan appWidgetId-nya. */
        void onZeroRemoveExternal(int wid);
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

    private final ZeroConfig cfg;
    private final Prefs prefs;
    private final boolean dark;
    private final int fg;
    private final LinearLayout col;
    private final ScrollView sv;
    private boolean edit;

    ZeroScreen(Context c, Prefs prefs, boolean dark, int insTop, int insBottom, ZeroConfig cfg, Host host) {
        super(c);
        this.host = host;
        this.cfg = cfg;
        this.prefs = prefs;
        this.dark = dark;
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
        setClickable(true);

        scrim = new View(c);
        scrim.setBackground(new Glass.GlassDrawable(scrim, 0, 0.6f, dark));
        addView(scrim, new LayoutParams(-1, -1));

        body = new FrameLayout(c);
        body.setClipChildren(false);
        addView(body, new LayoutParams(-1, -1));

        sv = new ScrollView(c);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.setClipChildren(false);
        sv.setClipToPadding(false);
        body.addView(sv, new LayoutParams(-1, -1));

        int side = U.dp(c, 16);
        col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        col.setPadding(side, insTop + U.dp(c, 14), side, insBottom + U.dp(c, 28));
        sv.addView(col, new ScrollView.LayoutParams(-1, -2));

        fg = dark ? 0xFFFFFFFF : 0xFF111114;
        rebuild();
        applyProgress();
    }

    // ------------------------------------------------------------------ isi & mode edit

    /** Membangun ulang daftar widget dari ZeroConfig (dipanggil setelah ada perubahan). */
    void rebuild() {
        final int keepY = sv.getScrollY();
        final Context c = getContext();
        col.removeAllViews();

        LinearLayout head = new LinearLayout(c);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(U.dp(c, 4), 0, 0, U.dp(c, 12));
        TextView title = new TextView(c);
        title.setText(cfg.title);
        title.setTextColor(fg);
        title.setTextSize(30f);
        title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        title.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        if (!cfg.showTitle) title.setVisibility(edit ? View.VISIBLE : View.INVISIBLE);
        if (!cfg.showTitle && edit) title.setAlpha(0.35f);
        head.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        head.addView(pill(edit ? "Selesai" : "Ubah", new View.OnClickListener() {
            @Override public void onClick(View v) { edit = !edit; rebuild(); }
        }), new LinearLayout.LayoutParams(-2, U.dp(c, 34)));
        col.addView(head, new LinearLayout.LayoutParams(-1, -2));

        int gap = U.dp(c, 12);
        java.util.ArrayList<ZeroConfig.Entry> items = cfg.items;
        int i = 0;
        while (i < items.size()) {
            ZeroConfig.Entry e = items.get(i);
            LinearLayout.LayoutParams lp;
            if (e.half) {
                ZeroConfig.Entry n = (i + 1 < items.size() && items.get(i + 1).half) ? items.get(i + 1) : null;
                int rowH = U.dp(c, n == null ? e.hDp : Math.max(e.hDp, n.hDp));
                LinearLayout row = new LinearLayout(c);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setClipChildren(false);
                row.addView(cell(e), new LinearLayout.LayoutParams(0, rowH, 1f));
                LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(0, rowH, 1f);
                p2.setMarginStart(gap);
                row.addView(n != null ? cell(n) : new View(c), p2);
                lp = new LinearLayout.LayoutParams(-1, -2);
                col.addView(row, lp);
                i += n != null ? 2 : 1;
            } else {
                lp = new LinearLayout.LayoutParams(-1, U.dp(c, e.hDp));
                col.addView(cell(e), lp);
                i++;
            }
            lp.bottomMargin = gap;
        }

        if (edit) {
            GlassLayout add = new GlassLayout(c, 20, 0.40f, true);
            add.setGravity(Gravity.CENTER);
            TextView t = new TextView(c);
            t.setText("+  Tambah Widget");
            t.setTextColor(0xFFFFFFFF);
            t.setTextSize(16f);
            t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            add.addView(t, new LinearLayout.LayoutParams(-2, -2));
            add.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { ZeroScreen.this.host.onZeroAdd(); }
            });
            col.addView(add, new LinearLayout.LayoutParams(-1, U.dp(c, 56)));

            LinearLayout tools = new LinearLayout(c);
            tools.setGravity(Gravity.CENTER);
            tools.addView(pill(cfg.showTitle ? "Judul: Tampil" : "Judul: Sembunyi", new View.OnClickListener() {
                @Override public void onClick(View v) { cfg.showTitle = !cfg.showTitle; cfg.save(); rebuild(); }
            }), new LinearLayout.LayoutParams(-2, U.dp(c, 34)));
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-2, U.dp(c, 34));
            rp.setMarginStart(U.dp(c, 10));
            tools.addView(pill("Atur Ulang", new View.OnClickListener() {
                @Override public void onClick(View v) {
                    for (ZeroConfig.Entry x : cfg.items) if (x.isExt()) ZeroScreen.this.host.onZeroRemoveExternal(x.wid);
                    cfg.defaults();
                    cfg.save();
                    rebuild();
                }
            }), rp);
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, -2);
            tp.topMargin = U.dp(c, 12);
            col.addView(tools, tp);
        }
        sv.post(new Runnable() { @Override public void run() { sv.scrollTo(0, keepY); } });
    }

    private GlassLayout pill(String text, View.OnClickListener l) {
        Context c = getContext();
        GlassLayout g = new GlassLayout(c, 17, 0.40f, true);
        g.setGravity(Gravity.CENTER);
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextColor(0xFFFFFFFF);
        t.setTextSize(14f);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setPadding(U.dp(c, 14), 0, U.dp(c, 14), 0);
        t.setGravity(Gravity.CENTER);
        g.addView(t, new LinearLayout.LayoutParams(-2, -1));
        g.setOnClickListener(l);
        return g;
    }

    private View cell(final ZeroConfig.Entry e) {
        final Context c = getContext();
        View w;
        if (e.isExt()) {
            w = host.createExternal(e.toItem());
        } else {
            BuiltinWidgets.Base b = BuiltinWidgets.create(c, e.toItem(), prefs, dark);
            if (BuiltinWidgets.SEARCH.equals(e.kind) && !edit) {
                b.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { ZeroScreen.this.host.onZeroSearch(); }
                });
            }
            w = b;
        }
        if (!edit) return w;

        FrameLayout f = new FrameLayout(c);
        f.setClipChildren(false);
        f.addView(w, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout ov = new FrameLayout(c);
        ov.setClickable(true);
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setColor(0x4D000000);
        g.setCornerRadius(U.dpf(c, 20));
        ov.setBackground(g);
        f.addView(ov, new FrameLayout.LayoutParams(-1, -1));

        GlassLayout bar = new GlassLayout(c, 16, 0.55f, true);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);
        bar.addView(tool(c, "\u2212", 0, new Runnable() { @Override public void run() { e.hDp = ZeroConfig.clampH(e.hDp - 24); changed(); } }));
        bar.addView(tool(c, "+", 0, new Runnable() { @Override public void run() { e.hDp = ZeroConfig.clampH(e.hDp + 24); changed(); } }));
        bar.addView(tool(c, "\u00BD", e.half ? 0xFF0A84FF : 0, new Runnable() { @Override public void run() { e.half = !e.half; changed(); } }));
        bar.addView(tool(c, "\u2191", 0, new Runnable() { @Override public void run() { move(e, -1); } }));
        bar.addView(tool(c, "\u2193", 0, new Runnable() { @Override public void run() { move(e, 1); } }));
        bar.addView(tool(c, "\u00D7", 0xFFFF453A, new Runnable() {
            @Override public void run() {
                if (e.isExt()) host.onZeroRemoveExternal(e.wid);
                cfg.items.remove(e);
                changed();
            }
        }));
        f.addView(bar, new FrameLayout.LayoutParams(-2, U.dp(c, 36), Gravity.CENTER));
        return f;
    }

    private TextView tool(Context c, String label, int color, final Runnable r) {
        TextView t = new TextView(c);
        t.setText(label);
        t.setTextColor(color == 0 ? 0xFFFFFFFF : color);
        t.setTextSize(18f);
        t.setGravity(Gravity.CENTER);
        t.setMinWidth(U.dp(c, 42));
        t.setMinHeight(U.dp(c, 36));
        t.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { r.run(); } });
        return t;
    }

    private void move(ZeroConfig.Entry e, int dir) {
        int i = cfg.items.indexOf(e), j = i + dir;
        if (i < 0 || j < 0 || j >= cfg.items.size()) return;
        java.util.Collections.swap(cfg.items, i, j);
        changed();
    }

    private void changed() {
        cfg.save();
        rebuild();
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
