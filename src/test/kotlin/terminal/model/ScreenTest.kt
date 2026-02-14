package terminal.model

import com.gagik.terminal.buffer.HistoryRing
import com.gagik.terminal.model.Line
import com.gagik.terminal.model.Screen
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("Screen")
class ScreenTest {

    @Nested
    @DisplayName("Constructor")
    inner class ConstructorTests {

        @Test
        fun `throws on invalid height`() {
            val ring = HistoryRing(100) { Line(80) }
            assertThrows<IllegalArgumentException> { Screen(ring, 0, 80) }
            assertThrows<IllegalArgumentException> { Screen(ring, -1, 80) }
        }

        @Test
        fun `throws on invalid width`() {
            val ring = HistoryRing(100) { Line(80) }
            assertThrows<IllegalArgumentException> { Screen(ring, 24, 0) }
            assertThrows<IllegalArgumentException> { Screen(ring, 24, -1) }
        }

        @Test
        fun `accepts valid dimensions`() {
            val ring = HistoryRing(100) { Line(80) }
            assertDoesNotThrow { Screen(ring, 24, 80) }
        }
    }

    @Nested
    @DisplayName("getLine()")
    inner class GetLineTests {

        @Test
        fun `maps to last N lines of ring`() {
            val ring = HistoryRing(100) { Line(10) }
            val screen = Screen(ring, height = 3, width = 10)

            // Push 5 lines to ring
            ring.push().setCell(0, 'A'.code, 0)  // index 0 (oldest)
            ring.push().setCell(0, 'B'.code, 0)  // index 1
            ring.push().setCell(0, 'C'.code, 0)  // index 2
            ring.push().setCell(0, 'D'.code, 0)  // index 3
            ring.push().setCell(0, 'E'.code, 0)  // index 4 (newest)

            // Screen shows last 3 lines: C, D, E
            assertEquals('C'.code, screen.getLine(0).getCodepoint(0), "Row 0 should show line C")
            assertEquals('D'.code, screen.getLine(1).getCodepoint(0), "Row 1 should show line D")
            assertEquals('E'.code, screen.getLine(2).getCodepoint(0), "Row 2 should show line E")
        }

        @Test
        fun `handles ring smaller than screen height`() {
            val ring = HistoryRing(100) { Line(10) }
            val screen = Screen(ring, height = 5, width = 10)

            // Only push 2 lines
            ring.push().setCell(0, 'A'.code, 0)
            ring.push().setCell(0, 'B'.code, 0)

            // Screen shows from index 0 (since size < height)
            assertEquals('A'.code, screen.getLine(0).getCodepoint(0), "Row 0 should show line A")
            assertEquals('B'.code, screen.getLine(1).getCodepoint(0), "Row 1 should show line B")
        }

        @Test
        fun `throws on negative row`() {
            val ring = HistoryRing(100) { Line(10) }
            val screen = Screen(ring, height = 3, width = 10)

            assertThrows<IllegalArgumentException> { screen.getLine(-1) }
        }

        @Test
        fun `throws on row exceeding height`() {
            val ring = HistoryRing(100) { Line(10) }
            val screen = Screen(ring, height = 3, width = 10)

            assertThrows<IllegalArgumentException> { screen.getLine(3) }
            assertThrows<IllegalArgumentException> { screen.getLine(10) }
        }
    }

    @Nested
    @DisplayName("write()")
    inner class WriteTests {

        @Test
        fun `writes to screen coordinates`() {
            val ring = HistoryRing(100) { Line(10) }
            val screen = Screen(ring, height = 3, width = 10)

            // Initialize screen lines
            repeat(3) { ring.push() }

            screen.write(1, 5, 'X'.code, 99)

            assertEquals('X'.code, screen.getLine(1).getCodepoint(5), "Codepoint at (1,5) should be 'X'")
            assertEquals(99, screen.getLine(1).getAttr(5), "Attr at (1,5) should be 99")
        }

        @Test
        fun `ignores negative row`() {
            val ring = HistoryRing(100) { Line(10) }
            val screen = Screen(ring, height = 3, width = 10)
            repeat(3) { ring.push() }

            assertDoesNotThrow {
                screen.write(-1, 0, 'X'.code, 0)
            }
        }

        @Test
        fun `ignores row beyond height`() {
            val ring = HistoryRing(100) { Line(10) }
            val screen = Screen(ring, height = 3, width = 10)
            repeat(3) { ring.push() }

            assertDoesNotThrow {
                screen.write(3, 0, 'X'.code, 0)
                screen.write(100, 0, 'X'.code, 0)
            }
        }

        @Test
        fun `ignores negative col`() {
            val ring = HistoryRing(100) { Line(10) }
            val screen = Screen(ring, height = 3, width = 10)
            repeat(3) { ring.push() }

            assertDoesNotThrow {
                screen.write(0, -1, 'X'.code, 0)
            }
        }

        @Test
        fun `ignores col beyond width`() {
            val ring = HistoryRing(100) { Line(10) }
            val screen = Screen(ring, height = 3, width = 10)
            repeat(3) { ring.push() }

            assertDoesNotThrow {
                screen.write(0, 10, 'X'.code, 0)
                screen.write(0, 100, 'X'.code, 0)
            }
        }

        @Test
        fun `writes at boundaries`() {
            val ring = HistoryRing(100) { Line(10) }
            val screen = Screen(ring, height = 3, width = 10)
            repeat(3) { ring.push() }

            screen.write(0, 0, 'A'.code, 1)      // Top-left
            screen.write(2, 9, 'B'.code, 2)      // Bottom-right

            assertEquals('A'.code, screen.getLine(0).getCodepoint(0), "Codepoint at (0,0) should be 'A'")
            assertEquals('B'.code, screen.getLine(2).getCodepoint(9), "Codepoint at (2,9) should be 'B'")
        }
    }

