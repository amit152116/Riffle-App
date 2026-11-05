package com.amit152116.riffle.ui.list

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.amit152116.riffle.data.AppContainer
import com.amit152116.riffle.data.model.TrackUiModel
import com.amit152116.riffle.data.model.toTrackUiModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class TrackListViewModel(
    application: Application,
    private val listType: String
) : AndroidViewModel(application) {

    private val container = AppContainer.getInstance(application)
    private val repository = container.libraryRepository

    private val _tracks = MutableStateFlow<List<TrackUiModel>>(emptyList())
    val tracks: StateFlow<List<TrackUiModel>> = _tracks.asStateFlow()

    init {
        val flow = when (listType) {
            "last-added" -> repository.getLastAddedTracksFlow()
            "favorites" -> repository.getFavoriteTracksFlow()
            "history" -> repository.getHistoryTracksFlow()
            "most-played" -> repository.getMostPlayedTracksFlow()
            "blacklist" -> repository.getBlacklistedTracksFlow()
            else -> repository.getPresentTracksFlow()
        }

        viewModelScope.launch {
            flow.collect { trackList ->
                _tracks.value = trackList.mapNotNull { it.toTrackUiModel() }
            }
        }
    }

    fun unblacklist(track: TrackUiModel) {
        viewModelScope.launch {
            val roomId = container.resolveTrackId(track.id) ?: return@launch
            container.playbackRepository.removeBlacklisted(roomId, System.currentTimeMillis())
        }
    }

    class Factory(
        private val application: Application,
        private val listType: String
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(TrackListViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return TrackListViewModel(application, listType) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
