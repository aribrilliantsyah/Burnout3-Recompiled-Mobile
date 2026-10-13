#!/usr/bin/env bash
# Build the Android native dependencies for arm64-v8a into .work/android-deps:
#   SDL2     shared (libSDL2.so) + headers + CMake config, and SDL's Java
#            glue (android-project/.../org/libsdl/app) for the Gradle app
#   glslang  static, no optimizer (SPIRV-Tools not needed at run time)
#
#   scripts/android/deps.sh [--clean]
#
# Environment:
#   ANDROID_HOME   SDK root (holds ndk/<NDK_VERSION>)
#   NDK_VERSION    default 28.2.13676358 (r28c)
#   SDL_VERSION    default 2.32.10
#   GLSLANG_TAG    default 15.4.0
#   JOBS           parallel jobs (default 4)
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
: "${ANDROID_HOME:=${ANDROID_SDK_ROOT:-$HOME/Documents/Repos/Android/SDK}}"
NDK_VERSION="${NDK_VERSION:-28.2.13676358}"
SDL_VERSION="${SDL_VERSION:-2.32.10}"
GLSLANG_TAG="${GLSLANG_TAG:-15.4.0}"
JOBS="${JOBS:-4}"
ABI=arm64-v8a
API=30

NDK="$ANDROID_HOME/ndk/$NDK_VERSION"
TOOLCHAIN="$NDK/build/cmake/android.toolchain.cmake"
WORK="$HERE/.work/android-deps"
SRC="$WORK/src"
PREFIX="$WORK/$ABI"

log() { echo "[deps] $*"; }
die() { echo "[deps] error: $*" >&2; exit 1; }

[ -f "$TOOLCHAIN" ] || die "no NDK at $NDK (set ANDROID_HOME / NDK_VERSION)"
command -v cmake >/dev/null || die "cmake not found"
command -v ninja >/dev/null || die "ninja not found"

if [ "${1:-}" = "--clean" ]; then
    log "removing $WORK/build and $PREFIX"
    rm -rf "$WORK/build" "$PREFIX"
fi
mkdir -p "$SRC" "$PREFIX"

fetch() {   # fetch <url> <dir name>
    local url="$1" dir="$2" tgz="$SRC/$2.tar.gz"
    [ -d "$SRC/$dir" ] && return 0
    log "download $url"
    curl -fL --retry 3 -o "$tgz.part" "$url"
    mv "$tgz.part" "$tgz"
    mkdir -p "$SRC/$dir"
    tar -xzf "$tgz" -C "$SRC/$dir" --strip-components=1
}

android_cmake() {   # android_cmake <src> <build> [cmake args...]
    local src="$1" bld="$2"; shift 2
    cmake -S "$src" -B "$bld" -G Ninja \
        -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN" \
        -DANDROID_ABI=$ABI -DANDROID_PLATFORM=android-$API \
        -DCMAKE_BUILD_TYPE=Release \
        -DCMAKE_INSTALL_PREFIX="$PREFIX" \
        -DCMAKE_FIND_ROOT_PATH="$PREFIX" \
        "$@"
    cmake --build "$bld" -j "$JOBS"
    cmake --install "$bld"
}

fetch "https://github.com/libsdl-org/SDL/releases/download/release-$SDL_VERSION/SDL2-$SDL_VERSION.tar.gz" "SDL2-$SDL_VERSION"
fetch "https://github.com/KhronosGroup/glslang/archive/refs/tags/$GLSLANG_TAG.tar.gz" "glslang-$GLSLANG_TAG"

if [ ! -f "$PREFIX/lib/libSDL2.so" ]; then
    log "SDL2 $SDL_VERSION"
    android_cmake "$SRC/SDL2-$SDL_VERSION" "$WORK/build/sdl2" \
        -DSDL_SHARED=ON -DSDL_STATIC=OFF -DSDL_TEST=OFF
fi
# SDL's Java side, pinned to the same version as the .so.
JAVA_DST="$WORK/sdl-java"
if [ ! -d "$JAVA_DST/org/libsdl/app" ]; then
    mkdir -p "$JAVA_DST"
    cp -r "$SRC/SDL2-$SDL_VERSION/android-project/app/src/main/java/org" "$JAVA_DST/"
fi

if [ ! -f "$PREFIX/lib/libglslang.a" ]; then
    log "glslang $GLSLANG_TAG"
    android_cmake "$SRC/glslang-$GLSLANG_TAG" "$WORK/build/glslang" \
        -DENABLE_OPT=OFF -DENABLE_GLSLANG_BINARIES=OFF -DGLSLANG_TESTS=OFF \
        -DENABLE_HLSL=OFF -DENABLE_SPVREMAPPER=OFF -DBUILD_SHARED_LIBS=OFF \
        -DGLSLANG_ENABLE_INSTALL=ON
fi

[ -f "$PREFIX/lib/libSDL2.so" ] || die "libSDL2.so missing after build"
[ -f "$PREFIX/lib/libglslang.a" ] || die "libglslang.a missing after build"
log "ok: $PREFIX"
