package com.ryuna.b3x;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.provider.DocumentsContract;
import android.text.InputType;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Start screen, laid out like NFSMW-Recompiled-Mobile's SetupActivity: a
 * title, then dark cards -- Game (folder + PLAY), Display, Touch controls
 * (editor, layout, steering, tilt, vibration), Controller (mapping),
 * Graphics, Sound, Advanced (collapsed). Landscape: two columns of cards.
 */
public class LauncherActivity extends Activity {
    private static final int REQ_TREE = 1;

    private SharedPreferences mPrefs;
    private UiKit ui;
    private EditText mDir;
    private TextView mDirStatus, mPadStatus;
    private View mPlay;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        mPrefs = Settings.prefs(this);
        ui = new UiKit(this);
        setContentView(build());
        refreshDir();
        android.view.WindowInsetsController c = getWindow().getInsetsController();
        if (c != null) {
            c.hide(android.view.WindowInsets.Type.systemBars());
            c.setSystemBarsBehavior(android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshDir();               // back from the permission screen
        refreshPads();
    }

    // ---------------------------------------------------------------- layout

    private View build() {
        LinearLayout page = ui.column();
        page.setBackgroundColor(UiKit.BG);
        page.setPadding(ui.dp(14), ui.dp(10), ui.dp(14), ui.dp(10));

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.BOTTOM);
        TextView title = ui.title("Burnout 3: Takedown");
        title.setTextSize(26);
        head.addView(title);
        TextView ver = ui.summary("  b3x " + versionName() + " · Xbox static recompilation");
        ver.setPadding(0, 0, 0, ui.dp(4));
        head.addView(ver);
        page.addView(head);
        page.addView(ui.summary("You need your own copy of the game, extracted from your disc. This app contains no game data."));
        page.addView(ui.gap(10));

        LinearLayout cols = new LinearLayout(this);
        LinearLayout left = ui.column(), right = ui.column();
        cols.addView(left, new LinearLayout.LayoutParams(0, -2, 1f));
        cols.addView(ui.gap(10));
        cols.addView(right, new LinearLayout.LayoutParams(0, -2, 1f));
        page.addView(cols);

        left.addView(gameCard());
        left.addView(touchCard());
        left.addView(controllerCard());
        right.addView(displayCard());
        right.addView(graphicsCard());
        right.addView(soundCard());
        right.addView(advancedCard());

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(UiKit.BG);
        sv.setFillViewport(true);
        sv.addView(page);
        return sv;
    }

    private LinearLayout gameCard() {
        LinearLayout c = ui.card();
        c.addView(ui.section("GAME"));
        c.addView(ui.text("Game folder (contains default.xbe)", 13, UiKit.DIM));
        mDir = new EditText(this);
        mDir.setSingleLine(true);
        mDir.setEllipsize(android.text.TextUtils.TruncateAt.START);
        mDir.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        mDir.setTextSize(13);
        mDir.setTextColor(UiKit.FG);
        mDir.setTypeface(Typeface.MONOSPACE);
        mDir.setBackground(ui.box(UiKit.PANEL, UiKit.LINE, 8));
        mDir.setPadding(ui.dp(10), ui.dp(8), ui.dp(10), ui.dp(8));
        mDir.setText(Settings.gameDir(this));
        mDir.setOnFocusChangeListener((v, f) -> { if (!f) saveDir(mDir.getText().toString()); });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = ui.dp(4);
        c.addView(mDir, lp);
        mDirStatus = ui.summary("");
        mDirStatus.setPadding(0, ui.dp(4), 0, ui.dp(6));
        c.addView(mDirStatus);
        LinearLayout row = new LinearLayout(this);
        row.addView(ui.secondary("Choose folder…", v -> pickFolder()), new LinearLayout.LayoutParams(0, -2, 1f));
        row.addView(ui.gap(8));
        row.addView(ui.secondary("Use app folder", v -> {
            mDir.setText(Settings.defaultGameDir(this));
            saveDir(mDir.getText().toString());
        }), new LinearLayout.LayoutParams(0, -2, 1f));
        c.addView(row);
        mPlay = ui.primary("PLAY", v -> play());
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(-1, -2);
        pl.topMargin = ui.dp(12);
        c.addView(mPlay, pl);
        return c;
    }

