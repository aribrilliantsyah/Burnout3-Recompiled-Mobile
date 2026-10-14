package com.ryuna.b3x;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Physical controller mapping: each Xbox input (A..rthumb) takes one
 * controller button or trigger. Tap a row, then press the button on the pad.
 * Stored as SDL GameController names in Settings ("map_<xbox>"), written to
 * b3x_padmap.txt at Play and read by xinput_device.c (RECOMP_PAD_MAP).
 * Android key codes are turned into the SDL names here; SDL's own mapping
 * of the same pad is what the game reads, so both name the same buttons.
 */
public class GamepadMapActivity extends Activity {
    private SharedPreferences mPrefs;
    private final TextView[] mValues = new TextView[Settings.XBOX_INPUTS.length];
    private final View[] mRows = new View[Settings.XBOX_INPUTS.length];
    private int mListening = -1;
    private TextView mStatus;
    private float mLtPrev, mRtPrev;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        mPrefs = Settings.prefs(this);
        UiKit ui = new UiKit(this);

        LinearLayout col = ui.column();
        col.setPadding(ui.dp(14), ui.dp(10), ui.dp(14), ui.dp(14));
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = ui.title("Controller mapping");
        head.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        View reset = ui.secondary("Reset all", v -> {
            SharedPreferences.Editor e = mPrefs.edit();
            for (String k : Settings.XBOX_INPUTS) e.remove(Settings.K_MAP_PREFIX + k);
            e.apply();
            refresh();
        });
        head.addView(reset);
        head.addView(ui.gap(8));
        head.addView(ui.secondary("Done", v -> finish()));
        col.addView(head);
        mStatus = ui.summary("Tap an input, then press the button on your controller. Back cancels.");
        col.addView(mStatus);
        col.addView(ui.gap(8));

        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout left = ui.column(), right = ui.column();
        int half = (Settings.XBOX_INPUTS.length + 1) / 2;
        for (int i = 0; i < Settings.XBOX_INPUTS.length; i++) {
            final int idx = i;
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(ui.dp(12), ui.dp(9), ui.dp(12), ui.dp(9));
            row.setBackground(ui.box(UiKit.PANEL, UiKit.LINE, 8));
            TextView name = ui.text(Settings.XBOX_LABELS[i], 14, UiKit.FG);
            row.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
            TextView val = ui.text("", 13, UiKit.ACCENT);
            val.setTypeface(Typeface.MONOSPACE);
            row.addView(val);
            mValues[i] = val;
            mRows[i] = row;
            row.setOnClickListener(v -> listen(idx));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = ui.dp(6);
            (i < half ? left : right).addView(row, lp);
        }
        grid.addView(left, new LinearLayout.LayoutParams(0, -2, 1f));
        grid.addView(ui.gap(10));
        grid.addView(right, new LinearLayout.LayoutParams(0, -2, 1f));
        col.addView(grid);

