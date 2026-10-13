#!/usr/bin/env bash
# Build Burnout 3 for Android and drive it on a connected phone.
#
#   scripts/android/b3x.sh [-s SERIAL] [--release] <cmd>...
#
# Commands run in the order given:
#   deps       build SDL2 + glslang for arm64-v8a (scripts/android/deps.sh)
#   native     CMake/NDK build of libmain.so, copied with libSDL2.so into jniLibs
#   apk        Gradle assembleDebug (assembleRelease with --release)
#   install    adb install -r the APK
#   push-game  dev only: copy roms/game/ to the app's files/game/ (skips
#              files already there with the same size)
#   env        push ./b3x_env.txt to the app's files directory
#   run        start the app
#   log        follow logcat for the app (Ctrl-C to stop)
#   stop       force-stop the app
#   all        deps native apk install run log
#
# Device: -s SERIAL, else $ANDROID_SERIAL, else the only attached device.
#
# Environment:
#   ANDROID_HOME   SDK root          NDK_VERSION  default 28.2.13676358
#   JOBS           build jobs (3)    NFSU2_GEN_DIR lifted C (default .work/gen)
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
: "${ANDROID_HOME:=${ANDROID_SDK_ROOT:-$HOME/Documents/Repos/Android/SDK}}"
export ANDROID_HOME
NDK_VERSION="${NDK_VERSION:-28.2.13676358}"
JOBS="${JOBS:-3}"
GEN="${NFSU2_GEN_DIR:-$HERE/.work/gen}"
PKG=com.ryuna.b3x
ACTIVITY=.B3xActivity
ABI=arm64-v8a
DEPS="$HERE/.work/android-deps/$ABI"
BUILD="$HERE/build/android"
JNILIBS="$HERE/android/app/src/main/jniLibs/$ABI"
DEVICE_FILES="/sdcard/Android/data/$PKG/files"
VARIANT=debug
SERIAL="${ANDROID_SERIAL:-}"

say() { echo "[b3x] $*"; }
die() { local code="$1"; shift; echo "[b3x] error: $*" >&2; exit "$code"; }

usage() { sed -n '2,24p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit "${1:-0}"; }

pick_device() {
    [ -n "${ADB_READY:-}" ] && return 0
    command -v adb >/dev/null || die 2 "adb not found"
    if [ -z "$SERIAL" ]; then
        mapfile -t devs < <(adb devices | awk 'NR>1 && $2=="device" {print $1}')
        case ${#devs[@]} in
            0) die 2 "no adb device (enable USB debugging and accept the prompt)" ;;
            1) SERIAL="${devs[0]}" ;;
            *) die 2 "several devices, pick one with -s: ${devs[*]}" ;;
        esac
    fi
    adb -s "$SERIAL" get-state >/dev/null 2>&1 || die 2 "device $SERIAL not available"
    ADB_READY=1
    say "device $SERIAL ($(adb -s "$SERIAL" shell getprop ro.product.model | tr -d '\r'))"
}
ADB() { adb -s "$SERIAL" "$@"; }

cmd_deps() {
    if [ -f "$DEPS/lib/libSDL2.so" ] && [ -f "$DEPS/lib/libglslang.a" ]; then
        say "deps: present"
    else
        NDK_VERSION="$NDK_VERSION" "$HERE/scripts/android/deps.sh"
    fi
}

cmd_native() {
    [ -f "$GEN/recomp_funcs.h" ] || die 1 "no lifted C in $GEN (run tools/regen.sh)"
    [ -f "$DEPS/lib/libSDL2.so" ] || cmd_deps
    local tc="$ANDROID_HOME/ndk/$NDK_VERSION/build/cmake/android.toolchain.cmake"
    [ -f "$tc" ] || die 1 "no NDK toolchain at $tc"
    if [ ! -f "$BUILD/build.ninja" ]; then
        cmake -S "$HERE" -B "$BUILD" -G Ninja \
            -DCMAKE_TOOLCHAIN_FILE="$tc" -DANDROID_ABI=$ABI -DANDROID_PLATFORM=android-30 \
            -DCMAKE_BUILD_TYPE=RelWithDebInfo \
            -DNFSU2_VULKAN=ON -DNFSU2_GEN_DIR="$GEN" \
            -DCMAKE_PREFIX_PATH="$DEPS" -DCMAKE_FIND_ROOT_PATH="$DEPS"
    fi
    cmake --build "$BUILD" -j "$JOBS"
    mkdir -p "$JNILIBS"
    # Debug info stays in build/android/libmain.so (for ndk-stack and
    # tombstones); the APK gets stripped copies.
    local strip="$ANDROID_HOME/ndk/$NDK_VERSION/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip"
    "$strip" --strip-debug -o "$JNILIBS/libmain.so" "$BUILD/libmain.so"
    "$strip" --strip-debug -o "$JNILIBS/libSDL2.so" "$DEPS/lib/libSDL2.so"
    say "native: $(du -h "$JNILIBS/libmain.so" | cut -f1) libmain.so"
}

