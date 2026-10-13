# Changelog

All notable changes to this project are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[SemVer](https://semver.org/).

## [Unreleased]

### Added
- Android port started from burnout3-sw (Switch/Linux) v0.2, commit
  `0bf84b5`. Package `com.ryuna.b3x`, arm64-v8a, Vulkan.

### Changed
- Switch-only files moved to `references/switch/` (not built).
- `tools/regen.sh` reads `roms/game/default.xbe` and writes `.work/gen/` by
  default.

The Switch port's history is in
[references/switch/CHANGELOG.md](references/switch/CHANGELOG.md).
