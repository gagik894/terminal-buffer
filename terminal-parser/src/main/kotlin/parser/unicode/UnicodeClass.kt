package com.gagik.parser.unicode

internal object UnicodeClass {
    const val GRAPHEME_OTHER: Int = 0
    const val GRAPHEME_CR: Int = 1
    const val GRAPHEME_LF: Int = 2
    const val GRAPHEME_CONTROL: Int = 3
    const val GRAPHEME_EXTEND: Int = 4
    const val GRAPHEME_ZWJ: Int = 5
    const val GRAPHEME_REGIONAL_INDICATOR: Int = 6
    const val GRAPHEME_SPACING_MARK: Int = 7
    const val GRAPHEME_PREPEND: Int = 8
    const val GRAPHEME_L: Int = 9
    const val GRAPHEME_V: Int = 10
    const val GRAPHEME_T: Int = 11
    const val GRAPHEME_LV: Int = 12
    const val GRAPHEME_LVT: Int = 13

    @JvmStatic
    fun graphemeBreakClass(codepoint: Int): Int {
        return GeneratedGraphemeBreakTable.graphemeBreakClass(codepoint)
    }

    @JvmStatic
    fun isExtendedPictographic(codepoint: Int): Boolean {
        return GeneratedGraphemeBreakTable.isExtendedPictographic(codepoint)
    }
}
