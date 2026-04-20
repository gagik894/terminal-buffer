package com.gagik.terminal.buffer

import com.gagik.terminal.TerminalBuffers
import com.gagik.terminal.api.TerminalBufferApi
import com.gagik.terminal.model.Attributes
import com.gagik.terminal.state.TerminalState
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("TerminalBuffer Integration Test Suite")
class TerminalBufferTest {

	private fun stateOf(api: TerminalBufferApi): TerminalState {
		val componentsField = api.javaClass.getDeclaredField("components")
		componentsField.isAccessible = true
		val components = componentsField.get(api)

		val stateField = components.javaClass.getDeclaredField("state")
		stateField.isAccessible = true
		return stateField.get(components) as TerminalState
	}

	private fun newBuffer(width: Int = 4, height: Int = 3, maxHistory: Int = 5): TerminalBuffer {
		return TerminalBuffer(width, height, maxHistory)
	}

	private fun newApiBuffer(width: Int = 4, height: Int = 3, maxHistory: Int = 5): TerminalBufferApi {
		return TerminalBuffers.create(width, height, maxHistory)
	}

	private fun blankScreen(height: Int): String = List(height) { "" }.joinToString("\n")

	@Test
	fun `constructs a blank buffer and factory returns a working api surface`() {
		val buffer = newBuffer(width = 3, height = 2, maxHistory = 4)
		val api = newApiBuffer(width = 3, height = 2, maxHistory = 4)

		assertAll(
			{ assertEquals(3, buffer.width) },
			{ assertEquals(2, buffer.height) },
			{ assertEquals(0, buffer.cursorCol) },
			{ assertEquals(0, buffer.cursorRow) },
			{ assertEquals(0, buffer.historySize) },
			{ assertEquals(blankScreen(2), buffer.getScreenAsString()) },
			{ assertEquals(blankScreen(2), buffer.getAllAsString()) },
			{ assertEquals(3, api.width) },
			{ assertEquals(2, api.height) },
			{ assertEquals(blankScreen(2), api.getScreenAsString()) }
		)
	}

	@Test
	fun `constructor and factory reject invalid dimensions`() {
		assertThrows<IllegalArgumentException> { TerminalBuffer(0, 1) }
		assertThrows<IllegalArgumentException> { TerminalBuffers.create(1, 0) }
	}

	@Test
	fun `saveCursor and restoreCursor round-trip cursor and pen state through the facade`() {
		val buffer = newApiBuffer(width = 4, height = 3)
		buffer.positionCursor(2, 1)
		buffer.setPenAttributes(3, 7, bold = true, italic = true, underline = false)

		buffer.saveCursor()
		buffer.positionCursor(0, 0)
		buffer.setPenAttributes(1, 2, bold = false, italic = false, underline = true)
		buffer.restoreCursor()

		assertAll(
			{ assertEquals(2, buffer.cursorCol) },
			{ assertEquals(1, buffer.cursorRow) }
		)

		buffer.writeCodepoint('X'.code)

		assertAll(
			{ assertEquals(3, buffer.cursorCol) },
			{ assertEquals(1, buffer.cursorRow) },
			{ assertEquals('X'.code, buffer.getCodepointAt(2, 1)) },
			{ assertEquals(Attributes(3, 7, bold = true, italic = true, underline = false), buffer.getAttrAt(2, 1)) }
		)
	}

	@Test
	fun `resize and reset remain coordinated through the TerminalBuffer facade`() {
		val buffer = newApiBuffer(width = 4, height = 2, maxHistory = 3)
		buffer.writeText("ABCD")
		buffer.resize(newWidth = 2, newHeight = 3)

		assertAll(
			{ assertEquals(2, buffer.width) },
			{ assertEquals(3, buffer.height) },
			{ assertEquals("AB\nCD\n", buffer.getScreenAsString()) },
			{ assertEquals(true, buffer.cursorCol in 0 until buffer.width) },
			{ assertEquals(true, buffer.cursorRow in 0 until buffer.height) }
		)

		buffer.reset()

		assertAll(
			{ assertEquals(2, buffer.width) },
			{ assertEquals(3, buffer.height) },
			{ assertEquals(blankScreen(3), buffer.getScreenAsString()) },
			{ assertEquals(blankScreen(3), buffer.getAllAsString()) },
			{ assertEquals(0, buffer.cursorCol) },
			{ assertEquals(0, buffer.cursorRow) }
		)
	}

	@Test
	fun `reset exits alt buffer and restores current core mode defaults`() {
		val buffer = newApiBuffer(width = 5, height = 3, maxHistory = 2)
		buffer.setInsertMode(true)
		buffer.setAutoWrap(false)
		buffer.setOriginMode(true)
		buffer.setApplicationCursorKeys(true)
		buffer.setTreatAmbiguousAsWide(true)
		buffer.enterAltBuffer()

		buffer.reset()

		val state = stateOf(buffer)
		assertAll(
			{ assertFalse(state.isAltScreenActive) },
			{ assertFalse(state.modes.isInsertMode) },
			{ assertTrue(state.modes.isAutoWrap) },
			{ assertFalse(state.modes.isOriginMode) },
			{ assertFalse(state.modes.isApplicationCursorKeys) },
			{ assertFalse(state.modes.treatAmbiguousAsWide) },
			{ assertEquals(0, buffer.cursorCol) },
			{ assertEquals(0, buffer.cursorRow) }
		)
	}
}
