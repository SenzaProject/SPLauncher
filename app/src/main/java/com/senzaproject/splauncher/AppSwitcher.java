package com.senzaproject.splauncher;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewOutlineProvider;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Pengalih Aplikasi bergaya iOS 27: kartu aplikasi berjajar horizontal di atas latar wallpaper yang diburamkan.
 * Ketuk kartu = buka aplikasi, geser kartu ke atas = tutup aplikasi, ketuk area kosong = kembali.
 */
final class AppSwitcher extends FrameLayout {
    private final LauncherActivity a;
    private final Prefs prefs;
    private final boolean dark;
    private final Paint bgPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Rect dst = new Rect();
    private final HorizontalScrollView hsv;
    private final LinearLayout strip;
    private final LinearLayout emptyBox;
    private final TextView emptyHint;
    private final GlassLayout clearBtn;
    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private final int slop;
    private final int cardW, cardH, radius;
    /** Mode tumpukan kartu: kartu saling menimpa, yang di tengah paling besar dan paling depan. */
    private final boolean stacked;
    private final int overlap;
    private boolean touching;
    private final Runnable snapRun = new Runnable() {
        @Override public void run() { snapToNearest(); }
    };
    private boolean closing;
    private boolean tapValid;
    private float tapX, tapY;
    private long tapT;