        col.addView(ui.gap(6));
        LinearLayout opts = ui.card();
        opts.addView(ui.section("STICKS"));
        opts.addView(ui.slider("Dead zone", "Stick movement ignored around the centre.",
                0, 40, mPrefs.getInt(Settings.K_DEADZONE, 8), v -> v + " %",
                v -> mPrefs.edit().putInt(Settings.K_DEADZONE, v).apply()));
        opts.addView(ui.toggle("Invert left stick Y", null, mPrefs.getBoolean(Settings.K_INVERT_LY, false),
                v -> mPrefs.edit().putBoolean(Settings.K_INVERT_LY, v).apply()));
        opts.addView(ui.toggle("Invert right stick Y", null, mPrefs.getBoolean(Settings.K_INVERT_RY, false),
                v -> mPrefs.edit().putBoolean(Settings.K_INVERT_RY, v).apply()));
        col.addView(opts);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(UiKit.BG);
        sv.addView(col);
        setContentView(sv);
        refresh();
    }

    private void refresh() {
        for (int i = 0; i < mValues.length; i++) {
            String m = Settings.mapping(mPrefs, i);
            mValues[i].setText(i == mListening ? "press…" : pretty(m));
            mRows[i].setBackground(new UiKit(this).box(UiKit.PANEL, i == mListening ? UiKit.ACCENT : UiKit.LINE, 8));
        }
    }

    private void listen(int i) {
        mListening = mListening == i ? -1 : i;
        mLtPrev = mRtPrev = 0;
        mStatus.setText(mListening < 0 ? "Tap an input, then press the button on your controller. Back cancels."
                : "Press a button for " + Settings.XBOX_LABELS[i] + "…");
        refresh();
    }

    private void assign(String sdl) {
        if (mListening < 0) return;
        mPrefs.edit().putString(Settings.K_MAP_PREFIX + Settings.XBOX_INPUTS[mListening], sdl).apply();
        mListening = -1;
        mStatus.setText("Saved. Tap another input to change it.");
        refresh();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (mListening >= 0 && e.getAction() == KeyEvent.ACTION_DOWN) {
            if (e.getKeyCode() == KeyEvent.KEYCODE_BACK && !fromPad(e.getSource())) {
                listen(mListening);
                return true;
            }
            String sdl = sdlName(e.getKeyCode());
            if (sdl != null) { assign(sdl); return true; }
        }
        if (mListening >= 0 && fromPad(e.getSource())) return true;   // swallow pad keys while listening
        return super.dispatchKeyEvent(e);
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent e) {
        if (mListening >= 0 && fromPad(e.getSource())) {
            float lt = Math.max(e.getAxisValue(MotionEvent.AXIS_LTRIGGER), e.getAxisValue(MotionEvent.AXIS_BRAKE));
            float rt = Math.max(e.getAxisValue(MotionEvent.AXIS_RTRIGGER), e.getAxisValue(MotionEvent.AXIS_GAS));
            if (lt > .6f && mLtPrev <= .6f) { assign("lefttrigger"); return true; }
            if (rt > .6f && mRtPrev <= .6f) { assign("righttrigger"); return true; }
            mLtPrev = lt; mRtPrev = rt;
            float hx = e.getAxisValue(MotionEvent.AXIS_HAT_X), hy = e.getAxisValue(MotionEvent.AXIS_HAT_Y);
            if (hx < -.5f) { assign("dpleft"); return true; }
            if (hx > .5f) { assign("dpright"); return true; }
            if (hy < -.5f) { assign("dpup"); return true; }
            if (hy > .5f) { assign("dpdown"); return true; }
            return true;
        }
        return super.dispatchGenericMotionEvent(e);
    }

    private static boolean fromPad(int src) {
        return (src & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
            || (src & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
            || (src & InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD;
    }

    /** Android key code -> SDL GameController button name (SDL's own table). */
    static String sdlName(int key) {
        switch (key) {
            case KeyEvent.KEYCODE_BUTTON_A: return "a";
            case KeyEvent.KEYCODE_BUTTON_B: return "b";
            case KeyEvent.KEYCODE_BUTTON_X: return "x";
            case KeyEvent.KEYCODE_BUTTON_Y: return "y";
            case KeyEvent.KEYCODE_BUTTON_L1: return "leftshoulder";
            case KeyEvent.KEYCODE_BUTTON_R1: return "rightshoulder";
            case KeyEvent.KEYCODE_BUTTON_L2: return "lefttrigger";
            case KeyEvent.KEYCODE_BUTTON_R2: return "righttrigger";
            case KeyEvent.KEYCODE_BUTTON_THUMBL: return "leftstick";
            case KeyEvent.KEYCODE_BUTTON_THUMBR: return "rightstick";
            case KeyEvent.KEYCODE_BUTTON_START: return "start";
            case KeyEvent.KEYCODE_BUTTON_SELECT: case KeyEvent.KEYCODE_BACK: return "back";
            case KeyEvent.KEYCODE_BUTTON_MODE: return "guide";
            case KeyEvent.KEYCODE_DPAD_UP: return "dpup";
            case KeyEvent.KEYCODE_DPAD_DOWN: return "dpdown";
            case KeyEvent.KEYCODE_DPAD_LEFT: return "dpleft";
            case KeyEvent.KEYCODE_DPAD_RIGHT: return "dpright";
            case KeyEvent.KEYCODE_BUTTON_1: return "paddle1";
            case KeyEvent.KEYCODE_BUTTON_2: return "paddle2";
            case KeyEvent.KEYCODE_BUTTON_3: return "paddle3";
            case KeyEvent.KEYCODE_BUTTON_4: return "paddle4";
            default: return null;
        }
    }

    static String pretty(String sdl) {
        switch (sdl) {
            case "a": return "A";
            case "b": return "B";
            case "x": return "X";
            case "y": return "Y";
            case "leftshoulder": return "LB";
            case "rightshoulder": return "RB";
            case "lefttrigger": return "LT";
            case "righttrigger": return "RT";
            case "leftstick": return "L3";
            case "rightstick": return "R3";
            case "start": return "Start";
            case "back": return "Back / Select";
            case "guide": return "Guide";
            case "dpup": return "D-pad ↑";
            case "dpdown": return "D-pad ↓";
            case "dpleft": return "D-pad ←";
            case "dpright": return "D-pad →";
            case "none": return "—";
            default: return sdl;
        }
    }
}
