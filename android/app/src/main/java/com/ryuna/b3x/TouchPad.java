package com.ryuna.b3x;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

/**
 * Transparent overlay over the SDL surface: an Xbox pad drawn with
 * Canvas (left stick, D-pad, A/B/X/Y, LT/RT, Black/White, Back/Start)
 * and an optional FPS counter. Every change of the touch state goes to
 * native code through {@link B3xActivity#nativeSetPad}; the game reads it
 * as controller port 0 (xinput_device.c).
 */
public class TouchPad extends View {
    // XBOX_GAMEPAD_* digital bits
    static final int DPAD_UP = 0x01, DPAD_DOWN = 0x02, DPAD_LEFT = 0x04, DPAD_RIGHT = 0x08;
    static final int START = 0x10, BACK = 0x20;
    // analog button indices (bit n of the face mask)
    static final int A = 0, B = 1, X = 2, Y = 3, BLACK = 4, WHITE = 5;

    /** One round or rectangular control. */
    private static final class Btn {
        final String label;
        final int digital;      // XBOX_GAMEPAD_* bit, or 0
        final int face;         // analog index, or -1
        final int trigger;      // 0 none, 1 LT, 2 RT
        final int color;
        final boolean round;
        RectF r = new RectF();
        boolean down;

        Btn(String label, int digital, int face, int trigger, int color, boolean round) {
            this.label = label;
            this.digital = digital;
            this.face = face;
            this.trigger = trigger;
            this.color = color;
            this.round = round;
        }
    }

    private static final boolean DEBUG = android.util.Log.isLoggable("b3x-pad", android.util.Log.DEBUG);

    private final Btn[] mBtns = {
        new Btn("A", 0, A, 0, 0xFF3FB950, true),
        new Btn("B", 0, B, 0, 0xFFE5534B, true),
        new Btn("X", 0, X, 0, 0xFF4493F8, true),
        new Btn("Y", 0, Y, 0, 0xFFD29922, true),
        new Btn("LT", 0, -1, 1, 0xFFB0B0B0, false),
        new Btn("RT", 0, -1, 2, 0xFFB0B0B0, false),
        new Btn("BLK", 0, BLACK, 0, 0xFF808080, false),
        new Btn("WHT", 0, WHITE, 0, 0xFFE0E0E0, false),
        new Btn("BACK", BACK, -1, 0, 0xFFB0B0B0, false),
        new Btn("START", START, -1, 0, 0xFFB0B0B0, false),
        new Btn("\u25B2", DPAD_UP, -1, 0, 0xFFB0B0B0, false),
        new Btn("\u25BC", DPAD_DOWN, -1, 0, 0xFFB0B0B0, false),
        new Btn("\u25C0", DPAD_LEFT, -1, 0, 0xFFB0B0B0, false),
        new Btn("\u25B6", DPAD_RIGHT, -1, 0, 0xFFB0B0B0, false),
    };

    // left stick
    private float mStickCx, mStickCy, mStickR;
    private float mKnobX, mKnobY;
    private int mStickPointer = -1;

    private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mLine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mFpsText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mFpsBg = new Paint();

    private boolean mPadVisible;
    private boolean mShowFps;
    private float mScale;
    private int mAlpha;         // 0..255
    private String mFps = "";

    private int mLastButtons = -1, mLastFace = -1, mLastLt = -1, mLastRt = -1, mLastLx, mLastLy;

    public TouchPad(Context ctx, boolean padVisible, boolean showFps, float scale, int opacityPct) {
        super(ctx);
        mPadVisible = padVisible;
        mShowFps = showFps;
        mScale = scale;
        mAlpha = Math.max(20, Math.min(100, opacityPct)) * 255 / 100;
        mLine.setStyle(Paint.Style.STROKE);
        mText.setTextAlign(Paint.Align.CENTER);
        mText.setTypeface(Typeface.DEFAULT_BOLD);
        mFpsText.setTypeface(Typeface.MONOSPACE);
        mFpsText.setColor(0xFFFFFF00);
        mFpsBg.setColor(0x80000000);
        setFocusable(false);
    }

    public void setPadVisible(boolean v) {
        if (mPadVisible == v) return;
        mPadVisible = v;
        if (!v) releaseAll();
        invalidate();
    }

    public boolean isPadVisible() { return mPadVisible; }

    public void setFps(float fps) {
        if (!mShowFps) return;
        mFps = String.format(java.util.Locale.US, "%.0f FPS", fps);
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        layoutControls(w, h);
    }

