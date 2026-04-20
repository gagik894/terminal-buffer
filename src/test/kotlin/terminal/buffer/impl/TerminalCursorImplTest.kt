package com.gagik.terminal.buffer.impl

import com.gagik.terminal.engine.CursorEngine
import com.gagik.terminal.state.TerminalState
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TerminalCursorImplTest {

    @Test
    fun `moves the cursor and clamps to bounds`() {
        val state = TerminalState(5, 4, 2)
        val cursor = TerminalCursorImpl(state, CursorEngine(state))

        cursor.positionCursor(2, 1)
        cursor.cursorRight(10)
        cursor.cursorDown(10)
        cursor.cursorLeft(99)
        cursor.cursorUp(99)

        assertAll(
            { assertEquals(0, state.cursor.col) },
            { assertEquals(0, state.cursor.row) }
        )
    }

    @Test
    fun `save and restore cursor round trip saved state`() {
        val state = TerminalState(4, 3, 2)
        val cursor = TerminalCursorImpl(state, CursorEngine(state))

        state.pen.setAttributes(2, 3, bold = true, italic = false, underline = true)
        cursor.positionCursor(3, 2)
        cursor.saveCursor()

        cursor.positionCursor(0, 0)
        state.pen.reset()
        cursor.restoreCursor()

        assertAll(
            { assertEquals(3, state.cursor.col) },
            { assertEquals(2, state.cursor.row) }
        )
    }

    @Test
    fun `tab stop helpers delegate to shared tab stop state`() {
        val state = TerminalState(20, 1, 0)
        val cursor = TerminalCursorImpl(state, CursorEngine(state))

        cursor.positionCursor(10, 0)
        cursor.setTabStop()
        cursor.resetCursor()
        cursor.horizontalTab()

        assertEquals(8, state.cursor.col)
    }

    @Test
    fun `set clear and clearAll tab stop helpers round trip through the facade semantics`() {
        val state = TerminalState(20, 1, 0)
        val cursor = TerminalCursorImpl(state, CursorEngine(state))

        cursor.clearAllTabStops()
        cursor.positionCursor(5, 0)
        cursor.setTabStop()
        cursor.resetCursor()
        cursor.horizontalTab()
        assertEquals(5, state.cursor.col)

        cursor.positionCursor(5, 0)
        cursor.clearTabStop()
        cursor.resetCursor()
        cursor.horizontalTab()
        assertEquals(19, state.cursor.col)
    }

    @Test
    fun `cursorForwardTab advances through multiple stops via the facade`() {
        val state = TerminalState(20, 1, 0)
        val cursor = TerminalCursorImpl(state, CursorEngine(state))

        cursor.positionCursor(0, 0)
        cursor.cursorForwardTab(2)

        assertEquals(16, state.cursor.col)
    }

    @Test
    fun `cursorBackwardTab moves through stops via the facade`() {
        val state = TerminalState(20, 1, 0)
        val cursor = TerminalCursorImpl(state, CursorEngine(state))

        cursor.positionCursor(19, 0)
        cursor.cursorBackwardTab(2)

        assertEquals(8, state.cursor.col)
    }
}

