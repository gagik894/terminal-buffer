package com.gagik.terminal.buffer.impl

import com.gagik.terminal.engine.CursorEngine
import com.gagik.terminal.engine.MutationEngine
import com.gagik.terminal.model.Attributes
import com.gagik.terminal.state.TerminalState
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TerminalWriterImplTest {

    @Test
    fun `writes codepoints and advances the cursor`() {
        val state = TerminalState(5, 2, 2)
        val writer = TerminalWriterImpl(state, MutationEngine(state), CursorEngine(state))

        writer.setPenAttributes(3, 7, bold = true, italic = true, underline = false)
        writer.writeCodepoint('X'.code)

        assertAll(
            { assertEquals('X'.code, state.ring[state.resolveRingIndex(0)].getCodepoint(0)) },
            { assertEquals(1, state.cursor.col) },
            { assertEquals(0, state.cursor.row) },
            { assertEquals(Attributes(3, 7, bold = true, italic = true, underline = false),
                com.gagik.terminal.codec.AttributeCodec.unpack(state.ring[state.resolveRingIndex(0)].getPackedAttr(0))) }
        )
    }

    @Test
    fun `writes text with supplementary code points literally`() {
        val state = TerminalState(6, 2, 2)
        val writer = TerminalWriterImpl(state, MutationEngine(state), CursorEngine(state))

        writer.writeText("A😀B")

        assertAll(
            { assertEquals("A😀B", state.ring[state.resolveRingIndex(0)].toTextTrimmed()) },
            { assertEquals(4, state.cursor.col) }
        )
    }

    @Test
    fun `clearAll wipes screen history cursor and saved cursor`() {
        val state = TerminalState(4, 2, 2)
        val mutation = MutationEngine(state)
        val writer = TerminalWriterImpl(state, mutation, CursorEngine(state))

        writer.writeText("ABCD")
        state.savedCursor.isSaved = true
        writer.clearAll()

        assertAll(
            { assertEquals("", state.ring[state.resolveRingIndex(0)].toTextTrimmed()) },
            { assertEquals(0, state.cursor.col) },
            { assertEquals(0, state.cursor.row) },
            { assertEquals(false, state.savedCursor.isSaved) }
        )
    }
}

