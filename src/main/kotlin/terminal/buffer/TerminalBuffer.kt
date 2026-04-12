package com.gagik.terminal.buffer

import com.gagik.terminal.codec.AttributeCodec
import com.gagik.terminal.engine.InputHandler
import com.gagik.terminal.model.Attributes
import com.gagik.terminal.model.Line
import com.gagik.terminal.state.TerminalState

/**
 * The primary entry point and public API for the terminal emulator.
 * Implements [TerminalBufferApi] to provide a strict, zero-allocation contract.
 * * Architecturally, this class contains NO physics and NO memory.
 * It coordinates the [InputHandler] and reads from the [TerminalState].
 */
internal class TerminalBuffer(
    initialWidth: Int,
    initialHeight: Int,
    maxHistory: Int = 1000
) : TerminalBufferApi {

    internal val state = TerminalState(initialWidth, initialHeight, maxHistory)

    private val inputHandler = InputHandler(state)


    // --- Viewport Math Helpers ---

    /** Safely retrieves the active line at the cursor. */
    private fun getActiveLine(): Line {
        val startIndex = state.ring.size - height
        return state.ring[startIndex + cursorRow]
    }

    private fun getVisibleLine(row: Int): Line? {
        if (!state.dimensions.isValidRow(row)) return null

        val startIndex = state.ring.size - height
        return state.ring[startIndex + row]
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

    // --- Writing API ---

    override fun writeCodepoint(codepoint: Int) {
        inputHandler.print(codepoint)
    }

    override fun writeText(text: String) {
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            inputHandler.print(cp)
            i += Character.charCount(cp)
        }
    }

    override fun insertBlankCharacters(count: Int) {
        getActiveLine().insertCells(cursorCol, count, state.pen.currentAttr)
    }

    override fun newLine() {
        inputHandler.newLine()
    }

    override fun carriageReturn() {
        inputHandler.carriageReturn()
    }

    // --- Viewport API ---

    override fun scrollUp() {
        val newLine = state.ring.push()
        newLine.clear(state.pen.currentAttr)
    }

    override fun clearScreen() {
        val lineCount = height.coerceAtMost(state.ring.size)
        val startIndex = (state.ring.size - height).coerceAtLeast(0)

        for (i in 0 until lineCount) {
            state.ring[startIndex + i].clear(state.pen.currentAttr)
        }
        resetCursor()
    }

    override fun clearAll() {
        state.ring.clear()
        resetPen()
        repeat(height) {
            state.ring.push().clear(state.pen.currentAttr)
        }
        resetCursor()
    }

    override fun eraseLineToEnd() {
        getActiveLine().clearFromColumn(cursorCol, state.pen.currentAttr)
    }

    override fun eraseLineToCursor() {
        getActiveLine().clearToColumn(cursorCol, state.pen.currentAttr)
    }

    override fun eraseCurrentLine() {
        getActiveLine().clear(state.pen.currentAttr)
    }

    // --- Rendering API (Zero Allocation - Critical Path) ---

    override fun getCodepointAt(col: Int, row: Int): Int {
        if (!state.dimensions.isValidCol(col)) return 0
        val line = getVisibleLine(row) ?: return 0
        return line.getCodepoint(col)
    }

    override fun getPackedAttrAt(col: Int, row: Int): Int {
        if (!state.dimensions.isValidCol(col)) return state.pen.currentAttr
        val line = getVisibleLine(row) ?: return state.pen.currentAttr
        return line.getAttr(col)
    }

    // --- Testing & Debugging API ---

    override fun getAttrAt(col: Int, row: Int): Attributes? {
        if (!state.dimensions.isValidCol(col) || !state.dimensions.isValidRow(row)) return null
        return AttributeCodec.unpack(getPackedAttrAt(col, row))
    }

    override fun getLineAsString(row: Int): String {
        return getVisibleLine(row)?.toTextTrimmed() ?: ""
    }

    override fun getScreenAsString(): String {
        val sb = StringBuilder()
        val lineCount = height.coerceAtMost(state.ring.size)
        for (i in 0 until lineCount) {
            if (i > 0) sb.append('\n')
            sb.append(getLineAsString(i))
        }
        return sb.toString()
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
        clearAll()
        resetPen()
        resetCursor()
    }
}