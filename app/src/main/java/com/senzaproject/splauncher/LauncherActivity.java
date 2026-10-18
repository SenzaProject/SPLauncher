package com.senzaproject.splauncher;

import android.app.Activity;
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
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.animation.DecelerateInterpolator;
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
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Layar Home SP Launcher. */
public class LauncherActivity extends Activity implements Workspace.Listener, LibraryView.Host, SearchOverlay.Host {

    static final class WallView extends View {
        Bitmap bmp;
        WallView(Context c) { super(c); }
        void set(Bitmap b) { bmp = b; invalidate(); }
        @Override protected void onDraw(Canvas c) {
            if (bmp != null) c.drawBitmap(bmp, 0, 0, null); else c.drawColor(0xFF000000);
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
    GlassLayout doneBtn;
    DragController drag;

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
    private LauncherApps.Callback pkgCallback;
    private int seenLayoutRev;

    // ------------------------------------------------------------------ siklus hidup

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new Prefs(this);
        repo = new AppRepo(this);
        store = new LayoutStore(this);
        seenLayoutRev = prefs.layoutRev();
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

    @Override protected void onDestroy() {
        super.onDestroy();
        try { repo.la.unregisterCallback(pkgCallback); } catch (Throwable ignored) { }
        exec.shutdownNow();
    }

    @Override protected void onResume() {
        super.onResume();
        if (prefs.layoutRev() != seenLayoutRev) { seenLayoutRev = prefs.layoutRev(); store.reset(); }
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
            dismissMenu();
            closePanel(false);
            closeSearch();
            if (edit) exitEdit();
            if (workspace.page() != 0) workspace.smoothTo(0);
        }
    }

    @Override public void onBackPressed() {
        if (menuScrim != null) dismissMenu();
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
        float label = prefs.labels() ? U.dpf(this, 5) + U.sp(this, 11) * 1.3f : 0f;
        float nat = iconSize + label + U.dpf(this, 22);
        rows = U.clamp((int) (availH / nat), land ? 2 : 4, 8);
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
        loadWallpaperIfNeeded(w, h);
        store.ensure(this, repo, prefs, pageCap, dockCap);

        int keep = workspace.page();
        dismissMenu();
        workspace.removeAllViews();
        for (ArrayList<LayoutStore.Item> page : store.pages) workspace.addView(newPage(page, false));
        library = new LibraryView(this);
        library.rebuild(repo.all(), prefs, dark, insTop, insBottom, w, this);
        workspace.addView(library);
        workspace.setHomeCount(store.pages.size());
        workspace.setPage(Math.min(keep, workspace.getChildCount() - 1));

        // dock
        boolean showDock = prefs.showDock();
        dockPanel.setVisibility(showDock ? View.VISIBLE : View.GONE);
        dockPanel.setGlass(30, Math.max(0.22f, prefs.glassOpacity() / 100f * 0.9f), dark);
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
        FrameLayout.LayoutParams dn = (FrameLayout.LayoutParams) doneBtn.getLayoutParams();
        dn.topMargin = insTop + U.dp(this, 8);
        dn.rightMargin = insRight + U.dp(this, 16);
        doneBtn.setLayoutParams(dn);
        doneBtn.setGlass(17, 0.40f, true);

        onScrollFraction(workspace.getScrollX() / (float) Math.max(1, w));
        if (edit) applyEdit(true);
    }

    private CellLayout newPage(List<LayoutStore.Item> items, boolean unused) {
        CellLayout cl = new CellLayout(this);
        cl.configure(cols, rows, cellW, cellH, padL(), padT());
        for (LayoutStore.Item it : items) cl.addView(newIcon(it, false));
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
            Glass.reduce = prefs.reduceTransparency();
            return;
        }
        wallLoading = true;
        final int blur = prefs.blur();
        final boolean reduce = prefs.reduceTransparency();
        exec.execute(new Runnable() {
            @Override public void run() {
                final Bitmap bmp = Wallpapers.render(LauncherActivity.this, prefs, w, h);
                main.post(new Runnable() {
                    @Override public void run() {
                        wallLoading = false;
                        loadedWallId = id; loadedWallRev = rev; loadedW = w; loadedH = h; loadedBlur = blur;
                        wallView.set(bmp);
                        Glass.setBackdrop(bmp, blur, reduce);
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
    int pageIndexOf(IconView v) {
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
        edit = true;
        applyEdit(true);
    }

    void exitEdit() {
        if (!edit) return;
        edit = false;
        applyEdit(false);
        store.normalize(pageCap, dockCap);
        store.save();
        rebuildAll();
    }

    private void applyEdit(boolean on) {
        doneBtn.setVisibility(on ? View.VISIBLE : View.GONE);
        workspace.setPullEnabled(!on);
        workspace.setLongPressEnabled(!on);
        for (int i = 0; i < workspace.getChildCount(); i++) {
            View p = workspace.getChildAt(i);
            if (p instanceof CellLayout) setEditOn((CellLayout) p, on);
        }
        setEditOn(dockLayout, on);
    }

    private void setEditOn(CellLayout cl, boolean on) {
        for (int i = 0; i < cl.getChildCount(); i++) ((IconView) cl.getChildAt(i)).setEdit(on);
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
        acts.add(new Menus.Action("Edit Layar Utama", new Runnable() { @Override public void run() { enterEdit(); } }));
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
        drag.arm(v, false);
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
                @Override public void run() { store.addToHome(a.key); store.normalize(pageCap, dockCap); store.save(); rebuildAll(); toast("Ditambahkan ke Home Screen"); }
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
        if (!prefs.reduceMotion()) {
            m.setPivotX(anchor.centerX() - left);
            m.setPivotY(above ? mh : 0);
            m.setScaleX(0.8f); m.setScaleY(0.8f); m.setAlpha(0f);
            m.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(170).setInterpolator(new DecelerateInterpolator()).start();
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
        final GlassLayout box = new GlassLayout(this, 32, dark ? 0.55f : 0.62f, dark);
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

        if (!prefs.reduceMotion()) {
            final RectF ar = rectOf(anchor);
            dim.setAlpha(0f);
            box.setScaleX(0.3f); box.setScaleY(0.3f);
            box.post(new Runnable() {
                @Override public void run() {
                    float px = ar.centerX() - box.getLeft(), py = ar.centerY() - box.getTop();
                    box.setPivotX(px); box.setPivotY(py);
                    dim.animate().alpha(1f).setDuration(220).start();
                    box.animate().scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(new DecelerateInterpolator()).start();
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
        if (animate && !prefs.reduceMotion()) {
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
        if (!prefs.reduceMotion()) {
            search.setAlpha(0f);
            search.animate().alpha(1f).setDuration(200).start();
        }
        search.post(new Runnable() { @Override public void run() { if (search != null) search.focusInput(); } });
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

    @Override public void onPullDown(float x, float y) { openSearch(); }

    @Override public void onEmptyLongPress(float x, float y) {
        List<Menus.Action> acts = new ArrayList<>();
        acts.add(new Menus.Action("Edit Layar Utama", new Runnable() { @Override public void run() { enterEdit(); } }));
        acts.add(new Menus.Action("Wallpaper & Tampilan", new Runnable() { @Override public void run() { openSettings(); } }));
        acts.add(new Menus.Action("Pengaturan SP Launcher", new Runnable() { @Override public void run() { openSettings(); } }));
        showMenu(new RectF(x, y, x, y), acts);
    }

    private void openSettings() { startActivity(new Intent(this, SettingsActivity.class)); }
}
