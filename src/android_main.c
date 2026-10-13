/*
 * android_main.c -- Android entry for the Burnout 3 recompilation.
 *
 * SDLActivity loads libSDL2.so and libmain.so and calls SDL_main on its
 * own thread. This file only does what a phone lacks compared to a desktop:
 *
 *   logs      stdout/stderr go to logcat (tag "b3x") through a pipe, so
 *             every printf of the runtime shows up in `adb logcat`
 *   settings  b3x_env.txt in the app's external files directory,
 *             KEY=VALUE per line, '#' comments -- an app has no shell
 *             environment; an explicit setting there wins over defaults
 *   paths     the game directory defaults to <external files>/game and the
 *             process runs from <external files> (b3x_options.txt, saves)
 *   stack     the game runs on a thread with a 16 MB stack, like a desktop
 *             main thread (recompiled code is stack hungry)
 */
#if defined(__ANDROID__)

#include <android/log.h>
#include <pthread.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include <SDL.h>
#include <SDL_main.h>

#define B3X_TAG "b3x"

int b3x_game_main(void);   /* main.c */

/* ------------------------------------------------------------------ */
/* stdout/stderr -> logcat                                            */

static int s_log_pipe[2];

static void *log_thread(void *arg)
{
    char buf[1024];
    size_t used = 0;
    (void)arg;
    for (;;) {
        ssize_t n = read(s_log_pipe[0], buf + used, sizeof(buf) - 1 - used);
        if (n <= 0)
            break;
        used += (size_t)n;
        buf[used] = 0;
        /* One logcat line per text line; a line longer than the buffer
         * goes out in pieces. */
        char *start = buf, *nl;
        while ((nl = strchr(start, '\n')) != NULL) {
            *nl = 0;
            __android_log_write(ANDROID_LOG_INFO, B3X_TAG, start);
            start = nl + 1;
        }
        used = strlen(start);
        if (used == sizeof(buf) - 1) {
            __android_log_write(ANDROID_LOG_INFO, B3X_TAG, start);
            used = 0;
        } else {
            memmove(buf, start, used);
        }
    }
    return NULL;
}

static void redirect_stdio_to_logcat(void)
{
    pthread_t th;
    setvbuf(stdout, NULL, _IOLBF, 0);
    setvbuf(stderr, NULL, _IONBF, 0);
    if (pipe(s_log_pipe) != 0)
        return;
    dup2(s_log_pipe[1], STDOUT_FILENO);
    dup2(s_log_pipe[1], STDERR_FILENO);
    if (pthread_create(&th, NULL, log_thread, NULL) == 0)
        pthread_detach(th);
}

/* ------------------------------------------------------------------ */
/* b3x_env.txt                                                        */

static void load_env_file(const char *dir)
{
    char path[512], line[512];
    FILE *f;
    snprintf(path, sizeof(path), "%s/b3x_env.txt", dir);
    f = fopen(path, "r");
    if (!f)
        return;
    while (fgets(line, sizeof(line), f)) {
        char *s = line, *eq, *end;
        while (*s == ' ' || *s == '\t') s++;
        if (*s == '#' || *s == '\n' || *s == '\r' || !*s)
            continue;
        end = s + strcspn(s, "\r\n");
        *end = 0;
        eq = strchr(s, '=');
        if (!eq || eq == s)
            continue;
        *eq = 0;
        setenv(s, eq + 1, 1);
        printf("[env] %s=%s\n", s, eq + 1);
    }
    fclose(f);
}

/* ------------------------------------------------------------------ */

static void *game_thread(void *arg)
{
    *(int *)arg = b3x_game_main();
    return NULL;
}

int SDL_main(int argc, char **argv)
{
    pthread_attr_t attr;
    pthread_t th;
    const char *ext;
    char game_dir[512];
    int rc = 1;

    (void)argc;
    (void)argv;
    redirect_stdio_to_logcat();

    ext = SDL_AndroidGetExternalStoragePath();
    if (!ext) {
        __android_log_print(ANDROID_LOG_ERROR, B3X_TAG,
                            "no external files directory: %s", SDL_GetError());
        SDL_ShowSimpleMessageBox(SDL_MESSAGEBOX_ERROR, "Burnout 3",
                                 "The app's storage is not available.", NULL);
        return 1;
    }
    printf("[android] files: %s\n", ext);
    if (chdir(ext) != 0)
        printf("[android] chdir %s failed\n", ext);
    load_env_file(ext);

    snprintf(game_dir, sizeof(game_dir), "%s/game", ext);
    setenv("NFSU2_GAME_DIR", game_dir, 0);
    /* Android maps no RAM mirrors (as on the Switch): fewer large shared
     * mappings for the low-memory killer to weigh. */
    setenv("RECOMP_NO_MIRRORS", "1", 0);

    pthread_attr_init(&attr);
    pthread_attr_setstacksize(&attr, 16u * 1024 * 1024);
    if (pthread_create(&th, &attr, game_thread, &rc) == 0)
        pthread_join(th, NULL);
    else
        fprintf(stderr, "[FATAL] cannot start the game thread\n");
    pthread_attr_destroy(&attr);
    printf("[android] game exited with %d\n", rc);
    return rc;
}

#endif /* __ANDROID__ */
