package com.senzaproject.splauncher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.text.TextPaint;
import android.view.View;
import android.view.ViewOutlineProvider;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

/**
 * Widget bawaan SPLauncher: digambar sendiri (tanpa AppWidgetProvider) dengan latar Liquid Glass.
 * Semua teks berbahasa Indonesia; pekan dimulai hari Senin.
 */
final class BuiltinWidgets {
    private BuiltinWidgets() {}

    /** Lebar penuh (mengikuti jumlah kolom saat widget ditambahkan). */
    static final int FULL = -1;

    static final String CLOCK_ANALOG = "clock_analog", CLOCK_DIGITAL = "clock_digital", DATE = "date",
            MONTH = "month", BATTERY = "battery", SEARCH = "search";

    static final String[] DAYS = {"Minggu", "Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu"};
    static final String[] MONTHS = {"Januari", "Februari", "Maret", "April", "Mei", "Juni", "Juli", "Agustus",
            "September", "Oktober", "November", "Desember"};
    /** Inisial hari, urut Senin..Minggu. */
    private static final String[] INITIALS = {"S", "S", "R", "K", "J", "S", "M"};

    static final class Spec {
        final String kind, title, desc;
        final int sw, sh;
        Spec(String kind, String title, String desc, int sw, int sh) {
            this.kind = kind; this.title = title; this.desc = desc; this.sw = sw; this.sh = sh;
        }
        int widthFor(int cols) { return sw == FULL ? cols : Math.min(sw, cols); }
        int heightFor(int rows) { return Math.min(sh, rows); }
    }

    static final Spec[] CATALOG = {
            new Spec(CLOCK_ANALOG, "Jam Analog", "Jarum jam, menit, dan detik", 2, 2),
            new Spec(CLOCK_ANALOG, "Jam Analog", "Jam dengan hari dan tanggal", 4, 2),
            new Spec(CLOCK_DIGITAL, "Jam Digital", "Waktu dan tanggal", 2, 2),
            new Spec(CLOCK_DIGITAL, "Jam Digital", "Waktu besar dengan zona waktu", 4, 2),
            new Spec(DATE, "Tanggal", "Hari dan tanggal hari ini", 2, 2),
            new Spec(DATE, "Tanggal", "Tanggal dengan pekan ini", 4, 2),
            new Spec(MONTH, "Kalender", "Kalender bulan ini", 4, 3),
            new Spec(MONTH, "Kalender", "Kalender bulan ini, ukuran besar", 4, 4),
            new Spec(BATTERY, "Baterai", "Level dan status pengisian", 2, 2),
            new Spec(BATTERY, "Baterai", "Level, suhu, dan tegangan", 4, 2),
            new Spec(SEARCH, "Pencarian", "Cari aplikasi dengan cepat", FULL, 1),
    };

    static List<Spec> specsFor(String kind) {
        ArrayList<Spec> out = new ArrayList<>();
        for (Spec s : CATALOG) if (s.kind.equals(kind)) out.add(s);
        return out;
    }

    static String titleOf(String kind) {
        for (Spec s : CATALOG) if (s.kind.equals(kind)) return s.title;
        return "Widget";
    }

    static String sizeLabel(int sw, int sh) { return sw + " × " + sh; }

    static Base create(Context c, LayoutStore.Item it, Prefs prefs, boolean dark) {
        int sw = it.spanW, sh = it.spanH;
        switch (it.wkind) {
            case CLOCK_ANALOG: return new AnalogClock(c, prefs, dark, sw, sh);
            case CLOCK_DIGITAL: return new DigitalClock(c, prefs, dark, sw, sh);
            case DATE: return new DateWidget(c, prefs, dark, sw, sh);
            case MONTH: return new MonthWidget(c, prefs, dark, sw, sh);
            case BATTERY: return new BatteryWidget(c, prefs, dark, sw, sh);
            case SEARCH: return new SearchWidget(c, prefs, dark, sw, sh);
            default: return new Missing(c, prefs, dark, sw, sh, "Widget tidak dikenal", "Versi widget ini tidak tersedia.");
        }
    }

    // ================================================================== dasar

