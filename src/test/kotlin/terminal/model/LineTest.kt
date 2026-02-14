package com.gagik.terminal.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource


@DisplayName("Line Test Suite")
class LineTest {

    @Nested
    @DisplayName("Initialization & Validation")
    inner class InitializationTests {

        @ParameterizedTest(name = "Create line with width={0}")
        @ValueSource(ints = [1, 10, 80, 1000])
        fun testValidConstruction(width: Int) {
            val line = Line(width)

            assertAll(
                { assertEquals(width, line.width, "Width mismatch") },
                { assertEquals(width, line.codepoints.size, "Codepoints array size mismatch") },
                { assertEquals(width, line.attrs.size, "Attributes array size mismatch") },
                { assertFalse(line.wrapped, "New line should not be wrapped") }
            )
        }

        @Test
        @DisplayName("New line should be initialized to zeros")
        fun testDefaultValues() {
            val line = Line(5)
            // Verify all cells start as 0
            for (i in 0 until 5) {
                assertEquals(0, line.codepoints[i], "Codepoint at $i should be 0")
                assertEquals(0, line.attrs[i], "Attribute at $i should be 0")
            }
        }

        @ParameterizedTest(name = "Reject invalid width={0}")
        @ValueSource(ints = [0, -1, -100])
        fun testInvalidConstruction(invalidWidth: Int) {
            val ex = assertThrows(IllegalArgumentException::class.java) {
                Line(invalidWidth)
            }
            assertNotNull(ex.message, "Exception should have a message")
        }
    }

    @Nested
    @DisplayName("Cell Operations (Set/Get)")
    inner class CellOperationsTests {

        @ParameterizedTest(name = "Set/Get at index {0} (Width 10)")
        @CsvSource(
            "0",   // Start boundary
            "5",   // Middle
            "9"    // End boundary
        )
        fun testValidCellAccess(index: Int) {
            val line = Line(10)
            val testCodepoint = 65 // 'A'
            val testAttr = 12345

            line.setCell(index, testCodepoint, testAttr)

            assertAll(
                { assertEquals(testCodepoint, line.getCodepoint(index), "Codepoint mismatch") },
                { assertEquals(testAttr, line.getAttr(index), "Attribute mismatch") }
            )
        }

        @ParameterizedTest(name = "Ignore out-of-bounds index {0} (Width 10)")
        @ValueSource(ints = [-1, -50, 10, 11, 100])
        fun testOutOfBoundsAccess(invalidIndex: Int) {
            val line = Line(10)

            // Ensure setting doesn't throw or change state
            assertDoesNotThrow {
                line.setCell(invalidIndex, 65, 123)
            }

            // Ensure getting returns null
            assertAll(
                { assertNull(line.getCodepoint(invalidIndex), "Should return null for invalid codepoint index") },
                { assertNull(line.getAttr(invalidIndex), "Should return null for invalid attr index") }
            )
        }
    }

    @Nested
    @DisplayName("State Management (Clear & Wrap)")
    inner class StateTests {

        @Test
        @DisplayName("Clear() resets content and wrap status")
        fun testClearResetsLine() {
            // Setup a dirty line with data and wrapped=true
            val width = 5
            val line = Line(width)

            line.setCell(0, 88, 99)
            line.setCell(width - 1, 88, 99)
            line.wrapped = true

            // Execute Clear
            val defaultAttr = 42
            line.clear(defaultAttr)

            assertAll(
                "Verify line was fully reset",
                { assertFalse(line.wrapped, "Wrapped flag should be reset to false") },
                { assertEquals(0, line.getCodepoint(0), "Codepoints should be reset to 0") },
                { assertEquals(defaultAttr, line.getAttr(0), "Attrs should be reset to default ($defaultAttr)") }
            )
        }
    }

    @Nested
    @DisplayName("copyFrom()")
    inner class CopyFromTests {

        @Test
        fun `copies all data from another line`() {
            val source = Line(10)
            source.setCell(0, 'A'.code, 1)
            source.setCell(5, 'B'.code, 2)
            source.wrapped = true

            val dest = Line(10)
            dest.copyFrom(source)

            assertEquals('A'.code, dest.getCodepoint(0))
            assertEquals(1, dest.getAttr(0))
            assertEquals('B'.code, dest.getCodepoint(5))
            assertEquals(2, dest.getAttr(5))
            assertTrue(dest.wrapped)
        }

        @Test
        fun `throws on width mismatch`() {
            val source = Line(10)
            val dest = Line(20)

            assertThrows<IllegalArgumentException> {
                dest.copyFrom(source)
            }
        }

        @Test
        fun `overwrites existing data`() {
            val source = Line(10)
            source.setCell(0, 'X'.code, 99)

            val dest = Line(10)
            dest.setCell(0, 'Y'.code, 88)
            dest.copyFrom(source)

            assertEquals('X'.code, dest.getCodepoint(0))
            assertEquals(99, dest.getAttr(0))
        }
    }
}