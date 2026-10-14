package com.ryuna.b3x;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import java.util.function.Consumer;
import java.util.function.IntFunction;

/**
 * The launcher's widgets, built in code (no Material dependency): dark
 * cards with a section title, rows of title + summary + control, as
 * NFSMW-Recompiled-Mobile's SetupActivity lays them out.
 */
final class UiKit {
    static final int BG = 0xFF121212, CARD = 0xFF1E1E1E, PANEL = 0xFF262626, LINE = 0xFF333333;
    static final int FG = 0xFFEDEDED, DIM = 0xFF9A9A9A, ACCENT = 0xFFE5534B, OK = 0xFF3FB950;

    private final Context c;

    UiKit(Context c) { this.c = c; }

    int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    GradientDrawable box(int fill, int stroke, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        if (stroke != 0) g.setStroke(dp(1), stroke);
        g.setCornerRadius(dp(radius));
        return g;
    }

    LinearLayout column() {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    View gap(int d) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(d), dp(d)));
        return v;
    }

    TextView text(String s, float sp, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }

    TextView title(String s) {
        TextView t = text(s, 22, Color.WHITE);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    TextView summary(String s) {
        TextView t = text(s, 12, DIM);
        t.setLineSpacing(0, 1.1f);
        return t;
    }

    TextView section(String s) {
        TextView t = text(s, 12, ACCENT);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setLetterSpacing(.08f);
        t.setPadding(0, 0, 0, dp(6));
        return t;
    }

    /** A card: rounded dark panel; add rows to it. */
    LinearLayout card() {
        LinearLayout l = column();
        l.setBackground(box(CARD, 0, 14));
        l.setPadding(dp(16), dp(14), dp(16), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(10);
        l.setLayoutParams(lp);
        return l;
    }

    Button primary(String s, View.OnClickListener on) {
        Button b = button(s, on);
        b.setBackground(box(ACCENT, 0, 12));
        b.setTextColor(Color.WHITE);
        b.setTextSize(20);
        b.setMinHeight(dp(60));
        return b;
    }

    Button secondary(String s, View.OnClickListener on) {
        Button b = button(s, on);
        b.setBackground(box(0xFF2C2C2C, 0, 10));
        return b;
    }

    private Button button(String s, View.OnClickListener on) {
        Button b = new Button(c);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTextColor(FG);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setMinHeight(dp(42));
        b.setPadding(dp(16), 0, dp(16), 0);
        b.setStateListAnimator(null);
        b.setOnClickListener(on);
        return b;
    }

    /** Title + summary on the left, the control on the right. */
    LinearLayout row(String title, String summary, View control) {
        LinearLayout r = new LinearLayout(c);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(7), 0, dp(7));
        LinearLayout texts = column();
        texts.addView(text(title, 15, FG));
        if (summary != null) texts.addView(summary(summary));
        r.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));
        if (control != null) {
            // one right-hand slot for every row: switches and dropdowns line up
            LinearLayout slot = new LinearLayout(c);
            slot.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            slot.addView(control);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(control instanceof Switch ? 64 : 140), -2);
            lp.leftMargin = dp(12);
            r.addView(slot, lp);
        }
        return r;
    }

    private static android.content.res.ColorStateList states(int on, int off) {
        return new android.content.res.ColorStateList(
                new int[][] {{android.R.attr.state_checked}, {}}, new int[] {on, off});
    }

    View toggle(String title, String summary, boolean on, Consumer<Boolean> set) {
        Switch s = new Switch(c);
        s.setThumbTintList(states(ACCENT, 0xFFBDBDBD));
        s.setTrackTintList(states(0x99E5534B, 0xFF4A4A4A));
        s.setChecked(on);
        s.setOnCheckedChangeListener((v, b) -> set.accept(b));
        LinearLayout r = row(title, summary, s);
        r.setOnClickListener(v -> s.toggle());
        return r;
    }

    View choice(String title, String summary, String[] labels, int[] values, int current, Consumer<Integer> set) {
        Spinner sp = new Spinner(c, Spinner.MODE_DROPDOWN);
        ArrayAdapter<String> a = new ArrayAdapter<String>(c, android.R.layout.simple_spinner_item, labels) {
            @Override public View getView(int pos, View cv, ViewGroup parent) {
                TextView t = (TextView) super.getView(pos, cv, parent);
                t.setText(labels[pos] + "  \u25BE");
                t.setTextColor(ACCENT);
                t.setTextSize(14);
                t.setTypeface(Typeface.DEFAULT_BOLD);
                t.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
                return t;
            }
        };
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp.setAdapter(a);
        sp.setBackground(box(PANEL, 0xFF4A4A4A, 8));
        sp.setLayoutParams(new LinearLayout.LayoutParams(dp(140), -2));
        sp.setPadding(dp(10), dp(4), dp(10), dp(4));
        for (int i = 0; i < values.length; i++) if (values[i] == current) sp.setSelection(i);
        sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { set.accept(values[pos]); }
            @Override public void onNothingSelected(AdapterView<?> p) { }
        });
        return row(title, summary, sp);
    }

    View slider(String title, String summary, int min, int max, int current, IntFunction<String> fmt,
                Consumer<Integer> set) {
        LinearLayout col = column();
        col.setPadding(0, dp(6), 0, dp(4));
        LinearLayout head = new LinearLayout(c);
        TextView t = text(title, 15, FG);
        head.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView val = text(fmt.apply(current), 14, ACCENT);
        val.setTypeface(Typeface.DEFAULT_BOLD);
        head.addView(val);
        col.addView(head);
        if (summary != null) col.addView(summary(summary));
        SeekBar sb = new SeekBar(c);
        sb.setProgressTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        sb.setThumbTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        sb.setPadding(0, sb.getPaddingTop(), 0, sb.getPaddingBottom());
        sb.setMax(max - min);
        sb.setProgress(current - min);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean user) {
                val.setText(fmt.apply(p + min));
                set.accept(p + min);
            }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });
        col.addView(sb, new LinearLayout.LayoutParams(-1, -2));
        return col;
    }

    /** Segmented choice (radio-like buttons in a row). */
    View segmented(String title, String summary, String[] labels, int[] values, int current, Consumer<Integer> set) {
        LinearLayout col = column();
        col.setPadding(0, dp(6), 0, dp(6));
        col.addView(text(title, 15, FG));
        if (summary != null) col.addView(summary(summary));
        LinearLayout seg = new LinearLayout(c);
        seg.setPadding(0, dp(6), 0, 0);
        TextView[] items = new TextView[labels.length];
        Runnable[] paint = new Runnable[1];
        int[] cur = {current};
        for (int i = 0; i < labels.length; i++) {
            final int v = values[i];
            TextView t = text(labels[i], 13, FG);
            t.setGravity(Gravity.CENTER);
            t.setTypeface(Typeface.DEFAULT_BOLD);
            t.setPadding(dp(8), dp(9), dp(8), dp(9));
            t.setOnClickListener(x -> { cur[0] = v; set.accept(v); paint[0].run(); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
            if (i > 0) lp.leftMargin = dp(6);
            seg.addView(t, lp);
            items[i] = t;
        }
        paint[0] = () -> {
            for (int i = 0; i < items.length; i++) {
                boolean on = values[i] == cur[0];
                items[i].setBackground(box(on ? ACCENT : PANEL, on ? 0 : LINE, 8));
                items[i].setTextColor(on ? Color.WHITE : FG);
            }
        };
        paint[0].run();
        col.addView(seg);
        return col;
    }
}
