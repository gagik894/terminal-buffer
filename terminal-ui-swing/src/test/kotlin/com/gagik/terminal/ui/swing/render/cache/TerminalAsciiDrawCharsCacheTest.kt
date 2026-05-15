package com.gagik.terminal.ui.swing.render.cache

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.awt.Font
import java.awt.font.FontRenderContext
import java.awt.geom.AffineTransform

class TerminalAsciiDrawCharsCacheTest {
    @Test
    fun `compatible monospace font can use drawChars`() {
        val font = Font(Font.MONOSPACED, Font.PLAIN, 18)
        val frc = FontRenderContext(AffineTransform(), false, false)
        val cellWidth = fixedAsciiAdvanceOrSkip(font, frc)
        val cache = TerminalAsciiDrawCharsCache()

        assertTrue(cache.canDrawChars(font, Font.PLAIN, cellWidth, frc))
    }

    @Test
    fun `mismatched cell width cannot use drawChars`() {
        val font = Font(Font.MONOSPACED, Font.PLAIN, 18)
        val frc = FontRenderContext(AffineTransform(), false, false)
        val cellWidth = fixedAsciiAdvanceOrSkip(font, frc)
        val cache = TerminalAsciiDrawCharsCache()

        assertFalse(cache.canDrawChars(font, Font.PLAIN, cellWidth + 1, frc))
    }

    @Test
    fun `style entries are cached independently`() {
        val plain = Font(Font.MONOSPACED, Font.PLAIN, 18)
        val bold = Font(Font.MONOSPACED, Font.BOLD, 18)
        val frc = FontRenderContext(AffineTransform(), false, false)
        val plainWidth = fixedAsciiAdvanceOrSkip(plain, frc)
        val boldWidth = fixedAsciiAdvanceOrSkip(bold, frc)
        val cache = TerminalAsciiDrawCharsCache()

        assertTrue(cache.canDrawChars(plain, Font.PLAIN, plainWidth, frc))
        assertTrue(cache.canDrawChars(bold, Font.BOLD, boldWidth, frc))
        assertTrue(cache.canDrawChars(plain, Font.PLAIN, plainWidth, frc))
    }

    @Test
    fun `render context is part of compatibility key`() {
        val font = Font(Font.MONOSPACED, Font.PLAIN, 18)
        val firstContext = FontRenderContext(AffineTransform(), false, false)
        val secondContext = FontRenderContext(AffineTransform.getScaleInstance(2.0, 2.0), false, false)
        val cache = TerminalAsciiDrawCharsCache()

        val firstWidth = fixedAsciiAdvanceOrSkip(font, firstContext)
        val secondWidth = fixedAsciiAdvanceOrSkip(font, secondContext)

        assertTrue(cache.canDrawChars(font, Font.PLAIN, firstWidth, firstContext))
        assertTrue(cache.canDrawChars(font, Font.PLAIN, secondWidth, secondContext))
    }

    private fun fixedAsciiAdvanceOrSkip(font: Font, frc: FontRenderContext): Int {
        val probe = CharArray(1)
        probe[0] = ' '.code.toChar()
        val firstAdvance = font.createGlyphVector(frc, probe).getGlyphPosition(1).x
        assumeTrue(firstAdvance == firstAdvance.toInt().toDouble(), "test font advance is fractional")

        var codepoint = 0x21
        while (codepoint <= 0x7e) {
            probe[0] = codepoint.toChar()
            val advance = font.createGlyphVector(frc, probe).getGlyphPosition(1).x
            assumeTrue(advance == firstAdvance, "test font is not fixed-width for printable ASCII")
            codepoint++
        }

        return firstAdvance.toInt()
    }
}
