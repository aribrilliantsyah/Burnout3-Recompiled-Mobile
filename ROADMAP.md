# Roadmap

## Done (0.0.1)

- XBE lifted to C, built as `libmain.so` for arm64 Android (NDK r28c).
- Vulkan renderer with a Vulkan 1.1 path for older drivers (Mali r32p1).
- Launcher: install from ISO or pick an extracted folder; display, graphics,
  sound, touch and controller settings.
- Touch pad (Drive and Full layouts) with an editor; controller remapping;
  rumble.
- Playable on a Dimensity 920 class phone (Mali-G68) at ~60 fps.

## Next

- Test on Snapdragon 7s Gen 2 (Adreno 710, Vulkan 1.3 path).
- Confirm the random-freeze fix over long sessions.
- Physical controllers, tilt steering, FXAA on Mali.
- Release builds: signed APK, stripped `libmain.so`.

## Out of scope

- Xbox Live and System Link play.
- Redistributing anything from the game: generated source, assets, movies.
