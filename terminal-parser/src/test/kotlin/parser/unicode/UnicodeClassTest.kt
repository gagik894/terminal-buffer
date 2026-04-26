package com.gagik.parser.unicode

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("UnicodeClass")
class UnicodeClassTest {

    @Nested
    @DisplayName("grapheme break class")
    inner class GraphemeBreakClass {

        @Test
        fun `classifies combining marks and variation selectors as Extend`() {
            assertEquals(UnicodeClass.GRAPHEME_EXTEND, UnicodeClass.graphemeBreakClass(0x0301))
            assertEquals(UnicodeClass.GRAPHEME_EXTEND, UnicodeClass.graphemeBreakClass(0x1AB0))
            assertEquals(UnicodeClass.GRAPHEME_EXTEND, UnicodeClass.graphemeBreakClass(0xFE0F))
            assertEquals(UnicodeClass.GRAPHEME_EXTEND, UnicodeClass.graphemeBreakClass(0xE0100))
        }

        @Test
        fun `classifies ZWJ regional indicators and spacing marks`() {
            assertEquals(UnicodeClass.GRAPHEME_ZWJ, UnicodeClass.graphemeBreakClass(0x200D))
            assertEquals(UnicodeClass.GRAPHEME_REGIONAL_INDICATOR, UnicodeClass.graphemeBreakClass(0x1F1FA))
            assertEquals(UnicodeClass.GRAPHEME_SPACING_MARK, UnicodeClass.graphemeBreakClass(0x0903))
        }

        @Test
        fun `classifies Hangul Jamo and syllable classes`() {
            assertEquals(UnicodeClass.GRAPHEME_L, UnicodeClass.graphemeBreakClass(0x1100))
            assertEquals(UnicodeClass.GRAPHEME_V, UnicodeClass.graphemeBreakClass(0x1161))
            assertEquals(UnicodeClass.GRAPHEME_T, UnicodeClass.graphemeBreakClass(0x11A8))
            assertEquals(UnicodeClass.GRAPHEME_LV, UnicodeClass.graphemeBreakClass(0xAC00))
            assertEquals(UnicodeClass.GRAPHEME_LVT, UnicodeClass.graphemeBreakClass(0xAC01))
        }
    }

    @Nested
    @DisplayName("extended pictographic")
    inner class ExtendedPictographic {

        @Test
        fun `classifies emoji and symbol bases`() {
            assertTrue(UnicodeClass.isExtendedPictographic(0x1F468))
            assertTrue(UnicodeClass.isExtendedPictographic(0x2764))
            assertTrue(UnicodeClass.isExtendedPictographic(0x00A9))
        }

        @Test
        fun `does not classify ordinary letters or regional indicators as extended pictographic`() {
            assertFalse(UnicodeClass.isExtendedPictographic('A'.code))
            assertFalse(UnicodeClass.isExtendedPictographic(0x1F1FA))
        }
    }

}
