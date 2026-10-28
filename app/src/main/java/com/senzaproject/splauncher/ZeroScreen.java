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
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.CompoundButton;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * Layar Nol: halaman widget di sebelah kiri Home pertama. Muncul saat Home pertama digeser ke kanan,
 * mengikuti jari, dan ditutup dengan geser ke kiri, tombol Kembali, atau tombol Home.
 * progress 0 = tertutup, 1 = terbuka penuh.
 *
 * Bisa dikustomisasi (tombol "Ubah" atau tekan lama judul): tambah/hapus widget, urutkan, ubah tinggi bebas dengan
 * menyeret pegangan di bawah widget, setengah/penuh lebar, ganti judul, sembunyikan judul, atur peredam latar.
 */
final class ZeroScreen extends FrameLayout {
    interface Host {
        void onZeroClosed(ZeroScreen z);
        boolean reduceMotion();
        /** Bilah pencarian di Layar Nol diketuk. */
        void onZeroSearch();
        /** View widget aplikasi lain (AppWidgetHostView) untuk ditampilkan di Layar Nol. */
        View zeroExternalWidget(LayoutStore.Item it, int wDp, int hDp);
        /** Buka galeri widget untuk menambah widget ke Layar Nol. */
        void onZeroAddWidget();
        /** Widget dihapus dari Layar Nol (lepaskan appWidgetId bila eksternal). */
        void onZeroWidgetRemoved(ZeroConfig.Entry e);
    }

    private final Context ctx;
    private final Host host;
    private final Prefs prefs;
    private final boolean dark;
    private final ZeroConfig cfg;
    private final int insTop, insBottom;
    private final View scrim;
    private final FrameLayout body;
    private final ScrollView sv;
    private final LinearLayout col;
    private final GlassLayout editBtn;
    private final TextView editBtnText;
    private final int slop;

    private boolean editing;
    private float progress;
    private ValueAnimator anim;
    private boolean closing;
    private float downX, downY;
    private boolean dragging;
    private VelocityTracker vt;

    ZeroScreen(Context c, Prefs prefs, boolean dark, int insTop, int insBottom, ZeroConfig cfg, Host host) {
        super(c);
        this.ctx = c;
        this.host = host;
        this.prefs = prefs;
        this.dark = dark;
        this.cfg = cfg;
        this.insTop = insTop;
        this.insBottom = insBottom;
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
        setClickable(true);

        // Latar OPAK (wallpaper diburamkan + peredam yang bisa diatur) sehingga ikon Home dan Dock tidak tembus.
        final Paint bgPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
        final Rect dst = new Rect();
        scrim = new View(c) {
            @Override protected void onDraw(Canvas cv) {
                Bitmap bg = Glass.blurred;
                if (bg != null && !bg.isRecycled()) {
                    dst.set(0, 0, getWidth(), getHeight());
                    cv.drawBitmap(bg, null, dst, bgPaint);
                } else {
                    cv.drawColor(ZeroScreen.this.dark ? 0xFF101014 : 0xFF8E8E93);
                }
                int a = Math.round(U.clamp(ZeroScreen.this.cfg.dim, 0, 90) * 2.55f);
                cv.drawColor(a << 24);
            }
        };
        scrim.setWillNotDraw(false);
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

        col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        sv.addView(col, new ScrollView.LayoutParams(-1, -2));

        // tombol Ubah / Selesai (mengambang di kanan atas)
        editBtn = new GlassLayout(c, 17, 0.40f, true);
        editBtn.setGravity(Gravity.CENTER);
        editBtnText = new TextView(c);
        editBtnText.setTextColor(0xFFFFFFFF);
        editBtnText.setTextSize(15f);
        editBtnText.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        editBtnText.setGravity(Gravity.CENTER);
        editBtnText.setPadding(U.dp(c, 18), 0, U.dp(c, 18), 0);
        editBtn.addView(editBtnText, new LinearLayout.LayoutParams(-2, -1));
        editBtn.setOnClickListener(new OnClickListener() { @Override public void onClick(View v) { setEditing(!editing); } });
        LayoutParams elp = new LayoutParams(-2, U.dp(c, 34), Gravity.TOP | Gravity.END);
        elp.topMargin = insTop + U.dp(c, 12);
        elp.rightMargin = U.dp(c, 16);
        body.addView(editBtn, elp);

        render();
        applyProgress();
    }

    // ------------------------------------------------------------------ isi

    private int dp(float v) { return U.dp(ctx, v); }

