package com.senzaproject.splauncher;

import android.appwidget.AppWidgetProviderInfo;
import android.os.Build;

import java.util.ArrayList;
import java.util.List;

/** Perhitungan ukuran sel untuk widget eksternal (AppWidgetProvider). */
final class ExtWidgets {
    private ExtWidgets() {}

    /** Metrik grid Home yang dibutuhkan untuk menghitung ukuran widget. */
    static final class Metrics {
        int cols, rows, cellW, cellH, insetX, insetY;
        int widthPx(int sw) { return Math.max(1, Math.min(sw, cols)) * cellW - 2 * insetX; }
        int heightPx(int sh) { return Math.max(1, Math.min(sh, rows)) * cellH - 2 * insetY; }
    }

    private static int cells(int px, int cell, int inset, int max) {
        int n = (int) Math.ceil((px + 2.0 * inset) / Math.max(1, cell));
        return U.clamp(n, 1, max);
    }

    /** Ukuran bawaan (kolom, baris) sebuah widget eksternal pada grid ini. */
    static int[] spanFor(AppWidgetProviderInfo info, Metrics m) {
        int sw, sh;
        if (Build.VERSION.SDK_INT >= 31 && info.targetCellWidth > 0 && info.targetCellHeight > 0) {
            sw = U.clamp(info.targetCellWidth, 1, m.cols);
            sh = U.clamp(info.targetCellHeight, 1, m.rows);
        } else {
            sw = cells(Math.max(info.minWidth, 1), m.cellW, m.insetX, m.cols);
            sh = cells(Math.max(info.minHeight, 1), m.cellH, m.insetY, m.rows);
        }
        // pastikan konten minimum muat
        while (sw < m.cols && m.widthPx(sw) < info.minWidth) sw++;
        while (sh < m.rows && m.heightPx(sh) < info.minHeight) sh++;
        return new int[]{sw, sh};
    }

    /** Boleh diubah menjadi sw x sh? */
    static boolean okSize(AppWidgetProviderInfo info, Metrics m, int sw, int sh) {
        int[] def = spanFor(info, m);
        boolean rh = (info.resizeMode & AppWidgetProviderInfo.RESIZE_HORIZONTAL) != 0;
        boolean rv = (info.resizeMode & AppWidgetProviderInfo.RESIZE_VERTICAL) != 0;
        if (!rh && sw != def[0]) return false;
        if (!rv && sh != def[1]) return false;
        int minW = info.minResizeWidth > 0 ? Math.min(info.minResizeWidth, info.minWidth) : info.minWidth;
        int minH = info.minResizeHeight > 0 ? Math.min(info.minResizeHeight, info.minHeight) : info.minHeight;
        if (m.widthPx(sw) < minW || m.heightPx(sh) < minH) return false;
        if (info.maxResizeWidth > 0 && m.widthPx(sw) > info.maxResizeWidth && sw != def[0]) return false;
        if (info.maxResizeHeight > 0 && m.heightPx(sh) > info.maxResizeHeight && sh != def[1]) return false;
        return true;
    }

    /** Pilihan ukuran untuk menu "ubah ukuran" (selalu menyertakan ukuran bawaan dan ukuran sekarang). */
    static List<int[]> sizeOptions(AppWidgetProviderInfo info, Metrics m, int curW, int curH) {
        int[][] presets = {{1, 1}, {2, 1}, {2, 2}, {4, 1}, {4, 2}, {2, 3}, {4, 3}, {2, 4}, {4, 4}};
        ArrayList<int[]> out = new ArrayList<>();
        for (int[] p : presets) {
            if (p[0] > m.cols || p[1] > m.rows) continue;
            if (okSize(info, m, p[0], p[1])) out.add(p);
        }
        boolean hasCur = false;
        for (int[] p : out) if (p[0] == curW && p[1] == curH) hasCur = true;
        if (!hasCur) out.add(new int[]{curW, curH});
        return out;
    }
}
