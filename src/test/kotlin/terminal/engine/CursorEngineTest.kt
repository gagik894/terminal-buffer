package com.gagik.terminal.engine

import com.gagik.terminal.state.TerminalState
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("CursorEngine Test Suite")
class CursorEngineTest {

    private fun createState(width: Int = 4, height: Int = 3, history: Int = 2): TerminalState {
        return TerminalState(width, height, maxHistory = history)
    }

    private fun lineAt(state: TerminalState, row: Int) = state.ring[state.resolveRingIndex(row)]

    private fun seedLine(state: TerminalState, row: Int, text: String, attr: Int = 0) {
        val line = lineAt(state, row)
        for ((i, ch) in text.withIndex()) {
            if (i >= line.width) break
            line.setCell(i, ch.code, attr)
        }
    }

    private fun screenSnapshot(state: TerminalState): String = buildString {
        for (row in 0 until state.dimensions.height) {
            if (row > 0) append('\n')
            append(lineAt(state, row).toTextTrimmed())
        }
    }

    @Nested
    @DisplayName("carriageReturn")
    inner class CarriageReturnTests {

        @Test
        fun `carriageReturn moves only the cursor column to zero`() {
            val state = createState()
            val engine = CursorEngine(state)
            state.cursor.col = 3
            state.cursor.row = 2

            engine.carriageReturn()

            assertAll(
                { assertEquals(0, state.cursor.col) },
                { assertEquals(2, state.cursor.row) }
            )
        }

        @Test
        fun `carriageReturn is idempotent at column zero`() {
            val state = createState()
            val engine = CursorEngine(state)
            state.cursor.col = 0
            state.cursor.row = 1

            engine.carriageReturn()
            engine.carriageReturn()

            assertAll(
                { assertEquals(0, state.cursor.col) },
                { assertEquals(1, state.cursor.row) }
            )
        }
    }

    @Nested
    @DisplayName("setCursor")
    inner class SetCursorTests {

        @Test
        fun `setCursor clamps to the top-left when given negatives`() {
            val state = createState(width = 5, height = 4)
            val engine = CursorEngine(state)

            engine.setCursor(-99, -42)

            assertAll(
                { assertEquals(0, state.cursor.col) },
                { assertEquals(0, state.cursor.row) }
            )
        }

        @Test
        fun `setCursor clamps to the bottom-right when given values past bounds`() {
            val state = createState(width = 5, height = 4)
            val engine = CursorEngine(state)

            engine.setCursor(99, 999)

            assertAll(
                { assertEquals(4, state.cursor.col) },
                { assertEquals(3, state.cursor.row) }
            )
        }

        @Test
        fun `setCursor accepts in-bounds coordinates without adjustment`() {
            val state = createState(width = 6, height = 3)
            val engine = CursorEngine(state)

            engine.setCursor(2, 1)

            assertAll(
                { assertEquals(2, state.cursor.col) },
                { assertEquals(1, state.cursor.row) }
            )
        }
    }

    @Nested
    @DisplayName("saveCursor")
    inner class SaveCursorTests {

        @Test
        fun `saveCursor captures cursor position and pen attributes`() {
            val state = createState()
            val engine = CursorEngine(state)
            state.cursor.col = 2
            state.cursor.row = 1
            state.pen.setAttributes(fg = 3, bg = 4, bold = true, italic = true, underline = false)

            engine.saveCursor()

            assertAll(
                { assertTrue(state.savedCursor.isSaved) },
                { assertEquals(2, state.savedCursor.col) },
                { assertEquals(1, state.savedCursor.row) },
                { assertEquals(state.pen.currentAttr, state.savedCursor.attr) }
            )
        }

        @Test
        fun `saveCursor overwrites the previous saved state`() {
            val state = createState()
            val engine = CursorEngine(state)

            state.cursor.col = 1
            state.cursor.row = 2
            state.pen.setAttributes(fg = 1, bg = 2, bold = true)
            engine.saveCursor()

            state.cursor.col = 3
            state.cursor.row = 0
            state.pen.setAttributes(fg = 5, bg = 6, italic = true)
            engine.saveCursor()

            assertAll(
                { assertTrue(state.savedCursor.isSaved) },
                { assertEquals(3, state.savedCursor.col) },
                { assertEquals(0, state.savedCursor.row) },
                { assertEquals(state.pen.currentAttr, state.savedCursor.attr) }
            )
        }

        @Test
        fun `saveCursor does not mutate the grid`() {
            val state = createState(width = 4, height = 2)
            val engine = CursorEngine(state)
            seedLine(state, 0, "AB")
            seedLine(state, 1, "CD")
            val before = screenSnapshot(state)

            engine.saveCursor()

            assertEquals(before, screenSnapshot(state))
        }
    }

