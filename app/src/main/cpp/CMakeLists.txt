cmake_minimum_required(VERSION 3.22.1)

project("riffle_dsp")

set(CMAKE_CXX_STANDARD 17)

# Prebuilt Essentia library
add_library(essentia STATIC IMPORTED)
set_target_properties(essentia PROPERTIES IMPORTED_LOCATION
    ${CMAKE_CURRENT_SOURCE_DIR}/libs/${ANDROID_ABI}/libessentia.a)

# Prebuilt Chromaprint library (cross-compiled with -DFFT_LIB=kissfft, no
# ffmpeg/libswresample: we feed it PCM we've already decoded ourselves).
add_library(chromaprint STATIC IMPORTED)
set_target_properties(chromaprint PROPERTIES IMPORTED_LOCATION
    ${CMAKE_CURRENT_SOURCE_DIR}/libs/${ANDROID_ABI}/libchromaprint.a)

# We create our shared library
add_library(
        riffle_dsp
        SHARED
        EssentiaWrapper.cpp
        speex_resampler/resample.c
        sha256/sha256.c)

# Vendored speexdsp resampler (see speex_resampler/README.md): OUTSIDE_SPEEX is
# its own supported standalone-build path, no autotools/config.h needed. Applied
# to the whole target, not just resample.c: EssentiaWrapper.cpp includes
# speex_resampler.h too, and it branches on OUTSIDE_SPEEX the same way.
target_compile_definitions(riffle_dsp PRIVATE
    DEBUGGING_ENABLED=0   # must match how libessentia.a was built (see build_essentia_android.sh)
    OUTSIDE_SPEEX
    FLOATING_POINT
    RANDOM_PREFIX=riffle)

# Optimise our own C/C++ (speex resampler, wrapper) in every variant: without an explicit -O the Android
# debug variant builds them at -O0, which made the 48 kHz -> 44.1 kHz resample ~10x slower.
target_compile_options(riffle_dsp PRIVATE -O3)

# Include headers for Essentia, Chromaprint, the speex resampler, and sha256
target_include_directories(riffle_dsp PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/include/essentia
    ${CMAKE_CURRENT_SOURCE_DIR}/include/chromaprint
    ${CMAKE_CURRENT_SOURCE_DIR}/speex_resampler
    ${CMAKE_CURRENT_SOURCE_DIR}/sha256
    /usr/include/eigen3)

find_library(
        log-lib
        log)

# Link against essentia, chromaprint, and android log
target_link_libraries(
        riffle_dsp
        essentia
        chromaprint
        ${log-lib})

# Ensure 16 KB page size alignment for Android 15+ compatibility
target_link_options(riffle_dsp PRIVATE "-Wl,-z,max-page-size=16384")
