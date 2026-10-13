#!/usr/bin/env bash
# Build the runtime's POSIX memory regressions with the NDK and run them on
# the phone (guest memory mapping under the device's kernel and page size).
#
#   scripts/android/test-runtime.sh [-s SERIAL]
#
# Exit: 0 all passed, 1 a test failed or did not build, 2 no device.
# memory_layout_posix runs against roms/game/default.xbe (its synthetic
# test.xbe crashes on every host; see docs/research/android-re.md).
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
: "${ANDROID_HOME:=${ANDROID_SDK_ROOT:-$HOME/Documents/Repos/Android/SDK}}"
NDK_VERSION="${NDK_VERSION:-28.2.13676358}"
DEPS="$HERE/.work/android-deps/arm64-v8a"
TC="$ANDROID_HOME/ndk/$NDK_VERSION/build/cmake/android.toolchain.cmake"
REMOTE=/data/local/tmp/b3x-tests
SERIAL="${ANDROID_SERIAL:-}"
[ "${1:-}" = "-s" ] && SERIAL="$2"

die() { local c="$1"; shift; echo "[test-runtime] $*" >&2; exit "$c"; }

if [ -z "$SERIAL" ]; then
    mapfile -t devs < <(adb devices | awk 'NR>1 && $2=="device" {print $1}')
    [ ${#devs[@]} -eq 0 ] && die 2 "no adb device"
    [ ${#devs[@]} -gt 1 ] && die 2 "several devices, use -s: ${devs[*]}"
    SERIAL="${devs[0]}"
fi
ADB() { adb -s "$SERIAL" "$@"; }

ADB shell mkdir -p "$REMOTE"
for t in kernel_memory_posix memory_layout_posix; do
    b="$HERE/build/android-t-$t"
    cmake -S "$HERE/xboxrecomp/tests/$t" -B "$b" -G Ninja \
        -DCMAKE_TOOLCHAIN_FILE="$TC" -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-30 \
        -DXBOXRECOMP_VULKAN=ON -DCMAKE_PREFIX_PATH="$DEPS" -DCMAKE_FIND_ROOT_PATH="$DEPS" \
        >/dev/null || die 1 "$t: configure failed"
    cmake --build "$b" -j 3 --target "${t}_test" >/dev/null || die 1 "$t: build failed"
    ADB push "$b/${t}_test" "$REMOTE/" >/dev/null
done
ADB push "$DEPS/lib/libSDL2.so" "$REMOTE/" >/dev/null
[ -f "$HERE/roms/game/default.xbe" ] && ADB push "$HERE/roms/game/default.xbe" "$REMOTE/" >/dev/null

rc=0
echo "[test-runtime] page size: $(ADB shell getconf PAGESIZE | tr -d '\r')"
ADB shell "cd $REMOTE && LD_LIBRARY_PATH=. ./kernel_memory_posix_test" || rc=1
if [ -f "$HERE/roms/game/default.xbe" ]; then
    ADB shell "cd $REMOTE && LD_LIBRARY_PATH=. ./memory_layout_posix_test default.xbe" || rc=1
else
    echo "[test-runtime] skipped memory_layout_posix (no roms/game/default.xbe)"
fi
[ $rc -eq 0 ] && echo "[test-runtime] PASS" || echo "[test-runtime] FAIL"
exit $rc
