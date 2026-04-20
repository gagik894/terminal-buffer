package com.gagik.terminal.buffer.impl

import com.gagik.terminal.codec.AttributeCodec
import com.gagik.terminal.engine.MutationEngine
import com.gagik.terminal.model.Attributes
import com.gagik.terminal.state.TerminalState
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TerminalInspectorImplTest {

    @Test
    fun `renders visible rows screen and all content`() {
        val state = TerminalState(3, 2, 2)
        val mutation = MutationEngine(state)
        val inspector = TerminalInspectorImpl(state)

        mutation.printCodepoint('A'.code, 1)
        mutation.printCodepoint('B'.code, 1)
        state.cursor.col = 0
        mutation.newLine()
        mutation.printCodepoint('C'.code, 1)

        assertAll(
            { assertEquals("AB", inspector.getLineAsString(0)) },
            { assertEquals("C", inspector.getLineAsString(1)) },
            { assertEquals("AB\nC", inspector.getScreenAsString()) },
            { assertEquals("AB\nC", inspector.getAllAsString()) }
        )
    }

    @Test
    fun `unpacks cell attributes at a visible coordinate`() {
        val state = TerminalState(3, 1, 1)
        val mutation = MutationEngine(state)
        val inspector = TerminalInspectorImpl(state)

        state.pen.setAttributes(3, 7, bold = true, italic = true, underline = false)
        mutation.printCodepoint('X'.code, 1)

        val expected = Attributes(3, 7, bold = true, italic = true, underline = false)
        assertAll(
            { assertEquals(expected, inspector.getAttrAt(0, 0)) },
            { assertEquals(expected, AttributeCodec.unpack(state.ring[state.resolveRingIndex(0)].getPackedAttr(0))) }
        )
    }
}

