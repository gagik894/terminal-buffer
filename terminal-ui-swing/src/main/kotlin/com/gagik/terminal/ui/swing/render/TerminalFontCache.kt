package com.gagik.terminal.ui.swing.render

import java.awt.Font

/**
 * Caches terminal font style variants for one settings snapshot.
 */
internal class TerminalFontCache {
    private var baseFont: Font? = null
    private var fallbackBaseFonts: List<Font> = emptyList()
    private val styleFonts = arrayOfNulls<Font>(STYLE_COUNT)
    private var fallbackStyleFonts: Array<Array<Font?>> = emptyArray()

    /**
     * Rebuilds cached style variants when [font] changes.
     *
     * @param font base terminal font.
     */
    fun update(font: Font, fallbackFonts: List<Font>) {
        if (font == baseFont && fallbackFonts == fallbackBaseFonts) return

        baseFont = font
        fallbackBaseFonts = fallbackFonts
        styleFonts.fill(null)
        styleFonts[font.style and STYLE_MASK] = font
        fallbackStyleFonts = Array(fallbackFonts.size) { arrayOfNulls(STYLE_COUNT) }
    }

    /**
     * Returns a cached font variant for [style].
     *
     * @param style AWT style bit mask.
     * @return cached style font.
     */
    fun font(style: Int): Font {
        val normalizedStyle = style and STYLE_MASK
        val cached = styleFonts[normalizedStyle]
        if (cached != null) return cached

        val font = requireNotNull(baseFont) {
            "TerminalFontCache.update must be called before font"
        }.deriveFont(normalizedStyle)
        styleFonts[normalizedStyle] = font
        return font
    }

    /**
     * Returns the first cached style font that can display all UTF-16 units in
     * [text], falling back to [font] when no configured fallback covers it.
     */
    fun fontForText(text: String, style: Int): Font {
        val primary = font(style)
        if (primary.canDisplayUpTo(text) < 0) return primary

        var index = 0
        while (index < fallbackBaseFonts.size) {
            val fallback = fallbackFont(index, style)
            if (fallback.canDisplayUpTo(text) < 0) {
                return fallback
            }
            index++
        }

        return primary
    }

    private fun fallbackFont(index: Int, style: Int): Font {
        val normalizedStyle = style and STYLE_MASK
        val cached = fallbackStyleFonts[index][normalizedStyle]
        if (cached != null) return cached

        val base = requireNotNull(baseFont) {
            "TerminalFontCache.update must be called before fallbackFont"
        }
        val fallback = fallbackBaseFonts[index]
            .deriveFont(normalizedStyle, base.size2D)
        fallbackStyleFonts[index][normalizedStyle] = fallback
        return fallback
    }

    private companion object {
        private const val STYLE_COUNT = 4
        private const val STYLE_MASK = Font.BOLD or Font.ITALIC
    }
}
