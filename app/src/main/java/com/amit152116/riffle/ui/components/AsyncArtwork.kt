package com.amit152116.riffle.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.amit152116.riffle.R

@Composable
fun AsyncArtwork(
    uri: Uri?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
    contentDescription: String? = null,
    placeholderColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    fallbackIcon: Any? = null
) {
    // clip MUST precede background, or the background paints the full
    // rectangular bounds and bleeds outside a non-rectangular shape (e.g. a circle).
    val clippedModifier = modifier.clip(shape).background(placeholderColor)
    val defaultIcon = fallbackIcon ?: R.mipmap.default_track
    if (uri != null) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(uri)
                .crossfade(true)
                .build(),
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = clippedModifier,
            error = {
                if (fallbackIcon is ImageVector) {
                    Icon(
                        imageVector = fallbackIcon,
                        contentDescription = contentDescription,
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    AsyncImage(
                        model = defaultIcon,
                        contentDescription = contentDescription,
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        )
    } else {
        if (fallbackIcon is ImageVector) {
            Icon(
                imageVector = fallbackIcon,
                contentDescription = contentDescription,
                modifier = clippedModifier.padding(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            AsyncImage(
                model = defaultIcon,
                contentDescription = contentDescription,
                contentScale = ContentScale.FillBounds,
                modifier = clippedModifier
            )
        }
    }
}
