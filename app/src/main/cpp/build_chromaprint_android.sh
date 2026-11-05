#!/usr/bin/env bash
# Rebuilds libs/arm64-v8a/libchromaprint.a from a Chromaprint checkout.
#
# Chromaprint (https://github.com/acoustid/chromaprint) is NOT vendored in
# this repo -- same reasoning as build_essentia_android.sh. Clone it, point
# CHROMAPRINT_SRC at the checkout, and run this script.
#
# Why -DFFT_LIB=kissfft: same reasoning as Essentia's --fft=KISS -- no FFTW
# on Android, and Chromaprint vendors its own KissFFT copy at
# src/3rdparty/kissfft, so no extra native dependency is needed.
#
# Why no FFmpeg: Chromaprint's own audio-file decoding (used by its `fpcalc`
# CLI tool) needs libavcodec/libavformat/libswresample, which have no Android
# build here either (matches Essentia's dropped MonoLoader). We don't need
# any of that: EssentiaExtractor.kt already decodes + resamples the file with
# MediaExtractor/MediaCodec, and we feed that PCM straight into
# chromaprint_feed() ourselves (see EssentiaWrapper.cpp's computeChromaprint).
# BUILD_TOOLS=OFF, so fpcalc itself is never built for Android.
#
# CMAKE_FIND_ROOT_PATH_MODE_INCLUDE=BOTH is required: the NDK toolchain file
# otherwise restricts find_path() to the sysroot only, so it can't see
# KISSFFT_ROOT under the host checkout.

set -euo pipefail

: "${CHROMAPRINT_SRC:?Set CHROMAPRINT_SRC to a local Chromaprint checkout}"
: "${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME, e.g. \$ANDROID_HOME/ndk/27.1.12297006}"

BUILD_DIR="$CHROMAPRINT_SRC/build-android"
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"
cd "$BUILD_DIR"

cmake .. \
    -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI=arm64-v8a \
    -DANDROID_PLATFORM=android-31 \
    -DBUILD_SHARED_LIBS=OFF \
    -DBUILD_TOOLS=OFF \
    -DBUILD_TESTS=OFF \
    -DFFT_LIB=kissfft \
    -DKISSFFT_ROOT="$CHROMAPRINT_SRC/src/3rdparty/kissfft" \
    -DCMAKE_FIND_ROOT_PATH_MODE_INCLUDE=BOTH \
    -DCMAKE_BUILD_TYPE=Release
cmake --build . -j"$(nproc)"

echo
echo "Built: $BUILD_DIR/src/libchromaprint.a"
echo "Copy it to: app/src/main/cpp/libs/arm64-v8a/libchromaprint.a"
echo "Public header (src/chromaprint.h) goes to: app/src/main/cpp/include/chromaprint/chromaprint.h"
