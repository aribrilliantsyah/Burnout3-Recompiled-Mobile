# Global.txd Format

> RenderWare texture dictionary containing HUD and FX textures

## Overview

`Global.txd` is a RenderWare binary stream file containing 191 texture entries used for HUD elements, particle effects, and UI. It does NOT contain track or vehicle textures.

## Structure

- Standard RenderWare binary stream header
- TOC with 16-byte entries: index, padding, data offset
- Each texture entry at its offset contains:
  - Format info at +0x34 (uint32 format code)
  - Dimensions at +0x38 (uint16 width), +0x3A (uint16 height)
  - Name at +0x48 (24-byte null-terminated ASCII)
  - Pixel data follows

## Texture Formats

- DXT1, DXT3, DXT5 (compressed)
- P8 (palettized 8-bit — 2 entries fail to load due to size mismatch)

## Fixup Process

After loading into Xbox memory, the RenderWare binary stream headers need relocation. The fixup function (`sub_00014730`) processes all 191 entries, adjusting internal pointers from file-relative to memory-absolute addresses.

## Contents

HUD textures (BoostFireCore01-30, BoostFireEdge01-41, etc.), UI elements (A_Button, B_Button, Arrow), effect textures (blobbyshadow, Aftertouch), and menu graphics.

## TODO

- [ ] Document RW binary stream header format in detail
- [ ] Document fixup pointer list and relocation algorithm
- [ ] Document P8 palette format and why 2 entries fail
