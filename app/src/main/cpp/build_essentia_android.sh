#!/usr/bin/env bash
# Rebuilds libs/arm64-v8a/libessentia.a from an Essentia checkout.
#
# Essentia itself is NOT vendored in this repo (see AGENTS.md: no runtime or
# build-time dependency on external Python/native projects checked into this
# tree). Point ESSENTIA_SRC at a local clone of https://github.com/MTG/essentia
# and run this script, then copy the resulting .a in.
#
# Why --fft=KISS: the default FFTW backend needs libfftw3f, which has no
# Android build here; KISS FFT is vendored inside Essentia's own src/3rdparty,
# so it cross-compiles with no extra native dependency. Without this flag,
# waf silently drops the FFT algorithm (and everything that depends on it --
# Spectrum, MFCC, RhythmExtractor2013, KeyExtractor, Dissonance, ...).
#
# Why --lightweight= (empty): skips libav/ffmpeg, libsamplerate, taglib, yaml
# and chromaprint entirely (none of them have an Android build here either).
# This drops MonoLoader/AudioLoader/Resample and Chromaprinter -- audio
# decode + resampling is done on the Kotlin side instead (MediaExtractor +
# MediaCodec + a linear resampler, see EssentiaExtractor.kt), and chromaprint
# fingerprinting is deferred (AudioFeatures.fingerprintAlgorithm == -1 until
# it's cross-built and linked in a later pass).
#
# --ignore-algos=LPC matches Essentia's own build_android.sh; LPC doesn't
# build cleanly for this target.

set -euo pipefail

: "${ESSENTIA_SRC:?Set ESSENTIA_SRC to a local Essentia checkout}"
: "${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME, e.g. \$ANDROID_HOME/ndk/27.1.12297006}"

NDK_BIN="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin"
export CC="$NDK_BIN/aarch64-linux-android31-clang"   # 31 = this app's minSdk
export CXX="$NDK_BIN/aarch64-linux-android31-clang++"
export AR="$NDK_BIN/llvm-ar"

cd "$ESSENTIA_SRC"

# Thread-safety patch (see essentia-thread-safety.patch): Essentia's global Logger queue and Network::lastCreated
# are not thread-safe, and this app runs several extractions concurrently. Applied once; skipped if already applied.
PATCH="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/essentia-thread-safety.patch"
if git apply --check "$PATCH" 2>/dev/null; then git apply "$PATCH"; else echo "patch already applied (or not applicable)"; fi

# Compile all logging/debug hooks out. CMakeLists.txt defines the same macro for our wrapper so both sides agree
# on the layout of streaming::Algorithm (it has a member that only exists when DEBUGGING_ENABLED is set).
export CXXFLAGS="${CXXFLAGS:-} -DDEBUGGING_ENABLED=0"
export CFLAGS="${CFLAGS:-} -DDEBUGGING_ENABLED=0"
./waf configure \
    --cross-compile-android \
    --build-static \
    --lightweight= \
    --fft=KISS \
    --ignore-algos=LPC \
    --prefix=/tmp/essentia-android-install
./waf -j"$(nproc)"

echo
echo "Built: $ESSENTIA_SRC/build/src/libessentia.a"
echo "Copy it to: app/src/main/cpp/libs/arm64-v8a/libessentia.a"
