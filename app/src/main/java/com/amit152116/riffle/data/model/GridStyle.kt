package com.amit152116.riffle.data.model

enum class GridStyle(val label: String) {
    GRID("Normal"),
    CARD("Card"),
    CIRCULAR("Circular"),
    IMAGE("Image"),
    GRADIENT_IMAGE("Gradient Image"),
    // Keep this at the end so existing ordinal-based preferences remain valid.
    COLORED_CARD("Colored Card")
}
