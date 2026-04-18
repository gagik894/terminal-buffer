package com.gagik.terminal.buffer.impl

import com.gagik.terminal.api.TerminalModeController
import com.gagik.terminal.engine.CursorEngine
import com.gagik.terminal.state.TerminalState

internal class TerminalModeControllerImpl(
	private val state: TerminalState,
	private val cursorEngine: CursorEngine
) : TerminalModeController {

	override fun setInsertMode(enabled: Boolean) {
		state.modes.isInsertMode = enabled
	}

	override fun setAutoWrap(enabled: Boolean) {
		state.modes.isAutoWrap = enabled
		if (!enabled) state.cancelPendingWrap()
	}

	override fun setOriginMode(enabled: Boolean) {
		state.modes.isOriginMode = enabled
		state.cursor.col = 0
		state.cursor.row = if (enabled) state.scrollTop else 0
		state.cancelPendingWrap()
	}

	override fun setApplicationCursorKeys(enabled: Boolean) {
		state.modes.isApplicationCursorKeys = enabled
	}

	override fun setTreatAmbiguousAsWide(enabled: Boolean) {
		state.modes.treatAmbiguousAsWide = enabled
	}

	override fun enterAltBuffer() {
		if (state.isAltScreenActive) return

		cursorEngine.saveCursor()
		state.enterAltScreen()
	}

	override fun exitAltBuffer() {
		if (!state.isAltScreenActive) return

		state.exitAltScreen()
		cursorEngine.restoreCursor()
	}
}