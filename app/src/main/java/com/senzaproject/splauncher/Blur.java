package com.senzaproject.splauncher;

/** Box blur 3 lintasan (aproksimasi Gaussian) untuk array ARGB opak. */
final class Blur {
    private Blur() {}

    static void blur(int[] px, int w, int h, int r) {
        if (r < 1) return;
        int[] tmp = new int[px.length];
        for (int i = 0; i < 3; i++) {
            horiz(px, tmp, w, h, r);
            vert(tmp, px, w, h, r);
        }
    }

    private static void horiz(int[] in, int[] out, int w, int h, int r) {
        int div = 2 * r + 1;
        for (int y = 0; y < h; y++) {
            int row = y * w;
            int sr = 0, sg = 0, sb = 0;
            for (int i = -r; i <= r; i++) {
                int p = in[row + U.clamp(i, 0, w - 1)];
                sr += (p >> 16) & 255; sg += (p >> 8) & 255; sb += p & 255;
            }
            for (int x = 0; x < w; x++) {
                out[row + x] = 0xFF000000 | ((sr / div) << 16) | ((sg / div) << 8) | (sb / div);
                int p0 = in[row + U.clamp(x - r, 0, w - 1)];
                int p1 = in[row + U.clamp(x + r + 1, 0, w - 1)];
                sr += ((p1 >> 16) & 255) - ((p0 >> 16) & 255);
                sg += ((p1 >> 8) & 255) - ((p0 >> 8) & 255);
                sb += (p1 & 255) - (p0 & 255);
            }
        }
    }

    private static void vert(int[] in, int[] out, int w, int h, int r) {
        int div = 2 * r + 1;
        for (int x = 0; x < w; x++) {
            int sr = 0, sg = 0, sb = 0;
            for (int i = -r; i <= r; i++) {
                int p = in[U.clamp(i, 0, h - 1) * w + x];
                sr += (p >> 16) & 255; sg += (p >> 8) & 255; sb += p & 255;
            }
            for (int y = 0; y < h; y++) {
                out[y * w + x] = 0xFF000000 | ((sr / div) << 16) | ((sg / div) << 8) | (sb / div);
                int p0 = in[U.clamp(y - r, 0, h - 1) * w + x];
                int p1 = in[U.clamp(y + r + 1, 0, h - 1) * w + x];
                sr += ((p1 >> 16) & 255) - ((p0 >> 16) & 255);
                sg += ((p1 >> 8) & 255) - ((p0 >> 8) & 255);
                sb += (p1 & 255) - (p0 & 255);
            }
        }
    }
}
