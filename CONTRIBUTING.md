# Contributing to the Burnout 3 recompilation

Thanks for looking. This repo is one title on the
[xboxrecomp](https://github.com/sp00nznet/xboxrecomp) toolkit, and most fixes
belong in the toolkit rather than here. Before you start, work out which side
yours is on:

| The problem is... | It goes in |
|---|---|
| wrong lifted code, a missing x86/x87/SSE instruction, a switch table the disassembler misreads | xboxrecomp `tools/recomp`, `tools/disasm` |
| the GPU draws something wrong, a kernel call misbehaves, pads, audio, movies | xboxrecomp `src/` |
| an address only Burnout 3 has (a fence, a doorbell, a seed function), boot defaults | here: `src/main.c`, `src/recomp_manual.c`, `config/seed_functions.json` |

A toolkit change is a PR against xboxrecomp that explains the bug in general
terms. The Burnout 3 commit that needs it names the PR (`needs xboxrecomp#NN`).

## Setting up

Follow the README's *Getting Started*, either route. You need your own disc
image. No dumps, extracted files or generated source go in issues or PRs: attach
logs and screenshots instead.

The README's *Configuration* section lists the debugging variables (frame
dumps, frame traces, APU voices). Run any change against at least one race and one menu path before
you open a PR, and say which modes you checked.

## Rules for what lands

- **Never commit generated or extracted material.** Nothing from
  `.work/gen/`, `build/`, `roms/`, disassembly listings,
  memory dumps or captured pushbuffers. Screenshots and GIFs of the game running
  are fine.
- **Don't hand-edit gen/.** If the generated C is wrong, fix the recompiler.
- Commit subjects in the imperative ("Map the second pad's rumble endpoint"), with
  the *why* in the body.
- C targets MSVC (Visual Studio 2022). Keep new code in the style of the file
  around it.

### Where your code comes from

Contributions must be your own work, or come from a source whose licence is
compatible with MIT (MIT, BSD, zlib, Apache-2.0, public domain). Specifically:

- **Don't port code from GPL projects**: xemu's GPL parts, Cxbx-Reloaded,
  RPCS3, MAME's GPL drivers, or other recompilation projects under the GPL.
  Rewriting it line by line doesn't change that. Reading one to understand how
  the hardware behaves is fine; carrying its code or structure across is not.
  A fix you saw in another project is best submitted as a description of the
  bug, and we'll write it fresh.
- **If something came from elsewhere, say so in the PR**, with a link, and
  keep any copyright header.
- The same goes for AI-assisted code. Those PRs are welcome, provided a human
  understood and verified the change and checked that it doesn't reproduce an
  existing project.
