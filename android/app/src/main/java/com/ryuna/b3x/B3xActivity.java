package com.ryuna.b3x;

import org.libsdl.app.SDLActivity;

/**
 * The game's only activity. SDLActivity creates the surface, the audio
 * device and the input plumbing, loads the native libraries and runs
 * SDL_main (src/android_main.c) on its own thread.
 */
public class B3xActivity extends SDLActivity {
    @Override
    protected String[] getLibraries() {
        return new String[] { "SDL2", "main" };
    }
}
