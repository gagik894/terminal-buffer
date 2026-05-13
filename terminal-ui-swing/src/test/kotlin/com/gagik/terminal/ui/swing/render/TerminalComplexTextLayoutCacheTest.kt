package com.gagik.terminal.ui.swing.render

import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.awt.Font
import java.awt.font.FontRenderContext

class TerminalComplexTextLayoutCacheTest {
    @Test
    fun `constructor rejects non-positive capacities`() {
        assertThrows<IllegalArgumentException> {
            TerminalComplexTextLayoutCache(codePointCapacity = 0)
        }
        assertThrows<IllegalArgumentException> {
            TerminalComplexTextLayoutCache(clusterCapacityPerStyle = 0)
        }
    }

    @Test
    fun `code point layouts are reused without rebuilding layout objects`() {
        val fontCache = fontCache()
        val layoutCache = TerminalComplexTextLayoutCache(codePointCapacity = 4)
        val frc = FontRenderContext(null, false, false)

        val first = layoutCache.codePointLayout(0x03A9, Font.PLAIN, frc, fontCache)
        val second = layoutCache.codePointLayout(0x03A9, Font.PLAIN, frc, fontCache)

        assertSame(first, second)
    }

    @Test
    fun `code point layouts are split by style`() {
        val fontCache = fontCache()
        val layoutCache = TerminalComplexTextLayoutCache(codePointCapacity = 4)
        val frc = FontRenderContext(null, false, false)

        val plain = layoutCache.codePointLayout(0x03A9, Font.PLAIN, frc, fontCache)
        val bold = layoutCache.codePointLayout(0x03A9, Font.BOLD, frc, fontCache)
        val italic = layoutCache.codePointLayout(0x03A9, Font.ITALIC, frc, fontCache)

        assertNotSame(plain, bold)
        assertNotSame(plain, italic)
        assertSame(bold, layoutCache.codePointLayout(0x03A9, Font.BOLD, frc, fontCache))
    }

    @Test
    fun `code point layouts are bounded by lru capacity`() {
        val fontCache = fontCache()
        val layoutCache = TerminalComplexTextLayoutCache(codePointCapacity = 2)
        val frc = FontRenderContext(null, false, false)

        val omega = layoutCache.codePointLayout(0x03A9, Font.PLAIN, frc, fontCache)
        val cjk = layoutCache.codePointLayout(0x4E2D, Font.PLAIN, frc, fontCache)
        layoutCache.codePointLayout(0x03A9, Font.PLAIN, frc, fontCache)
        layoutCache.codePointLayout(0x3042, Font.PLAIN, frc, fontCache)

        assertSame(omega, layoutCache.codePointLayout(0x03A9, Font.PLAIN, frc, fontCache))
        assertNotSame(cjk, layoutCache.codePointLayout(0x4E2D, Font.PLAIN, frc, fontCache))
    }

    @Test
    fun `code point access refreshes lru order`() {
        val fontCache = fontCache()
        val layoutCache = TerminalComplexTextLayoutCache(codePointCapacity = 2)
        val frc = FontRenderContext(null, false, false)

        val omega = layoutCache.codePointLayout(0x03A9, Font.PLAIN, frc, fontCache)
        val cjk = layoutCache.codePointLayout(0x4E2D, Font.PLAIN, frc, fontCache)
        assertSame(omega, layoutCache.codePointLayout(0x03A9, Font.PLAIN, frc, fontCache))
        layoutCache.codePointLayout(0x3042, Font.PLAIN, frc, fontCache)

        assertSame(omega, layoutCache.codePointLayout(0x03A9, Font.PLAIN, frc, fontCache))
        assertNotSame(cjk, layoutCache.codePointLayout(0x4E2D, Font.PLAIN, frc, fontCache))
    }

    @Test
    fun `clear invalidates code point layouts`() {
        val fontCache = fontCache()
        val layoutCache = TerminalComplexTextLayoutCache(codePointCapacity = 4)
        val frc = FontRenderContext(null, false, false)

        val first = layoutCache.codePointLayout(0x03A9, Font.PLAIN, frc, fontCache)
        layoutCache.clear()
        val second = layoutCache.codePointLayout(0x03A9, Font.PLAIN, frc, fontCache)

        assertNotSame(first, second)
    }

