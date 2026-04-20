package com.gagik.core.buffer.impl

import com.gagik.core.api.TerminalCursor
import com.gagik.core.engine.CursorEngine
import com.gagik.core.state.TerminalState

internal class TerminalCursorImpl(
    private val state: TerminalState,
    private val cursorEngine: CursorEngine
) : TerminalCursor {

	override fun positionCursor(col: Int, row: Int) = cursorEngine.setCursor(col, row)

	override fun cursorUp(n: Int) = cursorEngine.cursorUp(n)

	override fun cursorDown(n: Int) = cursorEngine.cursorDown(n)

	override fun cursorLeft(n: Int) = cursorEngine.cursorLeft(n)

	override fun cursorRight(n: Int) = cursorEngine.cursorRight(n)

	override fun saveCursor() = cursorEngine.saveCursor()

	override fun restoreCursor() = cursorEngine.restoreCursor()

	override fun resetCursor() = cursorEngine.setCursorAbsolute(0, 0)

	override fun setTabStop() {
		state.cancelPendingWrap()
		state.tabStops.setStop(state.cursor.col)
	}

	override fun clearTabStop() {
		state.cancelPendingWrap()
		state.tabStops.clearStop(state.cursor.col)
	}

	override fun clearAllTabStops() {
		state.cancelPendingWrap()
		state.tabStops.clearAll()
	}

	override fun horizontalTab() = cursorEngine.horizontalTab()

	override fun cursorForwardTab(count: Int) = cursorEngine.cursorForwardTab(count)

	override fun cursorBackwardTab(count: Int) = cursorEngine.cursorBackwardTab(count)
}
