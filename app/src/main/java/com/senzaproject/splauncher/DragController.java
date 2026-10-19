package com.senzaproject.splauncher;

import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.util.ArrayList;

/** Mengelola seret-dan-lepas ikon, folder, dan widget di Home Screen (Dock hanya untuk ikon/folder). */
final class DragController {
    private static final int IDLE = 0, ARMED = 1, DRAGGING = 2, DROPPING = 3;
    private static final int NONE = -2, DOCK = -1;

    private final LauncherActivity a;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final float slop;

    private int state = IDLE;
    private View src, proxy;
    private LayoutStore.Item item;
    private ArrayList<LayoutStore.Item> srcList;
    private float fingerX, fingerY, startX, startY, offX, offY;

    private CellLayout curLayout;
    private int curWhere = NONE;
    private int insertIdx = -1;
    private int hoverIdx = -1;
    private IconView mergeView;
    private LayoutStore.Item mergeTarget;
    private int edgeDir = 0;

    private final Runnable hoverRun = new Runnable() {
        @Override public void run() {
            if (state != DRAGGING || curLayout == null || hoverIdx < 0 || hoverIdx >= curLayout.getChildCount()) return;
            View v = curLayout.getChildAt(hoverIdx);
            if (!(v instanceof IconView)) return;
            mergeView = (IconView) v;
            mergeTarget = mergeView.item;
            mergeView.setMerge(true);
            a.haptic(mergeView);
        }
    };

    private final Runnable edgeRun = new Runnable() {
        @Override public void run() {
            if (state != DRAGGING || edgeDir == 0) return;
            int dir = edgeDir;
            int pg = a.workspace.page();
            int np = pg + dir;
            int homes = a.store.pages.size();
            if (np < 0) { edgeDir = 0; return; }
            if (np >= homes) {
                // melewati halaman Home terakhir: buat halaman baru bila halaman terakhir tidak kosong
                if (dir > 0 && pg == homes - 1 && !(a.store.pages.get(homes - 1).isEmpty())) {
                    a.addEmptyPage();
                } else { edgeDir = 0; return; }
            }
            a.workspace.smoothTo(np);
            edgeDir = 0;
            h.postDelayed(new Runnable() { @Override public void run() { if (state == DRAGGING) move(); } }, 380);
        }
    };

    DragController(LauncherActivity a) {
        this.a = a;
        slop = ViewConfiguration.get(a).getScaledTouchSlop();
    }

    boolean active() { return state != IDLE; }

    private int spanW() { return item.type == LayoutStore.WIDGET ? Math.max(1, item.spanW) : 1; }
    private int spanH() { return item.type == LayoutStore.WIDGET ? Math.max(1, item.spanH) : 1; }

    /** Dipanggil saat tekan-lama pada ikon/widget Home. immediate=true pada mode edit. */
    void arm(View v, boolean immediate) {
        if (state != IDLE || !(v instanceof ItemHolder)) return;
        LayoutStore.Item it = ((ItemHolder) v).getItem();
        if (it == null) return;
        int pg = a.pageIndexOf(v);
        if (pg == -2) return;
        src = v;
        item = it;
        srcList = pg == -1 ? a.store.dock : a.store.pages.get(pg);
        float[] p = a.toRoot(a.lastRawX, a.lastRawY);
        startX = fingerX = p[0];
        startY = fingerY = p[1];
        state = ARMED;
        a.sendCancel();
        if (immediate) beginDrag();
    }