    @Test
    fun `cluster layouts are reused and split by style`() {
        val fontCache = fontCache()
        val layoutCache = TerminalComplexTextLayoutCache(clusterCapacityPerStyle = 4)
        val frc = FontRenderContext(null, false, false)
        val cluster = "\u0E01\u0E34"

        val plain = layoutCache.clusterLayout(cluster, Font.PLAIN, frc, fontCache)
        val plainAgain = layoutCache.clusterLayout(cluster, Font.PLAIN, frc, fontCache)
        val bold = layoutCache.clusterLayout(cluster, Font.BOLD, frc, fontCache)

        assertSame(plain, plainAgain)
        assertNotSame(plain, bold)
    }

    @Test
    fun `cluster layouts are bounded by lru capacity per style`() {
        val fontCache = fontCache()
        val layoutCache = TerminalComplexTextLayoutCache(clusterCapacityPerStyle = 2)
        val frc = FontRenderContext(null, false, false)

        val first = layoutCache.clusterLayout("\u0E01\u0E34", Font.PLAIN, frc, fontCache)
        val second = layoutCache.clusterLayout("\u0E02\u0E34", Font.PLAIN, frc, fontCache)
        layoutCache.clusterLayout("\u0E01\u0E34", Font.PLAIN, frc, fontCache)
        layoutCache.clusterLayout("\u0E03\u0E34", Font.PLAIN, frc, fontCache)

        assertSame(first, layoutCache.clusterLayout("\u0E01\u0E34", Font.PLAIN, frc, fontCache))
        assertNotSame(second, layoutCache.clusterLayout("\u0E02\u0E34", Font.PLAIN, frc, fontCache))
    }

    @Test
    fun `cluster capacity is independent for each style`() {
        val fontCache = fontCache()
        val layoutCache = TerminalComplexTextLayoutCache(clusterCapacityPerStyle = 1)
        val frc = FontRenderContext(null, false, false)
        val cluster = "\u0E01\u0E34"

        val plain = layoutCache.clusterLayout(cluster, Font.PLAIN, frc, fontCache)
        val bold = layoutCache.clusterLayout(cluster, Font.BOLD, frc, fontCache)
        layoutCache.clusterLayout("\u0E02\u0E34", Font.PLAIN, frc, fontCache)

        assertNotSame(plain, layoutCache.clusterLayout(cluster, Font.PLAIN, frc, fontCache))
        assertSame(bold, layoutCache.clusterLayout(cluster, Font.BOLD, frc, fontCache))
    }

    @Test
    fun `clear invalidates cluster layouts`() {
        val fontCache = fontCache()
        val layoutCache = TerminalComplexTextLayoutCache(clusterCapacityPerStyle = 4)
        val frc = FontRenderContext(null, false, false)
        val cluster = "\u0E01\u0E34"

        val first = layoutCache.clusterLayout(cluster, Font.PLAIN, frc, fontCache)
        layoutCache.clear()
        val second = layoutCache.clusterLayout(cluster, Font.PLAIN, frc, fontCache)

        assertNotSame(first, second)
    }

    @Test
    fun `layouts are invalidated when font render context changes`() {
        val fontCache = fontCache()
        val layoutCache = TerminalComplexTextLayoutCache(codePointCapacity = 4)
        val firstFrc = FontRenderContext(null, false, false)
        val secondFrc = FontRenderContext(null, true, false)

        val first = layoutCache.codePointLayout(0x03A9, Font.PLAIN, firstFrc, fontCache)
        val second = layoutCache.codePointLayout(0x03A9, Font.PLAIN, secondFrc, fontCache)

        assertNotSame(first, second)
    }

    private fun fontCache(): TerminalFontCache {
        val cache = TerminalFontCache()
        cache.update(Font(Font.MONOSPACED, Font.PLAIN, 14), emptyList(), useSystemFallbackFonts = false)
        return cache
    }
}
