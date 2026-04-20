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
		cursorEngine.homeCursor()
	}

	override fun setApplicationCursorKeys(enabled: Boolean) {
		state.modes.isApplicationCursorKeys = enabled
	}

	override fun setLeftRightMarginMode(enabled: Boolean) {
		if (state.modes.isLeftRightMarginMode == enabled) return
		state.modes.isLeftRightMarginMode = enabled
		state.activeBuffer.resetLeftRightMargins(state.dimensions.width)
		cursorEngine.homeCursor()
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
