package com.senzaproject.splauncher;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/** Menu konteks bergaya kaca. */
final class Menus {
    private Menus() {}

    static final class Action {
        final String label;
        final Runnable run;
        final boolean destructive;
        Action(String label, Runnable run, boolean destructive) { this.label = label; this.run = run; this.destructive = destructive; }
        Action(String label, Runnable run) { this(label, run, false); }
    }

    static GlassLayout build(Context c, boolean dark, List<Action> actions, final Runnable dismiss) {
        GlassLayout m = new GlassLayout(c, 18, dark ? 0.55f : 0.62f, dark);
        m.setOrientation(LinearLayout.VERTICAL);
        int fg = dark ? 0xFFFFFFFF : 0xFF111114;
        for (int i = 0; i < actions.size(); i++) {
            final Action a = actions.get(i);
            TextView t = new TextView(c);
            t.setText(a.label);
            t.setTextSize(16f);
            t.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            t.setTextColor(a.destructive ? 0xFFFF453A : fg);
            t.setGravity(Gravity.CENTER_VERTICAL);
            t.setPadding(U.dp(c, 18), 0, U.dp(c, 18), 0);
            t.setSingleLine(true);
            t.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    dismiss.run();
                    if (a.run != null) a.run.run();
                }
            });
            m.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, U.dp(c, 46)));
            if (i < actions.size() - 1) {
                View d = new View(c);
                d.setBackgroundColor(dark ? 0x26FFFFFF : 0x1F000000);
                m.addView(d, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
            }
        }
        return m;
    }
}
