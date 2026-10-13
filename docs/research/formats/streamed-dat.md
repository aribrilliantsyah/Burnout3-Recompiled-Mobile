# streamed.dat Track Geometry Format

> Per-track world geometry with vertex/index buffers, object descriptors, and texture references

## Overview

Each track directory contains a `streamed.dat` file with all the renderable geometry for that track. The file is divided into variable-size **sections**, each representing a chunk of the game world (typically one city block or road segment).

Sections come in **pairs** — each pair shares the same center position. Only "type-0" sections contain the expected vertex/index layout; "type-1" sections have a different internal format and should be skipped.

## Section Header (128 bytes)

| Offset | Type | Description |
|--------|------|-------------|
| 0x00 | uint32 | Version (always 1) |
| 0x08 | uint32 | Section size (bytes) |
| 0x10 | float3 | Section center position (x, y, z) |
| 0x54 | uint32 | Waypoint table offset |
| 0x60 | uint32 | Object count |
| 0x80 | uint32 | (flags/reserved) |
| 0x84 | uint16[obj_count] | **Per-object texture indices** (global index into static.dat) |

## Type-0 Section Detection

A section is "type-0" (has standard geometry layout) when:

```
vertex_buffer_start == 0x0C90 + object_count * 0x90
```

Where `vertex_buffer_start = waypoint_offset + 0x50`.

## Object Descriptors (0x90 bytes each, starting at section+0x0C90)

| Offset | Size | Description |
|--------|------|-------------|
| 0x00-0x7F | 128 | 8 bounding box corners (8 × {float x, y, z, padding}) |
| 0x80 | uint32 | Render batch ID (observed: 2 or 6) |
| 0x84 | uint32 | **Index count** for this object's triangle strip |
| 0x88 | uint32 | (offset/reference, purpose TBD) |
| 0x8C | uint32 | Sentinel (always 0xFFFFFFFF) |

## Vertex Buffer

Starts at `0x0C90 + object_count * 0x90`. Vertex stride: **28 bytes**.

| Offset | Type | Description |
|--------|------|-------------|
| 0x00 | float3 | World position (x, y, z) |
| 0x0C | uint32 | Packed normal (11-11-10 bit signed, same as BGV) |
| 0x10 | uint32 | Vertex color (ARGB) — baked ambient occlusion (greyscale, range 5-128) |
| 0x14 | float2 | Texture coordinates (u, v) |

## Index Buffer

Follows the vertex buffer. 16-bit indices forming **triangle strips**.

### Strip Boundaries

Each object descriptor defines a contiguous range in the index buffer. Object strips are partitioned by cumulative `idx_count` from each descriptor at +0x84.

### Degenerate Restart

Within each object's strip, repeated indices (e.g., index N, N) act as sub-strip separators. The winding counter must reset after a degenerate.

### Index 0 as Draw Call Separator

Index 0 appears at object boundaries as a separator. Since vertex 0 is at a fixed position, any triangle connecting it to distant geometry creates a giant spanning polygon. These are filtered by edge-length check (>40 units = discard).

## Texture Mapping

The section header at offset `+0x84` contains a `uint16[]` array with one entry per object. Each entry is a **global texture index** into the static.dat texture list (see [static-dat.md](static-dat.md)).

Verified: all 1467 objects in AS/C1_V1 map to valid texture indices (0-159).

## Track Statistics (AS/C1_V1 — Bangkok)

- 98 total sections (49 type-0 pairs)
- 151,566 vertices, 272,092 indices
- 1,467 objects → 1,415 GPU meshes after filtering
- Track center: (1180, 88, 731), radius: 1593 units

## TODO

- [ ] Document type-1 section format
- [ ] Document waypoint table structure
- [ ] Document section connectivity/visibility tables
- [ ] Document the +0x80 batch ID field purpose
- [ ] Document the +0x88 offset field purpose
