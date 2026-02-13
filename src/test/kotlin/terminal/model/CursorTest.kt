package terminal.model

import com.gagik.terminal.model.Cursor
import org.junit.jupiter.api.Assertions.*
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
        fun `initializes at origin (0,0)`() {
            val cursor = Cursor(80, 24)
            assertEquals(0, cursor.col)
            assertEquals(0, cursor.row)
        }

        @ParameterizedTest
        @ValueSource(ints = [0, -1, -100])
        fun `throws IllegalArgumentException for invalid width`(invalidWidth: Int) {
            val exception = assertThrows<IllegalArgumentException> {
                Cursor(invalidWidth, 24)
            }
            assertNotNull(exception)
        }

        @ParameterizedTest
        @ValueSource(ints = [0, -1, -100])
        fun `throws IllegalArgumentException for invalid height`(invalidHeight: Int) {
            val exception = assertThrows<IllegalArgumentException> {
                Cursor(80, invalidHeight)
            }
            assertNotNull(exception)
        }

        @Test
        fun `accepts minimum valid dimensions (1x1)`() {
            assertDoesNotThrow {
                val cursor = Cursor(1, 1)
                assertEquals(0, cursor.col, "Column should initialize to 0")
                assertEquals(0, cursor.row, "Row should initialize to 0")
            }
        }

        @Test
        fun `accepts large dimensions`() {
            assertDoesNotThrow {
                val cursor = Cursor(1000, 1000)
                assertEquals(0, cursor.col, "Column should initialize to 0")
                assertEquals(0, cursor.row, "Row should initialize to 0")
            }
        }
    }

    @Nested
    @DisplayName("set()")
    inner class SetTests {

        @ParameterizedTest(name = "set({0}, {1}) on 80x24 terminal -> ({2}, {3})")
        @CsvSource(
            "10, 10, 10, 10",      // Valid position
            "0, 0, 0, 0",          // Top-left corner
            "79, 23, 79, 23",      // Bottom-right corner (max valid)
            "79, 0, 79, 0",        // Top-right corner
            "0, 23, 0, 23"         // Bottom-left corner
        )
        fun `sets valid positions correctly`(col: Int, row: Int, expectedCol: Int, expectedRow: Int) {
            val cursor = Cursor(80, 24)
            cursor.set(col, row)

            assertEquals(expectedCol, cursor.col, "Column should be set to expected value")
            assertEquals(expectedRow, cursor.row, "Row should be set to expected value")
        }

        @ParameterizedTest(name = "set({0}, {1}) on 80x24 terminal -> clamped to ({2}, {3})")
        @CsvSource(
            "-1, 0, 0, 0",         // Negative column
            "0, -1, 0, 0",         // Negative row
            "-5, -5, 0, 0",        // Both negative
            "80, 0, 79, 0",        // Column overflow by 1
            "0, 24, 0, 23",        // Row overflow by 1
            "100, 50, 79, 23",     // Both overflow
            "200, 200, 79, 23",    // Extreme overflow
            "-100, -100, 0, 0"     // Extreme underflow
        )
        fun `clamps out-of-bounds positions`(col: Int, row: Int, expectedCol: Int, expectedRow: Int) {
            val cursor = Cursor(80, 24)
            cursor.set(col, row)

            assertEquals(expectedCol, cursor.col, "Column should be clamped to expected value")
            assertEquals(expectedRow, cursor.row, "Row should be clamped to expected value")
        }

        @Test
        fun `handles 1x1 terminal bounds`() {
            val cursor = Cursor(1, 1)

            cursor.set(0, 0)
            assertEquals(0, cursor.col, "Column should be 0 in 1x1 terminal")
            assertEquals(0, cursor.row, "Row should be 0 in 1x1 terminal")

            cursor.set(10, 10)
            assertEquals(0, cursor.col, "Column should be clamped to 0 in 1x1 terminal")
            assertEquals(0, cursor.row, "Row should be clamped to 0 in 1x1 terminal")

            cursor.set(-5, -5)
            assertEquals(0, cursor.col, "Column should be clamped to 0 in 1x1 terminal")
            assertEquals(0, cursor.row, "Row should be clamped to 0 in 1x1 terminal")
        }

        @Test
        fun `overwrites previous position`() {
            val cursor = Cursor(80, 24)
            cursor.set(10, 10)
            cursor.set(20, 15)

            assertEquals(20, cursor.col, "Column should update to new value")
            assertEquals(15, cursor.row, "Row should update to new value")
        }
    }

    @Nested
    @DisplayName("move()")
    inner class MoveTests {

        @Test
        fun `moves right (positive dx)`() {
            val cursor = Cursor(10, 10)
            cursor.set(5, 5)
            cursor.move(2, 0)

            assertEquals(7, cursor.col, "Column should move right by 2")
            assertEquals(5, cursor.row, "Row should remain unchanged")
        }

        @Test
        fun `moves left (negative dx)`() {
            val cursor = Cursor(10, 10)
            cursor.set(5, 5)
            cursor.move(-2, 0)

            assertEquals(3, cursor.col, "Column should move left by 2")
            assertEquals(5, cursor.row, "Row should remain unchanged")
        }

        @Test
        fun `moves down (positive dy)`() {
            val cursor = Cursor(10, 10)
            cursor.set(5, 5)
            cursor.move(0, 3)

            assertEquals(5, cursor.col, "Column should remain unchanged")
            assertEquals(8, cursor.row, "Row should move down by 3")
        }

        @Test
        fun `moves up (negative dy)`() {
            val cursor = Cursor(10, 10)
            cursor.set(5, 5)
            cursor.move(0, -3)

            assertEquals(5, cursor.col, "Column should remain unchanged")
            assertEquals(2, cursor.row, "Row should move up by 3")
        }

        @Test
        fun `moves diagonally`() {
            val cursor = Cursor(10, 10)
            cursor.set(5, 5)
            cursor.move(2, -2)

            assertEquals(7, cursor.col, "Column should move right by 2")
            assertEquals(3, cursor.row, "Row should move up by 2")
        }

        @Test
        fun `move with zero delta does nothing`() {
            val cursor = Cursor(10, 10)
            cursor.set(5, 5)
            cursor.move(0, 0)

            assertEquals(5, cursor.col, "Column should remain unchanged")
            assertEquals(5, cursor.row, "Row should remain unchanged")
        }

        @ParameterizedTest(name = "From ({0},{1}) move({2},{3}) on 10x10 -> ({4},{5})")
        @CsvSource(
            "5, 5, 10, 0, 9, 5",      // Overflow right
            "5, 5, 0, 10, 5, 9",      // Overflow down
            "5, 5, -10, 0, 0, 5",     // Underflow left
            "5, 5, 0, -10, 5, 0",     // Underflow up
            "5, 5, 20, 20, 9, 9",     // Overflow both
            "5, 5, -20, -20, 0, 0",   // Underflow both
            "9, 9, 1, 1, 9, 9",       // From bottom-right, overflow
            "0, 0, -1, -1, 0, 0"      // From top-left, underflow
        )
        fun `clamps movement to bounds`(
            startCol: Int, startRow: Int,
            dx: Int, dy: Int,
            expectedCol: Int, expectedRow: Int
        ) {
            val cursor = Cursor(10, 10)
            cursor.set(startCol, startRow)
            cursor.move(dx, dy)

            assertEquals(expectedCol, cursor.col, "Column should be clamped to expected value")
            assertEquals(expectedRow, cursor.row, "Row should be clamped to expected value")
        }

        @Test
        fun `chained moves work correctly`() {
            val cursor = Cursor(20, 20)
            cursor.set(10, 10)

            cursor.move(2, 0)
            cursor.move(0, 3)
            cursor.move(-1, -1)

            assertEquals(11, cursor.col, "Column should be 11 after chained moves")
            assertEquals(12, cursor.row, "Row should be 12 after chained moves")
        }

        @Test
        fun `move from corners clamps correctly`() {
            val cursor = Cursor(10, 10)

            // Top-left corner
            cursor.set(0, 0)
            cursor.move(-5, -5)
            assertEquals(0, cursor.col, "Column should be clamped to 0 from top-left")
            assertEquals(0, cursor.row, "Row should be clamped to 0 from top-left")

            // Top-right corner
            cursor.set(9, 0)
            cursor.move(5, -5)
            assertEquals(9, cursor.col, "Column should be clamped to 9 from top-right")
            assertEquals(0, cursor.row, "Row should be clamped to 0 from top-right")

            // Bottom-left corner
            cursor.set(0, 9)
            cursor.move(-5, 5)
            assertEquals(0, cursor.col, "Column should be clamped to 0 from bottom-left")
            assertEquals(9, cursor.row, "Row should be clamped to 9 from bottom-left")

            // Bottom-right corner
            cursor.set(9, 9)
            cursor.move(5, 5)
            assertEquals(9, cursor.col, "Column should be clamped to 9 from bottom-right")
            assertEquals(9, cursor.row, "Row should be clamped to 9 from bottom-right")
        }
    }

    @Nested
    @DisplayName("reset()")
    inner class ResetTests {

        @Test
        fun `resets to origin from any position`() {
            val cursor = Cursor(80, 24)
            cursor.set(50, 15)
            cursor.reset()

            assertEquals(0, cursor.col, "Column should reset to 0")
            assertEquals(0, cursor.row, "Row should reset to 0")
        }

        @Test
        fun `reset when already at origin does nothing`() {
            val cursor = Cursor(80, 24)
            cursor.reset()

            assertEquals(0, cursor.col, "Column should remain at 0")
            assertEquals(0, cursor.row, "Row should remain at 0")
        }

        @Test
        fun `reset after multiple moves`() {
            val cursor = Cursor(20, 20)
            cursor.move(5, 5)
            cursor.move(3, 2)
            cursor.set(15, 10)
            cursor.reset()

            assertEquals(0, cursor.col, "Column should reset to 0 after multiple moves")
            assertEquals(0, cursor.row, "Row should reset to 0 after multiple moves")
        }

        @Test
        fun `can set position after reset`() {
            val cursor = Cursor(80, 24)
            cursor.set(40, 12)
            cursor.reset()
            cursor.set(10, 5)

            assertEquals(10, cursor.col, "Column should be set to 10 after reset")
            assertEquals(5, cursor.row, "Row should be set to 5 after reset")
        }
    }

    @Nested
    @DisplayName("Integration & Edge Cases")
    inner class IntegrationTests {

        @Test
        fun `complex workflow - mixed operations`() {
            val cursor = Cursor(80, 24)

            cursor.set(40, 12)
            assertEquals(40, cursor.col, "Initial set should position cursor at (40, 12)")
            assertEquals(12, cursor.row, "Initial set should position cursor at (40, 12)")

            cursor.move(5, -2)
            assertEquals(45, cursor.col, "Column should move right by 5")
            assertEquals(10, cursor.row, "Row should move up by 2")

            cursor.move(50, 0)  // Should clamp
            assertEquals(79, cursor.col, "Column should be clamped to 79 after horizontal overflow")
            assertEquals(10, cursor.row, "Row should remain unchanged after horizontal overflow")

            cursor.reset()
            assertEquals(0, cursor.col, "Column should reset to 0")
            assertEquals(0, cursor.row, "Row should reset to 0")

            cursor.move(-10, -10)  // Should clamp
            assertEquals(0, cursor.col, "Column should be clamped to 0 after negative overflow")
            assertEquals(0, cursor.row, "Row should be clamped to 0 after negative overflow")
        }

        @Test
        fun `handles maximum integer movements gracefully`() {
            val cursor = Cursor(100, 100)
            cursor.set(50, 50)

            cursor.move(Int.MAX_VALUE, Int.MAX_VALUE)
            assertEquals(99, cursor.col, "Column should be clamped to 99 after max positive overflow")
            assertEquals(99, cursor.row, "Row should be clamped to 99 after max positive overflow")

            cursor.move(Int.MIN_VALUE, Int.MIN_VALUE)
            assertEquals(0, cursor.col, "Column should be clamped to 0 after max negative overflow")
            assertEquals(0, cursor.row, "Row should be clamped to 0 after max negative overflow")
        }

        @Test
        fun `different terminal sizes behave correctly`() {
            // Small terminal
            val small = Cursor(5, 3)
            small.set(2, 1)
            small.move(10, 10)
            assertEquals(4, small.col, "Column should be clamped to 4 in small terminal")
            assertEquals(2, small.row, "Row should be clamped to 2 in small terminal")

            // Wide terminal
            val wide = Cursor(200, 10)
            wide.set(100, 5)
            wide.move(150, 0)
            assertEquals(199, wide.col, "Column should be clamped to 199 in wide terminal")
            assertEquals(5, wide.row, "Row should remain unchanged in wide terminal")

            // Tall terminal
            val tall = Cursor(10, 200)
            tall.set(5, 100)
            tall.move(0, 150)
            assertEquals(5, tall.col, "Column should remain unchanged in tall terminal")
            assertEquals(199, tall.row, "Row should be clamped to 199 in tall terminal")
        }
    }
}