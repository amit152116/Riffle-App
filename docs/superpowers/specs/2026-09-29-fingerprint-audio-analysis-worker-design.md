# Background Fingerprint + Audio Features Analysis Worker

**Status:** Decided (this conversation), not yet implemented.

## Problem

The native DSP extractor (`dsp/EssentiaExtractor.kt`, backed by
`EssentiaWrapper.cpp`) and the Room tables it feeds (`AnalysisDao`/
`AudioFeatures`, `FingerprintDao`/`Fingerprint`) all exist and compile, but
nothing calls the extractor for real library tracks. `AudioFeatures` and
`Fingerprint` rows are never written outside instrumented tests, and
`SongDetailSheet`'s new "Audio Features" section (wired this session) always
shows "Not analyzed yet." This spec designs the background worker that
closes that gap: walk the library's `AudioContent` rows, run the native
extractor, persist both tables. MusicBrainz identity lookup, near-duplicate
grouping/quarantine, and Tier 2 ML (see
`2026-09-28-tier2-ml-analysis-design.md`) are explicitly out of scope — this
is Tier 1 DSP only.

## Prerequisite fix (already applied this session)

`EssentiaExtractor.extractFile(path: String)` used
`MediaExtractor().setDataSource(path)` — the plain-string overload, which
does not reliably decode `content://` URIs. Every `Track.path` in this app
is a MediaStore `content://` URI, not a filesystem path (see
`LibraryScanner`/`AudioHasher`, which already uses the context-aware
overload for exactly this reason). Added `EssentiaExtractor.extractContent
(context: Context, uri: Uri)`, backed by a new `decodeToMonoPcm(context,
uri)` overload using `MediaExtractor.setDataSource(context, uri, null)`.
The original `extractFile(path: String)` is unchanged and stays in place
because `EssentiaExtractorTest` decodes real WAV fixtures from local
storage by path.

## Decision: one AudioContent-keyed pass, not per-track

Ported reference: `../riffle/riffle/fingerprint.py:fingerprint_pending` and
`riffle/features.py:feature_scan` run as two separate passes because
`fpcalc` and `essentia` are separate CLI processes on desktop. On Android,
`EssentiaExtractor.extract()`/`extractContent()` already returns **one**
`AudioFeatures` object per JNI call containing both the 13 Essentia metrics
*and* the chromaprint fingerprint (`fingerprint`, `fingerprintAlgorithm`,
`configHash`). So there is one DSP pass per `AudioContent`, not two, and it
writes both `AnalysisDao.insertAudioFeatures` and
`FingerprintDao.insertFingerprint` from a single extraction.

Follows `fingerprint.py`'s model (`AudioContent`-level), not `features.py`'s
(`Track`-level): `features.py` re-processes every `Track` row independently
and relies on `ON CONFLICT DO UPDATE` to tolerate redundant work when
several tracks share one `AudioContent`. That's fine for a one-off desktop
CLI batch; it's wasteful for a mobile worker running real CPU-bound DSP. The
Android pass is keyed on `AudioContent.id`, so two `Track`s sharing one
`AudioContent` (dedup-aware model) get analyzed exactly once.

## Decision: track/URI selection for a given AudioContent

Mirrors `fingerprint_pending`'s
`SELECT ac.id, MIN(t.path) FROM audio_content ac JOIN track t ON ... AND
t.present = 1 GROUP BY ac.id`: pick the alphabetically-smallest path among
*present* sibling tracks. Arbitrary but deterministic — by construction
every `Track` sharing an `AudioContent` has the same audio-content hash
under the selected hash method (`STREAMHASH` or the `WHOLE_FILE` fallback —
see `AudioHasher`), so which copy gets decoded doesn't affect the result,
and `present = 1` already
covers both "file went missing" (`AbsentReason.MISSING`) and "quarantined"
(`AbsentReason.QUARANTINED`) uniformly, the same way it does in `riffle`
(quarantine there is just `UPDATE track SET present = 0, absent_reason =
'quarantined'` — no separate exclusion mechanism to reimplement).

New `LibraryDao` method:

```kotlin
@Query("SELECT MIN(path) FROM Track WHERE audioContentId = :audioContentId AND present = 1")
suspend fun getAnyPresentTrackPath(audioContentId: Long): String?
```

If this returns null (every sibling went absent between the pending query
and now — a real but narrow race), the worker logs an `IngestError` for
that `AudioContent` and moves on rather than crashing.

