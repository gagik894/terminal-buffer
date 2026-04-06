package com.gagik.terminal.buffer

import com.gagik.terminal.model.Attributes

/**
 * Public API for the terminal buffer.
 * Exposes only the operations needed by consumers.
 */
interface TerminalBufferApi {
    val width: Int
    val height: Int

    /** Current cursor column (0-based). */
    val cursorCol: Int

    /** Current cursor row (0-based). */
    val cursorRow: Int

    /**
     * Number of lines currently in scrollback history.
     */
    val historySize: Int

    /**
     * Sets pen attributes for subsequent write operations.
     *
     * @param attributes Attributes to set on the pen
     */
    fun setAttributes(attributes: Attributes)

    /**
     * Resets pen to default attributes.
     */
    fun resetPen()

    /**
     * Sets cursor to an absolute position.
     * Position is clamped to screen bounds.
     *
     * @param col Target column (0-based)
     * @param row Target row (0-based)
     */
    fun setCursor(col: Int, row: Int)

    /**
     * Moves cursor relatively.
     * Movement is clamped to screen bounds.
     *
     * @param dx Column delta (positive = right, negative = left)
     * @param dy Row delta (positive = down, negative = up)
     */
    fun moveCursor(dx: Int, dy: Int)

    /**
     * Moves cursor up by N rows.
     * @param n Number of rows to move (default 1)
     */
    fun cursorUp(n: Int = 1)

    /**
     * Moves cursor down by N rows.
     * @param n Number of rows to move (default 1)
     */
    fun cursorDown(n: Int = 1)

    /**
     * Moves cursor left by N columns.
     * @param n Number of columns to move (default 1)
     */
    fun cursorLeft(n: Int = 1)

    /**
     * Moves cursor right by N columns.
     * @param n Number of columns to move (default 1)
     */
    fun cursorRight(n: Int = 1)

    /**
     * Resets cursor to origin (0, 0).
     */
    fun resetCursor()

    /**
     * Writes a single character at the current cursor position.
     * Uses current pen attributes and advances cursor with auto-wrap.
     *
     * @param value Character to write
     */
    fun writeChar(value: Char)

    /**
     * Writes a string at the current cursor position.
     * Each character is written sequentially with auto-wrap.
     *
     * @param text Text to write
     */
    fun writeText(text: String)

    /**
     * Inserts text at the current cursor position, shifting existing content to the right.
     *
     * @param text Text to insert
     */
    fun insertText(text: String)

    /**
     * Inserts a line break, moving cursor to the beginning of the next line.
     * If at the bottom of the screen, scrolls up.
     */
    fun newLine()

    /**
     * Moves cursor to the beginning of the current line.
     */
    fun carriageReturn()

    /**
     * Scrolls the screen up by one line.
     * Top line moves to history, new blank line appears at bottom.
     */
    fun scrollUp()

    /**
     * Clears the entire visible screen.
     * History is not affected.
     */
    fun clearScreen()

    /**
     * Clears the entire screen and history.
     */
    fun clearAll()

    /**
     * Fills the current line with a character using current attributes.
     * Cursor position is not affected.
     *
     * @param value Character to fill with (null clears to empty)
     */
    fun fillLine(value: Char? = null)

    /**
     * Fills a specific line with a character using current attributes.
     *
     * @param row Screen row (0-based)
     * @param value Character to fill with (null clears to empty)
     */
    fun fillLineAt(row: Int, value: Char? = null)

    /**
     * Gets the character at a screen position.
     *
     * @param col Column (0-based)
     * @param row Row (0-based)
     * @return Unicode character, or null if out of bounds
     */
    fun getCharAt(col: Int, row: Int): Char?

    /**
     * Gets the attributes at a screen position.
     *
     * @param col Column (0-based)
     * @param row Row (0-based)
     * @return Attributes, or null if out of bounds
     */
    fun getAttrAt(col: Int, row: Int): Attributes?

    /**
     * Gets the character at a history position.
     *
     * @param index History index (0 = oldest)
     * @param col Column (0-based)
     * @return Unicode character, or null if out of bounds
     */
    fun getHistoryCharAt(index: Int, col: Int): Char?

    /**
     * Gets the attributes at a history position.
     *
     * @param index History index (0 = oldest)
     * @param col Column (0-based)
     * @return Attributes, or null if out of bounds
     */
    fun getHistoryAttrAt(index: Int, col: Int): Attributes?

    /**
     * Gets a screen line as a string.
     *
     * @param row Screen row (0-based)
     * @return String content of the line (trimmed)
     * @throws IllegalArgumentException if row is out of bounds
     */
    fun getLineAsString(row: Int): String

    /**
     * Gets a history line as a string.
     *
     * @param index History index (0 = oldest)
     * @return String content of the line (trimmed)
     * @throws IllegalArgumentException if index is out of bounds
     */
    fun getHistoryLineAsString(index: Int): String

    /**
     * Gets the entire visible screen content as a string.
     * Lines are separated by newlines. Trailing spaces on each line are trimmed.
     *
     * @return String representation of the visible screen
     */
    fun getScreenAsString(): String

    /**
     * Gets all content (scrollback + screen) as a string.
     * Lines are separated by newlines. Trailing spaces on each line are trimmed.
     *
     * @return String representation of all content
     */
    fun getAllAsString(): String

    /**
     * Completely resets the terminal to initial state:
     * - Clears screen and history
     * - Resets cursor to origin
     * - Resets pen to defaults
     */
    fun reset()
}
