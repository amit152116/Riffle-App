package com.amit152116.riffle.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import com.amit152116.riffle.data.AppContainer
import com.amit152116.riffle.data.model.TrackUiModel
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongDetailSheet(
    track: TrackUiModel,
    onDismiss: () -> Unit,
    viewModel: SongDetailViewModel = viewModel(
        factory = AppContainer.getInstance(LocalContext.current).songDetailViewModelFactory
    )
) {
    val detail by viewModel.detail.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(track.id) {
        viewModel.load(track.id)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
        ) {
            item { SongDetailHeader(track) }
            item { Spacer(Modifier.height(20.dp)) }
            item { QuickFactsRow(track) }
            item { Spacer(Modifier.height(20.dp)) }
            item {
                DetailGroup(
                    title = "Audio",
                    icon = Icons.Filled.GraphicEq,
                    rows = listOf(
                        "Format" to (detail?.let { formatCodec(it.codec) } ?: LOADING),
                        "Bitrate" to (detail?.let { formatBitrate(it.bitrate) } ?: LOADING),
                        "Sample rate" to (detail?.let { formatSampleRate(it.sampleRate) } ?: LOADING),
                        "Channels" to (detail?.let { it.channels?.toString() ?: "—" } ?: LOADING)
                    )
                )
            }
            item { Spacer(Modifier.height(20.dp)) }
            item {
                DetailGroup(
                    title = "Audio Features",
                    icon = Icons.Filled.Equalizer,
                    rows = listOf(
                        "BPM" to (detail?.let { formatBpm(it.bpm) } ?: LOADING),
                        "Key" to (detail?.let { formatKey(it.keyName, it.scale) } ?: LOADING),
                        "Key strength" to (detail?.let { formatFraction(it.keyStrength) } ?: LOADING),
                        "Loudness" to (detail?.let { formatLoudness(it.loudnessLufs) } ?: LOADING),
                        "Danceability" to (detail?.let { formatFraction(it.danceability) } ?: LOADING),
                        "Energy" to (detail?.let { formatFraction(it.energy) } ?: LOADING),
                        "Spectral centroid" to (detail?.let { formatSpectralCentroid(it.spectralCentroid) } ?: LOADING),
                        "Onset rate" to (detail?.let { formatOnsetRate(it.onsetRate) } ?: LOADING),
                        "Dynamic complexity" to (detail?.let { formatFraction(it.dynamicComplexity) } ?: LOADING),
                        "Dissonance" to (detail?.let { formatFraction(it.dissonance) } ?: LOADING),
                        "Zero crossing rate" to (detail?.let { formatFraction(it.zcr) } ?: LOADING),
                        "MFCC" to (detail?.let { formatMfcc(it.mfccPresent) } ?: LOADING)
                    )
                )
            }
            item { Spacer(Modifier.height(20.dp)) }
            item {
                DetailGroup(
                    title = "File",
                    icon = Icons.Filled.InsertDriveFile,
                    rows = listOf(
                        "Name" to (detail?.let { it.fileName ?: "—" } ?: LOADING),
                        "Location" to (detail?.let { formatLocation(it.location) } ?: LOADING),
                        "Size" to (detail?.let { formatFileSize(it.fileSizeBytes) } ?: LOADING)
                    )
                )
            }
            item { Spacer(Modifier.height(20.dp)) }
            item {
                DetailGroup(
                    title = "Library",
                    icon = Icons.Filled.Event,
                    rows = listOf(
                        "Date added" to (detail?.let { formatEpochSeconds(it.dateAddedEpochSeconds) } ?: LOADING),
                        "Date modified" to (detail?.let { formatEpochSeconds(it.dateModifiedEpochSeconds) } ?: LOADING)
                    )
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

private const val LOADING = "…"

@Composable
private fun SongDetailHeader(track: TrackUiModel) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AsyncArtwork(
            uri = track.artworkUri,
            modifier = Modifier.size(88.dp),
            shape = RoundedCornerShape(20.dp)
        )
        Spacer(Modifier.width(16.dp))
        Column {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (track.album.isNotBlank()) {
                Text(
                    text = track.album,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun QuickFactsRow(track: TrackUiModel) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        InfoChip(Icons.Filled.Schedule, track.trailingText)
        if (track.genre.isNotBlank()) {
            InfoChip(Icons.Filled.Label, track.genre)
        }
    }
}

@Composable
private fun InfoChip(icon: ImageVector, text: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(6.dp))
            Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun DetailGroup(title: String, icon: ImageVector, rows: List<Pair<String, String>>) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = title.uppercase(Locale.US),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                rows.forEachIndexed { index, (label, value) ->
                    DetailValueRow(label, value)
                    if (index != rows.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailValueRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1.3f)
        )
    }
}

/** [relativePath] is MediaStore's RELATIVE_PATH, e.g. "Music/Downloads/" - trailing slash reads oddly as a value. */
private fun formatLocation(relativePath: String?): String =
    relativePath?.trimEnd('/') ?: "—"

private fun formatFileSize(sizeBytes: Long?): String {
    if (sizeBytes == null) return "—"
    val mb = sizeBytes / (1024.0 * 1024.0)
    return String.format(Locale.US, "%.1f MB", mb)
}

/** [codec] is a raw MIME type from MediaExtractor (e.g. "audio/mpeg") - shown as a short label. */
private fun formatCodec(codec: String?): String {
    if (codec == null) return "Not analyzed yet"
    val subtype = codec.substringAfter('/').uppercase(Locale.US)
    return if (subtype == "MPEG") "MP3" else subtype
}

private fun formatBitrate(bitrate: Int?): String =
    if (bitrate == null) "—" else "${bitrate / 1000} kb/s"

private fun formatSampleRate(sampleRate: Int?): String =
    if (sampleRate == null) "Not analyzed yet" else "${String.format(Locale.US, "%.1f", sampleRate / 1000.0)} kHz"

private fun formatEpochSeconds(epochSeconds: Long?): String {
    if (epochSeconds == null) return "—"
    return DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(epochSeconds * 1000))
}

private const val NOT_ANALYZED = "Not analyzed yet"

private fun formatBpm(bpm: Double?): String =
    if (bpm == null) NOT_ANALYZED else String.format(Locale.US, "%.0f", bpm)

private fun formatKey(keyName: String?, scale: String?): String =
    if (keyName == null) NOT_ANALYZED else listOfNotNull(keyName, scale).joinToString(" ")

private fun formatFraction(value: Double?): String =
    if (value == null) NOT_ANALYZED else String.format(Locale.US, "%.2f", value)

private fun formatLoudness(loudnessLufs: Double?): String =
    if (loudnessLufs == null) NOT_ANALYZED else String.format(Locale.US, "%.1f LUFS", loudnessLufs)

private fun formatSpectralCentroid(spectralCentroid: Double?): String =
    if (spectralCentroid == null) {
        NOT_ANALYZED
    } else {
        "${String.format(Locale.US, "%,d", spectralCentroid.toLong())} Hz"
    }

private fun formatOnsetRate(onsetRate: Double?): String =
    if (onsetRate == null) NOT_ANALYZED else String.format(Locale.US, "%.1f / sec", onsetRate)

private fun formatMfcc(mfccPresent: Boolean): String =
    if (mfccPresent) "13-dimensional representation" else NOT_ANALYZED