    abstract static class Base extends View {
        final Prefs prefs;
        final boolean dark;
        final float dp, sp;
        final int spanW, spanH;
        final int fg, fg2, red;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        final RectF card = new RectF();
        private final Handler h = new Handler(Looper.getMainLooper());
        private boolean running;
        private final Runnable tick = new Runnable() {
            @Override public void run() { running = false; invalidate(); schedule(); }
        };

        Base(Context c, Prefs prefs, boolean dark, int sw, int sh) {
            super(c);
            this.prefs = prefs;
            this.dark = dark;
            this.spanW = sw;
            this.spanH = sh;
            dp = U.dpf(c, 1f);
            sp = U.sp(c, 1f);
            fg = dark ? 0xFFFFFFFF : 0xFF111114;
            fg2 = dark ? 0xB3FFFFFF : 0x99000000;
            red = dark ? 0xFFFF453A : 0xFFFF3B30;
            setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View v, android.graphics.Outline o) {
                    o.setRoundRect(Math.round(card.left), Math.round(card.top), Math.round(card.right),
                            Math.round(card.bottom), cornerRadius());
                }
            });
            setElevation(dp * 3f);
            setOutlineAmbientShadowColor(0x33000000);
            setOutlineSpotShadowColor(0x44000000);
            Glass.watch(this);
        }

        float cornerRadius() { return dp * 22f; }

        /** Area kartu di dalam view (mis. bilah pencarian lebih pendek dari selnya). */
        void layoutCard(float w, float h, RectF out) { out.set(0, 0, w, h); }

        /** Jeda (ms) menuju penggambaran ulang berikutnya; 0 = tanpa pewaktu. */
        long tickDelay() { return 0; }

        abstract void drawContent(Canvas c, float w, float h);

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            super.onSizeChanged(w, h, ow, oh);
            layoutCard(w, h, card);
            invalidateOutline();
        }

        @Override protected void onDraw(Canvas c) {
            float tint = Math.max(0.22f, prefs.glassOpacity() / 100f * 0.9f);
            Glass.paint(c, this, card.left, card.top, card.right, card.bottom, cornerRadius(), tint, dark);
            int s = c.save();
            c.translate(card.left, card.top);
            drawContent(c, card.width(), card.height());
            c.restoreToCount(s);
        }

        private void schedule() {
            long d = tickDelay();
            if (d > 0 && !running && isAttachedToWindow() && getWindowVisibility() == VISIBLE) {
                running = true;
                h.postDelayed(tick, d);
            }
        }

        private void stop() { h.removeCallbacks(tick); running = false; }

        @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); schedule(); }
        @Override protected void onDetachedFromWindow() { super.onDetachedFromWindow(); stop(); }
        @Override protected void onWindowVisibilityChanged(int v) {
            super.onWindowVisibilityChanged(v);
            if (v == VISIBLE) { invalidate(); schedule(); } else stop();
        }

        // ---- util gambar teks
        void text(Canvas c, String s, float x, float y, float size, int color, Paint.Align al, Typeface tf) {
            tp.setTypeface(tf);
            tp.setTextSize(size);
            tp.setColor(color);
            tp.setTextAlign(al);
            c.drawText(s, x, y, tp);
        }

        /** Seperti text() tetapi ukuran diperkecil agar muat di maxW. */
        void fitText(Canvas c, String s, float x, float y, float size, float maxW, int color, Paint.Align al, Typeface tf) {
            tp.setTypeface(tf);
            tp.setTextSize(size);
            float w = tp.measureText(s);
            if (w > maxW && w > 0) size = size * maxW / w;
            text(c, s, x, y, size, color, al, tf);
        }

        static long untilNextMinute() {
            long now = System.currentTimeMillis();
            return 60000L - (now % 60000L) + 40L;
        }

        static long untilNextSecond() {
            long now = System.currentTimeMillis();
            return 1000L - (now % 1000L) + 8L;
        }
    }

    private static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private static final Typeface BOLD = Typeface.create("sans-serif", Typeface.BOLD);
    private static final Typeface REGULAR = Typeface.create("sans-serif", Typeface.NORMAL);
    private static final Typeface LIGHT = Typeface.create("sans-serif-light", Typeface.NORMAL);

    private static String zoneName() {
        TimeZone tz = TimeZone.getDefault();
        String id = tz.getID();
        int i = id.lastIndexOf('/');
        String city = i >= 0 ? id.substring(i + 1).replace('_', ' ') : id;
        String abbr = tz.getDisplayName(tz.inDaylightTime(new java.util.Date()), TimeZone.SHORT);
        if (abbr != null && abbr.length() <= 5 && !abbr.startsWith("GMT")) return abbr + " · " + city;
        return city;
    }

    private static String twoDigits(int v) { return v < 10 ? "0" + v : String.valueOf(v); }

    // ================================================================== jam analog

    static final class AnalogClock extends Base {
        AnalogClock(Context c, Prefs p, boolean dark, int sw, int sh) { super(c, p, dark, sw, sh); }

        @Override long tickDelay() { return untilNextSecond(); }

        private void hand(Canvas c, float cx, float cy, float deg, float len, float tail, float width, int color) {
            int s = c.save();
            c.rotate(deg, cx, cy);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeWidth(width);
            p.setColor(color);
            c.drawLine(cx, cy + tail, cx, cy - len, p);
            c.restoreToCount(s);
        }

        @Override void drawContent(Canvas c, float w, float h) {
            Calendar cal = Calendar.getInstance();
            boolean wide = w > h * 1.5f;
            float pad = dp * 12f;
            float R = (Math.min(w, h) - 2 * pad) / 2f;
            float cx = wide ? h / 2f : w / 2f, cy = h / 2f;

            // piringan
            p.setStyle(Paint.Style.FILL);
            p.setColor(dark ? 0x40000000 : 0x66FFFFFF);
            c.drawCircle(cx, cy, R, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp);
            p.setColor(U.alpha(fg, 0.14f));
            c.drawCircle(cx, cy, R - dp * 0.5f, p);

            // tanda menit / jam
            p.setStrokeCap(Paint.Cap.ROUND);
            for (int i = 0; i < 60; i++) {
                if (i % 15 == 0) continue;
                boolean major = i % 5 == 0;
                float len = major ? R * 0.10f : R * 0.045f;
                double a = Math.toRadians(i * 6.0);
                float s = (float) Math.sin(a), co = (float) Math.cos(a);
                float r1 = R * 0.93f, r0 = r1 - len;
                p.setStrokeWidth(major ? dp * 2f : dp);
                p.setColor(U.alpha(fg, major ? 0.85f : 0.35f));
                c.drawLine(cx + s * r0, cy - co * r0, cx + s * r1, cy - co * r1, p);
            }
            // angka 12, 3, 6, 9
            String[] nums = {"12", "3", "6", "9"};
            tp.setTypeface(MEDIUM);
            tp.setTextSize(R * 0.27f);
            tp.setTextAlign(Paint.Align.CENTER);
            tp.setColor(fg);
            for (int k = 0; k < 4; k++) {
                double a = Math.toRadians(k * 90.0);
                float tr = R * 0.72f;
                float x = cx + (float) Math.sin(a) * tr;
                float y = cy - (float) Math.cos(a) * tr - (tp.ascent() + tp.descent()) / 2f;
                c.drawText(nums[k], x, y, tp);
            }

            int sec = cal.get(Calendar.SECOND);
            float minF = cal.get(Calendar.MINUTE) + sec / 60f;
            float hourF = (cal.get(Calendar.HOUR) % 12) + minF / 60f;
            p.setShadowLayer(dp * 2f, 0, dp, 0x55000000);
            hand(c, cx, cy, hourF * 30f, R * 0.50f, R * 0.08f, R * 0.075f, fg);
            hand(c, cx, cy, minF * 6f, R * 0.78f, R * 0.08f, R * 0.052f, fg);
            hand(c, cx, cy, sec * 6f, R * 0.86f, R * 0.18f, R * 0.022f, 0xFFFF9F0A);
            p.clearShadowLayer();
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFFF9F0A);
            c.drawCircle(cx, cy, R * 0.065f, p);
            p.setColor(dark ? 0xFF1C1C1E : 0xFFFFFFFF);
            c.drawCircle(cx, cy, R * 0.024f, p);

            if (wide) {
                float tx = h + dp * 2f;
                float aw = w - tx - pad;
                int dow = cal.get(Calendar.DAY_OF_WEEK) - 1;
                float mid = h / 2f;
                fitText(c, DAYS[dow].toUpperCase(), tx, mid - 14 * sp, 13 * sp, aw, red, Paint.Align.LEFT, BOLD);
                fitText(c, cal.get(Calendar.DAY_OF_MONTH) + " " + MONTHS[cal.get(Calendar.MONTH)], tx, mid + 12 * sp,
                        24 * sp, aw, fg, Paint.Align.LEFT, MEDIUM);
                fitText(c, zoneName(), tx, mid + 34 * sp, 12 * sp, aw, fg2, Paint.Align.LEFT, REGULAR);
            }
        }
    }

    // ================================================================== jam digital

    static final class DigitalClock extends Base {
        DigitalClock(Context c, Prefs p, boolean dark, int sw, int sh) { super(c, p, dark, sw, sh); }

        @Override long tickDelay() { return untilNextMinute(); }

        @Override void drawContent(Canvas c, float w, float h) {
            Calendar cal = Calendar.getInstance();
            boolean h24 = android.text.format.DateFormat.is24HourFormat(getContext());
            int hour = cal.get(h24 ? Calendar.HOUR_OF_DAY : Calendar.HOUR);
            if (!h24 && hour == 0) hour = 12;
            String time = (h24 ? twoDigits(hour) : String.valueOf(hour)) + ":" + twoDigits(cal.get(Calendar.MINUTE));
            float pad = dp * 14f;
            float aw = w - 2 * pad;
            int dow = cal.get(Calendar.DAY_OF_WEEK) - 1;
            boolean wide = w > h * 1.5f;
            float size = Math.min(wide ? h * 0.46f : w * 0.34f, 84 * sp);
            tp.setTypeface(LIGHT);
            tp.setTextSize(size);
            float tw = tp.measureText(time);
            if (tw > aw) size = size * aw / tw;
            float mid = h / 2f;
            float timeBase = mid + size * 0.30f;
            text(c, time, w / 2f, timeBase, size, fg, Paint.Align.CENTER, LIGHT);
            if (!h24) {
                float ts = Math.max(11 * sp, size * 0.22f);
                tp.setTextSize(size);
                float half = tp.measureText(time) / 2f;
                text(c, cal.get(Calendar.AM_PM) == Calendar.AM ? "AM" : "PM", w / 2f + half + dp * 3f, timeBase - size * 0.46f,
                        ts, fg2, Paint.Align.LEFT, MEDIUM);
            }
            fitText(c, DAYS[dow].toUpperCase(), w / 2f, timeBase - size * 0.88f, 13 * sp, aw, red, Paint.Align.CENTER, BOLD);
            String date = cal.get(Calendar.DAY_OF_MONTH) + " " + MONTHS[cal.get(Calendar.MONTH)] + (wide ? " " + cal.get(Calendar.YEAR) : "");
            fitText(c, date, w / 2f, timeBase + 24 * sp, 15 * sp, aw, fg, Paint.Align.CENTER, MEDIUM);
            if (wide) fitText(c, zoneName(), w / 2f, timeBase + 44 * sp, 12 * sp, aw, fg2, Paint.Align.CENTER, REGULAR);
        }
    }

    // ================================================================== tanggal

    static final class DateWidget extends Base {
        DateWidget(Context c, Prefs p, boolean dark, int sw, int sh) { super(c, p, dark, sw, sh); }

        @Override long tickDelay() { return untilNextMinute(); }

        @Override void drawContent(Canvas c, float w, float h) {
            Calendar cal = Calendar.getInstance();
            boolean wide = w > h * 1.5f;
            float pad = dp * 16f;
            float leftW = wide ? Math.min(w * 0.38f, h * 1.05f) : w;
            int dow = cal.get(Calendar.DAY_OF_WEEK) - 1;
            fitText(c, DAYS[dow].toUpperCase(), pad, pad + 13 * sp, 13 * sp, leftW - 2 * pad, red, Paint.Align.LEFT, BOLD);
            float numSize = Math.min(leftW * 0.50f, h * 0.40f);
            text(c, String.valueOf(cal.get(Calendar.DAY_OF_MONTH)), pad - dp, pad + 13 * sp + numSize * 0.98f, numSize, fg,
                    Paint.Align.LEFT, LIGHT);
            fitText(c, MONTHS[cal.get(Calendar.MONTH)] + " " + cal.get(Calendar.YEAR), pad, h - pad, 13 * sp,
                    leftW - 2 * pad, fg2, Paint.Align.LEFT, MEDIUM);
            if (!wide) return;

            // pembatas + pekan ini
            p.setStyle(Paint.Style.FILL);
            p.setColor(U.alpha(fg, 0.14f));
            c.drawRect(leftW, pad, leftW + dp, h - pad, p);
            float x0 = leftW + dp * 12f, x1 = w - pad;
            float colW = (x1 - x0) / 7f;
            int lead = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7; // 0 = Senin
            Calendar day = (Calendar) cal.clone();
            day.add(Calendar.DAY_OF_MONTH, -lead);
            float cy = h / 2f;
            text(c, "PEKAN INI", x0, cy - 36 * sp, 11 * sp, fg2, Paint.Align.LEFT, BOLD);
            for (int i = 0; i < 7; i++) {
                float cx = x0 + colW * (i + 0.5f);
                boolean today = i == lead;
                text(c, INITIALS[i], cx, cy - 6 * sp, 12 * sp, i == 6 ? U.alpha(red, 0.9f) : fg2, Paint.Align.CENTER, MEDIUM);
                if (today) {
                    p.setColor(red);
                    c.drawCircle(cx, cy + 15 * sp, Math.min(colW * 0.46f, 17 * sp), p);
                }
                text(c, String.valueOf(day.get(Calendar.DAY_OF_MONTH)), cx, cy + 20 * sp, 15 * sp,
                        today ? 0xFFFFFFFF : fg, Paint.Align.CENTER, today ? BOLD : MEDIUM);
                day.add(Calendar.DAY_OF_MONTH, 1);
            }
        }
    }

    // ================================================================== kalender bulanan

    static final class MonthWidget extends Base {
        MonthWidget(Context c, Prefs p, boolean dark, int sw, int sh) { super(c, p, dark, sw, sh); }

        @Override long tickDelay() { return untilNextMinute(); }

        @Override void drawContent(Canvas c, float w, float h) {
            Calendar now = Calendar.getInstance();
            int year = now.get(Calendar.YEAR), month = now.get(Calendar.MONTH), today = now.get(Calendar.DAY_OF_MONTH);
            Calendar first = Calendar.getInstance();
            first.set(year, month, 1);
            int lead = (first.get(Calendar.DAY_OF_WEEK) + 5) % 7;
            int days = first.getActualMaximum(Calendar.DAY_OF_MONTH);
            int rows = (lead + days + 6) / 7;

            float pad = dp * 14f;
            float headerBase = pad + 17 * sp;
            text(c, MONTHS[month].toUpperCase(), pad, headerBase, 17 * sp, red, Paint.Align.LEFT, BOLD);
            text(c, String.valueOf(year), w - pad, headerBase, 15 * sp, fg2, Paint.Align.RIGHT, MEDIUM);

            float colW = (w - 2 * pad) / 7f;
            float wdBase = headerBase + 26 * sp;
            for (int i = 0; i < 7; i++) {
                text(c, INITIALS[i], pad + colW * (i + 0.5f), wdBase, 12 * sp, i == 6 ? U.alpha(red, 0.9f) : fg2,
                        Paint.Align.CENTER, MEDIUM);
            }
            float gridTop = wdBase + 8 * sp;
            float rowH = Math.min((h - gridTop - pad * 0.7f) / rows, 44 * dp);
            float numSize = Math.min(15 * sp, rowH * 0.42f);
            tp.setTypeface(MEDIUM);
            tp.setTextSize(numSize);
            for (int d = 1; d <= days; d++) {
                int idx = lead + d - 1;
                int col = idx % 7, row = idx / 7;
                float cx = pad + colW * (col + 0.5f);
                float cy = gridTop + rowH * (row + 0.5f);
                boolean isToday = d == today;
                if (isToday) {
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(red);
                    c.drawCircle(cx, cy, Math.min(colW, rowH) * 0.43f, p);
                }
                text(c, String.valueOf(d), cx, cy - (tp.ascent() + tp.descent()) / 2f, numSize,
                        isToday ? 0xFFFFFFFF : (col == 6 ? U.alpha(red, 0.85f) : fg), Paint.Align.CENTER,
                        isToday ? BOLD : MEDIUM);
            }
        }
    }

    // ================================================================== baterai

    static final class BatteryWidget extends Base {
        private int level = -1, status = BatteryManager.BATTERY_STATUS_UNKNOWN, plugged, temp = Integer.MIN_VALUE, volt;
        private final Path bolt = new Path();
        private final RectF arc = new RectF();
        private boolean registered;
        private final BroadcastReceiver rx = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) { read(i); invalidate(); }
        };

        BatteryWidget(Context c, Prefs p, boolean dark, int sw, int sh) { super(c, p, dark, sw, sh); }

        private void read(Intent i) {
            if (i == null) return;
            int l = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), sc = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            level = (l < 0 || sc <= 0) ? -1 : Math.round(l * 100f / sc);
            status = i.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN);
            plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
            temp = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
            volt = i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
        }

        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            try {
                Intent i = getContext().registerReceiver(rx, new IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                        Context.RECEIVER_NOT_EXPORTED);
                registered = true;
                read(i);
            } catch (Throwable ignored) { }
        }

        @Override protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (registered) {
                try { getContext().unregisterReceiver(rx); } catch (Throwable ignored) { }
                registered = false;
            }
        }

        private boolean charging() {
            return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL;
        }

        private String statusText() {
            if (status == BatteryManager.BATTERY_STATUS_FULL) return "Penuh";
            if (charging()) {
                String src = (plugged & BatteryManager.BATTERY_PLUGGED_WIRELESS) != 0 ? "nirkabel"
                        : (plugged & BatteryManager.BATTERY_PLUGGED_USB) != 0 ? "USB"
                        : (plugged & BatteryManager.BATTERY_PLUGGED_AC) != 0 ? "adaptor" : null;
                return src == null ? "Mengisi daya" : "Mengisi · " + src;
            }
            return "Tidak mengisi";
        }

        private void drawBolt(Canvas c, float cx, float cy, float s, int color) {
            bolt.reset();
            bolt.moveTo(cx + 0.10f * s, cy - 0.50f * s);
            bolt.lineTo(cx - 0.34f * s, cy + 0.08f * s);
            bolt.lineTo(cx - 0.04f * s, cy + 0.08f * s);
            bolt.lineTo(cx - 0.12f * s, cy + 0.50f * s);
            bolt.lineTo(cx + 0.34f * s, cy - 0.10f * s);
            bolt.lineTo(cx + 0.04f * s, cy - 0.10f * s);
            bolt.close();
            p.setStyle(Paint.Style.FILL);
            p.setColor(color);
            c.drawPath(bolt, p);
        }

        @Override void drawContent(Canvas c, float w, float h) {
            boolean wide = w > h * 1.5f;
            float pad = dp * 14f;
            float R = (Math.min(w, h) - 2 * pad) / 2f;
            if (!wide) R = Math.min(R, w * 0.34f);
            float cx = wide ? h / 2f : w / 2f, cy = wide ? h / 2f : h * 0.43f;
            float stroke = R * 0.20f;
            int lv = Math.max(0, level);
            int col = charging() ? 0xFF30D158 : (lv <= 20 ? 0xFFFF453A : (lv <= 30 ? 0xFFFFD60A : 0xFF30D158));
            arc.set(cx - R + stroke / 2f, cy - R + stroke / 2f, cx + R - stroke / 2f, cy + R - stroke / 2f);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(stroke);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(U.alpha(fg, 0.14f));
            c.drawArc(arc, 0, 360, false, p);
            if (level >= 0) {
                p.setColor(col);
                c.drawArc(arc, -90, Math.max(3f, 360f * lv / 100f), false, p);
            }
            String pct = level < 0 ? "--" : lv + "%";
            float ps = R * 0.50f;
            fitText(c, pct, cx, cy + ps * 0.35f, ps, R * 1.2f, fg, Paint.Align.CENTER, MEDIUM);
            if (charging()) drawBolt(c, cx, cy - R * 0.50f, R * 0.30f, col);

            if (wide) {
                float tx = h + dp * 2f, aw = w - tx - pad;
                float mid = h / 2f;
                fitText(c, "BATERAI", tx, mid - 26 * sp, 12 * sp, aw, red, Paint.Align.LEFT, BOLD);
                fitText(c, statusText(), tx, mid - 2 * sp, 18 * sp, aw, fg, Paint.Align.LEFT, MEDIUM);
                String t = temp == Integer.MIN_VALUE ? "" : String.format(java.util.Locale.US, "%.1f°C", temp / 10f).replace('.', ',');
                String v = volt > 0 ? String.format(java.util.Locale.US, "%.2f V", volt / 1000f).replace('.', ',') : "";
                String line = (t.isEmpty() ? "" : "Suhu " + t) + (!t.isEmpty() && !v.isEmpty() ? " · " : "") + v;
                fitText(c, line, tx, mid + 22 * sp, 13 * sp, aw, fg2, Paint.Align.LEFT, REGULAR);
            } else {
                fitText(c, statusText(), w / 2f, h - pad - 2 * sp, 13 * sp, w - 2 * pad, fg2, Paint.Align.CENTER, MEDIUM);
            }
        }
    }

    // ================================================================== pencarian

    static final class SearchWidget extends Base {
        SearchWidget(Context c, Prefs p, boolean dark, int sw, int sh) { super(c, p, dark, sw, sh); }

        @Override void layoutCard(float w, float h, RectF out) {
            float ch = Math.min(h, dp * 50f);
            out.set(0, (h - ch) / 2f, w, (h + ch) / 2f);
        }

        @Override float cornerRadius() { return card.height() / 2f; }

        @Override void drawContent(Canvas c, float w, float h) {
            float cy = h / 2f;
            float r = dp * 6.5f, x = dp * 20f;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeWidth(dp * 2f);
            p.setColor(fg2);
            c.drawCircle(x + r, cy - dp, r, p);
            c.drawLine(x + r * 1.75f, cy - dp + r * 0.75f, x + r * 2.45f, cy + r * 1.45f, p);
            tp.setTypeface(REGULAR);
            tp.setTextSize(16 * sp);
            text(c, "Cari", x + r * 2.45f + dp * 12f, cy - (tp.ascent() + tp.descent()) / 2f, 16 * sp, fg2,
                    Paint.Align.LEFT, REGULAR);
        }
    }

    // ================================================================== placeholder

    static final class Missing extends Base {
        private final String title, sub;

        Missing(Context c, Prefs p, boolean dark, int sw, int sh, String title, String sub) {
            super(c, p, dark, sw, sh);
            this.title = title;
            this.sub = sub;
        }

        @Override void drawContent(Canvas c, float w, float h) {
            float pad = dp * 16f;
            float aw = w - 2 * pad;
            float cy = h / 2f;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp * 2f);
            p.setColor(fg2);
            float r = dp * 11f;
            c.drawCircle(w / 2f, cy - 22 * sp, r, p);
            p.setStyle(Paint.Style.FILL);
            c.drawRect(w / 2f - dp, cy - 22 * sp - r * 0.55f, w / 2f + dp, cy - 22 * sp + r * 0.1f, p);
            c.drawCircle(w / 2f, cy - 22 * sp + r * 0.5f, dp * 1.3f, p);
            fitText(c, title, w / 2f, cy + 8 * sp, 15 * sp, aw, fg, Paint.Align.CENTER, MEDIUM);
            tp.setTypeface(REGULAR);
            tp.setTextSize(12 * sp);
            tp.setColor(fg2);
            tp.setTextAlign(Paint.Align.CENTER);
            android.text.StaticLayout sl = android.text.StaticLayout.Builder.obtain(sub, 0, sub.length(), tp, (int) Math.max(1, aw))
                    .setAlignment(android.text.Layout.Alignment.ALIGN_CENTER).setMaxLines(3)
                    .setEllipsize(android.text.TextUtils.TruncateAt.END).build();
            int s = c.save();
            c.translate(pad, cy + 16 * sp);
            sl.draw(c);
            c.restoreToCount(s);
        }
    }
}
