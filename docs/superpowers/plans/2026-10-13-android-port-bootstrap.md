# Burnout 3 Android Port — Bootstrap Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the burnout3-sw Switch tree into a fresh Android project (com.ryuna.b3x) whose debug APK, built and installed by one script, boots Burnout 3 to the title screen on a Snapdragon 7s Gen 2 and a Dimensity 920 phone.

**Architecture:** Keep the existing static recompilation (lifted C in `.work/gen/` + vendored xboxrecomp runtime) and the Vulkan renderer (nv2a_vk). Add an Android branch beside the Linux one: bionic fixes in the runtime, SDL2 for window/audio/input, a thin Gradle app that loads `libmain.so` through SDLActivity. The Linux build stays the dev host for debugging; Switch-only code moves to `references/switch/`.

**Tech Stack:** C11, CMake 3.22+, Android NDK r29 (29.0.14206865, arm64-v8a, API 30), SDL 2.32.x (source build), glslang (source build, no optimizer), Vulkan 1.1+, Gradle (AGP) for packaging, adb, Python 3 + capstone for the lifter, REA MCP tools for analysis and device inspection.

**Spec:** No spec file — this plan argues from the design agreed in chat on 2026-10-13, restated here:
- New git history (old `.git` kept as `../burnout3-sw.git-backup`), branch `main`, no remote yet.
- Switch-only files go to `references/switch/` (same relative layout); Linux build keeps working.
- `roms/` is ignored: dev-only ISO/extracted disc for reverse engineering. The APK never carries game data; the user supplies the disc (startup picker = later plan).
- Android package `com.ryuna.b3x`, app name "Burnout 3 (b3x)", Vulkan only.
- Target devices: Snapdragon 7s Gen 2 (Adreno 710) and Dimensity 920 (Mali-G68 MC4).
- A dedicated script builds and installs to the connected device.
- The disc is dissected with `tools.xiso` and REA (Reverse Engineer Anything).

## Global Constraints

