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
 * [dominantColor] is the swatch with the largest pixel population. [themeColor]
 * follows RetroMusicPlayer's preferred swatch order, which favors vivid colors
 * while still working for artwork that only contains muted colors.
 */
data class ArtworkColors(
    @ColorInt val dominantColor: Int,
    @ColorInt val themeColor: Int,
    @ColorInt val backgroundColor: Int,
    @ColorInt val playerAccentColor: Int,
    @ColorInt val gradientStartColor: Int,
    @ColorInt val gradientEndColor: Int,
    @ColorInt val primaryTextColor: Int,
    @ColorInt val secondaryTextColor: Int,
    val isLightBackground: Boolean,
    val palette: Palette?
)

/** Extracts reusable theme colors from track artwork. */
object ArtworkColorExtractor {

    @ColorInt
    private const val DEFAULT_THEME_COLOR = Color.DKGRAY

    @ColorInt
    private const val DEFAULT_PLAYER_COLOR = 0xFF191414.toInt()

    @ColorInt
    private const val DEFAULT_BACKGROUND_COLOR = Color.BLACK

    /**
     * Generates colors from [bitmap]. Palette generation is synchronous and should
     * be called from a worker thread when processing artwork during UI work.
     */
    @WorkerThread
    fun extract(
        bitmap: Bitmap?,
        @ColorInt fallbackColor: Int = DEFAULT_THEME_COLOR
    ): ArtworkColors {
        if (bitmap == null) {
            return fallbackColors(fallbackColor)
        }

        val preparedBitmap = resizeForRetroPalette(bitmap)
        val backgroundSelection = selectRetroBackground(preparedBitmap)
        val backgroundColor = backgroundSelection.color
        val palette = Palette.from(preparedBitmap)
            .setRegion(
                (preparedBitmap.width * TEXT_COLOR_START_FRACTION).toInt(),
                0,
                preparedBitmap.width,
                preparedBitmap.height
            )
            .resizeBitmapArea(RETRO_PALETTE_AREA)
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

        val dominantColor = palette.dominantSwatch?.rgb
            ?: mostPopulatedSwatch(palette)?.rgb
            ?: fallbackColor

        val foregroundColor = selectRetroForeground(backgroundColor, palette)
        val readableColors = ensureRetroTextColors(backgroundColor, foregroundColor)
        val themeColor = backgroundColor
        // The player has a different color contract from Retro's colored cards.
        // Use the complete artwork's population dominant color here. The card
        // color intentionally keeps the left-half Retro extraction above.
        val playerAccentColor = selectPlayerDominantColor(preparedBitmap, DEFAULT_PLAYER_COLOR)
        val gradientEndColor = secondGradientColor(palette, backgroundColor)
        val isLightBackground = isColorLight(backgroundColor)

        return ArtworkColors(
            dominantColor = dominantColor,
            themeColor = themeColor,
            backgroundColor = backgroundColor,
            playerAccentColor = playerAccentColor,
            gradientStartColor = themeColor,
            gradientEndColor = gradientEndColor,
            primaryTextColor = readableColors.first,
            secondaryTextColor = readableColors.second,
            isLightBackground = isLightBackground,
            palette = palette
        )
    }

    /** Returns a dark artwork-derived color suitable for the player background. */
    @ColorInt
    fun darkPlayerBackground(@ColorInt artworkColor: Int): Int {
        var color = ColorUtils.blendARGB(artworkColor, Color.BLACK, 0.35f)
        while (ColorUtils.calculateLuminance(color) > PLAYER_MAX_LUMINANCE) {
            color = ColorUtils.blendARGB(color, Color.BLACK, 0.15f)
        }
        return color
    }

    /** Returns the darker end of the player background gradient. */
    @ColorInt
    fun darkPlayerBackgroundEnd(@ColorInt artworkColor: Int): Int =
        Color.BLACK

