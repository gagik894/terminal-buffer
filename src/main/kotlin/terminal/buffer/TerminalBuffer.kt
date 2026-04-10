package com.gagik.terminal.buffer

import com.gagik.terminal.codec.AttributeCodec
import com.gagik.terminal.model.*
import com.gagik.terminal.util.Validations.requireNonNegative
import com.gagik.terminal.util.Validations.requirePositive

/**
 * Terminal buffer coordinator.
 * Components:
 * - Cursor: Position tracking with auto-wrap
 * - Pen: Attribute management
 * - Screen: Viewport over history ring
 * - HistoryRing: Scrollback storage
 *
 * @param initialWidth Width of the terminal in columns. Must be > 0.
 * @param initialHeight Height of the visible screen in rows. Must be > 0.
 * @param maxHistory Maximum number of scrollback lines. Must be >= 0.
 * @throws IllegalArgumentException if width, height, or maxHistory are invalid
 */
internal class TerminalBuffer(
    initialWidth: Int,
    initialHeight: Int,
    private val maxHistory: Int = 1000
) : TerminalBufferApi {

    init {
        requirePositive(initialWidth, "initialWidth")
        requirePositive(initialHeight, "initialHeight")
        requireNonNegative(maxHistory, "maxHistory")
    }

    private val dimensions = GridDimensions(initialWidth, initialHeight)
    private val ring = HistoryRing(maxHistory + dimensions.height) { Line(dimensions.width) }
    private val screen = Screen(ring, dimensions)
    private val cursor = Cursor(dimensions)
    private val pen = Pen()

    override val width: Int get() = dimensions.width
    override val height: Int get() = dimensions.height
    override val cursorCol: Int get() = cursor.col
    override val cursorRow: Int get() = cursor.row
    override val historySize: Int get() = ring.size - dimensions.height

    init {
        // Pre-populate ring so screen has lines to view
        initializeScreen()
    }


    override fun writeChar(value: Char) {
        writeSingleChar(value)
    }

    override fun writeText(text: String) {
        for (ch in text) {
            writeSingleChar(ch)
        }
    }

    override fun insertText(text: String) {
        for (ch in text) {
            insertSingleChar(ch)
        }
    }

    override fun newLine() {
        val nextRow = cursor.row + 1

        if (nextRow >= height) {
            screen.scrollUp(pen.currentAttr)
            cursor.set(0, height - 1)
        } else {
            cursor.set(0, nextRow)
        }
    }

    override fun carriageReturn() {
        cursor.set(0, cursor.row)
    }

    override fun setCursor(col: Int, row: Int) {
        cursor.set(col, row)
    }

    override fun moveCursor(dx: Int, dy: Int) {
        cursor.move(dx, dy)
    }

    override fun cursorUp(n: Int) {
        cursor.move(0, -n)
    }

    override fun cursorDown(n: Int) {
        cursor.move(0, n)
    }

    override fun cursorLeft(n: Int) {
        cursor.move(-n, 0)
    }

    override fun cursorRight(n: Int) {
        cursor.move(n, 0)
    }

    override fun resetCursor() {
        cursor.reset()
    }

    override fun setAttributes(attributes: Attributes) {
        pen.setAttributes(
            attributes.fg,
            attributes.bg,
            attributes.bold,
            attributes.italic,
            attributes.underline
        )
    }

    override fun resetPen() {
        pen.reset()
    }

    override fun scrollUp() {
        screen.scrollUp(pen.currentAttr)
    }

    override fun clearScreen() {
        screen.clear(pen.currentAttr)
    }

    override fun clearAll() {
        ring.clear()
        initializeScreen()
        cursor.reset()
    }

    override fun fillLine(value: Char?) {
        fillLineAt(cursor.row, value)
    }

    override fun fillLineAt(row: Int, value: Char?) {
        val codepoint = value?.code ?: 0
        screen.getLine(row).fill(codepoint, pen.currentAttr)
    }


    /**
     * Retrieves a line from scrollback history, or null if out of bounds.
     */
    private fun getHistoryLineOrNull(index: Int): Line? {
        if (index !in 0 until historySize) return null
        return ring[index]
    }

    private fun getHistoryLine(index: Int): Line {
        return getHistoryLineOrNull(index) ?: throw IllegalArgumentException("history index $index out of bounds (0..<$historySize)")
    }

    override fun getCharAt(col: Int, row: Int): Char? {
        return toCharOrNull(screen.getLineOrNull(row)?.getCodepointOrNull(col))
    }

    override fun getAttrAt(col: Int, row: Int): Attributes? {
        return screen.getLineOrNull(row)?.getAttrOrNull(col)?.let { AttributeCodec.unpack(it) }
    }

    override fun getHistoryAttrAt(index: Int, col: Int): Attributes? {
        return getHistoryLineOrNull(index)?.getAttrOrNull(col)?.let { AttributeCodec.unpack(it) }
    }

    override fun getHistoryCharAt(index: Int, col: Int): Char? {
        return toCharOrNull(getHistoryLineOrNull(index)?.getCodepointOrNull(col))
    }

    override fun getLineAsString(row: Int): String {
        return screen.getLine(row).toTextTrimmed()
    }

    override fun getHistoryLineAsString(index: Int): String {
        return getHistoryLine(index).toTextTrimmed()
    }

    override fun getScreenAsString(): String {
        return screen.toText()
    }

    override fun getAllAsString(): String {
        val sb = StringBuilder()

        // History lines
        for (i in 0 until historySize) {
            sb.append(ring[i].toTextTrimmed())
            sb.append('\n')
        }

        sb.append(screen.toText())

        return sb.toString()
    }

    override fun reset() {
        ring.clear()
        initializeScreen()
        cursor.reset()
        pen.reset()
    }

    // helpers

    private fun initializeScreen() {
        repeat(height) {
            ring.push().clear(pen.currentAttr)
        }
    }

    private fun writeSingleChar(value: Char) {
        screen.write(cursor.row, cursor.col, value.code, pen.currentAttr)
        advanceCursorAfterWrite()
    }

    private fun insertSingleChar(value: Char) {
        val line = screen.getLine(cursor.row)
        line.shiftRight(cursor.col, pen.currentAttr)
        line.setCell(cursor.col, value.code, pen.currentAttr)
        advanceCursorAfterWrite()
    }

    private fun toCharOrNull(codepoint: Int?): Char? {
        if (codepoint == null || codepoint == 0) return null
        if (codepoint > Char.MAX_VALUE.code) return null
        return codepoint.toChar()
    }

    private fun advanceCursorAfterWrite() {
        when (val result = cursor.advance()) {
            is AdvanceResult.Normal -> Unit
            is AdvanceResult.Wrapped -> screen.getLine(result.fromRow).wrapped = true
            is AdvanceResult.ScrollNeeded -> {
                screen.getLine(result.fromRow).wrapped = true
                screen.scrollUp(pen.currentAttr)
            }
        }
    }
}