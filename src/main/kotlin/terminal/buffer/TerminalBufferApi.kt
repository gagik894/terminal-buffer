package com.gagik.terminal.buffer

import com.gagik.terminal.model.Attributes

/**
 * Public API for the terminal buffer.
 *
 * Exposes cursor movement, writing, viewport mutation, rendering, and debugging
 * operations over a terminal grid with optional scrollback history.
 */
interface TerminalBufferApi {

    // --- Properties ---

    /** Width of the visible terminal grid, in cells. */
    val width: Int

    /** Height of the visible terminal grid, in rows. */
    val height: Int

    /** Current cursor column, 0-based. */
    val cursorCol: Int

    /** Current cursor row, 0-based. */
    val cursorRow: Int

    /** Number of lines currently retained in scrollback history. */
    val historySize: Int

    // --- Styling API ---

    /**
     * Sets the active pen attributes used by subsequent write and clear operations.
     *
     * Out-of-range color indices are clamped.
     *
     * @param fg Foreground color index.
     * @param bg Background color index.
     * @param bold `true` to enable bold text.
     * @param italic `true` to enable italic text.
     * @param underline `true` to enable underline.
     */
    fun setPenAttributes(
        fg: Int,
        bg: Int,
        bold: Boolean = false,
        italic: Boolean = false,
        underline: Boolean = false
    )

    /** Resets the active pen to the terminal default attributes. */
    fun resetPen()

    // --- Cursor API ---

    /**
     * Moves the cursor to an absolute position, clamped to visible bounds.
     *
     * @param col Target column, 0-based.
     * @param row Target row, 0-based.
     */
    fun setCursor(col: Int, row: Int)

    /**
     * Moves the cursor relatively, clamped to visible bounds.
     *
     * @param dx Horizontal delta; negative values move left.
     * @param dy Vertical delta; negative values move up.
     */
    fun moveCursor(dx: Int, dy: Int)

    /**
     * Moves the cursor up by [n] rows, clamped to the top edge.
     *
     * Negative values reverse direction instead of throwing.
     *
     * @param n Number of rows to move.
     */
    fun cursorUp(n: Int = 1)

    /**
     * Moves the cursor down by [n] rows, clamped to the bottom edge.
     *
     * Negative values reverse direction instead of throwing.
     *
     * @param n Number of rows to move.
     */
    fun cursorDown(n: Int = 1)

    /**
     * Moves the cursor left by [n] columns, clamped to the left edge.
     *
     * Negative values reverse direction instead of throwing.
     *
     * @param n Number of columns to move.
     */
    fun cursorLeft(n: Int = 1)

    /**
     * Moves the cursor right by [n] columns, clamped to the right edge.
     *
     * Negative values reverse direction instead of throwing.
     *
     * @param n Number of columns to move.
     */
    fun cursorRight(n: Int = 1)

    /** Resets the cursor to the home position `(0, 0)`. */
    fun resetCursor()

    /**
     * Resizes the terminal to [newWidth] × [newHeight].
     *
     * Existing content is reflowed to the new width, the cursor is relocated to
     * the corresponding position in the reflowed content, and scrollback is
     * preserved when possible within the configured history capacity.
     *
     * The active scroll region is reset to the full viewport after resize.
     *
     * @param newWidth New terminal width, in cells.
     * @param newHeight New terminal height, in rows.
     * @throws IllegalArgumentException if [newWidth] <= 0 or [newHeight] <= 0.
     */
    fun resize(newWidth: Int, newHeight: Int)

    // --- Writing API ---

    /**
     * Writes a single Unicode codepoint using the current pen attributes and
     * advances the cursor, handling wrapping and scrolling as needed.
     *
     * @param codepoint Unicode codepoint to write.
     */
    fun writeCodepoint(codepoint: Int)

    /**
     * Writes [text] literally to the buffer.
     *
     * Control characters such as `\n`, `\r`, and `\t` are not interpreted; they
     * are written as ordinary codepoints. Use [newLine] or [carriageReturn] for
     * terminal control behavior.
     *
     * @param text Text to write literally.
     */
    fun writeText(text: String)

    /**
     * Executes a line feed.
     *
     * Moves the cursor down by one row without resetting the column. If the
     * cursor is on the bottom margin, the active scroll region is scrolled up.
     */
    fun newLine()

    /**
     * Executes a carriage return.
     *
     * Moves the cursor to column 0 on the current row.
     */
    fun carriageReturn()

    // --- Viewport & Vertical Editing API ---

    /**
     * Sets the active vertical scroll region.
     *
     * Arguments follow DECSTBM semantics: both [top] and [bottom] are 1-based
     * inclusive row numbers. Inputs are clamped to viewport bounds. Invalid or
     * degenerate regions are ignored. Setting a region resets the cursor to home.
     *
     * @param top 1-based top row, inclusive.
     * @param bottom 1-based bottom row, inclusive.
     */
    fun setScrollRegion(top: Int, bottom: Int)

