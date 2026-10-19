package com.senzaproject.splauncher;

import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.AlarmClock;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/**
 * Control Center gaya iOS. Tarik ke bawah dari tepi atas-kanan Home.
 * Catatan platform: Android 10+ tidak mengizinkan aplikasi biasa mengubah Wi-Fi / Bluetooth / data seluler / mode
 * pesawat secara langsung, jadi ubin tersebut menampilkan status dan membuka panel sistem (kecuali bila SP Launcher
 * dipasang sebagai aplikasi sistem, maka percobaan toggle langsung dilakukan lebih dulu).
 * Senter, volume, kecerahan (izin "Ubah setelan sistem"), kunci rotasi, Fokus (izin Jangan Ganggu) dan kontrol media
 * (izin akses notifikasi) bekerja langsung.
 */
final class ControlCenter extends TopSheet {
    interface Actions {
        /** Buka activity; true bila berhasil. */
        boolean launch(Intent i);
        void toast(String s);
        void requestWriteSettings();
    }

    private static final int BLUE = 0xFF0A84FF, GREEN = 0xFF30D158, ORANGE = 0xFFFF9F0A, RED = 0xFFFF453A,
            PURPLE = 0xFF5E5CE6, WHITE = 0xF2FFFFFF, DARKGLYPH = 0xFF2C2C2E;

    private final Actions act;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ScrollView scroll;

    private Tile airplane, data, wifi, bluetooth, rotation, focus, torch, timer, calc, camera, recorder, settings;
    private VSlider brightness, volume;
    private MediaCard media;

