package com.ryuna.b3x;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Start screen: game folder, graphics, controls and display settings,
 * then Play. Plain views built in code, compact, landscape.
 */
public class LauncherActivity extends Activity {
    private static final int REQ_TREE = 1;

    private static final int BG = 0xFF101114, PANEL = 0xFF17191D, LINE = 0xFF2A2D33;
    private static final int FG = 0xFFE6E6E6, DIM = 0xFF8B9099, ACCENT = 0xFFE5534B, OK = 0xFF3FB950;

    private SharedPreferences mPrefs;
    private EditText mDir;
    private TextView mDirStatus;
    private Button mPlay;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        mPrefs = Settings.prefs(this);
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
        refreshDir();              // back from the permission screen
    }

    // ---------------------------------------------------------------- UI

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                                                    getResources().getDisplayMetrics()));
    }

    private View build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(16), dp(12), dp(16), dp(12));

        // left column: title, folder, play
        LinearLayout left = column();
        TextView title = text("BURNOUT 3", 26, FG);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setLetterSpacing(0.08f);
        left.addView(title);
        left.addView(text("Takedown \u00B7 Xbox recompilation \u00B7 b3x " + versionName(), 12, DIM));
        left.addView(gap(14));

        left.addView(label("GAME FOLDER (contains default.xbe)"));
        mDir = new EditText(this);
        mDir.setSingleLine(true);
        mDir.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        mDir.setTextSize(13);
        mDir.setTextColor(FG);
        mDir.setTypeface(Typeface.MONOSPACE);
        mDir.setBackground(box(PANEL, LINE));
        mDir.setPadding(dp(8), dp(6), dp(8), dp(6));
        mDir.setText(Settings.gameDir(this));
        mDir.setOnFocusChangeListener((v, f) -> { if (!f) saveDir(mDir.getText().toString()); });
        left.addView(mDir, matchWrap());
        left.addView(gap(6));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button pick = button("Choose folder\u2026", false);
        pick.setOnClickListener(v -> pickFolder());
        Button def = button("App folder", false);
        def.setOnClickListener(v -> { mDir.setText(Settings.defaultGameDir(this)); saveDir(mDir.getText().toString()); });
        row.addView(pick, weight());
        row.addView(gap(8));
        row.addView(def, weight());
        left.addView(row, matchWrap());
        left.addView(gap(6));
        mDirStatus = text("", 12, DIM);
        left.addView(mDirStatus);

        View spacer = new View(this);
        left.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1f));

        mPlay = button("PLAY", true);
        mPlay.setTextSize(20);
        mPlay.setOnClickListener(v -> play());
        left.addView(mPlay, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
        left.addView(gap(4));
        left.addView(text("You need your own copy of the game, extracted from the disc.", 11, DIM));

        root.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        root.addView(gap(16));

        // right column: settings
        LinearLayout set = column();
        set.addView(section("GRAPHICS"));
        set.addView(choice("Resolution scale", Settings.K_SCALE,
                new String[] { "1x (640x480)", "1.5x", "2x", "2.5x" }, new int[] { 100, 150, 200, 250 }, 100));
        set.addView(choice("Anisotropic filtering", Settings.K_ANISO,
                new String[] { "Off", "2x", "4x", "8x", "16x" }, new int[] { 1, 2, 4, 8, 16 }, 4));
        set.addView(toggle("Anti-aliasing (FXAA)", Settings.K_FXAA, false));
        set.addView(toggle("Vulkan 1.1 compatibility path", Settings.K_VK11, false));
        set.addView(hint("Used automatically on drivers that need it (Mali)."));

        set.addView(section("DISPLAY"));
        set.addView(toggle("Show FPS", Settings.K_FPS, false));
        set.addView(toggle("Lock game speed to frame rate", Settings.K_FRAME_LOCK, false));
        set.addView(hint("Off: races keep real-time speed below 60 fps."));

        set.addView(section("CONTROLS"));
        set.addView(toggle("On-screen gamepad", Settings.K_PAD, true));
        set.addView(toggle("Hide it when a controller is connected", Settings.K_PAD_AUTOHIDE, true));
        set.addView(slider("Gamepad size", Settings.K_PAD_SIZE, 60, 150, 100, "%"));
        set.addView(slider("Gamepad opacity", Settings.K_PAD_OPACITY, 20, 100, 60, "%"));
        set.addView(hint("Bluetooth/USB controllers work as player 1. RT accelerates, LT brakes, A boosts."));

        ScrollView sv = new ScrollView(this);
        sv.setBackground(box(PANEL, LINE));
        sv.setPadding(dp(12), dp(4), dp(12), dp(10));
        sv.addView(set);
        root.addView(sv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.25f));
        return root;
    }

    private String versionName() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (Exception e) { return ""; }
    }

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private TextView text(String s, float sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }

    private TextView label(String s) {
        TextView t = text(s, 11, DIM);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setLetterSpacing(0.06f);
        t.setPadding(0, 0, 0, dp(4));
        return t;
    }

    private TextView section(String s) {
        TextView t = label(s);
        t.setTextColor(ACCENT);
        t.setPadding(0, dp(12), 0, dp(2));
        return t;
    }

    private TextView hint(String s) {
        TextView t = text(s, 11, DIM);
        t.setPadding(0, 0, 0, dp(4));
        return t;
    }

    private View gap(int d) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(d), dp(d)));
        return v;
    }

    private GradientDrawable box(int fill, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setStroke(dp(1), stroke);
        g.setCornerRadius(dp(4));
        return g;
    }

    private Button button(String s, boolean primary) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setTextColor(primary ? Color.WHITE : FG);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setBackground(box(primary ? ACCENT : PANEL, primary ? ACCENT : LINE));
        b.setMinHeight(dp(38));
        b.setStateListAnimator(null);
        return b;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    private LinearLayout rowWith(String name, View control) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setMinimumHeight(dp(38));
        TextView t = text(name, 14, FG);
        r.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        r.addView(control);
        return r;
    }

    private View toggle(String name, String key, boolean def) {
        Switch s = new Switch(this);
        s.setChecked(mPrefs.getBoolean(key, def));
        s.setOnCheckedChangeListener((CompoundButton v, boolean on) -> mPrefs.edit().putBoolean(key, on).apply());
        return rowWith(name, s);
    }

    private View choice(String name, String key, String[] labels, int[] values, int def) {
        Spinner sp = new Spinner(this, Spinner.MODE_DROPDOWN);
        ArrayAdapter<String> a = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, labels) {
            @Override public View getView(int pos, View cv, ViewGroup parent) {
                TextView t = (TextView) super.getView(pos, cv, parent);
                t.setTextColor(FG);
                t.setTextSize(13);
                return t;
            }
        };
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp.setAdapter(a);
        int cur = mPrefs.getInt(key, def);
        for (int i = 0; i < values.length; i++) if (values[i] == cur) sp.setSelection(i);
        sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                mPrefs.edit().putInt(key, values[pos]).apply();
            }
            @Override public void onNothingSelected(AdapterView<?> p) { }
        });
        return rowWith(name, sp);
    }

    private View slider(String name, String key, int min, int max, int def, String unit) {
        LinearLayout c = column();
        TextView t = text("", 14, FG);
        SeekBar sb = new SeekBar(this);
        sb.setMax(max - min);
        int cur = mPrefs.getInt(key, def);
        sb.setProgress(cur - min);
        t.setText(name + "  " + cur + unit);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean user) {
                t.setText(name + "  " + (p + min) + unit);
                mPrefs.edit().putInt(key, p + min).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });
        c.setPadding(0, dp(6), 0, dp(2));
        c.addView(t);
        c.addView(sb, matchWrap());
        return c;
    }

    // ---------------------------------------------------------- folder

    private void saveDir(String d) {
        d = d.trim();
        while (d.length() > 1 && d.endsWith("/")) d = d.substring(0, d.length() - 1);
        mPrefs.edit().putString(Settings.K_GAME_DIR, d).apply();
        refreshDir();
    }

    private boolean needsAllFiles(String dir) {
        String own = Settings.filesDir(this).getAbsolutePath();
        return !dir.startsWith(own) && Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager();
    }

    private void refreshDir() {
        if (mDirStatus == null) return;
        String dir = Settings.gameDir(this);
        if (!mDir.hasFocus()) mDir.setText(dir);
        String err = Settings.checkGameDir(dir);
        if (err != null && needsAllFiles(dir))
            err = "needs \u201CAll files access\u201D \u2014 tap Choose folder to grant it";
        if (err == null) {
            mDirStatus.setText("\u2713 default.xbe found");
            mDirStatus.setTextColor(OK);
        } else {
            mDirStatus.setText("\u2717 " + err);
            mDirStatus.setTextColor(ACCENT);
        }
        mPlay.setAlpha(err == null ? 1f : 0.5f);
    }

    private void pickFolder() {
        if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) {
            Toast.makeText(this, "Allow \u201CAll files access\u201D so the game can read its folder, then come back.",
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

    // ------------------------------------------------------------ play

    private void play() {
        saveDir(mDir.getText().toString());
        String dir = Settings.gameDir(this);
        String err = Settings.checkGameDir(dir);
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
