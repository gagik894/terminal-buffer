package com.gagik.terminal.buffer

import com.gagik.terminal.codec.AttributeCodec
import com.gagik.terminal.engine.MutationEngine
import com.gagik.terminal.engine.TerminalResizer
import com.gagik.terminal.model.Attributes
import com.gagik.terminal.model.Line
import com.gagik.terminal.model.TerminalConstants
import com.gagik.terminal.model.VoidLine
import com.gagik.terminal.state.TerminalState
import com.gagik.terminal.util.UnicodeWidth

/**
 * The primary entry point and public API for the terminal emulator.
 * Implements [TerminalBufferApi] to provide a strict, zero-allocation contract.
 * * Architecturally, this class contains NO physics and NO memory.
 * It coordinates the [MutationEngine] and reads from the [TerminalState].
 */
internal class TerminalBuffer(
    initialWidth: Int,
    initialHeight: Int,
    maxHistory: Int = 1000
) : TerminalBufferApi {

    internal val state = TerminalState(initialWidth, initialHeight, maxHistory)
    private val mutationEngine = MutationEngine(state)


    // --- Viewport Math Helpers ---

    private fun getVisibleLine(row: Int): Line? {
        if (!state.dimensions.isValidRow(row)) return null
        return state.ring[state.resolveRingIndex(row)]
    }

    // --- Public Properties ---

    override val width: Int get() = state.dimensions.width
    override val height: Int get() = state.dimensions.height
    override val cursorCol: Int get() = state.cursor.col
    override val cursorRow: Int get() = state.cursor.row
    override val historySize: Int
        get() = (state.ring.size - height).coerceAtLeast(0)

    // --- Styling API ---

    override fun setPenAttributes(fg: Int, bg: Int, bold: Boolean, italic: Boolean, underline: Boolean) {
        state.pen.setAttributes(fg, bg, bold, italic, underline)
    }

    override fun resetPen() {
        state.pen.reset()
    }

    // --- Cursor API ---

    override fun setCursor(col: Int, row: Int) {
        state.cursor.col = state.dimensions.clampCol(col)
        state.cursor.row = state.dimensions.clampRow(row)
    }

    override fun moveCursor(dx: Int, dy: Int) {
        setCursor(cursorCol + dx, cursorRow + dy)
    }

    override fun cursorUp(n: Int) = moveCursor(0, -n)
    override fun cursorDown(n: Int) = moveCursor(0, n)
    override fun cursorLeft(n: Int) = moveCursor(-n, 0)
    override fun cursorRight(n: Int) = moveCursor(n, 0)

    override fun resetCursor() {
        setCursor(0, 0)
    }

    override fun resize(newWidth: Int, newHeight: Int) {
        require(newWidth > 0) { "newWidth must be > 0, was $newWidth" }
        require(newHeight > 0) { "newHeight must be > 0, was $newHeight" }

        TerminalResizer.resize(state, newWidth, newHeight)
    }

    // --- Writing API ---

    override fun writeCodepoint(codepoint: Int) {
        val charWidth = UnicodeWidth.calculate(codepoint, state.treatAmbiguousAsWide)
        mutationEngine.printCodepoint(codepoint, charWidth)
    }

    override fun writeText(text: String) {
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val charWidth = UnicodeWidth.calculate(cp, state.treatAmbiguousAsWide)
            mutationEngine.printCodepoint(cp, charWidth)
            i += Character.charCount(cp)
        }
    }

    override fun insertBlankCharacters(count: Int) {
        mutationEngine.insertBlankCharacters(count)
    }

    override fun newLine() = mutationEngine.newLine()

    override fun carriageReturn() {
        state.cursor.col = 0
    }

    // --- Viewport API ---

    override fun scrollUp() = mutationEngine.scrollUp()


    override fun clearScreen() {
        mutationEngine.clearViewport()
        resetCursor()
    }

    override fun clearAll() {
        resetPen()
        mutationEngine.clearAllHistory()
        resetCursor()
    }

    override fun eraseLineToEnd() = mutationEngine.eraseLineToEnd()
    override fun eraseLineToCursor() = mutationEngine.eraseLineToCursor()
    override fun eraseCurrentLine() = mutationEngine.eraseCurrentLine()

    // --- Rendering API (Zero Allocation - Critical Path) ---

    override fun getLine(row: Int): TerminalLineApi = getVisibleLine(row) ?: VoidLine

    override fun getCodepointAt(col: Int, row: Int): Int {
        if (!state.dimensions.isValidCol(col)) return TerminalConstants.EMPTY
        val line = getVisibleLine(row) ?: return TerminalConstants.EMPTY
        return line.getCodepoint(col)
    }

    override fun getPackedAttrAt(col: Int, row: Int): Int {
        if (!state.dimensions.isValidCol(col)) return state.pen.currentAttr
        val line = getVisibleLine(row) ?: return state.pen.currentAttr
        return line.getPackedAttr(col)
    }

    // --- Testing & Debugging API ---

    override fun getAttrAt(col: Int, row: Int): Attributes? {
        if (!state.dimensions.isValidCol(col) || !state.dimensions.isValidRow(row)) return null
        return AttributeCodec.unpack(getPackedAttrAt(col, row))
    }

    override fun getLineAsString(row: Int): String {
        return getVisibleLine(row)?.toTextTrimmed() ?: ""
    }

    override fun getScreenAsString(): String = buildString {
        for (i in 0 until height) {
            if (i > 0) append('\n')
            append(getLineAsString(i))
        }
    }

    override fun getAllAsString(): String {
        val sb = StringBuilder()
        for (i in 0 until state.ring.size) {
            if (i > 0) sb.append('\n')
            sb.append(state.ring[i].toTextTrimmed())
        }
        return sb.toString()
    }

    override fun reset() {
        // clearAll already resets pen + cursor and rebuilds a blank viewport.
        clearAll()
    }
}