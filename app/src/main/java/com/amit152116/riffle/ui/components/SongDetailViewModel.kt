package com.amit152116.riffle.ui.components

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.amit152116.riffle.data.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Everything the "Details" sheet shows beyond what [com.amit152116.riffle.data.model.TrackUiModel]
 * already carries (title/artist/album/genre/duration - shown straight from the row, no fetch
 * needed). File-level fields and codec/sampleRate/channels come from a fresh Room lookup since
 * TrackUiModel doesn't carry them; sampleRate/channels/codec stay null until a completed audio-hash
 * pass has written the track's AudioContent row.
 */
data class SongDetailUiModel(
    val fileName: String?,
    val location: String?,
    val fileSizeBytes: Long?,
    val bitrate: Int?,
    val sampleRate: Int?,
    val channels: Int?,
    val codec: String?,
    val dateAddedEpochSeconds: Long?,
    val dateModifiedEpochSeconds: Long?,
    val bpm: Double?,
    val bpmConfidence: Double?,
    val keyName: String?,
    val scale: String?,
    val keyStrength: Double?,
    val loudnessLufs: Double?,
    val danceability: Double?,
    val energy: Double?,
    val spectralCentroid: Double?,
    val onsetRate: Double?,
    val dynamicComplexity: Double?,
    val dissonance: Double?,
    val zcr: Double?,
    val mfccPresent: Boolean
)

class SongDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val libraryRepository = AppContainer.getInstance(application).libraryRepository
    private val analysisRepository = AppContainer.getInstance(application).analysisRepository

    private val _detail = MutableStateFlow<SongDetailUiModel?>(null)
    val detail: StateFlow<SongDetailUiModel?> = _detail.asStateFlow()

    /**
     * Re-queries every call, even for the same [mediaStoreId] as last time - the audio_features
     * fields this loads are filled in by a background analysis pass that can complete between
     * one sheet-open and the next. Caching "already loaded for this id" (as an earlier version
     * of this method did) would keep showing "Not analyzed yet" indefinitely once the sheet had
     * been opened once, even after analysis finished.
     */
    fun load(mediaStoreId: Long) {
        _detail.value = null
        viewModelScope.launch {
            val track = libraryRepository.getTrackByMediaStoreId(mediaStoreId)
            val audioContent = track?.audioContentId?.let { libraryRepository.getAudioContentById(it) }
            val audioFeatures = audioContent?.let { analysisRepository.getAudioFeaturesByAudioContentId(it.id) }
            _detail.value = SongDetailUiModel(
                // path is a content:// Uri whose last segment is only MediaStore's numeric
                // _ID (see Track.displayName kdoc) - never a fallback source for a file name.
                fileName = track?.displayName,
                location = track?.relativePath,
                fileSizeBytes = track?.size,
                bitrate = track?.bitrate,
                sampleRate = audioContent?.sampleRate,
                channels = audioContent?.channels,
                codec = audioContent?.codec,
                dateAddedEpochSeconds = track?.dateAdded,
                dateModifiedEpochSeconds = track?.mtime?.toLong(),
                bpm = audioFeatures?.bpm,
                bpmConfidence = audioFeatures?.bpmConfidence,
                keyName = audioFeatures?.keyName,
                scale = audioFeatures?.scale,
                keyStrength = audioFeatures?.keyStrength,
                loudnessLufs = audioFeatures?.loudnessLufs,
                danceability = audioFeatures?.danceability,
                energy = audioFeatures?.energy,
                spectralCentroid = audioFeatures?.spectralCentroid,
                onsetRate = audioFeatures?.onsetRate,
                dynamicComplexity = audioFeatures?.dynamicComplexity,
                dissonance = audioFeatures?.dissonance,
                zcr = audioFeatures?.zcr,
                mfccPresent = audioFeatures?.mfccMean != null
            )
        }
    }
}
