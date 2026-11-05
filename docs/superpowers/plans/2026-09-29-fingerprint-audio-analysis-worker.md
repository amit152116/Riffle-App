# Background Fingerprint + Audio Features Analysis Worker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire the already-built native DSP extractor and `AudioFeatures`/`Fingerprint` Room tables into a real background pass, so every library track eventually gets analyzed and `SongDetailSheet`'s "Audio Features" section stops saying "Not analyzed yet."

**Architecture:** A new `AnalysisWorker` (CoroutineWorker) runs `LibraryScanner.analyzePendingAudioContent()`, which loops over `AudioContent` rows missing current-version results, decodes one present sibling `Track`'s `content://` URI via a new `EssentiaExtractor.extractContent()`, and writes one `AudioFeatures` + one `Fingerprint` row per `AudioContent` inside a single Room transaction. `LibraryIngestPipeline` chains it after the existing `LibraryHashWorker` via WorkManager's `.then()`, since analysis needs the `AudioContent` rows hashing creates. A tag-along fix wraps `hashPendingTracks()`'s own two-write sequence in a `@Transaction` DAO method for the same reason.

**Tech Stack:** Kotlin, Room (`room-ktx`'s `withTransaction`), WorkManager (`CoroutineWorker`), JNI/C++ (existing `EssentiaWrapper.cpp`), JUnit4 (unit + instrumented tests).

**Spec:** `docs/superpowers/specs/2026-09-29-fingerprint-audio-analysis-worker-design.md`

## Global Constraints

- `minSdk 31`, `targetSdk`/`compileSdk 37`, Kotlin 2.2.10, package root `com.amit152116.riffle`.
- Enums backed by Room columns are stored as TEXT via `@TypeConverter` (`.name`/`.valueOf()`), never ordinal — no new enum needed in this plan, but any new column referencing `FingerprintPurpose`/`HashMethod` must go through the existing converters, not raw ordinals.
- `fallbackToDestructiveMigration` is forbidden — this plan adds no new columns/tables, so no migration is needed; if a task discovers otherwise, stop and flag it rather than adding one silently.
- Never hand-edit `app/schemas/.../*.json` — KSP regenerates it on compile.
- Instrumented tests live under `app/src/androidTest/.../data/db/<feature>/` (one file per DAO) and `.../dsp/` (native extractor tests); unit tests (no Android framework dependency) live under `app/src/test/.../`.
- `./gradlew :app:compileDebugKotlin` / `:app:compileDebugAndroidTestKotlin` validate compile without a device — run before any instrumented test. Instrumented tests run one class at a time: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<FQCN>`.
- `TestFixtures` (`app/src/androidTest/java/com/amit152116/riffle/data/db/TestFixtures.kt`) already has `audioContent()`, `track()`, `audioFeatures()`, `fingerprint()` builders with sensible defaults — use them, don't hand-roll new entity literals in tests.

## Review Focus

- **Corrupt/unsupported audio file during extraction must not crash the pass.** `extractContent` can throw (native `RuntimeException` via `EssentiaWrapper.cpp`'s `throwRuntimeException`, or a decode error from `MediaCodec`); the per-`AudioContent` `try/catch` in `analyzePendingAudioContent()` must catch it, write an `IngestError`, and continue to the next row. Owned by Task 8's test `analyzePendingAudioContent_extractionThrows_logsIngestErrorAndContinues`.
- **A sibling track can go absent between the pending-query snapshot and extraction.** `getAnyPresentTrackPath` can return null (every sibling went absent in that narrow window); the worker must not NPE on a null path. Owned by Task 4's test `getAnyPresentTrackPath_noPresentSiblings_returnsNull` and Task 8's test `analyzePendingAudioContent_noPresentSibling_logsIngestErrorAndContinues`.
- **Duplicate tracks sharing one `AudioContent` must be analyzed exactly once, not once per sibling.** Owned by Task 8's test `analyzePendingAudioContent_duplicateTracksShareOneAudioContent_analyzesOnce`.
- **A crash between the `AudioFeatures` write and the `Fingerprint` write must roll back both, not leave one orphaned.** Owned by Task 7's test `write_secondInsertFails_rollsBackBoth` (this is the accepted external-review fix from the spec).
- **Re-running the pass after nothing changed must do zero work.** `getAudioContentNeedingAnalysis` must return an empty list once every `AudioContent` has a current-version result, so a second `analyzePendingAudioContent()` call performs no native extraction. Owned by Task 4's test `getAudioContentNeedingAnalysis_excludesRowsWithBothTablesAtCurrentVersion` and Task 8's test `analyzePendingAudioContent_secondRunWithNothingPending_doesNoWork`.

---

### Task 1: Native `configHash()` entry point + extractor version/analyzer constants

**Files:**
- Modify: `app/src/main/cpp/EssentiaWrapper.cpp` (add JNI export after line 558, following `nativeExtract`)
- Modify: `app/src/main/java/com/amit152116/riffle/dsp/EssentiaExtractor.kt`
- Test: `app/src/androidTest/java/com/amit152116/riffle/dsp/EssentiaExtractorTest.kt`

**Interfaces:**
- Produces: `EssentiaExtractor.configHash(): String` (instance method, no audio decode), `EssentiaExtractor.EXTRACTOR_VERSION: String` (companion constant `"essentia-lightweight-1"`), `EssentiaExtractor.ANALYZER: String` (companion constant `"chromaprint"`). Tasks 4, 8, 9 consume all three.

- [ ] **Step 1: Write the failing test**

Add to `EssentiaExtractorTest.kt` (same file already has `configHash_matchesRiffleExtractionConfigHash` at line 194 using this exact hash — reuse it verbatim, it's the same underlying `extractionConfigHash()` C++ function):

```kotlin
@Test
fun configHash_isCallableWithoutAudioAndMatchesExtractionResult() {
    val expectedHash = "a4f8c745ae78bc8996b15514476df71ee8ff50888aa586dc3f135848fb14e80c"
    val extractor = EssentiaExtractor()

    val standalone = extractor.configHash()
    val fromExtraction = extractor.extract(sineWave(freqHz = 220.0, seconds = 1.0), SAMPLE_RATE).configHash

    assertEquals("configHash() must match riffle's extraction_config_hash()", expectedHash, standalone)
    assertEquals("standalone configHash() must equal the value attached to a real extraction", fromExtraction, standalone)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:compileDebugAndroidTestKotlin`
Expected: FAIL — `configHash()` is unresolved (method doesn't exist yet on `EssentiaExtractor`).

- [ ] **Step 3: Add the JNI export**

In `EssentiaWrapper.cpp`, after the closing brace of `nativeExtract` (line 558), before the `nativeResampleForTest` comment:

```cpp
extern "C" JNIEXPORT jstring JNICALL
Java_com_amit152116_riffle_dsp_EssentiaExtractor_nativeConfigHash(
        JNIEnv* env,
        jobject /* this */) {
    return env->NewStringUTF(extractionConfigHash().c_str());
}
```

(`extractionConfigHash()` is declared at line 104 inside the file's anonymous namespace, already in scope for any function later in the same translation unit — no header change needed.)

- [ ] **Step 4: Add the Kotlin side**

In `EssentiaExtractor.kt`, inside the `companion object` (after `TARGET_SAMPLE_RATE`):

```kotlin
const val EXTRACTOR_VERSION = "essentia-lightweight-1"
const val ANALYZER = "chromaprint"
```

After the existing `nativeExtract` external declaration:

```kotlin
private external fun nativeConfigHash(): String
```

After the existing `extract`/`extractFile` methods:

```kotlin
/**
 * The current native build's extraction config hash, without decoding any audio -
 * cheap and deterministic (see EssentiaWrapper.cpp's extractionConfigHash(), a
 * memoized SHA-256 of a fixed config struct). Callers needing "has this content
 * already been analyzed under the current config" must call this rather than
 * running a throwaway extraction just to read configHash off the result.
 */
fun configHash(): String = nativeConfigHash()
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :app:compileDebugAndroidTestKotlin` then, if a connected device/emulator is available: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.amit152116.riffle.dsp.EssentiaExtractorTest`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add app/src/main/cpp/EssentiaWrapper.cpp app/src/main/java/com/amit152116/riffle/dsp/EssentiaExtractor.kt app/src/androidTest/java/com/amit152116/riffle/dsp/EssentiaExtractorTest.kt
git commit -m "feat(dsp): add standalone configHash() JNI entry point and extractor version constants"
```

---

### Task 2: Byte-packing helpers for MFCC and fingerprint arrays

**Files:**
- Create: `app/src/main/java/com/amit152116/riffle/data/db/analysis/AnalysisMapping.kt`
- Test: `app/src/test/java/com/amit152116/riffle/data/db/analysis/AnalysisMappingTest.kt`

**Interfaces:**
- Produces: `packMfcc(values: FloatArray): ByteArray`, `packFingerprint(values: IntArray): ByteArray`. Task 3 consumes both.

Mirrors `../riffle/riffle/store.py`'s `pack_mfcc`/`pack_fingerprint` byte-for-byte: MFCC as little-endian float64 (8 bytes/value, matching `np.float64`), fingerprint as little-endian uint32 (4 bytes/value, matching `np.ascontiguousarray(arr, dtype="<u4")` — a Kotlin `Int`'s raw bits written as 4 LE bytes are bit-identical to the same value's `uint32` representation).

- [ ] **Step 1: Write the failing test**

```kotlin
package com.amit152116.riffle.data.db.analysis

import org.junit.Assert.assertArrayEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AnalysisMappingTest {
    @Test
    fun packMfcc_encodesAsLittleEndianFloat64() {
        val packed = packMfcc(floatArrayOf(1.5f, -2.25f))

        val expected = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
            .putDouble(1.5).putDouble(-2.25).array()
        assertArrayEquals(expected, packed)
    }

    @Test
    fun packFingerprint_encodesAsLittleEndianUInt32() {
        val packed = packFingerprint(intArrayOf(1, -1, 305419896))

        val expected = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(1).putInt(-1).putInt(305419896).array()
        assertArrayEquals(expected, packed)
    }

    @Test
    fun packMfcc_emptyArray_producesEmptyByteArray() {
        assertArrayEquals(ByteArray(0), packMfcc(FloatArray(0)))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.amit152116.riffle.data.db.analysis.AnalysisMappingTest"`
Expected: FAIL — `packMfcc`/`packFingerprint` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.amit152116.riffle.data.db.analysis

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Little-endian float64 per value - matches ../riffle/riffle/store.py::pack_mfcc byte for byte. */
fun packMfcc(values: FloatArray): ByteArray {
    val buffer = ByteBuffer.allocate(values.size * 8).order(ByteOrder.LITTLE_ENDIAN)
    values.forEach { buffer.putDouble(it.toDouble()) }
    return buffer.array()
}

/** Little-endian uint32 per value - matches ../riffle/riffle/store.py::pack_fingerprint byte for byte. */
fun packFingerprint(values: IntArray): ByteArray {
    val buffer = ByteBuffer.allocate(values.size * 4).order(ByteOrder.LITTLE_ENDIAN)
    values.forEach { buffer.putInt(it) }
    return buffer.array()
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.amit152116.riffle.data.db.analysis.AnalysisMappingTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/amit152116/riffle/data/db/analysis/AnalysisMapping.kt app/src/test/java/com/amit152116/riffle/data/db/analysis/AnalysisMappingTest.kt
git commit -m "feat(db): add MFCC/fingerprint byte-packing matching riffle's store.py encoding"
```

---

### Task 3: Map extractor output to `AudioFeatures` + `Fingerprint` rows

**Files:**
- Modify: `app/src/main/java/com/amit152116/riffle/data/db/analysis/AnalysisMapping.kt`
- Test: `app/src/test/java/com/amit152116/riffle/data/db/analysis/AnalysisMappingTest.kt`

**Interfaces:**
- Consumes: `com.amit152116.riffle.dsp.AudioFeatures` (extractor result: `bpm: Float, bpmConfidence: Float, keyName: String, scale: String, keyStrength: Float, loudnessLufs: Float, danceability: Float, energy: Float, spectralCentroid: Float, onsetRate: Float, dynamicComplexity: Float, dissonance: Float, zcr: Float, mfccMean: FloatArray, fingerprint: IntArray, fingerprintAlgorithm: Int, configHash: String`), `EssentiaExtractor.EXTRACTOR_VERSION`, `EssentiaExtractor.ANALYZER` (Task 1), `packMfcc`/`packFingerprint` (Task 2).
- Produces: `com.amit152116.riffle.dsp.AudioFeatures.toRoomAudioFeatures(audioContentId: Long, analyzedAt: Long): com.amit152116.riffle.data.db.analysis.AudioFeatures` and `.toFingerprint(audioContentId: Long, computedAt: Long): com.amit152116.riffle.data.db.fingerprint.Fingerprint`. Task 8 consumes both.

Two entities share the simple name `AudioFeatures` in different packages (`com.amit152116.riffle.dsp.AudioFeatures` vs `com.amit152116.riffle.data.db.analysis.AudioFeatures`) — the mapping file must alias one on import to avoid a compile collision.

- [ ] **Step 1: Write the failing test**

Append to `AnalysisMappingTest.kt`:

```kotlin
import com.amit152116.riffle.data.db.fingerprint.FingerprintPurpose
import com.amit152116.riffle.dsp.EssentiaExtractor
import com.amit152116.riffle.dsp.AudioFeatures as DspAudioFeatures

// ... inside the class:

@Test
fun toRoomAudioFeatures_mapsAllFieldsAndConvertsMfccToBytes() {
    val dsp = DspAudioFeatures(
        bpm = 120f, bpmConfidence = 0.9f, keyName = "A", scale = "minor",
        keyStrength = 0.8f, loudnessLufs = -12.3f, danceability = 0.7f, energy = 0.6f,
        spectralCentroid = 2000f, onsetRate = 4f, dynamicComplexity = 3f, dissonance = 0.2f,
        zcr = 0.1f, mfccMean = floatArrayOf(1f, 2f), duration = 180f,
        fingerprint = intArrayOf(1, 2, 3), fingerprintAlgorithm = 2, configHash = "cfg-1"
    )

    val room = dsp.toRoomAudioFeatures(audioContentId = 42L, analyzedAt = 1_000L)

    assertEquals(42L, room.audioContentId)
    assertEquals(120.0, room.bpm)
    assertEquals("A", room.keyName)
    assertEquals(EssentiaExtractor.EXTRACTOR_VERSION, room.extractorVersion)
    assertEquals("cfg-1", room.configHash)
    assertArrayEquals(packMfcc(floatArrayOf(1f, 2f)), room.mfccMean)
    assertEquals(1_000L, room.analyzedAt)
}

@Test
fun toFingerprint_mapsAllFieldsAndConvertsFingerprintToBytes() {
    val dsp = DspAudioFeatures(
        bpm = 120f, bpmConfidence = 0.9f, keyName = "A", scale = "minor",
        keyStrength = 0.8f, loudnessLufs = -12.3f, danceability = 0.7f, energy = 0.6f,
        spectralCentroid = 2000f, onsetRate = 4f, dynamicComplexity = 3f, dissonance = 0.2f,
        zcr = 0.1f, mfccMean = floatArrayOf(1f, 2f), duration = 180f,
        fingerprint = intArrayOf(1, 2, 3), fingerprintAlgorithm = 2, configHash = "cfg-1"
    )

    val fingerprint = dsp.toFingerprint(audioContentId = 42L, computedAt = 1_000L)

    assertEquals(42L, fingerprint.audioContentId)
    assertEquals(EssentiaExtractor.ANALYZER, fingerprint.analyzer)
    assertEquals(EssentiaExtractor.EXTRACTOR_VERSION, fingerprint.analyzerVersion)
    assertEquals("cfg-1", fingerprint.configHash)
    assertEquals(FingerprintPurpose.CANONICAL, fingerprint.purpose)
    assertEquals(2, fingerprint.algorithm)
    assertArrayEquals(packFingerprint(intArrayOf(1, 2, 3)), fingerprint.fingerprintRaw)
    assertEquals(3, fingerprint.fingerprintLength)
    assertEquals(1_000L, fingerprint.computedAt)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.amit152116.riffle.data.db.analysis.AnalysisMappingTest"`
Expected: FAIL — `toRoomAudioFeatures`/`toFingerprint` unresolved.

- [ ] **Step 3: Write minimal implementation**

Add to `AnalysisMapping.kt`:

```kotlin
import com.amit152116.riffle.data.db.fingerprint.Fingerprint
import com.amit152116.riffle.data.db.fingerprint.FingerprintPurpose
import com.amit152116.riffle.dsp.EssentiaExtractor
import com.amit152116.riffle.dsp.AudioFeatures as DspAudioFeatures

fun DspAudioFeatures.toRoomAudioFeatures(audioContentId: Long, analyzedAt: Long): AudioFeatures =
    AudioFeatures(
        audioContentId = audioContentId,
        bpm = bpm.toDouble(),
        bpmConfidence = bpmConfidence.toDouble(),
        keyName = keyName,
        scale = scale,
        keyStrength = keyStrength.toDouble(),
        loudnessLufs = loudnessLufs.toDouble(),
        danceability = danceability.toDouble(),
        energy = energy.toDouble(),
        spectralCentroid = spectralCentroid.toDouble(),
        onsetRate = onsetRate.toDouble(),
        dynamicComplexity = dynamicComplexity.toDouble(),
        dissonance = dissonance.toDouble(),
        zcr = zcr.toDouble(),
        mfccMean = packMfcc(mfccMean),
        extractorVersion = EssentiaExtractor.EXTRACTOR_VERSION,
        configHash = configHash,
        analyzedAt = analyzedAt
    )

fun DspAudioFeatures.toFingerprint(audioContentId: Long, computedAt: Long): Fingerprint =
    Fingerprint(
        audioContentId = audioContentId,
        analyzer = EssentiaExtractor.ANALYZER,
        analyzerVersion = EssentiaExtractor.EXTRACTOR_VERSION,
        configHash = configHash,
        purpose = FingerprintPurpose.CANONICAL,
        algorithm = fingerprintAlgorithm,
        fingerprintRaw = packFingerprint(fingerprint),
        fingerprintLength = fingerprint.size,
        computedAt = computedAt
    )
```

(`AudioFeatures` here resolves to `com.amit152116.riffle.data.db.analysis.AudioFeatures` — the receiver-less return type — since the file is in that package; only the extension receiver needs the `DspAudioFeatures` alias.)

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.amit152116.riffle.data.db.analysis.AnalysisMappingTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/amit152116/riffle/data/db/analysis/AnalysisMapping.kt app/src/test/java/com/amit152116/riffle/data/db/analysis/AnalysisMappingTest.kt
git commit -m "feat(db): map native extractor output to AudioFeatures/Fingerprint rows"
```

---

### Task 4: `LibraryDao` — pick a URI for an `AudioContent` and find pending work

**Files:**
- Modify: `app/src/main/java/com/amit152116/riffle/data/db/library/LibraryDao.kt`
- Test: `app/src/androidTest/java/com/amit152116/riffle/data/db/library/LibraryDaoTest.kt`

**Interfaces:**
- Produces: `LibraryDao.getAnyPresentTrackPath(audioContentId: Long): String?`, `LibraryDao.getAudioContentNeedingAnalysis(extractorVersion: String, configHash: String): List<AudioContent>`. Task 6 (repository pass-through) and Task 8 (`LibraryScanner`) consume both.

- [ ] **Step 1: Write the failing tests**

Add to `LibraryDaoTest.kt`:

```kotlin
@Test
fun getAnyPresentTrackPath_pathTieBreak_picksAlphabeticallySmallest() = runBlocking {
    val contentId = dao.insertAudioContent(TestFixtures.audioContent())
    dao.insertTrack(TestFixtures.track(path = "/z-track.mp3", audioContentId = contentId))
    dao.insertTrack(TestFixtures.track(path = "/a-track.mp3", audioContentId = contentId))

    assertEquals("/a-track.mp3", dao.getAnyPresentTrackPath(contentId))
}

@Test
fun getAnyPresentTrackPath_noPresentSiblings_returnsNull() = runBlocking {
    val contentId = dao.insertAudioContent(TestFixtures.audioContent())
    dao.insertTrack(TestFixtures.track(path = "/absent.mp3", audioContentId = contentId, present = false))

    assertNull(dao.getAnyPresentTrackPath(contentId))
}

@Test
fun getAudioContentNeedingAnalysis_returnsRowMissingBothTables() = runBlocking {
    val contentId = dao.insertAudioContent(TestFixtures.audioContent())
    dao.insertTrack(TestFixtures.track(path = "/pending.mp3", audioContentId = contentId))

    val pending = dao.getAudioContentNeedingAnalysis(extractorVersion = "v1", configHash = "cfg-1")

    assertEquals(1, pending.size)
    assertEquals(contentId, pending[0].id)
}

@Test
fun getAudioContentNeedingAnalysis_excludesRowsWithBothTablesAtCurrentVersion() = runBlocking {
    val contentId = dao.insertAudioContent(TestFixtures.audioContent())
    dao.insertTrack(TestFixtures.track(path = "/done.mp3", audioContentId = contentId))
    db.analysisDao().insertAudioFeatures(
        TestFixtures.audioFeatures(audioContentId = contentId, extractorVersion = "v1", configHash = "cfg-1")
    )
    db.fingerprintDao().insertFingerprint(
        TestFixtures.fingerprint(audioContentId = contentId, analyzerVersion = "v1", configHash = "cfg-1")
    )

    val pending = dao.getAudioContentNeedingAnalysis(extractorVersion = "v1", configHash = "cfg-1")

    assertEquals(0, pending.size)
}

@Test
fun getAudioContentNeedingAnalysis_versionBump_reincludesRow() = runBlocking {
    val contentId = dao.insertAudioContent(TestFixtures.audioContent())
    dao.insertTrack(TestFixtures.track(path = "/stale.mp3", audioContentId = contentId))
    db.analysisDao().insertAudioFeatures(
        TestFixtures.audioFeatures(audioContentId = contentId, extractorVersion = "v1", configHash = "cfg-1")
    )
    db.fingerprintDao().insertFingerprint(
        TestFixtures.fingerprint(audioContentId = contentId, analyzerVersion = "v1", configHash = "cfg-1")
    )

    val pending = dao.getAudioContentNeedingAnalysis(extractorVersion = "v2", configHash = "cfg-2")

    assertEquals(1, pending.size)
}

@Test
fun getAudioContentNeedingAnalysis_onlyAbsentTracks_excludesRow() = runBlocking {
    val contentId = dao.insertAudioContent(TestFixtures.audioContent())
    dao.insertTrack(TestFixtures.track(path = "/absent.mp3", audioContentId = contentId, present = false))

    val pending = dao.getAudioContentNeedingAnalysis(extractorVersion = "v1", configHash = "cfg-1")

    assertEquals(0, pending.size)
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:compileDebugAndroidTestKotlin`
Expected: FAIL — `getAnyPresentTrackPath`/`getAudioContentNeedingAnalysis` unresolved.

- [ ] **Step 3: Write minimal implementation**

Add to `LibraryDao.kt`:

```kotlin
@Query("SELECT MIN(path) FROM Track WHERE audioContentId = :audioContentId AND present = 1")
suspend fun getAnyPresentTrackPath(audioContentId: Long): String?

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

Add imports at the top of `LibraryDao.kt` if not already present: `com.amit152116.riffle.data.db.analysis.AudioFeatures`, `com.amit152116.riffle.data.db.fingerprint.Fingerprint`.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:compileDebugAndroidTestKotlin` then (device/emulator available) `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.amit152116.riffle.data.db.library.LibraryDaoTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/amit152116/riffle/data/db/library/LibraryDao.kt app/src/androidTest/java/com/amit152116/riffle/data/db/library/LibraryDaoTest.kt
git commit -m "feat(db): add pending-analysis query and any-present-track lookup to LibraryDao"
```

---

### Task 5: `LibraryDao.@Transaction getOrCreateAudioContentIdAndAssign` (tag-along hash-pass fix)

**Files:**
- Modify: `app/src/main/java/com/amit152116/riffle/data/db/library/LibraryDao.kt`
- Test: `app/src/androidTest/java/com/amit152116/riffle/data/db/library/LibraryDaoTest.kt`

**Interfaces:**
- Produces: `LibraryDao.getOrCreateAudioContentIdAndAssign(track: Track, audioContent: AudioContent): Long`. Task 6 (repository) and Task 8 (rewriting `hashPendingTracks()`) consume this.

- [ ] **Step 1: Write the failing tests**

Add to `LibraryDaoTest.kt`:

```kotlin
@Test
fun getOrCreateAudioContentIdAndAssign_newHash_insertsAndAssigns() = runBlocking {
    val track = TestFixtures.track(path = "/new.mp3")
    dao.insertTrack(track)
    val inserted = dao.getTrackByPath("/new.mp3")!!

    val contentId = dao.getOrCreateAudioContentIdAndAssign(inserted, TestFixtures.audioContent(audioHash = "new-hash"))

    val loaded = dao.getTrackByPath("/new.mp3")
    assertEquals(contentId, loaded!!.audioContentId)
    assertNotNull(dao.getAudioContentByHash("new-hash", HashMethod.STREAMHASH))
}

@Test
fun getOrCreateAudioContentIdAndAssign_repeatHash_reusesExistingRowNotDuplicate() = runBlocking {
    val existingContentId = dao.insertAudioContent(TestFixtures.audioContent(audioHash = "shared-hash"))
    dao.insertTrack(TestFixtures.track(path = "/first.mp3", audioContentId = existingContentId))
    dao.insertTrack(TestFixtures.track(path = "/second.mp3"))
    val second = dao.getTrackByPath("/second.mp3")!!

    val contentId = dao.getOrCreateAudioContentIdAndAssign(second, TestFixtures.audioContent(audioHash = "shared-hash"))

    assertEquals(existingContentId, contentId)
    assertEquals(existingContentId, dao.getTrackByPath("/second.mp3")!!.audioContentId)
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:compileDebugAndroidTestKotlin`
Expected: FAIL — `getOrCreateAudioContentIdAndAssign` unresolved.

- [ ] **Step 3: Write minimal implementation**

Add to `LibraryDao.kt` (needs `androidx.room.Transaction` import):

```kotlin
@Transaction
suspend fun getOrCreateAudioContentIdAndAssign(track: Track, audioContent: AudioContent): Long {
    val id = getAudioContentByHash(audioContent.audioHash, audioContent.hashMethod)?.id
        ?: insertAudioContent(audioContent)
    updateTrack(track.copy(audioContentId = id))
    return id
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:compileDebugAndroidTestKotlin` then (device/emulator available) `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.amit152116.riffle.data.db.library.LibraryDaoTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/amit152116/riffle/data/db/library/LibraryDao.kt app/src/androidTest/java/com/amit152116/riffle/data/db/library/LibraryDaoTest.kt
git commit -m "fix(db): wrap hash-pass AudioContent lookup/create + Track assign in one transaction"
```

---

### Task 6: `LibraryRepository` pass-throughs

**Files:**
- Modify: `app/src/main/java/com/amit152116/riffle/data/db/library/LibraryRepository.kt`

**Interfaces:**
- Consumes: Task 4/5's three new `LibraryDao` methods.
- Produces: matching `LibraryRepository` methods with identical names/signatures. Task 8 consumes these.

No test file for this task — these are one-line delegations with no branching logic, matching every other method already in this class (see `insertAudioContent`, `getTrackByPath`, etc.), and are exercised indirectly through Task 8's `LibraryScanner` tests.

- [ ] **Step 1: Add the pass-throughs**

Add to `LibraryRepository.kt`:

```kotlin
suspend fun getAnyPresentTrackPath(audioContentId: Long) = dao.getAnyPresentTrackPath(audioContentId)
suspend fun getAudioContentNeedingAnalysis(extractorVersion: String, configHash: String) =
    dao.getAudioContentNeedingAnalysis(extractorVersion, configHash)
suspend fun getOrCreateAudioContentIdAndAssign(track: Track, audioContent: AudioContent) =
    dao.getOrCreateAudioContentIdAndAssign(track, audioContent)
```

- [ ] **Step 2: Compile check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: PASS (no behavior to test yet — pure delegation)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/amit152116/riffle/data/db/library/LibraryRepository.kt
git commit -m "feat(db): expose analysis-pending and hash-transaction queries on LibraryRepository"
```

---

### Task 7: Cross-repository transaction for `AudioFeatures` + `Fingerprint`

**Files:**
- Create: `app/src/main/java/com/amit152116/riffle/data/db/analysis/AnalysisResultWriter.kt`
- Test: `app/src/androidTest/java/com/amit152116/riffle/data/db/analysis/AnalysisResultWriterTest.kt`

**Interfaces:**
- Consumes: `RiffleDatabase` (for `androidx.room.withTransaction`), `AnalysisRepository.insertAudioFeatures`, `FingerprintRepository.insertFingerprint` (both pre-existing).
- Produces: `AnalysisResultWriter.write(audioFeatures: AudioFeatures, fingerprint: Fingerprint)`. Task 8 consumes this.

This is the accepted external-review fix: `AudioFeatures` and `Fingerprint` live on two different DAOs, so a per-DAO `@Transaction` can't wrap both — `androidx.room.withTransaction(db) { }` (room-ktx, already a dependency per `app/build.gradle.kts`) is the tool that coordinates across DAOs sharing one `RoomDatabase`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.amit152116.riffle.data.db.analysis

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.amit152116.riffle.data.db.RiffleDatabase
import com.amit152116.riffle.data.db.TestFixtures
import com.amit152116.riffle.data.db.fingerprint.FingerprintRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnalysisResultWriterTest {
    private lateinit var db: RiffleDatabase
    private lateinit var writer: AnalysisResultWriter
    private var contentId: Long = 0

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, RiffleDatabase::class.java).build()
        val analysisRepository = AnalysisRepository(db.analysisDao())
        val fingerprintRepository = FingerprintRepository(db.fingerprintDao())
        writer = AnalysisResultWriter(db, analysisRepository, fingerprintRepository)
        contentId = db.libraryDao().insertAudioContent(TestFixtures.audioContent())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun write_bothInsertsSucceed_bothRowsPersisted() = runBlocking {
        writer.write(
            TestFixtures.audioFeatures(audioContentId = contentId),
            TestFixtures.fingerprint(audioContentId = contentId)
        )

        assertNotNull(db.analysisDao().getAudioFeaturesByAudioContentId(contentId))
    }

    @Test
    fun write_secondInsertFails_rollsBackBoth() {
        // A second Fingerprint for the same (audioContentId, analyzer, analyzerVersion,
        // configHash, purpose, algorithm) violates Fingerprint's unique index - pre-seeding
        // one and reusing the exact same fixture values forces insertFingerprint to fail
        // inside the transaction, while insertAudioFeatures for the same content id would
        // otherwise succeed cleanly (AudioFeatures.audioContentId is a separate unique index).
        runBlocking { db.fingerprintDao().insertFingerprint(TestFixtures.fingerprint(audioContentId = contentId)) }

        assertThrows(Exception::class.java) {
            runBlocking {
                writer.write(
                    TestFixtures.audioFeatures(audioContentId = contentId),
                    TestFixtures.fingerprint(audioContentId = contentId)
                )
            }
        }

        runBlocking {
            assertNull(
                "AudioFeatures insert must have rolled back when the Fingerprint insert failed",
                db.analysisDao().getAudioFeaturesByAudioContentId(contentId)
            )
        }
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:compileDebugAndroidTestKotlin`
Expected: FAIL — `AnalysisResultWriter` doesn't exist.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.amit152116.riffle.data.db.analysis

import androidx.room.withTransaction
import com.amit152116.riffle.data.db.RiffleDatabase
import com.amit152116.riffle.data.db.fingerprint.Fingerprint
import com.amit152116.riffle.data.db.fingerprint.FingerprintRepository

/**
 * Writes one AudioFeatures + one Fingerprint row atomically. Room's @Transaction only
 * coordinates calls within a single DAO; these two entities live on different DAOs
 * (AnalysisDao, FingerprintDao), so cross-DAO atomicity needs the database-level
 * withTransaction instead. Without this, a crash between the two inserts wouldn't
 * corrupt anything (the pending query already treats "either missing" as pending) but
 * would waste a full re-extraction next run - this makes that case impossible instead.
 */
class AnalysisResultWriter(
    private val database: RiffleDatabase,
    private val analysisRepository: AnalysisRepository,
    private val fingerprintRepository: FingerprintRepository
) {
    suspend fun write(audioFeatures: AudioFeatures, fingerprint: Fingerprint) {
        database.withTransaction {
            analysisRepository.insertAudioFeatures(audioFeatures)
            fingerprintRepository.insertFingerprint(fingerprint)
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:compileDebugAndroidTestKotlin` then (device/emulator available) `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.amit152116.riffle.data.db.analysis.AnalysisResultWriterTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/amit152116/riffle/data/db/analysis/AnalysisResultWriter.kt app/src/androidTest/java/com/amit152116/riffle/data/db/analysis/AnalysisResultWriterTest.kt
git commit -m "feat(db): write AudioFeatures+Fingerprint atomically via cross-DAO transaction"
```

---

### Task 8: `LibraryScanner.analyzePendingAudioContent()` + hash-pass rewrite

**Files:**
- Modify: `app/src/main/java/com/amit152116/riffle/data/db/library/LibraryScanner.kt`
- Test: `app/src/androidTest/java/com/amit152116/riffle/data/db/library/LibraryScannerTest.kt` (this file already exists per the working tree — check its current content before adding, don't duplicate an existing `setUp`/fixture pattern)

**Interfaces:**
- Consumes: `EssentiaExtractor.extractContent`/`.configHash()`/`.EXTRACTOR_VERSION` (Task 1), `toRoomAudioFeatures`/`toFingerprint` (Task 3), `LibraryRepository.getAudioContentNeedingAnalysis`/`.getAnyPresentTrackPath`/`.getOrCreateAudioContentIdAndAssign` (Task 6), `AnalysisResultWriter` (Task 7).
- Produces: `LibraryScanner.analyzePendingAudioContent(): AnalysisResult` where `data class AnalysisResult(val analyzed: Int, val failed: Int)`. Task 9 (`AnalysisWorker`) consumes this. `LibraryScanner`'s constructor changes from `(context: Context, repository: LibraryRepository)` to `(context: Context, repository: LibraryRepository, extractor: EssentiaExtractor, resultWriter: AnalysisResultWriter)` — Task 10 updates the one call site (`AppContainer`).

- [ ] **Step 1: Write the failing tests**

Read `LibraryScannerTest.kt` first to match its existing `setUp()`/DB-wiring style, then add:

```kotlin
@Test
fun analyzePendingAudioContent_writesFeaturesAndFingerprintForPresentTrack() = runBlocking {
    val contentId = db.libraryDao().insertAudioContent(TestFixtures.audioContent())
    db.libraryDao().insertTrack(TestFixtures.track(path = realWavPath, audioContentId = contentId))

    val result = scanner.analyzePendingAudioContent()

    assertEquals(1, result.analyzed)
    assertEquals(0, result.failed)
    assertNotNull(db.analysisDao().getAudioFeaturesByAudioContentId(contentId))
}

@Test
fun analyzePendingAudioContent_duplicateTracksShareOneAudioContent_analyzesOnce() = runBlocking {
    val contentId = db.libraryDao().insertAudioContent(TestFixtures.audioContent())
    db.libraryDao().insertTrack(TestFixtures.track(path = realWavPath, audioContentId = contentId))
    db.libraryDao().insertTrack(TestFixtures.track(path = "$realWavPath.dup", audioContentId = contentId))

    val result = scanner.analyzePendingAudioContent()

    assertEquals(1, result.analyzed)
}

@Test
fun analyzePendingAudioContent_noPresentSibling_logsIngestErrorAndContinues() = runBlocking {
    val contentId = db.libraryDao().insertAudioContent(TestFixtures.audioContent())
    db.libraryDao().insertTrack(TestFixtures.track(path = "/gone.mp3", audioContentId = contentId, present = false))

    val result = scanner.analyzePendingAudioContent()

    assertEquals(0, result.analyzed)
    assertEquals(1, result.failed)
}

@Test
fun analyzePendingAudioContent_extractionThrows_logsIngestErrorAndContinues() = runBlocking {
    val contentId = db.libraryDao().insertAudioContent(TestFixtures.audioContent())
    // Not a real audio file - MediaExtractor.setDataSource fails, extractContent throws.
    db.libraryDao().insertTrack(TestFixtures.track(path = "content://media/external/audio/media/999999", audioContentId = contentId))

    val result = scanner.analyzePendingAudioContent()

    assertEquals(0, result.analyzed)
    assertEquals(1, result.failed)
    assertNotNull(db.libraryDao().getIngestErrorByPath("content://media/external/audio/media/999999"))
}

@Test
fun analyzePendingAudioContent_secondRunWithNothingPending_doesNoWork() = runBlocking {
    val contentId = db.libraryDao().insertAudioContent(TestFixtures.audioContent())
    db.libraryDao().insertTrack(TestFixtures.track(path = realWavPath, audioContentId = contentId))
    scanner.analyzePendingAudioContent()

    val second = scanner.analyzePendingAudioContent()

    assertEquals(0, second.analyzed)
    assertEquals(0, second.failed)
}
```

(`realWavPath` needs to be a real decodable audio file reachable via a `content://` or `file://` URI from the instrumented test environment — reuse whatever fixture pattern `EssentiaExtractorTest`'s WAV-writing helpers already establish, or check how `LibraryScannerTest.kt`'s existing hash-pass tests supply a real file, since `hashPendingTracks()` already needs the same thing.)

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:compileDebugAndroidTestKotlin`
Expected: FAIL — `analyzePendingAudioContent`/`AnalysisResult` unresolved, and `LibraryScanner`'s constructor call in the test's `setUp()` doesn't match the new signature yet (update it as part of writing the test).

- [ ] **Step 3: Write minimal implementation**

Replace the two-write hash-pass body in `hashPendingTracks()`:

```kotlin
suspend fun hashPendingTracks(): HashResult = withContext(Dispatchers.IO) {
    var hashed = 0
    var failed = 0
    for (track in repository.getTracksNeedingHash()) {
        try {
            val uri = Uri.parse(track.path)
            val identity = AudioHasher.computeAudioIdentity(context, uri)
            repository.getOrCreateAudioContentIdAndAssign(
                track,
                AudioContent(
                    audioHash = identity.audioHash,
                    hashMethod = identity.hashMethod,
                    duration = track.durationMs?.let { it / 1000.0 },
                    codec = identity.codec,
                    sampleRate = identity.sampleRate,
                    channels = identity.channels,
                    firstSeenAt = System.currentTimeMillis()
                )
            )
            hashed++
        } catch (e: Exception) {
            failed++
            log.w("Failed to hash ${track.path}", e)
            repository.insertIngestError(
                IngestError(
                    path = track.path, dev = null, inode = null, size = track.size, mtime = track.mtime,
                    stage = "hash", message = e.message, attempts = 1,
                    lastAttemptAt = System.currentTimeMillis(), resolvedAt = null
                )
            )
        }
    }
    HashResult(hashed, failed)
}
```

Add the new pass and constructor params:

```kotlin
class LibraryScanner(
    private val context: Context,
    private val repository: LibraryRepository,
    private val extractor: EssentiaExtractor,
    private val resultWriter: AnalysisResultWriter
) {
    // ... existing scan()/hashPendingTracks() ...

    /** Third pass: DSP extraction + fingerprinting for AudioContent rows the hash pass created. */
    suspend fun analyzePendingAudioContent(): AnalysisResult = withContext(Dispatchers.IO) {
        var analyzed = 0
        var failed = 0
        val extractorVersion = EssentiaExtractor.EXTRACTOR_VERSION
        val configHash = extractor.configHash()
        for (audioContent in repository.getAudioContentNeedingAnalysis(extractorVersion, configHash)) {
            val path = repository.getAnyPresentTrackPath(audioContent.id)
            if (path == null) {
                failed++
                log.w("No present track left for AudioContent ${audioContent.id}, skipping analysis")
                continue
            }
            try {
                val features = extractor.extractContent(context, Uri.parse(path))
                val now = System.currentTimeMillis()
                resultWriter.write(
                    features.toRoomAudioFeatures(audioContentId = audioContent.id, analyzedAt = now),
                    features.toFingerprint(audioContentId = audioContent.id, computedAt = now)
                )
                analyzed++
            } catch (e: Exception) {
                failed++
                log.w("Failed to analyze AudioContent ${audioContent.id} via $path", e)
                repository.insertIngestError(
                    IngestError(
                        path = path, dev = null, inode = null, size = null, mtime = null,
                        stage = "analyze", message = e.message, attempts = 1,
                        lastAttemptAt = System.currentTimeMillis(), resolvedAt = null
                    )
                )
            }
        }
        AnalysisResult(analyzed, failed)
    }
}

data class AnalysisResult(val analyzed: Int, val failed: Int)
```

Add imports: `com.amit152116.riffle.data.db.analysis.AnalysisResultWriter`, `com.amit152116.riffle.data.db.analysis.toRoomAudioFeatures`, `com.amit152116.riffle.data.db.analysis.toFingerprint`, `com.amit152116.riffle.dsp.EssentiaExtractor`.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:compileDebugAndroidTestKotlin` then (device/emulator available) `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.amit152116.riffle.data.db.library.LibraryScannerTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/amit152116/riffle/data/db/library/LibraryScanner.kt app/src/androidTest/java/com/amit152116/riffle/data/db/library/LibraryScannerTest.kt
git commit -m "feat(library): add analyzePendingAudioContent() DSP pass, wrap hash-pass write in transaction"
```

---

### Task 9: `AnalysisWorker`

**Files:**
- Create: `app/src/main/java/com/amit152116/riffle/work/AnalysisWorker.kt`

**Interfaces:**
- Consumes: `LibraryScanner.analyzePendingAudioContent()` (Task 8) via `AppContainer.getInstance(applicationContext).libraryScanner`.
- Produces: `AnalysisWorker : CoroutineWorker`. Task 10 (`LibraryIngestPipeline`) references this class in a `OneTimeWorkRequestBuilder<AnalysisWorker>()`.

No new test file — mirrors the codebase's existing choice not to instrument-test `LibraryHashWorker` directly (the spec's Testing section already covers why: the logic worth testing is `analyzePendingAudioContent()`, tested in Task 8; this class is a thin `Result.success()`/`Result.retry()` shell).

- [ ] **Step 1: Write the implementation**

```kotlin
package com.amit152116.riffle.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.amit152116.riffle.data.AppContainer
import com.amit152116.riffle.log.RiffleLog

/**
 * Runs LibraryScanner.analyzePendingAudioContent() as a WorkManager job, chained after
 * LibraryHashWorker by LibraryIngestPipeline - never enqueued standalone, since AudioFeatures/
 * Fingerprint rows key off AudioContent.id, which only exists once hashing has run.
 */
class AnalysisWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val log = RiffleLog(this)

    override suspend fun doWork(): Result {
        return try {
            val container = AppContainer.getInstance(applicationContext)
            val result = container.libraryScanner.analyzePendingAudioContent()
            log.w("Analysis pass finished: analyzed=${result.analyzed} failed=${result.failed}")
            Result.success()
        } catch (e: Exception) {
            // Per-item extraction failures are already caught and logged as IngestErrors inside
            // analyzePendingAudioContent() - reaching here means something broader broke (e.g.
            // the DB itself), worth a retry, same distinction LibraryHashWorker draws.
            log.e("Analysis pass failed unexpectedly", e)
            Result.retry()
        }
    }
}
```

- [ ] **Step 2: Compile check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/amit152116/riffle/work/AnalysisWorker.kt
git commit -m "feat(work): add AnalysisWorker running the DSP analysis pass"
```

---

### Task 10: `LibraryIngestPipeline` + wire `AppContainer`/`LibraryViewModel`

**Files:**
- Create: `app/src/main/java/com/amit152116/riffle/work/LibraryIngestPipeline.kt`
- Modify: `app/src/main/java/com/amit152116/riffle/work/LibraryHashWorker.kt` (remove its own `enqueue()`/`WORK_NAME` — the pipeline now owns unique-work naming for the whole chain)
- Modify: `app/src/main/java/com/amit152116/riffle/data/AppContainer.kt`
- Modify: `app/src/main/java/com/amit152116/riffle/ui/library/LibraryViewModel.kt`

**Interfaces:**
- Consumes: `LibraryHashWorker`, `AnalysisWorker` (Task 9).
- Produces: `LibraryIngestPipeline.enqueue(context: Context)`, called from `LibraryViewModel.scanLibrary()`.

No new test file — this is WorkManager wiring (`beginUniqueWork(...).then(...).enqueue()`), the same category of code the codebase already leaves untested at the `LibraryHashWorker.enqueue()` level; correctness here is verified by running the app (Step 4 below), not a unit/instrumented test.

- [ ] **Step 1: Write `LibraryIngestPipeline`**

```kotlin
package com.amit152116.riffle.work

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/**
 * Owns the ordering between the hash pass and the analysis pass: AudioFeatures/Fingerprint
 * rows key off AudioContent.id, which only exists after LibraryHashWorker runs, so AnalysisWorker
 * must never be enqueued standalone. Both workers stay simple, single-purpose CoroutineWorkers
 * with no knowledge of what precedes or follows them - this object is the one place that knows
 * the chain.
 */
object LibraryIngestPipeline {
    private const val WORK_NAME = "library_ingest_pipeline"

    /**
     * KEEP, not REPLACE: both passes re-query "pending" fresh each run, so restarting a run
     * already in progress resets progress for no benefit (same reasoning LibraryHashWorker's
     * own enqueue() used to give before this object took over unique-work naming).
     */
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

- [ ] **Step 2: Remove `LibraryHashWorker`'s own enqueue logic**

In `LibraryHashWorker.kt`, delete the `companion object` block containing `WORK_NAME` and `enqueue(context: Context)` (the class body — `doWork()` — stays unchanged; only its own enqueue helper goes, since `LibraryIngestPipeline` now owns that).

- [ ] **Step 3: Wire `AppContainer`**

In `AppContainer.kt`, add:

```kotlin
val essentiaExtractor: EssentiaExtractor by lazy { EssentiaExtractor() }
val analysisResultWriter: AnalysisResultWriter by lazy {
    AnalysisResultWriter(database, analysisRepository, fingerprintRepository)
}
```

Change the `libraryScanner` line:

```kotlin
val libraryScanner: LibraryScanner by lazy {
    LibraryScanner(context.applicationContext, libraryRepository, essentiaExtractor, analysisResultWriter)
}
```

Add imports: `com.amit152116.riffle.data.db.analysis.AnalysisResultWriter`, `com.amit152116.riffle.dsp.EssentiaExtractor`.

- [ ] **Step 4: Wire `LibraryViewModel`**

In `LibraryViewModel.kt`, change `scanLibrary()`'s call site from `LibraryHashWorker.enqueue(getApplication())` to `LibraryIngestPipeline.enqueue(getApplication())`, updating the import accordingly.

- [ ] **Step 5: Full compile check**

Run: `./gradlew :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin`
Expected: PASS — this is the point where every earlier task's pieces link together; a signature mismatch anywhere in the chain (Task 8's `LibraryScanner` constructor, Task 9's `AnalysisWorker`, this task's `AppContainer` wiring) surfaces here first.

- [ ] **Step 6: Manual verification**

Install a debug build, grant media permission, trigger a library scan (or fresh install), and confirm via `SongDetailSheet`'s "Audio Features" section that at least one track shows real values instead of "Not analyzed yet" after the background pipeline has had time to run. Check `RiffleLog`/`adb logcat` for `AnalysisWorker`'s "Analysis pass finished" log line to confirm the chain actually ran end to end, not just compiled.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/amit152116/riffle/work/LibraryIngestPipeline.kt app/src/main/java/com/amit152116/riffle/work/LibraryHashWorker.kt app/src/main/java/com/amit152116/riffle/data/AppContainer.kt app/src/main/java/com/amit152116/riffle/ui/library/LibraryViewModel.kt
git commit -m "feat(work): chain AnalysisWorker after LibraryHashWorker via LibraryIngestPipeline"
```
