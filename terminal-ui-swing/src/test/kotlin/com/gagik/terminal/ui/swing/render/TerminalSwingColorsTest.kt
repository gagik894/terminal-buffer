package com.gagik.terminal.ui.swing.render

import com.gagik.terminal.render.api.TerminalColorPalette
import com.gagik.terminal.render.api.TerminalRenderAttrs
import kotlin.test.Test
import kotlin.test.assertEquals

class TerminalSwingColorsTest {
    @Test
    fun foregroundAppliesSwingFaintPolicy() {
        val palette = TerminalColorPalette(defaultForeground = 0xFF224466.toInt())
        val attrs = TerminalRenderAttrs.pack(faint = true)

        assertEquals(0xFF112233.toInt(), TerminalSwingColors.foreground(palette, attrs))
    }

    @Test
    fun invisibleForegroundMatchesBackgroundEvenWhenFaint() {
        val palette = TerminalColorPalette(
            defaultForeground = 0xFF224466.toInt(),
            defaultBackground = 0xFF102030.toInt(),
        )
        val attrs = TerminalRenderAttrs.pack(faint = true, invisible = true)

        assertEquals(TerminalSwingColors.background(palette, attrs), TerminalSwingColors.foreground(palette, attrs))
    }
}
