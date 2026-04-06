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
 * @param width Width of the terminal in columns. Must be > 0.
 * @param height Height of the visible screen in rows. Must be > 0.
 * @param maxHistory Maximum number of scrollback lines. Must be >= 0.
 * @throws IllegalArgumentException if width, height, or maxHistory are invalid
 */
internal class TerminalBuffer(
    width: Int,
    height: Int,
    private val maxHistory: Int = 1000
) : TerminalBufferApi {
    init {
        requirePositive(width, "width")
        requirePositive(height, "height")
        requireNonNegative(maxHistory, "maxHistory")
    }

    /** Current width of the terminal in columns */
    override var width: Int = width
        private set

    /** Current height of the terminal in rows */
    override var height: Int = height
        private set

    private val cursor = Cursor(width, height)
    private val pen = Pen()
    private val ring = HistoryRing(maxHistory + height) { Line(width) }
    private val screen = Screen(ring, height, width)

    init {
        // Pre-populate ring so screen has lines to view
        initializeScreen()
    }

    override val cursorCol: Int
        get() = cursor.col

    override val cursorRow: Int
        get() = cursor.row

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
        val codepoint = value?.code ?: 0
        screen.getLine(cursor.row).fill(codepoint, pen.currentAttr)
    }

    override fun fillLineAt(row: Int, value: Char?) {
        val codepoint = value?.code ?: 0
        screen.getLine(row).fill(codepoint, pen.currentAttr)
    }

    override val historySize: Int
        get() = (ring.size - height).coerceAtLeast(0)

    /**
     * Retrieves a line from scrollback history.
     * Implementation validates index bounds.
     */
    private fun getHistoryLine(index: Int): Line {
        require(index in 0 until historySize) {
            "history index $index out of bounds (0..<$historySize)"
        }
        return ring[index]
    }

    override fun getCharAt(col: Int, row: Int): Char? {
        val cp = try {
            screen.getLine(row).getCodepoint(col)
        } catch (_: IllegalArgumentException) {
            null
        }
        return toCharOrNull(cp)
    }

    override fun getAttrAt(col: Int, row: Int): Attributes? {
        val packed = try {
            screen.getLine(row).getAttr(col)
        } catch (_: IllegalArgumentException) {
            null
        }
        return packed?.let { AttributeCodec.unpack(it) }
    }

    override fun getHistoryAttrAt(index: Int, col: Int): Attributes? {
        val packed = try {
            getHistoryLine(index).getAttr(col)
        } catch (_: IllegalArgumentException) {
            null
        }
        return packed?.let { AttributeCodec.unpack(it) }
    }

    override fun getHistoryCharAt(index: Int, col: Int): Char? {
        val cp = try {
            getHistoryLine(index).getCodepoint(col)
        } catch (_: IllegalArgumentException) {
            null
        }
        return toCharOrNull(cp)
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

        // Screen lines
        for (row in 0 until height) {
            sb.append(screen.getLine(row).toTextTrimmed())
            if (row < height - 1) sb.append('\n')
        }

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
        shiftLineRight(line, cursor.col)
        line.setCell(cursor.col, value.code, pen.currentAttr)
        advanceCursorAfterWrite()
    }

    private fun shiftLineRight(line: Line, startCol: Int) {
        if (startCol !in 0 until width) return
        val end = width - 1
        var col = end
        while (col - 1 >= startCol) {
            line.codepoints[col] = line.codepoints[col - 1]
            line.attrs[col] = line.attrs[col - 1]
            col--
        }
        line.codepoints[startCol] = 0
        line.attrs[startCol] = pen.currentAttr
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