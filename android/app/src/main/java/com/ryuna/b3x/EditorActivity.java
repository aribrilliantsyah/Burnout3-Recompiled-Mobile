package com.ryuna.b3x;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * The touch-control editor from the launcher: TouchControllerView in editor
 * mode over a 4:3 frame where the game picture will be. Landscape, full
 * screen and under the cutout like the game, so the controls land in the
 * same places. DONE saves and closes, CANCEL (or back) closes.
 */
public class EditorActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.addView(new GameFrame(this), new ViewGroup.LayoutParams(-1, -1));
        root.addView(new TouchControllerView(this, this::finish), new ViewGroup.LayoutParams(-1, -1));
        setContentView(root);
        fullScreen();                     // the insets controller exists once there is a decor
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) fullScreen();
    }

    private void fullScreen() {
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

    /** Where the game picture goes: the largest 4:3 (or 16:9, or the whole
     *  screen when stretched) box, as the renderer letterboxes it. */
    private static final class GameFrame extends TextView {
        GameFrame(Activity a) {
            super(a);
            setText("GAME");
            setTextColor(0x33FFFFFF);
            setTextSize(28);
            setGravity(android.view.Gravity.CENTER);
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            super.onSizeChanged(w, h, ow, oh);
            android.content.SharedPreferences p = Settings.prefs(getContext());
            boolean stretch = p.getBoolean(Settings.K_STRETCH, false);
            float aspect = p.getBoolean(Settings.K_WIDESCREEN, false) ? 16f / 9f : 4f / 3f;
            int fw = w, fh = h;
            if (!stretch) {
                if (w > h * aspect) fw = Math.round(h * aspect); else fh = Math.round(w / aspect);
            }
            android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
            g.setColor(0xFF1B1D22);
            g.setStroke(2, 0xFF2F3238);
            final int l = (w - fw) / 2, t = (h - fh) / 2;
            setBackground(new android.graphics.drawable.InsetDrawable(g, l, t, w - fw - l, h - fh - t));
        }
    }
}