    private void layoutControls(int w, int h) {
        float u = Math.min(w, h) / 10f * mScale;     // base unit
        float m = u * 0.45f;                          // margin

        // left: stick bottom-left, D-pad above it
        mStickR = u * 1.6f;
        mStickCx = m + mStickR + u * 0.3f;
        mStickCy = h - m - mStickR;
        mKnobX = mStickCx; mKnobY = mStickCy;

        float dp = u * 0.75f;                         // d-pad cell
        float dcx = mStickCx + mStickR + dp * 2.2f, dcy = h - m - dp * 1.6f;
        set(DPAD_UP, dcx - dp / 2, dcy - dp * 1.5f, dp, dp);
        set(DPAD_DOWN, dcx - dp / 2, dcy + dp * 0.5f, dp, dp);
        set(DPAD_LEFT, dcx - dp * 1.5f, dcy - dp / 2, dp, dp);
        set(DPAD_RIGHT, dcx + dp * 0.5f, dcy - dp / 2, dp, dp);

        // right: face diamond
        float fr = u * 0.62f;                         // button radius
        float fcx = w - m - fr * 3.2f, fcy = h - m - fr * 3.2f;
        float off = fr * 2.05f;
        setRound(byFace(A), fcx, fcy + off, fr);
        setRound(byFace(B), fcx + off, fcy, fr);
        setRound(byFace(X), fcx - off, fcy, fr);
        setRound(byFace(Y), fcx, fcy - off, fr);

        // triggers: big pads on the shoulders (RT = accelerate in Burnout)
        float tw = u * 2.2f, th = u * 1.3f;
        byTrig(1).r.set(m, m + u * 0.6f, m + tw, m + u * 0.6f + th);
        byTrig(2).r.set(w - m - tw, m + u * 0.6f, w - m, m + u * 0.6f + th);
        // black/white under RT
        float bw = u * 1.0f, bh = u * 0.65f;
        byFace(BLACK).r.set(w - m - tw, m + u * 0.8f + th, w - m - tw + bw, m + u * 0.8f + th + bh);
        byFace(WHITE).r.set(w - m - bw, m + u * 0.8f + th, w - m, m + u * 0.8f + th + bh);

        // back/start: top centre
        float sw = u * 1.4f, sh = u * 0.6f;
        byDigital(BACK).r.set(w / 2f - sw - u * 0.2f, m, w / 2f - u * 0.2f, m + sh);
        byDigital(START).r.set(w / 2f + u * 0.2f, m, w / 2f + sw + u * 0.2f, m + sh);

        mText.setTextSize(u * 0.42f);
        mFpsText.setTextSize(Math.min(w, h) / 28f);
        mLine.setStrokeWidth(Math.max(2f, u * 0.05f));
    }

    private void set(int digital, float x, float y, float sw, float sh) {
        byDigital(digital).r.set(x, y, x + sw, y + sh);
    }

    private void setRound(Btn b, float cx, float cy, float r) {
        b.r.set(cx - r, cy - r, cx + r, cy + r);
    }

    private Btn byFace(int f) { for (Btn b : mBtns) if (b.face == f) return b; return null; }
    private Btn byTrig(int t) { for (Btn b : mBtns) if (b.trigger == t) return b; return null; }
    private Btn byDigital(int d) { for (Btn b : mBtns) if (b.digital == d) return b; return null; }

