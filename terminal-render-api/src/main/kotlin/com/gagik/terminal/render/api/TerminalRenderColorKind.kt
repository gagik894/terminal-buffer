package com.gagik.terminal.render.api

/**
 * Color encoding kind used by [TerminalRenderAttrs].
 */
object TerminalRenderColorKind {
    /**
     * Terminal default color. The associated value must be zero.
     */
    const val DEFAULT: Int = 0

    /**
     * Indexed palette color. The associated value is in `0..255`.
     */
    const val INDEXED: Int = 1

    /**
     * Direct RGB color. The associated value is encoded as `0xRRGGBB`.
     */
    const val RGB: Int = 2
}
