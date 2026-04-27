package com.gagik.core.codec

import com.gagik.core.model.AttributeColor
import com.gagik.core.model.AttributeColorKind
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

@DisplayName("AttributeCodec Test Suite")
class AttributeCodecTest {

    @Test
    fun `exports expected color constants`() {
        assertEquals(16, AttributeCodec.MAX_ANSI_COLOR)
        assertEquals(256, AttributeCodec.MAX_COLOR)
        assertEquals(255, AttributeCodec.MAX_INDEXED_COLOR)
    }

    @Nested
    @DisplayName("Round-Trip & Logic Consistency")
    inner class RoundTripTests {

        @ParameterizedTest(name = "FG={0}, BG={1}, B={2}, I={3}, U={4}")
        @CsvSource(
            // BOUNDARIES
            "0,  0,  false, false, false", // All Min
            "256, 256, true,  true,  true", // All Max

            // ISOLATION (Checking for Bit Bleed)
            // If FG=256 bleeds into BG, BG won't be 0.
            "256, 0,  false, false, false",
            // If BG=256 bleeds into Styles, Bold won't be false.
            "0,  256, false, false, false",

            // INDEPENDENCE (Styles vs Colors)
            "0,  0,  true,  false, false", // Bold Only
            "0,  0,  false, true,  false", // Italic Only
            "0,  0,  false, false, true",  // Underline Only
            "256, 256, false, false, false", // Max Colors, No Styles

            // MIXED / RANDOM PATTERNS
            "21, 10, true,  false, true",  // Checkerboard patterns
            "15, 16, false, true,  false"
        )
        fun testPackAndUnpack(fg: Int, bg: Int, bold: Boolean, italic: Boolean, underline: Boolean) {
            val packed = AttributeCodec.pack(fg, bg, bold, italic, underline)

            assertAll(
                { assertEquals(fg, AttributeCodec.foreground(packed), "Foreground mismatch") },
                { assertEquals(bg, AttributeCodec.background(packed), "Background mismatch") },
                { assertEquals(bold, AttributeCodec.isBold(packed), "Bold mismatch") },
                { assertEquals(italic, AttributeCodec.isItalic(packed), "Italic mismatch") },
                { assertEquals(underline, AttributeCodec.isUnderline(packed), "Underline mismatch") }
            )
        }
    }

    @Nested
    @DisplayName("Binary Layout")
    inner class BitLayoutTests {

        @ParameterizedTest(name = "Scenario: {1} -> Expect {0}")
        @CsvSource(
            "16777216,         FG_1",
            "16777471,         FG_256",
            "1125899906842624, BG_1",
            "1125917019602944, BG_256",
            "4503599627370496, PROTECTED",
            "9007199254740992, BOLD",
            "18014398509481984, ITALIC",
            "36028797018963968, UNDERLINE",
            "72057594037927936, INVERSE"
        )
        fun testExactBitValues(expectedValue: Long, scenario: String) {
            val packed = when (scenario) {
                "FG_1"      -> AttributeCodec.pack(1, 0, false, false, false)
                "FG_256"    -> AttributeCodec.pack(256, 0, false, false, false)
                "BG_1"      -> AttributeCodec.pack(0, 1, false, false, false)
                "BG_256"    -> AttributeCodec.pack(0, 256, false, false, false)
                "PROTECTED" -> AttributeCodec.pack(0, 0, false, false, false, protected = true)
                "BOLD"      -> AttributeCodec.pack(0, 0, true, false, false)
                "ITALIC"    -> AttributeCodec.pack(0, 0, false, true, false)
                "UNDERLINE" -> AttributeCodec.pack(0, 0, false, false, true)
                "INVERSE"   -> AttributeCodec.pack(0, 0, false, false, false, inverse = true)
                else -> throw IllegalArgumentException("Unknown scenario")
            }
            assertEquals(expectedValue, packed, "Bit layout mismatch! The raw attribute word is incorrect.")
        }
    }

