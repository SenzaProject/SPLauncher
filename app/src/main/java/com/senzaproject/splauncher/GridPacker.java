package com.senzaproject.splauncher;

/**
 * Penempatan item pada grid halaman Home. Item ditempatkan berurutan; tiap item mengambil posisi pertama
 * (kiri-kanan, atas-bawah) yang muat untuk ukuran span-nya. Ikon 1x1 otomatis mengisi sel kosong yang
 * tersisa, jadi urutan daftar = urutan baca, dan widget dapat berada di antara ikon.
 * Kelas ini murni Java (tanpa API Android) agar mudah diuji.
 */
final class GridPacker {
    private GridPacker() {}

    /** @return slot (baris*kolom + kolom) pojok kiri-atas tiap item, atau -1 bila tidak muat. */
    static int[] pack(int cols, int rows, int[] sw, int[] sh) {
        int n = sw.length;
        int[] out = new int[n];
        boolean[] used = new boolean[Math.max(1, cols * rows)];
        for (int i = 0; i < n; i++) {
            int w = Math.max(1, Math.min(sw[i], cols)), h = Math.max(1, Math.min(sh[i], rows));
            out[i] = -1;
            search:
            for (int r = 0; r + h <= rows; r++) {
                for (int c = 0; c + w <= cols; c++) {
                    if (free(used, cols, c, r, w, h)) {
                        for (int y = r; y < r + h; y++) for (int x = c; x < c + w; x++) used[y * cols + x] = true;
                        out[i] = r * cols + c;
                        break search;
                    }
                }
            }
        }
        return out;
    }

    private static boolean free(boolean[] used, int cols, int c, int r, int w, int h) {
        for (int y = r; y < r + h; y++) for (int x = c; x < c + w; x++) if (used[y * cols + x]) return false;
        return true;
    }

    static boolean fits(int cols, int rows, int[] sw, int[] sh) {
        int[] s = pack(cols, rows, sw, sh);
        for (int v : s) if (v < 0) return false;
        return true;
    }
}
