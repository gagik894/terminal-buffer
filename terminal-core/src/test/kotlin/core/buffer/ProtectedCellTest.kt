package com.gagik.core.buffer

import com.gagik.core.TerminalBuffers
import com.gagik.core.api.TerminalBufferApi
import com.gagik.core.codec.AttributeCodec
import com.gagik.core.state.TerminalState
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ProtectedCellTest {

    private fun blankScreen(height: Int): String = List(height) { "" }.joinToString("\n")

    private fun stateOf(api: TerminalBufferApi): TerminalState {
        val componentsField = api.javaClass.getDeclaredField("components")
        componentsField.isAccessible = true
        val components = componentsField.get(api)

        val stateField = components.javaClass.getDeclaredField("state")
        stateField.isAccessible = true
        return stateField.get(components) as TerminalState
    }

    @Test
    fun `altBufferEntry_noProtectedCells`() {
        val buffer = TerminalBuffers.create(width = 4, height = 2, maxHistory = 2)
        val state = stateOf(buffer)
        buffer.setSelectiveEraseProtection(true)
        buffer.writeCodepoint('A'.code)

        buffer.enterAltBuffer()

        assertAll(
            { assertEquals(blankScreen(2), buffer.getScreenAsString()) },
            { assertFalse(AttributeCodec.isProtected(state.altBuffer.ring[state.altBuffer.ring.size - 1].getPackedAttr(0))) }
        )
    }

    @Test
    fun `clearAllHistory_ignoresProtection`() {
        val buffer = TerminalBuffers.create(width = 4, height = 2, maxHistory = 2)
        val state = stateOf(buffer)
        buffer.setSelectiveEraseProtection(true)
        buffer.writeText("AB")

        buffer.clearAll()

        val top = (state.primaryBuffer.ring.size - state.dimensions.height).coerceAtLeast(0)
        assertAll(
            { assertEquals(blankScreen(2), buffer.getScreenAsString()) },
            { assertFalse(AttributeCodec.isProtected(state.primaryBuffer.ring[top].getPackedAttr(0))) }
        )
    }
}
