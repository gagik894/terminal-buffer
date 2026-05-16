package com.gagik.terminal.render.api
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TerminalColorPaletteTest {
    @Test
    fun defaultPaletteContains256IndexedColors() {
        assertEquals(256, TerminalColorPalette.defaultIndexedColors().size)
    }

    @Test
    fun resolvesDefaultForegroundAndBackground() {
        val palette = TerminalColorPalette(
            defaultForeground = 0xFF010203.toInt(),
            defaultBackground = 0xFF040506.toInt(),
        )

        assertEquals(0xFF010203.toInt(), palette.foreground(TerminalRenderAttrs.DEFAULT))
        assertEquals(0xFF040506.toInt(), palette.background(TerminalRenderAttrs.DEFAULT))
    }

    @Test
    fun boldForegroundUsesBrightAnsiColorWhenEnabled() {
        val colors = IntArray(256) { 0xFF000000.toInt() or it }
        val palette = TerminalColorPalette(indexedColors = colors, boldAsBright = true)
        val attrs = TerminalRenderAttrs.pack(
            foregroundKind = TerminalRenderColorKind.INDEXED,
            foregroundValue = 2,
            bold = true,
        )

        assertEquals(colors[10], palette.foreground(attrs))
    }

    @Test
    fun copiesIndexedColorsOnConstructionAndExplicitArrayExport() {
        val colors = IntArray(256) { 0xFF000000.toInt() or it }
        val palette = TerminalColorPalette(indexedColors = colors)

        colors[4] = 0xFFFF0000.toInt()
        val exposed = palette.toIndexedColorsArray()
        exposed[4] = 0xFF00FF00.toInt()

        assertEquals(0xFF000004.toInt(), palette.indexedColor(4))
        assertEquals(0xFF000004.toInt(), palette.toIndexedColorsArray()[4])
    }

    @Test
    fun copiesIndexedColorsIntoCallerOwnedBuffer() {
        val colors = IntArray(256) { 0xFF000000.toInt() or it }
        val palette = TerminalColorPalette(indexedColors = colors)
        val destination = IntArray(300) { -1 }

        palette.copyIndexedColorsInto(destination, offset = 10)

        assertEquals(-1, destination[9])
        assertEquals(colors[0], destination[10])
        assertEquals(colors[255], destination[265])
        assertEquals(-1, destination[266])
    }

    @Test
    fun copyIndexedColorsIntoRejectsTooSmallDestination() {
        val palette = TerminalColorPalette()

        val error = assertFailsWith<IllegalArgumentException> {
            palette.copyIndexedColorsInto(IntArray(255))
        }

        assertTrue(error.message!!.contains("insufficient capacity"))
    }

    @Test
    fun inverseSwapsForegroundAndBackground() {
        val palette = TerminalColorPalette(
            defaultForeground = 0xFF111111.toInt(),
            defaultBackground = 0xFF222222.toInt(),
        )
        val attrs = TerminalRenderAttrs.pack(inverse = true)

        assertEquals(0xFF222222.toInt(), palette.foreground(attrs))
        assertEquals(0xFF111111.toInt(), palette.background(attrs))
    }

    @Test
    fun rejectsWrongIndexedPaletteSize() {
        assertFailsWith<IllegalArgumentException> {
            TerminalColorPalette(indexedColors = IntArray(16))
        }
    }
}
