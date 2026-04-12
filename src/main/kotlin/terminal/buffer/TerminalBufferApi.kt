package com.gagik.terminal.buffer

import com.gagik.terminal.model.Attributes

/**
 * Public API for the terminal buffer.
 * Exposes only the operations needed by consumers.
 */
interface TerminalBufferApi {

    /** Width of the terminal grid. */
    val width: Int

    /** Height of the terminal grid. */
    val height: Int

    /** The current column index of the cursor (0-based). */
    val cursorCol: Int

    /** The current row index of the cursor (0-based). */
    val cursorRow: Int

    /** The number of lines currently held in the scrollback history. */
    val historySize: Int

    // --- Styling API ---

    /**
     * Sets the active pen attributes for subsequent write operations.
     * Uses packed primitives to prevent memory allocation during ANSI parsing.
     *
     * @param fg Foreground color index (0-16, 0 is default). Out-of-range values are clamped.
     * @param bg Background color index (0-16, 0 is default). Out-of-range values are clamped.
     * @param bold True to enable bold text
     * @param italic True to enable italic text
     * @param underline True to enable underlined text
     */
    fun setPenAttributes(fg: Int, bg: Int, bold: Boolean = false, italic: Boolean = false, underline: Boolean = false)

    /**
     * Resets the active pen to the terminal's default style.
     */
    fun resetPen()

    // --- Cursor API ---

    /**
     * Sets the cursor to an absolute position, safely clamping to grid bounds.
     *
     * @param col Column index (0-based)
     * @param row Row index (0-based)
     */
    fun setCursor(col: Int, row: Int)

    /**
     * Moves the cursor relatively, safely clamping to grid bounds.
     *
     * @param dx Horizontal offset (can be negative)
     * @param dy Vertical offset (can be negative)
     */
    fun moveCursor(dx: Int, dy: Int)

    /**
     * Moves cursor up by N rows, safely clamping to the top boundary.
     *
     * @param n Number of rows to move up (default: 1)
     */
    fun cursorUp(n: Int = 1)

    /**
     * Moves cursor down by N rows, safely clamping to the bottom boundary.
     *
     * @param n Number of rows to move down (default: 1)
     */
    fun cursorDown(n: Int = 1)

    /**
     * Moves cursor left by N columns, safely clamping to the left boundary.
     *
     * @param n Number of columns to move left (default: 1)
     */
    fun cursorLeft(n: Int = 1)

    /**
     * Moves cursor right by N columns, safely clamping to the right boundary.
     *
     * @param n Number of columns to move right (default: 1)
     */
    fun cursorRight(n: Int = 1)

    /** Resets the cursor to the home position (0, 0). */
    fun resetCursor()

    // --- Writing API ---

    /** Writes a single Unicode codepoint using current pen attributes
     * and advances the cursor, handling line wrapping automatically.
     */
    fun writeCodepoint(codepoint: Int)

    /**
     * Writes a string literally to the buffer.
     *
     * Note: This method does NOT interpret control characters (like \n, \r, \t).
     * They are written into cells as literal codepoints. Use [newLine] or
     * [carriageReturn] for terminal behavior.
     */
    fun writeText(text: String)

    /** Inserts N blank characters at the cursor, shifting the remainder of the
     * line's contents to the right (Corresponds to ANSI ICH).
     */
    fun insertBlankCharacters(count: Int)

    /** Executes a Line Feed (\n), scrolling the screen if at the bottom. */
    fun newLine()

    /** Executes a Carriage Return (\r), moving the cursor to column 0. */
    fun carriageReturn()

    // --- Viewport API ---

    /**
     * Pushes a new blank line to the bottom of the buffer using the current pen attributes.
     * Existing lines are shifted up, and the top visible line is moved into history.
     * The cursor position is preserved.
     */
    fun scrollUp()

    /** Clears the visible screen and resets the cursor to home. History is preserved. */
    fun clearScreen()

    /** Hard resets the terminal, destroying all visible text and history. */
    fun clearAll()

    /** Erases from the cursor to the end of the current line (ANSI EL 0). */
    fun eraseLineToEnd()

    /** Erases from the beginning of the current line to the cursor (ANSI EL 1). */
    fun eraseLineToCursor()

    /** Erases the entire current line (ANSI EL 2). */
    fun eraseCurrentLine()

    // --- Rendering API (Zero Allocation - Critical Path) ---

    /**
     * Retrieves a read-only view of a specific visual row.
     * @param row The visual row (0 is the top of the screen).
     * @return A read-only line, or null if the row is out of bounds.
     */
    fun getLine(row: Int): TerminalLineApi?

    /**
     * Gets the raw Unicode codepoint at a screen position.
     * @return The codepoint integer, or 0 if empty/out of bounds.
     */
    fun getCodepointAt(col: Int, row: Int): Int

    /**
     * Gets the packed attribute integer at a screen position.
     * Production Renderers MUST use this method and decode it manually.
     * @return The packed attribute integer, or the active pen's attributes if out of bounds.
     */
    fun getPackedAttrAt(col: Int, row: Int): Int

    // --- Testing & Debugging API (Allocating) ---

    /**
     * Gets the attributes at a screen position as an allocated [Attributes] object.
     *
     * **WARNING: DO NOT USE IN PRODUCTION RENDERING LOOPS.**
     * use [getPackedAttrAt] instead.
     *
     * @param col Column (0-based)
     * @param row Row (0-based)
     * @return Unpacked Attributes object, or null if out of bounds.
     */
    fun getAttrAt(col: Int, row: Int): Attributes?

    /** Gets the text of a specific visual row, trimming trailing spaces. */
    fun getLineAsString(row: Int): String

    /** Gets the entire visible screen content as a single string. */
    fun getScreenAsString(): String

    /** Gets all buffer content (scrollback + screen) as a single string. */
    fun getAllAsString(): String

    /** Resets cursor, pen, and screen to initial states. */
    fun reset()
}