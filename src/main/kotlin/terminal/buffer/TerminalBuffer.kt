package com.gagik.terminal.buffer

import com.gagik.terminal.model.AdvanceResult
import com.gagik.terminal.model.Cursor
import com.gagik.terminal.model.Line
import com.gagik.terminal.model.Pen

/**
 * The main terminal buffer controller.
 *
 * Coordinates:
 * - Cursor (position tracking)
 * - Pen (attribute management)
 * - Screen (viewport over history)
 * - HistoryRing (scrollback storage)
 *
 * @param width Width of the terminal in columns. Must be > 0.
 * @param height Height of the visible screen in rows. Must be > 0.
 * @param maxHistory Maximum number of scrollback lines. Must be >= 0.
 * @throws IllegalArgumentException if width, height, or maxHistory are invalid
 */
class TerminalBuffer(
    val width: Int,
    val height: Int,
    maxHistory: Int = 1000
) {
    init {
        require(width > 0) { "width must be > 0" }
        require(height > 0) { "height must be > 0" }
        require(maxHistory >= 0) { "maxHistory must be >= 0" }
    }

    private val cursor = Cursor(width, height)
    private val pen = Pen()
    private val ring = HistoryRing(maxHistory + height) { Line(width) }
    private val screen = Screen(ring, height, width)

    init {
        // Pre-populate ring so screen has lines to view
        repeat(height) {
            ring.push().clear(pen.currentAttr)
        }
    }

    // Cursor Accessors

    /**
     * Gets the current cursor column (0-based).
     */
    val cursorCol: Int
        get() = cursor.col

    /**
     * Gets the current cursor row (0-based).
     */
    val cursorRow: Int
        get() = cursor.row

    // Writing Operations

    /**
     * Writes a single character at the current cursor position using current pen attributes.
     *
     * After writing:
     * - Cursor advances right by 1
     * - If cursor reaches line end, wraps to next line (marks current line as wrapped)
     * - If cursor exceeds screen height, scrolls up by 1 line
     *
     * @param codepoint The Unicode codepoint to write
     */
    fun writeChar(codepoint: Int) {
        screen.write(cursor.row, cursor.col, codepoint, pen.currentAttr)

        when (val result = cursor.advance()) {
            is AdvanceResult.Normal -> {
                // Nothing to do
            }
            is AdvanceResult.Wrapped -> {
                // Mark the line we just left as wrapped
                screen.getLine(result.fromRow).wrapped = true
            }
            is AdvanceResult.ScrollNeeded -> {
                // Mark line as wrapped and scroll
                screen.getLine(result.fromRow).wrapped = true
                screen.scrollUp(pen.currentAttr)
            }
        }
    }

    /**
     * Writes a string at the current cursor position.
     * Each character is written using writeChar(), handling wrapping and scrolling automatically.
     *
     * @param text The text to write
     */
    fun writeText(text: String) {
        text.forEach { writeChar(it.code) }
    }

    /**
     * Writes a newline, moving cursor to the beginning of the next line.
     * If at the bottom of the screen, scrolls up by 1 line.
     */
    fun newLine() {
        cursor.set(0, cursor.row + 1)

        if (cursor.row >= height) {
            screen.scrollUp(pen.currentAttr)
            cursor.set(0, height - 1)
        }
    }

    // Cursor Operations

    /**
     * Sets the cursor to an absolute position.
     * Position is clamped to screen bounds.
     *
     * @param col Target column (0-based)
     * @param row Target row (0-based)
     */
    fun setCursor(col: Int, row: Int) {
        cursor.set(col, row)
    }

    /**
     * Moves the cursor relatively.
     * Movement is clamped to screen bounds.
     *
     * @param dx Column delta (positive = right, negative = left)
     * @param dy Row delta (positive = down, negative = up)
     */
    fun moveCursor(dx: Int, dy: Int) {
        cursor.move(dx, dy)
    }

    // Pen Operations

    /**
     * Sets the current pen attributes for subsequent write operations.
     *
     * @param fg Foreground color (0 = default, 1-16 = ANSI colors)
     * @param bg Background color (0 = default, 1-16 = ANSI colors)
     * @param bold Whether text should be bold
     * @param italic Whether text should be italic
     * @param underline Whether text should be underlined
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

    // screen operations

    /**
     * Scrolls the screen up by one line.
     * Top line moves to history, new blank line appears at bottom.
     */
    fun scrollUp() {
        screen.scrollUp(pen.currentAttr)
    }

    /**
     * Clears the entire visible screen with the current pen attributes.
     * Does not affect scrollback history.
     */
    fun clearScreen() {
        screen.clear(pen.currentAttr)
    }

    /**
     * Clears the current line (where cursor is located).
     */
    fun clearLine() {
        screen.getLine(cursor.row).clear(pen.currentAttr)
    }

    // Query Operations

    /**
     * Gets the Line at the specified row on the visible screen.
     *
     * @param row The row index (0-based, 0 = top)
     * @return The Line at the specified row
     * @throws IllegalArgumentException if row is out of bounds
     */
    fun getLine(row: Int): Line = screen.getLine(row)

    /**
     * Gets the number of lines currently in scrollback history.
     */
    val historySize: Int
        get() = (ring.size - height).coerceAtLeast(0)

    /**
     * Gets the Line at the specified index in scrollback history.
     *
     * @param index The history index (0 = oldest in history)
     * @return The Line at the specified history index
     * @throws IllegalArgumentException if index is out of bounds
     */
    fun getHistoryLine(index: Int): Line {
        require(index in 0 until historySize) { "history index $index out of bounds (0..<$historySize)" }
        return ring[index]
    }

    // Reset Operations

    /**
     * Completely resets the terminal buffer to initial state:
     * - Clears all screen lines
     * - Clears scrollback history
     * - Resets cursor to (0, 0)
     * - Resets pen to default attributes
     */
    fun reset() {
        ring.clear()
        repeat(height) {
            ring.push().clear(pen.currentAttr)
        }
        cursor.reset()
        pen.reset()
    }
}