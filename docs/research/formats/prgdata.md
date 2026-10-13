# PrgData.bin Format

> Criterion's nested pointer structure for game configuration data

## Overview

PrgData.bin is an 18 KB binary file containing game configuration data in a 3-level nested pointer structure. All internal pointers are relative offsets that must be relocated to absolute Xbox virtual addresses after loading.

## Relocation Process

The file uses a Criterion binary format where offsets are relative to the start of the data. After loading into Xbox memory, a 3-level relocation pass fixes up all pointers:

1. **Level 1**: Top-level items (direct pointers from the root table)
2. **Level 2**: Entries within items (stride 0x18, pointer at specific offsets)
3. **Level 3**: Sub-entries within entries (stride 0x40, pointer at specific offsets)

Each level's pointers are adjusted: `new_ptr = base_va + relative_offset`

## Implementation

See `sub_00159710` in `recomp_manual.c` for the full relocation implementation.

## TODO

- [ ] Document the root table structure
- [ ] Document what game data PrgData contains (vehicle stats? track configs?)
- [ ] Document the entry and sub-entry field layouts
- [ ] Document the verification function (sub_00157680) that reads PrgData hashes
