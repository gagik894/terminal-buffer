package com.gagik.core.model

/**
 * Public representation of cell attributes for UI/rendering.
 */
data class Attributes(
    val foreground: AttributeColor,
    val background: AttributeColor,
    val bold: Boolean,
    val italic: Boolean,
    val underline: Boolean,
    val selectiveEraseProtected: Boolean = false,
    val inverse: Boolean = false
)

/**
 * Renderer-facing color descriptor for a cell attribute.
 *
 * [value] is unused for [AttributeColorKind.DEFAULT], is `0..255` for
 * [AttributeColorKind.INDEXED], and is `0xRRGGBB` for [AttributeColorKind.RGB].
 */
data class AttributeColor(
    val kind: AttributeColorKind,
    val value: Int = 0
) {
    init {
        when (kind) {
            AttributeColorKind.DEFAULT -> require(value == 0) {
                "default color value must be 0, was $value"
            }
            AttributeColorKind.INDEXED -> require(value in 0..255) {
                "indexed color value must be in 0..255, was $value"
            }
            AttributeColorKind.RGB -> require(value in 0..0xFF_FF_FF) {
                "RGB color value must be in 0x000000..0xFFFFFF, was $value"
            }
        }
    }

    companion object {
        val DEFAULT = AttributeColor(AttributeColorKind.DEFAULT)

        fun indexed(index: Int): AttributeColor = AttributeColor(AttributeColorKind.INDEXED, index)

        fun rgb(red: Int, green: Int, blue: Int): AttributeColor {
            require(red in 0..255) { "red must be in 0..255, was $red" }
            require(green in 0..255) { "green must be in 0..255, was $green" }
            require(blue in 0..255) { "blue must be in 0..255, was $blue" }
            return AttributeColor(AttributeColorKind.RGB, (red shl 16) or (green shl 8) or blue)
        }

        fun rgb(rgb: Int): AttributeColor = AttributeColor(AttributeColorKind.RGB, rgb)
    }
}

enum class AttributeColorKind {
    DEFAULT,
    INDEXED,
    RGB
}