Explicitly out of scope here: `riffle/rank.py`'s keeper-selection
(`rank_key`: lossless > bitrate > tag completeness > duration > mtime) and
`riffle/quarantine.py`'s apply/undo. Those decide which duplicate **file**
survives on disk for the human-facing Duplicate Review screen — a different
concern from picking any-present-copy for DSP input, and they depend on
`dup_group`/`group_member` tables that don't exist in the Kotlin schema yet.

## Decision: pending-query and versioning

One shared version/config identity per native build, not two independently
versioned tools (Python has separate `fpcalc_version()` and Essentia
version because they're separate binaries; Android's `riffle_dsp` bundles
both statically as one library). Add to `EssentiaExtractor`'s companion:

```kotlin
const val EXTRACTOR_VERSION = "essentia-lightweight-1"
```

bumped manually whenever the native build changes (algorithm set, config).
Used as both `AudioFeatures.extractorVersion` and
`Fingerprint.analyzerVersion`; `AudioFeatures.configHash` and
`Fingerprint.configHash` both come directly from the extraction result's
`configHash` field (already computed natively, one value per build).

New `LibraryDao` method (returns `AudioContent` — the entity it owns —
even though its `WHERE` inspects `AnalysisEntities`/`FingerprintEntities`
tables; Room DAOs aren't required to stay single-table, and this keeps the
"pending" query's return type where the caller expects it):

```kotlin
@Query(
    """
    SELECT ac.* FROM AudioContent ac
    WHERE EXISTS (SELECT 1 FROM Track t WHERE t.audioContentId = ac.id AND t.present = 1)
    AND (
        NOT EXISTS (
            SELECT 1 FROM AudioFeatures af
            WHERE af.audioContentId = ac.id AND af.extractorVersion = :extractorVersion AND af.configHash = :configHash
        )
        OR NOT EXISTS (
            SELECT 1 FROM Fingerprint f
            WHERE f.audioContentId = ac.id AND f.analyzerVersion = :extractorVersion
              AND f.configHash = :configHash AND f.purpose = 'CANONICAL'
        )
    )
    """
)
suspend fun getAudioContentNeedingAnalysis(extractorVersion: String, configHash: String): List<AudioContent>
```

A content-hash change (edited/replaced file) already produces a brand-new
`AudioContent` row with no `AudioFeatures`/`Fingerprint` yet — this query
picks it up automatically, no separate "file changed" branch needed. Only
an `EXTRACTOR_VERSION`/`configHash` bump forces re-analysis of otherwise
unchanged content, matching `riffle`'s `config_hash` re-analysis trigger.

## Decision: worker shape — single chained CoroutineWorker, no per-track jobs, no manual batching

Rejected: one `WorkRequest` per `AudioContent`. WorkManager persists and
schedules each enqueued request individually; for a several-hundred-track
library that's hundreds of scheduling entries for work that gains nothing
from being independently retryable — `LibraryHashWorker`'s existing shape
(one worker, internal loop) already gets per-item resilience for free (see
below), and WorkManager is meant for coarse-grained deferred work, not a
per-item scheduler.

Rejected: manual "claim a batch of N, commit, re-enqueue self for the next
batch" chunking inside the worker. `hashPendingTracks()` — the one shipped
precedent — is a plain unbounded loop, and it's already checkpointed at the
row level: each track's DB write happens inside the loop, and a re-run only
re-queries rows still missing a result (`getTracksNeedingHash()`). If
WorkManager stops the worker mid-run (execution-window timeout, Doze,
process death), the next attempt just picks up wherever the DB says work is
still pending — the same self-healing property manual batching would add,
without a hand-rolled "more pending? continue" state machine, and without
introducing a shape inconsistent with the one existing worker in this
codebase. If real execution-window kills show up in testing/telemetry
later, that's a targeted follow-up, not a day-one requirement.

Decided shape: a new `AnalysisWorker : CoroutineWorker`, same structure as
`LibraryHashWorker`:

```kotlin
class AnalysisWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        val container = AppContainer.getInstance(applicationContext)
        val result = container.libraryScanner.analyzePendingAudioContent()
        log.w("Analysis pass finished: analyzed=${result.analyzed} failed=${result.failed}")
        Result.success()
    } catch (e: Exception) {
        log.e("Analysis pass failed unexpectedly", e)
        Result.retry()
    }
}
```

Two distinct failure levels, not to be conflated: a single track's DSP
failure is caught *inside* `analyzePendingAudioContent()`'s loop, logged as
an `IngestError`, and the loop continues — that path never throws out to
the worker, and the pass still ends in `Result.success()`. `Result.retry()`
is only for something breaking the pass itself (e.g. the database becoming
unreachable) — the same distinction `LibraryHashWorker` already draws.

`analyzePendingAudioContent()` lives on `LibraryScanner` (its class doc
already frames it as a sequence of independent passes — scan, hash, and now
analyze — each not knowing who calls it):

```kotlin
suspend fun analyzePendingAudioContent(): AnalysisResult = withContext(Dispatchers.IO) {
    var analyzed = 0
    var failed = 0
    val extractorVersion = EssentiaExtractor.EXTRACTOR_VERSION
    for (audioContent in repository.getAudioContentNeedingAnalysis(extractorVersion, /* configHash */)) {
        val path = repository.getAnyPresentTrackPath(audioContent.id)
        if (path == null) { failed++; continue }  // + IngestError, all siblings went absent
        try {
            val features = extractor.extractContent(context, Uri.parse(path))
            // Room's @Transaction only coordinates calls within one DAO; AudioFeatures and
            // Fingerprint are on two different DAOs, so atomicity across both needs the
            // database-level androidx.room.withTransaction(db) {} (room-ktx, already a
            // dependency), not a per-DAO @Transaction method. Otherwise a crash between
            // the two inserts just means a redundant re-extraction next run (the pending
            // query already treats "either missing" as pending) - correct but wasteful,
            // which withTransaction avoids for free.
            database.withTransaction {
                analysisRepository.insertAudioFeatures(AudioFeatures(audioContentId = audioContent.id, ...))
                fingerprintRepository.insertFingerprint(Fingerprint(audioContentId = audioContent.id, ...))
            }
            analyzed++
        } catch (e: Exception) {
            failed++
            repository.insertIngestError(IngestError(path = path, stage = "analyze", message = e.message, ...))
        }
    }
    AnalysisResult(analyzed, failed)
}
```

`LibraryScanner`'s constructor gains `EssentiaExtractor`, `AnalysisRepository`,
`FingerprintRepository`, and `RiffleDatabase` parameters (currently just
`context`, `repository: LibraryRepository`) — the last one specifically so
`withTransaction` has a database to call through, since it's a
`RoomDatabase` extension, not a per-repository one. All wired the same way
in `AppContainer.libraryScanner`'s `by lazy { }` block.

**Blocking prerequisite, not a deferrable nice-to-have:** `configHash` needs
a real value up front to run the pending query, before any extraction
happens this run — the pending query is not correct without it, so this is
implementation task #1, not something to work around later. `EssentiaExtractor`
needs a `companion` constant or cheap native call (`configHash()` with no
args, independent of `extract()`) that returns the current build's config
hash without decoding audio — add this to `EssentiaWrapper.cpp` alongside
`nativeExtract` if it doesn't already exist as a fixed value computable
without a JNI round-trip. Do not work around this by extracting a throwaway
file just to read its `configHash` off the result. (Today the `configHash`
only comes back attached to an `AudioFeatures` result — flagging this as an
implementation task, not re-deciding the config-hash design above.)

Concurrency: sequential, one track at a time, matching the existing
`for` loop style and `hashPendingTracks()`. No manual thread pool, no
parallel DSP calls — Essentia extraction is CPU-heavy and single-core
device variance is real; if profiling later shows this pass is too slow,
revisit with data, not preemptively.

## Tag-along fix: `hashPendingTracks()` has the same gap, simpler fix

`LibraryScanner.hashPendingTracks()` (pre-existing, already shipped) has
the same class of two-writes-for-one-result gap the `AnalysisWorker`
transaction fix above addresses:

```kotlin
val audioContentId = repository.getAudioContentByHash(identity.audioHash, identity.hashMethod)?.id
    ?: repository.insertAudioContent(AudioContent(...))
repository.updateTrack(track.copy(audioContentId = audioContentId))
```

If `insertAudioContent` succeeds and `updateTrack` doesn't (crash, process
death), the next pass re-decodes the file to hash it again (wasted work),
finds the `AudioContent` row already there via `getAudioContentByHash`, and
just retries `updateTrack` — self-healing, not data corruption, same as the
`AudioFeatures`/`Fingerprint` case. Simpler fix here, though: both writes
already go through the same DAO (`LibraryDao`), so a plain Room
`@Transaction` suffices — no `RiffleDatabase.withTransaction` needed, since
that's only required when the writes span different DAOs.

