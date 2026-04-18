package com.gagik.terminal.buffer.impl

import com.gagik.terminal.api.TerminalWriter
import com.gagik.terminal.engine.CursorEngine
import com.gagik.terminal.engine.MutationEngine
import com.gagik.terminal.state.TerminalState
import com.gagik.terminal.util.UnicodeWidth

internal class TerminalWriterImpl(
	private val state: TerminalState,
	private val mutationEngine: MutationEngine,
	private val cursorEngine: CursorEngine
) : TerminalWriter {

	override fun writeCodepoint(codepoint: Int) {
		val charWidth = UnicodeWidth.calculate(codepoint, state.modes.treatAmbiguousAsWide)
		mutationEngine.printCodepoint(codepoint, charWidth)
	}

	override fun writeText(text: String) {
		var i = 0
		while (i < text.length) {
			val cp = text.codePointAt(i)
			val charWidth = UnicodeWidth.calculate(cp, state.modes.treatAmbiguousAsWide)
			mutationEngine.printCodepoint(cp, charWidth)
			i += Character.charCount(cp)
		}
	}

	override fun newLine() = mutationEngine.newLine()

	override fun reverseLineFeed() = mutationEngine.reverseLineFeed()

	override fun carriageReturn() = cursorEngine.carriageReturn()

	override fun setScrollRegion(top: Int, bottom: Int) = state.setScrollRegion(top, bottom)

	override fun resetScrollRegion() = state.resetScrollRegion()

	override fun scrollUp() = mutationEngine.scrollUp()

	override fun scrollDown() = mutationEngine.scrollDown()

	override fun insertLines(count: Int) = mutationEngine.insertLines(count)

	override fun deleteLines(count: Int) = mutationEngine.deleteLines(count)

	override fun insertBlankCharacters(count: Int) = mutationEngine.insertBlankCharacters(count)

	override fun deleteCharacters(count: Int) = mutationEngine.deleteCharacters(count)

	override fun eraseLineToEnd() = mutationEngine.eraseLineToEnd()

	override fun eraseLineToCursor() = mutationEngine.eraseLineToCursor()

	override fun eraseCurrentLine() = mutationEngine.eraseCurrentLine()

	override fun eraseScreenToEnd() = mutationEngine.eraseScreenToEnd()

	override fun eraseScreenToCursor() = mutationEngine.eraseScreenToCursor()

	override fun eraseEntireScreen() = mutationEngine.clearViewport()

	override fun eraseScreenAndHistory() = mutationEngine.eraseScreenAndHistory()

	override fun clearScreen() {
		mutationEngine.clearViewport()
		cursorEngine.setCursorAbsolute(0, 0)
	}

	override fun clearAll() {
		resetPen()
		mutationEngine.clearAllHistory()
		cursorEngine.setCursorAbsolute(0, 0)
		state.savedCursor.clear()
	}

	override fun setPenAttributes(
		fg: Int,
		bg: Int,
		bold: Boolean,
		italic: Boolean,
		underline: Boolean
	) {
		state.pen.setAttributes(fg, bg, bold, italic, underline)
	}

	override fun resetPen() {
		state.pen.reset()
	}
}