Vendored from https://github.com/B-Con/crypto-algorithms (public domain, see
UPSTREAM_README.md) — `sha256.c`/`sha256.h` only, one change: wrapped
`sha256.h`'s declarations in `extern "C" { ... }` so a C++ translation unit
(EssentiaWrapper.cpp) linking against the C-compiled `sha256.c` doesn't hit
a C++ name-mangling mismatch at link time.

Used for one thing: hashing the JSON serialization of `kExtractionConfig`
(EssentiaWrapper.cpp) into `AudioFeatures.configHash`, matching
`../riffle/riffle/features.py::extraction_config_hash()` — same SHA-256 of
the same sorted-key JSON shape, so a config change is detectable the same
way riffle detects one (compare the hash, not the individual fields).