    @Override
    protected void onDraw(Canvas c) {
        if (mPadVisible) {
            // stick
            mFill.setColor(0xFF000000); mFill.setAlpha(mAlpha / 3);
            c.drawCircle(mStickCx, mStickCy, mStickR, mFill);
            mLine.setColor(0xFFFFFFFF); mLine.setAlpha(mAlpha / 2);
            c.drawCircle(mStickCx, mStickCy, mStickR, mLine);
            mFill.setColor(0xFFFFFFFF); mFill.setAlpha(mStickPointer >= 0 ? mAlpha : mAlpha / 2);
            c.drawCircle(mKnobX, mKnobY, mStickR * 0.42f, mFill);

            for (Btn b : mBtns) {
                mFill.setColor(b.down ? b.color : 0xFF000000);
                mFill.setAlpha(b.down ? mAlpha : mAlpha / 3);
                mLine.setColor(b.color); mLine.setAlpha(mAlpha);
                if (b.round) {
                    c.drawCircle(b.r.centerX(), b.r.centerY(), b.r.width() / 2, mFill);
                    c.drawCircle(b.r.centerX(), b.r.centerY(), b.r.width() / 2, mLine);
                } else {
                    float rr = b.r.height() * 0.25f;
                    c.drawRoundRect(b.r, rr, rr, mFill);
                    c.drawRoundRect(b.r, rr, rr, mLine);
                }
                mText.setColor(b.down ? 0xFF000000 : b.color); mText.setAlpha(mAlpha);
                float ty = b.r.centerY() - (mText.descent() + mText.ascent()) / 2;
                c.drawText(b.label, b.r.centerX(), ty, mText);
            }
        }
        if (mShowFps && !mFps.isEmpty()) {
            float pad = mFpsText.getTextSize() * 0.3f;
            float tw = mFpsText.measureText(mFps);
            float x = getWidth() - tw - pad * 3, y = pad;
            c.drawRect(x - pad, y, x + tw + pad, y + mFpsText.getTextSize() + pad * 2, mFpsBg);
            c.drawText(mFps, x, y + pad - mFpsText.ascent(), mFpsText);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (DEBUG) android.util.Log.d("b3x-pad", "touch " + e.getActionMasked() + " " + e.getX() + "," + e.getY());
        if (!mPadVisible) return false;
        int action = e.getActionMasked();
        int idx = e.getActionIndex();

        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            float x = e.getX(idx), y = e.getY(idx);
            float dx = x - mStickCx, dy = y - mStickCy;
            if (mStickPointer < 0 && dx * dx + dy * dy <= (mStickR * 1.5f) * (mStickR * 1.5f))
                mStickPointer = e.getPointerId(idx);
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            mStickPointer = -1;
        } else if (action == MotionEvent.ACTION_POINTER_UP) {
            if (e.getPointerId(idx) == mStickPointer) mStickPointer = -1;
        }

        // buttons: pressed while any live pointer (not the stick's) is on them
        for (Btn b : mBtns) b.down = false;
        mKnobX = mStickCx; mKnobY = mStickCy;
        for (int i = 0; i < e.getPointerCount(); i++) {
            boolean lifting = (action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_UP
                               || action == MotionEvent.ACTION_CANCEL) && i == idx;
            if (lifting) continue;
            float x = e.getX(i), y = e.getY(i);
            if (e.getPointerId(i) == mStickPointer) {
                float dx = x - mStickCx, dy = y - mStickCy;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                if (d > mStickR) { dx = dx / d * mStickR; dy = dy / d * mStickR; }
                mKnobX = mStickCx + dx; mKnobY = mStickCy + dy;
                continue;
            }
            for (Btn b : mBtns) {
                RectF r = b.r;
                float slack = r.width() * 0.15f;
                if (x >= r.left - slack && x <= r.right + slack && y >= r.top - slack && y <= r.bottom + slack)
                    b.down = true;
            }
        }
        send();
        invalidate();
        return true;
    }

    private void releaseAll() {
        for (Btn b : mBtns) b.down = false;
        mStickPointer = -1;
        mKnobX = mStickCx; mKnobY = mStickCy;
        send();
    }

    private void send() {
        int buttons = 0, face = 0, lt = 0, rt = 0;
        for (Btn b : mBtns) {
            if (!b.down) continue;
            buttons |= b.digital;
            if (b.face >= 0) face |= 1 << b.face;
            if (b.trigger == 1) lt = 255;
            if (b.trigger == 2) rt = 255;
        }
        int lx = 0, ly = 0;
        if (mStickR > 0) {
            float fx = (mKnobX - mStickCx) / mStickR, fy = (mStickCy - mKnobY) / mStickR;
            if (Math.abs(fx) < 0.12f) fx = 0;
            if (Math.abs(fy) < 0.12f) fy = 0;
            lx = Math.round(fx * 32767);
            ly = Math.round(fy * 32767);
        }
        if (buttons == mLastButtons && face == mLastFace && lt == mLastLt && rt == mLastRt
                && lx == mLastLx && ly == mLastLy)
            return;
        mLastButtons = buttons; mLastFace = face; mLastLt = lt; mLastRt = rt; mLastLx = lx; mLastLy = ly;
        if (DEBUG)
            android.util.Log.d("b3x-pad", "buttons=" + buttons + " face=" + face + " lt=" + lt + " rt=" + rt + " lx=" + lx + " ly=" + ly);
        B3xActivity.nativeSetPad(buttons, face, lt, rt, lx, ly);
    }
}