    private void setEditing(boolean on) {
        editing = on;
        if (!on) cfg.save();
        render();
    }

    /** Dipanggil Launcher setelah widget ditambahkan lewat galeri widget. */
    void refresh() { render(); }

    boolean isEditing() { return editing; }

    private void render() {
        editBtnText.setText(editing ? "Selesai" : "Ubah");
        int side = dp(16);
        col.setPadding(side, insTop + dp(14), side, insBottom + dp(editing ? 96 : 28));
        col.removeAllViews();
        int screenW = ctx.getResources().getDisplayMetrics().widthPixels;
        int cw = screenW - 2 * side;
        int gap = dp(12);

        // judul
        if (cfg.showTitle || editing) {
            TextView title = new TextView(ctx);
            String t = cfg.title == null || cfg.title.trim().isEmpty() ? "Layar Nol" : cfg.title;
            title.setText(t);
            title.setTextColor(0xFFFFFFFF);
            title.setTextSize(30f);
            title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            title.setShadowLayer(U.dpf(ctx, 4), 0, U.dpf(ctx, 1), 0x66000000);
            title.setSingleLine(true);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            title.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            title.setPadding(dp(4), 0, 0, dp(12));
            if (!cfg.showTitle) title.setAlpha(0.4f);
            title.setOnLongClickListener(new OnLongClickListener() {
                @Override public boolean onLongClick(View v) { setEditing(!editing); return true; }
            });
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, -2);
            tlp.rightMargin = dp(96);
            col.addView(title, tlp);
        } else {
            col.addView(new View(ctx), new LinearLayout.LayoutParams(-1, dp(44)));
        }

        if (editing) col.addView(buildOptions(), lpBottom(gap));

