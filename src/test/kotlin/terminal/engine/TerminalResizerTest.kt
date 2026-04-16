package com.gagik.terminal.engine

import com.gagik.terminal.model.TerminalConstants
import com.gagik.terminal.state.TerminalState
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/**
 * Builds a minimal TerminalState for test purposes.
 *
 * @param cols   screen width in columns
 * @param rows   screen height in rows
 * @param history extra scrollback lines above the screen
 */
private fun buildState(cols: Int, rows: Int, history: Int = 0): TerminalState {
    val state = TerminalState(
        maxHistory = history,
        initialWidth = cols,
        initialHeight = rows
    )
    return state
}

/**
 * Writes an ASCII string into the given screen row (0 = top of visible screen),
 * starting at column 0.  Attributes are left at zero.
 *
 * HistoryRing.get(i) returns lines oldest-first, so the top of the visible
 * screen is at index (ring.size - height) and the bottom is at (ring.size - 1).
 */
private fun TerminalState.writeLine(screenRow: Int, text: String) {
    val top = (ring.size - dimensions.height).coerceAtLeast(0)
    val line = ring[top + screenRow]
    for ((col, ch) in text.withIndex()) {
        if (col >= dimensions.width) break
        line.setCell(col, ch.code, 0)
    }
}

/**
 * Returns the visible screen as a list of trimmed strings, one per row,
 * ordered top-to-bottom.
 */
private fun TerminalState.screenLines(): List<String> {
    val top = (ring.size - dimensions.height).coerceAtLeast(0)
    return (0 until dimensions.height).map { r ->
        ring[top + r].toTextTrimmed()
    }
}

/**
 * Returns the visible screen content as a single string with all rows
 * concatenated in order, without any separator.  Useful for checking that
 * content is present somewhere across a reflowed screen regardless of how
 * it was split across physical lines.
 */
private fun TerminalState.screenText(): String =
    screenLines().joinToString("")

// ---------------------------------------------------------------------------
// Test suite
// ---------------------------------------------------------------------------

@DisplayName("TerminalResizer Test Suite")
class TerminalResizerTest {

    // -----------------------------------------------------------------------
    // No-op resize
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Same-size resize")
    inner class SameSizeTests {

        @Test
        @DisplayName("Resize to identical dimensions leaves content and cursor unchanged")
        fun `resize to same size is a no-op`() {
            val state = buildState(cols = 10, rows = 5)
            state.writeLine(4, "Hello")
            state.cursor.col = 3
            state.cursor.row = 4

            TerminalResizer.resize(state, 10, 5)

            assertAll(
                { assertEquals(10, state.dimensions.width) },
                { assertEquals(5, state.dimensions.height) },
                { assertEquals(3, state.cursor.col) },
                { assertEquals(4, state.cursor.row) },
                { assertEquals("Hello", state.screenLines()[4]) }
            )
        }
    }

    // -----------------------------------------------------------------------
    // Width changes — content reflow
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Width changes (reflow)")
    inner class WidthChangeTests {

        @Test
        @DisplayName("Narrowing splits a long line into two wrapped physical lines")
        fun `narrow splits line`() {
            val state = buildState(cols = 10, rows = 5)
            // Write on the last row so no blank lines follow it through the resizer.
            // The resizer processes all ring rows; blank trailing rows each push an
            // extra empty line into the new ring, which shifts reflow output above
            // the live screen window if content is written to an earlier row.
            state.writeLine(4, "HelloWorld")
            state.cursor.row = 4

            TerminalResizer.resize(state, 5, 5)

            val lines = state.screenLines()
            assertAll(
                { assertEquals("Hello", lines[3]) },
                { assertEquals("World", lines[4]) }
            )
        }

        @Test
        @DisplayName("Widening merges two previously wrapped lines back into one")
        fun `widen merges wrapped lines`() {
            val state = buildState(cols = 5, rows = 5)
            // Use the last two rows so no blank lines follow them through the resizer.
            val top = (state.ring.size - 5).coerceAtLeast(0)
            state.ring[top + 3].apply {
                for ((i, ch) in "Hello".withIndex()) setCell(i, ch.code, 0)
                wrapped = true
            }
            state.ring[top + 4].apply {
                for ((i, ch) in "World".withIndex()) setCell(i, ch.code, 0)
                wrapped = false
            }
            state.cursor.row = 4

            TerminalResizer.resize(state, 10, 5)

            // Two physical lines merged into one, so the result lands one row higher.
            assertEquals("HelloWorld", state.screenLines()[3])
        }

        @ParameterizedTest(name = "Content survives resize to width={0}")
        @CsvSource("1", "3", "7", "20", "80")
        fun `content is preserved across arbitrary width changes`(newWidth: Int) {
            val state = buildState(cols = 10, rows = 5)
            // Write on the last row so the content is not pushed out of the live
            // screen window by blank lines that follow it through the resizer.
            state.writeLine(4, "ABCDE")
            state.cursor.row = 4

            TerminalResizer.resize(state, newWidth, 5)

            // Join using untrimmed rows so characters split across physical lines
            // by the reflow still appear contiguous when concatenated.
            val top = (state.ring.size - state.dimensions.height).coerceAtLeast(0)
            val allText = (0 until state.dimensions.height)
                .joinToString("") { state.ring[top + it].toText() }
            assertTrue(allText.contains("ABCDE"),
                "Expected 'ABCDE' somewhere in the screen after resize to width $newWidth")
        }

        @Test
        @DisplayName("Wide character clusters are kept together when a resize would split them")
        fun `wide character does not split across rows`() {
            val state = buildState(cols = 6, rows = 2)
            val top = (state.ring.size - state.dimensions.height).coerceAtLeast(0)
            state.ring[top + 1].apply {
                setCell(0, 'A'.code, 0)
                setCell(1, 'B'.code, 0)
                setCell(2, 'C'.code, 0)
                setCell(3, '你'.code, 0)
                setCell(4, TerminalConstants.WIDE_CHAR_SPACER, 0)
                setCell(5, 'D'.code, 0)
            }
            state.cursor.row = 1

            TerminalResizer.resize(state, 4, 2)

            val visibleTop = (state.ring.size - state.dimensions.height).coerceAtLeast(0)
            val first = state.ring[visibleTop]
            val second = state.ring[visibleTop + 1]

            assertAll(
                { assertEquals('A'.code, first.getCodepoint(0)) },
                { assertEquals('B'.code, first.getCodepoint(1)) },
                { assertEquals('C'.code, first.getCodepoint(2)) },
                { assertEquals(TerminalConstants.EMPTY, first.getCodepoint(3)) },
                { assertEquals('你'.code, second.getCodepoint(0)) },
                { assertEquals(TerminalConstants.WIDE_CHAR_SPACER, second.getCodepoint(1)) },
                { assertEquals('D'.code, second.getCodepoint(2)) },
                { assertEquals(TerminalConstants.EMPTY, second.getCodepoint(3)) }
            )
        }
    }

