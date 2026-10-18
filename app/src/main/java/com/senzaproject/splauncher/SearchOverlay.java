package com.senzaproject.splauncher;

import android.content.Context;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

/** Pencarian aplikasi layar penuh (geser ke bawah di Home atau ketuk kolom App Library). */
final class SearchOverlay extends FrameLayout {
    interface Host {
        void launch(AppRepo.AppInfo a, View v);
        void longPress(AppRepo.AppInfo a, View v);
        void closeSearch();
    }

    private final Host host;
    private final Prefs prefs;
    private final AppRepo repo;
    private final EditText input;
    private final LinearLayout results;
    private final ScrollView scroll;

    SearchOverlay(Context c, Host h, Prefs p, AppRepo r, int topInset, int bottomInset) {
        super(c);
        host = h; prefs = p; repo = r;
        setBackground(new Glass.GlassDrawable(this, 0, 0.62f, true));
        setClickable(true);

        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(U.dp(c, 16), topInset + U.dp(c, 12), U.dp(c, 16), 0);
        addView(col, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout bar = new LinearLayout(c);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        col.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, U.dp(c, 46)));

        LinearLayout field = new LinearLayout(c);
        field.setOrientation(LinearLayout.HORIZONTAL);
        field.setGravity(Gravity.CENTER_VERTICAL);
        field.setBackground(new Glass.GlassDrawable(field, U.dpf(c, 23), 0.30f, true));
        View glyph = new View(c);
        glyph.setBackground(Glyphs.search(0xCCFFFFFF, U.dpf(c, 2f)));
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(U.dp(c, 18), U.dp(c, 18));
        gp.setMarginStart(U.dp(c, 14));
        field.addView(glyph, gp);
        input = new EditText(c);
        input.setHint("Cari aplikasi");
        input.setHintTextColor(0x99FFFFFF);
        input.setTextColor(0xFFFFFFFF);
        input.setTextSize(16f);
        input.setSingleLine(true);
        input.setBackground(null);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setPadding(U.dp(c, 10), 0, U.dp(c, 12), 0);
        field.addView(input, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        bar.addView(field, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        TextView cancel = new TextView(c);
        cancel.setText("Batal");
        cancel.setTextColor(0xFFFFFFFF);
        cancel.setTextSize(16f);
        cancel.setPadding(U.dp(c, 14), 0, U.dp(c, 4), 0);
        cancel.setGravity(Gravity.CENTER_VERTICAL);
        cancel.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { host.closeSearch(); } });
        bar.addView(cancel, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

        scroll = new ScrollView(c);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setPadding(0, U.dp(c, 10), 0, bottomInset);
        scroll.setClipToPadding(false);
        results = new LinearLayout(c);
        results.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(results, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        col.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void afterTextChanged(Editable s) { refresh(s.toString()); }
        });
        input.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, android.view.KeyEvent e) {
                List<AppRepo.AppInfo> m = match(input.getText().toString());
                if (!m.isEmpty() && !input.getText().toString().trim().isEmpty()) host.launch(m.get(0), null);
                return true;
            }
        });
        refresh("");
    }

    void setBottomInset(int px) {
        scroll.setPadding(0, U.dp(getContext(), 10), 0, px);
    }

    void focusInput() {
        input.requestFocus();
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
    }

    void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
    }

    private List<AppRepo.AppInfo> match(String q) {
        q = q.trim().toLowerCase(Locale.ROOT);
        java.util.ArrayList<AppRepo.AppInfo> starts = new java.util.ArrayList<>();
        java.util.ArrayList<AppRepo.AppInfo> contains = new java.util.ArrayList<>();
        for (AppRepo.AppInfo a : repo.all()) {
            String l = a.label.toLowerCase(Locale.ROOT);
            if (q.isEmpty()) starts.add(a);
            else if (l.startsWith(q)) starts.add(a);
            else if (l.contains(q) || a.pkg.toLowerCase(Locale.ROOT).contains(q)) contains.add(a);
        }
        starts.addAll(contains);
        return starts;
    }

    private void refresh(String q) {
        Context c = getContext();
        results.removeAllViews();
        List<AppRepo.AppInfo> list = match(q);
        boolean grouped = q.trim().isEmpty();
        char last = 0;
        int count = 0;
        int size = U.dp(c, 44);
        for (final AppRepo.AppInfo a : list) {
            if (++count > 80) break;
            if (grouped) {
                char ch = Character.toUpperCase(a.label.isEmpty() ? '#' : a.label.charAt(0));
                if (!Character.isLetter(ch)) ch = '#';
                if (ch != last) {
                    last = ch;
                    TextView h = new TextView(c);
                    h.setText(String.valueOf(ch));
                    h.setTextColor(0x99FFFFFF);
                    h.setTextSize(13f);
                    h.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                    h.setPadding(U.dp(c, 6), U.dp(c, 12), 0, U.dp(c, 4));
                    results.addView(h);
                }
            }
            LinearLayout row = new LinearLayout(c);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(U.dp(c, 6), U.dp(c, 6), U.dp(c, 6), U.dp(c, 6));
            IconView iv = new IconView(c);
            iv.bindApp(a, prefs, size - U.dp(c, 4), false, true);
            iv.setClickable(false);
            row.addView(iv, new LinearLayout.LayoutParams(size, size));
            TextView t = new TextView(c);
            t.setText(a.label);
            t.setTextColor(0xFFFFFFFF);
            t.setTextSize(16f);
            t.setSingleLine(true);
            t.setPadding(U.dp(c, 14), 0, 0, 0);
            row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { host.launch(a, v); } });
            row.setOnLongClickListener(new View.OnLongClickListener() {
                @Override public boolean onLongClick(View v) { host.longPress(a, v); return true; }
            });
            results.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        if (list.isEmpty()) {
            TextView t = new TextView(c);
            t.setText("Tidak ada hasil");
            t.setTextColor(0x99FFFFFF);
            t.setTextSize(15f);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, U.dp(c, 40), 0, 0);
            results.addView(t);
        }
        scroll.scrollTo(0, 0);
    }
}