    private LinearLayout touchCard() {
        LinearLayout c = ui.card();
        c.addView(ui.section("TOUCH CONTROLS"));
        c.addView(ui.summary("Move the touch buttons and change their size and opacity, for each layout. "
                + "In the game: the ⚙ next to HIDE/TOUCH opens the same editor."));
        c.addView(ui.gap(6));
        c.addView(ui.secondary("Edit touch controls", v -> startActivity(new Intent(this, EditorActivity.class))));
        c.addView(ui.toggle("On-screen controller", null, mPrefs.getBoolean(Settings.K_PAD, true),
                v -> put(Settings.K_PAD, v)));
        c.addView(ui.segmented("Layout", "Full: the original Xbox controller. Drive: steering, pedals and boost.",
                new String[] {"Full controller", "Drive"}, new int[] {Settings.MODE_FULL, Settings.MODE_STEERING},
                mPrefs.getInt(Settings.K_PAD_MODE, Settings.MODE_STEERING), v -> put(Settings.K_PAD_MODE, v)));
        c.addView(ui.segmented("Steering (Drive layout)", "Stick: analog. Arrows: two big buttons. Tilt: turn the phone like a wheel.",
                new String[] {"Stick", "Arrows", "Tilt"},
                new int[] {Settings.STEER_STICK, Settings.STEER_ARROWS, Settings.STEER_TILT},
                mPrefs.getInt(Settings.K_STEER, Settings.STEER_STICK), v -> put(Settings.K_STEER, v)));
        c.addView(ui.slider("Tilt sensitivity", "Further right, less tilt is needed to steer all the way.",
                1, 10, mPrefs.getInt(Settings.K_TILT_SENS, 5),
                s -> s + " · full lock at " + Math.round(Settings.tiltAngle(s)) + "°",
                v -> put(Settings.K_TILT_SENS, v)));
        c.addView(ui.toggle("Hide with a controller", "Touch controls go away while a physical controller is used; touch the screen to bring them back.",
                mPrefs.getBoolean(Settings.K_PAD_AUTOHIDE, true), v -> put(Settings.K_PAD_AUTOHIDE, v)));
        c.addView(ui.toggle("Button feedback", "A short tick when a touch button is pressed.",
                mPrefs.getBoolean(Settings.K_HAPTIC, true), v -> put(Settings.K_HAPTIC, v)));
        c.addView(ui.toggle("Vibration", "Crashes and takedowns rumble the controller; with none connected, the phone vibrates.",
                mPrefs.getBoolean(Settings.K_VIBRATE, true), v -> put(Settings.K_VIBRATE, v)));
        c.addView(ui.secondary("Test vibration", v -> testVibration()));
        return c;
    }

    private LinearLayout controllerCard() {
        LinearLayout c = ui.card();
        c.addView(ui.section("CONTROLLER"));
        mPadStatus = ui.summary("");
        c.addView(mPadStatus);
        c.addView(ui.gap(6));
        c.addView(ui.secondary("Controller mapping", v -> startActivity(new Intent(this, GamepadMapActivity.class))));
        return c;
    }

    private LinearLayout displayCard() {
        LinearLayout c = ui.card();
        c.addView(ui.section("DISPLAY"));
        c.addView(ui.toggle("Widescreen (16:9)", "Off: the Xbox's 4:3 picture with side bars. On: the game's own widescreen mode.",
                mPrefs.getBoolean(Settings.K_WIDESCREEN, false), v -> put(Settings.K_WIDESCREEN, v)));
        c.addView(ui.toggle("Stretch to full screen", "Fills the screen, but distorts the picture.",
                mPrefs.getBoolean(Settings.K_STRETCH, false), v -> put(Settings.K_STRETCH, v)));
        c.addView(ui.toggle("Show FPS", "Frames per second in the corner.",
                mPrefs.getBoolean(Settings.K_FPS, false), v -> put(Settings.K_FPS, v)));
        return c;
    }