        ArrayList<ZeroConfig.Entry> items = cfg.items;
        int half = (cw - gap) / 2;
        for (int i = 0; i < items.size(); ) {
            ZeroConfig.Entry e = items.get(i);
            if (e.half && i + 1 < items.size() && items.get(i + 1).half) {
                ZeroConfig.Entry e2 = items.get(i + 1);
                LinearLayout row = new LinearLayout(ctx);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.TOP);
                row.setClipChildren(false);
                row.addView(cell(e, half), new LinearLayout.LayoutParams(half, dp(e.hDp)));
                LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(half, dp(e2.hDp));
                p2.leftMargin = gap;
                row.addView(cell(e2, half), p2);
                col.addView(row, lpBottom(gap));
                i += 2;
            } else {
                int w = e.half ? half : cw;
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, dp(e.hDp));
                lp.bottomMargin = gap;
                col.addView(cell(e, w), lp);
                i++;
            }
        }

        if (editing) {
            col.addView(pill("+  Tambah Widget", new OnClickListener() {
                @Override public void onClick(View v) { host.onZeroAddWidget(); }
            }), lpPill());
            col.addView(pill("Atur Ulang ke Bawaan", new OnClickListener() {
                @Override public void onClick(View v) { resetDefaults(); }
            }), lpPill());
        } else if (items.isEmpty()) {
            TextView hint = new TextView(ctx);
            hint.setText("Layar Nol masih kosong.\nKetuk \"Ubah\" untuk menambahkan widget.");
            hint.setTextColor(0xCCFFFFFF);
            hint.setTextSize(15f);
            hint.setGravity(Gravity.CENTER);
            hint.setPadding(0, dp(48), 0, 0);
            col.addView(hint, new LinearLayout.LayoutParams(-1, -2));
        }
    }

    private LinearLayout.LayoutParams lpBottom(int gap) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = gap;
        return lp;
    }

    private LinearLayout.LayoutParams lpPill() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(46));
        lp.topMargin = dp(4);
        lp.bottomMargin = dp(8);
        return lp;
    }

    private View pill(String text, OnClickListener l) {
        GlassLayout g = new GlassLayout(ctx, 23, 0.40f, true);
        g.setGravity(Gravity.CENTER);
        TextView t = new TextView(ctx);
        t.setText(text);
        t.setTextColor(0xFFFFFFFF);
        t.setTextSize(15.5f);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setGravity(Gravity.CENTER);
        g.addView(t, new LinearLayout.LayoutParams(-2, -2));
        g.setOnClickListener(l);
        return g;
    }

    private void resetDefaults() {
        for (ZeroConfig.Entry e : new ArrayList<>(cfg.items)) host.onZeroWidgetRemoved(e);
        cfg.defaults();
        cfg.save();
        scrim.invalidate();
        render();
    }

    // ------------------------------------------------------------------ satu widget

    private View cell(final ZeroConfig.Entry e, int wPx) {
        LayoutStore.Item it = e.toItem();
        View content;
        if (e.isExt()) {
            content = host.zeroExternalWidget(it, Math.round(wPx / ctx.getResources().getDisplayMetrics().density), e.hDp);
        } else {
            BuiltinWidgets.Base w = BuiltinWidgets.create(ctx, it, prefs, dark);
            if (!editing && BuiltinWidgets.SEARCH.equals(e.kind)) {
                w.setOnClickListener(new OnClickListener() { @Override public void onClick(View v) { host.onZeroSearch(); } });
            }
            content = w;
        }
        if (!editing) return content;

        final FrameLayout fl = new FrameLayout(ctx);
        fl.setClipChildren(false);
        if (content.getParent() instanceof android.view.ViewGroup) ((android.view.ViewGroup) content.getParent()).removeView(content);
        fl.addView(content, new LayoutParams(-1, -1));

        // lapisan penangkap sentuhan + garis putus-putus
        View frame = new View(ctx);
        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(U.dpf(ctx, 22));
        gd.setStroke(Math.max(2, dp(2)), 0xE6FFFFFF, U.dpf(ctx, 7), U.dpf(ctx, 5));
        gd.setColor(0x14000000);
        frame.setBackground(gd);
        frame.setClickable(true);
        fl.addView(frame, new LayoutParams(-1, -1));

        // tombol kontrol (kanan atas)
        LinearLayout ctl = new LinearLayout(ctx);
        ctl.setOrientation(LinearLayout.HORIZONTAL);
        ctl.addView(ctlBtn("↑", new OnClickListener() { @Override public void onClick(View v) { move(e, -1); } }));
        ctl.addView(ctlBtn("↓", new OnClickListener() { @Override public void onClick(View v) { move(e, 1); } }));
        ctl.addView(ctlBtn(e.half ? "1" : "½", new OnClickListener() {
            @Override public void onClick(View v) { e.half = !e.half; cfg.save(); render(); }
        }));
        ctl.addView(ctlBtn("✕", new OnClickListener() {
            @Override public void onClick(View v) { cfg.items.remove(e); host.onZeroWidgetRemoved(e); cfg.save(); render(); }
        }));
        LayoutParams clp = new LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        clp.topMargin = dp(6);
        clp.rightMargin = dp(6);
        fl.addView(ctl, clp);

        // pegangan ubah tinggi (bawah tengah): seret ke atas/bawah
        final TextView hLabel = new TextView(ctx);
        hLabel.setText(e.hDp + " dp");
        hLabel.setTextColor(0xFFFFFFFF);
        hLabel.setTextSize(11f);
        hLabel.setShadowLayer(U.dpf(ctx, 3), 0, 0, 0xCC000000);
        LayoutParams llp = new LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
        llp.leftMargin = dp(12);
        llp.bottomMargin = dp(8);
        fl.addView(hLabel, llp);

        FrameLayout grip = new FrameLayout(ctx);
        View bar = new View(ctx);
        GradientDrawable bd = new GradientDrawable();
        bd.setCornerRadius(U.dpf(ctx, 3));
        bd.setColor(0xFFFFFFFF);
        bar.setBackground(bd);
        grip.addView(bar, new LayoutParams(dp(56), dp(6), Gravity.CENTER));
        LayoutParams glp = new LayoutParams(dp(160), dp(34), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        glp.bottomMargin = dp(2);
        fl.addView(grip, glp);
        grip.setOnTouchListener(new View.OnTouchListener() {
            private float startY;
            private int startH;
            @Override public boolean onTouch(View v, MotionEvent ev) {
                switch (ev.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startY = ev.getRawY();
                        startH = e.hDp;
                        sv.requestDisallowInterceptTouchEvent(true);
                        return true;
                    case MotionEvent.ACTION_MOVE: {
                        float d = (ev.getRawY() - startY) / ctx.getResources().getDisplayMetrics().density;
                        e.hDp = ZeroConfig.clampH(Math.round(startH + d));
                        hLabel.setText(e.hDp + " dp");
                        View p = (View) fl;
                        android.view.ViewGroup.LayoutParams lp = p.getLayoutParams();
                        if (lp != null) { lp.height = dp(e.hDp); p.setLayoutParams(lp); }
                        return true;
                    }
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        sv.requestDisallowInterceptTouchEvent(false);
                        cfg.save();
                        return true;
                    default:
                        return true;
                }
            }
        });
        return fl;
    }

    private View ctlBtn(String label, OnClickListener l) {
        TextView t = new TextView(ctx);
        t.setText(label);
        t.setTextColor(0xFFFFFFFF);
        t.setTextSize(15f);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(0xB3000000);
        g.setStroke(Math.max(1, dp(1)), 0x55FFFFFF);
        t.setBackground(g);
        t.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(34), dp(34));
        lp.leftMargin = dp(5);
        t.setLayoutParams(lp);
        return t;
    }

    private void move(ZeroConfig.Entry e, int dir) {
        int i = cfg.items.indexOf(e);
        int j = i + dir;
        if (i < 0 || j < 0 || j >= cfg.items.size()) return;
        cfg.items.remove(i);
        cfg.items.add(j, e);
        cfg.save();
        render();
    }

    // ------------------------------------------------------------------ panel opsi (mode ubah)

    private View buildOptions() {
        GlassLayout card = new GlassLayout(ctx, 22, 0.55f, true);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(12), dp(16), dp(14));

        TextView l1 = label("Judul");
        card.addView(l1);
        EditText et = new EditText(ctx);
        et.setText(cfg.title);
        et.setHint("Layar Nol");
        et.setSingleLine(true);
        et.setTextColor(0xFFFFFFFF);
        et.setHintTextColor(0x88FFFFFF);
        et.setTextSize(16f);
        et.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                cfg.title = s.toString();
                cfg.save();
                if (col.getChildCount() > 0 && col.getChildAt(0) instanceof TextView && !(col.getChildAt(0) instanceof EditText)) {
                    String t = cfg.title.trim().isEmpty() ? "Layar Nol" : cfg.title;
                    ((TextView) col.getChildAt(0)).setText(t);
                }
            }
        });
        card.addView(et, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout r2 = new LinearLayout(ctx);
        r2.setGravity(Gravity.CENTER_VERTICAL);
        r2.setOrientation(LinearLayout.HORIZONTAL);
        TextView l2 = label("Tampilkan judul");
        r2.addView(l2, new LinearLayout.LayoutParams(0, -2, 1f));
        Switch sw = new Switch(ctx);
        sw.setChecked(cfg.showTitle);
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean on) { cfg.showTitle = on; cfg.save(); }
        });
        r2.addView(sw);
        LinearLayout.LayoutParams r2p = new LinearLayout.LayoutParams(-1, -2);
        r2p.topMargin = dp(8);
        card.addView(r2, r2p);

        final TextView l3 = label("Peredam latar: " + cfg.dim + "%");
        LinearLayout.LayoutParams l3p = new LinearLayout.LayoutParams(-1, -2);
        l3p.topMargin = dp(10);
        card.addView(l3, l3p);
        SeekBar sb = new SeekBar(ctx);
        sb.setMax(90);
        sb.setProgress(cfg.dim);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean user) {
                cfg.dim = p;
                l3.setText("Peredam latar: " + p + "%");
                scrim.invalidate();
            }
            @Override public void onStartTrackingTouch(SeekBar s) { sv.requestDisallowInterceptTouchEvent(true); }
            @Override public void onStopTrackingTouch(SeekBar s) { cfg.save(); sv.requestDisallowInterceptTouchEvent(false); }
        });
        card.addView(sb, new LinearLayout.LayoutParams(-1, -2));

        TextView tip = label("Seret garis putih di bawah widget untuk mengubah tinggi. ↑ ↓ mengurutkan, ½ membuat dua widget bersebelahan.");
        tip.setTextSize(12f);
        tip.setAlpha(0.75f);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, -2);
        tp.topMargin = dp(8);
        card.addView(tip, tp);
        return card;
    }

    private TextView label(String s) {
        TextView t = new TextView(ctx);
        t.setText(s);
        t.setTextColor(0xFFFFFFFF);
        t.setTextSize(14f);
        return t;
    }

    // ------------------------------------------------------------------ animasi buka/tutup

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
        if (closing && editing) { editing = false; cfg.save(); render(); }
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

    /** Kembali: keluar dari mode ubah dulu, baru tutup Layar Nol. */
    void close() {
        if (editing) { setEditing(false); return; }
        animateTo(0f);
    }

    /** Dipanggil saat panel dihapus dari layar. */
    void release() {
        if (anim != null) anim.cancel();
        if (editing) cfg.save();
    }

    // ------------------------------------------------------------------ geser ke kiri untuk menutup

    @Override public boolean onInterceptTouchEvent(MotionEvent e) {
        if (editing) return false;
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
        if (editing) return true;
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