    @Nested
    @DisplayName("Input Validation")
    inner class ValidationTests {

        @ParameterizedTest(name = "Reject FG={0}")
        @ValueSource(ints = [-1, 257, 1000, Int.MIN_VALUE, Int.MAX_VALUE])
        fun testInvalidForeground(invalidFg: Int) {
            val ex = assertThrows(IllegalArgumentException::class.java) {
                AttributeCodec.pack(invalidFg, 0, false, false, false)
            }
            assertNotNull(ex.message)
        }

        @ParameterizedTest(name = "Reject BG={0}")
        @ValueSource(ints = [-1, 257, 1000, Int.MIN_VALUE, Int.MAX_VALUE])
        fun testInvalidBackground(invalidBg: Int) {
            val ex = assertThrows(IllegalArgumentException::class.java) {
                AttributeCodec.pack(0, invalidBg, false, false, false)
            }
            assertNotNull(ex.message)
        }
    }

    @Nested
    @DisplayName("Decoder Robustness")
    inner class RobustnessTests {

        @Test
        @DisplayName("Ignore dirty bits above the defined layout")
        fun testIgnoreDirtyBits() {
            val dirtyHighBits = -1L shl 57

            // The extractors should act as if these high bits don't exist
            assertAll(
                { assertEquals(0, AttributeCodec.foreground(dirtyHighBits)) },
                { assertEquals(0, AttributeCodec.background(dirtyHighBits)) },
                { assertFalse(AttributeCodec.isBold(dirtyHighBits)) }
            )
        }

        @Test
        @DisplayName("Handle -1 (All bits set) safely")
        fun testAllBitsSet() {
            // -1 is 11111...11111 in binary
            val allOnes = -1L

            assertAll(
                { assertEquals(0, AttributeCodec.foreground(allOnes)) },
                { assertEquals(0, AttributeCodec.background(allOnes)) },
                { assertTrue(AttributeCodec.isBold(allOnes)) },
                { assertTrue(AttributeCodec.isInverse(allOnes)) },
                { assertTrue(AttributeCodec.isProtected(allOnes)) }
            )
        }

        @Test
        fun `unpack returns structured attributes`() {
            val packed = AttributeCodec.pack(9, 4, bold = true, italic = true, underline = false)
            val unpacked = AttributeCodec.unpack(packed)

            assertAll(
                { assertEquals(AttributeColor.indexed(8), unpacked.foreground) },
                { assertEquals(AttributeColor.indexed(3), unpacked.background) },
                { assertTrue(unpacked.bold) },
                { assertTrue(unpacked.italic) },
                { assertFalse(unpacked.underline) },
                { assertFalse(unpacked.inverse) },
                { assertEquals(9, AttributeCodec.foreground(packed)) },
                { assertEquals(4, AttributeCodec.background(packed)) }
            )
        }

        @Test
        fun `packColors preserves indexed rgb and inverse attributes`() {
            val packed = AttributeCodec.packColors(
                foreground = AttributeColor.indexed(244),
                background = AttributeColor.rgb(0x12, 0x34, 0x56),
                bold = true,
                italic = false,
                underline = true,
                inverse = true
            )
            val unpacked = AttributeCodec.unpack(packed)

            assertAll(
                { assertEquals(AttributeColorKind.INDEXED, unpacked.foreground.kind) },
                { assertEquals(244, unpacked.foreground.value) },
                { assertEquals(AttributeColorKind.RGB, unpacked.background.kind) },
                { assertEquals(0x12_34_56, unpacked.background.value) },
                { assertEquals(245, AttributeCodec.foreground(packed)) },
                { assertEquals(0, AttributeCodec.background(packed)) },
                { assertTrue(unpacked.bold) },
                { assertTrue(unpacked.underline) },
                { assertTrue(unpacked.inverse) }
            )
        }
    }
}
