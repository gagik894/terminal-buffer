package com.gagik.terminal.state

import org.junit.jupiter.api.Assertions.*
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
        assertEquals(0, state.scrollTop)
        assertEquals(2, state.scrollBottom)
        assertTrue(state.isFullViewportScroll)
    }

    @Test
    fun `resolveRingIndex maps directly when there is no scrollback`() {
        val state = TerminalState(initialWidth = 10, initialHeight = 3, maxHistory = 7)

        assertAll(
            { assertEquals(0, state.resolveRingIndex(0)) },
            { assertEquals(1, state.resolveRingIndex(1)) },
            { assertEquals(2, state.resolveRingIndex(2)) }
        )
    }

    @Test
    fun `resolveRingIndex offsets by live screen top when history exists`() {
        val state = TerminalState(initialWidth = 10, initialHeight = 3, maxHistory = 7)

        repeat(2) { state.ring.push().clear(state.pen.currentAttr) }

        assertAll(
            { assertEquals(2, state.resolveRingIndex(0)) },
            { assertEquals(3, state.resolveRingIndex(1)) },
            { assertEquals(4, state.resolveRingIndex(2)) }
        )
    }

    @Test
    fun `setScrollRegion converts from 1-based rows and resets cursor`() {
        val state = TerminalState(initialWidth = 10, initialHeight = 5, maxHistory = 0)
        state.cursor.col = 7
        state.cursor.row = 3

        state.setScrollRegion(top = 2, bottom = 4)

        assertAll(
            { assertEquals(1, state.scrollTop) },
            { assertEquals(3, state.scrollBottom) },
            { assertEquals(0, state.cursor.col) },
            { assertEquals(0, state.cursor.row) },
            { assertFalse(state.isFullViewportScroll) }
        )
    }

    @Test
    fun `setScrollRegion clamps out-of-range bounds`() {
        val state = TerminalState(initialWidth = 10, initialHeight = 5, maxHistory = 0)

        state.setScrollRegion(top = -50, bottom = 99)

        assertAll(
            { assertEquals(0, state.scrollTop) },
            { assertEquals(4, state.scrollBottom) },
            { assertTrue(state.isFullViewportScroll) }
        )
    }

    @Test
    fun `setScrollRegion ignores degenerate region`() {
        val state = TerminalState(initialWidth = 10, initialHeight = 5, maxHistory = 0)
        state.setScrollRegion(top = 2, bottom = 4)
        state.cursor.col = 5
        state.cursor.row = 2

        state.setScrollRegion(top = 5, bottom = 5)

        assertAll(
            { assertEquals(1, state.scrollTop) },
            { assertEquals(3, state.scrollBottom) },
            { assertEquals(5, state.cursor.col) },
            { assertEquals(2, state.cursor.row) }
        )
    }

    @Test
    fun `resetScrollRegion restores full viewport`() {
        val state = TerminalState(initialWidth = 10, initialHeight = 5, maxHistory = 0)
        state.setScrollRegion(top = 2, bottom = 4)

        state.resetScrollRegion()

        assertAll(
            { assertEquals(0, state.scrollTop) },
            { assertEquals(4, state.scrollBottom) },
            { assertTrue(state.isFullViewportScroll) }
        )
    }
}