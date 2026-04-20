package com.gagik.terminal.engine

import com.gagik.terminal.codec.AttributeCodec
import com.gagik.terminal.model.TerminalConstants
import com.gagik.terminal.state.TerminalState
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TerminalResizerProtectionTest {

    private fun resizeState(state: TerminalState, newWidth: Int, newHeight: Int) {
        val oldWidth = state.dimensions.width
        val oldHeight = state.dimensions.height
        TerminalResizer.resizeBuffer(state.primaryBuffer, oldWidth, oldHeight, newWidth, newHeight)
        state.dimensions.width = newWidth
        state.dimensions.height = newHeight
        state.tabStops.resize(newWidth)
    }

    @Test
    fun `resize_reflow_preservesProtectionBits`() {
        val state = TerminalState(initialWidth = 5, initialHeight = 2, maxHistory = 2)
        val writer = MutationEngine(state)
        state.pen.setSelectiveEraseProtection(true)
        writer.printCodepoint('A'.code, 1)
        state.pen.setSelectiveEraseProtection(false)
        writer.printCodepoint('B'.code, 1)
        writer.printCodepoint('C'.code, 1)

        resizeState(state, newWidth = 2, newHeight = 3)

        val top = (state.ring.size - state.dimensions.height).coerceAtLeast(0)
        val firstVisible = state.ring[top]
        val secondVisible = state.ring[top + 1]
        val thirdVisible = state.ring[top + 2]

        assertAll(
            { assertEquals('A'.code, firstVisible.getCodepoint(0)) },
            { assertTrue(AttributeCodec.isProtected(firstVisible.getPackedAttr(0))) },
            { assertEquals('B'.code, firstVisible.getCodepoint(1)) },
            { assertFalse(AttributeCodec.isProtected(firstVisible.getPackedAttr(1))) },
            { assertEquals('C'.code, secondVisible.getCodepoint(0)) },
            { assertFalse(AttributeCodec.isProtected(secondVisible.getPackedAttr(0))) },
            { assertEquals(TerminalConstants.EMPTY, thirdVisible.getCodepoint(0)) }
        )
    }
}
