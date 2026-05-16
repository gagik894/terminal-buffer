package com.gagik.terminal.ui.swing.render

import com.gagik.terminal.render.api.TerminalColorPalette
import com.gagik.terminal.render.api.TerminalRenderAttrs

/**
 * Swing-specific visual interpretation of render colors.
 */
internal object TerminalSwingColors {
    /**
     * Resolves the foreground color Swing should paint for [attrWord].
     */
    fun foreground(palette: TerminalColorPalette, attrWord: Long): Int {
        if (TerminalRenderAttrs.isInvisible(attrWord)) {
            return background(palette, attrWord)
        }

        val color = palette.foreground(attrWord)
        return if (TerminalRenderAttrs.isFaint(attrWord)) dim(color) else color
    }

    /**
     * Resolves the background color Swing should paint for [attrWord].
     */
    fun background(palette: TerminalColorPalette, attrWord: Long): Int {
        return palette.background(attrWord)
    }

    /**
     * Applies Swing's current faint rendering policy to a packed ARGB color.
     */
    fun dim(color: Int): Int {
        val alpha = color and 0xFF000000.toInt()
        val red = ((color ushr 16) and 0xFF) / 2
        val green = ((color ushr 8) and 0xFF) / 2
        val blue = (color and 0xFF) / 2
        return alpha or (red shl 16) or (green shl 8) or blue
    }
}
