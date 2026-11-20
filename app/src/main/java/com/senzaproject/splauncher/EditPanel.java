package com.senzaproject.splauncher;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

/**
 * Panel "Sesuaikan" pada mode edit Home: slider yang langsung mengubah Home Screen di belakangnya
 * (ukuran ikon, kolom, baris, jarak, ukuran Dock). Tiap perubahan disimpan ke Prefs dan memicu rebuild.
 */
final class EditPanel extends GlassLayout {
    private final Prefs prefs;
    private final Runnable onChange;
    private final int fg;
    private final LinearLayout list;

    EditPanel(Context c, Prefs prefs, boolean dark, int curRows, Runnable onChange, final Runnable onReset) {
        super(c, 26, dark ? 0.66f : 0.74f, dark);
        this.prefs = prefs;
        this.onChange = onChange;
        fg = dark ? 0xFFFFFFFF : 0xFF111114;
        setOrientation(VERTICAL);
        setPadding(U.dp(c, 18), U.dp(c, 12), U.dp(c, 18), U.dp(c, 10));
        setClickable(true);

        LinearLayout head = new LinearLayout(c);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = new TextView(c);
        t.setText("Sesuaikan Home Screen");
        t.setTextColor(fg);
        t.setTextSize(16f);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        head.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView reset = new TextView(c);
        reset.setText("Atur Ulang");
        reset.setTextColor(0xFF0A84FF);
        reset.setTextSize(14f);
        reset.setPadding(U.dp(c, 8), U.dp(c, 6), 0, U.dp(c, 6));
        reset.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { onReset.run(); } });
        head.addView(reset);
        addView(head, new LinearLayout.LayoutParams(-1, -2));

        ScrollView sv = new ScrollView(c);
        sv.setVerticalScrollBarEnabled(false);
        list = new LinearLayout(c);
        list.setOrientation(VERTICAL);
        sv.addView(list, new ScrollView.LayoutParams(-1, -2));
        addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));

        row("Ukuran ikon", "iconScale", 70, 130, prefs.iconScale(), "%");
        row("Kolom", "cols", 3, 7, prefs.cols(), "");
        row("Baris", "rowsPref", 4, 8, prefs.rowsPref() == 0 ? U.clamp(curRows, 4, 8) : prefs.rowsPref(), "");
        row("Jarak horizontal", "hGap", 60, 100, prefs.hGap(), "%");
        row("Jarak vertikal", "vGap", 60, 100, prefs.vGap(), "%");
        row("Ukuran Dock", "dockScale", 80, 130, prefs.dockScale(), "%");
    }

    private void row(String label, final String key, final int min, int max, int value, final String unit) {
        Context c = getContext();
        LinearLayout top = new LinearLayout(c);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView l = new TextView(c);
        l.setText(label);
        l.setTextColor(fg);
        l.setTextSize(14f);
        top.addView(l, new LinearLayout.LayoutParams(0, -2, 1f));
        final TextView val = new TextView(c);
        val.setText(value + unit);
        val.setTextColor((fg & 0x00FFFFFF) | 0xB0000000);
        val.setTextSize(14f);
        top.addView(val);
        list.addView(top, new LinearLayout.LayoutParams(-1, -2));
        SeekBar sb = new SeekBar(c);
        sb.setMax(max - min);
        sb.setProgress(value - min);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean user) {
                val.setText((p + min) + unit);
                if (!user) return;
                prefs.put(key, p + min);
                onChange.run();
            }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = U.dp(c, 4);
        list.addView(sb, lp);
    }
}
