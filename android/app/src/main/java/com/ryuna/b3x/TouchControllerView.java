package com.ryuna.b3x;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.input.InputManager;
import android.util.SparseArray;
import android.view.Display;
import android.view.HapticFeedbackConstants;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Touch controller over the game (B3xActivity) or alone as its editor
 * (EditorActivity). Modelled on NFSMW-Recompiled-Mobile's
 * TouchControllerView: layouts with factory positions as fractions of the
 * screen, overridden per control by what the editor saved; a HIDE/TOUCH
 * button that fades after 4 s; the whole overlay hidden while a physical
 * pad is in use; an in-game editor behind the gear.
 *
 * Two layouts (Settings.MODE_*):
 *   FULL      the original Xbox controller: both sticks with L3/R3, D-pad,
 *             A B X Y, Black and White, LT and RT, Back and Start
 *   STEERING  for driving: steering (stick, arrows or tilting the phone)
 *             on the left, the RT and LT pedals and a big A (boost) on the
 *             right, the other buttons small
 *
 * State goes to native code (B3xActivity.nativeSetPad / nativeSetPadRight)
 * as controller port 0, merged with a physical pad if one is plugged in.
 */
public final class TouchControllerView extends View {
    // XBOX_GAMEPAD_* digital bits
    static final int UP = 0x01, DOWN = 0x02, LEFT = 0x04, RIGHT = 0x08;
    static final int START = 0x10, BACK = 0x20, L3 = 0x40, R3 = 0x80;
    // analog button indices (bit n of the face mask)
    static final int A = 0, B = 1, X = 2, Y = 3, BLACK = 4, WHITE = 5;

    private static final String PREFS = "b3x_touch";
    /** Bump when the factory layouts change. */
    private static final int LAYOUT_VERSION = 2;
    private static final boolean DEBUG = android.util.Log.isLoggable("b3x-pad", android.util.Log.DEBUG);
    private static final int MODES = 2;
    private static final String[] MODE_NAMES = {"FULL", "DRIVE"};
    private static final long TOGGLE_MS = 4000;
    private static final float STEP = .1f, SCALE_MIN = .5f, SCALE_MAX = 2.2f, OPA_MIN = .15f;

    private static final String HELP = "Drag to move · SIZE: selected or all";
    private static final int ACCENT = Color.rgb(0xE5, 0x53, 0x4B);
    private static final int PRESSED = Color.argb(200, 0xE5, 0x53, 0x4B);
    private static final int FILL = Color.argb(95, 10, 10, 12);
    private static final int EDGE = Color.argb(210, 255, 255, 255);
    private static final int UI_BG = Color.argb(190, 16, 17, 20);

    private static final int K_STICK = 0, K_ROUND = 1, K_RECT = 2, K_PEDAL = 3, K_DPAD = 4, K_ARROW = 5;

    /** One on-screen control. */
    private final class Control {
        final String id, label, sub;
        final int kind, digital, face, trigger, arrow;
        final float baseW, baseH;          // radius in w for round/stick/dpad
        int color = EDGE;
        int visibleIn;                      // bit per mode
        final float[] fx = new float[MODES], fy = new float[MODES], fs = new float[MODES];
        final float[] px = new float[MODES], py = new float[MODES], ps = new float[MODES];
        final boolean[] own = new boolean[MODES];
        float cx, cy, scale = 1f;
        boolean down;
        float kx, ky;                       // stick knob offset, -1..1
        int pointer = -1;

        Control(String id, String label, String sub, int kind, int digital, int face, int trigger,
                int arrow, float baseW, float baseH) {
            this.id = id; this.label = label; this.sub = sub; this.kind = kind;
            this.digital = digital; this.face = face; this.trigger = trigger; this.arrow = arrow;
            this.baseW = baseW; this.baseH = baseH;
        }

        Control at(int mode, float x, float y, float s) {
            fx[mode] = x; fy[mode] = y; fs[mode] = s;
            visibleIn |= 1 << mode;
            return this;
        }

        boolean shown() { return (visibleIn & (1 << mode)) != 0; }
        float w() { return baseW * unit * scale; }
        float h() { return baseH * unit * scale; }
        float reach() {
            if (kind == K_STICK) return w() * 1.35f;
            if (kind == K_DPAD) return w() * 1.05f;              // the cross spans 2w
            return Math.max(w(), h()) * .6f + unit * .12f;
        }

        void use(int m) {
            cx = (own[m] ? px[m] : fx[m]) * width;
            cy = (own[m] ? py[m] : fy[m]) * height;
            scale = own[m] ? ps[m] : fs[m];
        }

        void keep(int m) {
            px[m] = cx / width; py[m] = cy / height; ps[m] = scale; own[m] = true;
        }

        boolean contains(float x, float y) {
            switch (kind) {
                case K_STICK: case K_ROUND: case K_DPAD: {
                    float r = reach(), dx = x - cx, dy = y - cy;
                    return dx * dx + dy * dy <= r * r;
                }
                default: {
                    float s = unit * .12f;
                    return Math.abs(x - cx) <= w() * .5f + s && Math.abs(y - cy) <= h() * .5f + s;
                }
            }
        }
    }

    private final List<Control> controls = new ArrayList<>();
    private final SparseArray<Control> pointers = new SparseArray<>();
    private final SharedPreferences touchPrefs, prefs;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint frame = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint.FontMetrics fm = new Paint.FontMetrics();
    private final RectF r = new RectF();
    private final Path path = new Path();
    private final float density;
    private float width, height, unit;

