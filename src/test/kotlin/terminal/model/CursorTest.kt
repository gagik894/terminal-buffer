package com.gagik.terminal.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.Test

@DisplayName("Cursor")
class CursorTest {

    @Nested
    @DisplayName("Constructor")
    inner class ConstructorTests {

        @Test
        fun `initializes at origin`() {
            val cursor = Cursor(80, 24)
            assertEquals(0, cursor.col, "Cursor should initialize at column 0")
            assertEquals(0, cursor.row, "Cursor should initialize at row 0")
        }

        @ParameterizedTest
        @ValueSource(ints = [0, -1, -100])
        fun `throws on invalid width`(invalidWidth: Int) {
            assertThrows<IllegalArgumentException> { Cursor(invalidWidth, 24) }
        }

        @ParameterizedTest
        @ValueSource(ints = [0, -1, -100])
        fun `throws on invalid height`(invalidHeight: Int) {
            assertThrows<IllegalArgumentException> { Cursor(80, invalidHeight) }
        }
    }

    @Nested
    @DisplayName("set()")
    inner class SetTests {

        @ParameterizedTest(name = "set({0}, {1}) -> clamped to ({2}, {3})")
        @CsvSource(
            "10, 10, 10, 10",      // Valid
            "0, 0, 0, 0",          // Top-left
            "79, 23, 79, 23",      // Bottom-right (max)
            "-1, -1, 0, 0",        // Negative (clamp to 0)
            "80, 24, 79, 23",      // Overflow (clamp to max)
            "200, 200, 79, 23"     // Extreme overflow
        )
        fun `sets and clamps positions`(col: Int, row: Int, expectedCol: Int, expectedRow: Int) {
            val cursor = Cursor(80, 24)
            cursor.set(col, row)

            assertEquals(expectedCol, cursor.col, "Column should be set to $col and clamped to $expectedCol if out of bounds")
            assertEquals(expectedRow, cursor.row, "Row should be set to $row and clamped to $expectedRow if out of bounds")
        }

        @Test
        fun `handles 1x1 terminal`() {
            val cursor = Cursor(1, 1)
            cursor.set(10, 10)

            assertEquals(0, cursor.col, "In a 1x1 terminal, column should always be 0 regardless of input")
            assertEquals(0, cursor.row, "In a 1x1 terminal, row should always be 0 regardless of input")
        }
    }

    @Nested
    @DisplayName("move()")
    inner class MoveTests {

        @ParameterizedTest(name = "From (5,5) move({0},{1}) -> ({2},{3})")
        @CsvSource(
            "2, 0, 7, 5",          // Right
            "-2, 0, 3, 5",         // Left
            "0, 3, 5, 8",          // Down
            "0, -3, 5, 2",         // Up
            "2, -2, 7, 3",         // Diagonal
            "0, 0, 5, 5",          // Zero delta
            "10, 0, 9, 5",         // Overflow right
            "0, 10, 5, 9",         // Overflow down
            "-10, 0, 0, 5",        // Underflow left
            "0, -10, 5, 0",        // Underflow up
            "20, 20, 9, 9",        // Overflow both
            "-20, -20, 0, 0"       // Underflow both
        )
        fun `moves relatively with clamping`(dx: Int, dy: Int, expectedCol: Int, expectedRow: Int) {
            val cursor = Cursor(10, 10)
            cursor.set(5, 5)
            cursor.move(dx, dy)

            assertEquals(expectedCol, cursor.col, "Should move horizontally by $dx and clamp to bounds")
            assertEquals(expectedRow, cursor.row, "Should move vertically by $dy and clamp to bounds")
        }

        @Test
        fun `handles integer overflow`() {
            val cursor = Cursor(100, 100)
            cursor.set(50, 50)

            cursor.move(Int.MAX_VALUE, Int.MAX_VALUE)
            assertEquals(99, cursor.col, "Should handle integer overflow and clamp to bounds correctly")
            assertEquals(99, cursor.row, "Should handle integer overflow and clamp to bounds correctly")

            cursor.move(Int.MIN_VALUE, Int.MIN_VALUE)
            assertEquals(0, cursor.col, "Should handle integer overflow and clamp to bounds correctly")
            assertEquals(0, cursor.row, "Should handle integer overflow and clamp to bounds correctly")
        }

        @Test
        fun `chained moves accumulate correctly`() {
            val cursor = Cursor(20, 20)
            cursor.set(10, 10)
            cursor.move(2, 0)
            cursor.move(0, 3)
            cursor.move(-1, -1)

            assertEquals(11, cursor.col, "Chained moves should accumulate correctly and respect bounds")
            assertEquals(12, cursor.row, "Chained moves should accumulate correctly and respect bounds")
        }
    }

    @Nested
    @DisplayName("reset()")
    inner class ResetTests {

        @Test
        fun `resets to origin`() {
            val cursor = Cursor(80, 24)
            cursor.set(50, 15)
            cursor.reset()

            assertEquals(0, cursor.col, "Reset should return cursor to top-left corner (0, 0)")
            assertEquals(0, cursor.row, "Reset should return cursor to top-left corner (0, 0)")
        }

        @Test
        fun `reset is idempotent`() {
            val cursor = Cursor(80, 24)
            cursor.set(40, 12)
            cursor.reset()
            val first = cursor.col to cursor.row
            cursor.reset()
            val second = cursor.col to cursor.row

            assertEquals(first, second, "Reset should be idempotent, multiple calls should yield the same result")
        }
    }

    @Nested
    @DisplayName("advance()")
    inner class AdvanceTests {

        @Test
        fun `normal advance within line`() {
            val cursor = Cursor(10, 5)
            val result = cursor.advance()

            assertTrue(result is AdvanceResult.Normal)
            assertEquals(1, cursor.col)
            assertEquals(0, cursor.row)
        }

        @Test
        fun `wraps to next line at end`() {
            val cursor = Cursor(3, 5)
            cursor.set(2, 0)  // Last column

            val result = cursor.advance()

            assertTrue(result is AdvanceResult.Wrapped)
            assertEquals(0, (result as AdvanceResult.Wrapped).fromRow)
            assertEquals(0, cursor.col)
            assertEquals(1, cursor.row)
        }

        @Test
        fun `signals scroll when wrapping past bottom`() {
            val cursor = Cursor(3, 2)
            cursor.set(2, 1)  // Last column, last row

            val result = cursor.advance()

            assertTrue(result is AdvanceResult.ScrollNeeded)
            assertEquals(1, (result as AdvanceResult.ScrollNeeded).fromRow)
            assertEquals(0, cursor.col)
            assertEquals(1, cursor.row)  // Stays at bottom
        }
    }
}