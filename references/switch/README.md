# Switch-only files (reference)

Files that only the Nintendo Switch (libnx/devkitA64) build of
[burnout3-sw](https://github.com/antoxa2584x/burnout3-sw) used, moved here
from the same relative paths when the Android port started (source commit
`0bf84b5`, v0.2). They are not built. They stay as reference for how the
runtime was brought up on another arm64 handheld: boot thread with a 16 MB
stack, log and env files on the SD card, a loading screen, a CPU exception
handler that prints guest state, pad and rumble through libnx, and the
NVK/EGL stubs for a static Vulkan driver.

`#if defined(__SWITCH__)` blocks inside `xboxrecomp/src/` were left in place;
they compile to nothing on Linux and Android.
