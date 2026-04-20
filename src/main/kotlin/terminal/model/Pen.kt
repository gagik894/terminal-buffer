package com.gagik.terminal.model

import com.gagik.terminal.codec.AttributeCodec

/**
 * Manages the current writing attributes.
 * * Follows the Spec:
 * - 0 = Logical DEFAULT (The terminal's base color)
 * - 1..16 = Specific ANSI Colors
 */
internal class Pen {

    private val defaultAttr = AttributeCodec.pack(
        fg = 0, bg = 0,
        bold = false, italic = false, underline = false, protected = false
    )

    /** Current packed attributes for the pen state */
    var currentAttr: Int = defaultAttr
        private set

    /** Current blank-fill attribute with selective-erase protection stripped off. */
    val blankAttr: Int
        get() = AttributeCodec.withProtected(currentAttr, enabled = false)

    /** Whether future printed cells are protected from DECSEL/DECSED. */
    val isSelectiveEraseProtected: Boolean
        get() = AttributeCodec.isProtected(currentAttr)

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
        val safeFg = fg.coerceIn(0, AttributeCodec.MAX_ANSI_COLOR)
        val safeBg = bg.coerceIn(0, AttributeCodec.MAX_ANSI_COLOR)
        val isProtected = AttributeCodec.isProtected(currentAttr)

        currentAttr = AttributeCodec.pack(
            safeFg,
            safeBg,
            bold,
            italic,
            underline,
            protected = isProtected
        )
    }

    /** Enables or disables selective-erase protection for future printable writes (DECSCA). */
    fun setSelectiveEraseProtection(enabled: Boolean) {
        currentAttr = AttributeCodec.withProtected(currentAttr, enabled)
    }

    /**
     * Restores the pen to a previously packed attribute value.
     * Called exclusively by DECRC to reinstate a saved pen state.
     */
    fun restoreAttr(packedAttr: Int) {
        currentAttr = packedAttr
    }

    /**
     * Resets the current attributes to the default state.
     */
    fun reset() {
        currentAttr = defaultAttr
    }
}
