package com.gagik.terminal.buffer.impl

import com.gagik.terminal.api.TerminalModeController
import com.gagik.terminal.engine.CursorEngine
import com.gagik.terminal.model.MouseEncodingMode
import com.gagik.terminal.model.MouseTrackingMode
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

	override fun setApplicationKeypad(enabled: Boolean) {
		state.modes.isApplicationKeypad = enabled
	}

	override fun setLeftRightMarginMode(enabled: Boolean) {
		if (state.modes.isLeftRightMarginMode == enabled) return
		state.modes.isLeftRightMarginMode = enabled
		state.activeBuffer.resetLeftRightMargins(state.dimensions.width)
		cursorEngine.homeCursor()
	}

	override fun setNewLineMode(enabled: Boolean) {
		state.modes.isNewLineMode = enabled
	}

	override fun setMouseTrackingMode(mode: MouseTrackingMode) {
		state.modes.mouseTrackingMode = mode
	}

	override fun setMouseEncodingMode(mode: MouseEncodingMode) {
		state.modes.mouseEncodingMode = mode
	}

	override fun setBracketedPasteEnabled(enabled: Boolean) {
		state.modes.isBracketedPasteEnabled = enabled
	}

	override fun setFocusReportingEnabled(enabled: Boolean) {
		state.modes.isFocusReportingEnabled = enabled
	}

	override fun setModifyOtherKeysMode(mode: Int) {
		state.modes.modifyOtherKeysMode = mode
	}

	override fun setReverseVideo(enabled: Boolean) {
		state.modes.isReverseVideo = enabled
	}

	override fun setCursorVisible(enabled: Boolean) {
		state.modes.isCursorVisible = enabled
	}

	override fun setCursorBlinking(enabled: Boolean) {
		state.modes.isCursorBlinking = enabled
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
