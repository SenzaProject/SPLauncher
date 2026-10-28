package com.senzaproject.splauncher;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/**
 * Halaman grid. Anak ke-i = item ke-i pada model; posisinya ditentukan oleh {@link GridPacker}
 * (ikon 1x1 mengisi sel kosong, widget mengambil beberapa sel).
 */
final class CellLayout extends ViewGroup {
    int cols = 4, rows = 6, cellW = 100, cellH = 120, padL = 0, padT = 0;

    /** Slot (baris*cols+kolom) pojok kiri-atas tiap anak pada susunan saat ini. */
    private int[] slots = new int[0];
    private int[] spW = new int[0], spH = new int[0];

    CellLayout(Context c) {
        super(c);
        setClipChildren(false);
        setClipToPadding(false);
    }

    void configure(int cols, int rows, int cellW, int cellH, int padL, int padT) {
        this.cols = cols; this.rows = rows; this.cellW = cellW; this.cellH = cellH; this.padL = padL; this.padT = padT;
        requestLayout();
    }

    int capacity() { return cols * rows; }
    float slotX(int slot) { return padL + (slot % cols) * cellW; }
    float slotY(int slot) { return padT + (slot / cols) * cellH; }

    private int spanOf(View v, boolean horizontal) {
        if (v instanceof ItemHolder) {
            LayoutStore.Item it = ((ItemHolder) v).getItem();
            if (it != null) return Math.max(1, Math.min(horizontal ? it.spanW : it.spanH, horizontal ? cols : rows));
        }
        return 1;
    }

    private void repack() {
        int n = getChildCount();
        spW = new int[n]; spH = new int[n];
        for (int i = 0; i < n; i++) {
            View v = getChildAt(i);
            spW[i] = spanOf(v, true); spH[i] = spanOf(v, false);
        }
        slots = GridPacker.pack(cols, rows, spW, spH);
    }

    /** Slot anak ke-i pada susunan saat ini (-1 bila tidak muat). */
    int slotOfChild(int i) { return i >= 0 && i < slots.length ? slots[i] : -1; }

