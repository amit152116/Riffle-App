package com.amit152116.riffle.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import com.amit152116.riffle.ui.components.RiffleTopBar
import com.amit152116.riffle.ui.components.RiffleSlider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

import androidx.compose.runtime.collectAsState
import com.amit152116.riffle.ui.player.PlayerViewModel
import com.amit152116.riffle.ui.library.LibraryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    playerViewModel: PlayerViewModel,
    libraryViewModel: LibraryViewModel,
    onBack: () -> Unit = {},
    onNavigateToBlacklist: () -> Unit = {}
) {
    val isCrossfadeEnabled by playerViewModel.isCrossfadeEnabled.collectAsState()
    val crossfadeSeconds by playerViewModel.crossfadeSeconds.collectAsState()
    val minimumDurationSeconds by libraryViewModel.minimumDurationSeconds.collectAsState()
    var pendingMinimumDuration by remember(minimumDurationSeconds) {
        mutableFloatStateOf(minimumDurationSeconds.toFloat())
    }
    val settingsListState = rememberLazyListState()
    Scaffold(
        topBar = {
            RiffleTopBar(title = "Settings", onBack = onBack)
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            state = settingsListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(20.dp)
        ) {
            item {
                SettingsGroup("Playback", contentTopPadding = 4.dp) {
                    SettingToggleItem(
                        title = "Enable Crossfade",
                        subtitle = "Smooth volume fading between tracks",
                        checked = isCrossfadeEnabled,
                        onCheckedChange = { playerViewModel.setCrossfadeEnabled(it) }
                    )
                    if (isCrossfadeEnabled) {
                        SettingSliderItem(
                            title = "Crossfade duration",
                            value = crossfadeSeconds,
                            valueRange = 1f..10f,
                            onValueChange = { playerViewModel.setCrossfadeSeconds(it) },
                            startLabel = "1s",
                            endLabel = "10s",
                            valueText = "${crossfadeSeconds.toInt()} seconds"
                        )
                    }
                    SettingToggleItem("Gapless playback", checked = true, onCheckedChange = {})
                    SettingToggleItem("Normalize volume", checked = false, onCheckedChange = {})
                }
            }
            item {
                var bpmJump by remember { mutableFloatStateOf(15f) }
                SettingsGroup("Shuffle") {
                    SettingToggleItem("Avoid recent tracks", checked = true, onCheckedChange = {})
                    SettingToggleItem("Avoid same artist (last 3)", checked = true, onCheckedChange = {})
                    SettingSliderItem(
                        title = "Max BPM jump",
                        value = bpmJump,
                        valueRange = 5f..30f,
                        onValueChange = { bpmJump = it },
                        startLabel = "5",
                        endLabel = "30 BPM",
                        valueText = "${bpmJump.toInt()} BPM"
                    )
                    SettingToggleItem("Prefer harmonic transitions", checked = true, onCheckedChange = {})
                    SettingToggleItem("Exclude duplicate recordings", checked = true, onCheckedChange = {})
                }
            }
            item {
                SettingsGroup("Library") {
                    SettingSliderItem(
                        title = "Minimum song duration",
                        subtitle = "Exclude shorter audio files from the library",
                        value = pendingMinimumDuration,
                        valueRange = 0f..60f,
                        onValueChange = { pendingMinimumDuration = it },
                        onValueChangeFinished = {
                            libraryViewModel.setMinimumDurationSeconds(pendingMinimumDuration.toInt())
                        },
                        startLabel = "Off",
                        endLabel = "60s",
                        valueText = if (pendingMinimumDuration == 0f) "Off" else "${pendingMinimumDuration.toInt()} seconds"
                    )
                    SettingActionItem("Scan directories", onClick = {})
                    SettingActionItem("Rescan library", onClick = {})
                    SettingToggleItem("Metadata enrichment", checked = true, onCheckedChange = {})
                    SettingActionItem("Feature analysis", "Run audio analysis on missing tracks", onClick = {})
                    SettingActionItem("Rebuild similarity index", onClick = {})
                    SettingActionItem("Blacklisted tracks", onClick = onNavigateToBlacklist)
                }
            }
            item {
                SettingsGroup("Appearance") {
                    SettingActionItem("Theme", "Dark", onClick = {})
                    SettingActionItem("Accent color", "Indigo", onClick = {})
                }
            }
            item {
                SettingsGroup("Advanced") {
                    SettingActionItem("Audio analysis version", "v2.1.4 (ML-enabled)", onClick = {})
                    SettingActionItem("Diagnostics", onClick = {})
                    SettingActionItem("Clear cache", "1.2 GB used", onClick = {})
                }
            }
        }
    }
}

@Composable
fun SettingsGroup(
    title: String,
    contentTopPadding: androidx.compose.ui.unit.Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
            )
        ) {
            Column(
                modifier = Modifier.padding(top = contentTopPadding),
                content = content
            )
        }
    }
}

@Composable
fun SettingSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
    )
}

@Composable
fun SettingToggleItem(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimaryContainer,
                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                uncheckedBorderColor = MaterialTheme.colorScheme.outline
            ),
            modifier = Modifier.scale(0.75f)
        )
    }
}

@Composable
fun SettingActionItem(
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SettingSliderItem(
    title: String,
    subtitle: String? = null,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (() -> Unit)? = null,
    startLabel: String? = null,
    endLabel: String? = null,
    valueText: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primaryContainer
            )
        }
        
        RiffleSlider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            startLabel = startLabel,
            endLabel = endLabel,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        )
    }
}