    private fun resizeForRetroPalette(bitmap: Bitmap): Bitmap {
        val area = bitmap.width * bitmap.height
        if (area == RETRO_PALETTE_AREA) return bitmap
        val factor = sqrt(RETRO_PALETTE_AREA.toDouble() / area.toDouble())
        val width = (bitmap.width * factor).toInt().coerceAtLeast(1)
        val height = (bitmap.height * factor).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun selectRetroBackground(bitmap: Bitmap): BackgroundSelection {
        val palette = Palette.from(bitmap)
            .setRegion(0, 0, bitmap.width / 2, bitmap.height)
            .clearFilters()
            .resizeBitmapArea(RETRO_PALETTE_AREA)
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

    private fun selectRetroForeground(backgroundColor: Int, palette: Palette): Int {
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
        swatch != null && swatch.population / RETRO_PALETTE_AREA.toFloat() > MINIMUM_IMAGE_FRACTION

    private fun isColorLight(@ColorInt color: Int): Boolean =
        ColorUtils.calculateLuminance(color) > 0.5

    private fun isWhiteOrBlack(hsl: FloatArray): Boolean =
        hsl[2] <= BLACK_MAX_LIGHTNESS || hsl[2] >= WHITE_MIN_LIGHTNESS

    private fun ensureRetroTextColors(
        @ColorInt backgroundColor: Int,
        @ColorInt foregroundColor: Int
    ): Pair<Int, Int> {
        val backgroundLuminance = ColorUtils.calculateLuminance(backgroundColor)
        val textLuminance = ColorUtils.calculateLuminance(foregroundColor)
        val contrast = ColorUtils.calculateContrast(foregroundColor, backgroundColor)
        val backgroundLight = (
            backgroundLuminance > textLuminance &&
                ColorUtils.calculateContrast(Color.BLACK, backgroundColor) >= MIN_CONTRAST
            ) || (
            backgroundLuminance <= textLuminance &&
                ColorUtils.calculateContrast(Color.WHITE, backgroundColor) < MIN_CONTRAST
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
        if (ColorUtils.calculateContrast(secondary, backgroundColor) < MIN_CONTRAST) {
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
        if (ColorUtils.calculateContrast(color, other) >= minRatio) return color
        val lab = DoubleArray(3)
        ColorUtils.colorToLAB(if (findForeground) color else other, lab)
        var low = 0.0
        var high = lab[0]
        val a = lab[1]
        val b = lab[2]
        var foreground = if (findForeground) color else other
        var background = if (findForeground) other else color
        repeat(15) {
            if (high - low <= 0.00001) return@repeat
            val lightness = (low + high) / 2
            val candidate = ColorUtils.LABToColor(lightness, a, b)
            if (findForeground) foreground = candidate else background = candidate
            if (ColorUtils.calculateContrast(foreground, background) > minRatio) low = lightness else high = lightness
        }
        return ColorUtils.LABToColor(low, a, b)
    }

    private fun findContrastColorAgainstDark(
        @ColorInt color: Int,
        @ColorInt other: Int,
        findForeground: Boolean,
        minRatio: Double
    ): Int {
        if (ColorUtils.calculateContrast(color, other) >= minRatio) return color
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(if (findForeground) color else other, hsl)
        var low = hsl[2]
        var high = 1f
        var foreground = if (findForeground) color else other
        var background = if (findForeground) other else color
        repeat(15) {
            if (high - low <= 0.00001f) return@repeat
            val lightness = (low + high) / 2
            hsl[2] = lightness
            val candidate = ColorUtils.HSLToColor(hsl)
            if (findForeground) foreground = candidate else background = candidate
            if (ColorUtils.calculateContrast(foreground, background) > minRatio) high = lightness else low = lightness
        }
        hsl[2] = low
        return ColorUtils.HSLToColor(hsl)
    }

    private fun changeColorLightness(@ColorInt color: Int, amount: Int): Int {
        val lab = DoubleArray(3)
        ColorUtils.colorToLAB(color, lab)
        lab[0] = (lab[0] + amount).coerceIn(0.0, 100.0)
        return ColorUtils.LABToColor(lab[0], lab[1], lab[2])
    }

    private data class BackgroundSelection(@ColorInt val color: Int, val hsl: FloatArray?)

    @ColorInt
    private fun secondGradientColor(palette: Palette, @ColorInt startColor: Int): Int {
        val candidate = palette.swatches
            .asSequence()
            .filter { it.rgb != startColor }
            .maxByOrNull { swatch ->
                swatch.population.toLong() * colorDistanceSquared(startColor, swatch.rgb)
            }

        return candidate?.rgb ?: ColorUtils.blendARGB(startColor, Color.BLACK, 0.35f)
    }

    @ColorInt
    private fun selectPlayerDominantColor(bitmap: Bitmap, @ColorInt fallbackColor: Int): Int {
        val palette = Palette.from(bitmap)
            .clearFilters()
            .resizeBitmapArea(RETRO_PALETTE_AREA)
            .generate()

        val readableCandidate = palette.swatches
            .asSequence()
            .filter { isUsablePlayerColor(it) }
            .sortedByDescending { it.population }
            .firstOrNull { swatch ->
                val playerColor = darkPlayerBackground(swatch.rgb)
                ColorUtils.calculateContrast(Color.WHITE, playerColor) >= MIN_CONTRAST
            }

        return readableCandidate?.rgb ?: fallbackColor
    }

    /**
     * Very dark neutral artwork can dominate the histogram while producing no
     * visible hue after the player background is darkened. Light artwork can
     * also make the white player foreground hard to read. Skip every swatch
     * outside the usable player lightness range so the next dominant color can
     * provide the visible artwork hue.
     */
    private fun isUsablePlayerColor(swatch: Palette.Swatch): Boolean {
        val hsl = swatch.hsl
        val darkNeutral = hsl[2] < PLAYER_MIN_LIGHTNESS && hsl[1] < PLAYER_MIN_SATURATION
        val lightPlayerColor = hsl[2] > PLAYER_MAX_LIGHTNESS
        return !isWhiteOrBlack(hsl) && !darkNeutral && !lightPlayerColor
    }

    private fun colorDistanceSquared(first: Int, second: Int): Long {
        val red = Color.red(first) - Color.red(second)
        val green = Color.green(first) - Color.green(second)
        val blue = Color.blue(first) - Color.blue(second)
        return red.toLong() * red + green.toLong() * green + blue.toLong() * blue
    }

    private fun fallbackColors(@ColorInt fallbackColor: Int): ArtworkColors {
        val backgroundColor = Color.BLACK
        val secondaryColor = ColorUtils.blendARGB(fallbackColor, Color.BLACK, 0.35f)
        return ArtworkColors(
            dominantColor = fallbackColor,
            themeColor = fallbackColor,
            backgroundColor = backgroundColor,
            playerAccentColor = DEFAULT_PLAYER_COLOR,
            gradientStartColor = fallbackColor,
            gradientEndColor = secondaryColor,
            primaryTextColor = Color.WHITE,
            secondaryTextColor = Color.LTGRAY,
            isLightBackground = false,
            palette = null
        )
    }

    private fun mostPopulatedSwatch(palette: Palette): Palette.Swatch? =
        palette.swatches.maxByOrNull { it.population }

    private const val RETRO_PALETTE_AREA = 150 * 150
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
    private const val PLAYER_MAX_LUMINANCE = 0.35
    private const val LIGHTNESS_TEXT_DIFFERENCE_LIGHT = 20
    private const val LIGHTNESS_TEXT_DIFFERENCE_DARK = -10
}
