package com.amit152116.riffle.ui.components

import android.net.Uri
import androidx.core.graphics.drawable.toBitmap
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
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.imageLoader
import coil.request.ImageRequest
import com.amit152116.riffle.data.color.ArtworkColorExtractor
import com.amit152116.riffle.data.color.ArtworkColors
import com.amit152116.riffle.data.model.GridStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun GridStyleTile(
    title: String,
    subtitle: String?,
    artworkUri: Uri?,
    style: GridStyle,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fallbackIcon: Any? = null
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
                shape = RoundedCornerShape(16.dp),
                fallbackIcon = fallbackIcon
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
                        .aspectRatio(1.08f),
                    shape = RoundedCornerShape(0.dp),
                    fallbackIcon = fallbackIcon
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
                shape = CircleShape,
                fallbackIcon = fallbackIcon
            )
            TileText(title, subtitle, Modifier.padding(top = 8.dp), centered = true)
        }

        // Image tiles are edge-to-edge with sharp corners, unlike gradient image tiles.
        GridStyle.IMAGE -> Box(
            modifier = modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clickable(onClick = onClick)
        ) {
            AsyncArtwork(
                uri = artworkUri,
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(0.dp),
                fallbackIcon = fallbackIcon
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
                    shape = RoundedCornerShape(0.dp),
                    fallbackIcon = fallbackIcon
                )
                ScrimOverlay()
                OverlayTitle(title, modifier = Modifier.align(Alignment.BottomCenter))
            }
        }

        GridStyle.COLORED_CARD -> {
            val colors = rememberArtworkColors(artworkUri)
            ColoredCardTile(
                title = title,
                subtitle = subtitle,
                artworkUri = artworkUri,
                colors = colors,
                onClick = onClick,
                modifier = modifier
            )
        }
    }
}

@Composable
fun rememberArtworkColors(artworkUri: Uri?): ArtworkColors {
    val context = LocalContext.current.applicationContext
    return produceState(
        initialValue = ArtworkColorExtractor.extract(null),
        key1 = artworkUri
    ) {
        value = withContext(Dispatchers.IO) {
            val bitmap = artworkUri?.let { uri ->
                runCatching {
                    context.imageLoader
                        .execute(
                            ImageRequest.Builder(context)
                                .data(uri)
                                .allowHardware(false)
                                .build()
                        )
                        .drawable
                        ?.toBitmap()
                }.getOrNull()
            }
            withContext(Dispatchers.Default) {
                ArtworkColorExtractor.extract(bitmap)
            }
        }
    }.value
}

@Composable
private fun ColoredCardTile(
    title: String,
    subtitle: String?,
    artworkUri: Uri?,
    colors: ArtworkColors,
    onClick: () -> Unit,
    modifier: Modifier
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = Color(colors.backgroundColor)
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            AsyncArtwork(
                uri = artworkUri,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.08f),
                shape = RoundedCornerShape(0.dp)
            )
            TileText(
                title = title,
                subtitle = subtitle,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                titleColor = Color(colors.primaryTextColor),
                subtitleColor = Color(colors.secondaryTextColor)
            )
        }
    }
}

@Composable
private fun TileText(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    centered: Boolean = false,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = titleColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = subtitleColor,
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
