package com.gagik.core.model

/**
 * Public representation of cell attributes for UI/rendering.
 */
data class Attributes(
    val fg: Int,
    val bg: Int,
    val bold: Boolean,
    val italic: Boolean,
    val underline: Boolean,
    val selectiveEraseProtected: Boolean = false
)

