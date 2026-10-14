package com.ryuna.b3x;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

import org.libsdl.app.SDLActivity;

/**
 * The game. SDLActivity creates the surface, the audio device and the
 * input plumbing, loads the native libraries and runs SDL_main
 * (src/android_main.c) on its own thread. On top: TouchControllerView
 * (touch pad, its in-game editor, the FPS counter). Runs in its own
 * process (":game"), which ends with it.
 */
public class B3xActivity extends SDLActivity {
    static native void nativeSetPad(int buttons, int face, int lt, int rt, int lx, int ly);
    static native void nativeSetPadRight(int rx, int ry);
    static native float nativeGetFps();

    private static B3xActivity sSelf;
    private static boolean sVibrate;
    private TouchControllerView mPad;
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
        sSelf = this;
        hideSystemBars();
        SharedPreferences p = Settings.prefs(this);
        sVibrate = p.getBoolean(Settings.K_VIBRATE, true);
        mPad = new TouchControllerView(this);
        mPad.onMenu = this::showMenu;
        mLayout.addView(mPad, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                                                         ViewGroup.LayoutParams.MATCH_PARENT));
        // Plugged in from the start (nothing pressed), as a pad on port 1 of
        // the Xbox would be, so the game sees player 1 before the first touch.
        nativeSetPad(0, 0, 0, 0, 0, 0);
        mHandler.post(mFpsTick);
    }

    /** Full screen under the cutout; bars come back on a swipe. */
    private void hideSystemBars() {
        Window w = getWindow();
        w.setDecorFitsSystemWindows(false);
        WindowManager.LayoutParams lp = w.getAttributes();
        lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        w.setAttributes(lp);
        WindowInsetsController c = w.getDecorView().getWindowInsetsController();
        if (c != null) {
            c.hide(WindowInsets.Type.systemBars());
            c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
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
    protected void onPause() {
        if (mPad != null) mPad.onPauseResume(false);
        rumbleNow(0, 0);
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mPad != null) mPad.onPauseResume(true);
    }

    /** The hamburger: resume, layout editor, or leave for the launcher. */
    private void showMenu() {
        String[] items = { "Resume", "Edit touch layout", "Exit to launcher" };
        android.app.AlertDialog d = new android.app.AlertDialog.Builder(this,
                android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Burnout 3")
            .setItems(items, (dlg, which) -> {
                if (which == 1 && mPad != null) mPad.openEditor();
                else if (which == 2) exitToLauncher();
            })
            .create();
        d.setOnDismissListener(x -> hideSystemBars());
        d.show();
    }

    private void exitToLauncher() {
        Intent i = new Intent(this, LauncherActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(i);
        finish();          // onDestroy ends the :game process
    }

    @Override
    public void onBackPressed() {
        if (mPad != null && mPad.onBack()) return;
        super.onBackPressed();
    }

    // A physical pad in use hides the touch overlay.
    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (mPad != null && isPad(e.getSource())) mPad.onPhysicalInput();
        return super.dispatchKeyEvent(e);
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent e) {
        if (mPad != null && isPad(e.getSource())) mPad.onPhysicalInput();
        return super.dispatchGenericMotionEvent(e);
    }

    private static boolean isPad(int src) {
        return (src & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
            || (src & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
    }

    @Override
    protected void onDestroy() {
        mHandler.removeCallbacks(mFpsTick);
        rumbleNow(0, 0);
        sSelf = null;
        boolean finishing = isFinishing();
        super.onDestroy();
        // The recompiled game cannot start twice in one process; it runs in
        // its own (":game"), which goes with the activity, so Play from the
        // launcher always gets a fresh one.
        if (finishing)
            android.os.Process.killProcess(android.os.Process.myPid());
    }

    // ------------------------------------------------------------- rumble

    /** From native (android_main.c), the game thread: the game's rumble,
     *  0..65535 per motor, when no physical pad takes it. */
    @SuppressWarnings("unused")
    static void rumble(int left, int right) {
        if (!sVibrate) return;
        B3xActivity a = sSelf;
        if (a != null) a.rumbleNow(left, right);
    }

    private void rumbleNow(int left, int right) {
        Vibrator v;
        if (Build.VERSION.SDK_INT >= 31) {
            VibratorManager vm = (VibratorManager) getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            v = vm == null ? null : vm.getDefaultVibrator();
        } else {
            v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        }
        if (v == null || !v.hasVibrator()) return;
        int amp = Math.max(left, right) >> 8;          // 0..255
        if (amp < 8) { v.cancel(); return; }
        // A second at that strength; the game sends a new level when it
        // changes, and zero to stop.
        v.vibrate(VibrationEffect.createOneShot(1000, Math.min(255, Math.max(1, amp))));
    }
}