    private LinearLayout graphicsCard() {
        LinearLayout c = ui.card();
        c.addView(ui.section("GRAPHICS"));
        c.addView(ui.choice("Internal resolution", "The size the game is drawn at. 1x is the Xbox's 640×480.",
                new String[] {"1x · 640×480", "1.5x", "2x", "2.5x"}, new int[] {100, 150, 200, 250},
                mPrefs.getInt(Settings.K_SCALE, 100), v -> put(Settings.K_SCALE, v)));
        c.addView(ui.choice("Anisotropic filtering", "Sharper textures seen at an angle, like the road ahead.",
                new String[] {"Off", "2x", "4x", "8x", "16x"}, new int[] {1, 2, 4, 8, 16},
                mPrefs.getInt(Settings.K_ANISO, 4), v -> put(Settings.K_ANISO, v)));
        c.addView(ui.toggle("Anti-aliasing (FXAA)", "Smooths jagged edges with a filter over the final picture.",
                mPrefs.getBoolean(Settings.K_FXAA, false), v -> put(Settings.K_FXAA, v)));
        return c;
    }

    private LinearLayout soundCard() {
        LinearLayout c = ui.card();
        c.addView(ui.section("SOUND"));
        c.addView(ui.slider("Volume", null, 0, 100, mPrefs.getInt(Settings.K_VOLUME, 100), v -> v + " %",
                v -> put(Settings.K_VOLUME, v)));
        return c;
    }

    private LinearLayout advancedCard() {
        LinearLayout c = ui.card();
        TextView head = ui.section("▸ ADVANCED");
        c.addView(head);
        LinearLayout body = ui.column();
        body.setVisibility(View.GONE);
        body.addView(ui.summary("To test what each thing costs on your phone. The defaults are the recommended ones."));
        body.addView(ui.toggle("Vulkan 1.1 compatibility path", "Render passes instead of dynamic rendering. Used automatically on drivers that need it (Mali).",
                mPrefs.getBoolean(Settings.K_VK11, false), v -> put(Settings.K_VK11, v)));
        body.addView(ui.toggle("Lock game speed to frame rate", "Off: races keep real-time speed below 60 fps. On: the original one step per frame (slow motion when the phone cannot hold 60).",
                mPrefs.getBoolean(Settings.K_FRAME_LOCK, false), v -> put(Settings.K_FRAME_LOCK, v)));
        body.addView(ui.toggle("Debug log", "Writes Android/data/com.ryuna.b3x/files/b3x_log.txt for bug reports. Slower.",
                mPrefs.getBoolean(Settings.K_DEBUG_LOG, false), v -> put(Settings.K_DEBUG_LOG, v)));
        c.addView(body);
        head.setOnClickListener(v -> {
            boolean open = body.getVisibility() != View.VISIBLE;
            body.setVisibility(open ? View.VISIBLE : View.GONE);
            head.setText(open ? "▾ ADVANCED" : "▸ ADVANCED");
        });
        return c;
    }

    // ---------------------------------------------------------------- actions

    private void put(String k, boolean v) { mPrefs.edit().putBoolean(k, v).apply(); }

    private void put(String k, int v) { mPrefs.edit().putInt(k, v).apply(); }