    private CameraManager camMgr;
    private String torchId;
    private boolean torchOn;
    private CameraManager.TorchCallback torchCb;
    private boolean askedWrite;

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            refresh();
            main.postDelayed(this, 700);
        }
    };

    ControlCenter(Context c, Host host, Actions act, int screenW, int insTop, int insBottom) {
        super(c, host, 0.50f);
        this.act = act;

        int margin = U.dp(c, 20), gap = U.dp(c, 12);
        int u = Math.max(U.dp(c, 56), (Math.min(screenW, U.dp(c, 460)) - 2 * margin - 3 * gap) / 4);
        int gridW = 4 * u + 3 * gap;

        scroll = new ScrollView(c);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(OVER_SCROLL_NEVER);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, insTop + U.dp(c, 14), 0, insBottom + U.dp(c, 24));
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(gridW, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        body.addView(scroll, sp);

        FrameLayout grid = new FrameLayout(c);
        grid.setClickable(true);
        grid.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { close(); } });
        scroll.addView(grid, new ScrollView.LayoutParams(gridW, 5 * u + 4 * gap));

        // ---- kluster konektivitas (2x2)
        GlassLayout cluster = new GlassLayout(c, 28, 0.30f, true);
        cluster.setOrientation(LinearLayout.VERTICAL);
        cluster.setElevation(0);
        int cw = 2 * u + gap, cp = U.dp(c, 12);
        int d = (cw - 3 * cp) / 2;
        cluster.setPadding(cp, cp, cp, cp);
        airplane = new Tile(c, R.drawable.sym_airplane, ORANGE, 0xFFFFFFFF, true);
        data = new Tile(c, R.drawable.sym_cellular, GREEN, 0xFFFFFFFF, true);
        wifi = new Tile(c, R.drawable.sym_wifi, BLUE, 0xFFFFFFFF, true);
        bluetooth = new Tile(c, R.drawable.sym_bluetooth, BLUE, 0xFFFFFFFF, true);
        cluster.addView(row(c, airplane, data, d, cp), new LinearLayout.LayoutParams(-1, d));
        LinearLayout.LayoutParams r2 = new LinearLayout.LayoutParams(-1, d);
        r2.topMargin = cp;
        cluster.addView(row(c, wifi, bluetooth, d, cp), r2);
        place(grid, cluster, 0, 0, 2, 2, u, gap);

        // ---- media (2x2)
        media = new MediaCard(c);
        place(grid, media, 2, 0, 2, 2, u, gap);

        // ---- 4 ubin kecil (kiri) + 2 slider (kanan)
        rotation = new Tile(c, R.drawable.sym_rotation_lock, RED, 0xFFFFFFFF, false);
        focus = new Tile(c, R.drawable.sym_moon, PURPLE, 0xFFFFFFFF, false);
        torch = new Tile(c, R.drawable.sym_flashlight, WHITE, DARKGLYPH, false);
        timer = new Tile(c, R.drawable.sym_timer, 0, 0xFFFFFFFF, false);
        place(grid, rotation, 0, 2, 1, 1, u, gap);
        place(grid, focus, 1, 2, 1, 1, u, gap);
        place(grid, torch, 0, 3, 1, 1, u, gap);
        place(grid, timer, 1, 3, 1, 1, u, gap);
        brightness = new VSlider(c, R.drawable.sym_brightness);
        volume = new VSlider(c, R.drawable.sym_volume);
        place(grid, brightness, 2, 2, 1, 2, u, gap);
        place(grid, volume, 3, 2, 1, 2, u, gap);

        // ---- baris bawah
        calc = new Tile(c, R.drawable.sym_calculator, 0, 0xFFFFFFFF, false);
        camera = new Tile(c, R.drawable.sym_camera, 0, 0xFFFFFFFF, false);
        recorder = new Tile(c, R.drawable.sym_recorder, 0, 0xFFFFFFFF, false);
        settings = new Tile(c, R.drawable.sym_gear, 0, 0xFFFFFFFF, false);
        place(grid, calc, 0, 4, 1, 1, u, gap);
        place(grid, camera, 1, 4, 1, 1, u, gap);
        place(grid, recorder, 2, 4, 1, 1, u, gap);
        place(grid, settings, 3, 4, 1, 1, u, gap);

        airplane.setContentDescription("Mode pesawat");
        data.setContentDescription("Data seluler");
        wifi.setContentDescription("Wi-Fi");
        bluetooth.setContentDescription("Bluetooth");
        rotation.setContentDescription("Kunci rotasi");
        focus.setContentDescription("Fokus");
        torch.setContentDescription("Senter");
        timer.setContentDescription("Timer");
        calc.setContentDescription("Kalkulator");
        camera.setContentDescription("Kamera");
        recorder.setContentDescription("Perekam suara");
        settings.setContentDescription("Pengaturan");
        brightness.setContentDescription("Kecerahan");
        volume.setContentDescription("Volume");
        wire();
        initTorch();
        refresh();
    }

    private LinearLayout row(Context c, View a, View b, int d, int gap) {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.addView(a, new LinearLayout.LayoutParams(d, d));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(d, d);
        lp.setMarginStart(gap);
        r.addView(b, lp);
        return r;
    }

    private static void place(FrameLayout g, View v, int col, int row, int cs, int rs, int u, int gap) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(cs * u + (cs - 1) * gap, rs * u + (rs - 1) * gap);
        lp.leftMargin = col * (u + gap);
        lp.topMargin = row * (u + gap);
        g.addView(v, lp);
    }

    @Override protected boolean canDragClose(float x, float y) { return !scroll.canScrollVertically(1); }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        main.post(poll);
        if (camMgr != null && torchCb != null) {
            try { camMgr.registerTorchCallback(torchCb, main); } catch (Throwable ignored) { }
        }
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        main.removeCallbacks(poll);
        if (camMgr != null && torchCb != null) {
            try { camMgr.unregisterTorchCallback(torchCb); } catch (Throwable ignored) { }
        }
    }

    // ------------------------------------------------------------------ aksi

    private Context ctx() { return getContext(); }

    private void go(Intent i) {
        if (act.launch(i)) close();
    }

    private void wire() {
        final Context c = ctx();
        airplane.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { go(new Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)); }
        });
        data.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { go(new Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)); }
        });
        wifi.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    WifiManager wm = (WifiManager) c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                    @SuppressWarnings("deprecation") boolean ok = wm != null && wm.setWifiEnabled(!wm.isWifiEnabled());
                    if (ok) { refresh(); return; }
                } catch (Throwable ignored) { }
                go(new Intent(Settings.Panel.ACTION_WIFI));
            }
        });
        wifi.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) { go(new Intent(Settings.ACTION_WIFI_SETTINGS)); return true; }
        });
        bluetooth.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    BluetoothManager bm = (BluetoothManager) c.getSystemService(Context.BLUETOOTH_SERVICE);
                    BluetoothAdapter ad = bm == null ? null : bm.getAdapter();
                    if (ad != null) {
                        @SuppressWarnings("deprecation") boolean ok = ad.isEnabled() ? ad.disable() : ad.enable();
                        if (ok) { refresh(); return; }
                    }
                } catch (Throwable ignored) { }
                go(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
            }
        });
        rotation.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (!Settings.System.canWrite(c)) { askWrite(); return; }
                boolean locked = Settings.System.getInt(c.getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 1) == 0;
                Settings.System.putInt(c.getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, locked ? 1 : 0);
                act.toast(locked ? "Rotasi layar: otomatis" : "Rotasi layar dikunci");
                refresh();
            }
        });
        focus.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm == null) return;
                if (!nm.isNotificationPolicyAccessGranted()) {
                    act.toast("Izinkan akses Jangan Ganggu untuk SP Launcher");
                    go(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
                    return;
                }
                boolean on = nm.getCurrentInterruptionFilter() != NotificationManager.INTERRUPTION_FILTER_ALL
                        && nm.getCurrentInterruptionFilter() != NotificationManager.INTERRUPTION_FILTER_UNKNOWN;
                nm.setInterruptionFilter(on ? NotificationManager.INTERRUPTION_FILTER_ALL
                        : NotificationManager.INTERRUPTION_FILTER_PRIORITY);
                refresh();
            }
        });
        torch.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (camMgr == null || torchId == null) { act.toast("Perangkat tidak memiliki senter"); return; }
                try { camMgr.setTorchMode(torchId, !torchOn); }
                catch (CameraAccessException e) { act.toast("Senter sedang dipakai aplikasi lain"); }
                catch (Throwable t) { act.toast("Senter tidak dapat dinyalakan"); }
            }
        });
        timer.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent i = new Intent(AlarmClock.ACTION_SET_TIMER);
                i.putExtra(AlarmClock.EXTRA_SKIP_UI, false);
                if (!act.launch(i)) { go(new Intent(AlarmClock.ACTION_SHOW_ALARMS)); return; }
                close();
            }
        });
        calc.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                go(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALCULATOR));
            }
        });
        camera.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { go(new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)); }
        });
        recorder.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { go(new Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION)); }
        });
        settings.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { go(new Intent(Settings.ACTION_SETTINGS)); }
        });

        brightness.setListener(new VSlider.Listener() {
            @Override public void onValue(float v) {
                if (!Settings.System.canWrite(c)) { askWrite(); return; }
                try {
                    Settings.System.putInt(c.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE,
                            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
                    Settings.System.putInt(c.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS,
                            U.clamp(Math.round(v * 255f), 1, 255));
                } catch (Throwable ignored) { }
            }
        });
        volume.setListener(new VSlider.Listener() {
            @Override public void onValue(float v) {
                AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
                if (am == null) return;
                int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                try { am.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(v * max), 0); } catch (Throwable ignored) { }
            }
        });
    }

    private void askWrite() {
        if (askedWrite) { act.toast("Izin 'Ubah setelan sistem' belum diberikan"); return; }
        askedWrite = true;
        act.toast("Izinkan 'Ubah setelan sistem' untuk SP Launcher");
        act.requestWriteSettings();
    }

    private void initTorch() {
        try {
            camMgr = (CameraManager) ctx().getSystemService(Context.CAMERA_SERVICE);
            for (String id : camMgr.getCameraIdList()) {
                CameraCharacteristics cc = camMgr.getCameraCharacteristics(id);
                Boolean flash = cc.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                Integer facing = cc.get(CameraCharacteristics.LENS_FACING);
                if (flash != null && flash) {
                    if (torchId == null || (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK)) torchId = id;
                }
            }
            torchCb = new CameraManager.TorchCallback() {
                @Override public void onTorchModeChanged(String id, boolean enabled) {
                    if (id.equals(torchId)) { torchOn = enabled; torch.setOn(enabled); }
                }
            };
            camMgr.registerTorchCallback(torchCb, main);
        } catch (Throwable t) {
            camMgr = null;
        }
    }

    // ------------------------------------------------------------------ status

    void refresh() {
        Context c = ctx();
        try {
            boolean plane = Settings.Global.getInt(c.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) == 1;
            airplane.setOn(plane);
            data.setOn(!plane && Settings.Global.getInt(c.getContentResolver(), "mobile_data", 1) == 1);
            WifiManager wm = (WifiManager) c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            wifi.setOn(wm != null && wm.isWifiEnabled());
            bluetooth.setOn(Settings.Global.getInt(c.getContentResolver(), "bluetooth_on", 0) == 1);
            rotation.setOn(Settings.System.getInt(c.getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 1) == 0);
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            int f = nm == null ? NotificationManager.INTERRUPTION_FILTER_ALL : nm.getCurrentInterruptionFilter();
            focus.setOn(f != NotificationManager.INTERRUPTION_FILTER_ALL && f != NotificationManager.INTERRUPTION_FILTER_UNKNOWN);
            torch.setOn(torchOn);
            if (!brightness.isDragging()) {
                int b = Settings.System.getInt(c.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, 128);
                brightness.setValue(b / 255f);
            }
            AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
            if (am != null && !volume.isDragging()) {
                int max = Math.max(1, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
                volume.setValue(am.getStreamVolume(AudioManager.STREAM_MUSIC) / (float) max);
            }
        } catch (Throwable ignored) { }
        media.refresh();
    }

    // ================================================================== komponen

    /** Ubin ikon: kaca saat mati, berwarna saat hidup. */
    private static final class Tile extends View {
        private final Drawable glyph;
        private final int onFill, onGlyph;
        private final boolean circle;
        private boolean on;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rf = new RectF();

        Tile(Context c, int res, int onFill, int onGlyph, boolean circle) {
            super(c);
            glyph = c.getDrawable(res).mutate();
            this.onFill = onFill;
            this.onGlyph = onGlyph;
            this.circle = circle;
            setClickable(true);
            Glass.watch(this); // kaca digambar manual di onDraw agar radius mengikuti ukuran
        }

        void setOn(boolean v) {
            if (v == on) return;
            on = v;
            invalidate();
        }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            float r = circle ? Math.min(w, h) / 2f : U.dpf(getContext(), 22);
            boolean lit = on && onFill != 0;
            if (lit) {
                rf.set(0, 0, w, h);
                fill.setColor(onFill);
                c.drawRoundRect(rf, r, r, fill);
            } else {
                Glass.paint(c, this, 0, 0, w, h, r, 0.26f, true);
            }
            int gs = Math.round(Math.min(w, h) * (circle ? 0.46f : 0.44f));
            glyph.setTint(lit ? onGlyph : 0xFFFFFFFF);
            glyph.setBounds(Math.round((w - gs) / 2f), Math.round((h - gs) / 2f), Math.round((w + gs) / 2f), Math.round((h + gs) / 2f));
            glyph.draw(c);
        }

        @Override public void setPressed(boolean p) {
            boolean was = isPressed();
            super.setPressed(p);
            if (was != p) { animate().cancel(); animate().scaleX(p ? 0.93f : 1f).scaleY(p ? 0.93f : 1f).setDuration(p ? 80 : 150).start(); }
        }
    }

    /** Slider vertikal (kecerahan / volume). */
    private static final class VSlider extends View {
        interface Listener { void onValue(float v); }

        private final Drawable glyph;
        private float value;
        private boolean dragging;
        private Listener l;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path clip = new Path();
        private final RectF rf = new RectF();

        VSlider(Context c, int res) {
            super(c);
            glyph = c.getDrawable(res).mutate();
            setClickable(true);
            Glass.watch(this);
        }

        void setListener(Listener l) { this.l = l; }
        boolean isDragging() { return dragging; }

        void setValue(float v) {
            v = U.clampf(v, 0f, 1f);
            if (Math.abs(v - value) < 0.004f) return;
            value = v;
            invalidate();
        }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), r = U.dpf(getContext(), 26);
            Glass.paint(c, this, 0, 0, w, h, r, 0.26f, true);
            float fh = h * value;
            c.save();
            rf.set(0, 0, w, h);
            clip.reset();
            clip.addRoundRect(rf, r, r, Path.Direction.CW);
            c.clipPath(clip);
            fill.setColor(0xF2FFFFFF);
            c.drawRect(0, h - fh, w, h, fill);
            c.restore();
            int gs = Math.round(w * 0.42f);
            int cy = Math.round(h - w * 0.5f);
            boolean covered = h - fh < cy + gs / 2f;
            glyph.setTint(covered ? 0xFF6B6B70 : 0xFFFFFFFF);
            glyph.setBounds(Math.round((w - gs) / 2f), cy - gs / 2, Math.round((w + gs) / 2f), cy + gs / 2);
            glyph.draw(c);
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    dragging = true;
                    getParent().requestDisallowInterceptTouchEvent(true);
                    // fallthrough
                case MotionEvent.ACTION_MOVE: {
                    float v = 1f - e.getY() / Math.max(1f, getHeight());
                    value = U.clampf(v, 0f, 1f);
                    invalidate();
                    if (l != null) l.onValue(value);
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    dragging = false;
                    return true;
            }
            return true;
        }
    }

    /** Kartu "Sedang Diputar" dengan kontrol media. */
    private final class MediaCard extends GlassLayout {
        private final ImageView art;
        private final TextView title, sub;
        private final ImageView prev, play, next;
        private MediaController mc;

        MediaCard(final Context c) {
            super(c, 28, 0.30f, true);
            setOrientation(LinearLayout.VERTICAL);
            setElevation(0);
            int p = U.dp(c, 14);
            setPadding(p, p, p, U.dp(c, 8));

            art = new ImageView(c);
            art.setScaleType(ImageView.ScaleType.CENTER_CROP);
            art.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), U.dpf(c, 10)); }
            });
            art.setClipToOutline(true);
            GradientDrawable ph = new GradientDrawable();
            ph.setColor(0x33FFFFFF);
            ph.setCornerRadius(U.dpf(c, 10));
            art.setBackground(ph);
            art.setPadding(U.dp(c, 9), U.dp(c, 9), U.dp(c, 9), U.dp(c, 9));
            addView(art, new LinearLayout.LayoutParams(U.dp(c, 46), U.dp(c, 46)));

            title = new TextView(c);
            title.setTextColor(0xFFFFFFFF);
            title.setTextSize(14f);
            title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-1, -2);
            tl.topMargin = U.dp(c, 8);
            addView(title, tl);
            sub = new TextView(c);
            sub.setTextColor(0xB3FFFFFF);
            sub.setTextSize(12f);
            sub.setSingleLine(true);
            sub.setEllipsize(TextUtils.TruncateAt.END);
            addView(sub, new LinearLayout.LayoutParams(-1, -2));

            View spacer = new View(c);
            addView(spacer, new LinearLayout.LayoutParams(1, 0, 1f));

            LinearLayout ctl = new LinearLayout(c);
            ctl.setOrientation(LinearLayout.HORIZONTAL);
            ctl.setGravity(Gravity.CENTER);
            prev = ctrl(c, R.drawable.sym_prev);
            play = ctrl(c, R.drawable.sym_play);
            next = ctrl(c, R.drawable.sym_next);
            ctl.addView(prev, new LinearLayout.LayoutParams(0, U.dp(c, 40), 1f));
            ctl.addView(play, new LinearLayout.LayoutParams(0, U.dp(c, 40), 1f));
            ctl.addView(next, new LinearLayout.LayoutParams(0, U.dp(c, 40), 1f));
            addView(ctl, new LinearLayout.LayoutParams(-1, -2));

            prev.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { if (mc != null) mc.getTransportControls().skipToPrevious(); }
            });
            next.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { if (mc != null) mc.getTransportControls().skipToNext(); }
            });
            play.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (mc == null) return;
                    PlaybackState ps = mc.getPlaybackState();
                    if (ps != null && ps.getState() == PlaybackState.STATE_PLAYING) mc.getTransportControls().pause();
                    else mc.getTransportControls().play();
                    main.postDelayed(new Runnable() { @Override public void run() { refresh(); } }, 250);
                }
            });
            setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (!NotificationService.isEnabled(c)) {
                        act.toast("Aktifkan akses notifikasi untuk kontrol media");
                        go(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
                    }
                }
            });
            refresh();
        }

        private ImageView ctrl(Context c, int res) {
            ImageView v = new ImageView(c);
            v.setImageDrawable(NotificationCenter.tinted(c, res, 0xFFFFFFFF));
            v.setPadding(U.dp(c, 8), U.dp(c, 8), U.dp(c, 8), U.dp(c, 8));
            v.setClickable(true);
            return v;
        }

        void refresh() {
            Context c = getContext();
            mc = null;
            String state = null;
            if (!NotificationService.isEnabled(c)) {
                state = "Aktifkan akses notifikasi";
            } else {
                try {
                    MediaSessionManager msm = (MediaSessionManager) c.getSystemService(Context.MEDIA_SESSION_SERVICE);
                    List<MediaController> l = msm.getActiveSessions(NotificationService.component(c));
                    for (MediaController m : l) {
                        PlaybackState ps = m.getPlaybackState();
                        if (ps != null && ps.getState() == PlaybackState.STATE_PLAYING) { mc = m; break; }
                    }
                    if (mc == null && !l.isEmpty()) mc = l.get(0);
                } catch (Throwable t) {
                    state = "Aktifkan akses notifikasi";
                }
            }
            boolean playing = false;
            if (mc != null) {
                MediaMetadata md = mc.getMetadata();
                CharSequence t = md == null ? null : md.getText(MediaMetadata.METADATA_KEY_TITLE);
                CharSequence a = md == null ? null : md.getText(MediaMetadata.METADATA_KEY_ARTIST);
                title.setText(TextUtils.isEmpty(t) ? "Sedang Diputar" : t);
                sub.setText(TextUtils.isEmpty(a) ? mc.getPackageName() : a);
                Bitmap b = md == null ? null : md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
                if (b == null && md != null) b = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
                if (b != null) { art.setPadding(0, 0, 0, 0); art.setImageBitmap(b); }
                else { art.setPadding(U.dp(c, 9), U.dp(c, 9), U.dp(c, 9), U.dp(c, 9)); art.setImageDrawable(NotificationCenter.tinted(c, R.drawable.sym_music, 0xCCFFFFFF)); }
                PlaybackState ps = mc.getPlaybackState();
                playing = ps != null && ps.getState() == PlaybackState.STATE_PLAYING;
            } else {
                title.setText(state != null ? "Media" : "Tidak diputar");
                sub.setText(state != null ? state : "Mulai putar musik atau video");
                art.setPadding(U.dp(c, 9), U.dp(c, 9), U.dp(c, 9), U.dp(c, 9));
                art.setImageDrawable(NotificationCenter.tinted(c, R.drawable.sym_music, 0x80FFFFFF));
            }
            play.setImageDrawable(NotificationCenter.tinted(c, playing ? R.drawable.sym_pause : R.drawable.sym_play, 0xFFFFFFFF));
            float a = mc != null ? 1f : 0.35f;
            prev.setAlpha(a); play.setAlpha(a); next.setAlpha(a);
        }
    }
}
