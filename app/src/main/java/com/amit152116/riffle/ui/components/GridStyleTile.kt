package com.amit152116.riffle.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.amit152116.riffle.data.model.GridStyle

@Composable
fun GridStyleTile(
    title: String,
    subtitle: String?,
    artworkUri: Uri?,
    style: GridStyle,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (style) {
        GridStyle.GRID -> Column(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onClick)
        ) {
            AsyncArtwork(
                uri = artworkUri,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                shape = RoundedCornerShape(16.dp)
            )
            TileText(title, subtitle, Modifier.padding(top = 8.dp))
        }

        GridStyle.CARD -> Card(
            onClick = onClick,
            shape = RoundedCornerShape(16.dp),
            modifier = modifier.fillMaxWidth()
        ) {
            Column {
                AsyncArtwork(
                    uri = artworkUri,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                    shape = RoundedCornerShape(0.dp)
                )
                TileText(
                    title,
                    subtitle,
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(12.dp)
                )
            }
        }

        GridStyle.CIRCULAR -> Column(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onClick),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AsyncArtwork(
                uri = artworkUri,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                shape = CircleShape
            )
            TileText(title, subtitle, Modifier.padding(top = 8.dp), centered = true)
        }

        // Retro's "Image" tile is edge-to-edge with sharp corners, no card - unlike GradientImage below.
        GridStyle.IMAGE -> Box(
            modifier = modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clickable(onClick = onClick)
        ) {
            AsyncArtwork(
                uri = artworkUri,
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(0.dp)
            )
            ScrimOverlay()
            OverlayTitle(title, modifier = Modifier.align(Alignment.BottomCenter))
        }

        GridStyle.GRADIENT_IMAGE -> Card(
            onClick = onClick,
            shape = RoundedCornerShape(16.dp),
            modifier = modifier.fillMaxWidth().aspectRatio(1f)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncArtwork(
                    uri = artworkUri,
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(0.dp)
                )
                ScrimOverlay()
                OverlayTitle(title, modifier = Modifier.align(Alignment.BottomCenter))
            }
        }
    }
}

@Composable
private fun TileText(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    centered: Boolean = false
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (centered) TextAlign.Center else TextAlign.Start
            )
        }
    }
}

@Composable
private fun BoxScope.ScrimOverlay() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.4f)
            .align(Alignment.BottomCenter)
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
                )
            )
    )
}

@Composable
private fun OverlayTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = Color.White,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = modifier.padding(12.dp)
    )
}