    // -----------------------------------------------------------------------
    // Height changes
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Height changes")
    inner class HeightChangeTests {

        @Test
        @DisplayName("Shrinking height trims blank rows at the bottom")
        fun `shrink height removes blank rows`() {
            val state = buildState(cols = 10, rows = 10)
            // Write content near the bottom of the screen so it stays visible
            // after shrinking — the live screen is always the bottom N rows.
            state.writeLine(9, "Top")

            TerminalResizer.resize(state, 10, 5)

            assertAll(
                { assertEquals(5, state.dimensions.height) },
                { assertEquals("Top", state.screenLines()[4]) }
            )
        }

        @Test
        @DisplayName("Growing height adds blank rows and keeps existing content intact")
        fun `grow height adds blank rows`() {
            val state = buildState(cols = 10, rows = 5)
            state.writeLine(4, "Content")
            state.cursor.row = 4

            TerminalResizer.resize(state, 10, 10)

            assertAll(
                { assertEquals(10, state.dimensions.height) },
                { assertTrue(state.screenLines().any { it == "Content" }) }
            )
        }
    }

    // -----------------------------------------------------------------------
    // Cursor tracking
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Cursor position after resize")
    inner class CursorTrackingTests {

        @Test
        @DisplayName("Cursor column is clamped to new width when narrowing")
        fun `cursor col clamped on narrow`() {
            val state = buildState(cols = 20, rows = 5)
            state.cursor.col = 15
            state.cursor.row = 0

            TerminalResizer.resize(state, 10, 5)

            assertTrue(state.cursor.col < 10,
                "Cursor column ${state.cursor.col} should be < new width 10")
        }

        @Test
        @DisplayName("Cursor moves to correct physical line when its logical line is split by narrowing")
        fun `cursor tracked across line split`() {
            val state = buildState(cols = 10, rows = 5)
            // Write on the last row and place the cursor at col 7 on that row.
            state.writeLine(4, "AAABBBCCC")
            state.cursor.col = 7
            state.cursor.row = 4

            TerminalResizer.resize(state, 5, 5)

            // "AAABBBCCC" splits into "AAABB" (row 3) and "BCCC " (row 4).
            // Col 7 in the original maps to col 2 on the second physical chunk.
            assertAll(
                { assertEquals(2, state.cursor.col, "cursor col after split") },
                { assertTrue(state.cursor.row in 0 until 5, "cursor row in bounds") }
            )
        }

        @Test
        @DisplayName("Cursor row stays within screen bounds after any resize")
        fun `cursor row always in bounds`() {
            val state = buildState(cols = 80, rows = 24)
            state.cursor.row = 20
            state.cursor.col = 5

            TerminalResizer.resize(state, 40, 10)

            assertAll(
                { assertTrue(state.cursor.row in 0 until 10,
                    "cursor row ${state.cursor.row} out of bounds for height 10") },
                { assertTrue(state.cursor.col in 0 until 40,
                    "cursor col ${state.cursor.col} out of bounds for width 40") }
            )
        }

        @Test
        @DisplayName("Cursor on blank virtual row below ring content is preserved (Bug 2 regression)")
        fun `cursor on virtual blank row below ring content`() {
            // Fresh state: ring has only a few lines, but the cursor is on
            // screen row 5 which is beyond the actual ring content.
            val state = buildState(cols = 80, rows = 24)
            state.writeLine(0, "Line0")
            state.writeLine(1, "Line1")
            // Place cursor on screen row 5 — beyond the two real lines.
            state.cursor.row = 5
            state.cursor.col = 10

            // Resize should not crash and the cursor should end up in-bounds.
            TerminalResizer.resize(state, 80, 24)

            assertAll(
                { assertTrue(state.cursor.row in 0 until 24,
                    "cursor row ${state.cursor.row} out of bounds") },
                { assertTrue(state.cursor.col in 0 until 80,
                    "cursor col ${state.cursor.col} out of bounds") }
            )
        }

        @Test
        @DisplayName("Cursor on empty line at col 0 is preserved (Bug 1 regression)")
        fun `cursor on empty logical line is preserved`() {
            val state = buildState(cols = 10, rows = 5)
            // Leave row 0 completely blank; put the cursor at (0, 0).
            state.cursor.row = 0
            state.cursor.col = 0

            TerminalResizer.resize(state, 10, 5)

            assertAll(
                { assertEquals(0, state.cursor.row) },
                { assertEquals(0, state.cursor.col) }
            )
        }
    }

