package com.amit152116.riffle.data.color

import android.graphics.Bitmap
import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.annotation.WorkerThread
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import kotlin.math.sqrt

/**
 * Colors extracted from a track's album artwork.
 *
 * Provides artwork-derived colors for colored cards and the player surfaces.
 */
data class ArtworkColors(
    @ColorInt val backgroundColor: Int,
    @ColorInt val playerAccentColor: Int,
    @ColorInt val primaryTextColor: Int,
    @ColorInt val secondaryTextColor: Int
)

/** Extracts reusable theme colors from track artwork. */
object ArtworkColorExtractor {

    @ColorInt
    private const val DEFAULT_PLAYER_COLOR = 0xFF191414.toInt()


    @ColorInt
    private fun Int.toOpaque(): Int = ColorUtils.setAlphaComponent(this, 255)

    private fun calculateContrast(@ColorInt foreground: Int, @ColorInt background: Int): Double {
        return try {
            ColorUtils.calculateContrast(foreground.toOpaque(), background.toOpaque())
        } catch (_: IllegalArgumentException) {
            1.0
        }
    }

    private fun calculateLuminance(@ColorInt color: Int): Double =
        ColorUtils.calculateLuminance(color.toOpaque())

    /**
     * Generates colors from [bitmap]. Palette generation is synchronous and should
     * be called from a worker thread when processing artwork during UI work.
     */
    @WorkerThread
    fun extract(bitmap: Bitmap?): ArtworkColors {
        if (bitmap == null) {
            return fallbackColors()
        }

        val preparedBitmap = resizeForPalette(bitmap)
        val backgroundSelection = selectArtworkBackground(preparedBitmap)
        val backgroundColor = backgroundSelection.color
        val palette = Palette.from(preparedBitmap)
            .setRegion(
                (preparedBitmap.width * TEXT_COLOR_START_FRACTION).toInt(),
                0,
                preparedBitmap.width,
                preparedBitmap.height
            )
            .resizeBitmapArea(PALETTE_SAMPLE_AREA)
            .apply {
                backgroundSelection.hsl?.let { backgroundHsl ->
                    addFilter { _, hsl ->
                        val difference = kotlin.math.abs(hsl[0] - backgroundHsl[0])
                        difference > 10f && difference < 350f
                    }
                }
                addFilter { _, hsl -> !isWhiteOrBlack(hsl) }
            }
            .generate()

        val foregroundColor = selectArtworkForeground(backgroundColor, palette)
        val readableColors = ensureReadableTextColors(backgroundColor, foregroundColor)
        // The player uses the complete artwork while colored cards use the
        // left side of the artwork for their background color.
        val playerAccentColor = selectPlayerDominantColor(preparedBitmap, DEFAULT_PLAYER_COLOR)

        return ArtworkColors(
            backgroundColor = backgroundColor.toOpaque(),
            playerAccentColor = playerAccentColor.toOpaque(),
            primaryTextColor = readableColors.first.toOpaque(),
            secondaryTextColor = readableColors.second.toOpaque()
        )
    }

    /** Returns a dark artwork-derived color suitable for the player background. */
    @ColorInt
    fun darkPlayerBackground(@ColorInt artworkColor: Int): Int {
        var color = artworkColor
        while (shouldDarkenPlayerColor(color)) {
            color = darkenPlayerColor(
                color,
                PLAYER_MIN_DARKEN_BLEND,
                PLAYER_DARKEN_BLEND
            )
        }
        return color.toOpaque()
    }

    /** Returns a more strongly darkened artwork color for the mini player. */
    @ColorInt
    fun darkMiniPlayerBackground(@ColorInt artworkColor: Int): Int {
        var color = artworkColor
        while (shouldDarkenPlayerColor(color)) {
            color = darkenPlayerColor(
                color,
                MINI_PLAYER_MIN_DARKEN_BLEND,
                MINI_PLAYER_DARKEN_BLEND
            )
        }
        return color.toOpaque()
    }

