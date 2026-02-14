package com.gagik.terminal.codec

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

@DisplayName("AttributeCodec Test Suite")
class AttributeCodecTest {

    @Nested
    @DisplayName("Round-Trip & Logic Consistency")
    inner class RoundTripTests {

        @ParameterizedTest(name = "FG={0}, BG={1}, B={2}, I={3}, U={4}")
        @CsvSource(
            // BOUNDARIES
            "0,  0,  false, false, false", // All Min
            "31, 31, true,  true,  true",  // All Max

            // ISOLATION (Checking for Bit Bleed)
            // If FG=31 bleeds into BG, BG won't be 0.
            "31, 0,  false, false, false",
            // If BG=31 bleeds into Styles, Bold won't be false.
            "0,  31, false, false, false",

            // INDEPENDENCE (Styles vs Colors)
            "0,  0,  true,  false, false", // Bold Only
            "0,  0,  false, true,  false", // Italic Only
            "0,  0,  false, false, true",  // Underline Only
            "31, 31, false, false, false", // Max Colors, No Styles

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
            "1,    FG_1",
            "31,   FG_31",
            "32,   BG_1",      // 1 << 5
            "992,  BG_31",     // 31 << 5
            "1024, BOLD",      // 1 << 10
            "2048, ITALIC",    // 1 << 11
            "4096, UNDERLINE"  // 1 << 12
        )
        fun testExactBitValues(expectedValue: Int, scenario: String) {
            val packed = when (scenario) {
                "FG_1"      -> AttributeCodec.pack(1, 0, false, false, false)
                "FG_31"     -> AttributeCodec.pack(31, 0, false, false, false)
                "BG_1"      -> AttributeCodec.pack(0, 1, false, false, false)
                "BG_31"     -> AttributeCodec.pack(0, 31, false, false, false)
                "BOLD"      -> AttributeCodec.pack(0, 0, true, false, false)
                "ITALIC"    -> AttributeCodec.pack(0, 0, false, true, false)
                "UNDERLINE" -> AttributeCodec.pack(0, 0, false, false, true)
                else -> throw IllegalArgumentException("Unknown scenario")
            }
            assertEquals(expectedValue, packed, "Bit layout mismatch! The raw integer is incorrect.")
        }
    }

    @Nested
    @DisplayName("Input Validation")
    inner class ValidationTests {

        @ParameterizedTest(name = "Reject FG={0}")
        @ValueSource(ints = [-1, 32, 100, Int.MIN_VALUE, Int.MAX_VALUE])
        fun testInvalidForeground(invalidFg: Int) {
            val ex = assertThrows(IllegalArgumentException::class.java) {
                AttributeCodec.pack(invalidFg, 0, false, false, false)
            }
            assertNotNull(ex.message)
        }

        @ParameterizedTest(name = "Reject BG={0}")
        @ValueSource(ints = [-1, 32, 100, Int.MIN_VALUE, Int.MAX_VALUE])
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
        @DisplayName("Ignore dirty bits (13-31)")
        fun testIgnoreDirtyBits() {
            // 13-31 bits are 1, and 0-12 are 0.
            val dirtyHighBits = -1 shl 13

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
            val allOnes = -1

            // Should mask out the high bits and just return the max valid values for the low bits
            assertAll(
                { assertEquals(31, AttributeCodec.foreground(allOnes)) },
                { assertEquals(31, AttributeCodec.background(allOnes)) },
                { assertTrue(AttributeCodec.isBold(allOnes)) }
            )
        }
    }
}