    private int mode;
    private final int steer;                // Settings.STEER_*
    private final float[] opacity = new float[MODES];
    private boolean controlsVisible = true;
    private boolean physicalPad;
    private final boolean autoHide, haptic;
    private boolean toggleVisible = true;
    private final Runnable hideToggle = () -> { toggleVisible = false; invalidate(); };
    private final RectF toggleBtn = new RectF(), gearBtn = new RectF(), modeBtn = new RectF(), menuBtn = new RectF();
    /** The in-game menu (hamburger): set by B3xActivity. */
    Runnable onMenu;

    // FPS counter (game only)
    private final boolean showFps;
    private String fps = "";

    // editor
    private final boolean inGame;
    private final Runnable onEditorDone;     // EditorActivity: finish()
    private boolean editing;
    private Control picked;
    private int editPointer = -1;
    private float grabX, grabY;
    private final RectF sizeMinus = new RectF(), sizePlus = new RectF(), opaMinus = new RectF(),
            opaPlus = new RectF(), modeEdit = new RectF(), reset = new RectF(), cancel = new RectF(),
            done = new RectF(), helpBg = new RectF();
    private float sizeTextX, opaTextX;

    // tilt steering
    private final SensorManager sensors;
    private final Sensor gravity;
    private final boolean accelOnly;
    private final float[] lowPass = new float[2];
    private final float fullLockSin;
    private boolean listening;
    private float tilt;                      // -1..1
    private final SensorEventListener tiltListener = new SensorEventListener() {
        @Override public void onSensorChanged(SensorEvent e) {
            float gx = e.values[0], gy = e.values[1];
            if (accelOnly) {
                lowPass[0] += (gx - lowPass[0]) * .2f;
                lowPass[1] += (gy - lowPass[1]) * .2f;
                gx = lowPass[0]; gy = lowPass[1];
            }
            Display d = getDisplay();
            int rot = d == null ? Surface.ROTATION_90 : d.getRotation();
            float sx;
            switch (rot) {
                case Surface.ROTATION_90: sx = gy; break;
                case Surface.ROTATION_180: sx = -gx; break;
                case Surface.ROTATION_270: sx = -gy; break;
                default: sx = gx; break;
            }
            float t = clamp(sx / (SensorManager.GRAVITY_EARTH * fullLockSin), -1f, 1f);
            float dz = .04f;
            t = Math.abs(t) < dz ? 0f : Math.signum(t) * (Math.abs(t) - dz) / (1f - dz);
            if (Math.abs(t - tilt) > .005f) {
                tilt = t;
                if (tiltActive()) send();
            }
        }
        @Override public void onAccuracyChanged(Sensor s, int a) { }
    };

    private final InputManager inputManager;
    private final InputManager.InputDeviceListener padListener = new InputManager.InputDeviceListener() {
        @Override public void onInputDeviceAdded(int id) { checkPads(); }
        @Override public void onInputDeviceRemoved(int id) { checkPads(); }
        @Override public void onInputDeviceChanged(int id) { checkPads(); }
    };

    /** In the game. */
    public TouchControllerView(Context c) {
        this(c, null);
    }

    /** With onDone: the editor (EditorActivity), no native calls. */
    public TouchControllerView(Context c, Runnable onDone) {
        super(c);
        onEditorDone = onDone;
        inGame = onDone == null;
        editing = !inGame;
        density = getResources().getDisplayMetrics().density;
        prefs = Settings.prefs(c);
        touchPrefs = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        // saved positions are offsets from one set of factory defaults; when
        // the defaults change, start again from the new ones
        if (touchPrefs.getInt("layout_version", 0) != LAYOUT_VERSION)
            touchPrefs.edit().clear().putInt("layout_version", LAYOUT_VERSION).apply();
        mode = prefs.getInt(Settings.K_PAD_MODE, Settings.MODE_STEERING);
        steer = prefs.getInt(Settings.K_STEER, Settings.STEER_STICK);
        autoHide = prefs.getBoolean(Settings.K_PAD_AUTOHIDE, true);
        haptic = prefs.getBoolean(Settings.K_HAPTIC, true);
        showFps = inGame && prefs.getBoolean(Settings.K_FPS, false);
        controlsVisible = !inGame || prefs.getBoolean(Settings.K_PAD, true);
        for (int m = 0; m < MODES; m++) opacity[m] = touchPrefs.getFloat("m" + m + "_opacity", .7f);
        fullLockSin = (float) Math.sin(Math.toRadians(Settings.tiltAngle(prefs.getInt(Settings.K_TILT_SENS, 5))));
        sensors = (SensorManager) c.getSystemService(Context.SENSOR_SERVICE);
        Sensor g = sensors == null ? null : sensors.getDefaultSensor(Sensor.TYPE_GRAVITY);
        accelOnly = g == null && sensors != null;
        gravity = g != null ? g : sensors == null ? null : sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        inputManager = (InputManager) c.getSystemService(Context.INPUT_SERVICE);

        setWillNotDraw(false);
        setFocusable(false);
        fill.setStyle(Paint.Style.FILL);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(dp(1.6f));
        frame.setStyle(Paint.Style.STROKE);
        frame.setStrokeWidth(dp(2.5f));
        frame.setColor(ACCENT);
        text.setColor(Color.WHITE);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(Typeface.DEFAULT_BOLD);
    }

