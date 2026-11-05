package com.amit152116.riffle.data

import android.content.Context
import com.amit152116.riffle.data.db.RiffleDatabase
import com.amit152116.riffle.data.db.analysis.AnalysisRepository
import com.amit152116.riffle.data.db.analysis.AnalysisResultWriter
import com.amit152116.riffle.data.db.cluster.ClusterRepository
import com.amit152116.riffle.data.db.dedup.DedupRepository
import com.amit152116.riffle.data.db.fingerprint.FingerprintRepository
import com.amit152116.riffle.data.db.library.LibraryRepository
import com.amit152116.riffle.data.db.library.LibraryScanner
import com.amit152116.riffle.data.db.musicbrainz.MusicBrainzRepository
import com.amit152116.riffle.data.db.playback.PlaybackRepository
import com.amit152116.riffle.data.db.playback.TelemetryRepository
import com.amit152116.riffle.data.prefs.PreferencesManager
import com.amit152116.riffle.dsp.EssentiaExtractor

/**
 * Sole owner of every repository, the scanner, and prefs - the single place
 * that turns [RiffleDatabase] DAOs into the objects ViewModels/Service
 * actually use. Before this, each ViewModel called
 * RiffleDatabase.getInstance(application).xDao() and built its own
 * repository, duplicating that wiring at every call site (see
 * PreferencesManager's kdoc for the same problem with SharedPreferences).
 * Add a new dependency here, not as a fresh RiffleDatabase.getInstance(...)
 * call in a ViewModel.
 */
class AppContainer private constructor(context: Context) {
    private val database = RiffleDatabase.getInstance(context)

    val preferences: PreferencesManager = PreferencesManager.getInstance(context)

    val libraryRepository: LibraryRepository by lazy { LibraryRepository(database.libraryDao()) }
    val fingerprintRepository: FingerprintRepository by lazy { FingerprintRepository(database.fingerprintDao()) }
    val dedupRepository: DedupRepository by lazy { DedupRepository(database.dedupDao()) }
    val analysisRepository: AnalysisRepository by lazy { AnalysisRepository(database.analysisDao()) }
    val musicBrainzRepository: MusicBrainzRepository by lazy { MusicBrainzRepository(database.musicBrainzDao()) }
    val clusterRepository: ClusterRepository by lazy { ClusterRepository(database.clusterDao()) }
    val playbackRepository: PlaybackRepository by lazy { PlaybackRepository(database.playbackDao()) }
    val telemetryRepository: TelemetryRepository by lazy { TelemetryRepository(database.telemetryDao()) }

    // essentiaExtractor/analysisResultWriter aren't their own AppContainer properties: neither
    // has any consumer besides this constructor call, so there's nothing to share across
    // classes - they're implementation details of building the one shared libraryScanner, not
    // dependencies of their own.
    val libraryScanner: LibraryScanner by lazy {
        // Lazy<EssentiaExtractor>, not an eagerly-resolved EssentiaExtractor: constructing one
        // triggers System.loadLibrary("riffle_dsp") (Essentia+chromaprint, a sizeable native
        // lib) via the class's companion init. LibraryScanner only needs this for
        // analyzePendingAudioContent() - resolving it here would force that load on whatever
        // thread first touches libraryScanner (typically a ViewModel constructor on Main), even
        // for callers who only ever use scan()/hashPendingTracks().
        val extractor = lazy { EssentiaExtractor() }
        val resultWriter = AnalysisResultWriter(database, analysisRepository, fingerprintRepository)
        LibraryScanner(context.applicationContext, libraryRepository, extractor, resultWriter)
    }

    /**
     * TrackUiModel.id is MediaStore's _ID, not Room's Track.id (see
     * Track.mediaStoreId kdoc) - every UI-layer call site that needs to
     * write a trackId FK (favorite, blacklist, playlist, ...) resolves
     * through here first. Null when the track hasn't been scanned into Room
     * yet (e.g. the fast metadata pass hasn't reached it).
     */
    suspend fun resolveTrackId(mediaStoreId: Long): Long? =
        libraryRepository.getTrackByMediaStoreId(mediaStoreId)?.id

    companion object {
        @Volatile private var instance: AppContainer? = null

        fun getInstance(context: Context): AppContainer =
            instance ?: synchronized(this) {
                instance ?: AppContainer(context.applicationContext).also { instance = it }
            }
    }
}
