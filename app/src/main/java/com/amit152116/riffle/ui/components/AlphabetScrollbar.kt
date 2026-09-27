package com.amit152116.riffle.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.delay

private const val MINIMUM_ITEMS_FOR_ALPHABET = 20

@Composable
fun AlphabetScrollbar(
    labels: List<String>,
    onLetterSelected: (Char) -> Unit,
    modifier: Modifier = Modifier
) {
    if (labels.size < MINIMUM_ITEMS_FOR_ALPHABET) return

    val letters = remember(labels) {
        buildList {
            if (labels.any { it.firstOrNull()?.uppercaseChar() !in 'A'..'Z' }) add('#')
            addAll(('A'..'Z').filter { letter ->
                labels.any { it.firstOrNull()?.uppercaseChar() == letter }
            })
        }
    }
    if (letters.isEmpty()) return

    var activeLetter by remember { mutableStateOf<Char?>(null) }

    fun selectLetter(y: Float, height: Float) {
        val index = ((y / height) * letters.size)
            .toInt()
            .coerceIn(0, letters.lastIndex)
        val letter = letters[index]
        activeLetter = letter
        onLetterSelected(letter)
    }

    LaunchedEffect(activeLetter) {
        if (activeLetter != null) {
            delay(650)
            activeLetter = null
        }
    }

    Box(
        modifier = modifier
            .width(18.dp)
            .fillMaxHeight(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .wrapContentHeight()
                .pointerInput(letters) {
                    detectDragGestures(
                        onDragStart = { offset -> selectLetter(offset.y, size.height.toFloat()) },
                        onDrag = { change, _ ->
                            selectLetter(change.position.y, size.height.toFloat())
                        }
                    )
                },
            verticalArrangement = Arrangement.spacedBy(5.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            letters.forEach { letter ->
                Text(
                    text = letter.toString(),
                    color = if (letter == activeLetter) {
                        Color.White
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 8.sp,
                        fontWeight = if (letter == activeLetter) FontWeight.Bold else FontWeight.Normal
                    )
                )
            }
        }

        AnimatedVisibility(
            visible = activeLetter != null,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = (-50).dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = activeLetter?.toString().orEmpty(),
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}
