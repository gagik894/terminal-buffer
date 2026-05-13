package com.gagik.terminal.ui.swing.render

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.awt.Font

class TerminalFontCacheTest {
    @Test
    fun `font returns cached primary style variant`() {
        val base = Font(Font.MONOSPACED, Font.PLAIN, 14)
        val cache = TerminalFontCache()

        cache.update(base, emptyList())

        assertSame(base, cache.font(Font.PLAIN))
        assertSame(cache.font(Font.BOLD), cache.font(Font.BOLD))
    }

    @Test
    fun `fontForText uses configured fallback when primary cannot display text`() {
        val primary = Font("Courier New", Font.PLAIN, 17)
        val fallback = Font("Dialog", Font.PLAIN, 11)
        val thai = "\u0E01\u0E34"

        assumeTrue(primary.canDisplayUpTo(thai) >= 0)
        assumeTrue(fallback.canDisplayUpTo(thai) < 0)

        val cache = TerminalFontCache()
        cache.update(primary, listOf(fallback))

        val resolved = cache.fontForText(thai, Font.BOLD)

        assertEquals(Font.BOLD, resolved.style)
        assertEquals(primary.size2D, resolved.size2D)
        assertEquals(fallback.family, resolved.family)
    }
}