    @Nested
    @DisplayName("scrollUp()")
    inner class ScrollUpTests {

        @Test
        fun `pushes new line and shifts view`() {
            val ring = HistoryRing(100) { Line(10) }
            val screen = Screen(ring, height = 3, width = 10)

            // Setup initial screen
            ring.push().setCell(0, 'A'.code, 0)
            ring.push().setCell(0, 'B'.code, 0)
            ring.push().setCell(0, 'C'.code, 0)

            screen.scrollUp(fillAttr = 88)

            // View shifted: B, C, (new blank)
            assertEquals('B'.code, screen.getLine(0).getCodepoint(0), "Row 0 should show line B after scroll")
            assertEquals('C'.code, screen.getLine(1).getCodepoint(0), "Row 1 should show line C after scroll")
            assertEquals(0, screen.getLine(2).getCodepoint(0), "Row 2 should be blank after scroll")
            assertEquals(88, screen.getLine(2).getAttr(0), "Row 2 should have fillAttr after scroll")

            // Ring size increased
            assertEquals(4, ring.size, "Ring size should increase by 1 after scroll")
        }

        @Test
        fun `old top line becomes history`() {
            val ring = HistoryRing(100) { Line(10) }
            val screen = Screen(ring, height = 3, width = 10)

            ring.push().setCell(0, 'A'.code, 0)
            ring.push().setCell(0, 'B'.code, 0)
            ring.push().setCell(0, 'C'.code, 0)

            screen.scrollUp(fillAttr = 0)

            // 'A' is now history (not visible on screen)
            assertEquals('A'.code, ring[0].getCodepoint(0), "Old top line 'A' should be in history after scroll")
            assertNotEquals('A'.code, screen.getLine(0).getCodepoint(0), "Old top line 'A' should not be visible on screen after scroll")
        }

        @Test
        fun `multiple scrolls build history`() {
            val ring = HistoryRing(100) { Line(10) }
            val screen = Screen(ring, height = 2, width = 10)

            ring.push().setCell(0, 'A'.code, 0)
            ring.push().setCell(0, 'B'.code, 0)

            screen.scrollUp(0)  // Push blank C
            screen.scrollUp(0)  // Push blank D

            assertEquals(4, ring.size)

            // Screen shows last 2 lines (C, D - both blank)
            assertEquals(0, screen.getLine(0).getCodepoint(0), "Row 0 should be blank after multiple scrolls")
            assertEquals(0, screen.getLine(1).getCodepoint(0), "Row 1 should be blank after multiple scrolls")

            // A and B are in history
            assertEquals('A'.code, ring[0].getCodepoint(0), "Line 0 in history should be 'A'")
            assertEquals('B'.code, ring[1].getCodepoint(0), "Line 1 in history should be 'B'")
        }

        @Test
        fun `clears new line with fillAttr`() {
            val ring = HistoryRing(100) { Line(10) }
            val screen = Screen(ring, height = 2, width = 10)

            ring.push()
            ring.push()

            screen.scrollUp(fillAttr = 123)

            val newLine = screen.getLine(1)
            for (col in 0 until 10) {
                assertEquals(0, newLine.getCodepoint(col), "New line should be cleared to codepoint 0")
                assertEquals(123, newLine.getAttr(col), "New line should have fillAttr 123")
            }
        }
    }