    // ------------------------------------------------------------------ setup

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (inGame) {
            if (inputManager != null) inputManager.registerInputDeviceListener(padListener, getHandler());
            checkPads();
            showToggle();
        }
        updateTilt();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (inputManager != null) inputManager.unregisterInputDeviceListener(padListener);
        removeCallbacks(hideToggle);
        stopTilt();
        super.onDetachedFromWindow();
    }

    /** B3xActivity: pause/resume the tilt sensor with the activity. */
    void onPauseResume(boolean resumed) {
        if (resumed) updateTilt(); else stopTilt();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        width = w; height = h;
        unit = Math.min(w / 16f, h / 9f);
        build();
    }

    private void build() {
        controls.clear();
        final int F = Settings.MODE_FULL, S = Settings.MODE_STEERING;

        // ---------------- FULL: the original Xbox controller ----------------
        Control lt = add(new Control("lt", "LT", null, K_RECT, 0, -1, 1, 0, 1.45f, .62f));
        Control rt = add(new Control("rt", "RT", null, K_RECT, 0, -1, 2, 0, 1.45f, .62f));

        Control back = add(new Control("back", "BACK", null, K_RECT, BACK, -1, 0, 0, .95f, .44f));
        Control start = add(new Control("start", "START", null, K_RECT, START, -1, 0, 0, .95f, .44f));

        Control ls = add(new Control("ls", "L", null, K_STICK, 0, -1, 0, 0, .9f, 0));
        Control rs = add(new Control("rs", "R", null, K_STICK, 0, -1, 0, 0, .9f, 0));

        Control dpad = add(new Control("dpad", "", null, K_DPAD, 0, -1, 0, 0, .95f, 0));

        Control a = add(new Control("a", "A", null, K_ROUND, 0, A, 0, 0, .40f, 0));
        Control b = add(new Control("b", "B", null, K_ROUND, 0, B, 0, 0, .40f, 0));
        Control x = add(new Control("x", "X", null, K_ROUND, 0, X, 0, 0, .40f, 0));
        Control y = add(new Control("y", "Y", null, K_ROUND, 0, Y, 0, 0, .40f, 0));
        a.color = Color.rgb(0x3F, 0xB9, 0x50); b.color = Color.rgb(0xE5, 0x53, 0x4B);
        x.color = Color.rgb(0x44, 0x93, 0xF8); y.color = Color.rgb(0xE3, 0xB3, 0x41);

        Control blk = add(new Control("black", "BLK", null, K_ROUND, 0, BLACK, 0, 0, .30f, 0));
        Control wht = add(new Control("white", "WHT", null, K_ROUND, 0, WHITE, 0, 0, .30f, 0));
        blk.color = Color.rgb(0x90, 0x90, 0x90);

        Control l3 = add(new Control("l3", "L3", null, K_ROUND, L3, -1, 0, 0, .28f, 0));
        Control r3 = add(new Control("r3", "R3", null, K_ROUND, R3, -1, 0, 0, .28f, 0));


        // FULL, placed like a held Xbox controller in the side bars: left
        // thumb = left stick (L3 beside it) over the D-pad, right thumb = the
        // face diamond with Black/White above it over the right stick (R3
        // beside it); triggers at the top corners, Back/Start inside them.
        {
            float u = unit, cxR = width - 2.0f * u;
            place(lt, F, 1.25f * u, .6f * u, 1f);
            place(rt, F, width - 1.25f * u, .6f * u, 1f);
            place(back, F, 2.9f * u, .6f * u, 1f);
            place(start, F, width - 2.9f * u, .6f * u, 1f);
            place(ls, F, 2.0f * u, height * .5f - .55f * u, 1f);
            place(l3, F, .55f * u, height * .5f - 1.75f * u, 1f);
            place(dpad, F, 2.0f * u, height - 1.55f * u, 1.05f);
            float fy = height * .5f - .55f * u, st = .8f * u;
            place(a, F, cxR, fy + st, 1f);
            place(b, F, cxR + st, fy, 1f);
            place(x, F, cxR - st, fy, 1f);
            place(y, F, cxR, fy - st, 1f);
            place(wht, F, cxR + st, fy - 1.85f * st, 1f);
            place(blk, F, cxR - st, fy - 1.85f * st, 1f);
            place(rs, F, cxR, height - 1.6f * u, .95f);
            place(r3, F, width - .55f * u, height - 2.6f * u, 1f);
        }

        // ---------------- STEERING: driving ----------------
        // Placed in units (min(w/16, h/9)) from the screen edges, so the
        // clusters stay tight on any aspect: left thumb = steering, D-pad and
        // menu buttons; right thumb = pedals, boost and the face buttons
        // as one diamond.
        float u = unit;
        // left
        if (steer == Settings.STEER_STICK) place(ls, S, 2.05f * u, height - 2.0f * u, 1.3f);
        Control al = add(new Control("steer_l", "\u25C0", null, K_ARROW, 0, -1, 0, -1, 1.35f, 1.35f));
        Control ar = add(new Control("steer_r", "\u25B6", null, K_ARROW, 0, -1, 0, 1, 1.35f, 1.35f));
        if (steer == Settings.STEER_ARROWS) {
            place(al, S, 1.25f * u, height - 1.45f * u, 1f);
            place(ar, S, 2.75f * u, height - 1.45f * u, 1f);
        }
        place(dpad, S, 2.05f * u, height * .5f - .45f * u, 1.25f);
        place(back, S, 1.3f * u, 1.45f * u, 1.35f);
        place(start, S, 2.8f * u, 1.45f * u, 1.35f);
        place(blk, S, 1.3f * u, .62f * u, 1.2f);
        place(wht, S, 2.8f * u, .62f * u, 1.2f);
        // right: pedals side by side, bottoms on one line
        Control gas = add(new Control("gas", "RT", "GAS", K_PEDAL, 0, -1, 2, 0, 1.25f, 2.5f));
        Control brk = add(new Control("brake", "LT", "BRAKE", K_PEDAL, 0, -1, 1, 0, 1.15f, 1.9f));
        float foot = height - .45f * u;
        float gasX = width - 1.15f * u, brkX = gasX - 1.45f * u;
        place(gas, S, gasX, foot - 2.5f * u * .5f, 1f);
        place(brk, S, brkX, foot - 1.9f * u * .5f, 1f);
        // face diamond right above the pedals, A (boost) at the bottom
        Control boost = add(new Control("boost", "A", "BOOST", K_ROUND, 0, A, 0, 0, .46f, 0));
        boost.color = a.color;
        float dx = (gasX + brkX) * .5f, dy = foot - 2.5f * u - .5f * u - .46f * u;
        float step = .82f * u;
        place(boost, S, dx, dy, 1f);
        place(x, S, dx - step, dy - step, 1.05f);
        place(b, S, dx + step, dy - step, 1.05f);
        place(y, S, dx, dy - 2 * step, 1.05f);
        lt.visibleIn &= ~(1 << S);
        rt.visibleIn &= ~(1 << S);

        for (Control c : controls) load(c);
        useMode(mode);
        layoutUi();
        send();
        invalidate();
    }

    /** Factory position in pixels for a mode (stored as fractions). */
    private void place(Control c, int mode, float px, float py, float s) {
        c.at(mode, px / width, py / height, s);
    }

    private Control add(Control c) {
        controls.add(c);
        return c;
    }

    private void load(Control c) {
        for (int m = 0; m < MODES; m++) {
            String k = "m" + m + "_" + c.id;
            if (!touchPrefs.contains(k + "_x")) continue;
            c.px[m] = touchPrefs.getFloat(k + "_x", 0f);
            c.py[m] = touchPrefs.getFloat(k + "_y", 0f);
            c.ps[m] = touchPrefs.getFloat(k + "_s", 1f);
            c.own[m] = true;
        }
    }

    private void useMode(int m) {
        for (Control c : controls) c.use(m);
        invalidate();
    }

    private void layoutUi() {
        text.setTextSize(dp(11));
        float h = Math.max(dp(26), unit * .42f);
        float top = dp(6);
        float tw = Math.max(dp(64), text.measureText("TOUCH") + dp(24));
        toggleBtn.set(width * .5f - tw * .5f, top, width * .5f + tw * .5f, top + h);
        gearBtn.set(toggleBtn.right + dp(8), top, toggleBtn.right + dp(8) + h, top + h);
        float mw = text.measureText("DRIVE") + dp(22);
        modeBtn.set(toggleBtn.left - dp(8) - mw, top, toggleBtn.left - dp(8), top + h);
        menuBtn.set(modeBtn.left - dp(8) - h, top, modeBtn.left - dp(8), top + h);

        // editor bar: [-] SIZE [+]   [-] OPACITY [+]  /  [MODE] [RESET] [CANCEL] [DONE]
        text.setTextSize(dp(12));
        float side = Math.max(dp(34), unit * .6f), gap = dp(10);
        float sw = text.measureText("SIZE 220 %") + dp(16);
        float ow = text.measureText("OPACITY 100 %") + dp(16);
        float row = side * 4 + sw + ow + gap * 7;
        float x = width * .5f - row * .5f, y = height * .25f;
        sizeMinus.set(x, y, x + side, y + side); x += side + gap;
        sizeTextX = x + sw * .5f; x += sw + gap;
        sizePlus.set(x, y, x + side, y + side); x += side + gap * 3;
        opaMinus.set(x, y, x + side, y + side); x += side + gap;
        opaTextX = x + ow * .5f; x += ow + gap;
        opaPlus.set(x, y, x + side, y + side);
        float mw2 = text.measureText("LAYOUT: DRIVE") + dp(24);
        float rw = text.measureText("RESET") + dp(26), cw = text.measureText("CANCEL") + dp(26),
              dw = text.measureText("DONE") + dp(26);
        row = mw2 + rw + cw + dw + gap * 3;
        x = width * .5f - row * .5f; y += side + gap;
        modeEdit.set(x, y, x + mw2, y + side); x += mw2 + gap;
        reset.set(x, y, x + rw, y + side); x += rw + gap;
        cancel.set(x, y, x + cw, y + side); x += cw + gap;
        done.set(x, y, x + dw, y + side);
        text.setTextSize(dp(11));
        float hw = text.measureText(HELP) * .5f + dp(12);
        y += side + gap;
        helpBg.set(width * .5f - hw, y, width * .5f + hw, y + dp(24));
    }

    // ---------------------------------------------------------------- drawing

    @Override
    protected void onDraw(Canvas canvas) {
        if (editing) {
            for (Control c : controls) if (c.shown() && !hiddenBySteer(c)) drawControl(canvas, c);
            if (picked != null) {
                float rr = picked.kind == K_STICK ? picked.w() + dp(6) : Math.max(picked.w(), picked.h()) * .5f + dp(6);
                canvas.drawRect(picked.cx - rr, picked.cy - rr, picked.cx + rr, picked.cy + rr, frame);
            }
            drawEditor(canvas);
            return;
        }
        if (controlsVisible && !physicalPad)
            for (Control c : controls) if (c.shown() && !hiddenBySteer(c)) drawControl(canvas, c);
        if (toggleVisible) {
            uiButton(canvas, toggleBtn, controlsVisible ? "HIDE" : "TOUCH", dp(11));
            uiButton(canvas, gearBtn, "\u2699", dp(15));
            uiButton(canvas, modeBtn, MODE_NAMES[mode], dp(11));
            uiButton(canvas, menuBtn, "\u2630", dp(15));
        }
        if (showFps && !fps.isEmpty()) {
            text.setTextSize(dp(12));
            text.setTextAlign(Paint.Align.RIGHT);
            float tw = text.measureText(fps);
            fill.setColor(Color.argb(140, 0, 0, 0));
            canvas.drawRoundRect(width - tw - dp(16), dp(4), width - dp(4), dp(24), dp(4), dp(4), fill);
            text.setColor(Color.rgb(0xF5, 0xE0, 0x40));
            canvas.drawText(fps, width - dp(10), dp(19), text);
            text.setColor(Color.WHITE);
            text.setTextAlign(Paint.Align.CENTER);
        }
    }

    /** Steering-mode controls that the chosen steering does not use. */
    private boolean hiddenBySteer(Control c) {
        if (mode != Settings.MODE_STEERING) return false;
        if (c.id.equals("ls")) return steer != Settings.STEER_STICK;
        if (c.kind == K_ARROW) return steer != Settings.STEER_ARROWS;
        return false;
    }

    private int alpha(int color, float a) {
        return Color.argb(Math.round(Color.alpha(color) * a), Color.red(color), Color.green(color), Color.blue(color));
    }

    private void drawControl(Canvas canvas, Control c) {
        float op = opacity[mode];
        stroke.setColor(alpha(c.color, op));
        switch (c.kind) {
            case K_STICK: {
                float rad = c.w();
                fill.setColor(alpha(FILL, op));
                canvas.drawCircle(c.cx, c.cy, rad, fill);
                canvas.drawCircle(c.cx, c.cy, rad, stroke);
                float kx = c.cx + c.kx * rad * .6f, ky = c.cy + c.ky * rad * .6f;
                fill.setColor(alpha(c.down ? PRESSED : Color.argb(170, 200, 200, 205), op));
                canvas.drawCircle(kx, ky, rad * .42f, fill);
                label(canvas, c.label, kx, ky, rad * .36f, op);
                break;
            }
            case K_ROUND: {
                float rad = c.w();
                fill.setColor(alpha(c.down ? PRESSED : FILL, op));
                canvas.drawCircle(c.cx, c.cy, rad, fill);
                canvas.drawCircle(c.cx, c.cy, rad, stroke);
                if (c.sub != null) {
                    label(canvas, c.label, c.cx, c.cy - rad * .14f, rad * .62f, op);
                    label(canvas, c.sub, c.cx, c.cy + rad * .45f, rad * .26f, op);
                } else {
                    label(canvas, c.label, c.cx, c.cy, rad * (c.label.length() > 1 ? .55f : .8f), op);
                }
                break;
            }
            case K_RECT: case K_PEDAL: case K_ARROW: {
                r.set(c.cx - c.w() * .5f, c.cy - c.h() * .5f, c.cx + c.w() * .5f, c.cy + c.h() * .5f);
                float rr = Math.min(c.w(), c.h()) * (c.kind == K_PEDAL ? .22f : .3f);
                fill.setColor(alpha(c.down ? PRESSED : FILL, op));
                canvas.drawRoundRect(r, rr, rr, fill);
                canvas.drawRoundRect(r, rr, rr, stroke);
                if (c.kind == K_PEDAL) {
                    // ribs, like a pedal
                    float sx = r.left + c.w() * .22f, ex = r.right - c.w() * .22f;
                    for (int i = 1; i <= 3; i++) {
                        float yy = r.top + c.h() * (.42f + i * .12f);
                        canvas.drawLine(sx, yy, ex, yy, stroke);
                    }
                    label(canvas, c.sub, c.cx, r.top + c.h() * .16f, c.w() * .2f, op);
                    label(canvas, c.label, c.cx, r.top + c.h() * .34f, c.w() * .3f, op);
                } else {
                    label(canvas, c.label, c.cx, c.cy, Math.min(c.h() * .45f, c.w() * .26f) * (c.kind == K_ARROW ? 1.4f : 1f), op);
                }
                break;
            }
            case K_DPAD: {
                // one connected cross: five equal squares, the outline only
                float s = c.w(), q = s * 2f / 3f, h = q * .5f;   // q: side of a square
                float cx = c.cx, cy = c.cy;
                int bits = c.down ? currentDpad : 0;
                path.reset();
                path.moveTo(cx - h, cy - h - q);
                path.lineTo(cx + h, cy - h - q);
                path.lineTo(cx + h, cy - h);
                path.lineTo(cx + h + q, cy - h);
                path.lineTo(cx + h + q, cy + h);
                path.lineTo(cx + h, cy + h);
                path.lineTo(cx + h, cy + h + q);
                path.lineTo(cx - h, cy + h + q);
                path.lineTo(cx - h, cy + h);
                path.lineTo(cx - h - q, cy + h);
                path.lineTo(cx - h - q, cy - h);
                path.lineTo(cx - h, cy - h);
                path.close();
                fill.setColor(alpha(FILL, op));
                canvas.drawPath(path, fill);
                fill.setColor(alpha(PRESSED, op));
                if ((bits & UP) != 0) canvas.drawRect(cx - h, cy - h - q, cx + h, cy - h, fill);
                if ((bits & DOWN) != 0) canvas.drawRect(cx - h, cy + h, cx + h, cy + h + q, fill);
                if ((bits & LEFT) != 0) canvas.drawRect(cx - h - q, cy - h, cx - h, cy + h, fill);
                if ((bits & RIGHT) != 0) canvas.drawRect(cx + h, cy - h, cx + h + q, cy + h, fill);
                canvas.drawPath(path, stroke);
                break;
            }
        }
    }

    private int currentDpad;

    private void arm(Canvas canvas, float x, float y, float hw, float hh, boolean on, float op) {
        r.set(x - hw * .5f, y - hh * .5f, x + hw * .5f, y + hh * .5f);
        if (hw < hh) r.set(x - hw * .5f, y - hh * .5f, x + hw * .5f, y + hh * .5f);
        fill.setColor(alpha(on ? PRESSED : FILL, op));
        canvas.drawRoundRect(r, dp(5), dp(5), fill);
        canvas.drawRoundRect(r, dp(5), dp(5), stroke);
    }

    private void label(Canvas canvas, String s, float x, float y, float size, float op) {
        if (s == null || s.isEmpty()) return;
        text.setTextSize(size);
        text.setColor(alpha(Color.WHITE, Math.min(1f, op + .15f)));
        text.getFontMetrics(fm);
        canvas.drawText(s, x, y - (fm.ascent + fm.descent) * .5f, text);
        text.setColor(Color.WHITE);
    }

    private void uiButton(Canvas canvas, RectF b, String s, float size) {
        fill.setColor(UI_BG);
        canvas.drawRoundRect(b, dp(7), dp(7), fill);
        stroke.setColor(ACCENT);
        canvas.drawRoundRect(b, dp(7), dp(7), stroke);
        text.setTextSize(size);
        text.getFontMetrics(fm);
        canvas.drawText(s, b.centerX(), b.centerY() - (fm.ascent + fm.descent) * .5f, text);
    }

    private void drawEditor(Canvas canvas) {
        uiButton(canvas, sizeMinus, "\u2212", dp(18));
        uiButton(canvas, sizePlus, "+", dp(18));
        uiButton(canvas, opaMinus, "\u2212", dp(18));
        uiButton(canvas, opaPlus, "+", dp(18));
        String st = picked == null ? "SIZE (ALL)" : "SIZE " + Math.round(picked.scale * 100) + " %";
        pill(canvas, st, sizeTextX, sizeMinus.centerY());
        pill(canvas, "OPACITY " + Math.round(opacity[mode] * 100) + " %", opaTextX, opaMinus.centerY());
        uiButton(canvas, modeEdit, "LAYOUT: " + MODE_NAMES[mode], dp(12));
        uiButton(canvas, reset, "RESET", dp(12));
        uiButton(canvas, cancel, "CANCEL", dp(12));
        uiButton(canvas, done, "DONE", dp(12));
        fill.setColor(UI_BG);
        canvas.drawRoundRect(helpBg, dp(6), dp(6), fill);
        text.setTextSize(dp(11));
        text.getFontMetrics(fm);
        canvas.drawText(HELP,
                helpBg.centerX(), helpBg.centerY() - (fm.ascent + fm.descent) * .5f, text);
    }

    private void pill(Canvas canvas, String s, float x, float y) {
        text.setTextSize(dp(12));
        float half = text.measureText(s) * .5f + dp(8);
        fill.setColor(UI_BG);
        canvas.drawRoundRect(x - half, y - dp(13), x + half, y + dp(13), dp(7), dp(7), fill);
        text.getFontMetrics(fm);
        canvas.drawText(s, x, y - (fm.ascent + fm.descent) * .5f, text);
    }

    // ------------------------------------------------------------------ touch

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (editing) return editTouch(e);
        int action = e.getActionMasked(), idx = e.getActionIndex(), id = e.getPointerId(idx);
        float x = e.getX(idx), y = e.getY(idx);

        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            if (toggleVisible) {
                if (gearBtn.contains(x, y)) { openEditor(); return true; }
                if (toggleBtn.contains(x, y)) {
                    controlsVisible = !controlsVisible;
                    if (!controlsVisible) releaseAll();
                    showToggle();
                    return true;
                }
                if (modeBtn.contains(x, y)) { switchMode(); showToggle(); return true; }
                if (menuBtn.contains(x, y)) { releaseAll(); if (onMenu != null) onMenu.run(); return true; }
            }
            if (toggleBtn.contains(x, y) || gearBtn.contains(x, y) || modeBtn.contains(x, y) || menuBtn.contains(x, y)
                    || (physicalPad && autoHide)) {
                if (physicalPad && autoHide) { physicalPad = false; controlsVisible = true; }
                showToggle();
                return true;
            }
            if (!controlsVisible) return false;
            for (int i = controls.size() - 1; i >= 0; i--) {
                Control c = controls.get(i);
                if (c.pointer < 0 && c.shown() && !hiddenBySteer(c) && c.contains(x, y)) {
                    c.pointer = id;
                    pointers.put(id, c);
                    press(c, x, y);
                    if (haptic) performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    send();
                    invalidate();
                    return true;
                }
            }
            return false;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            boolean any = false;
            for (int i = 0; i < e.getPointerCount(); i++) {
                Control c = pointers.get(e.getPointerId(i));
                if (c != null) { move(c, e.getX(i), e.getY(i)); any = true; }
            }
            if (any) { send(); invalidate(); }
            return any;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
            Control c = pointers.get(id);
            if (c == null) return false;
            release(c);
            pointers.remove(id);
            send();
            invalidate();
            return true;
        }
        if (action == MotionEvent.ACTION_CANCEL) { releaseAll(); return true; }
        return false;
    }

    private void press(Control c, float x, float y) {
        c.down = true;
        move(c, x, y);
    }

    private void move(Control c, float x, float y) {
        if (c.kind == K_STICK) {
            float rad = c.w() * .6f, dx = (x - c.cx) / rad, dy = (y - c.cy) / rad;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            if (d > 1f) { dx /= d; dy /= d; }
            c.kx = dx; c.ky = dy;
        } else if (c.kind == K_DPAD) {
            float dx = x - c.cx, dy = y - c.cy, dead = c.w() * .2f;
            int bits = 0;
            if (Math.abs(dx) > dead || Math.abs(dy) > dead) {
                double ang = Math.toDegrees(Math.atan2(-dy, dx));        // 0 right, 90 up
                if (ang < 0) ang += 360;
                if (ang < 67.5 || ang >= 292.5) bits |= RIGHT;
                if (ang >= 22.5 && ang < 157.5) bits |= UP;
                if (ang >= 112.5 && ang < 247.5) bits |= LEFT;
                if (ang >= 202.5 && ang < 337.5) bits |= DOWN;
            }
            currentDpad = bits;
        }
    }

    private void release(Control c) {
        c.down = false;
        c.pointer = -1;
        c.kx = c.ky = 0;
        if (c.kind == K_DPAD) currentDpad = 0;
    }

    private void releaseAll() {
        for (Control c : controls) release(c);
        pointers.clear();
        send();
        invalidate();
    }

    private void switchMode() {
        releaseAll();
        mode = (mode + 1) % MODES;
        prefs.edit().putInt(Settings.K_PAD_MODE, mode).apply();
        useMode(mode);
        updateTilt();
        send();
    }

    // -------------------------------------------------------------- to native

    private int lastB = -1, lastF = -1, lastLt = -1, lastRt = -1, lastLx, lastLy, lastRx = -1, lastRy = -1;

    private void send() {
        if (!inGame) return;
        int buttons = 0, face = 0, lt = 0, rt = 0, lx = 0, ly = 0, rx = 0, ry = 0;
        boolean on = controlsVisible && !physicalPad;
        if (on) {
            for (Control c : controls) {
                if (!c.down || !c.shown() || hiddenBySteer(c)) continue;
                buttons |= c.digital;
                if (c.face >= 0) face |= 1 << c.face;
                if (c.trigger == 1) lt = 255;
                if (c.trigger == 2) rt = 255;
                if (c.kind == K_DPAD) buttons |= currentDpad;
                if (c.kind == K_ARROW) lx = c.arrow * 32767;
                if (c.kind == K_STICK) {
                    int vx = axis(c.kx), vy = axis(-c.ky);
                    if (c.id.equals("ls")) { lx = vx; ly = vy; } else { rx = vx; ry = vy; }
                }
            }
            if (tiltActive()) lx = Math.round(tilt * 32767);
        }
        if (buttons != lastB || face != lastF || lt != lastLt || rt != lastRt || lx != lastLx || ly != lastLy) {
            lastB = buttons; lastF = face; lastLt = lt; lastRt = rt; lastLx = lx; lastLy = ly;
            if (DEBUG) android.util.Log.d("b3x-pad", "b=" + buttons + " f=" + face + " lt=" + lt + " rt=" + rt + " lx=" + lx + " ly=" + ly);
            try { B3xActivity.nativeSetPad(buttons, face, lt, rt, lx, ly); } catch (UnsatisfiedLinkError ignored) { }
        }
        if (rx != lastRx || ry != lastRy) {
            lastRx = rx; lastRy = ry;
            try { B3xActivity.nativeSetPadRight(rx, ry); } catch (UnsatisfiedLinkError ignored) { }
        }
    }

    private static int axis(float v) {
        if (Math.abs(v) < .1f) return 0;
        float s = Math.signum(v) * (Math.abs(v) - .1f) / .9f;
        return Math.round(clamp(s, -1f, 1f) * 32767);
    }

    // ------------------------------------------------------------ pads, tilt

    private void checkPads() {
        boolean pad = false;
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice d = InputDevice.getDevice(id);
            if (d == null || d.isVirtual()) continue;
            int s = d.getSources();
            if ((s & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                    || (s & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) pad = true;
        }
        boolean hide = pad && autoHide;
        if (hide != physicalPad) {
            physicalPad = hide;
            if (hide) releaseAll();
            showToggle();
            updateTilt();
            send();
            invalidate();
        }
    }

    /** B3xActivity: any key/motion from a physical pad hides the overlay. */
    void onPhysicalInput() {
        if (autoHide && !physicalPad) {
            physicalPad = true;
            releaseAll();
            invalidate();
        }
    }

    private boolean tiltActive() {
        return inGame && mode == Settings.MODE_STEERING && steer == Settings.STEER_TILT
                && controlsVisible && !physicalPad && !editing;
    }

    private void updateTilt() {
        boolean want = tiltActive() && gravity != null;
        if (want && !listening) {
            listening = sensors.registerListener(tiltListener, gravity, SensorManager.SENSOR_DELAY_GAME);
        } else if (!want) {
            stopTilt();
        }
    }

    private void stopTilt() {
        if (listening) sensors.unregisterListener(tiltListener);
        listening = false;
        tilt = 0;
    }

    void setFps(float v) {
        if (!showFps) return;
        fps = String.format(Locale.US, "%.0f FPS", v);
        invalidate();
    }

    private void showToggle() {
        toggleVisible = true;
        removeCallbacks(hideToggle);
        postDelayed(hideToggle, TOGGLE_MS);
        invalidate();
    }

    // ----------------------------------------------------------------- editor

    void openEditor() {
        releaseAll();
        removeCallbacks(hideToggle);
        toggleVisible = false;
        editing = true;
        picked = null;
        editPointer = -1;
        stopTilt();
        invalidate();
    }

    private boolean editTouch(MotionEvent e) {
        int action = e.getActionMasked(), idx = e.getActionIndex();
        float x = e.getX(idx), y = e.getY(idx);
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                if (editPointer != -1) return true;
                if (sizeMinus.contains(x, y)) resize(-STEP);
                else if (sizePlus.contains(x, y)) resize(STEP);
                else if (opaMinus.contains(x, y)) opacity[mode] = clamp(round1(opacity[mode] - STEP), OPA_MIN, 1f);
                else if (opaPlus.contains(x, y)) opacity[mode] = clamp(round1(opacity[mode] + STEP), OPA_MIN, 1f);
                else if (modeEdit.contains(x, y)) {
                    for (Control c : controls) if (c.shown()) c.keep(mode);
                    mode = (mode + 1) % MODES;
                    picked = null;
                    useMode(mode);
                } else if (reset.contains(x, y)) {
                    for (Control c : controls) { c.own[mode] = false; c.use(mode); }
                    opacity[mode] = .7f;
                    picked = null;
                } else if (cancel.contains(x, y)) finishEditor(false);
                else if (done.contains(x, y)) finishEditor(true);
                else {
                    picked = null;
                    for (int i = controls.size() - 1; i >= 0; i--) {
                        Control c = controls.get(i);
                        if (c.shown() && !hiddenBySteer(c) && c.contains(x, y)) { picked = c; break; }
                    }
                    if (picked != null) {
                        editPointer = e.getPointerId(idx);
                        grabX = x - picked.cx;
                        grabY = y - picked.cy;
                    }
                }
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE: {
                int i = editPointer == -1 ? -1 : e.findPointerIndex(editPointer);
                if (i < 0 || picked == null) return true;
                picked.cx = clamp(e.getX(i) - grabX, 0, width);
                picked.cy = clamp(e.getY(i) - grabY, 0, height);
                picked.keep(mode);
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                if (e.getPointerId(idx) == editPointer) editPointer = -1;
                return true;
            default:
                editPointer = -1;
                return true;
        }
    }

    private void resize(float step) {
        if (picked != null) {
            picked.scale = clamp(round1(picked.scale + step), SCALE_MIN, SCALE_MAX);
            picked.keep(mode);
        } else {
            for (Control c : controls) {
                if (!c.shown()) continue;
                c.scale = clamp(round1(c.scale + step), SCALE_MIN, SCALE_MAX);
                c.keep(mode);
            }
        }
    }

    private void finishEditor(boolean save) {
        if (save) {
            for (Control c : controls) if (c.shown()) c.keep(mode);
            SharedPreferences.Editor ed = touchPrefs.edit();
            for (int m = 0; m < MODES; m++) {
                ed.putFloat("m" + m + "_opacity", opacity[m]);
                for (Control c : controls) {
                    String k = "m" + m + "_" + c.id;
                    if (c.own[m]) {
                        ed.putFloat(k + "_x", c.px[m]).putFloat(k + "_y", c.py[m]).putFloat(k + "_s", c.ps[m]);
                    } else {
                        ed.remove(k + "_x").remove(k + "_y").remove(k + "_s");
                    }
                }
            }
            ed.apply();
            prefs.edit().putInt(Settings.K_PAD_MODE, mode).apply();
        }
        if (!inGame) {
            onEditorDone.run();
            return;
        }
        editing = false;
        picked = null;
        if (!save) {                          // back to what is saved
            for (int m = 0; m < MODES; m++) opacity[m] = touchPrefs.getFloat("m" + m + "_opacity", .7f);
            for (Control c : controls) { java.util.Arrays.fill(c.own, false); load(c); }
        }
        useMode(mode);
        updateTilt();
        showToggle();
    }

    /** Back in the game while the in-game editor is open: cancel it. */
    boolean onBack() {
        if (editing && inGame) { finishEditor(false); return true; }
        return false;
    }

    // ------------------------------------------------------------------ utils

    private float dp(float v) { return v * density; }

    private static float round1(float v) { return Math.round(v * 10f) / 10f; }

    private static float clamp(float v, float lo, float hi) { return v < lo ? lo : v > hi ? hi : v; }
}
