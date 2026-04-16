package com.gagik.terminal.state

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TerminalStateTest {

    @Test
    fun `constructor initializes dimensions cursor pen and ring capacity`() {
        val state = TerminalState(initialWidth = 10, initialHeight = 3, maxHistory = 7)

        assertEquals(10, state.dimensions.width)
        assertEquals(3, state.dimensions.height)
        assertEquals(0, state.cursor.col)
        assertEquals(0, state.cursor.row)
        assertEquals(3, state.ring.size)
        assertEquals(10, state.ring.capacity)
    }
}

