# static.dat Track Texture Format

> Per-track DXT texture dictionary embedded in static.dat files

## Overview

Each track directory contains a `static.dat` file with all textures used by the track geometry in `streamed.dat`. Textures are DXT1, DXT3, or DXT5 compressed. A typical track contains ~160 textures.

## Texture Entry Layout

Each texture entry consists of a **24-byte header** followed by a **32-byte name** followed by **pixel data**.

Relative to the name string offset:

| Offset | Type | Description |
|--------|------|-------------|
| name - 20 | uint32 | Xbox format code (0x0C=DXT1, 0x0E=DXT3, 0x0F=DXT5) |
| name - 16 | uint32 | Width (pixels, power of 2, 8-2048) |
| name - 12 | uint32 | Height (pixels, power of 2, 8-2048) |
| name - 8 | uint32 | Name field size (always 0x20 = 32) |
| name - 4 | float | LOD bias |
| name + 0 | char[32] | Null-terminated texture name (ASCII, underscore-separated) |
| name + 32 | uint8[] | Pixel data (base mip, optionally followed by mipmaps) |

## Pixel Data Size

- **DXT1**: `(width/4) * (height/4) * 8` bytes
- **DXT3/DXT5**: `(width/4) * (height/4) * 16` bytes

## Scanning Algorithm

Textures are found by scanning for valid name strings (4+ chars, contains underscore, printable ASCII) preceded by a valid header (recognized format code, power-of-2 dimensions, name_field_size == 0x20).

## Texture Naming Convention

Textures follow a naming pattern indicating their use:
- `bk_paving_rd2` — road paving
- `bk_shopbot2` — shop building bottom section
- `bk_shoptop3` — shop building top section
- `HK_Road1` — Hong Kong road surface
- `bk_warnsigna` — warning sign
- `Chgo_armco_01` — Chicago armco barrier
- `KS_tree_hero02` — hero tree billboard
- `bk_meshfence` — chain-link fence (alpha)

## Example (AS/C1_V1 — Bangkok)

160 textures found:
- DXT1: ~120 textures (roads, concrete, buildings)
- DXT5: ~40 textures (signs, fences, glass — with alpha channel)
- Dimensions: 128x128 to 512x512

## TODO

- [ ] Document mipmap chain storage
- [ ] Document LOD bias usage
- [ ] Document any header structure before the first texture entry
- [ ] Check if texture order in file matches the index used by streamed.dat
