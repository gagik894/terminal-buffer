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

    // Cursor Accessors
    /**
     * Current cursor column (0-based).
     */
    override val cursorCol: Int
        get() = cursor.col

    /**
     * Current cursor row (0-based).
     */
    override val cursorRow: Int
        get() = cursor.row

    // Writing Operations

    /**
     * Writes a single character at the current cursor position.
     * Uses current pen attributes and advances cursor with auto-wrap.
     *
     * @param value Character to write
     */
    override fun writeChar(value: Char) {
        writeSingleChar(value)
    }

    /**
     * Writes a string at the current cursor position.
     * Each character is written sequentially with auto-wrap.
     *
     * @param text Text to write
     */
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

    /**
     * Inserts a line break, moving cursor to the beginning of the next line.
     * If at the bottom of the screen, scrolls up.
     */
    override fun newLine() {
        val nextRow = cursor.row + 1

        if (nextRow >= height) {
            screen.scrollUp(pen.currentAttr)
            cursor.set(0, height - 1)
        } else {
            cursor.set(0, nextRow)
        }
    }

    /**
     * Moves cursor to the beginning of the current line.
     */
    override fun carriageReturn() {
        cursor.set(0, cursor.row)
    }

    // Cursor Operations

    /**
     * Sets cursor to an absolute position.
     * Position is clamped to screen bounds.
     *
     * @param col Target column (0-based)
     * @param row Target row (0-based)
     */
    override fun setCursor(col: Int, row: Int) {
        cursor.set(col, row)
    }

    /**
     * Moves cursor relatively.
     * Movement is clamped to screen bounds.
     *
     * @param dx Column delta (positive = right, negative = left)
     * @param dy Row delta (positive = down, negative = up)
     */
    override fun moveCursor(dx: Int, dy: Int) {
        cursor.move(dx, dy)
    }

    /**
     * Moves cursor up by N rows.
     * @param n Number of rows to move (default 1)
     */
    override fun cursorUp(n: Int) {
        cursor.move(0, -n)
    }

    /**
     * Moves cursor down by N rows.
     * @param n Number of rows to move (default 1)
     */
    override fun cursorDown(n: Int) {
        cursor.move(0, n)
    }

    /**
     * Moves cursor left by N columns.
     * @param n Number of columns to move (default 1)
     */
    override fun cursorLeft(n: Int) {
        cursor.move(-n, 0)
    }

    /**
     * Moves cursor right by N columns.
     * @param n Number of columns to move (default 1)
     */
    override fun cursorRight(n: Int) {
        cursor.move(n, 0)
    }

    /**
     * Resets cursor to origin (0, 0).
     */
    override fun resetCursor() {
        cursor.reset()
    }

    // Pen Operations

    /**
     * Sets pen attributes for subsequent write operations.
     *
     * @param attributes Attributes to set on the pen
     */
    override fun setAttributes(attributes: Attributes) {
        pen.setAttributes(
            attributes.fg,
            attributes.bg,
            attributes.bold,
            attributes.italic,
            attributes.underline
        )
    }

    /**
     * Resets pen to default attributes.
     */
    override fun resetPen() {
        pen.reset()
    }

    // Screen Operations

    /**
     * Scrolls the screen up by one line.
     * Top line moves to history, new blank line appears at bottom.
     */
    override fun scrollUp() {
        screen.scrollUp(pen.currentAttr)
    }

    /**
     * Clears the entire visible screen.
     * History is not affected.
     */
    override fun clearScreen() {
        screen.clear(pen.currentAttr)
    }

    override fun clearAll() {
        ring.clear()
        initializeScreen()
        cursor.reset()
    }

    /**
     * Fills the current line with a character using current attributes.
     * Cursor position is not affected.
     *
     * @param value Character to fill with (null clears to empty)
     */
    override fun fillLine(value: Char?) {
        val codepoint = value?.code ?: 0
        screen.getLine(cursor.row).fill(codepoint, pen.currentAttr)
    }

    /**
     * Fills a specific line with a character using current attributes.
     *
     * @param row Screen row (0-based)
     * @param value Character to fill with (null clears to empty)
     */
    override fun fillLineAt(row: Int, value: Char?) {
        val codepoint = value?.code ?: 0
        screen.getLine(row).fill(codepoint, pen.currentAttr)
    }

    // Query Operations

    /**
     * Number of lines currently in scrollback history.
     */
    override val historySize: Int
        get() = (ring.size - height).coerceAtLeast(0)

    /**
     * Gets a line from scrollback history.
     */
    private fun getHistoryLine(index: Int): Line {
        require(index in 0 until historySize) {
            "history index $index out of bounds (0..<$historySize)"
        }
        return ring[index]
    }

    /**
     * Gets the character at a screen position.
     *
     * @param col Column (0-based)
     * @param row Row (0-based)
     * @return Unicode codepoint, or null if out of bounds
     */
    override fun getCharAt(col: Int, row: Int): Char? {
        val cp = try {
            screen.getLine(row).getCodepoint(col)
        } catch (_: IllegalArgumentException) {
            null
        }
        return toCharOrNull(cp)
    }

    /**
     * Gets the attributes at a screen position.
     *
     * @param col Column (0-based)
     * @param row Row (0-based)
     * @return Packed attribute value, or null if out of bounds
     */
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


    /**
     * Gets a screen line as a string.
     *
     * @param row Screen row (0-based)
     * @return String content of the line (trimmed)
     * @throws IllegalArgumentException if row is out of bounds
     */
    override fun getLineAsString(row: Int): String {
        return screen.getLine(row).toTextTrimmed()
    }

    /**
     * Gets a history line as a string.
     *
     * @param index History index (0 = oldest)
     * @return String content of the line (trimmed)
     * @throws IllegalArgumentException if index is out of bounds
     */
    override fun getHistoryLineAsString(index: Int): String {
        return getHistoryLine(index).toTextTrimmed()
    }

    /**
     * Gets the entire visible screen content as a string.
     * Lines are separated by newlines. Trailing spaces on each line are trimmed.
     *
     * @return String representation of the visible screen
     */
    override fun getScreenAsString(): String {
        return screen.toText()
    }

    /**
     * Gets all content (scrollback + screen) as a string.
     * Lines are separated by newlines. Trailing spaces on each line are trimmed.
     *
     * @return String representation of all content
     */
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

    // Reset

    /**
     * Completely resets the terminal to initial state:
     * - Clears screen and history
     * - Resets cursor to origin
     * - Resets pen to defaults
     */
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