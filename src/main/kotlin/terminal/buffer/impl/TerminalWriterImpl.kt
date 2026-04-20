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

    override fun writeCluster(codepoints: IntArray, length: Int, charWidth: Int) {
        require(length in 1..codepoints.size) { "length must be in 1..${codepoints.size}, was $length" }
        require(charWidth == 1 || charWidth == 2) { "charWidth must be 1 or 2, was $charWidth" }

        if (length == 1) {
            mutationEngine.printCodepoint(codepoints[0], charWidth)
        } else {
            mutationEngine.printCluster(codepoints, length, charWidth)
        }
    }

    override fun newLine() = mutationEngine.newLine()

    override fun reverseLineFeed() = mutationEngine.reverseLineFeed()

    override fun carriageReturn() = cursorEngine.carriageReturn()

    override fun setScrollRegion(top: Int, bottom: Int) {
        state.activeBuffer.setScrollRegion(
            top,
            bottom,
            state.modes.isOriginMode,
            state.dimensions.height,
            state.effectiveLeftMargin
        )
    }

    override fun setLeftRightMargins(left: Int, right: Int) {
        if (!state.modes.isLeftRightMarginMode) return
        if (state.activeBuffer.setLeftRightMargins(left, right, state.dimensions.width)) {
            cursorEngine.homeCursor()
        }
    }

    override fun resetScrollRegion() {
        state.activeBuffer.resetScrollRegion(state.dimensions.height)
        cursorEngine.homeCursor()
    }

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
        state.tabStops.resetToDefault()
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
