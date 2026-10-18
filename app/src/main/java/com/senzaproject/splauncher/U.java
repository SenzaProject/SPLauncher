package com.senzaproject.splauncher;

import android.content.Context;
import android.util.TypedValue;

/** Utilitas kecil. */
final class U {
    private U() {}

    static float dpf(Context c, float v) { return v * c.getResources().getDisplayMetrics().density; }
    static int dp(Context c, float v) { return Math.round(dpf(c, v)); }
    static float sp(Context c, float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, c.getResources().getDisplayMetrics());
    }
    static int clamp(int v, int lo, int hi) { return v < lo ? lo : (v > hi ? hi : v); }
    static float clampf(float v, float lo, float hi) { return v < lo ? lo : (v > hi ? hi : v); }
    static int alpha(int color, float a) {
        return (color & 0x00FFFFFF) | (Math.round(clampf(a, 0f, 1f) * 255f) << 24);
    }
    static float luminance(int c) {
        float r = ((c >> 16) & 255) / 255f, g = ((c >> 8) & 255) / 255f, b = (c & 255) / 255f;
        return 0.2126f * r + 0.7152f * g + 0.0722f * b;
    }
    static int lerpColor(int a, int b, float t) {
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return 0xFF000000 | (Math.round(ar + (br - ar) * t) << 16) | (Math.round(ag + (bg - ag) * t) << 8)
                | Math.round(ab + (bb - ab) * t);
    }
}
