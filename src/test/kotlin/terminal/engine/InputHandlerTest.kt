package com.gagik.terminal.engine

import com.gagik.terminal.state.TerminalState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InputHandlerTest {

    private fun createState(width: Int = 3, height: Int = 2): TerminalState {
        return TerminalState(width, height, maxHistory = 4)
    }

    @Test
    fun `print writes cell and advances cursor`() {
        val state = createState()
        val handler = InputHandler(state)

        handler.print('A'.code)

        assertEquals('A'.code, state.ring[0].getCodepoint(0))
        assertEquals(1, state.cursor.col)
        assertEquals(0, state.cursor.row)
    }

    @Test
    fun `print wraps at end of line and marks wrapped`() {
        val state = createState(width = 2, height = 2)
        val handler = InputHandler(state)

        handler.print('A'.code)
        handler.print('B'.code)

        assertTrue(state.ring[0].wrapped)
        assertEquals(0, state.cursor.col)
        assertEquals(1, state.cursor.row)
    }

    @Test
    fun `newLine at bottom scrolls and keeps cursor on last row`() {
        val state = createState(width = 2, height = 2)
        val handler = InputHandler(state)
        state.cursor.row = 1

        handler.newLine()

        assertEquals(1, state.cursor.row)
        assertEquals(3, state.ring.size)
    }

    @Test
    fun `carriageReturn resets only column`() {
        val state = createState()
        val handler = InputHandler(state)
        state.cursor.col = 2
        state.cursor.row = 1

        handler.carriageReturn()

        assertEquals(0, state.cursor.col)
        assertEquals(1, state.cursor.row)
    }
}