    @ColorInt
    private fun darkenPlayerColor(
        @ColorInt color: Int,
        minBlend: Float,
        maxBlend: Float
    ): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        val luminanceExcess = (
            calculateLuminance(color) / PLAYER_MAX_LUMINANCE - 1.0
            ).coerceIn(0.0, 1.0)
        val valueExcess = (
            hsv[2] / PLAYER_MAX_VALUE - 1.0f
            ).coerceIn(0.0f, 1.0f).toDouble()
        val severity = maxOf(luminanceExcess, valueExcess)
        val blend = minBlend + (maxBlend - minBlend) * severity.toFloat()
        return ColorUtils.blendARGB(color, Color.BLACK, blend).toOpaque()
    }

    private fun shouldDarkenPlayerColor(@ColorInt color: Int): Boolean {
        val luminanceTooHigh =
            calculateLuminance(color) > PLAYER_MAX_LUMINANCE
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        val vividColorTooBright =
            hsv[2] > PLAYER_MAX_VALUE && hsv[1] > PLAYER_MIN_SATURATION_FOR_BRIGHTNESS
        return luminanceTooHigh || vividColorTooBright
    }

    /** Returns the darker end of the player background gradient. */
    @ColorInt
    fun darkPlayerBackgroundEnd(@ColorInt artworkColor: Int): Int =
        Color.BLACK

    private fun resizeForPalette(bitmap: Bitmap): Bitmap {
        val area = bitmap.width * bitmap.height
        if (area == PALETTE_SAMPLE_AREA) return bitmap
        val factor = sqrt(PALETTE_SAMPLE_AREA.toDouble() / area.toDouble())
        val width = (bitmap.width * factor).toInt().coerceAtLeast(1)
        val height = (bitmap.height * factor).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun selectArtworkBackground(bitmap: Bitmap): BackgroundSelection {
        val palette = Palette.from(bitmap)
            .setRegion(0, 0, bitmap.width / 2, bitmap.height)
            .clearFilters()
            .resizeBitmapArea(PALETTE_SAMPLE_AREA)
            .generate()
        val dominant = palette.dominantSwatch
            ?: return BackgroundSelection(Color.WHITE, null)

        if (!isWhiteOrBlack(dominant.hsl)) {
            return BackgroundSelection(dominant.rgb, dominant.hsl)
        }

        val second = palette.swatches
            .asSequence()
            .filter { it !== dominant && !isWhiteOrBlack(it.hsl) }
            .maxByOrNull { it.population }

        if (second == null || dominant.population / second.population.toFloat() > WHITE_BLACK_POPULATION_RATIO) {
            return BackgroundSelection(dominant.rgb, null)
        }
        return BackgroundSelection(second.rgb, second.hsl)
    }

    private fun selectArtworkForeground(backgroundColor: Int, palette: Palette): Int {
        val fallback = if (isColorLight(backgroundColor)) Color.BLACK else Color.WHITE
        val dominant = palette.dominantSwatch
        val candidate = if (isColorLight(backgroundColor)) {
            selectVibrantCandidate(palette.darkVibrantSwatch, palette.vibrantSwatch)
                ?: selectMutedCandidate(palette.mutedSwatch, palette.darkMutedSwatch)
        } else {
            selectVibrantCandidate(palette.lightVibrantSwatch, palette.vibrantSwatch)
                ?: selectMutedCandidate(palette.mutedSwatch, palette.lightMutedSwatch)
        }

        if (candidate != null && dominant != null) {
            return if (candidate === dominant ||
                candidate.population / dominant.population.toFloat() >= DOMINANT_POPULATION_FRACTION ||
                dominant.hsl[1] <= MIN_SATURATION_WHEN_DECIDING
            ) {
                candidate.rgb
            } else {
                dominant.rgb
            }
        }
        return if (dominant != null && hasEnoughPopulation(dominant)) dominant.rgb else fallback
    }

    private fun selectVibrantCandidate(first: Palette.Swatch?, second: Palette.Swatch?): Palette.Swatch? {
        val firstValid = hasEnoughPopulation(first)
        val secondValid = hasEnoughPopulation(second)
        return when {
            firstValid && secondValid -> if (first!!.population / second!!.population.toFloat() < 1f) second else first
            firstValid -> first
            secondValid -> second
            else -> null
        }
    }

    private fun selectMutedCandidate(first: Palette.Swatch?, second: Palette.Swatch?): Palette.Swatch? {
        val firstValid = hasEnoughPopulation(first)
        val secondValid = hasEnoughPopulation(second)
        return when {
            firstValid && secondValid -> {
                if (first!!.hsl[1] * (first.population / second!!.population.toFloat()) > second.hsl[1]) first else second
            }
            firstValid -> first
            secondValid -> second
            else -> null
        }
    }

    private fun hasEnoughPopulation(swatch: Palette.Swatch?): Boolean =
        swatch != null && swatch.population / PALETTE_SAMPLE_AREA.toFloat() > MINIMUM_IMAGE_FRACTION

    private fun isColorLight(@ColorInt color: Int): Boolean =
        calculateLuminance(color) > 0.5

    private fun isWhiteOrBlack(hsl: FloatArray): Boolean =
        hsl[2] <= BLACK_MAX_LIGHTNESS || hsl[2] >= WHITE_MIN_LIGHTNESS

    private fun ensureReadableTextColors(
        @ColorInt backgroundColor: Int,
        @ColorInt foregroundColor: Int
    ): Pair<Int, Int> {
        val backgroundLuminance = calculateLuminance(backgroundColor)
        val textLuminance = calculateLuminance(foregroundColor)
        val contrast = calculateContrast(foregroundColor, backgroundColor)
        val backgroundLight = (
            backgroundLuminance > textLuminance &&
                calculateContrast(Color.BLACK, backgroundColor) >= MIN_CONTRAST
            ) || (
            backgroundLuminance <= textLuminance &&
                calculateContrast(Color.WHITE, backgroundColor) < MIN_CONTRAST
            )

        if (contrast < MIN_CONTRAST) {
            val secondary = if (backgroundLight) {
                findContrastColor(foregroundColor, backgroundColor, findForeground = true, MIN_CONTRAST)
            } else {
                findContrastColorAgainstDark(foregroundColor, backgroundColor, findForeground = true, MIN_CONTRAST)
            }
            val primary = changeColorLightness(secondary, if (backgroundLight) -LIGHTNESS_TEXT_DIFFERENCE_LIGHT else -LIGHTNESS_TEXT_DIFFERENCE_DARK)
            return primary to secondary
        }

        var secondary = changeColorLightness(
            foregroundColor,
            if (backgroundLight) LIGHTNESS_TEXT_DIFFERENCE_LIGHT else LIGHTNESS_TEXT_DIFFERENCE_DARK
        )
        var primary = foregroundColor
        if (calculateContrast(secondary, backgroundColor) < MIN_CONTRAST) {
            secondary = if (backgroundLight) {
                findContrastColor(secondary, backgroundColor, findForeground = true, MIN_CONTRAST)
            } else {
                findContrastColorAgainstDark(secondary, backgroundColor, findForeground = true, MIN_CONTRAST)
            }
            primary = changeColorLightness(secondary, if (backgroundLight) -LIGHTNESS_TEXT_DIFFERENCE_LIGHT else -LIGHTNESS_TEXT_DIFFERENCE_DARK)
        }
        return primary to secondary
    }

    private fun findContrastColor(
        @ColorInt color: Int,
        @ColorInt other: Int,
        findForeground: Boolean,
        minRatio: Double
    ): Int {
        val opaqueColor = color.toOpaque()
        val opaqueOther = other.toOpaque()
        if (calculateContrast(opaqueColor, opaqueOther) >= minRatio) return opaqueColor
        val lab = DoubleArray(3)
        ColorUtils.colorToLAB(if (findForeground) opaqueColor else opaqueOther, lab)
        var low = 0.0
        var high = lab[0]
        val a = lab[1]
        val b = lab[2]
        var foreground = if (findForeground) opaqueColor else opaqueOther
        var background = if (findForeground) opaqueOther else opaqueColor
        repeat(15) {
            if (high - low <= 0.00001) return@repeat
            val lightness = (low + high) / 2
            val candidate = ColorUtils.LABToColor(lightness, a, b).toOpaque()
            if (findForeground) foreground = candidate else background = candidate
            if (calculateContrast(foreground, background) > minRatio) low = lightness else high = lightness
        }
        return ColorUtils.LABToColor(low, a, b).toOpaque()
    }

    private fun findContrastColorAgainstDark(
        @ColorInt color: Int,
        @ColorInt other: Int,
        findForeground: Boolean,
        minRatio: Double
    ): Int {
        val opaqueColor = color.toOpaque()
        val opaqueOther = other.toOpaque()
        if (calculateContrast(opaqueColor, opaqueOther) >= minRatio) return opaqueColor
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(if (findForeground) opaqueColor else opaqueOther, hsl)
        var low = hsl[2]
        var high = 1f
        var foreground = if (findForeground) opaqueColor else opaqueOther
        var background = if (findForeground) opaqueOther else opaqueColor
        repeat(15) {
            if (high - low <= 0.00001f) return@repeat
            val lightness = (low + high) / 2
            hsl[2] = lightness
            val candidate = ColorUtils.HSLToColor(hsl).toOpaque()
            if (findForeground) foreground = candidate else background = candidate
            if (calculateContrast(foreground, background) > minRatio) high = lightness else low = lightness
        }
        hsl[2] = low
        return ColorUtils.HSLToColor(hsl).toOpaque()
    }

    private fun changeColorLightness(@ColorInt color: Int, amount: Int): Int {
        val lab = DoubleArray(3)
        ColorUtils.colorToLAB(color.toOpaque(), lab)
        lab[0] = (lab[0] + amount).coerceIn(0.0, 100.0)
        return ColorUtils.LABToColor(lab[0], lab[1], lab[2]).toOpaque()
    }

    private data class BackgroundSelection(@ColorInt val color: Int, val hsl: FloatArray?)

    @ColorInt
    private fun selectPlayerDominantColor(bitmap: Bitmap, @ColorInt fallbackColor: Int): Int {
        val palette = Palette.from(bitmap)
            .clearFilters()
            .resizeBitmapArea(PALETTE_SAMPLE_AREA)
            .generate()

        val dominant = palette.dominantSwatch
            ?: return fallbackColor

        if (isUsablePlayerColor(dominant)) {
            return dominant.rgb
        }

        val second = palette.swatches
            .asSequence()
            .filter { it !== dominant && isUsablePlayerColor(it) }
            .maxByOrNull { it.population }

        return second?.rgb ?: fallbackColor
    }

    /**
     * Very dark neutral artwork can dominate the histogram while producing no
     * visible hue after the player background is darkened. Keep dark saturated
     * colors, such as maroon, but skip near-black neutral swatches.
     */
    private fun isUsablePlayerColor(swatch: Palette.Swatch): Boolean {
        val hsl = swatch.hsl
        val darkNeutral = hsl[2] < PLAYER_MIN_LIGHTNESS && hsl[1] < PLAYER_MIN_SATURATION
        val lightPlayerColor = hsl[2] > PLAYER_MAX_LIGHTNESS
        return !isWhiteOrBlack(hsl) &&
            !darkNeutral &&
            !lightPlayerColor
    }

    private fun fallbackColors(): ArtworkColors {
        val backgroundColor = Color.BLACK
        return ArtworkColors(
            backgroundColor = backgroundColor,
            playerAccentColor = DEFAULT_PLAYER_COLOR,
            primaryTextColor = Color.WHITE,
            secondaryTextColor = Color.LTGRAY
        )
    }

    private const val PALETTE_SAMPLE_AREA = 150 * 150
    private const val TEXT_COLOR_START_FRACTION = 0.4f
    private const val MINIMUM_IMAGE_FRACTION = 0.002f
    private const val DOMINANT_POPULATION_FRACTION = 0.01f
    private const val WHITE_BLACK_POPULATION_RATIO = 2.5f
    private const val PLAYER_MIN_LIGHTNESS = 0.16f
    private const val PLAYER_MIN_SATURATION = 0.28f
    private const val PLAYER_MAX_LIGHTNESS = 0.78f
    private const val MIN_SATURATION_WHEN_DECIDING = 0.19f
    private const val BLACK_MAX_LIGHTNESS = 0.08f
    private const val WHITE_MIN_LIGHTNESS = 0.90f
    private const val MIN_CONTRAST = 4.5
    private const val PLAYER_MAX_LUMINANCE = 0.30
    private const val PLAYER_MIN_DARKEN_BLEND = 0.10f
    private const val PLAYER_MAX_VALUE = 0.78f
    private const val PLAYER_MIN_SATURATION_FOR_BRIGHTNESS = 0.40f
    private const val PLAYER_DARKEN_BLEND = 0.30f
    private const val MINI_PLAYER_MIN_DARKEN_BLEND = 0.30f
    private const val MINI_PLAYER_DARKEN_BLEND = 0.60f
    private const val LIGHTNESS_TEXT_DIFFERENCE_LIGHT = 20
    private const val LIGHTNESS_TEXT_DIFFERENCE_DARK = -10
}