    /** Resets the active scroll region to the full visible viewport. */
    fun resetScrollRegion()

    /**
     * Scrolls the active scroll region upward by one line.
     *
     * If the active region is the full viewport, the top visible line may enter
     * scrollback history and a blank line is exposed at the bottom. If the
     * region is restricted, only lines inside that region are rotated and
     * scrollback history is unchanged.
     *
     * The cursor position is preserved.
     */
    fun scrollUp()

    /**
     * Scrolls the active scroll region downward by one line.
     *
     * Lines inside the active region are shifted downward in place and a blank
     * line is exposed at the top of the region. Scrollback history is not
     * consumed.
     *
     * The cursor position is preserved.
     */
    fun scrollDown()

    /**
     * Inserts blank lines at the current cursor row within the active scroll region (ANSI IL).
     *
     * Lines at and below the cursor row are shifted downward inside the active
     * region. Lines shifted past the bottom margin are discarded. If the cursor
     * is outside the active scroll region, the operation is ignored.
     *
     * The cursor position is preserved.
     *
     * @param count Number of lines to insert. Non-positive values are ignored.
     */
    fun insertLines(count: Int)

    /**
     * Deletes lines starting at the current cursor row within the active scroll region (ANSI DL).
     *
     * Lines below the deleted area are shifted upward inside the active region,
     * and blank lines are exposed at the bottom margin. If the cursor is outside
     * the active scroll region, the operation is ignored.
     *
     * The cursor position is preserved.
     *
     * @param count Number of lines to delete. Non-positive values are ignored.
     */
    fun deleteLines(count: Int)

    // --- Line & Screen Editing API ---

    /**
     * Inserts blank cells at the cursor position on the current row (ANSI ICH),
     * shifting existing cells to the right.
     *
     * Cells shifted past the end of the line are discarded.
     *
     * @param count Number of blank cells to insert. Non-positive values are ignored.
     */
    fun insertBlankCharacters(count: Int)

    /** Erases from the cursor position to the end of the current line (ANSI EL 0). */
    fun eraseLineToEnd()

    /** Erases from the start of the current line through the cursor position (ANSI EL 1). */
    fun eraseLineToCursor()

    /** Erases the entire current line (ANSI EL 2). */
    fun eraseCurrentLine()

    /**
     * Clears the visible screen using the current pen attributes and resets the
     * cursor to home. Scrollback history is preserved.
     */
    fun clearScreen()

    /**
     * Clears all visible content and scrollback history, resets the pen, and
     * resets the cursor home.
     *
     * This does not reset the scroll region; use [reset] for a fuller terminal reset.
     */
    fun clearAll()

    // --- Rendering API (Zero Allocation - Critical Path) ---

    /**
     * Returns a read-only view of a visible row.
     *
     * @param row Visible row index, 0-based.
     * @return A read-only line, or `VoidLine` if [row] is out of bounds.
     */
    fun getLine(row: Int): TerminalLineApi

    /**
     * Returns the Unicode codepoint at a screen position.
     *
     * @param col Column index, 0-based.
     * @param row Row index, 0-based.
     * @return The stored codepoint, or `0` if the position is empty or out of bounds.
     */
    fun getCodepointAt(col: Int, row: Int): Int

    /**
     * Returns the packed attribute value at a screen position.
     *
     * Production renderers should use this method directly and decode the packed
     * value themselves.
     *
     * @param col Column index, 0-based.
     * @param row Row index, 0-based.
     * @return The packed cell attributes, or the active pen attributes if out of bounds.
     */
    fun getPackedAttrAt(col: Int, row: Int): Int

    // --- Testing & Debugging API (Allocating) ---

    /**
     * Returns the attributes at a screen position as an allocated [Attributes] object.
     *
     * This method is intended for tests and debugging, not hot rendering paths.
     *
     * @param col Column index, 0-based.
     * @param row Row index, 0-based.
     * @return Unpacked attributes, or `null` if out of bounds.
     */
    fun getAttrAt(col: Int, row: Int): Attributes?

    /**
     * Returns a visible row as text, trimming trailing blank cells while
     * preserving actual space characters stored in the line.
     *
     * @param row Visible row index, 0-based.
     * @return The rendered row text, or an empty string if the row is blank or out of bounds.
     */
    fun getLineAsString(row: Int): String

    /**
     * Returns the visible screen as newline-joined text.
     *
     * @return The visible screen contents, one row per line.
     */
    fun getScreenAsString(): String

    /**
     * Returns scrollback history followed by the visible screen as newline-joined text.
     *
     * @return Entire buffer contents, oldest history first.
     */
    fun getAllAsString(): String

    /**
     * Resets the terminal to its initial observable state.
     *
     * Clears visible content and history, resets the pen, resets the cursor, and
     * restores the scroll region to the full viewport.
     */
    fun reset()
}