    @Override protected void onMeasure(int wSpec, int hSpec) {
        setMeasuredDimension(MeasureSpec.getSize(wSpec), MeasureSpec.getSize(hSpec));
        repack();
        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).measure(MeasureSpec.makeMeasureSpec(spW[i] * cellW, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(spH[i] * cellH, MeasureSpec.EXACTLY));
        }
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (slots.length != getChildCount()) repack();
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            if (slots[i] < 0) { v.layout(0, 0, 0, 0); continue; }
            int x = Math.round(slotX(slots[i])), y = Math.round(slotY(slots[i]));
            v.layout(x, y, x + spW[i] * cellW, y + spH[i] * cellH);
        }
    }

    // ------------------------------------------------------------------ seret & lepas

    /** Susunan slot bila anak `exclude` dikeluarkan dan placeholder (sw x sh) disisipkan di `insert` (-1 = tanpa). */
    private int[] packWith(int exclude, int insert, int sw, int sh, int[] childOut) {
        int n = getChildCount();
        if (spW.length != n) repack();
        int m = n - (exclude >= 0 ? 1 : 0) + (insert >= 0 ? 1 : 0);
        int[] w = new int[m], h = new int[m], map = new int[m];
        int j = 0;
        for (int k = 0; k < n; k++) {
            if (k == exclude) continue;
            if (insert >= 0 && j == insert) { w[j] = sw; h[j] = sh; map[j] = -1; j++; }
            w[j] = spW.length > k ? spW[k] : 1; h[j] = spH.length > k ? spH[k] : 1; map[j] = k; j++;
        }
        if (insert >= 0 && j == insert) { w[j] = sw; h[j] = sh; map[j] = -1; j++; }
        int[] packed = GridPacker.pack(cols, rows, w, h);
        int placeholder = -1;
        if (childOut != null) java.util.Arrays.fill(childOut, -1);
        for (int q = 0; q < m; q++) {
            if (map[q] < 0) placeholder = packed[q];
            else if (childOut != null) childOut[map[q]] = packed[q];
        }
        return new int[]{placeholder};
    }

    private int[] pvSlots = null;
    private int pvExclude = -1, pvInsert = -1, pvInsertSlot = -1;

    /**
     * Indeks sisip (dalam daftar tanpa `excludeIdx`) untuk item berukuran sw x sh yang pusatnya ada di
     * titik lokal (lx, ly): item disisipkan sebelum item pertama yang slot-nya >= sel tujuan.
     */
    int insertIndexFor(float lx, float ly, int excludeIdx, int sw, int sh) {
        sw = Math.max(1, Math.min(sw, cols)); sh = Math.max(1, Math.min(sh, rows));
        int c = U.clamp(Math.round((lx - sw * cellW / 2f - padL) / cellW), 0, cols - sw);
        int r = U.clamp(Math.round((ly - sh * cellH / 2f - padT) / cellH), 0, rows - sh);
        int target = r * cols + c;
        int n = getChildCount();
        int[] others = new int[n];
        packWith(excludeIdx, -1, 1, 1, others);
        int idx = 0;
        for (int k = 0; k < n; k++) {
            if (k == excludeIdx) continue;
            if (others[k] < 0 || others[k] >= target) return idx;
            idx++;
        }
        return idx;
    }

    /** Slot yang akan ditempati item sisipan (sw x sh) pada indeks `insert`; -1 bila tidak muat. */
    int slotForInsert(int excludeIdx, int insert, int sw, int sh) {
        sw = Math.max(1, Math.min(sw, cols)); sh = Math.max(1, Math.min(sh, rows));
        return packWith(excludeIdx, insert, sw, sh, null)[0];
    }

    /** Indeks anak ikon (selain exclude) yang pusat tampilnya dekat titik lokal; selain itu -1. */
    int hitZone(float lx, float ly, int exclude) { return hitZone(lx, ly, exclude, false); }

    /** foldersOnly = true: hanya folder yang dianggap target (saat menyeret folder untuk digabung). */
    int hitZone(float lx, float ly, int exclude, boolean foldersOnly) {
        for (int k = 0; k < getChildCount(); k++) {
            if (k == exclude) continue;
            View v = getChildAt(k);
            if (!(v instanceof IconView)) continue;
            if (foldersOnly && !((IconView) v).isFolder()) continue;
            int slot = pvSlots != null && k < pvSlots.length ? pvSlots[k] : slotOfChild(k);
            if (slot < 0) continue;
            float cx = slotX(slot) + cellW / 2f, cy = slotY(slot) + cellH / 2f;
            if (Math.abs(lx - cx) < cellW * 0.30f && Math.abs(ly - cy) < cellH * 0.28f) return k;
        }
        return -1;
    }

    /** Geser anak lain agar tercipta celah untuk item sw x sh di insertIdx (dalam daftar tanpa excludeIdx). */
    void preview(int excludeIdx, int insertIdx, int sw, int sh) {
        sw = Math.max(1, Math.min(sw, cols)); sh = Math.max(1, Math.min(sh, rows));
        pvExclude = excludeIdx;
        pvInsert = insertIdx;
        int[] mapped = new int[getChildCount()];
        pvInsertSlot = packWith(excludeIdx, insertIdx, sw, sh, mapped)[0];
        pvSlots = mapped;
        for (int k = 0; k < getChildCount(); k++) {
            if (k == excludeIdx || mapped[k] < 0 || slotOfChild(k) < 0) continue;
            View v = getChildAt(k);
            float tx = slotX(mapped[k]) - slotX(slotOfChild(k)), ty = slotY(mapped[k]) - slotY(slotOfChild(k));
            if (v.getTranslationX() != tx || v.getTranslationY() != ty) {
                v.animate().translationX(tx).translationY(ty).setDuration(190).start();
            }
        }
    }

    int previewInsert() { return pvInsert; }
    int previewInsertSlot() { return pvInsertSlot; }

    void clearPreview() {
        pvExclude = -1;
        pvInsert = -1;
        pvInsertSlot = -1;
        pvSlots = null;
        for (int k = 0; k < getChildCount(); k++) {
            View v = getChildAt(k);
            if (v.getTranslationX() != 0 || v.getTranslationY() != 0) {
                v.animate().translationX(0).translationY(0).setDuration(170).start();
            }
        }
    }
}