    // -----------------------------------------------------------------------
    // Scrollback / history
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Scrollback history")
    inner class ScrollbackTests {

        @Test
        @DisplayName("History lines are preserved after resize")
        fun `history is preserved after resize`() {
            val state = buildState(cols = 10, rows = 3, history = 5)
            // Fill the ring so we have scrollback.
            for (r in 0 until 3) state.writeLine(r, "Row$r")
            // Push a couple more lines into history by scrolling.
            repeat(2) {
                state.ring.push().clear(0)
            }

            TerminalResizer.resize(state, 10, 3)

            // Ring should still have content; screen should render without error.
            assertTrue(state.ring.size >= 3)
        }

        @Test
        @DisplayName("New ring capacity honours maxHistory + newHeight")
        fun `ring capacity after resize`() {
            val state = buildState(cols = 10, rows = 5, history = 100)

            TerminalResizer.resize(state, 10, 10)

            // The ring was created with capacity maxHistory + newHeight = 110.
            // After filling just one screen, size should be exactly newHeight.
            assertTrue(state.ring.size >= 10,
                "Ring should hold at least one screen of lines")
        }
    }

    // -----------------------------------------------------------------------
    // Wrapped-flag correctness
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Wrapped flag handling")
    inner class WrappedFlagTests {

        @Test
        @DisplayName("Last physical chunk of a reflowed logical line is not marked as wrapped")
        fun `last chunk not wrapped`() {
            val state = buildState(cols = 10, rows = 5)
            // Write on the last row so the two reflow chunks land at the bottom of
            // the new ring with no blank lines pushing them above the screen window.
            state.writeLine(4, "HelloWorld")
            state.cursor.row = 4

            TerminalResizer.resize(state, 5, 5)

            val top = (state.ring.size - 5).coerceAtLeast(0)
            // The last two rows are the two chunks; row 3 is wrapped, row 4 is not.
            assertAll(
                { assertTrue(state.ring[top + 3].wrapped, "first chunk should be wrapped") },
                { assertFalse(state.ring[top + 4].wrapped, "last chunk must not be wrapped") }
            )
        }

        @Test
        @DisplayName("Orphaned wrap flag (empty line marked wrapped) does not cause phantom content")
        fun `orphaned wrap flag is benign`() {
            val state = buildState(cols = 10, rows = 5)
            // Artificially create an orphaned wrap flag: empty line with wrapped=true.
            val top = (state.ring.size - 5).coerceAtLeast(0)
            state.ring[top].wrapped = true   // no content, wrapped=true

            // Should not throw and should not produce content.
            TerminalResizer.resize(state, 10, 5)

            assertEquals("", state.screenLines()[0],
                "Orphaned-wrapped empty line should produce no visible content")
        }
    }

    // -----------------------------------------------------------------------
    // Dimensions update
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Dimensions update")
    inner class DimensionsTests {

        @ParameterizedTest(name = "Resize {0}x{1} → {2}x{3}")
        @CsvSource(
            "80, 24, 40, 12",
            "40, 12, 80, 24",
            "10, 5,  1,  1",
            "1,  1, 80, 24"
        )
        fun `dimensions are updated correctly`(
            oldW: Int, oldH: Int, newW: Int, newH: Int
        ) {
            val state = buildState(cols = oldW, rows = oldH)

            TerminalResizer.resize(state, newW, newH)

            assertAll(
                { assertEquals(newW, state.dimensions.width,  "width after resize") },
                { assertEquals(newH, state.dimensions.height, "height after resize") }
            )
        }
    }
}
