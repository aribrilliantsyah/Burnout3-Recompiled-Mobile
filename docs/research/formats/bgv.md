# BGV Vehicle Geometry Format

> Criterion Games' proprietary vehicle model format used in Burnout 3: Takedown (Xbox)

## Overview

BGV files contain vehicle geometry data: vertices, normals, UVs, and triangle strip indices. Each vehicle has one BGV file containing 4 LOD levels. The game ships with 67 vehicles across 7 classes.

## File Structure

| Offset | Type | Description |
|--------|------|-------------|
| 0x00 | uint32 | Magic: `0x17` |
| 0x14 | float | Bounding radius |
| 0x4C | uint32 | LOD 0 offset (lowest detail) |
| 0x50 | uint32 | LOD 1 offset |
| 0x54 | uint32 | LOD 2 offset |
| 0x58 | uint32 | LOD 3 offset (highest detail) |

## Vertex Format (24 bytes)

| Offset | Type | Description |
|--------|------|-------------|
| 0x00 | float3 | Position (x, y, z) |
| 0x0C | uint32 | Packed normal (Xbox D3DVSDT_NORMPACKED3: 11-11-10 bit signed) |
| 0x14 | float2 | Texture coordinates (u, v) |

### Packed Normal Decoding

```c
int raw_x = (packed >> 0)  & 0x7FF; if (raw_x & 0x400) raw_x -= 0x800;
int raw_y = (packed >> 11) & 0x7FF; if (raw_y & 0x400) raw_y -= 0x800;
int raw_z = (packed >> 22) & 0x3FF; if (raw_z & 0x200) raw_z -= 0x400;
float nx = raw_x / 1023.0f;
float ny = raw_y / 1023.0f;
float nz = raw_z / 511.0f;
```

## Draw Calls

<!-- TODO: Document draw call pattern scan -->
<!-- Pattern: 0x3F800000, 0, byte_offset, index_count -->

## Index Format

- 16-bit indices
- Triangle strips with degenerate restart markers (repeated index = strip break)

## Vehicle Classes

| Code | Class | Count |
|------|-------|-------|
| COMP | Compact | ~10 |
| CUPE | Coupe | ~10 |
| HEVY | Heavy | ~10 |
| HSPC | High-spec | ~10 |
| MSCL | Muscle | ~10 |
| SPRT | Sport | ~10 |
| SUPR | Super | ~7 |

## Related Files

- `.btv` — Vehicle texture/paint variants (format TBD)

## TODO

- [ ] Document LOD section internal structure
- [ ] Document draw call descriptor format in detail
- [ ] Decode .btv texture format
- [ ] Document sub-entry structure within LOD sections
