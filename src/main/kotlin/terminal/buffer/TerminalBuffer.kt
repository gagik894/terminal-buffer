package com.gagik.terminal.buffer

import com.gagik.terminal.model.AdvanceResult
import com.gagik.terminal.model.Cursor
import com.gagik.terminal.model.Line
import com.gagik.terminal.model.Pen
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
class TerminalBuffer(
    width: Int,
    height: Int,
    private val maxHistory: Int = 1000
) {
    init {
        requirePositive(width, "width")
        requirePositive(height, "height")
        requireNonNegative(maxHistory, "maxHistory")
    }

    /** Current width of the terminal in columns */
    var width: Int = width
        private set

    /** Current height of the terminal in rows */
    var height: Int = height
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
    val cursorCol: Int
        get() = cursor.col

    /**
     * Current cursor row (0-based).
     */
    val cursorRow: Int
        get() = cursor.row

    // Writing Operations

    /**
     * Writes a single character at the current cursor position.
     * Uses current pen attributes and advances cursor with auto-wrap.
     *
     * @param codepoint Unicode codepoint to write
     */
    fun writeChar(codepoint: Int) {
        screen.write(cursor.row, cursor.col, codepoint, pen.currentAttr)

        when (val result = cursor.advance()) {
            is AdvanceResult.Normal -> {
                // Nothing special needed
            }
            is AdvanceResult.Wrapped -> {
                // Mark the line we just left as wrapped
                screen.getLine(result.fromRow).wrapped = true
            }
            is AdvanceResult.ScrollNeeded -> {
                // Mark line as wrapped, then scroll
                screen.getLine(result.fromRow).wrapped = true
                screen.scrollUp(pen.currentAttr)
            }
        }
    }

    /**
     * Writes a string at the current cursor position.
     * Each character is written sequentially with auto-wrap.
     *
     * @param text Text to write
     */
    fun writeText(text: String) {
        text.forEach { char ->
            writeChar(char.code)
        }
    }

    /**
     * Inserts a line break, moving cursor to the beginning of the next line.
     * If at the bottom of the screen, scrolls up.
     */
    fun newLine() {
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
    fun carriageReturn() {
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
    fun setCursor(col: Int, row: Int) {
        cursor.set(col, row)
    }

    /**
     * Moves cursor relatively.
     * Movement is clamped to screen bounds.
     *
     * @param dx Column delta (positive = right, negative = left)
     * @param dy Row delta (positive = down, negative = up)
     */
    fun moveCursor(dx: Int, dy: Int) {
        cursor.move(dx, dy)
    }

    /**
     * Moves cursor up by N rows.
     * @param n Number of rows to move (default 1)
     */
    fun cursorUp(n: Int = 1) {
        cursor.move(0, -n)
    }

    /**
     * Moves cursor down by N rows.
     * @param n Number of rows to move (default 1)
     */
    fun cursorDown(n: Int = 1) {
        cursor.move(0, n)
    }

    /**
     * Moves cursor left by N columns.
     * @param n Number of columns to move (default 1)
     */
    fun cursorLeft(n: Int = 1) {
        cursor.move(-n, 0)
    }

    /**
     * Moves cursor right by N columns.
     * @param n Number of columns to move (default 1)
     */
    fun cursorRight(n: Int = 1) {
        cursor.move(n, 0)
    }

    /**
     * Resets cursor to origin (0, 0).
     */
    fun resetCursor() {
        cursor.reset()
    }

    // Pen Operations

    /**
     * Sets pen attributes for subsequent write operations.
     *
     * @param fg Foreground color (0 = default, 1-16 = ANSI colors)
     * @param bg Background color (0 = default, 1-16 = ANSI colors)
     * @param bold Bold style
     * @param italic Italic style
     * @param underline Underline style
     */
    fun setAttributes(
        fg: Int,
        bg: Int,
        bold: Boolean = false,
        italic: Boolean = false,
        underline: Boolean = false
    ) {
        pen.setAttributes(fg, bg, bold, italic, underline)
    }

    /**
     * Resets pen to default attributes.
     */
    fun resetPen() {
        pen.reset()
    }

    // Screen Operations

    /**
     * Scrolls the screen up by one line.
     * Top line moves to history, new blank line appears at bottom.
     */
    fun scrollUp() {
        screen.scrollUp(pen.currentAttr)
    }

    /**
     * Clears the entire visible screen.
     * History is not affected.
     */
    fun clearScreen() {
        screen.clear(pen.currentAttr)
    }

    /**
     * Clears the line where the cursor is currently located.
     */
    fun clearLine() {
        screen.getLine(cursor.row).clear(pen.currentAttr)
    }

    /**
     * Clears from cursor position to end of the current line.
     */
    fun clearToEndOfLine() {
        screen.getLine(cursor.row).clearFromColumn(cursor.col, pen.currentAttr)
    }

    /**
     * Clears from beginning of current line to cursor position.
     */
    fun clearToBeginningOfLine() {
        screen.getLine(cursor.row).clearToColumn(cursor.col, pen.currentAttr)
    }

    /**
     * Clears from cursor position to end of screen.
     */
    fun clearToEndOfScreen() {
        screen.clearFromPosition(cursor.row, cursor.col, pen.currentAttr)
    }

    /**
     * Clears from beginning of screen to cursor position.
     */
    fun clearToBeginningOfScreen() {
        screen.clearToPosition(cursor.row, cursor.col, pen.currentAttr)
    }

    // Query Operations

    /**
     * Gets the Line at the specified screen row.
     *
     * @param row Screen row (0-based, 0 = top)
     * @return Line at that row
     * @throws IllegalArgumentException if row is out of bounds
     */
    fun getLine(row: Int): Line = screen.getLine(row)

    /**
     * Number of lines currently in scrollback history.
     */
    val historySize: Int
        get() = (ring.size - height).coerceAtLeast(0)

    /**
     * Gets a line from scrollback history.
     *
     * @param index History index (0 = oldest in history)
     * @return Line at that history index
     * @throws IllegalArgumentException if index is out of bounds
     */
    fun getHistoryLine(index: Int): Line {
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
    fun getCharAt(col: Int, row: Int): Int? {
        return try {
            screen.getLine(row).getCodepoint(col)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    /**
     * Gets the attributes at a screen position.
     *
     * @param col Column (0-based)
     * @param row Row (0-based)
     * @return Packed attribute value, or null if out of bounds
     */
    fun getAttrAt(col: Int, row: Int): Int? {
        return try {
            screen.getLine(row).getAttr(col)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    // Reset

    /**
     * Completely resets the terminal to initial state:
     * - Clears screen and history
     * - Resets cursor to origin
     * - Resets pen to defaults
     */
    fun reset() {
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
}