Add to `LibraryDao`:

```kotlin
@Transaction
suspend fun getOrCreateAudioContentIdAndAssign(track: Track, audioContent: AudioContent): Long {
    val id = getAudioContentByHash(audioContent.audioHash, audioContent.hashMethod)?.id
        ?: insertAudioContent(audioContent)
    updateTrack(track.copy(audioContentId = id))
    return id
}
```

`hashPendingTracks()` calls this one method instead of the three separate
`repository` calls it makes today. `LibraryRepository` gets a matching
one-line pass-through, same as its other DAO delegations.

## Decision: chaining — hash must complete before analysis starts

`AudioFeatures`/`Fingerprint` rows key off `AudioContent.id`, which
`hashPendingTracks()` creates. Analysis can't run meaningfully before hash.
Rather than couple `LibraryHashWorker` to know about `AnalysisWorker`
(backwards — a Tier 1 concern's worker reaching into a orchestration
concern), add a small coordinating entry point,
`work/LibraryIngestPipeline.kt`:

```kotlin
object LibraryIngestPipeline {
    private const val WORK_NAME = "library_ingest_pipeline"

    fun enqueue(context: Context) {
        val hash = OneTimeWorkRequestBuilder<LibraryHashWorker>().build()
        val analyze = OneTimeWorkRequestBuilder<AnalysisWorker>().build()
        WorkManager.getInstance(context)
            .beginUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, hash)
            .then(analyze)
            .enqueue()
    }
}
```

