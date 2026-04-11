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
     * @param fg Foreground color index (0-31)
     * @param bg Background color index (0-31)
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
     * Moves cursor up by N rows, clamping to top boundary.
     *
     * @param n Number of rows to move up (default: 1)
     * @throws IndexOutOfBoundsException if attempting to move beyond top boundary
     */
    fun cursorUp(n: Int = 1)

    /**
     * Moves cursor down by N rows, clamping to bottom boundary.
     *
     * @param n Number of rows to move down (default: 1)
     * @throws IndexOutOfBoundsException if attempting to move beyond bottom boundary
     */
    fun cursorDown(n: Int = 1)

    /**
     * Moves cursor left by N columns, clamping to left boundary.
     *
     * @param n Number of columns to move left (default: 1)
     * @throws IndexOutOfBoundsException if attempting to move beyond left boundary
     */
    fun cursorLeft(n: Int = 1)

    /**
     * Moves cursor right by N columns, clamping to right boundary.
     *
     * @param n Number of columns to move right (default: 1)
     * @throws IndexOutOfBoundsException if attempting to move beyond right boundary
     */
    fun cursorRight(n: Int = 1)

    /**Resets the cursor to the home position (0, 0). */
    fun resetCursor()

    // --- Writing API ---

    /** Writes a single Unicode codepoint using current pen attributes
     * and advances the cursor, handling line wrapping automatically.
     */
    fun writeCodepoint(codepoint: Int)

    /** Writes a string, safely iterating over Unicode code points to preserve
     * surrogate pairs (e.g., emojis) without breaking them across cell boundaries.
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

    /** Pushes a new line to the buffer, moving the top visible line into history. */
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
     * Gets the raw Unicode codepoint at a screen position.
     * @return The codepoint integer, or 0 if empty/out of bounds.
     */
    fun getCodepointAt(col: Int, row: Int): Int

    /**
     * Gets the packed attribute integer at a screen position.
     * Production Renderers MUST use this method and decode it manually.
     * @return The packed attribute integer, or default attributes if out of bounds.
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