<div align="center">

<img src="https://cdn2.steamgriddb.com/logo/816d385f9dfa8b6e2f3637f77b97ac9c.png" alt="Burnout 3: Takedown" width="520">

### Xbox static recompilation for Android

The original Xbox (NTSC-U) release of **Burnout 3: Takedown**, lifted
instruction by instruction to C and running natively on arm64 phones, with
no emulator.

![Android](https://img.shields.io/badge/Android-arm64--v8a-3DDC84?logo=android&logoColor=white)
![Vulkan](https://img.shields.io/badge/Vulkan-1.1+-AC162C?logo=vulkan&logoColor=white)
![Status](https://img.shields.io/badge/status-bring--up-orange)
![Version](https://img.shields.io/badge/version-0.0--dev-blue)

[Status](#-status) · [Devices](#-target-devices) · [Building](#%EF%B8%8F-building) · [Configuration](#%EF%B8%8F-configuration) · [Layout](#-project-layout) · [Credits](#-credits)

</div>

---

> [!IMPORTANT]
> **No game data is included, and none ever will be.** The APK carries only
> the recompiled engine. You need your own copy of the Xbox disc (NTSC-U,
> title ID `0x4541005B`). A first-run menu that lets you pick the ISO or the
> extracted folder from your phone's storage is planned; until then the
> developer script pushes an extracted disc over adb.

## 📍 Status

Early bring-up. The game is not playable on Android yet.

| | |
|---|---|
| Linux (dev host): intros, menus, races, audio, saves | ✅ inherited from the Switch/Linux port |
| Android build (`libmain.so` + APK `com.ryuna.b3x`, 31 MB) | ✅ builds; not yet run on a phone |
| Boots to the title screen on a phone | ⏳ |
| First-run menu: pick ISO / folder | ⏳ planned |
| Touch controls | ⏳ planned (gamepads first) |

The plan for this stage is in
[docs/superpowers/plans/2026-10-13-android-port-bootstrap.md](docs/superpowers/plans/2026-10-13-android-port-bootstrap.md).

## 📱 Target devices

| SoC | CPU | GPU / driver |
|---|---|---|
| Qualcomm Snapdragon 7s Gen 2 | 4x Cortex-A78 2.4 GHz + 4x A55 | Adreno 710, Vulkan 1.3 |
| MediaTek Dimensity 920 | 2x Cortex-A78 2.5 GHz + 6x A55 | Mali-G68 MC4, Vulkan 1.1 |

Android 11 (API 30) or newer, arm64-v8a, Vulkan required. The game logic,
the NV2A command processor and the APU all run on the CPU, so per-core speed
matters most; both chips are well ahead of the Switch's Tegra X1, which runs
this code at 40-55 fps.

## 🛠️ Building

Development happens on Linux: the same code runs there as a desktop program,
which is where most debugging is done.

<details open>
<summary><b>1. Extract the disc and lift the XBE to C</b></summary>

```sh
python3 -m venv .work/venv && .work/venv/bin/pip install -r tools/requirements.txt
# Your own image goes in roms/ (git-ignored)
(cd xboxrecomp && ../.work/venv/bin/python -m tools.xiso unpack "../roms/<disc>.iso" -o ../roms/game)
tools/regen.sh          # roms/game/default.xbe -> .work/gen/
```

</details>

<details open>
<summary><b>2a. Linux</b> (SDL2 + Vulkan or OpenGL)</summary>

```sh
cmake -S . -B build/linux-vk -G Ninja -DNFSU2_VULKAN=ON -DNFSU2_GEN_DIR=$PWD/.work/gen
cmake --build build/linux-vk -j3
NFSU2_GAME_DIR=$PWD/roms/game build/linux-vk/b3_recomp
```

</details>

<details open>
<summary><b>2b. Android</b> (NDK r29, SDK 36, a phone with USB debugging)</summary>

```sh
scripts/android/b3x.sh all          # deps, native, apk, install, run, log
scripts/android/b3x.sh push-game    # dev only: copy roms/game to the phone
scripts/android/b3x.sh -s <serial> install run log
```

`b3x.sh` commands: `deps native apk install push-game env run log stop all`.
`ANDROID_HOME` must point at the SDK.

</details>

## ⚙️ Configuration

On Linux these are environment variables. On Android they go in
`b3x_env.txt` in the app's external files directory
(`/sdcard/Android/data/com.ryuna.b3x/files/`), one `KEY=VALUE` per line, `#`
starts a comment; `scripts/android/b3x.sh env` pushes the repo's copy.

<details>
<summary><b>Build and code generation</b></summary>

| Variable | Meaning |
|---|---|
| `XBOXRECOMP_DIR` | Toolkit to build or regenerate with (default: the vendored `xboxrecomp/`). |
| `B3_XBE` | `default.xbe` to lift (default `roms/game/default.xbe`). |
| `B3_GEN_DIR` / `NFSU2_GEN_DIR` | Directory for the lifted C (default `.work/gen`). |
| `PYTHON` | `regen.sh`: interpreter with capstone (default `.work/venv/bin/python`). |
| `LIFT_ONLY=1` | `regen.sh`: only re-lift (enough after translator or `recomp_manual.c` changes; seed changes need the full run). |
| `TRACE_FUNCS` | `regen.sh`: JSON list of addresses whose entries are logged (`[TRACE]`). |

</details>

<details>
<summary><b>Game and host</b></summary>

| Variable | Meaning |
|---|---|
| `NFSU2_GAME_DIR` | Extracted disc (Linux default `./game`; Android default `<external files>/game`). |
| `NFSU2_GL=0` | Use the executor's CPU renderer instead of the GPU renderer. |
| `NFSU2_APU=0` | No emulated APU (no sound). |
| `B3_FRAME_LOCK=1` | One simulation step per rendered frame, as on the Xbox (races go slow motion below 60 fps). |
| `NFSU2_EXIT_TRACE=1` | Print the guest state at exit. |

</details>

<details>
<summary><b>Scheduling and kernel</b></summary>

| Variable | Meaning |
|---|---|
| `RECOMP_GIL_EAGER=1` | Hand the guest lock over by guest priority. |
| `RECOMP_VBLANK` | Vblank interrupt for D3D (default 1). |
| `RECOMP_NO_MIRRORS=1` | Map no RAM mirrors (Android default). |
| `RECOMP_QUIET` | 1 = no periodic kernel/GPU summaries, 0 = show them. |
| `XBOX_LOG_LEVEL` | Kernel log level (0 errors … trace). |

</details>

<details>
<summary><b>Graphics (Vulkan; GL on Linux only)</b></summary>

| Variable | Meaning |
|---|---|
| `RECOMP_GL_SCALE` | Render resolution multiple (fractions allowed). Vulkan: only until Gameplay → Resolution Scale is changed. |
| `RECOMP_PROG_CACHE` | Shader/pipeline cache file, `=0` off. |
| `RECOMP_CULL=0` | No off-screen batch culling (the port's default). |
| `RECOMP_VK_MIPS=0` | No mipmaps. |
| `RECOMP_VK_ANISO` | Anisotropic filtering cap, 1..16. |
| `RECOMP_VK_LOD_BIAS` | Mip bias, negative = sharper (default -0.25). |
| `RECOMP_VK_FXAA=0` | No FXAA. |
| `RECOMP_VK_SQUARE=0` | In 16:9, no extra 4/3 columns for square pixels. |
| `RECOMP_VK_CUBE=0` | No reflection cube maps. |
| `RECOMP_VK_HEADLESS=1` | Linux: no window (also implied by `SDL_VIDEODRIVER=offscreen`). |
| `RECOMP_VK_VALIDATION=1` | Khronos validation layer. |
| `RECOMP_GL_DUMP=<prefix>[,every]` | Write presented frames as BMP (every N frames, default 60). |
| `RECOMP_VK_DUMP_SURF=<va>[,<va>...]` | Also dump these surfaces with each dumped frame; `RECOMP_VK_DUMP_RAW=1` adds BGRA files with alpha. |
| `RECOMP_FRAME_TRACE=<flag file>` | When the file exists: one line per draw/clear of the next frame. |
| `RECOMP_FPS_LOG=1` | Presented frames per 10 s. |

</details>

<details>
<summary><b>Audio</b></summary>

| Variable | Meaning |
|---|---|
| `RECOMP_AUDIO=0` | No host audio device. |
| `RECOMP_AUDIO_VOLUME` | Master volume 0..100. |
| `RECOMP_AUDIO_DUMP=<path>,<start s>,<secs>` | Record the exact PCM sent to the device. |
| `RECOMP_APU_VOICES=1` | Every APU voice of the last 5 s (format, bins, buffer, peak). |
| `RECOMP_APU_TRACE=1` | Front-end methods and a per-second voice summary. |

</details>

## 📂 Project layout

| Path | What |
|---|---|
| `src/main.c` | boot and runtime defaults |
| `src/recomp_manual.c` | hand-written overrides of lifted functions (library hooks, XMV colour converter, simulation stepping, the Gameplay picture rows) |
| `config/seed_functions.json` | entry points the static pass cannot see |
| `xboxrecomp/` | the toolkit (MIT), vendored with this port's changes: NV2A renderers (Vulkan, OpenGL), SDL audio, translator fixes |
| `tools/regen.sh` | XBE → lifted C (`.work/gen/`, never committed) |
| `scripts/android/` | Android dependency build, build/install/run script |
| `android/` | Gradle app `com.ryuna.b3x` (SDLActivity) |
| `docs/research/` | format and reverse-engineering notes |
| `references/switch/` | the Switch-only files this port started from (not built) |
| `roms/` | your disc image and its extraction (git-ignored) |

## 🙏 Credits

- [sp00nznet](https://github.com/sp00nznet): the Burnout 3 recompilation and
  the [xboxrecomp](https://github.com/sp00nznet/xboxrecomp) toolkit (MIT).
- [antoxa2584x](https://github.com/antoxa2584x/burnout3-sw): the Switch/Linux
  port with the GPU renderers this Android port is built from.

Burnout 3: Takedown is © Electronic Arts / Criterion Games. This project is
not affiliated with them and contains none of their data.

## 📜 License

[MIT](LICENSE), as the projects it is built on.