`LibraryHashWorker`/`AnalysisWorker` themselves stay simple, single-purpose
`CoroutineWorker`s with no knowledge of what precedes or follows them
(matches "Design for isolation and clarity" — each answers what it does,
how you use it, what it depends on, independent of the others).
`LibraryViewModel.scanLibrary()` changes its one call site from
`LibraryHashWorker.enqueue(getApplication())` to
`LibraryIngestPipeline.enqueue(getApplication())`; `LibraryHashWorker`'s own
`enqueue()`/`WORK_NAME` are deleted since the pipeline now owns unique-work
naming for the whole chain. `KEEP` (not `REPLACE`) for the same reason
`LibraryHashWorker`'s kdoc already gives: both passes re-query "pending"
fresh each run, so restarting a run in progress resets progress for no
benefit.

## Data flow summary

```
MediaStore
    │
    ▼
LibraryScanner.scan()              (fast, viewModelScope — unchanged)
    │
    ▼
LibraryIngestPipeline.enqueue()
    │
    ▼
LibraryHashWorker → hashPendingTracks()       creates AudioContent
    │
    ▼  (WorkManager .then(), only after hash completes)
AnalysisWorker → analyzePendingAudioContent()  writes AudioFeatures + Fingerprint
```

## Testing

Follows existing conventions (`AGENTS.md`: instrumented tests under
`androidTest/.../data/db/<feature>/`, one file per DAO):

- `LibraryDaoTest`: `getAnyPresentTrackPath` (present-only filter, MIN
  tie-break, null when no present siblings), `getAudioContentNeedingAnalysis`
  (returns rows missing either table, excludes rows with both at current
  version/configHash, excludes rows whose only tracks are absent,
  re-includes a row after a version/configHash bump), and
  `getOrCreateAudioContentIdAndAssign` (reuses an existing `AudioContent`
  row for a repeat hash instead of inserting a duplicate, and assigns the
  id onto the given `Track` — both effects visible after one call).
- No new instrumented test for `AnalysisWorker` itself planned — mirrors
  the codebase's existing choice not to instrument-test `LibraryHashWorker`
  directly; `analyzePendingAudioContent()` is the unit worth testing, the
  `CoroutineWorker` wrapper is a thin `Result.success()`/`Result.retry()`
  shell around it (same shape `LibraryHashWorker` already has, untested at
  the Worker layer today).

## Open items not resolved by this spec

- **`configHash()` without a full extraction.** Needs a native entry point
  (or a hardcoded Kotlin constant recomputed at build time) so the pending
  query can run before any track is decoded. First implementation task's
  job to resolve against `EssentiaWrapper.cpp`, not assumed here.
- **`AnalysisResult` return shape** (`analyzed`/`failed` counts) — mirrors
  `HashResult` exactly; no open question, just not yet written.
- Dedup grouping (`dup_group`/`rank.py`/`quarantine.py` port) and
  MusicBrainz identity lookup remain fully out of scope — future specs,
  consuming the `Fingerprint`/`AudioFeatures` rows this pass produces.
- **Optional follow-up, not part of this pass:** adding `audioContentId`
  to `IngestError` so an "analyze" stage error can be queried by content id
  directly instead of round-tripping through `path`. Costs a real Room
  migration (`IngestError` has no such column today, and
  `fallbackToDestructiveMigration` is forbidden) — not free, so deferred.
  Also: the working tree already has untracked, uncommitted
  `app/schemas/.../2.json` and `3.json` from earlier session work this spec
  hasn't inspected, so the exact next schema version to target should be
  checked at implementation time, not assumed here.
