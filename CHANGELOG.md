# Changelog

All notable changes to this project are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[SemVer](https://semver.org/).

## [Unreleased]

### Added
- Android port started from burnout3-sw (Switch/Linux) v0.2, commit
  `0bf84b5`. Package `com.ryuna.b3x`, arm64-v8a, Vulkan.
- Vulkan 1.1 path in nv2a_vk (render passes, descriptor sets, baked
  pipeline state) for drivers such as Mali r32p1; the game runs on a
  Mali-G68 phone at ~60 fps.
- Launcher: game folder (app folder or All files access), display,
  graphics, sound, touch, controller and advanced settings.
- Touch pad with two layouts (Drive, Full Xbox controller), a layout
  editor, tilt steering, haptics; controller remapping; rumble to the
  phone's vibrator.
- Install from ISO: the launcher checks an Xbox disc image (XDVDFS, title
  4541005B, free space) and extracts it into the app's folder once.
- In-game ☰ menu: resume, edit touch layout, exit to the launcher.
- Debug log file (`b3x_log.txt`) with fps and guest snapshots.

### Fixed
- Random freezes: the D3D fence wait ends once the GPU ring is empty.
- Mali swapchain with 9 images; surface lost after home/resume; tiled
  aperture mapping race; picture rotated 90° with landscape locked.

### Changed
- Switch-only files removed (they stay in the burnout3-sw history).
- `tools/regen.sh` reads `roms/game/default.xbe` and writes `.work/gen/` by
  default.

The Switch port's history is in
[antoxa2584x/burnout3-sw](https://github.com/antoxa2584x/burnout3-sw).
