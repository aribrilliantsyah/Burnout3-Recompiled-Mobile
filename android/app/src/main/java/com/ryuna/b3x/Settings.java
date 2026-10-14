package com.ryuna.b3x;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Every launcher setting, kept in SharedPreferences ("b3x"), and the files
 * native code reads at start in the app's external files directory (the
 * process's working directory):
 *
 *   b3x_launcher.txt  KEY=VALUE environment, loaded before b3x_env.txt
 *   b3x_options.txt   scale/fxaa/aniso (also written by the in-game rows)
 *   b3x_padmap.txt    the physical pad remap (RECOMP_PAD_MAP)
 *
 * Touch layout positions live in their own preferences ("b3x_touch"),
 * written by the editor (TouchControllerView).
 */
final class Settings {
    // Game
    static final String K_GAME_DIR = "game_dir";
    // Display
    static final String K_FPS = "show_fps";
    static final String K_STRETCH = "stretch";
    static final String K_WIDESCREEN = "widescreen";
    // Graphics
    static final String K_SCALE = "scale";            // percent: 100,150,200,250
    static final String K_FXAA = "fxaa";
    static final String K_ANISO = "aniso";            // 1,2,4,8,16
    static final String K_VK11 = "vk11";
    // Gameplay / performance
    static final String K_FRAME_LOCK = "frame_lock";
    // Sound
    static final String K_VOLUME = "volume";          // 0..100
    // Touch controls
    static final String K_PAD = "pad";
    static final String K_PAD_AUTOHIDE = "pad_autohide";
    static final String K_PAD_MODE = "pad_mode";      // 0 full, 1 steering
    static final String K_STEER = "steer";            // steering mode: 0 stick, 1 arrows, 2 tilt
    static final String K_TILT_SENS = "tilt_sens";    // 1..10
    static final String K_VIBRATE = "vibrate";
    static final String K_HAPTIC = "haptic";          // tick on press
    // Physical pad
    static final String K_MAP_PREFIX = "map_";        // + xbox input name
    static final String K_DEADZONE = "deadzone";      // percent
    static final String K_INVERT_LY = "invert_ly";
    static final String K_INVERT_RY = "invert_ry";
    // Advanced
    static final String K_DEBUG_LOG = "debug_log";

    static final int MODE_FULL = 0;
    static final int MODE_STEERING = 1;
    static final int STEER_STICK = 0;
    static final int STEER_ARROWS = 1;
    static final int STEER_TILT = 2;

    /** Tilt angle (degrees) for full lock at sensitivity s (1..10). */
    static float tiltAngle(int s) {
        return 45f - (Math.max(1, Math.min(10, s)) - 1) * 3.5f;   // 45 .. 13.5
    }

    /** Xbox inputs that can be remapped, in display order (native names). */
    static final String[] XBOX_INPUTS = {
        "a", "b", "x", "y", "black", "white", "lt", "rt",
        "up", "down", "left", "right", "start", "back", "lthumb", "rthumb" };
    static final String[] XBOX_LABELS = {
        "A", "B", "X", "Y", "Black", "White", "Left trigger", "Right trigger",
        "D-pad up", "D-pad down", "D-pad left", "D-pad right", "Start", "Back",
        "Left stick click", "Right stick click" };
    /** Default SDL source for each (SDL_GameControllerGetStringFor* names). */
    static final String[] SDL_DEFAULTS = {
        "a", "b", "x", "y", "leftshoulder", "rightshoulder", "lefttrigger", "righttrigger",
        "dpup", "dpdown", "dpleft", "dpright", "start", "back", "leftstick", "rightstick" };

    private Settings() { }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("b3x", Context.MODE_PRIVATE);
    }

    static File filesDir(Context c) {
        File d = c.getExternalFilesDir(null);
        return d != null ? d : c.getFilesDir();
    }

    static String defaultGameDir(Context c) {
        return new File(filesDir(c), "game").getAbsolutePath();
    }

    static String gameDir(Context c) {
        return prefs(c).getString(K_GAME_DIR, defaultGameDir(c));
    }

    /** null when the folder holds a readable default.xbe, else the reason. */
    static String checkGameDir(String dir) {
        File xbe = new File(dir, "default.xbe");
        if (!new File(dir).isDirectory()) return "folder not found";
        if (!xbe.exists()) return "default.xbe not in this folder";
        if (!xbe.canRead()) return "default.xbe not readable (storage permission)";
        return null;
    }

    static String mapping(SharedPreferences p, int i) {
        return p.getString(K_MAP_PREFIX + XBOX_INPUTS[i], SDL_DEFAULTS[i]);
    }

    static void writeNativeFiles(Context c) throws IOException {
        SharedPreferences p = prefs(c);
        File dir = filesDir(c);

        StringBuilder env = new StringBuilder();
        env.append("# written by the launcher; b3x_env.txt overrides\n");
        env.append("NFSU2_GAME_DIR=").append(gameDir(c)).append('\n');
        env.append("RECOMP_WIDESCREEN=").append(p.getBoolean(K_WIDESCREEN, false) ? 1 : 0).append('\n');
        if (p.getBoolean(K_STRETCH, false)) env.append("RECOMP_STRETCH=1\n");
        if (p.getBoolean(K_VK11, false)) env.append("RECOMP_VK_LEGACY=1\n");
        if (p.getBoolean(K_FRAME_LOCK, false)) env.append("B3_FRAME_LOCK=1\n");
        env.append("RECOMP_AUDIO_VOLUME=").append(p.getInt(K_VOLUME, 100)).append('\n');
        env.append("RECOMP_PAD_MAP=").append(new File(dir, "b3x_padmap.txt").getAbsolutePath()).append('\n');
        if (p.getBoolean(K_DEBUG_LOG, false)) {
            // Android/data/com.ryuna.b3x/files/b3x_log.txt: a guest snapshot
            // every 10 s and the FPS every second.
            env.append("B3X_LOG_FILE=1\nRECOMP_WATCHDOG_SECS=10\nRECOMP_WATCHDOG_REPEAT=1\n");
            env.append("RECOMP_FPS_LOG=2\n");
        }
        write(new File(dir, "b3x_launcher.txt"), env.toString());

        String opt = "scale=" + p.getInt(K_SCALE, 100) + "\n"
                   + "fxaa=" + (p.getBoolean(K_FXAA, false) ? 1 : 0) + "\n"
                   + "aniso=" + p.getInt(K_ANISO, 4) + "\n";
        write(new File(dir, "b3x_options.txt"), opt);

        StringBuilder map = new StringBuilder("# xbox=sdl, written by the launcher\n");
        for (int i = 0; i < XBOX_INPUTS.length; i++)
            map.append(XBOX_INPUTS[i]).append('=').append(mapping(p, i)).append('\n');
        map.append("deadzone=").append(p.getInt(K_DEADZONE, 8)).append('\n');
        map.append("invert_ly=").append(p.getBoolean(K_INVERT_LY, false) ? 1 : 0).append('\n');
        map.append("invert_ry=").append(p.getBoolean(K_INVERT_RY, false) ? 1 : 0).append('\n');
        write(new File(dir, "b3x_padmap.txt"), map.toString());
    }

    private static void write(File f, String s) throws IOException {
        try (FileOutputStream o = new FileOutputStream(f)) {
            o.write(s.getBytes(StandardCharsets.UTF_8));
        }
    }
}