    boolean onTouch(MotionEvent ev) {
        float[] p = a.toRoot(ev.getRawX(), ev.getRawY());
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                fingerX = p[0]; fingerY = p[1];
                if (state == ARMED) {
                    float dx = fingerX - startX, dy = fingerY - startY;
                    if (dx * dx + dy * dy > slop * slop * 2.25f) beginDrag();
                }
                if (state == DRAGGING) move();
                break;
            case MotionEvent.ACTION_UP:
                fingerX = p[0]; fingerY = p[1];
                if (state == DRAGGING) drop();
                else if (state == ARMED) state = IDLE;
                break;
            case MotionEvent.ACTION_CANCEL:
                if (state == DRAGGING) revert();
                else if (state == ARMED) state = IDLE;
                break;
        }
        return true;
    }

    private void beginDrag() {
        a.dismissMenu();
        if (!a.isEdit()) a.enterEdit();
        int[] loc = new int[2];
        src.getLocationInWindow(loc);
        float scale;
        if (src instanceof WidgetCell) {
            ImageView iv = new ImageView(a);
            iv.setImageBitmap(((WidgetCell) src).snapshot());
            iv.setScaleType(ImageView.ScaleType.FIT_XY);
            proxy = iv;
            scale = 1.04f;
        } else {
            IconView ip = new IconView(a);
            a.bindIcon(ip, item, a.pageIndexOf(src) == -1);
            ip.setClickable(false);
            proxy = ip;
            scale = 1.14f;
        }
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(src.getWidth(), src.getHeight());
        a.dragLayer.addView(proxy, lp);
        proxy.setX(loc[0]);
        proxy.setY(loc[1]);
        proxy.setScaleX(scale);
        proxy.setScaleY(scale);
        proxy.setAlpha(0.94f);
        proxy.setElevation(U.dpf(a, 12));
        glassOffset();
        offX = loc[0] + src.getWidth() / 2f - fingerX;
        offY = loc[1] + src.getHeight() / 2f - fingerY;
        src.setAlpha(0f);
        state = DRAGGING;
        a.haptic(src);
        move();
    }

    /** Proxy dipindah dengan translasi: beri tahu backdrop kaca posisi tampil sebenarnya. */
    private void glassOffset() {
        if (proxy instanceof IconView) ((IconView) proxy).setGlassOffset(Math.round(proxy.getX()), Math.round(proxy.getY()));
    }

    private void resetHover() {
        h.removeCallbacks(hoverRun);
        hoverIdx = -1;
        if (mergeView != null) { mergeView.setMerge(false); mergeView = null; }
        mergeTarget = null;
    }

    private void move() {
        float cx = fingerX + offX, cy = fingerY + offY;
        proxy.setX(cx - proxy.getWidth() / 2f);
        proxy.setY(cy - proxy.getHeight() / 2f);
        glassOffset();

        // tentukan target
        CellLayout target = null;
        int where = NONE;
        boolean dockOk = item.type != LayoutStore.WIDGET;
        if (dockOk && a.dockEnabled() && cy > a.dockTop() - U.dpf(a, 6)) {
            target = a.dockLayout; where = DOCK;
        } else {
            int pg = a.workspace.page();
            if (pg < a.store.pages.size()) { target = a.pageLayout(pg); where = pg; }
        }
        boolean same = target != null && target == src.getParent();
        if (target != null && where == DOCK && !same && target.getChildCount() >= a.dockCap) {
            target = null; where = NONE; // dock penuh
        }
        if (target != curLayout) {
            if (curLayout != null) curLayout.clearPreview();
            resetHover();
            insertIdx = -1;
            curLayout = target;
        }
        curWhere = where;

        // gulir tepi
        float w = a.root.getWidth();
        int dir = cx < U.dpf(a, 30) ? -1 : (cx > w - U.dpf(a, 30) ? 1 : 0);
        if (dir != edgeDir) {
            h.removeCallbacks(edgeRun);
            edgeDir = dir;
            if (dir != 0) h.postDelayed(edgeRun, 650);
        }

        if (curLayout == null) return;
        float[] lp = a.toLocal(curLayout, cx, cy);
        int srcChild = same ? curLayout.indexOfChild(src) : -1;

        int zone = item.type == LayoutStore.APP ? curLayout.hitZone(lp[0], lp[1], srcChild) : -1;
        if (zone >= 0) {
            if (zone != hoverIdx) {
                resetHover();
                hoverIdx = zone;
                h.postDelayed(hoverRun, 450);
            }
        } else {
            if (hoverIdx != -1 || mergeView != null) resetHover();
            int ni = curLayout.insertIndexFor(lp[0], lp[1], srcChild, spanW(), spanH());
            if (ni != insertIdx) {
                insertIdx = ni;
                curLayout.preview(srcChild, ni, spanW(), spanH());
            }
        }
    }

    private void drop() {
        h.removeCallbacks(hoverRun);
        h.removeCallbacks(edgeRun);
        if (curLayout == null || curWhere == NONE) { revert(); return; }
        final ArrayList<LayoutStore.Item> dest = curWhere == DOCK ? a.store.dock : a.store.pages.get(curWhere);
        float tx, ty, ts = 1f, ta = 0.94f;
        if (mergeTarget != null && mergeView != null && mergeView.getParent() == curLayout && item.type == LayoutStore.APP) {
            int[] ml = new int[2];
            mergeView.getLocationInWindow(ml);
            tx = ml[0]; ty = ml[1]; ts = 0.35f; ta = 0f;
            srcList.remove(item);
            if (mergeTarget.type == LayoutStore.APP) {
                LayoutStore.Item f = LayoutStore.Item.folder("Folder");
                f.kids.add(mergeTarget.key);
                f.kids.add(item.key);
                int idx = dest.indexOf(mergeTarget);
                if (idx >= 0) dest.set(idx, f); else dest.add(f);
            } else if (!mergeTarget.kids.contains(item.key)) {
                mergeTarget.kids.add(item.key);
            }
        } else {
            boolean same = curLayout == src.getParent();
            int srcChild = same ? curLayout.indexOfChild(src) : -1;
            int idx = insertIdx < 0 ? dest.size() : insertIdx;
            if (same && insertIdx < 0) idx = Math.max(0, dest.size() - 1);
            int slot = curLayout.slotForInsert(srcChild, idx, spanW(), spanH());
            if (slot < 0) slot = Math.max(0, curLayout.slotOfChild(Math.max(0, curLayout.getChildCount() - 1)));
            int[] cl = new int[2];
            curLayout.getLocationInWindow(cl);
            tx = cl[0] + curLayout.slotX(slot) + (src.getWidth() - proxy.getWidth()) / 2f;
            ty = cl[1] + curLayout.slotY(slot) + (src.getHeight() - proxy.getHeight()) / 2f;
            srcList.remove(item);
            dest.add(Math.min(idx, dest.size()), item);
        }
        state = DROPPING;
        h.removeCallbacks(hoverRun);
        proxy.animate().x(tx).y(ty).scaleX(ts).scaleY(ts).alpha(ta).setDuration(a.prefs.reduceMotion() ? 0 : 200)
                .withEndAction(new Runnable() {
                    @Override public void run() {
                        a.dragLayer.removeView(proxy);
                        proxy = null;
                        state = IDLE;
                        resetHover();
                        curLayout = null;
                        insertIdx = -1;
                        a.store.normalize(a.cols, a.rows, a.dockCap);
                        a.store.save();
                        a.rebuildAll();
                    }
                }).start();
        a.haptic(proxy);
    }

    private void revert() {
        h.removeCallbacks(hoverRun);
        h.removeCallbacks(edgeRun);
        if (curLayout != null) curLayout.clearPreview();
        resetHover();
        state = DROPPING;
        int[] loc = new int[2];
        src.getLocationInWindow(loc);
        proxy.animate().x(loc[0]).y(loc[1]).scaleX(1f).scaleY(1f).alpha(1f)
                .setDuration(a.prefs.reduceMotion() ? 0 : 200)
                .withEndAction(new Runnable() {
                    @Override public void run() {
                        a.dragLayer.removeView(proxy);
                        proxy = null;
                        src.setAlpha(1f);
                        state = IDLE;
                        curLayout = null;
                        insertIdx = -1;
                    }
                }).start();
    }
}
