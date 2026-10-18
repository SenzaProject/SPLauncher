package com.senzaproject.splauncher;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/** Halaman grid: anak ke-i menempati slot ke-i (kiri-kanan, atas-bawah). */
final class CellLayout extends ViewGroup {
    int cols = 4, rows = 6, cellW = 100, cellH = 120, padL = 0, padT = 0;

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
    float slotX(int i) { return padL + (i % cols) * cellW; }
    float slotY(int i) { return padT + (i / cols) * cellH; }

    @Override protected void onMeasure(int wSpec, int hSpec) {
        setMeasuredDimension(MeasureSpec.getSize(wSpec), MeasureSpec.getSize(hSpec));
        int cw = MeasureSpec.makeMeasureSpec(cellW, MeasureSpec.EXACTLY);
        int ch = MeasureSpec.makeMeasureSpec(cellH, MeasureSpec.EXACTLY);
        for (int i = 0; i < getChildCount(); i++) getChildAt(i).measure(cw, ch);
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            int x = Math.round(slotX(i)), y = Math.round(slotY(i));
            v.layout(x, y, x + cellW, y + cellH);
        }
    }

    /** Indeks sisip untuk titik lokal (dibatasi ke grid). */
    int indexAt(float lx, float ly) {
        int col = U.clamp((int) ((lx - padL) / cellW), 0, cols - 1);
        int row = U.clamp((int) ((ly - padT) / cellH), 0, rows - 1);
        return row * cols + col;
    }

    private int pvExclude = -1, pvInsert = -1;

    /** Indeks anak (selain exclude) yang pusatnya (posisi tampil saat ini) dekat titik lokal; selain itu -1. */
    int hitZone(float lx, float ly, int exclude) {
        int j = 0;
        for (int k = 0; k < getChildCount(); k++) {
            if (k == exclude) continue;
            int slot = pvInsert >= 0 ? (j >= pvInsert ? j + 1 : j) : k;
            j++;
            float cx = slotX(slot) + cellW / 2f, cy = slotY(slot) + cellH / 2f;
            if (Math.abs(lx - cx) < cellW * 0.30f && Math.abs(ly - cy) < cellH * 0.28f) return k;
        }
        return -1;
    }

    /** Geser anak lain agar tercipta celah di insertIdx (dalam daftar tanpa excludeIdx). */
    void preview(int excludeIdx, int insertIdx) {
        pvExclude = excludeIdx;
        pvInsert = insertIdx;
        int j = 0;
        for (int k = 0; k < getChildCount(); k++) {
            if (k == excludeIdx) continue;
            int slot = j >= insertIdx ? j + 1 : j;
            View v = getChildAt(k);
            float tx = slotX(slot) - slotX(k), ty = slotY(slot) - slotY(k);
            if (v.getTranslationX() != tx || v.getTranslationY() != ty) {
                v.animate().translationX(tx).translationY(ty).setDuration(190).start();
            }
            j++;
        }
    }

    int previewInsert() { return pvInsert; }

    void clearPreview() {
        pvExclude = -1;
        pvInsert = -1;
        for (int k = 0; k < getChildCount(); k++) {
            View v = getChildAt(k);
            if (v.getTranslationX() != 0 || v.getTranslationY() != 0) {
                v.animate().translationX(0).translationY(0).setDuration(170).start();
            }
        }
    }
}
