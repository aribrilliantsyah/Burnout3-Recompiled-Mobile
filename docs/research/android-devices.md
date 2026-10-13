# Test devices

## Infinix X6815D (Dimensity 920 class)

Identified with REA `list_adb_devices` + `inspect_adb_device` and `getprop`:

| | |
|---|---|
| Serial | `095392531E002113` (USB) |
| Model | Infinix X6815D (`X6815D-GL`) |
| SoC | MediaTek MT6877V/TZA (`mt6877`, Dimensity 900/920 family), 8 cores, 2.0 / 2.5 GHz clusters |
| GPU | Mali-G68 MC4, driver `v1.r32p1` (`ro.hardware.vulkan=mali`) |
| Android | 13 (SDK 33), patch 2024-05-05, build `TP1A.220624.014`, user/release-keys |
| Kernel | 4.19.191, page size 4096 |
| RAM | 7.4 GB (`MemTotal 7748476 kB`) |
| Storage | 226 GB, 19 GB free at first connection |
| ABIs | arm64-v8a, armeabi-v7a, armeabi |

### Results

- `scripts/android/test-runtime.sh`: first run FAIL. Every fixed aperture
  (0x80000000, 0xF0000000, 0xFD000000, 0xFE800000, 0xFF000000, extended VMA)
  failed to map: the guest base lands just under the libraries where mmap
  hands out space top-down, so base + guest VA was already taken. Fixed by
  reserving the full 4 GB guest span (commit `0bde4d8`); all PASS since.
- No game data: SDL message box "Failed to load default.xbe...", process
  stays up, no tombstone.
- The screen must be on and unlocked for SDLActivity to start the game
  (`adb shell svc power stayon usb` keeps it awake on USB).
- Game data pushed with `b3x.sh push-game` (719 files, ~2 min). adb pushes
  as `shell` with mode 644; the app could not create
  `UDATA\4541005b\TitleMeta.xbx` (STATUS_ACCESS_DENIED) and the title quit
  via HalReturnToFirmware. push-game now skips UDATA/TDATA and makes the
  tree group-writable.
- **Boot runs** (2026-10-13): XBE loads, kernel thunks 147/147, APU and SDL
  audio (OpenSL ES 48 kHz) up, guest threads run, the title streams the
  intro movie and pushes NV2A command buffers; ~140% CPU, 266 MB RSS.
- **Screen stays black: the Vulkan renderer cannot start.** The Mali-G68
  driver (r32p1) is Vulkan **1.1.177** and lacks every extension nv2a_vk
  requires: `VK_KHR_push_descriptor`, `VK_EXT_vertex_input_dynamic_state`,
  `VK_KHR_dynamic_rendering`, `VK_EXT_extended_dynamic_state` (and Vulkan
  1.3 itself). Log: `[VK] vkCreateDevice failed (needs push descriptors,
  dynamic vertex input, Vulkan 1.3)`. Features: no BC textures (ETC2/ASTC
  only), no logicOp, no dualSrcBlend, no shaderClipDistance, no
  fillModeNonSolid. Full list: `adb shell cmd gpu vkjson`.
