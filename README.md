<div align="center">

<img src="https://cdn2.steamgriddb.com/logo/816d385f9dfa8b6e2f3637f77b97ac9c.png" alt="Burnout 3: Takedown" width="520">

### Xbox static recompilation for Nintendo Switch and Linux

The original Xbox (NTSC-U) release of **Burnout 3: Takedown**, lifted
instruction by instruction to C and running natively, with no emulator.

![Switch](https://img.shields.io/badge/Nintendo%20Switch-homebrew-E60012?logo=nintendoswitch&logoColor=white)
![Linux](https://img.shields.io/badge/Linux-x86__64-FCC624?logo=linux&logoColor=black)
![Vulkan](https://img.shields.io/badge/Vulkan-1.3-AC162C?logo=vulkan&logoColor=white)
![OpenGL](https://img.shields.io/badge/OpenGL-renderer-5586A4?logo=opengl&logoColor=white)
![Version](https://img.shields.io/badge/version-0.2-blue)

[Features](#-features) · [Playing on Switch](#%EF%B8%8F-playing-on-switch) · [Building](#%EF%B8%8F-building) · [Configuration](#%EF%B8%8F-configuration) · [Status](#-status)

</div>

---

> [!IMPORTANT]
> **No game data is included.** You need your own copy of the Xbox disc,
> extracted (`default.xbe`, `Data/`, `Tracks/`, `ovid/`, `pveh/`, `sound/`, ...).

Built on the runtime of the NFS Underground 2 / Carbon Switch ports (GPU
renderers, Horizon support). An upstream Windows port with a software GPU
exists at [sp00nznet/burnout3](https://github.com/sp00nznet/burnout3).

## ✨ Features

- 🏁 **Native code**: the whole game runs as recompiled C, built with
  [xboxrecomp](https://github.com/sp00nznet/xboxrecomp)
- 🎮 **Two renderers**: Vulkan (NVK on Switch) and OpenGL, with render
  scaling
- 🌆 **The full 3D world**: races, the car-select garage, motion blur and
  bloom (the game's off-screen scene composite), fog
- 🪞 **Car reflections** (Vulkan): the game's dynamic reflection cube with
  its light-strip highlights
- 🖼️ **Picture options** in **Driver Details → Settings → Gameplay**
  (Vulkan): **Resolution Scale** 1x / 1.5x / 2x / 2.5x, **Anti-Aliasing**
  (FXAA) and **Anisotropic Filtering** Off-16x, applied at once and kept in
  `b3x_options.txt`; mipmaps with trilinear filtering
- ⏱️ **Real-time races at any frame rate**: the game steps its simulation
  by the time that really passed instead of one 60 Hz step per frame, so
  races below 60 fps are not slow motion
- 🔊 **Audio** through an emulated Xbox APU (engine, effects, EA Trax,
  speech), 5.1 downmixed to stereo
- 🎬 **Movies** (XMV) with a native colour converter
- 💾 **Profiles** save and load (`game/UDATA`), including after racing

## 🕹️ Playing on Switch

1. Copy the NRO to `sdmc:/switch/b3x/` (`b3x-vulkan.nro` for the Vulkan
   build, `b3x.nro` for OpenGL).
2. Copy the **extracted** disc (not the ISO) to `sdmc:/switch/b3x/game/`.
3. Start it with **title takeover**: hold **R** while launching any game.
   Applet mode leaves too little memory.

```
sdmc:/switch/b3x/
├── b3x-vulkan.nro
├── b3x_env.txt       optional settings, KEY=VALUE per line
├── b3x_options.txt   written by the in-game picture options
└── game/
    ├── default.xbe
    ├── UDATA/        your profiles
    └── ...
```

Buttons map by label (Switch A = Xbox A). Settings go in `b3x_env.txt`
(see [Configuration](#%EF%B8%8F-configuration)) and the log is written to the
same folder (`b3x_log.txt`).

## 🛠️ Building

<details open>
<summary><b>1. Lift the XBE to C</b></summary>

```sh
# Writes B3_GEN_DIR (default /root/b3x/gen)
B3_XBE=/path/to/game/default.xbe B3_GEN_DIR=/path/to/gen tools/regen.sh
```

</details>

<details open>
<summary><b>2a. Linux</b> (SDL2 + OpenGL or Vulkan)</summary>

```sh
cmake -S . -B build -G Ninja -DNFSU2_GEN_DIR=/path/to/gen   # add -DNFSU2_VULKAN=ON for Vulkan
cmake --build build
NFSU2_GAME_DIR=/path/to/game build/b3_recomp
```

</details>

<details open>
<summary><b>2b. Nintendo Switch</b> (devkitA64, switch-sdl2, switch-mesa)</summary>

```sh
NFSU2_GEN_DIR=/path/to/gen NFSU2_GAME_SRC=/path/to/game switch/build.sh
# Vulkan build (needs mesa-switch NVK and glslang for Switch)
VULKAN=1 JOBS=6 NFSU2_GEN_DIR=/path/to/gen switch/build.sh
```

</details>

### Project layout

| Path | What |
|---|---|
| `src/main.c` | boot and runtime defaults |
| `src/recomp_manual.c` | hand-written overrides of lifted functions (library hooks, XMV colour converter, simulation stepping, the Gameplay picture rows) |
| `src/switch_nx.c` | Switch log device, env file, exception handler, loading screen |
| `config/seed_functions.json` | entry points the static pass cannot see |
| `xboxrecomp/` | the toolkit (MIT), vendored with this port's changes: NV2A renderers (Vulkan, OpenGL), SDL audio, Switch platform layer, translator fixes |
| `tools/regen.sh` | XBE → lifted C (`gen/`, never committed) |
| `switch/build.sh` | Switch NRO build + SD-card staging |

## ⚙️ Configuration

On Linux these are ordinary environment variables. On the Switch they go in
`sdmc:/switch/b3x/b3x_env.txt`, one `KEY=VALUE` per line (`#` starts a
comment). Anything left out runs at its default. Most of the list is for
debugging, so the sections below are folded.

```ini
# sdmc:/switch/b3x/b3x_env.txt
RECOMP_GL_SCALE=1.5
NFSU2_CPU_MHZ=1785
```

<details>
<summary><b>Build and code generation</b></summary>

| Variable | Meaning |
|---|---|
| `XBOXRECOMP_DIR` | Toolkit to build or regenerate with (default: the vendored `xboxrecomp/`). |
| `B3_XBE` | `default.xbe` to lift (`tools/regen.sh`). |
| `B3_GEN_DIR` / `NFSU2_GEN_DIR` | Directory for the lifted C (default `/root/b3x/gen`). |
| `LIFT_ONLY=1` | `regen.sh`: only re-lift (enough after translator or `recomp_manual.c` changes; seed changes need the full run). |
| `TRACE_FUNCS` | `regen.sh`: JSON list of addresses whose entries are logged (`[TRACE]`). |
| `NFSU2_GAME_SRC` | `switch/build.sh`: extracted disc to stage (default `/root/b3x/game`). |
| `SD_ROOT` | `switch/build.sh`: staging SD-card root (default `<repo>/switch_sd`). |
| `BUILD_DIR` | `switch/build.sh`: build directory (default `/root/b3x/build-switch`, `-vk` with `VULKAN=1`). |
| `JOBS` | `switch/build.sh`: parallel compile jobs (6 fits `-O2` in RAM). |
| `VULKAN=1` | `switch/build.sh`: build the Vulkan renderer (`b3x-vulkan.nro`). |
| `NVK_SDK`, `GLSLANG_DIR` | `switch/build.sh` with `VULKAN=1`: mesa-switch NVK install and Switch glslang. |
| `LTO=1` | `switch/build.sh`: link-time optimisation of the lifted code. |

</details>

<details>
<summary><b>Game and host</b></summary>

| Variable | Meaning |
|---|---|
| `NFSU2_GAME_DIR` | Extracted disc (Linux; default `./game`). The Switch always uses `sdmc:/switch/b3x/game/`. |
| `NFSU2_GL=0` | Use the executor's CPU renderer instead of the GPU renderer. |
| `NFSU2_APU=0` | No emulated APU (no sound). |
| `B3_FRAME_LOCK=1` | One simulation step per rendered frame, as on the Xbox (races go slow motion below 60 fps). |
| `NFSU2_EXIT_TRACE=1` | Print the guest state at exit. |

</details>

<details>
<summary><b>Switch (Horizon)</b></summary>

| Variable | Meaning |
|---|---|
| `NFSU2_CPU_MHZ`, `NFSU2_GPU_MHZ`, `NFSU2_MEM_MHZ` | Clocks (opt-in; more heat and battery). The log shows `[clock] ...`. |
| `NFSU2_NO_LOG=1` / `NFSU2_LOG=0` | No log file, no `[perf]` reports, no profiler. |
| `NFSU2_LOG_SYNC=1` | Write every log line to the card at once (slow; for hangs). |
| `NFSU2_LOADER=0` | No loading screen before the first frame. |
| `RECOMP_NX_PROFILE` | Sampling profiler into `prof.bin`: 1 = busy threads, 2 = every thread above 2%. |
| `RECOMP_NX_AUDIO_PRIO=0` | Leave the audio threads at the default priority. |

</details>

<details>
<summary><b>Scheduling and kernel</b></summary>

| Variable | Meaning |
|---|---|
| `RECOMP_GIL_EAGER=1` | Hand the guest lock over by guest priority. |
| `RECOMP_VBLANK` | Vblank interrupt for D3D (default 1). |
| `RECOMP_NO_MIRRORS=1` | Map no RAM mirrors (what the Switch does; for reproducing it on a desktop). |
| `RECOMP_QUIET` | 1 = no periodic kernel/GPU summaries (Switch default), 0 = show them. |
| `XBOX_LOG_LEVEL` | Kernel log level (0 errors … trace). |

</details>

<details>
<summary><b>Graphics (GL and Vulkan renderers)</b></summary>

| Variable | Meaning |
|---|---|
| `RECOMP_GL_SCALE` | Render resolution multiple (fractions allowed). Vulkan: only until Gameplay → Resolution Scale is changed. |
| `RECOMP_GL_THREAD=1` | GL renderer: GL calls on their own thread. |
| `RECOMP_PROG_CACHE` | Shader/pipeline cache file, `=0` off. |
| `RECOMP_CULL=0` | No off-screen batch culling (the port's default). |
| `RECOMP_VK_MIPS=0` | Vulkan: no mipmaps. |
| `RECOMP_VK_ANISO` | Vulkan: anisotropic filtering cap, 1..16. |
| `RECOMP_VK_LOD_BIAS` | Vulkan: mip bias, negative = sharper (default -0.25). |
| `RECOMP_VK_FXAA=0` | Vulkan: no FXAA. |
| `RECOMP_VK_SQUARE=0` | Vulkan: in 16:9, no extra 4/3 columns for square pixels. |
| `RECOMP_VK_CUBE=0` | Vulkan: no reflection cube maps. |
| `RECOMP_VK_HEADLESS=1` | Vulkan on Linux: no window (also implied by `SDL_VIDEODRIVER=offscreen`). |
| `RECOMP_VK_VALIDATION=1` | Vulkan on Linux: Khronos validation layer. |
| `RECOMP_GL_DUMP=<prefix>[,every]` | Write presented frames as BMP (every N frames, default 60). |
| `RECOMP_VK_DUMP_SURF=<va>[,<va>...]` | Vulkan: also dump these surfaces with each dumped frame; `RECOMP_VK_DUMP_RAW=1` adds BGRA files with alpha. |
| `RECOMP_FRAME_TRACE=<flag file>` | When the file exists: one line per draw/clear of the next frame (target, state, textures, combiner, first vertices). |
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

## 📊 Status

| | |
|---|---|
| Intro movies, title, menus | ✅ working |
| Profile create / save / load | ✅ working (saves after racing fixed in 0.1) |
| 3D world: races, garage, motion blur, reflections | ✅ working (GL has no reflections) |
| Audio: music, engine, effects | ✅ working |
| Races at real-time speed below 60 fps | ✅ working |
| Picture options (Vulkan) | ✅ working |
| Race training videos | ⚠️ play in full before an event's first race (cannot be skipped) |
| Switch | 🧪 runs on hardware (Vulkan), 40-55 fps in races; being tuned |

See [CHANGELOG.md](CHANGELOG.md) and [ROADMAP.md](ROADMAP.md).