    @Nested
    @DisplayName("restoreCursor")
    inner class RestoreCursorTests {

        @Test
        fun `restoreCursor without a saved state homes the cursor and resets the pen`() {
            val state = createState()
            val engine = CursorEngine(state)
            val defaultAttr = state.pen.currentAttr
            state.cursor.col = 3
            state.cursor.row = 2
            state.pen.setAttributes(fg = 7, bg = 8, bold = true, italic = true, underline = true)

            engine.restoreCursor()

            assertAll(
                { assertFalse(state.savedCursor.isSaved) },
                { assertEquals(0, state.cursor.col) },
                { assertEquals(0, state.cursor.row) },
                { assertEquals(defaultAttr, state.pen.currentAttr) }
            )
        }

        @Test
        fun `restoreCursor restores saved cursor position and pen attributes`() {
            val state = createState()
            val engine = CursorEngine(state)
            state.cursor.col = 1
            state.cursor.row = 2
            state.pen.setAttributes(fg = 2, bg = 3, bold = true, underline = true)
            engine.saveCursor()

            state.cursor.col = 0
            state.cursor.row = 0
            state.pen.setAttributes(fg = 5, bg = 6, italic = true)

            engine.restoreCursor()

            assertAll(
                { assertEquals(1, state.cursor.col) },
                { assertEquals(2, state.cursor.row) },
                { assertEquals(state.savedCursor.attr, state.pen.currentAttr) }
            )
        }

        @Test
        fun `restoreCursor clamps saved coordinates after resize`() {
            val state = createState(width = 6, height = 5)
            val engine = CursorEngine(state)
            state.cursor.col = 5
            state.cursor.row = 4
            state.pen.setAttributes(fg = 4, bg = 5, bold = true)
            engine.saveCursor()

            state.dimensions.width = 3
            state.dimensions.height = 2
            state.scrollBottom = 1

            engine.restoreCursor()

            assertAll(
                { assertEquals(2, state.cursor.col) },
                { assertEquals(1, state.cursor.row) },
                { assertEquals(state.savedCursor.attr, state.pen.currentAttr) }
            )
        }

        @Test
        fun `restoreCursor does not mutate the grid`() {
            val state = createState(width = 4, height = 2)
            val engine = CursorEngine(state)
            seedLine(state, 0, "AB")
            seedLine(state, 1, "CD")
            val before = screenSnapshot(state)

            engine.restoreCursor()

            assertEquals(before, screenSnapshot(state))
        }

        @Test
        fun `restoreCursor preserves a previously saved state across multiple restores`() {
            val state = createState()
            val engine = CursorEngine(state)
            state.cursor.col = 2
            state.cursor.row = 1
            state.pen.setAttributes(fg = 1, bg = 2)
            engine.saveCursor()

            state.cursor.col = 0
            state.cursor.row = 0
            state.pen.reset()

            engine.restoreCursor()
            state.cursor.col = 3
            state.cursor.row = 2
            state.pen.setAttributes(fg = 7, bg = 8)
            engine.restoreCursor()

            assertAll(
                { assertEquals(2, state.cursor.col) },
                { assertEquals(1, state.cursor.row) },
                { assertEquals(state.savedCursor.attr, state.pen.currentAttr) },
                { assertTrue(state.savedCursor.isSaved) }
            )
        }
    }
}


