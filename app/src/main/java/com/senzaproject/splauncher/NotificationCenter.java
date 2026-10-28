package com.senzaproject.splauncher;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.List;

/**
 * Pusat Notifikasi gaya layar kunci iOS: jam besar + tanggal + kartu notifikasi (geser untuk hapus,
 * ketuk untuk membuka). Tarik dari tepi atas-kiri/tengah Home untuk membuka, geser ke atas untuk menutup.
 */
final class NotificationCenter extends TopSheet implements NotificationService.Listener {
    interface Actions {
        void openSettingsForAccess();
        AppRepo.AppInfo appFor(String pkg);
        Prefs prefs();
        boolean dark();
    }

    private final Actions act;
    private final LinearLayout list;
    private final ScrollView scroll;
    private final int insTop, insBottom;

    NotificationCenter(Context c, Host host, Actions act, int insTop, int insBottom) {
        super(c, host, 0.46f);
        this.act = act;
        this.insTop = insTop;
        this.insBottom = insBottom;

        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(U.dp(c, 16), insTop + U.dp(c, 18), U.dp(c, 16), insBottom + U.dp(c, 8));
        body.addView(col, new LayoutParams(-1, -1));

        // jam & tanggal
        TextClock date = new TextClock(c);
        date.setFormat12Hour("EEEE, d MMMM");
        date.setFormat24Hour("EEEE, d MMMM");
        date.setTextColor(0xE6FFFFFF);
        date.setTextSize(17f);
        date.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        date.setGravity(Gravity.CENTER);
        date.setShadowLayer(U.dpf(c, 3), 0, U.dpf(c, 1), 0x66000000);
        col.addView(date, new LinearLayout.LayoutParams(-1, -2));
        TextClock clock = new TextClock(c);
        clock.setFormat12Hour("h:mm");
        clock.setFormat24Hour("HH:mm");
        clock.setTextColor(0xFFFFFFFF);
        clock.setTextSize(78f);
        clock.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        clock.setGravity(Gravity.CENTER);
        clock.setIncludeFontPadding(false);
        clock.setShadowLayer(U.dpf(c, 4), 0, U.dpf(c, 1), 0x55000000);
        col.addView(clock, new LinearLayout.LayoutParams(-1, -2));

        // judul bagian + Hapus Semua
        LinearLayout head = new LinearLayout(c);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(U.dp(c, 6), U.dp(c, 18), U.dp(c, 6), U.dp(c, 8));
        TextView t = new TextView(c);
        t.setText("Notifikasi");
        t.setTextColor(0xFFFFFFFF);
        t.setTextSize(19f);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        head.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView clear = new TextView(c);
        clear.setText("Hapus Semua");
        clear.setTextColor(0xFFFFFFFF);
        clear.setTextSize(13f);
        clear.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        clear.setPadding(U.dp(c, 14), U.dp(c, 7), U.dp(c, 14), U.dp(c, 7));
        GradientDrawable cb = new GradientDrawable();
        cb.setColor(0x40FFFFFF);
        cb.setCornerRadius(U.dpf(c, 16));
        clear.setBackground(cb);
        clear.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { NotificationService.dismissAll(); }
        });
        head.addView(clear);
        col.addView(head, new LinearLayout.LayoutParams(-1, -2));

        scroll = new ScrollView(c);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(OVER_SCROLL_NEVER);
        list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new ScrollView.LayoutParams(-1, -2));
        col.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        // pegangan bawah
        View pill = new View(c);
        GradientDrawable pg = new GradientDrawable();
        pg.setColor(0x99FFFFFF);
        pg.setCornerRadius(U.dpf(c, 3));
        pill.setBackground(pg);
        pill.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { close(); } });
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(U.dp(c, 140), U.dp(c, 6));
        pp.gravity = Gravity.CENTER_HORIZONTAL;
        pp.topMargin = U.dp(c, 10);
        pp.bottomMargin = U.dp(c, 4);
        col.addView(pill, pp);
        rebuild();
    }

    @Override protected boolean closeOnBackgroundTap() { return false; }

    @Override protected boolean canDragClose(float x, float y) {
        // geser ke atas menutup bila daftar tidak bisa digulir lagi ke bawah, atau dimulai dari bagian bawah layar
        return !scroll.canScrollVertically(1) || y > getHeight() - U.dp(getContext(), 150);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        NotificationService.addListener(this);
        NotificationService.ensureBound(getContext());
        rebuild();
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        NotificationService.removeListener(this);
    }

    @Override public void onNotificationsChanged() { rebuild(); }

    // ------------------------------------------------------------------ isi daftar

    private void rebuild() {
        final Context c = getContext();
        list.removeAllViews();
        if (!NotificationService.isEnabled(c)) {
            list.addView(permissionCard(c), new LinearLayout.LayoutParams(-1, -2));
            return;
        }
        List<StatusBarNotification> cards = NotificationService.cards(c.getPackageName());
        if (cards.isEmpty()) {
            TextView e = new TextView(c);
            e.setText(NotificationService.connected() ? "Tidak ada notifikasi" : "Menghubungkan ke layanan notifikasi…");
            e.setTextColor(0xB3FFFFFF);
            e.setTextSize(16f);
            e.setGravity(Gravity.CENTER);
            e.setPadding(0, U.dp(c, 48), 0, 0);
            list.addView(e, new LinearLayout.LayoutParams(-1, -2));
            return;
        }
        int n = 0;
        for (StatusBarNotification sb : cards) {
            if (++n > 60) break;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = U.dp(c, 8);
            list.addView(new Card(c, sb), lp);
        }
    }

    private View permissionCard(Context c) {
        GlassLayout g = new GlassLayout(c, 24, 0.32f, true);
        g.setOrientation(LinearLayout.VERTICAL);
        g.setElevation(0);
        g.setPadding(U.dp(c, 18), U.dp(c, 18), U.dp(c, 18), U.dp(c, 18));
        ImageView ic = new ImageView(c);
        ic.setImageDrawable(tinted(c, R.drawable.sym_bell, 0xFFFFFFFF));
        g.addView(ic, new LinearLayout.LayoutParams(U.dp(c, 30), U.dp(c, 30)));
        TextView t = new TextView(c);
        t.setText("Aktifkan akses notifikasi");
        t.setTextColor(0xFFFFFFFF);
        t.setTextSize(17f);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setPadding(0, U.dp(c, 10), 0, U.dp(c, 4));
        g.addView(t);
        TextView d = new TextView(c);
        d.setText("Izinkan SPLauncher membaca notifikasi agar tampil di sini, lengkap dengan badge pada ikon aplikasi dan kontrol media di Control Center.");
        d.setTextColor(0xCCFFFFFF);
        d.setTextSize(14f);
        g.addView(d);
        TextView b = new TextView(c);
        b.setText("Buka Pengaturan");
        b.setTextColor(0xFFFFFFFF);
        b.setTextSize(15f);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setGravity(Gravity.CENTER);
        GradientDrawable bb = new GradientDrawable();
        bb.setColor(0xFF0A84FF);
        bb.setCornerRadius(U.dpf(c, 14));
        b.setBackground(bb);
        b.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { act.openSettingsForAccess(); }
        });
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, U.dp(c, 46));
        bp.topMargin = U.dp(c, 14);
        g.addView(b, bp);
        return g;
    }

    static android.graphics.drawable.Drawable tinted(Context c, int res, int color) {
        android.graphics.drawable.Drawable d = c.getDrawable(res).mutate();
        d.setTint(color);
        return d;
    }

    // ------------------------------------------------------------------ kartu notifikasi

    private final class Card extends GlassLayout {
        private final StatusBarNotification sb;
        private final int slop2;
        private float downX, downY;
        private boolean swiping;
        private VelocityTracker cvt;

        Card(final Context c, StatusBarNotification s) {
            super(c, 22, 0.34f, true);
            sb = s;
            slop2 = ViewConfiguration.get(c).getScaledTouchSlop();
            setOrientation(LinearLayout.HORIZONTAL);
            setElevation(0);
            setPadding(U.dp(c, 12), U.dp(c, 12), U.dp(c, 14), U.dp(c, 12));
            setClickable(true);

            ImageView icon = new ImageView(c);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            bindIcon(icon, s.getPackageName());
            addView(icon, new LinearLayout.LayoutParams(U.dp(c, 40), U.dp(c, 40)));

            LinearLayout col = new LinearLayout(c);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setPadding(U.dp(c, 12), 0, 0, 0);
            addView(col, new LinearLayout.LayoutParams(0, -2, 1f));

            Notification n = s.getNotification();
            android.os.Bundle ex = n.extras;
            CharSequence title = ex == null ? null : ex.getCharSequence(Notification.EXTRA_TITLE);
            CharSequence text = ex == null ? null : ex.getCharSequence(Notification.EXTRA_BIG_TEXT);
            if (TextUtils.isEmpty(text) && ex != null) text = ex.getCharSequence(Notification.EXTRA_TEXT);
            if (TextUtils.isEmpty(title)) title = appLabel(c, s.getPackageName());
            if (TextUtils.isEmpty(text) && n.tickerText != null) text = n.tickerText;

            LinearLayout top = new LinearLayout(c);
            top.setOrientation(LinearLayout.HORIZONTAL);
            TextView tt = new TextView(c);
            tt.setText(title);
            tt.setTextColor(0xFFFFFFFF);
            tt.setTextSize(15f);
            tt.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            tt.setSingleLine(true);
            tt.setEllipsize(TextUtils.TruncateAt.END);
            top.addView(tt, new LinearLayout.LayoutParams(0, -2, 1f));
            TextView tm = new TextView(c);
            tm.setText(DateUtils.getRelativeTimeSpanString(s.getPostTime(), System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE));
            tm.setTextColor(0x99FFFFFF);
            tm.setTextSize(12f);
            tm.setPadding(U.dp(c, 8), 0, 0, 0);
            top.addView(tm);
            col.addView(top, new LinearLayout.LayoutParams(-1, -2));

            CharSequence app = appLabel(c, s.getPackageName());
            if (!TextUtils.isEmpty(text)) {
                TextView tx = new TextView(c);
                tx.setText(text);
                tx.setTextColor(0xE6FFFFFF);
                tx.setTextSize(14f);
                tx.setMaxLines(3);
                tx.setEllipsize(TextUtils.TruncateAt.END);
                col.addView(tx, new LinearLayout.LayoutParams(-1, -2));
            }
            if (!TextUtils.equals(title, app)) {
                TextView ap = new TextView(c);
                ap.setText(app);
                ap.setTextColor(0x80FFFFFF);
                ap.setTextSize(11.5f);
                ap.setSingleLine(true);
                ap.setPadding(0, U.dp(c, 3), 0, 0);
                col.addView(ap, new LinearLayout.LayoutParams(-1, -2));
            }
            setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { open(); } });
        }

        private void bindIcon(final ImageView iv, String pkg) {
            Context c = getContext();
            AppRepo.AppInfo a = act.appFor(pkg);
            int sz = U.dp(c, 40);
            if (a != null) {
                Bitmap b = IconFactory.peek(a, sz, act.prefs(), act.dark());
                if (b != null) { iv.setImageBitmap(b); return; }
                IconFactory.load(a, sz, act.prefs(), act.dark(), new IconFactory.Callback() {
                    @Override public void onIcon(Bitmap bm) { iv.setImageBitmap(bm); }
                });
            } else {
                GradientDrawable g = new GradientDrawable();
                g.setColor(0xFF8E8E93);
                g.setCornerRadius(U.dpf(c, 10));
                iv.setBackground(g);
                iv.setPadding(U.dp(c, 8), U.dp(c, 8), U.dp(c, 8), U.dp(c, 8));
                iv.setImageDrawable(tinted(c, R.drawable.sym_bell, 0xFFFFFFFF));
            }
        }

        private void open() {
            Notification n = sb.getNotification();
            PendingIntent pi = n.contentIntent;
            if (pi != null) {
                try { pi.send(); } catch (Throwable ignored) { }
                if ((n.flags & Notification.FLAG_AUTO_CANCEL) != 0) NotificationService.dismiss(sb.getKey());
            }
            NotificationCenter.this.close();
        }

        // geser horizontal untuk menghapus
        @Override public boolean onInterceptTouchEvent(MotionEvent e) {
            if (!sb.isClearable()) return false;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getX(); downY = e.getY(); swiping = false;
                    break;
                case MotionEvent.ACTION_MOVE: {
                    float dx = e.getX() - downX, dy = e.getY() - downY;
                    if (!swiping && Math.abs(dx) > slop2 * 1.5f && Math.abs(dx) > Math.abs(dy) * 1.5f) {
                        swiping = true;
                        if (cvt != null) cvt.recycle();
                        cvt = VelocityTracker.obtain();
                        ViewGroup p = (ViewGroup) getParent();
                        while (p != null) { p.requestDisallowInterceptTouchEvent(true); p = p.getParent() instanceof ViewGroup ? (ViewGroup) p.getParent() : null; }
                        return true;
                    }
                    break;
                }
            }
            return false;
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (!swiping) return super.onTouchEvent(e);
            if (cvt == null) cvt = VelocityTracker.obtain();
            cvt.addMovement(e);
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_MOVE: {
                    float dx = e.getX() - downX;
                    setTranslationX(dx);
                    setAlpha(1f - Math.min(1f, Math.abs(dx) / (getWidth() * 0.9f)));
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    cvt.computeCurrentVelocity(1000);
                    float vx = cvt.getXVelocity();
                    cvt.recycle(); cvt = null;
                    swiping = false;
                    float dx = getTranslationX();
                    boolean gone = e.getActionMasked() == MotionEvent.ACTION_UP
                            && (Math.abs(dx) > getWidth() * 0.38f || Math.abs(vx) > 1200f);
                    if (gone) {
                        float to = (dx != 0 ? Math.signum(dx) : Math.signum(vx)) * getWidth();
                        animate().translationX(to).alpha(0f).setDuration(150).withEndAction(new Runnable() {
                            @Override public void run() {
                                NotificationService.dismiss(sb.getKey());
                                ViewGroup p = (ViewGroup) getParent();
                                if (p != null) p.removeView(Card.this);
                            }
                        }).start();
                    } else {
                        animate().translationX(0f).alpha(1f).setDuration(160).start();
                    }
                    return true;
                }
            }
            return true;
        }
    }

    private static CharSequence appLabel(Context c, String pkg) {
        try {
            android.content.pm.PackageManager pm = c.getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0));
        } catch (Throwable t) { return pkg; }
    }

    static Intent accessSettingsIntent() {
        return new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
    }
}