    @Nested
    @DisplayName("clear()")
    inner class ClearTests {

        @Test
        fun `clears only visible lines`() {
            val ring = HistoryRing(100) { Line(10) }
            val screen = Screen(ring, height = 2, width = 10)

            // Create history + screen
            ring.push().setCell(0, 'A'.code, 1)  // history
            ring.push().setCell(0, 'B'.code, 2)  // screen[0]
            ring.push().setCell(0, 'C'.code, 3)  // screen[1]

            screen.clear(fillAttr = 99)

            // History line unchanged
            assertEquals('A'.code, ring[0].getCodepoint(0))
            assertEquals(1, ring[0].getAttr(0))

            // Screen lines cleared
            assertEquals(0, screen.getLine(0).getCodepoint(0), "Screen line 0 should be cleared to codepoint 0")
            assertEquals(99, screen.getLine(0).getAttr(0), "Screen line 0 should have fillAttr 99")
            assertEquals(0, screen.getLine(1).getCodepoint(0), "Screen line 1 should be cleared to codepoint 0")
            assertEquals(99, screen.getLine(1).getAttr(0), "Screen line 1 should have fillAttr 99")
        }

        @Test
        fun `clears all cells in visible lines`() {
            val ring = HistoryRing(100) { Line(10) }
            val screen = Screen(ring, height = 2, width = 10)

            ring.push().apply {
                setCell(0, 'A'.code, 1)
                setCell(5, 'B'.code, 2)
                setCell(9, 'C'.code, 3)
            }
            ring.push()

            screen.clear(fillAttr = 88)

            val line = screen.getLine(0)
            for (col in 0 until 10) {
                assertEquals(0, line.getCodepoint(col), "All codepoints should be cleared to 0")
                assertEquals(88, line.getAttr(col), "All attrs should be set to fillAttr 88")
            }
        }

        @Test
        fun `handles partially filled ring`() {
            val ring = HistoryRing(100) { Line(10) }
            val screen = Screen(ring, height = 5, width = 10)

            // Only 2 lines exist (less than height)
            ring.push().setCell(0, 'A'.code, 0)
            ring.push().setCell(0, 'B'.code, 0)

            assertDoesNotThrow {
                screen.clear(fillAttr = 0)
            }

            // Both lines cleared
            assertEquals(0, screen.getLine(0).getCodepoint(0), "Line 0 should be cleared to codepoint 0")
            assertEquals(0, screen.getLine(1).getCodepoint(0), "Line 1 should be cleared to codepoint 0")
        }

        @Test
        fun `clear does not affect ring size`() {
            val ring = HistoryRing(100) { Line(10) }
            val screen = Screen(ring, height = 3, width = 10)

            repeat(5) { ring.push() }
            val sizeBefore = ring.size

            screen.clear(fillAttr = 0)

            assertEquals(sizeBefore, ring.size, "Ring size should not change after clear")
        }
    }

    @Nested
    @DisplayName("Integration")
    inner class IntegrationTests {

        @Test
        fun `write then scroll maintains data`() {
            val ring = HistoryRing(100) { Line(10) }
            val screen = Screen(ring, height = 2, width = 10)

            ring.push()
            ring.push()

            screen.write(0, 0, 'A'.code, 1)
            screen.write(1, 0, 'B'.code, 2)

            screen.scrollUp(0)

            // Old screen[0] ('A') is now history
            assertEquals('A'.code, ring[0].getCodepoint(0), "Old line 'A' should be in history after scroll")
            // Old screen[1] ('B') is now screen[0]
            assertEquals('B'.code, screen.getLine(0).getCodepoint(0), "After scroll, line B should be at screen row 0")
            // New screen[1] is blank
            assertEquals(0, screen.getLine(1).getCodepoint(0), "After scroll, line 1 should be blank")
        }

        @Test
        fun `scroll then write works correctly`() {
            val ring = HistoryRing(100) { Line(10) }
            val screen = Screen(ring, height = 2, width = 10)

            ring.push()
            ring.push()

            screen.scrollUp(0)
            screen.write(1, 0, 'X'.code, 99)

            assertEquals('X'.code, screen.getLine(1).getCodepoint(0), "After scroll and write, line X should be at screen row 1")
        }

        @Test
        fun `clear then write works correctly`() {
            val ring = HistoryRing(100) { Line(10) }
            val screen = Screen(ring, height = 2, width = 10)

            ring.push().setCell(0, 'A'.code, 1)
            ring.push()

            screen.clear(0)
            screen.write(0, 0, 'B'.code, 2)

            assertEquals('B'.code, screen.getLine(0).getCodepoint(0), "After clear and write, line B should be at screen row 0")
            assertEquals(2, screen.getLine(0).getAttr(0), "After clear and write, line B should have attr 2")
        }
    }
}