    AppSwitcher(LauncherActivity act) {
        super(act);
        a = act;
        prefs = act.prefs;
        dark = act.dark;
        slop = ViewConfiguration.get(act).getScaledTouchSlop();
        setWillNotDraw(false);
        setClipChildren(false);

        int w = Math.max(1, a.root.getWidth()), h = Math.max(1, a.root.getHeight());
        int availH = h - a.insTop - a.insBottom - U.dp(a, 190);
        int cw = Math.round(w * prefs.switcherCardSize() / 100f);
        int ch = Math.round(cw * h / (float) w);
        if (ch > availH && availH > 0) {
            ch = availH;
            cw = Math.round(ch * w / (float) h);
        }
        cardW = Math.max(U.dp(a, 120), cw);
        cardH = Math.max(U.dp(a, 160), ch);
        radius = U.dp(a, 28);
        stacked = prefs.switcherStyle() == 1;
        overlap = stacked ? Math.round(cardW * 0.40f) : 0;

        hsv = new HorizontalScrollView(act);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        hsv.setClipChildren(false);
        hsv.setClipToPadding(false);
        hsv.setFillViewport(true);
        strip = new LinearLayout(act);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setGravity(Gravity.CENTER_VERTICAL);
        strip.setClipChildren(false);
        int side = Math.max(0, (w - cardW) / 2);
        int padEnd = stacked ? side + overlap : (side - U.dp(a, 16) > 0 ? side - U.dp(a, 16) : 0);
        strip.setPadding(side, a.insTop, padEnd, a.insBottom + U.dp(a, 70));
        hsv.addView(strip, new FrameLayout.LayoutParams(-2, -1));
        addView(hsv, new FrameLayout.LayoutParams(-1, -1));
        if (stacked) {
            hsv.setOnScrollChangeListener(new View.OnScrollChangeListener() {
                @Override public void onScrollChange(View v, int x, int y, int ox, int oy) {
                    updateStack();
                    if (!touching) {
                        removeCallbacks(snapRun);
                        postDelayed(snapRun, 110);
                    }
                }
            });
            strip.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                @Override public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                    updateStack();
                }
            });
        }

        hsv.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        tapValid = true;
                        touching = true;
                        removeCallbacks(snapRun);
                        tapX = e.getRawX(); tapY = e.getRawY(); tapT = e.getEventTime();
                        break;
                    case MotionEvent.ACTION_MOVE:
                        if (Math.abs(e.getRawX() - tapX) > slop || Math.abs(e.getRawY() - tapY) > slop) tapValid = false;
                        break;
                    case MotionEvent.ACTION_UP:
                        if (tapValid && e.getEventTime() - tapT < 400) a.closeSwitcher(true);
                        tapValid = false;
                        touching = false;
                        if (stacked) { removeCallbacks(snapRun); postDelayed(snapRun, 140); }
                        break;
                    case MotionEvent.ACTION_CANCEL:
                        tapValid = false;
                        touching = false;
                        if (stacked) { removeCallbacks(snapRun); postDelayed(snapRun, 140); }
                        break;
                    default:
                        break;
                }
                return false;
            }
        });

        emptyBox = new LinearLayout(act);
        emptyBox.setOrientation(LinearLayout.VERTICAL);
        emptyBox.setGravity(Gravity.CENTER);
        emptyBox.setPadding(U.dp(a, 32), 0, U.dp(a, 32), 0);
        TextView et = new TextView(act);
        et.setText("Tidak ada aplikasi terbaru");
        et.setTextColor(0xFFFFFFFF);
        et.setTextSize(19f);
        et.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        et.setGravity(Gravity.CENTER);
        emptyBox.addView(et, new LinearLayout.LayoutParams(-2, -2));
        emptyHint = new TextView(act);
        emptyHint.setTextColor(0xCCFFFFFF);
        emptyHint.setTextSize(13.5f);
        emptyHint.setGravity(Gravity.CENTER);
        emptyHint.setPadding(0, U.dp(a, 8), 0, 0);
        emptyBox.addView(emptyHint, new LinearLayout.LayoutParams(-2, -2));
        emptyBox.setVisibility(View.GONE);
        addView(emptyBox, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));

        clearBtn = new GlassLayout(act, 19, 0.40f, true);
        clearBtn.setGravity(Gravity.CENTER);
        TextView ct = new TextView(act);
        ct.setText("Hapus Semua");
        ct.setTextColor(0xFFFFFFFF);
        ct.setTextSize(15f);
        ct.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        ct.setGravity(Gravity.CENTER);
        ct.setPadding(U.dp(a, 24), 0, U.dp(a, 24), 0);
        clearBtn.addView(ct, new LinearLayout.LayoutParams(-2, -1));
        clearBtn.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { clearAll(); } });
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(-2, U.dp(a, 38), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        clp.bottomMargin = a.insBottom + U.dp(a, 34);
        addView(clearBtn, clp);
        if (!prefs.switcherClearAll()) clearBtn.setVisibility(View.GONE);
    }

    // ------------------------------------------------------------------ siklus

    void open() {
        populate();
        if (!prefs.reduceMotion()) {
            setAlpha(0f);
            hsv.setScaleX(1.1f);
            hsv.setScaleY(1.1f);
            animate().alpha(1f).setDuration(200).start();
            hsv.animate().scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(new DecelerateInterpolator(1.6f)).start();
        }
    }

    void close(boolean animate, final Runnable end) {
        if (closing) return;
        closing = true;
        removeCallbacks(snapRun);
        exec.shutdownNow();
        if (!animate || prefs.reduceMotion()) {
            end.run();
            return;
        }
        animate().alpha(0f).setDuration(160).withEndAction(end).start();
        hsv.animate().scaleX(0.94f).scaleY(0.94f).setDuration(160).start();
    }

    @Override protected void onDraw(Canvas c) {
        Bitmap bg = Glass.blurred;
        if (bg != null && !bg.isRecycled()) {
            dst.set(0, 0, getWidth(), getHeight());
            c.drawBitmap(bg, null, dst, bgPaint);
        } else {
            c.drawColor(0xFF000000);
        }
        c.drawColor(dark ? 0x80000000 : 0x5C000000);
    }

    // ------------------------------------------------------------------ tumpukan kartu

    /** Atur skala dan urutan gambar tiap kartu menurut jaraknya dari tengah layar. */
    private void updateStack() {
        if (!stacked) return;
        int n = strip.getChildCount();
        float vc = hsv.getScrollX() + hsv.getWidth() / 2f;
        float pitch = Math.max(1f, cardW - overlap);
        for (int i = 0; i < n; i++) {
            View sl = strip.getChildAt(i);
            float d = (sl.getLeft() + sl.getWidth() / 2f - vc) / pitch;   // 0 = di tengah
            float ad = Math.min(Math.abs(d), 3f);
            float sc = 1f - 0.075f * ad;
            sl.setScaleX(sc);
            sl.setScaleY(sc);
            // kartu terdekat dari tengah digambar paling depan
            sl.setTranslationZ(U.dpf(a, 40) - ad * U.dpf(a, 10));
        }
    }

    /** Setelah geser berhenti, rapikan agar satu kartu tepat di tengah. */
    private void snapToNearest() {
        if (!stacked || closing || touching) return;
        int n = strip.getChildCount();
        if (n == 0) return;
        float pitch = Math.max(1f, cardW - overlap);
        int target = Math.round(U.clampf(hsv.getScrollX() / pitch, 0f, n - 1f));
        int x = Math.round(target * pitch);
        if (Math.abs(x - hsv.getScrollX()) > 1) hsv.smoothScrollTo(x, 0);
    }

    // ------------------------------------------------------------------ isi

    private void populate() {
        List<String> pkgs = Recents.packages(a);
        int n = 0;
        for (String p : pkgs) {
            if (n >= 14) break;
            if (p.equals(a.getPackageName())) continue;
            AppRepo.AppInfo app = a.repo.findByPackage(p);
            if (app == null) continue;
            addSlot(app);
            n++;
        }
        refreshEmpty();
    }

    private void refreshEmpty() {
        boolean none = strip.getChildCount() == 0;
        emptyBox.setVisibility(none ? View.VISIBLE : View.GONE);
        if (none) {
            boolean any = SPAccessibilityService.isEnabled(a) || Recents.usageGranted(a);
            emptyHint.setText(any ? "" : "Aktifkan layanan Aksesibilitas atau Akses Penggunaan di Pengaturan SP Launcher "
                    + "agar daftar aplikasi terbaru muncul di sini.");
            emptyHint.setVisibility(any ? View.GONE : View.VISIBLE);
        }
        if (prefs.switcherClearAll()) clearBtn.setVisibility(none ? View.GONE : View.VISIBLE);
    }

    private void addSlot(final AppRepo.AppInfo app) {
        final LinearLayout slot = new LinearLayout(a);
        slot.setOrientation(LinearLayout.VERTICAL);
        slot.setClipChildren(false);
        slot.setTag(app);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(cardW, -2);
        slp.setMarginEnd(stacked ? -overlap : U.dp(a, 16));
        strip.addView(slot, slp);

        // kepala: ikon + nama
        LinearLayout head = new LinearLayout(a);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(U.dp(a, 6), 0, U.dp(a, 6), U.dp(a, 8));
        final ImageView hi = new ImageView(a);
        head.addView(hi, new LinearLayout.LayoutParams(U.dp(a, 26), U.dp(a, 26)));
        TextView tv = new TextView(a);
        tv.setText(app.label);
        tv.setTextColor(0xFFFFFFFF);
        tv.setTextSize(14f);
        tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        tv.setSingleLine(true);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        tv.setShadowLayer(U.dpf(a, 3), 0, U.dpf(a, 1), 0x99000000);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, -2, 1f);
        tlp.setMarginStart(U.dp(a, 8));
        head.addView(tv, tlp);
        if (!prefs.switcherLabels()) tv.setVisibility(View.GONE);
        slot.addView(head, new LinearLayout.LayoutParams(-1, -2));
        IconFactory.load(app, U.dp(a, 26), prefs, dark, new IconFactory.Callback() {
            @Override public void onIcon(Bitmap b) { if (!closing) hi.setImageBitmap(b); }
        });

        // kartu
        final FrameLayout card = new FrameLayout(a);
        card.setClipToOutline(true);
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius);
            }
        });
        card.setElevation(U.dp(a, 12));
        card.setContentDescription(app.label);
        final GradientDrawable fill = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                dark ? new int[]{0xFF3A3A3C, 0xFF1C1C1E} : new int[]{0xFFF2F2F7, 0xFFC7C7CC});
        card.setBackground(fill);
        final ImageView big = new ImageView(a);
        card.addView(big, new FrameLayout.LayoutParams(U.dp(a, 76), U.dp(a, 76), Gravity.CENTER));
        final ImageView snap = new ImageView(a);
        snap.setScaleType(ImageView.ScaleType.CENTER_CROP);
        snap.setVisibility(View.GONE);
        card.addView(snap, new FrameLayout.LayoutParams(-1, -1));
        GradientDrawable border = new GradientDrawable();
        border.setCornerRadius(radius);
        border.setStroke(Math.max(1, U.dp(a, 1)), 0x33FFFFFF);
        card.setForeground(border);
        slot.addView(card, new LinearLayout.LayoutParams(cardW, cardH));

        IconFactory.load(app, U.dp(a, 76), prefs, dark, new IconFactory.Callback() {
            @Override public void onIcon(Bitmap b) {
                if (closing) return;
                big.setImageBitmap(b);
                try {
                    int avg = Bitmap.createScaledBitmap(b, 1, 1, true).getPixel(0, 0) | 0xFF000000;
                    int top = U.lerpColor(avg, dark ? 0xFF2C2C2E : 0xFFFFFFFF, 0.55f);
                    int bot = U.lerpColor(avg, 0xFF000000, dark ? 0.62f : 0.35f);
                    fill.setColors(new int[]{top, bot});
                } catch (Throwable ignored) { }
            }
        });
        if (prefs.switcherSnapshots()) loadSnap(app.pkg, snap, big);

        card.setOnTouchListener(new CardTouch(slot, app, card));
    }

    private void loadSnap(final String pkg, final ImageView iv, final View iconView) {
        try {
            exec.execute(new Runnable() {
                @Override public void run() {
                    final Bitmap b = Recents.snapshot(a, pkg);
                    if (b == null) return;
                    post(new Runnable() {
                        @Override public void run() {
                            if (closing) return;
                            iv.setImageBitmap(b);
                            iv.setVisibility(View.VISIBLE);
                            iconView.setVisibility(View.GONE);
                        }
                    });
                }
            });
        } catch (Throwable ignored) { }
    }

    // ------------------------------------------------------------------ aksi

    private void openApp(final AppRepo.AppInfo app, View card) {
        a.repo.launch(app, card);
        postDelayed(new Runnable() { @Override public void run() { a.closeSwitcher(false); } }, 320);
    }

    private void dismissSlot(final View slot, AppRepo.AppInfo app, long delay, final boolean refresh) {
        Recents.remove(a, app.pkg);
        if (prefs.switcherKill()) {
            try {
                ActivityManager am = (ActivityManager) a.getSystemService(Context.ACTIVITY_SERVICE);
                if (am != null) am.killBackgroundProcesses(app.pkg);
            } catch (Throwable ignored) { }
        }
        slot.animate().cancel();
        slot.animate().translationY(-Math.max(getHeight(), 1)).alpha(0f)
                .setStartDelay(delay).setDuration(prefs.reduceMotion() ? 1 : 210)
                .setInterpolator(new AccelerateInterpolator(1.2f))
                .withEndAction(new Runnable() {
                    @Override public void run() {
                        strip.removeView(slot);
                        if (refresh) refreshEmpty();
                    }
                }).start();
    }

    private void clearAll() {
        int n = strip.getChildCount();
        if (n == 0) return;
        a.haptic(this);
        ArrayList<View> slots = new ArrayList<>();
        for (int i = 0; i < n; i++) slots.add(strip.getChildAt(i));
        for (int i = 0; i < slots.size(); i++) {
            View s = slots.get(i);
            Object t = s.getTag();
            if (t instanceof AppRepo.AppInfo) dismissSlot(s, (AppRepo.AppInfo) t, i * 35L, i == slots.size() - 1);
        }
        clearBtn.setVisibility(View.GONE);
    }

    private void springBack(View slot) {
        slot.animate().cancel();
        slot.animate().translationY(0f).alpha(1f).setStartDelay(0).setDuration(220)
                .setInterpolator(new OvershootInterpolator(1.1f)).start();
    }

    /** Ketuk = buka; geser ke atas = tutup. Geser horizontal diserahkan ke HorizontalScrollView. */
    private final class CardTouch implements View.OnTouchListener {
        private final View slot, card;
        private final AppRepo.AppInfo app;
        private float downX, downY;
        private boolean vertical, horizontal;
        private VelocityTracker vt;

        CardTouch(View slot, AppRepo.AppInfo app, View card) {
            this.slot = slot;
            this.app = app;
            this.card = card;
        }

        private void recycle() {
            if (vt != null) { vt.recycle(); vt = null; }
        }

        @Override public boolean onTouch(View v, MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getRawX();
                    downY = e.getRawY();
                    vertical = false;
                    horizontal = false;
                    recycle();
                    vt = VelocityTracker.obtain();
                    vt.addMovement(e);
                    slot.animate().cancel();
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    if (vt != null) vt.addMovement(e);
                    float dx = e.getRawX() - downX, dy = e.getRawY() - downY;
                    if (!vertical && !horizontal) {
                        if (dy < -slop && Math.abs(dy) > Math.abs(dx) * 1.3f) {
                            vertical = true;
                            if (v.getParent() != null) v.getParent().requestDisallowInterceptTouchEvent(true);
                        } else if (Math.abs(dx) > slop) {
                            horizontal = true;
                        }
                    }
                    if (vertical) {
                        float ty = Math.min(dy, U.dpf(a, 10));
                        slot.setTranslationY(ty);
                        float up = Math.max(0f, -ty);
                        slot.setAlpha(1f - U.clampf(up / (getHeight() * 0.55f), 0f, 0.85f));
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP: {
                    float vy = 0f;
                    if (vt != null) {
                        vt.addMovement(e);
                        vt.computeCurrentVelocity(1000);
                        vy = vt.getYVelocity();
                    }
                    recycle();
                    if (vertical) {
                        if (slot.getTranslationY() < -U.dpf(a, 110) || vy < -900f) {
                            a.haptic(AppSwitcher.this);
                            dismissSlot(slot, app, 0, true);
                        } else {
                            springBack(slot);
                        }
                    } else if (!horizontal) {
                        openApp(app, card);
                    }
                    vertical = false;
                    return true;
                }
                case MotionEvent.ACTION_CANCEL:
                    recycle();
                    if (vertical) springBack(slot);
                    vertical = false;
                    return true;
                default:
                    return true;
            }
        }
    }
}