copy_sdl_java() {
    local src="$HERE/.work/android-deps/sdl-java/org"
    local dst="$HERE/android/app/src/main/java/org"
    [ -d "$dst/libsdl/app" ] && return 0
    [ -d "$src" ] || cmd_deps
    cp -r "$src" "$dst"
}

cmd_apk() {
    [ -f "$JNILIBS/libmain.so" ] || die 1 "no $JNILIBS/libmain.so (run native first)"
    copy_sdl_java
    local task=assembleDebug
    [ "$VARIANT" = release ] && task=assembleRelease
    (cd "$HERE/android" && ./gradlew --console=plain -q "$task")
    APK="$HERE/android/app/build/outputs/apk/$VARIANT/app-$VARIANT.apk"
    [ -f "$APK" ] || die 1 "no APK at $APK"
    say "apk: $APK"
}

apk_path() { echo "$HERE/android/app/build/outputs/apk/$VARIANT/app-$VARIANT.apk"; }

cmd_install() {
    pick_device
    local apk; apk="$(apk_path)"
    [ -f "$apk" ] || die 1 "no APK at $apk (run apk first)"
    ADB install -r "$apk"
}

cmd_push_game() {
    pick_device
    local src="$HERE/roms/game"
    [ -f "$src/default.xbe" ] || die 1 "no $src/default.xbe (extract the disc into roms/game)"
    ADB shell mkdir -p "$DEVICE_FILES/game"
    # Remote sizes in one listing; push only what is missing or different.
    local list; list="$(mktemp)"
    ADB shell "cd '$DEVICE_FILES/game' && find . -type f -exec stat -c '%s %n' {} +" \
        2>/dev/null | tr -d '\r' > "$list" || true
    local n=0 skipped=0
    while IFS= read -r -d '' f; do
        local rel="${f#"$src"/}"
        local size; size=$(stat -c %s "$f")
        if grep -qxF "$size ./$rel" "$list"; then
            skipped=$((skipped + 1)); continue
        fi
        # </dev/null: adb reads stdin, which here is the find list.
        ADB shell mkdir -p "$DEVICE_FILES/game/$(dirname "$rel")" </dev/null >/dev/null
        ADB push "$f" "$DEVICE_FILES/game/$rel" </dev/null >/dev/null
        n=$((n + 1))
    done < <(find "$src" \( -path "$src/UDATA" -o -path "$src/TDATA" \) -prune -o \
                 -type f ! -name '*_analysis.json' -print0)
    rm -f "$list"
    # adb pushes as the shell user, mode 644: the app (same ext_data_rw
    # group) could read but not write, so the title failed to create its
    # save metadata (STATUS_ACCESS_DENIED) and quit. Group-writable fixes it.
    # UDATA/TDATA (saves made by a desktop run) stay on the host.
    ADB shell chmod -R g+w "$DEVICE_FILES/game" </dev/null
    say "push-game: $n pushed, $skipped already there"
}

cmd_env() {
    pick_device
    [ -f "$HERE/b3x_env.txt" ] || die 1 "no b3x_env.txt in $HERE"
    ADB shell mkdir -p "$DEVICE_FILES"
    ADB push "$HERE/b3x_env.txt" "$DEVICE_FILES/b3x_env.txt" >/dev/null
}

cmd_run() {
    pick_device
    ADB logcat -c || true
    ADB shell am start -n "$PKG/$ACTIVITY" >/dev/null
}

cmd_log() {
    pick_device
    ADB logcat -v time b3x:V SDL:V SDL/APP:V DEBUG:V libc:V AndroidRuntime:E vulkan:V '*:S'
}

cmd_stop() {
    pick_device
    ADB shell am force-stop "$PKG"
}

[ $# -gt 0 ] || usage 1
CMDS=()
while [ $# -gt 0 ]; do
    case "$1" in
        -s) SERIAL="$2"; shift 2 ;;
        --release) VARIANT=release; shift ;;
        -h|--help) usage 0 ;;
        all) CMDS+=(deps native apk install run log); shift ;;
        deps|native|apk|install|push-game|env|run|log|stop) CMDS+=("$1"); shift ;;
        *) echo "[b3x] unknown command: $1" >&2; usage 1 ;;
    esac
done

for c in "${CMDS[@]}"; do
    "cmd_${c//-/_}"
    say "$c: ok"
done
