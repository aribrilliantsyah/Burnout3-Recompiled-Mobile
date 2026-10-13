# Roadmap

## Now: bootstrap (docs/superpowers/plans/2026-10-13-android-port-bootstrap.md)

- Lift the XBE from the user's disc, Linux baseline run.
- Android dependencies (SDL2, glslang) built with the NDK.
- Runtime and lifted game as `libmain.so` on bionic (16 KB pages included).
- Gradle app `com.ryuna.b3x` on SDLActivity.
- `scripts/android/b3x.sh`: build, install, run, log on the phone.
- Title screen on Snapdragon 7s Gen 2 and Dimensity 920.

## Next

- **First-run menu**: pick the ISO or the extracted folder (Storage Access
  Framework), check `default.xbe` (title ID `0x4541005B`), extract into app
  storage. The APK never carries game data.
- **Touch controls** overlay (gamepads work through SDL first).
- **Performance** on the target phones: thread placement on the big cores,
  `-O2`/LTO for the lifted code, pipeline cache.
- **Driver issues** on Mali (Dimensity 920) as they show up.

## Out of scope

- Xbox Live and System Link play.
- Redistributing anything from the game: generated source, assets, movies.