    private String versionName() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (Exception e) { return ""; }
    }

    private void refreshPads() {
        if (mPadStatus == null) return;
        StringBuilder sb = new StringBuilder();
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice d = InputDevice.getDevice(id);
            if (d == null || d.isVirtual()) continue;
            int s = d.getSources();
            if ((s & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                    || (s & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(d.getName());
            }
        }
        mPadStatus.setText(sb.length() == 0
                ? "No controller connected. USB and Bluetooth controllers work as player 1."
                : "Connected: " + sb + ". Works as player 1.");
    }

    private void testVibration() {
        Vibrator v;
        if (Build.VERSION.SDK_INT >= 31) {
            VibratorManager vm = (VibratorManager) getSystemService(VIBRATOR_MANAGER_SERVICE);
            v = vm == null ? null : vm.getDefaultVibrator();
        } else {
            v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        }
        if (v == null || !v.hasVibrator()) {
            Toast.makeText(this, "This phone has no vibrator.", Toast.LENGTH_SHORT).show();
            return;
        }
        v.vibrate(VibrationEffect.createWaveform(new long[] {0, 120, 80, 260}, new int[] {0, 160, 0, 255}, -1));
    }

    private void saveDir(String d) {
        d = d.trim();
        while (d.length() > 1 && d.endsWith("/")) d = d.substring(0, d.length() - 1);
        mPrefs.edit().putString(Settings.K_GAME_DIR, d).apply();
        refreshDir();
    }

    private boolean needsAllFiles(String dir) {
        String own = Settings.filesDir(this).getAbsolutePath();
        return !dir.startsWith(own) && !Environment.isExternalStorageManager();
    }

    private void refreshDir() {
        if (mDirStatus == null) return;
        String dir = Settings.gameDir(this);
        if (!mDir.hasFocus()) mDir.setText(dir);
        String err = Settings.checkGameDir(dir);
        if (err != null && needsAllFiles(dir))
            err = "needs “All files access” — tap Choose folder to grant it";
        if (err == null) {
            mDirStatus.setText("✓ default.xbe found");
            mDirStatus.setTextColor(UiKit.OK);
        } else {
            mDirStatus.setText("✗ " + err);
            mDirStatus.setTextColor(UiKit.ACCENT);
        }
        mPlay.setAlpha(err == null ? 1f : .5f);
    }

    private void pickFolder() {
        if (!Environment.isExternalStorageManager()) {
            Toast.makeText(this, "Allow “All files access” so the game can read its folder, then come back.",
                           Toast.LENGTH_LONG).show();
            try {
                startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                         Uri.parse("package:" + getPackageName())));
            } catch (Exception e) {
                startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            }
            return;
        }
        try {
            startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), REQ_TREE);
        } catch (Exception e) {
            Toast.makeText(this, "No folder picker; type the path instead.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_TREE || res != RESULT_OK || data == null || data.getData() == null) return;
        String path = treeToPath(data.getData());
        if (path == null) {
            Toast.makeText(this, "Pick a folder on the internal storage or SD card.", Toast.LENGTH_LONG).show();
            return;
        }
        mDir.setText(path);
        saveDir(path);
    }

    /** "primary:Games/Burnout3" -> /storage/emulated/0/Games/Burnout3;
     *  "1234-ABCD:x" -> /storage/1234-ABCD/x. */
    private static String treeToPath(Uri tree) {
        String id;
        try { id = DocumentsContract.getTreeDocumentId(tree); } catch (Exception e) { return null; }
        int c = id.indexOf(':');
        if (c < 0) return null;
        String vol = id.substring(0, c), rel = id.substring(c + 1);
        String base = "primary".equalsIgnoreCase(vol)
                ? Environment.getExternalStorageDirectory().getAbsolutePath()
                : "/storage/" + vol;
        return rel.isEmpty() ? base : base + "/" + rel;
    }

    private void play() {
        saveDir(mDir.getText().toString());
        String err = Settings.checkGameDir(Settings.gameDir(this));
        if (err != null) {
            Toast.makeText(this, err, Toast.LENGTH_LONG).show();
            return;
        }
        try {
            Settings.writeNativeFiles(this);
        } catch (Exception e) {
            Toast.makeText(this, "Cannot save settings: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        startActivity(new Intent(this, B3xActivity.class));
    }
}
