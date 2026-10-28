package com.senzaproject.splauncher;

import android.app.Activity;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherApps;
import android.content.pm.ShortcutInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Insets;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.AlarmClock;
import android.provider.Settings;
import android.text.InputType;
import android.util.SizeF;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.graphics.Outline;
import android.view.ViewOutlineProvider;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.Typeface;
import android.os.UserHandle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Layar Home SPLauncher. */
public class LauncherActivity extends Activity implements Workspace.Listener, LibraryView.Host, SearchOverlay.Host,
        WidgetCell.Host, WidgetPicker.Host, NotificationService.Listener {

    static final class WallView extends View {
        Bitmap bmp;
        int dim; // peredupan wallpaper 0..60 (%)
        WallView(Context c) { super(c); }
        void set(Bitmap b) { bmp = b; invalidate(); }
        @Override protected void onDraw(Canvas c) {
            if (bmp != null) c.drawBitmap(bmp, 0, 0, null); else c.drawColor(0xFF000000);
            if (dim > 0) c.drawColor((Math.round(dim * 2.55f)) << 24);
        }
    }

    Prefs prefs;
    AppRepo repo;
    LayoutStore store;
    FrameLayout root, overlayLayer, dragLayer;
    WallView wallView;
    Workspace workspace;
    LibraryView library;
    PageDots dots;
    GlassLayout dockPanel;
    CellLayout dockLayout;
    GlassLayout doneBtn, addBtn;
    DragController drag;
    AppWidgetHost widgetHost;
    AppWidgetManager awm;

    int insTop, insBottom, insLeft, insRight, imeBottom;
    int cols = 4, rows = 6, cellW, cellH, iconSize, dockCap = 4, pageCap = 24, dockH, dockBottom;
    boolean dark, edit;
    float lastRawX, lastRawY;

    private boolean bypass;
    private int lastW, lastH;
    private String lastSig = "";
    private int loadedWallRev = -999, loadedWallId = -999, loadedW, loadedH, loadedBlur = -1;
    private boolean wallLoading;
    private View menuScrim;
    private FrameLayout panelDim;
    private GlassLayout panelBox;
    private LayoutStore.Item panelFolder;
    private EditText panelTitle;
    private SearchOverlay search;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private final Runnable rebuildRun = new Runnable() { @Override public void run() { rebuildAll(); } };
    /** Cek tiap menit agar tema Jadwal (gelap/terang) berganti sendiri saat Home sedang terbuka. */
    private final Runnable themeTick = new Runnable() {
        @Override public void run() {
            if (prefs.theme() == Prefs.THEME_SCHEDULE && !prefs.signature(LauncherActivity.this).equals(lastSig)) scheduleRebuild();
            main.postDelayed(this, 60000);
        }
    };
    private LauncherApps.Callback pkgCallback;
    private int seenLayoutRev;

    static final int HOST_ID = 0x5350;
    private static final int REQ_BIND = 4101, REQ_CONFIGURE = 4102;
    private final HashMap<Integer, AppWidgetHostView> hostViews = new HashMap<>();
    private WidgetPicker picker;
    private TopSheet sheet, pullSheet;
    /** Layar Nol (panel widget di kiri Home pertama); null bila tertutup. */
    private ZeroScreen zero;
    AppSwitcher switcher;
    private final Runnable switcherRun = new Runnable() { @Override public void run() { openSwitcher(); } };
    private float dsX, dsY;
    private boolean dsOn;
    private int pendingId = -1;
    private AppWidgetProviderInfo pendingInfo;

    // ------------------------------------------------------------------ siklus hidup

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new Prefs(this);
        repo = new AppRepo(this);
        store = new LayoutStore(this);
        seenLayoutRev = prefs.layoutRev();
        awm = AppWidgetManager.getInstance(this);
        widgetHost = new AppWidgetHost(this, HOST_ID);
        getWindow().setDecorFitsSystemWindows(false);

        root = new FrameLayout(this);
        wallView = new WallView(this);
        root.addView(wallView, new FrameLayout.LayoutParams(-1, -1));
        workspace = new Workspace(this);
        workspace.setListener(this);
        root.addView(workspace, new FrameLayout.LayoutParams(-1, -1));
        dots = new PageDots(this);
        root.addView(dots, new FrameLayout.LayoutParams(-2, U.dp(this, 20), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));
        dockLayout = new CellLayout(this);
        dockPanel = new GlassLayout(this, 30, 0.30f, true);
        dockPanel.addView(dockLayout, new LinearLayout.LayoutParams(-1, -1));
        root.addView(dockPanel, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        overlayLayer = new FrameLayout(this);
        root.addView(overlayLayer, new FrameLayout.LayoutParams(-1, -1));
        doneBtn = new GlassLayout(this, 17, 0.40f, true);
        doneBtn.setGravity(Gravity.CENTER);
        TextView dt = new TextView(this);
        dt.setText("Selesai");
        dt.setTextColor(0xFFFFFFFF);
        dt.setTextSize(15f);
        dt.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        dt.setPadding(U.dp(this, 18), 0, U.dp(this, 18), 0);
        dt.setGravity(Gravity.CENTER);
        doneBtn.addView(dt, new LinearLayout.LayoutParams(-2, -1));
        doneBtn.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { exitEdit(); } });
        doneBtn.setVisibility(View.GONE);
        root.addView(doneBtn, new FrameLayout.LayoutParams(-2, U.dp(this, 34), Gravity.TOP | Gravity.END));
        addBtn = new GlassLayout(this, 17, 0.40f, true);
        addBtn.setGravity(Gravity.CENTER);
        TextView plus = new TextView(this);
        plus.setText("+");
        plus.setTextColor(0xFFFFFFFF);
        plus.setTextSize(24f);
        plus.setGravity(Gravity.CENTER);
        plus.setIncludeFontPadding(false);
        addBtn.addView(plus, new LinearLayout.LayoutParams(-1, -1));
        addBtn.setContentDescription("Tambah widget");
        addBtn.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { openWidgetPicker(); } });
        addBtn.setVisibility(View.GONE);
        root.addView(addBtn, new FrameLayout.LayoutParams(U.dp(this, 34), U.dp(this, 34), Gravity.TOP | Gravity.START));
        dragLayer = new FrameLayout(this);
        dragLayer.setClipChildren(false);
        root.addView(dragLayer, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        drag = new DragController(this);

        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override public WindowInsets onApplyWindowInsets(View v, WindowInsets ins) {
                Insets sb = ins.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                int ime = ins.getInsets(WindowInsets.Type.ime()).bottom;
                boolean changed = sb.top != insTop || sb.bottom != insBottom || sb.left != insLeft || sb.right != insRight;
                insTop = sb.top; insBottom = sb.bottom; insLeft = sb.left; insRight = sb.right;
                if (ime != imeBottom) {
                    imeBottom = ime;
                    if (search != null) search.setBottomInset(Math.max(ime, insBottom));
                }
                if (changed) scheduleRebuild();
                return ins;
            }
        });
        root.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override public void onLayoutChange(View v, int l, int t, int r, int b2, int ol, int ot, int or, int ob) {
                int w = r - l, h = b2 - t;
                if (w > 0 && h > 0 && (w != lastW || h != lastH)) { lastW = w; lastH = h; scheduleRebuild(); }
            }
        });

        pkgCallback = new LauncherApps.Callback() {
            private void changed() { main.post(new Runnable() { @Override public void run() { repo.reload(); scheduleRebuild(); } }); }
            @Override public void onPackageRemoved(String p, UserHandle u) { changed(); }
            @Override public void onPackageAdded(String p, UserHandle u) { changed(); }
            @Override public void onPackageChanged(String p, UserHandle u) { changed(); }
            @Override public void onPackagesAvailable(String[] p, UserHandle u, boolean r) { changed(); }
            @Override public void onPackagesUnavailable(String[] p, UserHandle u, boolean r) { changed(); }
        };
        try { repo.la.registerCallback(pkgCallback); } catch (Throwable ignored) { }
        repo.reload();
    }

    @Override protected void onStart() {
        super.onStart();
        try { widgetHost.startListening(); } catch (Throwable ignored) { }
        NotificationService.addListener(this);
        NotificationService.ensureBound(this);
        main.removeCallbacks(themeTick);
        main.postDelayed(themeTick, 60000);
    }

    @Override protected void onStop() {
        super.onStop();
        if (switcher != null) closeSwitcher(false);
        try { widgetHost.stopListening(); } catch (Throwable ignored) { }
        NotificationService.removeListener(this);
        main.removeCallbacks(themeTick);
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        hostViews.clear();
        try { repo.la.unregisterCallback(pkgCallback); } catch (Throwable ignored) { }
        exec.shutdownNow();
    }

    @Override protected void onResume() {
        super.onResume();
        if (prefs.layoutRev() != seenLayoutRev) {
            seenLayoutRev = prefs.layoutRev();
            for (LayoutStore.Item w : store.externalWidgets()) {
                try { widgetHost.deleteAppWidgetId(w.wid); } catch (Throwable ignored) { }
            }
            hostViews.clear();
            store.reset();
        }
        refreshBadges();
        applyStatusBar();
        if (Recents.consumeOpenRequest()) main.post(switcherRun);
        String sig = prefs.signature(this);
        if (!sig.equals(lastSig)) scheduleRebuild();
    }

    @Override public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        scheduleRebuild();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (Intent.ACTION_MAIN.equals(intent.getAction())) {
            boolean wantSwitcher = Recents.consumeOpenRequest();
            dismissMenu();
            closePanel(false);
            closeSearch();
            removeZero();
            if (picker != null) picker.dismiss(false);
            if (sheet != null) removeSheet(sheet);
            if (edit) exitEdit();
            if (workspace.page() != 0) workspace.smoothTo(0);
            if (switcher != null) closeSwitcher(false);
            if (wantSwitcher) main.post(switcherRun);
        }
    }

    @Override public void onBackPressed() {
        if (switcher != null) closeSwitcher(true);
        else if (picker != null) picker.dismiss(true);
        else if (sheet != null) sheet.close();
        else if (zero != null) zero.close();
        else if (menuScrim != null) dismissMenu();
        else if (panelDim != null) closePanel(true);
        else if (search != null) closeSearch();
        else if (edit) exitEdit();
        else if (workspace.page() != 0) workspace.smoothTo(0);
    }

    // ------------------------------------------------------------------ sentuhan

    @Override public boolean dispatchTouchEvent(MotionEvent ev) {
        lastRawX = ev.getRawX();
        lastRawY = ev.getRawY();
        if (!bypass && drag != null && drag.active()) return drag.onTouch(ev);
        if (!bypass) { trackDockSwipe(ev); trackDrawerSwipe(ev); }
        return super.dispatchTouchEvent(ev);
    }

    /** Batalkan sentuhan pada view anak (setelah tekan-lama dialihkan ke drag). */
    void sendCancel() {
        long t = SystemClock.uptimeMillis();
        MotionEvent c = MotionEvent.obtain(t, t, MotionEvent.ACTION_CANCEL, lastRawX, lastRawY, 0);
        bypass = true;
        try { super.dispatchTouchEvent(c); } finally { bypass = false; c.recycle(); }
    }

    float[] toRoot(float rawX, float rawY) {
        int[] l = new int[2];
        root.getLocationOnScreen(l);
        return new float[]{rawX - l[0], rawY - l[1]};
    }

    float[] toLocal(View v, float rx, float ry) {
        int[] l = new int[2];
        v.getLocationInWindow(l);
        int[] r = new int[2];
        root.getLocationInWindow(r);
        return new float[]{rx - (l[0] - r[0]), ry - (l[1] - r[1])};
    }

    void haptic(View v) {
        if (v != null && prefs.haptics()) v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
    }

    // ------------------------------------------------------------------ build

    private void scheduleRebuild() {
        main.removeCallbacks(rebuildRun);
        main.post(rebuildRun);
    }

    private void computeMetrics(int w, int h) {
        dark = prefs.isDark(this);
        cols = prefs.cols();
        boolean land = w > h;
        int side = U.dp(this, 6);
        int availW = w - insLeft - insRight - 2 * side;
        cellW = availW / cols;
        float scale = prefs.iconScale() / 100f;
        iconSize = Math.round(Math.min(cellW * 0.62f, U.dpf(this, 70)) * scale);
        iconSize = U.clamp(iconSize, U.dp(this, 28), cellW - U.dp(this, 6));
        dockCap = cols;
        boolean showDock = prefs.showDock();
        dockH = showDock ? iconSize + U.dp(this, 26) : 0;
        dockBottom = Math.max(insBottom, U.dp(this, 8)) + U.dp(this, 6);
        int dotsH = U.dp(this, 26);
        int topPad = insTop + U.dp(this, 10);
        int reserved = (showDock ? dockH + dockBottom : insBottom) + dotsH + U.dp(this, 8);
        int availH = h - topPad - reserved;
        float label = prefs.labels() ? U.dpf(this, 5) + U.sp(this, prefs.labelSize()) * 1.3f : 0f;
        float nat = iconSize + label + U.dpf(this, 22);
        rows = U.clamp((int) (availH / nat), land ? 2 : 4, 8);
        if (prefs.rowsPref() > 0) rows = prefs.rowsPref();
        cellH = availH / rows;
        pageCap = cols * rows;
    }

    private int padL() { return insLeft + U.dp(this, 6) + (root.getWidth() - insLeft - insRight - 2 * U.dp(this, 6) - cellW * cols) / 2; }
    private int padT() { return insTop + U.dp(this, 10); }

    void rebuildAll() {
        int w = root.getWidth(), h = root.getHeight();
        if (w <= 0 || h <= 0) return;
        lastSig = prefs.signature(this);
        computeMetrics(w, h);
        IconFactory.setShape(prefs.iconShape());
        wallView.dim = prefs.wallDim();
        wallView.invalidate();
        loadWallpaperIfNeeded(w, h);
        store.ensure(this, repo, prefs, cols, rows, dockCap);

        int keep = workspace.page();
        dismissMenu();
        workspace.removeAllViews();
        for (ArrayList<LayoutStore.Item> page : store.pages) workspace.addView(newPage(page, false));
        library = new LibraryView(this);
        library.rebuild(repo.all(), prefs, dark, insTop, insBottom, w, this);
        workspace.addView(library);
        workspace.setHomeCount(store.pages.size());
        workspace.setZeroEnabled(prefs.zeroScreen());
        if (zero != null) removeZero();
        workspace.setPage(Math.min(keep, workspace.getChildCount() - 1));

        // dock
        boolean showDock = prefs.showDock();
        dockPanel.setVisibility(showDock ? View.VISIBLE : View.GONE);
        dockPanel.setGlass(prefs.dockRadius(),
                prefs.dockOpacity() > 0 ? prefs.dockOpacity() / 100f : Math.max(0.22f, prefs.glassOpacity() / 100f * 0.9f), dark);
        FrameLayout.LayoutParams dlp = (FrameLayout.LayoutParams) dockPanel.getLayoutParams();
        dlp.height = dockH;
        dlp.leftMargin = insLeft + U.dp(this, 14);
        dlp.rightMargin = insRight + U.dp(this, 14);
        dlp.bottomMargin = dockBottom;
        dockPanel.setLayoutParams(dlp);
        int innerW = w - insLeft - insRight - 2 * U.dp(this, 14) - 2 * U.dp(this, 10);
        dockLayout.configure(dockCap, 1, innerW / dockCap, dockH, U.dp(this, 10), 0);
        dockLayout.removeAllViews();
        for (LayoutStore.Item it : store.dock) dockLayout.addView(newIcon(it, true));

        FrameLayout.LayoutParams dotp = (FrameLayout.LayoutParams) dots.getLayoutParams();
        dotp.bottomMargin = showDock ? dockBottom + dockH + U.dp(this, 6) : insBottom + U.dp(this, 10);
        dots.setLayoutParams(dotp);
        dots.setVisibility(prefs.pageDots() ? View.VISIBLE : View.GONE);
        FrameLayout.LayoutParams dn = (FrameLayout.LayoutParams) doneBtn.getLayoutParams();
        dn.topMargin = insTop + U.dp(this, 8);
        dn.rightMargin = insRight + U.dp(this, 16);
        doneBtn.setLayoutParams(dn);
        doneBtn.setGlass(17, 0.40f, true);
        FrameLayout.LayoutParams an = (FrameLayout.LayoutParams) addBtn.getLayoutParams();
        an.topMargin = insTop + U.dp(this, 8);
        an.leftMargin = insLeft + U.dp(this, 16);
        addBtn.setLayoutParams(an);
        addBtn.setGlass(17, 0.40f, true);

        onScrollFraction(workspace.getScrollX() / (float) Math.max(1, w));
        if (edit) applyEdit(true);
        refreshBadges();
    }

    private CellLayout newPage(List<LayoutStore.Item> items, boolean unused) {
        CellLayout cl = new CellLayout(this);
        cl.configure(cols, rows, cellW, cellH, padL(), padT());
        for (LayoutStore.Item it : items) cl.addView(it.type == LayoutStore.WIDGET ? newWidget(it) : newIcon(it, false));
        return cl;
    }

    private IconView newIcon(LayoutStore.Item it, boolean inDock) {
        IconView iv = new IconView(this);
        bindIcon(iv, it, inDock);
        iv.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { onIconClick((IconView) v); } });
        iv.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) { onIconLongClick((IconView) v); return true; }
        });
        return iv;
    }

    void bindIcon(IconView iv, LayoutStore.Item it, boolean inDock) {
        boolean labels = prefs.labels() && !inDock;
        if (it.type == LayoutStore.APP) {
            AppRepo.AppInfo a = repo.get(it.key);
            if (a != null) iv.bindItemApp(it, a, prefs, iconSize, labels, dark);
        } else {
            ArrayList<AppRepo.AppInfo> kids = new ArrayList<>();
            for (String k : it.kids) { AppRepo.AppInfo a = repo.get(k); if (a != null) kids.add(a); }
            iv.bindFolder(it, kids, prefs, iconSize, labels, dark);
        }
    }

    private void loadWallpaperIfNeeded(final int w, final int h) {
        final int id = prefs.wallpaper(), rev = prefs.wallpaperRev();
        if (wallLoading) return;
        if (id == loadedWallId && rev == loadedWallRev && w == loadedW && h == loadedH && prefs.blur() == loadedBlur && Glass.blurred != null) {
            // wallpaper & blur tidak berubah: cukup terapkan mode kaca (Blur / Kaca Cair / Padat)
            Glass.setMode(prefs.reduceTransparency(), prefs.glassMode());
            return;
        }
        wallLoading = true;
        final int blur = prefs.blur();
        final boolean reduce = prefs.reduceTransparency();
        final int mode = prefs.glassMode();
        exec.execute(new Runnable() {
            @Override public void run() {
                final Bitmap bmp = Wallpapers.render(LauncherActivity.this, prefs, w, h);
                // hitung blur di thread latar (sebelumnya di thread utama sehingga UI tersendat saat slider diubah)
                final Glass.Backdrop bd = Glass.prepare(bmp, blur);
                main.post(new Runnable() {
                    @Override public void run() {
                        wallLoading = false;
                        loadedWallId = id; loadedWallRev = rev; loadedW = w; loadedH = h; loadedBlur = blur;
                        wallView.set(bmp);
                        Glass.commit(bd, reduce, mode);
                        applyBarIcons(bmp);
                        if (!prefs.signature(LauncherActivity.this).equals(lastSig)) scheduleRebuild();
                    }
                });
            }
        });
    }

    private void applyBarIcons(Bitmap b) {
        try {
            int w = b.getWidth(), h = b.getHeight();
            float top = 0, bot = 0;
            int n = 0;
            for (int i = 1; i < 8; i++) {
                top += U.luminance(b.getPixel(w * i / 8, Math.max(0, Math.min(h - 1, h / 30))));
                bot += U.luminance(b.getPixel(w * i / 8, Math.max(0, Math.min(h - 1, h - h / 30))));
                n++;
            }
            WindowInsetsController c = getWindow().getInsetsController();
            if (c == null) return;
            c.setSystemBarsAppearance(top / n > 0.62f ? WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS : 0,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
            c.setSystemBarsAppearance(bot / n > 0.62f ? WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS : 0,
                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        } catch (Throwable ignored) { }
    }

    // ------------------------------------------------------------------ akses untuk DragController

    boolean dockEnabled() { return prefs.showDock() && dockPanel.getVisibility() == View.VISIBLE && dockPanel.getAlpha() > 0.5f; }
    float dockTop() { return root.getHeight() - dockBottom - dockH; }
    boolean isEdit() { return edit; }
    CellLayout pageLayout(int i) { return (CellLayout) workspace.getChildAt(i); }

    /** -1 = dock, -2 = bukan di Home, selain itu indeks halaman. */
    int pageIndexOf(View v) {
        if (v.getParent() == dockLayout) return -1;
        int i = workspace.indexOfChild((View) v.getParent());
        return (i >= 0 && i < store.pages.size()) ? i : -2;
    }

    void addEmptyPage() {
        store.pages.add(new ArrayList<LayoutStore.Item>());
        CellLayout cl = new CellLayout(this);
        cl.configure(cols, rows, cellW, cellH, padL(), padT());
        workspace.addView(cl, store.pages.size() - 1);
        workspace.setHomeCount(store.pages.size());
        onScrollFraction(workspace.getScrollX() / (float) Math.max(1, root.getWidth()));
    }

    // ------------------------------------------------------------------ mode edit

    void enterEdit() {
        if (edit) return;
        if (prefs.lockLayout()) {
            Toast.makeText(this, "Susunan Home dikunci (Pengaturan > Home Screen)", Toast.LENGTH_SHORT).show();
            return;
        }
        edit = true;
        applyEdit(true);
    }

    void exitEdit() {
        if (!edit) return;
        edit = false;
        applyEdit(false);
        store.normalize(cols, rows, dockCap);
        store.save();
        rebuildAll();
    }

    private void applyEdit(boolean on) {
        doneBtn.setVisibility(on && picker == null ? View.VISIBLE : View.GONE);
        addBtn.setVisibility(on && picker == null ? View.VISIBLE : View.GONE);
        workspace.setPullEnabled(!on);
        workspace.setLongPressEnabled(!on);
        for (int i = 0; i < workspace.getChildCount(); i++) {
            View p = workspace.getChildAt(i);
            if (p instanceof CellLayout) setEditOn((CellLayout) p, on);
        }
        setEditOn(dockLayout, on);
    }

    private void setEditOn(CellLayout cl, boolean on) {
        for (int i = 0; i < cl.getChildCount(); i++) {
            View v = cl.getChildAt(i);
            if (v instanceof IconView) ((IconView) v).setEdit(on);
            else if (v instanceof WidgetCell) ((WidgetCell) v).setEdit(on);
        }
    }

    // ------------------------------------------------------------------ ikon

    private void onIconClick(IconView v) {
        if (edit) {
            if (v.badgeHit(v.lastDownX, v.lastDownY)) confirmRemove(v);
            return;
        }
        if (v.isFolder()) openFolder(v);
        else if (v.app != null) repo.launch(v.app, v);
    }

    private void onIconLongClick(IconView v) {
        haptic(v);
        if (edit) { drag.arm(v, true); return; }
        List<Menus.Action> acts = new ArrayList<>();
        final LayoutStore.Item it = v.item;
        if (!prefs.lockLayout()) acts.add(new Menus.Action("Edit Layar Utama", new Runnable() { @Override public void run() { enterEdit(); } }));
        if (it.type == LayoutStore.FOLDER) {
            final IconView fv = v;
            acts.add(new Menus.Action("Ganti Nama", new Runnable() { @Override public void run() { openFolder(fv, true); } }));
            acts.add(new Menus.Action("Hapus Folder", new Runnable() {
                @Override public void run() { store.removeItem(it); store.save(); rebuildAll(); }
            }, true));
        } else if (v.app != null) {
            appMenuItems(acts, v.app, it, null);
        }
        showMenu(rectOf(v), acts);
        if (!prefs.lockLayout()) drag.arm(v, false);
    }

    private void confirmRemove(final IconView v) {
        List<Menus.Action> acts = new ArrayList<>();
        final LayoutStore.Item it = v.item;
        acts.add(new Menus.Action(it.type == LayoutStore.FOLDER ? "Hapus Folder dari Home" : "Hapus dari Home Screen",
                new Runnable() { @Override public void run() { store.removeItem(it); store.save(); rebuildAll(); } }, true));
        acts.add(new Menus.Action("Batal", null));
        showMenu(rectOf(v), acts);
    }

    /** Item menu umum untuk sebuah aplikasi. */
    private void appMenuItems(List<Menus.Action> acts, final AppRepo.AppInfo a, final LayoutStore.Item homeItem,
                              final LayoutStore.Item folder) {
        // pintasan aplikasi (hanya bila launcher ini default)
        try {
            LauncherApps.ShortcutQuery q = new LauncherApps.ShortcutQuery();
            q.setPackage(a.pkg);
            q.setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC | LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST);
            List<ShortcutInfo> sc = repo.la.getShortcuts(q, a.user);
            if (sc != null) {
                int n = 0;
                for (final ShortcutInfo s : sc) {
                    if (n++ >= 3) break;
                    CharSequence l = s.getShortLabel();
                    acts.add(new Menus.Action(l == null ? s.getId() : l.toString(), new Runnable() {
                        @Override public void run() {
                            try { repo.la.startShortcut(s, null, null); } catch (Throwable t) { toast("Pintasan tidak tersedia"); }
                        }
                    }));
                }
            }
        } catch (Throwable ignored) { }
        if (folder != null) {
            acts.add(new Menus.Action("Keluarkan dari Folder", new Runnable() {
                @Override public void run() { closePanel(false); store.ejectFromFolder(folder, a.key); store.save(); rebuildAll(); }
            }));
        } else if (homeItem != null) {
            acts.add(new Menus.Action("Hapus dari Home Screen", new Runnable() {
                @Override public void run() { store.removeItem(homeItem); store.save(); rebuildAll(); }
            }));
        } else if (!store.isOnHome(a.key)) {
            acts.add(new Menus.Action("Tambahkan ke Home Screen", new Runnable() {
                @Override public void run() { store.addToHome(a.key); store.normalize(cols, rows, dockCap); store.save(); rebuildAll(); toast("Ditambahkan ke Home Screen"); }
            }));
        }
        acts.add(new Menus.Action("Info Aplikasi", new Runnable() {
            @Override public void run() {
                try { startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + a.pkg))); }
                catch (Throwable t) { toast("Tidak dapat membuka info aplikasi"); }
            }
        }));
        acts.add(new Menus.Action("Bagikan Aplikasi", new Runnable() {
            @Override public void run() {
                Intent s = new Intent(Intent.ACTION_SEND);
                s.setType("text/plain");
                s.putExtra(Intent.EXTRA_TEXT, a.label + "\nhttps://play.google.com/store/apps/details?id=" + a.pkg);
                startActivity(Intent.createChooser(s, "Bagikan " + a.label));
            }
        }));
        if (!a.system && !a.pkg.equals(getPackageName())) {
            acts.add(new Menus.Action("Hapus Aplikasi", new Runnable() {
                @Override public void run() {
                    try { startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + a.pkg))); }
                    catch (Throwable t) { toast("Tidak dapat menghapus aplikasi"); }
                }
            }, true));
        }
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    RectF rectOf(View v) {
        int[] l = new int[2], r = new int[2];
        v.getLocationInWindow(l);
        root.getLocationInWindow(r);
        float x = l[0] - r[0], y = l[1] - r[1];
        return new RectF(x, y, x + v.getWidth(), y + v.getHeight());
    }

    // ------------------------------------------------------------------ menu konteks

    void showMenu(RectF anchor, List<Menus.Action> actions) {
        dismissMenu();
        FrameLayout scrim = new FrameLayout(this);
        scrim.setClickable(true);
        scrim.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { dismissMenu(); } });
        GlassLayout m = Menus.build(this, dark, actions, new Runnable() { @Override public void run() { dismissMenu(); } });
        int mw = U.dp(this, 230);
        m.measure(View.MeasureSpec.makeMeasureSpec(mw, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int mh = m.getMeasuredHeight();
        int W = root.getWidth(), H = root.getHeight();
        float left = U.clampf(anchor.centerX() - mw / 2f, U.dp(this, 12) + insLeft, W - mw - U.dp(this, 12) - insRight);
        boolean above = anchor.top - mh - U.dp(this, 10) > insTop + U.dp(this, 8);
        float top = above ? anchor.top - mh - U.dp(this, 10) : anchor.bottom + U.dp(this, 10);
        top = U.clampf(top, insTop + U.dp(this, 8), Math.max(insTop + 8, H - insBottom - mh - U.dp(this, 8)));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(mw, mh);
        lp.leftMargin = Math.round(left);
        lp.topMargin = Math.round(top);
        scrim.addView(m, lp);
        overlayLayer.addView(scrim, new FrameLayout.LayoutParams(-1, -1));
        menuScrim = scrim;
        if (animOn()) {
            m.setPivotX(anchor.centerX() - left);
            m.setPivotY(above ? mh : 0);
            m.setScaleX(startScale(0.8f)); m.setScaleY(startScale(0.8f)); m.setAlpha(0f);
            m.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(170).setInterpolator(panelInterp()).start();
        }
    }

    void dismissMenu() {
        if (menuScrim != null) {
            overlayLayer.removeView(menuScrim);
            menuScrim = null;
        }
    }

    // ------------------------------------------------------------------ folder / panel kategori

    private void openFolder(IconView icon) { openFolder(icon, false); }

    private void openFolder(IconView icon, boolean rename) {
        LayoutStore.Item f = icon.item;
        ArrayList<AppRepo.AppInfo> apps = new ArrayList<>();
        for (String k : f.kids) { AppRepo.AppInfo a = repo.get(k); if (a != null) apps.add(a); }
        showAppPanel(f.title, apps, f, icon, rename);
    }

    @Override public void openCategory(String title, List<AppRepo.AppInfo> apps, View anchor) {
        showAppPanel(title, apps, null, anchor, false);
    }

    private void showAppPanel(String title, List<AppRepo.AppInfo> apps, final LayoutStore.Item folder, View anchor,
                              boolean focusTitle) {
        closePanel(false);
        int W = root.getWidth(), H = root.getHeight();
        final FrameLayout dim = new FrameLayout(this);
        dim.setBackgroundColor(0x73000000);
        dim.setClickable(true);
        dim.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { closePanel(true); } });

        int pad = U.dp(this, 16);
        final GlassLayout box = new GlassLayout(this, 32, prefs.transparentFolders() ? (dark ? 0.55f : 0.62f) : 0.92f, dark);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad, pad, pad);
        box.setClickable(true);
        int fg = dark ? 0xFFFFFFFF : 0xFF111114;

        if (folder != null) {
            panelTitle = new EditText(this);
            panelTitle.setText(title);
            panelTitle.setSingleLine(true);
            panelTitle.setImeOptions(EditorInfo.IME_ACTION_DONE);
            panelTitle.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
            panelTitle.setBackground(null);
            panelTitle.setSelectAllOnFocus(true);
            panelTitle.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                @Override public boolean onEditorAction(TextView v, int a, android.view.KeyEvent e) {
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                    v.clearFocus();
                    return true;
                }
            });
        } else {
            panelTitle = null;
        }
        TextView tv = panelTitle != null ? panelTitle : new TextView(this);
        if (panelTitle == null) tv.setText(title);
        tv.setTextColor(fg);
        tv.setTextSize(21f);
        tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        tv.setGravity(Gravity.CENTER);
        box.addView(tv, new LinearLayout.LayoutParams(-1, U.dp(this, 44)));

        int boxW = Math.min(W - insLeft - insRight - U.dp(this, 40), U.dp(this, 420));
        int fcols = 4;
        int cw = (boxW - 2 * pad) / fcols;
        int fi = Math.min(U.dp(this, 58), Math.round(cw * 0.74f));
        int ch = fi + U.dp(this, 30);
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout line = null;
        for (int i = 0; i < apps.size(); i++) {
            if (i % fcols == 0) {
                line = new LinearLayout(this);
                line.setOrientation(LinearLayout.HORIZONTAL);
                grid.addView(line, new LinearLayout.LayoutParams(-1, ch));
            }
            final AppRepo.AppInfo a = apps.get(i);
            IconView iv = new IconView(this);
            iv.bindApp(a, prefs, fi, true, dark);
            iv.setLabelColor(fg, dark);
            iv.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { closePanel(false); repo.launch(a, v); }
            });
            iv.setOnLongClickListener(new View.OnLongClickListener() {
                @Override public boolean onLongClick(View v) {
                    haptic(v);
                    List<Menus.Action> acts = new ArrayList<>();
                    appMenuItems(acts, a, null, folder);
                    showMenu(rectOf(v), acts);
                    return true;
                }
            });
            line.addView(iv, new LinearLayout.LayoutParams(cw, ch));
        }
        sv.addView(grid, new ScrollView.LayoutParams(-1, -2));
        int rowsN = (apps.size() + fcols - 1) / fcols;
        int maxH = Math.round(H * 0.58f);
        int contentH = Math.min(maxH, Math.max(1, rowsN) * ch);
        box.addView(sv, new LinearLayout.LayoutParams(-1, contentH));

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(boxW, -2, Gravity.CENTER);
        lp.bottomMargin = U.dp(this, 40);
        dim.addView(box, lp);
        overlayLayer.addView(dim, new FrameLayout.LayoutParams(-1, -1));
        panelDim = dim;
        panelBox = box;
        panelFolder = folder;

        if (animOn()) {
            final RectF ar = rectOf(anchor);
            dim.setAlpha(0f);
            box.setScaleX(startScale(0.3f)); box.setScaleY(startScale(0.3f));
            box.post(new Runnable() {
                @Override public void run() {
                    float px = ar.centerX() - box.getLeft(), py = ar.centerY() - box.getTop();
                    box.setPivotX(px); box.setPivotY(py);
                    dim.animate().alpha(1f).setDuration(220).start();
                    box.animate().scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(panelInterp()).start();
                }
            });
        }
        if (focusTitle && panelTitle != null) {
            panelTitle.postDelayed(new Runnable() {
                @Override public void run() {
                    if (panelTitle == null) return;
                    panelTitle.requestFocus();
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) imm.showSoftInput(panelTitle, InputMethodManager.SHOW_IMPLICIT);
                }
            }, 300);
        }
    }

    private void closePanel(boolean animate) {
        if (panelDim == null) return;
        final FrameLayout dim = panelDim;
        boolean changed = false;
        if (panelFolder != null && panelTitle != null) {
            String t = panelTitle.getText().toString().trim();
            if (!t.isEmpty() && !t.equals(panelFolder.title)) { panelFolder.title = t; store.save(); changed = true; }
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(panelTitle.getWindowToken(), 0);
        }
        panelDim = null; panelBox = null; panelFolder = null; panelTitle = null;
        dismissMenu();
        if (animate && animOn()) {
            dim.animate().alpha(0f).setDuration(160).withEndAction(new Runnable() {
                @Override public void run() { overlayLayer.removeView(dim); }
            }).start();
        } else {
            overlayLayer.removeView(dim);
        }
        if (changed) rebuildAll();
    }

    // ------------------------------------------------------------------ pencarian

    @Override public void openSearch() {
        if (search != null) return;
        dismissMenu();
        search = new SearchOverlay(this, this, prefs, repo, insTop, Math.max(imeBottom, insBottom));
        overlayLayer.addView(search, new FrameLayout.LayoutParams(-1, -1));
        if (animOn()) {
            search.setAlpha(0f);
            search.animate().alpha(1f).setDuration(200).start();
        }
        search.post(new Runnable() { @Override public void run() { if (search != null) search.focusInput(); } });
    }

    /** Laci Aplikasi: daftar semua aplikasi A-Z yang naik dari bawah (geser ke atas di Home). Tanpa memunculkan keyboard. */
    void openDrawer() {
        if (search != null) return;
        dismissMenu();
        search = new SearchOverlay(this, this, prefs, repo, insTop, Math.max(imeBottom, insBottom));
        overlayLayer.addView(search, new FrameLayout.LayoutParams(-1, -1));
        if (animOn()) {
            search.setAlpha(0f);
            search.setTranslationY(root.getHeight() * 0.3f);
            search.animate().alpha(1f).translationY(0f).setDuration(260)
                    .setInterpolator(new DecelerateInterpolator()).start();
        }
    }

    // ------------------------------------------------------------------ animasi transisi

    /** Animasi aktif bila Kurangi Gerakan mati dan Animasi Transisi bukan "Tanpa Animasi". */
    private boolean animOn() { return !prefs.reduceMotion() && prefs.transition() != Prefs.TR_NONE; }

    /** Skala awal panel/menu: mode Pudar hanya memudar tanpa membesar. */
    private float startScale(float v) { return prefs.transition() == Prefs.TR_FADE ? 1f : v; }

    private android.animation.TimeInterpolator panelInterp() {
        return prefs.transition() == Prefs.TR_LIQUID ? new OvershootInterpolator(0.9f) : new DecelerateInterpolator();
    }

    @Override public void closeSearch() {
        if (search == null) return;
        final SearchOverlay s = search;
        search = null;
        s.hideKeyboard();
        overlayLayer.removeView(s);
    }

    // ------------------------------------------------------------------ callback host

    @Override public void launch(AppRepo.AppInfo a, View v) {
        closeSearch();
        closePanel(false);
        repo.launch(a, v);
    }

    @Override public void longPress(AppRepo.AppInfo a, View v) {
        haptic(v);
        List<Menus.Action> acts = new ArrayList<>();
        appMenuItems(acts, a, null, null);
        showMenu(rectOf(v), acts);
    }

    @Override public void onScrollFraction(float pos) {
        int homes = store.pages.size();
        dots.set(homes + 1, pos);
        float a = 1f - U.clampf(pos - (homes - 1), 0f, 1f);
        dots.setAlpha(a);
        dockPanel.setAlpha(a);
        int vis = a <= 0.02f ? View.INVISIBLE : View.VISIBLE;
        if (prefs.showDock()) dockPanel.setVisibility(vis);
    }

    @Override public void onPageSettled(int page) { }

    @Override public void onPullBegin(float x, float y) {
        pullSheet = null;
        if (sheet != null || !prefs.topGestures()) return;
        if (y < insTop + U.dp(this, 120)) {
            float fx = x / Math.max(1f, (float) root.getWidth());
            int mode;
            if (prefs.topZones27()) mode = fx < 0.36f ? 1 : (fx > 0.64f ? 2 : 0); // 0 = tengah: Cari
            else mode = fx > 0.55f ? 2 : 1;
            if (mode != 0) {
                pullSheet = makeSheet(mode);
                pullSheet.setProgress(0f);
            }
        }
    }

    @Override public void onPullMove(float dy) {
        if (pullSheet != null) pullSheet.setProgress(dy / (root.getHeight() * 0.40f));
    }

    @Override public void onPullEnd(float dy, float vy, boolean cancelled) {
        TopSheet s = pullSheet;
        pullSheet = null;
        if (s != null) {
            boolean open = !cancelled && (s.progress() > 0.30f || vy > 1000f);
            s.animateTo(open ? 1f : 0f, vy);
            return;
        }
        if (prefs.pullSearch() && !cancelled && (dy > U.dpf(this, 70) || vy > 1200f)) openSearch();
    }

    // ------------------------------------------------------------------ Layar Nol

    private final ZeroScreen.Host zeroHost = new ZeroScreen.Host() {
        @Override public void onZeroClosed(ZeroScreen z) { if (zero == z) removeZero(); else { z.release(); overlayLayer.removeView(z); } }
        @Override public boolean reduceMotion() { return prefs.reduceMotion(); }
        @Override public void onZeroSearch() { removeZero(); openSearch(); }
    };

    private void removeZero() {
        ZeroScreen z = zero;
        zero = null;
        if (z == null) return;
        z.release();
        overlayLayer.removeView(z);
    }

    @Override public void onZeroBegin() {
        if (zero != null) removeZero();
        dismissMenu();
        zero = new ZeroScreen(this, prefs, dark, insTop, insBottom, zeroHost);
        overlayLayer.addView(zero, new FrameLayout.LayoutParams(-1, -1));
        zero.setProgress(0f);
    }

    @Override public void onZeroMove(float dx) {
        if (zero != null) zero.setProgress(dx / Math.max(1f, root.getWidth() * 0.9f));
    }

    @Override public void onZeroEnd(float dx, float vx, boolean cancelled) {
        ZeroScreen z = zero;
        if (z == null) return;
        boolean open = !cancelled && (z.progress() > 0.30f || vx > 900f);
        z.animateTo(open ? 1f : 0f);
    }

    @Override public void onEmptyLongPress(float x, float y) {
        List<Menus.Action> acts = new ArrayList<>();
        if (!prefs.lockLayout()) acts.add(new Menus.Action("Edit Layar Utama", new Runnable() { @Override public void run() { enterEdit(); } }));
        acts.add(new Menus.Action("Tambah Widget", new Runnable() { @Override public void run() { openWidgetPicker(); } }));
        acts.add(new Menus.Action("Wallpaper & Tampilan", new Runnable() { @Override public void run() { openSettings(); } }));
        acts.add(new Menus.Action("Pengaturan SPLauncher", new Runnable() { @Override public void run() { openSettings(); } }));
        showMenu(new RectF(x, y, x, y), acts);
    }

    // ------------------------------------------------------------------ widget

    int widgetInsetX() { return Math.max(U.dp(this, 6), (cellW - iconSize) / 2); }

    int widgetInsetY() {
        int block = iconSize + (prefs.labels() ? U.dp(this, 5) + Math.round(U.sp(this, prefs.labelSize()) * 1.3f) : 0);
        return U.clamp((cellH - block) / 2, U.dp(this, 5), U.dp(this, 18));
    }

    ExtWidgets.Metrics metrics() {
        ExtWidgets.Metrics m = new ExtWidgets.Metrics();
        m.cols = cols; m.rows = rows; m.cellW = cellW; m.cellH = cellH;
        m.insetX = widgetInsetX(); m.insetY = widgetInsetY();
        return m;
    }

    private WidgetCell newWidget(LayoutStore.Item it) {
        View content = it.isExternal() ? externalView(it) : BuiltinWidgets.create(this, it, prefs, dark);
        return new WidgetCell(this, it, content, this, widgetInsetX(), widgetInsetY());
    }

    private View externalView(LayoutStore.Item it) {
        AppWidgetProviderInfo info = null;
        try { info = awm.getAppWidgetInfo(it.wid); } catch (Throwable ignored) { }
        if (info == null) {
            // penyedia widget tidak ada (aplikasi dihapus / sedang diperbarui): jangan hapus otomatis
            return new BuiltinWidgets.Missing(this, prefs, dark, it.spanW, it.spanH, "Widget tidak tersedia",
                    "Aplikasi widget ini dihapus atau sedang diperbarui. Tahan untuk menghapus.");
        }
        AppWidgetHostView hv = hostViews.get(it.wid);
        if (hv == null) {
            hv = widgetHost.createView(this, it.wid, info);
            hostViews.put(it.wid, hv);
        } else if (hv.getParent() instanceof ViewGroup) {
            ((ViewGroup) hv.getParent()).removeView(hv);
        }
        hv.setPadding(0, 0, 0, 0);
        final float rad = U.dpf(this, 20);
        hv.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), rad); }
        });
        hv.setClipToOutline(true);
        ExtWidgets.Metrics m = metrics();
        float d = getResources().getDisplayMetrics().density;
        ArrayList<SizeF> sizes = new ArrayList<>();
        sizes.add(new SizeF(m.widthPx(it.spanW) / d, m.heightPx(it.spanH) / d));
        try { hv.updateAppWidgetSize(new Bundle(), sizes); } catch (Throwable ignored) { }
        return hv;
    }

    @Override public void onWidgetClick(WidgetCell c) {
        if (edit) {
            if (c.badgeHit(c.lastDownX, c.lastDownY)) confirmRemoveWidget(c); else showWidgetMenu(c, false);
            return;
        }
        if (!c.item.isExternal()) openBuiltin(c.item.wkind);
    }

    @Override public void onWidgetLongClick(WidgetCell c) {
        if (edit) { drag.arm(c, true); return; }
        showWidgetMenu(c, true);
    }

    @Override public boolean isEditMode() { return edit; }

    @Override public boolean reduceMotion() { return prefs.reduceMotion(); }

    private void openBuiltin(String kind) {
        try {
            switch (kind) {
                case BuiltinWidgets.CLOCK_ANALOG:
                case BuiltinWidgets.CLOCK_DIGITAL:
                    startActivity(new Intent(AlarmClock.ACTION_SHOW_ALARMS));
                    break;
                case BuiltinWidgets.DATE:
                case BuiltinWidgets.MONTH:
                    startActivity(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR));
                    break;
                case BuiltinWidgets.BATTERY:
                    startActivity(new Intent(Intent.ACTION_POWER_USAGE_SUMMARY));
                    break;
                case BuiltinWidgets.SEARCH:
                    openSearch();
                    break;
                default:
                    break;
            }
        } catch (Throwable t) {
            toast("Aplikasi tidak tersedia");
        }
    }

    private List<int[]> sizeOptionsFor(LayoutStore.Item it) {
        ArrayList<int[]> out = new ArrayList<>();
        if (it.isExternal()) {
            AppWidgetProviderInfo info = null;
            try { info = awm.getAppWidgetInfo(it.wid); } catch (Throwable ignored) { }
            if (info != null) out.addAll(ExtWidgets.sizeOptions(info, metrics(), it.spanW, it.spanH));
            return out;
        }
        for (BuiltinWidgets.Spec sp : BuiltinWidgets.specsFor(it.wkind)) {
            int w = sp.widthFor(cols), h = sp.heightFor(rows);
            boolean dup = false;
            for (int[] o : out) if (o[0] == w && o[1] == h) dup = true;
            if (!dup) out.add(new int[]{w, h});
        }
        return out;
    }

    private void showWidgetMenu(final WidgetCell c, boolean arm) {
        haptic(c);
        List<Menus.Action> acts = new ArrayList<>();
        final LayoutStore.Item it = c.item;
        if (!edit) acts.add(new Menus.Action("Edit Layar Utama", new Runnable() { @Override public void run() { enterEdit(); } }));
        List<int[]> sizes = sizeOptionsFor(it);
        if (sizes.size() > 1) {
            for (final int[] sz : sizes) {
                boolean cur = sz[0] == it.spanW && sz[1] == it.spanH;
                acts.add(new Menus.Action((cur ? "\u2713  " : "") + "Ukuran " + BuiltinWidgets.sizeLabel(sz[0], sz[1]),
                        cur ? null : new Runnable() {
                            @Override public void run() {
                                store.resizeWidget(it, sz[0], sz[1]);
                                store.normalize(cols, rows, dockCap);
                                store.save();
                                rebuildAll();
                            }
                        }));
            }
        }
        acts.add(new Menus.Action("Hapus Widget", new Runnable() { @Override public void run() { removeHomeItem(it); } }, true));
        showMenu(rectOf(c), acts);
        if (arm) drag.arm(c, false);
    }

    private void confirmRemoveWidget(WidgetCell c) {
        List<Menus.Action> acts = new ArrayList<>();
        final LayoutStore.Item it = c.item;
        acts.add(new Menus.Action("Hapus Widget", new Runnable() { @Override public void run() { removeHomeItem(it); } }, true));
        acts.add(new Menus.Action("Batal", null));
        showMenu(rectOf(c), acts);
    }

    /** Hapus item dari Home; widget eksternal juga dilepas dari AppWidgetHost. */
    void removeHomeItem(LayoutStore.Item it) {
        if (it.isExternal() && it.wid >= 0) {
            try { widgetHost.deleteAppWidgetId(it.wid); } catch (Throwable ignored) { }
            hostViews.remove(it.wid);
        }
        store.removeItem(it);
        store.save();
        rebuildAll();
    }

    // ---- galeri widget

    void openWidgetPicker() {
        if (picker != null) return;
        dismissMenu();
        closePanel(false);
        closeSearch();
        picker = new WidgetPicker(this, this, prefs, dark, metrics(), root.getWidth(), root.getHeight(), insTop,
                Math.max(insBottom, 0), exec);
        overlayLayer.addView(picker, new FrameLayout.LayoutParams(-1, -1));
        doneBtn.setVisibility(View.GONE);
        addBtn.setVisibility(View.GONE);
    }

    @Override public void onPickerClosed(WidgetPicker p) {
        if (picker == p) picker = null;
        p.release();
        overlayLayer.removeView(p);
        if (edit && picker == null) {
            doneBtn.setVisibility(View.VISIBLE);
            addBtn.setVisibility(View.VISIBLE);
        }
    }

    @Override public void onPickBuiltin(BuiltinWidgets.Spec s) {
        placeWidget(LayoutStore.Item.widget(s.kind, s.widthFor(cols), s.heightFor(rows)));
    }

    /** Taruh widget baru di halaman aktif; bila tidak muat, coba halaman lain atau buat halaman baru. */
    private void placeWidget(LayoutStore.Item w) {
        int pref = U.clamp(workspace.page(), 0, Math.max(0, store.pages.size() - 1));
        int pg = store.addWidget(w, pref, cols, rows, prefs.widgetPlace() == 0);
        store.normalize(cols, rows, dockCap);
        store.save();
        rebuildAll();
        workspace.smoothTo(Math.min(pg, store.pages.size() - 1));
        toast("Widget ditambahkan");
    }

    @Override public void onPickExternal(AppWidgetProviderInfo info) {
        cancelPending();
        pendingInfo = info;
        try {
            pendingId = widgetHost.allocateAppWidgetId();
        } catch (Throwable t) {
            pendingInfo = null;
            toast("Tidak dapat menambahkan widget");
            return;
        }
        boolean ok = false;
        try { ok = awm.bindAppWidgetIdIfAllowed(pendingId, info.getProfile(), info.provider, null); } catch (Throwable ignored) { }
        if (ok) {
            afterBind();
            return;
        }
        Intent i = new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND);
        i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingId);
        i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider);
        i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.getProfile());
        try {
            startActivityForResult(i, REQ_BIND);
        } catch (Throwable t) {
            cancelPending();
            toast("Izin widget tidak dapat diminta");
        }
    }

    private void afterBind() {
        if (pendingInfo != null && pendingInfo.configure != null) {
            try {
                widgetHost.startAppWidgetConfigureActivityForResult(this, pendingId, 0, REQ_CONFIGURE, null);
                return;
            } catch (Throwable ignored) { /* lanjut tanpa layar konfigurasi */ }
        }
        finishAdd();
    }

    private void finishAdd() {
        AppWidgetProviderInfo info = pendingInfo;
        if (info == null && pendingId >= 0) {
            try { info = awm.getAppWidgetInfo(pendingId); } catch (Throwable ignored) { }
        }
        if (info == null || pendingId < 0) { cancelPending(); return; }
        int[] span = ExtWidgets.spanFor(info, metrics());
        LayoutStore.Item w = LayoutStore.Item.external(pendingId, info.provider.flattenToString(), span[0], span[1]);
        pendingId = -1;
        pendingInfo = null;
        placeWidget(w);
    }

    private void cancelPending() {
        if (pendingId >= 0) {
            try { widgetHost.deleteAppWidgetId(pendingId); } catch (Throwable ignored) { }
        }
        pendingId = -1;
        pendingInfo = null;
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        if (req == REQ_BIND || req == REQ_CONFIGURE) {
            int id = data == null ? -1 : data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
            if (pendingId < 0 && id >= 0) pendingId = id;
            if (pendingInfo == null && pendingId >= 0) {
                try { pendingInfo = awm.getAppWidgetInfo(pendingId); } catch (Throwable ignored) { }
            }
            if (res == RESULT_OK) {
                if (req == REQ_BIND) afterBind(); else finishAdd();
            } else {
                cancelPending();
            }
            return;
        }
        super.onActivityResult(req, res, data);
    }

    // ------------------------------------------------------------------ Pusat Notifikasi & Control Center

    private final TopSheet.Host sheetHost = new TopSheet.Host() {
        @Override public void onSheetClosed(TopSheet s) { removeSheet(s); }
        @Override public boolean reduceMotion() { return prefs.reduceMotion(); }
    };

    private void removeSheet(TopSheet s) {
        s.release();
        overlayLayer.removeView(s);
        if (sheet == s) sheet = null;
        if (pullSheet == s) pullSheet = null;
    }

    /** mode 1 = Pusat Notifikasi, 2 = Control Center. Lembar dibuat tertutup (progress 0). */
    private TopSheet makeSheet(int mode) {
        dismissMenu();
        TopSheet s;
        if (mode == 1) {
            s = new NotificationCenter(this, sheetHost, new NotificationCenter.Actions() {
                @Override public void openSettingsForAccess() {
                    try { startActivity(NotificationCenter.accessSettingsIntent()); }
                    catch (Throwable t) { toast("Tidak dapat membuka pengaturan"); }
                }
                @Override public AppRepo.AppInfo appFor(String pkg) { return repo.findByPackage(pkg); }
                @Override public Prefs prefs() { return prefs; }
                @Override public boolean dark() { return dark; }
            }, insTop, insBottom);
        } else {
            s = new ControlCenter(this, sheetHost, new ControlCenter.Actions() {
                @Override public boolean launch(Intent i) {
                    try { startActivity(i); return true; }
                    catch (Throwable t) { toast("Tidak dapat membuka pengaturan"); return false; }
                }
                @Override public void toast(String m) { LauncherActivity.this.toast(m); }
                @Override public void requestWriteSettings() {
                    try {
                        startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + getPackageName())));
                    } catch (Throwable t) { toast("Tidak dapat membuka pengaturan"); }
                }
            }, root.getWidth(), insTop, insBottom);
        }
        overlayLayer.addView(s, new FrameLayout.LayoutParams(-1, -1));
        sheet = s;
        return s;
    }

    void openSheet(int mode) {
        if (sheet != null || picker != null) return;
        closePanel(false);
        closeSearch();
        makeSheet(mode).open();
    }

    // ------------------------------------------------------------------ badge notifikasi

    @Override public void onNotificationsChanged() { refreshBadges(); }

    void refreshBadges() {
        if (workspace == null || repo == null) return;
        HashMap<String, Integer> counts = prefs.notifBadges() ? NotificationService.counts(getPackageName())
                : new HashMap<String, Integer>();
        applyBadges(dockLayout, counts);
        for (int i = 0; i < workspace.getChildCount(); i++) {
            View p = workspace.getChildAt(i);
            if (p instanceof CellLayout) applyBadges((CellLayout) p, counts);
        }
    }

    private void applyBadges(CellLayout cl, HashMap<String, Integer> counts) {
        for (int i = 0; i < cl.getChildCount(); i++) {
            View v = cl.getChildAt(i);
            if (!(v instanceof IconView)) continue;
            IconView iv = (IconView) v;
            int n = 0;
            if (iv.item != null && iv.item.type == LayoutStore.APP && iv.app != null) {
                Integer c = counts.get(iv.app.pkg);
                n = c == null ? 0 : c;
            } else if (iv.item != null && iv.item.type == LayoutStore.FOLDER) {
                for (String k : iv.item.kids) {
                    AppRepo.AppInfo a = repo.get(k);
                    Integer c = a == null ? null : counts.get(a.pkg);
                    if (c != null) n += c;
                }
            }
            iv.setBadge(n);
        }
    }

    private void openSettings() { startActivity(new Intent(this, SettingsActivity.class)); }

    // ------------------------------------------------------------------ ketuk dua kali, Pengalih Aplikasi, bilah status

    @Override public void onEmptyDoubleTap() {
        if (edit || switcher != null) return;
        switch (prefs.doubleTap()) {
            case 1: SPAccessibilityService.lockScreen(this); break;
            case 2: openSearch(); break;
            case 3: openSwitcher(); break;
            default: break;
        }
    }

    void openSwitcher() {
        if (switcher != null || root == null || root.getWidth() <= 0) return;
        dismissMenu();
        closeSearch();
        if (picker != null) picker.dismiss(false);
        if (sheet != null) removeSheet(sheet);
        if (edit) exitEdit();
        switcher = new AppSwitcher(this);
        root.addView(switcher, new FrameLayout.LayoutParams(-1, -1));
        switcher.open();
        haptic(switcher);
    }

    void closeSwitcher(boolean animate) {
        if (switcher == null) return;
        final AppSwitcher s = switcher;
        switcher = null;
        s.close(animate, new Runnable() { @Override public void run() { root.removeView(s); } });
    }

    /** Geser ke atas dari dock membuka Pengalih Aplikasi. */
    private float duX, duY;
    private boolean duOn;

    /** Geser ke atas di area Home (di atas Dock) membuka Laci Aplikasi bila diaktifkan di Pengaturan. */
    private void trackDrawerSwipe(MotionEvent ev) {
        if (prefs.drawerMode() != 0 || edit || switcher != null || search != null || picker != null || sheet != null || zero != null
                || menuScrim != null || panelDim != null || workspace.page() >= store.pages.size()) {
            duOn = false;
            return;
        }
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                float[] p = toRoot(ev.getRawX(), ev.getRawY());
                float bottom = prefs.showDock() ? dockTop() - U.dp(this, 6) : root.getHeight() - insBottom - U.dp(this, 30);
                duOn = p[1] > insTop + U.dp(this, 90) && p[1] < bottom;
                duX = p[0];
                duY = p[1];
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                if (!duOn) break;
                float[] q = toRoot(ev.getRawX(), ev.getRawY());
                float dy = q[1] - duY, dx = q[0] - duX;
                if (-dy > U.dp(this, 80) && -dy > Math.abs(dx) * 1.8f) {
                    duOn = false;
                    sendCancel();
                    openDrawer();
                }
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                duOn = false;
                break;
            default:
                break;
        }
    }

    private void trackDockSwipe(MotionEvent ev) {
        if (!prefs.dockSwipeSwitcher() || edit || switcher != null || zero != null) { dsOn = false; return; }
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                float[] p = toRoot(ev.getRawX(), ev.getRawY());
                dsOn = dockEnabled() && p[1] >= dockTop() - U.dp(this, 6);
                dsX = p[0];
                dsY = p[1];
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                if (!dsOn) break;
                float[] q = toRoot(ev.getRawX(), ev.getRawY());
                float dy = q[1] - dsY, dx = q[0] - dsX;
                if (-dy > U.dp(this, 56) && -dy > Math.abs(dx) * 1.5f) {
                    dsOn = false;
                    sendCancel();
                    openSwitcher();
                }
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dsOn = false;
                break;
            default:
                break;
        }
    }

    private void applyStatusBar() {
        try {
            WindowInsetsController c = getWindow().getInsetsController();
            if (c == null) return;
            if (prefs.hideStatusBar()) {
                c.hide(WindowInsets.Type.statusBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            } else {
                c.show(WindowInsets.Type.statusBars());
            }
        } catch (Throwable ignored) { }
    }
}
