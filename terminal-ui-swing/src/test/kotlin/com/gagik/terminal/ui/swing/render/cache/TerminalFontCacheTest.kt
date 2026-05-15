package com.gagik.terminal.ui.swing.render.cache

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import java.awt.Font

class TerminalFontCacheTest {
    @Test
    fun `font returns cached primary style variant`() {
        val base = Font(Font.MONOSPACED, Font.PLAIN, 14)
        val cache = TerminalFontCache()

        cache.update(base, emptyList(), useSystemFallbackFonts = false)

        Assertions.assertSame(base, cache.font(Font.PLAIN))
        Assertions.assertSame(cache.font(Font.BOLD), cache.font(Font.BOLD))
    }

    @Test
    fun `update reports whether font settings changed`() {
        val base = Font(Font.MONOSPACED, Font.PLAIN, 14)
        val fallback = Font("Dialog", Font.PLAIN, 14)
        val cache = TerminalFontCache()

        Assertions.assertTrue(cache.update(base, emptyList(), useSystemFallbackFonts = false))
        Assertions.assertFalse(cache.update(base, emptyList(), useSystemFallbackFonts = false))
        Assertions.assertTrue(cache.update(base, listOf(fallback), useSystemFallbackFonts = false))
        Assertions.assertTrue(cache.update(base, listOf(fallback), useSystemFallbackFonts = true))
    }

    @Test
    fun `generation changes only when font settings change`() {
        val base = Font(Font.MONOSPACED, Font.PLAIN, 14)
        val fallback = Font("Dialog", Font.PLAIN, 14)
        val cache = TerminalFontCache()

        val initialGeneration = cache.generation
        Assertions.assertTrue(cache.update(base, emptyList(), useSystemFallbackFonts = false))
        val firstGeneration = cache.generation
        Assertions.assertFalse(cache.update(base, emptyList(), useSystemFallbackFonts = false))
        Assertions.assertEquals(firstGeneration, cache.generation)
        Assertions.assertTrue(cache.update(base, listOf(fallback), useSystemFallbackFonts = false))

        Assertions.assertEquals(initialGeneration + 1, firstGeneration)
        Assertions.assertEquals(firstGeneration + 1, cache.generation)
    }

    @Test
    fun `fontForText uses configured fallback when primary cannot display text`() {
        val primary = Font("Courier New", Font.PLAIN, 17)
        val fallback = Font("Dialog", Font.PLAIN, 11)
        val thai = "\u0E01\u0E34"

        Assumptions.assumeTrue(primary.canDisplayUpTo(thai) >= 0)
        Assumptions.assumeTrue(fallback.canDisplayUpTo(thai) < 0)

        val cache = TerminalFontCache()
        cache.update(primary, listOf(fallback), useSystemFallbackFonts = false)

        val resolved = cache.fontForText(thai, Font.BOLD)

        Assertions.assertEquals(Font.BOLD, resolved.style)
        Assertions.assertEquals(primary.size2D, resolved.size2D)
        Assertions.assertEquals(fallback.family, resolved.family)
    }

    @Test
    fun `fontForText caches fallback fonts per style`() {
        val primary = Font("Courier New", Font.PLAIN, 17)
        val fallback = Font("Dialog", Font.PLAIN, 11)
        val thai = "\u0E01\u0E34"

        Assumptions.assumeTrue(primary.canDisplayUpTo(thai) >= 0)
        Assumptions.assumeTrue(fallback.canDisplayUpTo(thai) < 0)

        val cache = TerminalFontCache()
        cache.update(primary, listOf(fallback), useSystemFallbackFonts = false)

        val plain = cache.fontForText(thai, Font.PLAIN)
        val bold = cache.fontForText(thai, Font.BOLD)

        Assertions.assertEquals(Font.PLAIN, plain.style)
        Assertions.assertEquals(Font.BOLD, bold.style)
        Assertions.assertEquals(fallback.family, plain.family)
        Assertions.assertEquals(fallback.family, bold.family)
    }

    @Test
    fun `fontForText caches missing glyph resolution to primary font`() {
        val primary = Font(Font.MONOSPACED, Font.PLAIN, 14)
        val missing = String(Character.toChars(0x10FFFF))

        Assumptions.assumeTrue(primary.canDisplayUpTo(missing) >= 0)

        val cache = TerminalFontCache()
        cache.update(primary, emptyList(), useSystemFallbackFonts = false)

        val resolved = cache.fontForText(missing, Font.PLAIN)

        Assertions.assertSame(primary, resolved)
        Assertions.assertSame(primary, cache.resolvedTextFontCache(Font.PLAIN)[missing])
    }

