Vendored from https://github.com/xiph/speexdsp (BSD license, see COPYING),
`libspeexdsp/resample.c` + `libspeexdsp/arch.h` + `include/speex/speex_resampler.h`
(flattened into one directory, header renamed from `speex/speex_resampler.h`).

Compiled standalone via speexdsp's own supported `OUTSIDE_SPEEX` vendoring path
(see the `#ifdef OUTSIDE_SPEEX` branches in resample.c/arch.h/speex_resampler.h) —
no autotools, no rest of libspeexdsp, no config.h needed. Built with:

- `OUTSIDE_SPEEX` — use the standalone code path
- `FLOATING_POINT` — float API (matches Essentia's `Real` = float)
- `RANDOM_PREFIX=riffle` — required by speex_resampler.h; renames the exported
  symbols (`riffle_resampler_init` etc.) so they can never clash with a real
  libspeex/libspeexdsp if one ever ends up linked into the same binary.

Replaces the hand-rolled windowed-sinc FIR resampler that used to live in
EssentiaExtractor.kt: same anti-aliasing goal (band-limit before decimating),
but a real polyphase implementation instead of a fixed-tap heuristic.
