package com.ryuna.b3x;

import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.hardware.input.InputManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.InputDevice;
import android.view.ViewGroup;

import org.libsdl.app.SDLActivity;

/**
 * The game. SDLActivity creates the surface, the audio device and the
 * input plumbing, loads the native libraries and runs SDL_main
 * (src/android_main.c) on its own thread. On top: the touch pad and the
 * FPS counter (TouchPad), configured in LauncherActivity.
 */
public class B3xActivity extends SDLActivity implements InputManager.InputDeviceListener {
    static native void nativeSetPad(int buttons, int face, int lt, int rt, int lx, int ly);
    static native float nativeGetFps();

    private TouchPad mPad;
    private boolean mPadWanted, mPadAuto;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mFpsTick = new Runnable() {
        @Override public void run() {
            if (mPad != null) {
                try { mPad.setFps(nativeGetFps()); } catch (UnsatisfiedLinkError ignored) { }
            }
            mHandler.postDelayed(this, 500);
        }
    };

    @Override
    protected String[] getLibraries() {
        return new String[] { "SDL2", "main" };
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        super.onCreate(savedInstanceState);
        if (mLayout == null) return;     // SDL failed to start (shows its own dialog)
        hideSystemBars();

        SharedPreferences p = Settings.prefs(this);
        mPadWanted = p.getBoolean(Settings.K_PAD, true);
        mPadAuto = p.getBoolean(Settings.K_PAD_AUTOHIDE, true);
        mPad = new TouchPad(this, mPadWanted && !(mPadAuto && hasGamepad()),
                            p.getBoolean(Settings.K_FPS, false),
                            p.getInt(Settings.K_PAD_SIZE, 100) / 100f,
                            p.getInt(Settings.K_PAD_OPACITY, 60));
        mLayout.addView(mPad, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                                                         ViewGroup.LayoutParams.MATCH_PARENT));
        // Plugged in from the start (nothing pressed), as a pad on port 1 of
        // the Xbox would be, so the game sees player 1 before the first touch.
        if (mPadWanted) nativeSetPad(0, 0, 0, 0, 0, 0);
        InputManager im = (InputManager) getSystemService(INPUT_SERVICE);
        if (im != null) im.registerInputDeviceListener(this, mHandler);
        mHandler.post(mFpsTick);
    }

    /** Full screen: no status or navigation bar (they come back on a swipe). */
    private void hideSystemBars() {
        android.view.Window w = getWindow();
        w.setDecorFitsSystemWindows(false);
        android.view.WindowManager.LayoutParams lp = w.getAttributes();
        lp.layoutInDisplayCutoutMode =
            android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        w.setAttributes(lp);
        android.view.WindowInsetsController c = w.getInsetsController();
        if (c != null) {
            c.hide(android.view.WindowInsets.Type.systemBars());
            c.setSystemBarsBehavior(android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemBars();
    }

    /** SDL asks for an orientation from the window size and hint; the game
     *  is landscape only, whatever it asks. */
    @Override
    public void setOrientationBis(int w, int h, boolean resizable, String hint) {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
    }

    @Override
    protected void onDestroy() {
        mHandler.removeCallbacks(mFpsTick);
        InputManager im = (InputManager) getSystemService(INPUT_SERVICE);
        if (im != null) im.unregisterInputDeviceListener(this);
        boolean finishing = isFinishing();
        super.onDestroy();
        // The recompiled game cannot start twice in one process; it runs in
        // its own (":game"), which goes with the activity, so Play from the
        // launcher always gets a fresh one.
        if (finishing)
            android.os.Process.killProcess(android.os.Process.myPid());
    }

    private static boolean hasGamepad() {
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice d = InputDevice.getDevice(id);
            if (d == null || d.isVirtual()) continue;
            int s = d.getSources();
            if ((s & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                    || (s & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK)
                return true;
        }
        return false;
    }

    private void updatePad() {
        if (mPad != null)
            mPad.setPadVisible(mPadWanted && !(mPadAuto && hasGamepad()));
    }

    @Override public void onInputDeviceAdded(int id) { updatePad(); }
    @Override public void onInputDeviceRemoved(int id) { updatePad(); }
    @Override public void onInputDeviceChanged(int id) { updatePad(); }
}
