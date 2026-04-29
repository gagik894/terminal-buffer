package com.gagik.core.buffer

import com.gagik.core.api.TerminalBufferApi
import com.gagik.core.model.Line
import com.gagik.core.model.UnderlineStyle
import com.gagik.core.state.TerminalState
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TerminalRenderGenerationTest {
    @Test
    fun `printing changes frame and line generation`() {
        val buffer = TerminalBuffer(initialWidth = 4, initialHeight = 2)
        val state = stateOf(buffer)
        val line = lineAt(state, 0)
        val oldFrame = state.frameGeneration
        val oldLine = line.renderGeneration

        buffer.writeCodepoint('A'.code)

        assertAll(
            { assertNotEquals(oldFrame, state.frameGeneration) },
            { assertNotEquals(oldLine, line.renderGeneration) },
        )
    }

    @Test
    fun `cursor movement changes cursor generation without dirtying line generation`() {
        val buffer = TerminalBuffer(initialWidth = 4, initialHeight = 2)
        val state = stateOf(buffer)
        buffer.writeCodepoint('A'.code)
        val line = lineAt(state, 0)
        val oldFrame = state.frameGeneration
        val oldCursor = state.cursorGeneration
        val oldLine = line.renderGeneration

        buffer.positionCursor(0, 1)

        assertAll(
            { assertNotEquals(oldFrame, state.frameGeneration) },
            { assertNotEquals(oldCursor, state.cursorGeneration) },
            { assertEquals(oldLine, line.renderGeneration) },
        )
    }

    @Test
    fun `SGR alone does not change render generations but later print does`() {
        val buffer = TerminalBuffer(initialWidth = 4, initialHeight = 2)
        val state = stateOf(buffer)
        val line = lineAt(state, 0)
        val oldFrame = state.frameGeneration
        val oldLine = line.renderGeneration

        buffer.setPenAttributes(
            fg = 1,
            bg = 0,
            bold = true,
            faint = false,
            italic = false,
            underlineStyle = UnderlineStyle.SINGLE,
        )

        assertAll(
            { assertEquals(oldFrame, state.frameGeneration) },
            { assertEquals(oldLine, line.renderGeneration) },
        )

        buffer.writeCodepoint('B'.code)

        assertAll(
            { assertNotEquals(oldFrame, state.frameGeneration) },
            { assertNotEquals(oldLine, line.renderGeneration) },
        )
    }

    @Test
    fun `erase changes the affected line generation`() {
        val buffer = TerminalBuffer(initialWidth = 4, initialHeight = 2)
        val state = stateOf(buffer)
        buffer.writeText("AB")
        val line = lineAt(state, 0)
        val oldFrame = state.frameGeneration
        val oldLine = line.renderGeneration

        buffer.positionCursor(0, 0)
        buffer.eraseCurrentLine()

        assertAll(
            { assertNotEquals(oldFrame, state.frameGeneration) },
            { assertNotEquals(oldLine, line.renderGeneration) },
        )
    }

    @Test
    fun `scroll changes structure generation`() {
        val buffer = TerminalBuffer(initialWidth = 4, initialHeight = 2)
        val state = stateOf(buffer)
        val oldStructure = state.structureGeneration

        buffer.positionCursor(0, 1)
        buffer.newLine()

        assertNotEquals(oldStructure, state.structureGeneration)
    }

    @Test
    fun `resize changes structure generation`() {
        val buffer = TerminalBuffer(initialWidth = 4, initialHeight = 2)
        val state = stateOf(buffer)
        val oldStructure = state.structureGeneration

        buffer.resize(5, 3)

        assertNotEquals(oldStructure, state.structureGeneration)
    }

    @Test
    fun `alternate screen switch changes structure generation`() {
        val buffer = TerminalBuffer(initialWidth = 4, initialHeight = 2)
        val state = stateOf(buffer)
        val oldStructure = state.structureGeneration

        buffer.enterAltBuffer()

        assertNotEquals(oldStructure, state.structureGeneration)
    }

    private fun stateOf(api: TerminalBufferApi): TerminalState {
        val componentsField = api.javaClass.getDeclaredField("components")
        componentsField.isAccessible = true
        val components = componentsField.get(api)

        val stateField = components.javaClass.getDeclaredField("state")
        stateField.isAccessible = true
        return stateField.get(components) as TerminalState
    }

    private fun lineAt(state: TerminalState, row: Int): Line {
        return state.ring[state.resolveRingIndex(row)]
    }
}
