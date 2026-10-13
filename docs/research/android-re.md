# Reverse-engineering notes for the Android port

What was taken from the user's own disc (kept in `roms/`, never committed)
and how. Everything here can be re-derived with the commands shown.

## Disc and XBE identity (2026-10-13)

`roms/Burnout 3 - Takedown (USA).iso`, 2,476,867,584 bytes, unpacked with
`python -m tools.xiso unpack` (xboxrecomp/tools) into `roms/game/`: 716 files,
2.4 GB (`default.xbe`, `update.xbe`, `dashupdate.xbe`, `Data/`, `Graphics/`,
`Tracks/`, `ovid/`, `pveh/`, `sound/`).

`roms/game/default.xbe` (`tools.xbe_parser`):

| Field | Value |
|---|---|
| sha256 | `9f497acd82adb8edfb72cc31aeeb35010b45d32b13a3f1fc6540717ff24d13f9` |
| Title ID | `0x4541005B` (Retail, North America, DVD_X2) |
| Build | 2004-07-29 19:24:09 UTC; certificate 2004-08-06 |
| Base / image size | `0x00010000` / `0x00768200` (7.41 MB) |
| Entry point | `0x001D2807` |
| Kernel thunks | `0x0036B7C0`, 147 imports |
| Libraries | XAPILIB, XMV, DSOUND, XBOXKRNL, XONLINES, XVOICE, LIBCMT, LIBCPMT, LIBC, D3D8LTCG, XGRAPHCL, all 1.0.5849 |
| Debug path | `C:\work\B3\CG1Code\GameProjects\VisualStudio.NET\Burnout3\XboxExternal\Burnout3_External.exe` |

Sections: `.text` 0x00011000 (0x2BB200), `XMV` 0x002CC200, `DSOUND`
0x002F3F40, `WMADEC` 0x00300D00, `XONLINE` 0x0031AA80, `XNET` 0x003391E0,
`D3D` 0x0034C2E0, `XGRPH` 0x00360A60, `XPP` 0x00362AE0, `.rdata` 0x0036B7C0,
`.data` 0x003B2360 (0x3B95DC virtual, 0x6844C raw), `DOLBY` 0x0076B940,
`XON_RD`, `.data1`, `$$XTIMAGE`, `$$XSIMAGE`, `.XTLID`.

## Lift (tools/regen.sh)

- 24,048 functions found (24,195 labels, 631 thiscall), 24,046 translated,
  0 failed, 79 unresolved call targets stubbed, 48 spin loops, 1,007 MMIO
  accessor functions in DSOUND/XPP/D3D/XGRPH.
- 1,877,219 lines of C in 97 files (`.work/gen/`, 89 MB); about 4.5 minutes
  on the 4-core dev laptop.
- The library hooks listed in CLAUDE.md are present in the lift:
  memmove `sub_002442A0`, `_ftol2` `sub_00244DF0`, `BlockOnTime`
  `sub_00351770`, vblank handler `sub_00356DA0`.

## Memory model check on the real XBE

`xboxrecomp/tests/memory_layout_posix` with `roms/game/default.xbe` on the
Linux host: all checks pass, `Kernel thunks: 147 entries at 0x0036B7C0`.
With its synthetic `tools/conformance/test.xbe` the same test segfaults, also
on the unmodified base commit: a known gap in the synthetic image (no thunk
table), not a regression.

## Linux baseline (Task 5)

`build/linux-vk/b3_recomp` (GCC, -O1 lifted code, 26 MB) on the dev laptop
(Ryzen 3 3250U, Radeon Vega 3, RADV), 90 s headless with
`RECOMP_VK_HEADLESS=1 RECOMP_FPS_LOG=1`: intro movies and the title logo
render correctly, 52-56 fps, vblank 60 Hz, no fatal. With
`NFSU2_GAME_DIR=/nonexistent`: `[FATAL] Failed to load default.xbe...`,
exit 1 at once.

## REA (Reverse Engineer Anything) 6.4.0

- `open_binary` on `default.xbe`: `unsupported binary format`. REA's only
  native deep-analysis provider is Ghidra, which is not configured
  (`GHIDRA_INSTALL_DIR` unset, no Ghidra on the machine), and even with it
  the XBE container would need an XBE loader. Static analysis therefore uses
  xboxrecomp's own pipeline (xbe_parser, disasm, func_id, abi_analysis),
  which is built for exactly this format.
- REA's adb tools (`list_adb_devices`, `inspect_adb_device`,
  `read_adb_logcat`, `install_adb_package`) are the device side of the
  bring-up; see `android-devices.md`.