- Package / applicationId: `com.ryuna.b3x`; native library name `main` (SDL's default `libmain.so`).
- ABI `arm64-v8a` only; `minSdk 30`, `targetSdk 36`, `compileSdk 36`; NDK `29.0.14206865`.
- SDK root: `$ANDROID_HOME` (= `/home/ordinary/Documents/Repos/Android/SDK` on this machine); never hard-code it in committed files except as a fallback default in scripts.
- Never commit: `roms/`, `.work/` (gen, deps, venv), `build/`, `*.apk`, `*.aab`, `android/.gradle/`, `android/local.properties`, game data of any kind.
- Generated C lives in `.work/gen/`; extracted disc in `roms/game/`; ISO in `roms/`.
- Renderer: Vulkan (`NFSU2_VULKAN=ON`) on Android; the GL backend is not built there.
- Every task ends with a commit (user rule: no uncommitted work). No push until the user gives a remote.
- License: keep MIT `LICENSE`, `xboxrecomp/LICENSE`, `xboxrecomp/NOTICE`, `xboxrecomp/LICENSES/`.
- Host has 13 GB RAM / 4 cores: lifted-code builds use `JOBS=3` by default.

## Review Focus

1. **No game data on the device** — app started before `push-game`: expect an SDL message box "Game data not found in <path>" and a clean exit, not a native crash. Test in Task 7 (Linux equivalent) and Task 9 (device).
2. **16 KB page kernels** (Android 15+ devices may run 16 KB pages): `xbox_memory_layout.c` must not assume 4 KB; `libmain.so` must be linked with `-Wl,-z,max-page-size=16384`. Test in Task 7 (`llvm-readelf -l` LOAD alignment 0x4000).
3. **App sent to background and resumed** — Vulkan surface is destroyed; expect the game to pause and recreate the swapchain, not crash. Test in Task 10 (`adb shell input keyevent HOME`, then relaunch).
4. **No physical gamepad connected** — expect the game to run and show the title (touch controls are a later plan); log line `[input] no controller`. Test in Task 10.
5. **Mali shader/driver failures** (Dimensity 920) — a pipeline that fails to compile must log `[vk] pipeline failed` with the shader hash and skip the draw, not abort. Test in Task 10 on the Dimensity device.

---

### Task 1: Fresh repository

**Files:**
- Modify: `.gitignore`
- Move: `.git` → `../burnout3-sw.git-backup`

**Interfaces:** Produces the `main` branch every later task commits to.

- [ ] **Step 1:** `mv .git ../burnout3-sw.git-backup` and confirm `git -C ../burnout3-sw.git-backup log --oneline -1` (via `git --git-dir`) still prints `0bf84b5`.
- [ ] **Step 2:** `git init -b main`.
- [ ] **Step 3:** Commit the tree exactly as inherited, excluding `roms/` and `docs/superpowers/`: message `Import burnout3-sw switch v0.2 (0bf84b5) as base`.
- [ ] **Step 4:** Append to `.gitignore`: `roms/`, `.work/`, `build/` (already), `*.apk`, `*.aab`, `*.crdownload`, `android/.gradle/`, `android/local.properties`, `android/app/build/`, `android/build/`, `.cxx/`.
- [ ] **Step 5:** Verify: `git check-ignore -v roms/x .work/gen/a.c x.apk` prints three matches; `git status --short` shows only `.gitignore` and `docs/superpowers/`.
- [ ] **Step 6:** Commit `.gitignore` + this plan: `Ignore roms/, work dirs and Android outputs; add bootstrap plan`.

### Task 2: Move Switch-only code to references/switch

**Files:**
- Move (git mv, same relative path under `references/switch/`): `switch/build.sh`, `src/switch_nx.c`, `src/nfsu2_logo.h`, `assets/nfsu2_logo.png`, `tools/make_logo.py`, `tools/prof_report.py`, `xboxrecomp/src/input/xinput_nx.c`, `xboxrecomp/src/input/xinput_nx.h`, `xboxrecomp/src/nv2a_vk/switch_egl_stubs.c`, `xboxrecomp/src/nv2a_vk/switch_elf_stubs.c`, `xboxrecomp/src/d3d/d3d8_null.c`, `CHANGELOG.md`, `ROADMAP.md`
- Create: `references/switch/README.md` (one paragraph: what these files were, source commit `0bf84b5`)
- Modify: `CMakeLists.txt` (drop `src/switch_nx.c` from `GAME_SOURCES`, delete the `if(NINTENDO_SWITCH)` block at the end and the `NINTENDO_SWITCH` arm of `NFSU2_GEN_OPT`), `xboxrecomp/CMakeLists.txt:46-56`, `xboxrecomp/src/d3d/CMakeLists.txt:20-24`, `xboxrecomp/src/input/CMakeLists.txt:6-9`, `xboxrecomp/src/kernel/CMakeLists.txt:58-63`, `xboxrecomp/src/nv2a_gl/CMakeLists.txt:16-23`, `xboxrecomp/src/nv2a_vk/CMakeLists.txt:18-28`
- Modify: `src/main.c` (remove `switch_boot/switch_shutdown/switch_error_dialog` prototypes, the `#ifdef __SWITCH__` `main()`, the `__SWITCH__` arm of `fatal()`), `xboxrecomp/src/input/xinput_device.c:282-300,376-381` (remove the `xinput_nx` arms)
- Leave: the other `#if defined(__SWITCH__)` arms inside the runtime (dead code on Android, kept as reference for Horizon-specific choices).

**Interfaces:** Produces a tree where `grep -rn NINTENDO_SWITCH --include=CMakeLists.txt . | grep -v references/` is empty.

- [ ] **Step 1:** `git mv` every file above.
- [ ] **Step 2:** Apply the CMake and C edits.
- [ ] **Step 3:** Verify configure: `cmake -S . -B build/linux-vk -G Ninja -DNFSU2_VULKAN=ON -DNFSU2_GEN_DIR=$PWD/.work/gen` exits 0 (gen may be empty here).
- [ ] **Step 4:** Verify the runtime compiles: `cmake --build build/linux-vk --target xbox_kernel xbox_nv2a_vk xbox_input xbox_apu xbox_usb -j4` exits 0.
- [ ] **Step 5:** Verify the grep in Interfaces is empty and `grep -rn "switch_nx\|xinput_nx\|nfsu2_logo" --exclude-dir=references --exclude-dir=.git .` only hits `CLAUDE.md`.
- [ ] **Step 6:** Commit: `Move Switch-only files to references/switch`.

### Task 3: Project docs for the Android port

**Files:**
- Rewrite: `README.md`
- Create: `CHANGELOG.md` (`## [Unreleased]` — "Android port started from burnout3-sw switch v0.2"), `ROADMAP.md`
- Modify: `CLAUDE.md` (header paragraph and Layout table: new repo, Android paths `.work/gen`, `roms/game`, `build/linux-vk`, `build/android`, `scripts/android/b3x.sh`; keep every Finding; mark Switch status as historical)

**Interfaces:** none.

- [ ] **Step 1:** README sections, in order: title "Burnout 3: Takedown — Xbox static recompilation for Android" with badges (Android arm64, Vulkan, version 0.0-dev); status "early bring-up, not playable yet"; "No game data included" box (user dumps/downloads own disc; startup picker planned); target devices table (Snapdragon 7s Gen 2 / Adreno 710, Dimensity 920 / Mali-G68 MC4, Android 11+); Building (Linux dev host, Android via `scripts/android/b3x.sh all`); Configuration (keep the env-var tables, drop the Switch table, add "on Android: `files/b3x_env.txt` in the app's external files dir"); Project layout; Credits (sp00nznet: burnout3 + xboxrecomp, MIT; antoxa2584x: Switch port); Licence.
- [ ] **Step 2:** ROADMAP: Now = this plan's milestones (Tasks 4-10); Next = startup menu (pick ISO or folder via SAF, verify `default.xbe` title id `0x4541005B`, extract into app storage), touch controls overlay, performance tuning; Out of scope = online play, redistributing game data.
- [ ] **Step 3:** Verify: `grep -n "sdmc:\|\.nro" README.md` is empty; every relative link in README resolves (`ls` each).
- [ ] **Step 4:** Commit: `Docs: Android port README, roadmap, changelog`.

### Task 4: Dissect the disc and regenerate the lifted C (REA + xiso)

**Files:**
- Create: `tools/requirements.txt` (`capstone>=5`), `docs/research/android-re.md`
- Modify: `tools/regen.sh` (defaults: `B3_XBE=$HERE/roms/game/default.xbe`, `B3_GEN_DIR=$HERE/.work/gen`)

**Interfaces:**
- Produces: `roms/game/` (extracted disc), `.work/gen/*.c` + `recomp_types.h`, `.work/venv/` (python with capstone). Later tasks pass `NFSU2_GEN_DIR=$PWD/.work/gen`.

- [ ] **Step 1:** `python3 -m venv .work/venv && .work/venv/bin/pip install -r tools/requirements.txt`.
- [ ] **Step 2:** Unpack: `cd xboxrecomp && ../.work/venv/bin/python -m tools.xiso unpack "../roms/Burnout 3 - Takedown (USA).iso" -o ../roms/game`. Verify `roms/game/default.xbe`, `Data/`, `Tracks/`, `pveh/`, `sound/`, `ovid/` exist.
- [ ] **Step 3:** Identity: `python -m tools.xbe_parser ../roms/game/default.xbe --json ../roms/game/default_analysis.json` and confirm title id `0x4541005B`, XDK 5849; record sha256 of `default.xbe` in `docs/research/android-re.md`.
- [ ] **Step 4:** REA pass on `roms/game/default.xbe`: `mcp__rea__open_binary` (absolute path), then `binary_overview`, `search_strings` for `D3D`, `DSOUND`, `XPP`, `RenderWare`. If no REA provider accepts XBE, record that fact and the provider message in `android-re.md` and fall back to `tools.xbe_parser` + `tools.disasm` output for the same facts. Write to `android-re.md`: section table, library versions, counts of functions, the hook addresses listed in `CLAUDE.md` "Library hooks" confirmed present (memmove 0x2442A0, _ftol2 0x244DF0, device 0x35D6A0).
- [ ] **Step 5:** Regenerate: `PATH=$PWD/.work/venv/bin:$PATH tools/regen.sh`. Verify `ls .work/gen/*.c | wc -l` > 0 and `.work/gen/recomp_types.h` exists.
- [ ] **Step 6:** Verify nothing from `roms/` or `.work/` is staged (`git status --short` lists only the three files above).
- [ ] **Step 7:** Commit: `RE: disc identity and REA notes; regen defaults to roms/ and .work/`.

### Task 5: Linux baseline run

**Files:** none (verification task; fix only what blocks the run, in the file that breaks).

**Interfaces:** Consumes `.work/gen`, `roms/game`. Produces `build/linux-vk/b3_recomp` — the reference behaviour Android is compared against.

- [ ] **Step 1:** `cmake --build build/linux-vk -j3` exits 0.
- [ ] **Step 2:** Run 90 s headless with frame dumps: `NFSU2_GAME_DIR=$PWD/roms/game RECOMP_VK_HEADLESS=1 RECOMP_GL_DUMP=.work/run/f,120 timeout -s KILL 90 build/linux-vk/b3_recomp > .work/run/linux.log 2>&1`.
- [ ] **Step 3:** Verify the log has `=== Burnout 3: Takedown (Xbox)` and no `[FATAL]`; view the last dumped BMP (vision tool) and confirm the EA/Criterion intro or title is drawn.
- [ ] **Step 4:** Missing-data check (Review Focus 1, host side): `NFSU2_GAME_DIR=/nonexistent build/linux-vk/b3_recomp` prints `[FATAL]` naming the path and exits non-zero within 5 s.
- [ ] **Step 5:** Commit any fix needed (if none, no commit; note result in `docs/research/android-re.md` and commit that: `Linux baseline verified`).

### Task 6: Android native dependencies

**Files:**
- Create: `scripts/android/deps.sh`

**Interfaces:**
- Produces: `.work/android-deps/arm64-v8a/` containing `lib/libSDL2.so`, `include/SDL2/`, `lib/libglslang.a` (+ `libglslang-default-resource-limits.a`, `libSPIRV.a` as the source build names them), `lib/cmake/glslang/` config; and `.work/android-deps/SDL2-src/android-project/app/src/main/java/org/libsdl/app/*.java` (copied by Task 8).
- Script interface: `scripts/android/deps.sh [--clean]`, env `ANDROID_HOME`, `NDK_VERSION` (default `29.0.14206865`), `SDL_VERSION` (default `2.32.10`), `GLSLANG_TAG` (default `15.4.0`).

- [ ] **Step 1:** Write the script: download SDL2 and glslang source tarballs into `.work/android-deps/src/` (skip if present), configure each with `$NDK/build/cmake/android.toolchain.cmake -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-30 -DCMAKE_BUILD_TYPE=Release`, install to the prefix. glslang with `-DENABLE_OPT=OFF -DENABLE_GLSLANG_BINARIES=OFF -DGLSLANG_TESTS=OFF`. `set -euo pipefail`; idempotent.
- [ ] **Step 2:** Run it. Verify `file .work/android-deps/arm64-v8a/lib/libSDL2.so` says `ARM aarch64`, and the glslang config file exists.
- [ ] **Step 3:** Commit: `Android: script to build SDL2 and glslang for arm64-v8a`.

### Task 7: Runtime on bionic (libmain.so)

**Files:**
- Modify: `CMakeLists.txt` (when `ANDROID`: `add_library(main SHARED ...)` instead of the executable, `OUTPUT_NAME main`, link `SDL2::SDL2 vulkan log android m`, `-Wl,-z,max-page-size=16384`, `NFSU2_GEN_OPT -O2`)
- Modify: `xboxrecomp/src/kernel/CMakeLists.txt` (skip OpenSSL when `ANDROID`, as the Switch did), `xboxrecomp/src/nv2a_vk/CMakeLists.txt` (`ANDROID` arm: `find_package(SDL2 CONFIG)`, `Vulkan` from the NDK sysroot, no SPIRV-Tools), `xboxrecomp/src/apu/CMakeLists.txt`, `xboxrecomp/src/input/CMakeLists.txt` (SDL2 via config package on Android)
- Modify: `xboxrecomp/src/platform/win32_compat.c` — `TerminateThread` (bionic has no `pthread_cancel`: mark the object exited and `pthread_kill(thread, SIGUSR2)` whose handler calls `pthread_exit(NULL)`), `xbox_guest_pin` area line ~885 (`sched_setaffinity(0, ...)` instead of `pthread_setaffinity_np` under `__ANDROID__`)
- Modify: `xboxrecomp/src/kernel/xbox_memory_layout.c` — keep `sysconf(_SC_PAGESIZE)`; any hard 4 KB assumption found while building is replaced by it
- Create: `src/android_main.c` — `int SDL_main(int argc, char **argv)`: redirect stdout/stderr to logcat (tag `b3x`) through a pipe thread; read `b3x_env.txt` from `SDL_AndroidGetExternalStoragePath()` (`KEY=VALUE`, `#` comments, `setenv(k, v, 0)`); `setenv("NFSU2_GAME_DIR", "<ext>/game", 0)`; `setenv("RECOMP_NO_MIRRORS","1",0)`; start `game_main` on a pthread with a 16 MB stack and join it.
- Modify: `src/main.c` — make `game_main` non-static and named `b3x_game_main(void)`; the desktop `main()` calls it; under `__ANDROID__` the desktop `main()` is not compiled; `fatal()` keeps the SDL message box arm.
- Create: `scripts/android/test-runtime.sh` — builds `xboxrecomp/tests/memory_layout_posix` and `kernel_memory_posix` with the NDK toolchain, `adb push` to `/data/local/tmp/b3x-tests/`, runs them, exits with their status.

**Interfaces:**
- Consumes: deps prefix from Task 6, `.work/gen`.
- Produces: `build/android/libmain.so`; `int b3x_game_main(void)` in `src/main.c` (Task 8's Java side never calls it directly — SDLActivity calls `SDL_main`).

- [ ] **Step 1:** Host regression first: build and run the existing POSIX tests on Linux (`cmake -S xboxrecomp/tests/memory_layout_posix -B build/t-ml && cmake --build build/t-ml && build/t-ml/*test*`; same for `kernel_memory_posix`). Expected: PASS before any edit.
- [ ] **Step 2:** Configure Android: `cmake -S . -B build/android -G Ninja -DCMAKE_TOOLCHAIN_FILE=$ANDROID_HOME/ndk/29.0.14206865/build/cmake/android.toolchain.cmake -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-30 -DNFSU2_VULKAN=ON -DNFSU2_GEN_DIR=$PWD/.work/gen -DCMAKE_PREFIX_PATH=$PWD/.work/android-deps/arm64-v8a`. Expected first failure: missing Android arms; make the CMake edits until configure passes.
- [ ] **Step 3:** `cmake --build build/android -j3`; fix each bionic compile/link error in the file named, using the decisions above. Expected end state: `build/android/libmain.so` exists.
- [ ] **Step 4:** Verify linkage: `llvm-readelf -d build/android/libmain.so | grep NEEDED` lists only `libSDL2.so libvulkan.so liblog.so libandroid.so libm.so libdl.so libc.so` (+ `libc++_shared.so` if glslang needs it — then Task 8 packages it). `llvm-readelf -l` shows every LOAD `Align 0x4000` (Review Focus 2).
- [ ] **Step 5:** Host regression: Task 5 Step 2-4 still pass on Linux.
- [ ] **Step 6:** Write `scripts/android/test-runtime.sh`; with a device attached, run it. Expected: both tests PASS on the device (proves guest memory mapping works under the phone's kernel and page size). Without a device the script exits 2 with `no adb device`.
- [ ] **Step 7:** Commit: `Android: build the runtime and lifted game as libmain.so`.

### Task 8: Gradle app com.ryuna.b3x

**Files:**
- Create: `android/settings.gradle.kts`, `android/build.gradle.kts`, `android/gradle.properties`, `android/gradlew`, `android/gradlew.bat`, `android/gradle/wrapper/gradle-wrapper.{jar,properties}`
- Create: `android/app/build.gradle.kts` (namespace/applicationId `com.ryuna.b3x`, versionName `0.0.1`, minSdk 30, target/compile 36, `ndk.abiFilters += "arm64-v8a"`, `jniLibs` from `build/android` copy step, no externalNativeBuild — the native build is the script's job)
- Create: `android/app/src/main/AndroidManifest.xml` (activity `.B3xActivity`, `screenOrientation="sensorLandscape"`, `configChanges` per SDL template, `android:hasFragileUserData="true"`, `uses-feature android.hardware.vulkan.version 0x401000 required=true`, `android.hardware.gamepad required=false`, `android.hardware.touchscreen required=false`)
- Create: `android/app/src/main/java/com/ryuna/b3x/B3xActivity.java` (`extends SDLActivity`, `getLibraries()` returns `{"SDL2", "main"}`)
- Copy: SDL's `org/libsdl/app/*.java` into `android/app/src/main/java/org/libsdl/app/` (zlib licence header kept)
- Create: `android/app/src/main/res/` launcher icon from `assets/icon.jpg` (mipmap PNGs), `values/strings.xml` (`app_name` = `Burnout 3 (b3x)`)

**Interfaces:**
- Consumes: `build/android/libmain.so`, `.work/android-deps/arm64-v8a/lib/libSDL2.so` (copied to `android/app/src/main/jniLibs/arm64-v8a/` by the Task 9 script, which is ignored by git: add `android/app/src/main/jniLibs/` to `.gitignore`).
- Produces: `android/app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 1:** Generate the wrapper with a pinned Gradle (`9.x` matching the AGP chosen) downloaded once into `.work/gradle/`; commit only the wrapper files.
- [ ] **Step 2:** Copy the two `.so` files into `jniLibs/arm64-v8a/` by hand for now; `cd android && ./gradlew assembleDebug`.
- [ ] **Step 3:** Verify: `$ANDROID_HOME/build-tools/36.1.0/aapt2 dump badging app-debug.apk` shows `package: name='com.ryuna.b3x'`, `native-code: 'arm64-v8a'`, `minSdkVersion:'30'`; `unzip -l` shows `lib/arm64-v8a/libmain.so` and `libSDL2.so` and no file from `roms/`.
- [ ] **Step 4:** Commit: `Android: Gradle app com.ryuna.b3x on SDLActivity`.

### Task 9: One script to build and install on the device

**Files:**
- Create: `scripts/android/b3x.sh`

**Interfaces:**
- Consumes: `deps.sh` (Task 6), the CMake configure line (Task 7 Step 2), Gradle (Task 8).
- Command line: `scripts/android/b3x.sh [-s SERIAL] [--release] <cmd>...` with commands, run in the order given:
  - `deps` — run `deps.sh` if the prefix is missing
  - `native` — configure (once) + build `build/android` with `JOBS` (default 3), copy `.so` files to `jniLibs`
  - `apk` — `./gradlew assembleDebug` (or `assembleRelease` with `--release`)
  - `install` — `adb install -r` the APK
  - `push-game` — `adb push roms/game/. /sdcard/Android/data/com.ryuna.b3x/files/game/` (skip files whose size already matches; dev-only convenience until the startup picker exists)
  - `env` — push `b3x_env.txt` from the repo root if present
  - `run` — `adb shell am start -n com.ryuna.b3x/.B3xActivity`
  - `log` — `adb logcat -v time b3x:V SDL:V DEBUG:V AndroidRuntime:E *:S` (until Ctrl-C)
  - `stop` — `adb shell am force-stop com.ryuna.b3x`
  - `all` = `deps native apk install run log`
- Device choice: `-s` wins; else `$ANDROID_SERIAL`; else the only device from `adb devices`; more than one → exit 2 listing them. No device → exit 2 `no adb device`.
- Each command prints `[b3x] <cmd>: ok` or exits non-zero with the failing step.

- [ ] **Step 1:** Write the script (`set -euo pipefail`, `ANDROID_HOME` fallback `/home/ordinary/Documents/Repos/Android/SDK` only if unset and present).
- [ ] **Step 2:** Verify offline parts: `scripts/android/b3x.sh native apk` from a clean `build/android` produces the APK; `scripts/android/b3x.sh install` with no device exits 2 with `no adb device`.
- [ ] **Step 3:** With a device: `scripts/android/b3x.sh stop install run` before `push-game` → app shows the "Game data not found" message box (Review Focus 1); `adb logcat` shows the `[FATAL]` line and no tombstone (`mcp__rea__read_adb_logcat` buffer `crash` is empty for the package).
- [ ] **Step 4:** `scripts/android/b3x.sh push-game run log` → logcat shows `=== Burnout 3: Takedown (Xbox)`.
- [ ] **Step 5:** Commit: `Android: b3x.sh build/install/run script`.

### Task 10: Device bring-up to the title screen

**Files:** whatever the failures point at (runtime, `nv2a_vk.c`, `android_main.c`); each fix is its own commit with the device and symptom in the message. Findings appended to `CLAUDE.md` "Findings" in the existing style.

**Interfaces:** Consumes everything above. Produces the acceptance evidence in `docs/research/android-devices.md`.

- [ ] **Step 1:** Identify each device with REA: `mcp__rea__list_adb_devices`, `mcp__rea__inspect_adb_device` (record model, SoC, Android version, ABI, page size from `adb shell getconf PAGESIZE`, Vulkan driver from `adb shell dumpsys gpu` / `vulkaninfo` if present) into `android-devices.md`.
- [ ] **Step 2:** Snapdragon 7s Gen 2 first: `b3x.sh push-game run`, then loop: read `mcp__rea__read_adb_logcat` (main + crash buffers) → fix → `b3x.sh native apk install run`. Acceptance: `adb exec-out screencap -p > .work/run/sd7s2_title.png` shows "Press START"; EA and Criterion intro movies played with audio.
- [ ] **Step 3:** Background/resume (Review Focus 3): at the title, `adb shell input keyevent HOME`, wait 5 s, `b3x.sh run`. Acceptance: picture returns, no tombstone.
- [ ] **Step 4:** No gamepad (Review Focus 4): unplug/disconnect controllers, cold start. Acceptance: title shown, logcat has `[input] no controller`. With a Bluetooth/USB pad: Start advances past the title.
- [ ] **Step 5:** Dimensity 920 (Review Focus 5): same as Step 2-4. Any pipeline failure logs `[vk] pipeline failed <hash>` and the frame continues; record which draws failed.
- [ ] **Step 6:** Record per device in `android-devices.md`: boot time to title, fps at title (`RECOMP_FPS_LOG=1` via `b3x_env.txt`), thermal state after 5 min (`adb shell dumpsys thermalservice | grep -i status`).
- [ ] **Step 7:** Commit: `Device bring-up: title screen on <devices>`; update README status line.

---

Out of this plan (each gets its own design → plan): startup menu (pick ISO/folder via SAF, verify title id, extract to app storage), touch controls, in-race performance tuning, release signing and distribution.