    @Test
    fun `fontForText caches missing glyph resolution per style`() {
        val primary = Font(Font.MONOSPACED, Font.PLAIN, 14)
        val missing = String(Character.toChars(0x10FFFF))

        Assumptions.assumeTrue(primary.canDisplayUpTo(missing) >= 0)

        val cache = TerminalFontCache()
        cache.update(primary, emptyList(), useSystemFallbackFonts = false)

        val resolved = cache.fontForText(missing, Font.BOLD)

        Assertions.assertEquals(Font.BOLD, resolved.style)
        Assertions.assertSame(resolved, cache.resolvedTextFontCache(Font.BOLD)[missing])
        Assertions.assertNull(cache.resolvedTextFontCache(Font.PLAIN)[missing])
    }

    @Test
    fun `fontForText evicts old cluster fallback entries`() {
        val primary = Font(Font.MONOSPACED, Font.PLAIN, 14)
        val cache = TerminalFontCache(textFallbackCapacityPerStyle = 2)
        cache.update(primary, emptyList(), useSystemFallbackFonts = false)

        cache.fontForText(String(Character.toChars(0x10FFFF)), Font.PLAIN)
        cache.fontForText(String(Character.toChars(0x10FFFE)), Font.PLAIN)
        cache.fontForText(String(Character.toChars(0x10FFFD)), Font.PLAIN)

        Assertions.assertEquals(2, cache.resolvedTextFontCache(Font.PLAIN).size)
    }

    @Test
    fun `fontForCodePoint uses primitive bounded cache`() {
        val primary = Font(Font.MONOSPACED, Font.PLAIN, 14)
        val codePoint = 0x10FFFF

        Assumptions.assumeTrue(!primary.canDisplay(codePoint))

        val cache = TerminalFontCache(codePointFallbackCapacityPerStyle = 2)
        cache.update(primary, emptyList(), useSystemFallbackFonts = false)

        val resolved = cache.fontForCodePoint(codePoint, Font.BOLD)

        Assertions.assertEquals(Font.BOLD, resolved.style)
        Assertions.assertEquals(1, cache.resolvedCodePointFontCacheSize(Font.BOLD))
        Assertions.assertTrue(cache.resolvedTextFontCache(Font.BOLD).isEmpty())
    }

    @Test
    fun `fontForCodePoint evicts old primitive fallback entries`() {
        val primary = Font(Font.MONOSPACED, Font.PLAIN, 14)
        Assumptions.assumeTrue(!primary.canDisplay(0x10FFFF))
        Assumptions.assumeTrue(!primary.canDisplay(0x10FFFE))
        Assumptions.assumeTrue(!primary.canDisplay(0x10FFFD))

        val cache = TerminalFontCache(codePointFallbackCapacityPerStyle = 2)
        cache.update(primary, emptyList(), useSystemFallbackFonts = false)

        cache.fontForCodePoint(0x10FFFF, Font.PLAIN)
        cache.fontForCodePoint(0x10FFFE, Font.PLAIN)
        cache.fontForCodePoint(0x10FFFD, Font.PLAIN)

        Assertions.assertEquals(2, cache.resolvedCodePointFontCacheSize(Font.PLAIN))
    }

    @Suppress("UNCHECKED_CAST")
    private fun TerminalFontCache.resolvedTextFontCache(style: Int): Map<String, Font> {
        val field = TerminalFontCache::class.java.getDeclaredField("resolvedTextFonts")
        field.isAccessible = true
        val caches = field.get(this) as Array<Map<String, Font>>
        return caches[style and (Font.BOLD or Font.ITALIC)]
    }

    @Suppress("UNCHECKED_CAST")
    private fun TerminalFontCache.resolvedCodePointFontCacheSize(style: Int): Int {
        val field = TerminalFontCache::class.java.getDeclaredField("resolvedCodePointFonts")
        field.isAccessible = true
        val caches = field.get(this) as Array<Any>
        val sizeField = caches[style and (Font.BOLD or Font.ITALIC)].javaClass.getDeclaredField("size")
        sizeField.isAccessible = true
        return sizeField.getInt(caches[style and (Font.BOLD or Font.ITALIC)])
    }
}