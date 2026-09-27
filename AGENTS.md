# Riffle (Android)

Kotlin + Jetpack Compose Android music player/library app. **Fully
self-contained** — all audio analysis, similarity, clustering, and shuffle
logic is reimplemented natively in Kotlin on-device. There is no runtime
dependency on the `riffle` Python project (sibling directory: `../riffle`)
or any Python interpreter — Android cannot execute that CLI tool or its
native dependencies (Essentia, chromaprint/fpcalc) at runtime, and no
Python-on-Android bridge (Chaquopy, etc.) is in use or planned.

`../riffle` is **reference material only**: its schema, algorithms, and the
calibration lessons learned building it (see below) inform how this app's
own Kotlin logic is designed. Nothing in this app imports, shells out to,
or depends on that repo at build or run time.

## Product vision

Riffle is a **local-first music intelligence app**, not just a player. It
should feel like Artist → Album → Song browsing *plus* Audio identity →
Audio characteristics → Similarity → Clusters → Listening context. Full
screen-by-screen spec: `DESIGN2.md`. Visual design system (colors,
typography, spacing, component specs): `DESIGN.md`. Read both before
building a new screen — don't improvise a screen `DESIGN2.md` already
specifies.

Seven priority screens per `DESIGN2.md`: Home, Browse, Now Playing, Smart
Shuffle, Audio Clusters, Duplicate Review, Library Health.

## Reference: `../riffle`'s data model and algorithms

The Python project at `../riffle` worked out the data model and scoring
logic this app's own Kotlin implementation should mirror (not import). Its
SQLite schema documents what fields matter and why:

- **Library**: `track`, `audio_content` (dedup-aware — multiple `track` rows
  can share one `audio_content`), tags (artist/title/album/genre)
- **Audio features** (`audio_features`): bpm, key/scale, loudness_lufs,
  danceability, energy, spectral_centroid, onset_rate, dynamic_complexity,
  dissonance, zcr, mfcc_mean (13-dim, packed as float64 BLOB)
- **Identity** (`musicbrainz_match`): AcoustID/MusicBrainz recording,
  artists (JSON array — Bollywood/collab tracks have multiple), release
- **Duplicates** (`dup_group`/`group_content`): exact/near-duplicate
  recordings, with approve/reject/apply workflow — never destructive by
  default (matches `DESIGN2.md` Screen 13's "never show deletion as
  default")
- **Clusters** (`cluster_run`/`cluster_centroid`/`cluster_assignment`):
  k-means over normalized audio features, auto-labeled (e.g. "Upbeat
  Energetic Major Bright") from library-relative percentiles, not fixed
  thresholds — see `riffle/cluster.py:_library_terciles`
- **Similarity** (`track_similarity`): directed top-K nearest neighbors per
  track, weighted MFCC/BPM/key/energy distance, library-calibrated (see
  `riffle/similarity.py:compute_bpm_spread`)

Read `../riffle/docs/superpowers/specs/2026-09-26-subsystem-bc-design.md`
§10 for the binding contract rules this app's Kotlin logic should also
follow (missing-data handling, quarantine exclusion, directed similarity
storage, library-relative calibration instead of fixed thresholds) — these
were hard-won from a real review, not incidental choices.

**What ports straightforwardly to Kotlin** (pure math/logic, no native
deps, small files):
- Similarity scoring (`riffle/similarity.py`) — MFCC cosine distance, key
  distance via circle of fifths, missing-data-aware weighted scoring,
  `compute_bpm_spread` (IQR-based library-relative BPM scale)
- Clustering (`riffle/cluster.py`) — k-means is available via on-device ML
  libs or a small custom implementation at this data size; the
  library-relative labeling logic (`_library_terciles`) is trivial to port
- Smart shuffle (`riffle/shuffle.py`) — greedy nearest-neighbor chain with
  hard/soft constraints, duplicate-sibling exclusion

**The hard, unresolved part: on-device audio feature extraction.** The
Python side leans entirely on Essentia (BPM, key, loudness, MFCC,
danceability, spectral centroid, onset rate, dynamic complexity, dissonance,
ZCR) — a C++ library with no Android build. There is no drop-in Kotlin/Java
equivalent covering all of that. This needs its own decision (candidates:
TarsosDSP for a subset of these, Android's own `MediaCodec`/`Visualizer`
APIs for lower-level signal access plus custom DSP, or building Essentia
for Android via NDK) before feature extraction can be built — don't pick
one silently, surface it.

Same gap applies to Subsystem A (dedup): the Python fingerprinting path
uses chromaprint/`fpcalc`, also unavailable natively on Android.

## Current implementation state (as of repo init)

UI scaffold only. No commits yet (`git log` is empty — this is truly step
zero). No data layer, no persistence, no playback engine, no permissions
declared. What exists:

- Navigation: 4-tab bottom nav (Home/Browse/Shuffle/Library) via
  `navigation-compose`, `MainActivity.kt`
- Screens with static/mock content: `HomeScreen`, `BrowseScreen`,
  `ShuffleScreen`, `LibraryScreen`, `PlayerScreen`, `MiniPlayer`
- Theme: `ui/theme/{Color,Theme,Type}.kt`, matches `DESIGN.md` tokens
- No `AndroidManifest` permissions (no storage/media read yet)
- No Media3/ExoPlayer wiring — `PlayerScreen`/`MiniPlayer` are UI shells
- No Room/SQLite bridge to the backend DB

## Tech stack

- Kotlin 2.2.10, Compose BOM 2026.02.01, AGP 9.4.1
- Material 3 (`androidx.compose.material3`), Navigation Compose 2.7.7
- `minSdk 31`, `targetSdk`/`compileSdk 37`
- Package: `com.amit152116.riffle`

## Conventions

- One screen per feature package: `ui/<feature>/<Feature>Screen.kt` (see
  `ui/home/HomeScreen.kt`, `ui/browse/BrowseScreen.kt`). Follow this
  pattern for new screens rather than nesting under `ui/theme` or flat in
  `ui/`.
- `Screen` sealed class in `MainActivity.kt` is the route registry for
  bottom-nav destinations; screens reached by navigation (Artist, Album,
  Now Playing, Similar Tracks, Smart Shuffle Setup, etc.) go through
  `NavHost`, not the bottom nav list.
- No test infrastructure beyond the default `ExampleUnitTest`/
  `ExampleInstrumentedTest` stubs yet — don't treat those as a pattern to
  follow, they're unedited template files.

## Build & test

```
./gradlew assembleDebug     # build debug APK
./gradlew test              # unit tests
./gradlew connectedAndroidTest  # instrumented tests (needs device/emulator)
./gradlew lint
```

## Working agreements

- This is a fresh repo with no commits — the first commit is a real
  decision point (what counts as "initial scaffold" vs. first feature).
  Ask before assuming.
- Don't wire on-device feature extraction without resolving the DSP-library
  question above first — it determines what's even measurable, which
  shapes the data layer and every screen that displays audio features.
- Never add a Python runtime, Chaquopy, or any dependency on `../riffle`
  running or being present on the device. If a Python detail is unclear,
  read the source in `../riffle` for reference, then port the logic —
  don't shell out to it.
- `DESIGN2.md` is prescriptive about screen content and flow (it lists
  exact sections, actions, and metadata chips per screen) — build to it
  rather than inventing alternative layouts, but flag if something in it
  assumes a feature (e.g. an audio metric) this app can't actually compute
  on-device yet.
# Reference Repositories\n\n* RetroMusicPlayer: \n
# Reference Repositories

* RetroMusicPlayer: `/home/amit_152116/myDisk/GithubRepositories/RetroMusicPlayer`
