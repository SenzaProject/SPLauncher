package com.senzaproject.splauncher;

import android.content.Context;
import android.graphics.Outline;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.LinearLayout;

/** LinearLayout dengan latar Liquid Glass, sudut membulat, dan bayangan. */
class GlassLayout extends LinearLayout {
    private final Glass.GlassDrawable bg;
    private float radiusPx;

    GlassLayout(Context c, float radiusDp, float tint, boolean dark) {
        super(c);
        radiusPx = U.dpf(c, radiusDp);
        bg = new Glass.GlassDrawable(this, radiusPx, tint, dark);
        setBackground(bg);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radiusPx);
            }
        });
        setClipToOutline(true);
        setElevation(U.dpf(c, 8));
        setOutlineAmbientShadowColor(0x40000000);
        setOutlineSpotShadowColor(0x66000000);
    }

    void setGlass(float radiusDp, float tint, boolean dark) {
        radiusPx = U.dpf(getContext(), radiusDp);
        bg.set(radiusPx, tint, dark);
        invalidateOutline();
    }
}
