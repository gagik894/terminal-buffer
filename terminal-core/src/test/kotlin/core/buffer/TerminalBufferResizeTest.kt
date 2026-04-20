package com.gagik.core.buffer

import com.gagik.core.state.TerminalState
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TerminalBufferResizeTest {

    private fun stateOf(buffer: TerminalBuffer): TerminalState {
        val componentsField = TerminalBuffer::class.java.getDeclaredField("components")
        componentsField.isAccessible = true
        val components = componentsField.get(buffer)

        val stateField = components.javaClass.getDeclaredField("state")
        stateField.isAccessible = true
        return stateField.get(components) as TerminalState
    }

    @Test
    fun `resizeWhileAltActive_clampsPrimaryScrollRegionBeforeExit`() {
        val buffer = TerminalBuffer(initialWidth = 5, initialHeight = 6, maxHistory = 4)
        buffer.setScrollRegion(top = 3, bottom = 6)
        buffer.enterAltBuffer()

        buffer.resize(newWidth = 5, newHeight = 3)

        val state = stateOf(buffer)
        assertAll(
            { assertTrue(state.primaryBuffer.scrollTop in 0 until 3) },
            { assertTrue(state.primaryBuffer.scrollBottom in 0 until 3) },
            { assertTrue(state.primaryBuffer.scrollTop <= state.primaryBuffer.scrollBottom) }
        )
    }

    @Test
    fun `resizeWhileAltActive_thenInsertLinesOnPrimary_doesNotIndexPastRing`() {
        val buffer = TerminalBuffer(initialWidth = 5, initialHeight = 6, maxHistory = 4)
        buffer.setScrollRegion(top = 3, bottom = 6)
        buffer.enterAltBuffer()
        buffer.resize(newWidth = 5, newHeight = 3)
        buffer.exitAltBuffer()
        buffer.positionCursor(col = 0, row = 2)

        assertDoesNotThrow {
            buffer.insertLines(1)
        }
    }

    @Test
    fun `resize_resetsOrClampsBothBuffersMarginsPerChosenPolicy`() {
        val buffer = TerminalBuffer(initialWidth = 7, initialHeight = 6, maxHistory = 4)
        buffer.setScrollRegion(top = 2, bottom = 5)
        buffer.enterAltBuffer()
        buffer.setScrollRegion(top = 3, bottom = 6)

        buffer.resize(newWidth = 7, newHeight = 3)

        val state = stateOf(buffer)
        assertAll(
            { assertTrue(state.primaryBuffer.scrollTop in 0 until 3) },
            { assertTrue(state.primaryBuffer.scrollBottom in 0 until 3) },
            { assertTrue(state.primaryBuffer.scrollTop <= state.primaryBuffer.scrollBottom) },
            { assertTrue(state.altBuffer.scrollTop in 0 until 3) },
            { assertTrue(state.altBuffer.scrollBottom in 0 until 3) },
            { assertTrue(state.altBuffer.scrollTop <= state.altBuffer.scrollBottom) }
        )
    }
}
