package com.ryuna.b3x;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Launcher settings: kept in SharedPreferences for the UI, and written as
 * the two files native code reads at start, in the app's external files
 * directory (the process's working directory):
 *
 *   b3x_launcher.txt  KEY=VALUE environment (android_main.c loads it
 *                     before b3x_env.txt, so a dev b3x_env.txt still wins)
 *   b3x_options.txt   scale/fxaa/aniso, the same file the in-game
 *                     Gameplay rows write (recomp_manual.c)
 */
final class Settings {
    static final String K_GAME_DIR = "game_dir";
    static final String K_SCALE = "scale";            // percent: 100,150,200,250
    static final String K_FXAA = "fxaa";
    static final String K_ANISO = "aniso";            // 1,2,4,8,16
    static final String K_VK11 = "vk11";
    static final String K_FRAME_LOCK = "frame_lock";
    static final String K_FPS = "show_fps";
    static final String K_PAD = "pad";
    static final String K_PAD_AUTOHIDE = "pad_autohide";
    static final String K_PAD_SIZE = "pad_size";      // percent
    static final String K_PAD_OPACITY = "pad_opacity";// percent
    static final String K_DEBUG_LOG = "debug_log";

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

    static void writeNativeFiles(Context c) throws IOException {
        SharedPreferences p = prefs(c);
        File dir = filesDir(c);
        StringBuilder env = new StringBuilder();
        env.append("# written by the launcher; b3x_env.txt overrides\n");
        env.append("NFSU2_GAME_DIR=").append(gameDir(c)).append('\n');
        if (p.getBoolean(K_VK11, false)) env.append("RECOMP_VK_LEGACY=1\n");
        if (p.getBoolean(K_FRAME_LOCK, false)) env.append("B3_FRAME_LOCK=1\n");
        if (p.getBoolean(K_DEBUG_LOG, false)) {
            // Android/data/com.ryuna.b3x/files/b3x_log.txt: a guest snapshot
            // every 10 s and the FPS.
            env.append("B3X_LOG_FILE=1\nRECOMP_WATCHDOG_SECS=10\nRECOMP_WATCHDOG_REPEAT=1\n");
            env.append("RECOMP_FPS_LOG=2\n");
        }
        write(new File(dir, "b3x_launcher.txt"), env.toString());

        String opt = "scale=" + p.getInt(K_SCALE, 100) + "\n"
                   + "fxaa=" + (p.getBoolean(K_FXAA, false) ? 1 : 0) + "\n"
                   + "aniso=" + p.getInt(K_ANISO, 4) + "\n";
        write(new File(dir, "b3x_options.txt"), opt);
    }

    private static void write(File f, String s) throws IOException {
        try (FileOutputStream o = new FileOutputStream(f)) {
            o.write(s.getBytes(StandardCharsets.UTF_8));
        }
    }
}
