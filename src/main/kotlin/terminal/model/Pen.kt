package com.gagik.terminal.model

import com.gagik.terminal.codec.AttributeCodec

/**
 * Manages the current writing attributes.
 * * Follows the Spec:
 * - 0 = Logical DEFAULT (The terminal's base color)
 * - 1..16 = Specific ANSI Colors
 */
class Pen {
    private val defaultAttr = AttributeCodec.pack(
        fg = 0, bg = 0,
        bold = false, italic = false, underline = false
    )

    var currentAttr: Int = defaultAttr // Packed attributes for the current pen state
        private set

    /**
     * Sets the current drawing style.
     * @param fg 0 for Default, 1-16 for ANSI colors
     * @param bg 0 for Default, 1-16 for ANSI colors
     * @param bold Whether the text should be bold
     * @param italic Whether the text should be italic
     * @param underline Whether the text should be underlined
     */
    fun setAttributes(
        fg: Int,
        bg: Int,
        bold: Boolean = false,
        italic: Boolean = false,
        underline: Boolean = false
    ) {
        val safeFg = fg.coerceIn(0, 16)
        val safeBg = bg.coerceIn(0, 16)

        currentAttr = AttributeCodec.pack(safeFg, safeBg, bold, italic, underline)
    }

    /**
     * Resets the current attributes to the default state.
     */
    fun reset() {
        currentAttr = defaultAttr
    }
}