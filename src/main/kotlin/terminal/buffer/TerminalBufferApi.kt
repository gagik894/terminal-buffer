package com.gagik.terminal.buffer

import com.gagik.terminal.model.Attributes

/**
 * Public contract for the terminal buffer.
 *
 * Exposes cursor movement, writing, viewport mutation, rendering, and debugging
 * operations over a terminal grid with optional scrollback history.
 *
 * All row and column indices are **0-based** unless a method explicitly states
 * otherwise (e.g. [setScrollRegion] follows the 1-based DECSTBM convention).
 */
interface TerminalBufferApi {

    // ── Properties ────────────────────────────────────────────────────────────

    /** Width of the visible terminal grid, in cells. */
    val width: Int

    /** Height of the visible terminal grid, in rows. */
    val height: Int

    /** Current cursor column (0-based). */
    val cursorCol: Int

    /** Current cursor row (0-based). */
    val cursorRow: Int

    /** Number of lines currently retained in scrollback history. */
    val historySize: Int

    // ── Pen / Styling ─────────────────────────────────────────────────────────

    /**
     * Sets the active pen attributes used by all subsequent write and erase operations.
     *
     * Out-of-range colour indices are clamped to the nearest valid value.
     *
     * @param fg Foreground colour index (0 = default).
     * @param bg Background colour index (0 = default).
     * @param bold `true` to enable bold weight.
     * @param italic `true` to enable italic style.
     * @param underline `true` to enable underline.
     */
    fun setPenAttributes(
        fg: Int,
        bg: Int,
        bold: Boolean = false,
        italic: Boolean = false,
        underline: Boolean = false
    )

    /**
     * Resets the active pen to the terminal default attributes.
     *
     * Equivalent to `SGR 0` (`CSI 0 m`).
     */
    fun resetPen()

    // ── Cursor movement ───────────────────────────────────────────────────────

    /**
     * Saves the current cursor position and pen attributes (DECSC, `ESC 7`).
     *
     * Only one save slot exists per screen. Calling this again overwrites the
     * previous save. The saved state can be restored with [restoreCursor].
     */
    fun saveCursor()

    /**
     * Restores the cursor position and pen attributes previously saved by
     * [saveCursor] (DECRC, `ESC 8`).
     *
     * If no cursor has been saved, homes the cursor to `(0, 0)` and resets the
     * pen to the default attribute — matching xterm behaviour. The restored
     * position is clamped to the current grid bounds in case the terminal was
     * resized since the save.
     */
    fun restoreCursor()

    /**
     * Moves the cursor using ANSI CUP/HVP semantics.
     *
     * When DECOM is enabled, [row] is relative to the active scroll region and
     * clamped within it. When DECOM is disabled, [row] is absolute in the viewport.
     *
     * @param col Column index (0-based).
     * @param row Row index (0-based).
     */
    fun positionCursor(col: Int, row: Int)

    /**
     * Moves the cursor up by [n] rows, respecting the active scroll region (CUU, `CSI n A`).
     *
     * If the cursor is within the scroll region, movement stops at scrollTop.
     * If the cursor is above the scroll region, movement stops at row 0.
     * [n] must be positive; non-positive values are treated as no-op.
     *
     * @param n Number of rows. Must be >= 1.
     */
    fun cursorUp(n: Int = 1)

    /**
     * Moves the cursor down by [n] rows, respecting the active scroll region (CUD, `CSI n B`).
     *
     * If the cursor is within the scroll region, movement stops at scrollBottom.
     * If the cursor is below the scroll region, movement stops at the bottom row.
     * [n] must be positive; non-positive values are treated as no-op.
     *
     * @param n Number of rows. Must be >= 1.
     */
    fun cursorDown(n: Int = 1)

    /**
     * Moves the cursor left by [n] columns, clamped to column 0 (CUB, CSI n D).
     * Non-positive values are treated as no-ops.
     *
     *@param n Number of columns. Must be >= 1.
     */
    fun cursorLeft(n: Int = 1)

    /**
     * Moves the cursor left by [n] columns, clamped to column 0 (CUB, CSI n D).
     * Non-positive values are treated as no-ops.
     *
     * @param n Number of columns. Must be >= 1.
     */
    fun cursorRight(n: Int = 1)

    /**
     * Resets the cursor to the home position `(col=0, row=0)`.
     *
     * Pen attributes are not affected.
     */
    fun resetCursor()

    // ── Tab stops ─────────────────────────────────────────────────────────────

    /**
     * Sets a tab stop at the current cursor column (HTS, `ESC H`).
     *
     * The stop persists until explicitly cleared by [clearTabStop],
     * [clearAllTabStops], or a hard terminal reset.
     */
    fun setTabStop()

    /**
     * Clears the tab stop at the current cursor column (TBC 0, `CSI 0 g`).
     *
     * Has no effect if no stop exists at the current column.
     */
    fun clearTabStop()

    /**
     * Clears all tab stops (TBC 3, `CSI 3 g`).
     *
     * After this call, [horizontalTab] will advance the cursor directly to the
     * right margin until stops are re-established via [setTabStop] or a hard reset.
     */
    fun clearAllTabStops()

    /**
     * Advances the cursor to the next tab stop (HT, `0x09`).
     *
     * If no stop exists to the right of the current column, the cursor clamps
     * to the right margin (`width - 1`). Tab never triggers a line wrap.
     */
    fun horizontalTab()

    // ── Terminal modes ────────────────────────────────────────────────────────

    /**
     * Sets Insert/Replace Mode (IRM, `CSI 4 h` / `CSI 4 l`).
     *
     * - `true`  — insert mode: characters are shifted right before being written.
     * - `false` — replace mode: characters overwrite the cell at the cursor (default).
     *
     * @param enabled `true` to enable insert mode, `false` to restore replace mode.
     */
    fun setInsertMode(enabled: Boolean)

    /**
     * Sets Auto-Wrap Mode (DECAWM, `CSI ? 7 h` / `CSI ? 7 l`).
     *
     * - `true`  — the cursor wraps to the next line when it reaches the right margin (default).
     * - `false` — the cursor clamps at the right margin; subsequent characters overwrite the last cell.
     *
     * @param enabled `true` to enable auto-wrap, `false` to disable it.
     */
    fun setAutoWrap(enabled: Boolean)

    /**
     * Sets Application Cursor Keys mode (DECCKM, `CSI ? 1 h` / `CSI ? 1 l`).
     *
     * - `true`  — cursor keys emit application sequences (`ESC O A/B/C/D`).
     * - `false` — cursor keys emit normal sequences (`ESC [ A/B/C/D`) (default).
     *
     * @param enabled `true` to activate application cursor keys.
     */
    fun setApplicationCursorKeys(enabled: Boolean)

    /**
     * Sets Origin Mode (DECOM, `CSI ? 6 h` / `CSI ? 6 l`).
     *
     * Setting or clearing DECOM homes the cursor to `(0, 0)` in the new coordinate
     * system.
     */
    fun setOriginMode(enabled: Boolean)

    /**
     * Controls how ambiguous-width Unicode characters are measured.
     *
     * - `true`  — ambiguous characters (e.g. U+00B7, U+2019) occupy 2 cells.
     * - `false` — ambiguous characters occupy 1 cell (default; matches most Western locales).
     *
     * Set this once at terminal initialisation to match the host environment's
     * locale. Changing it mid-session will cause column misalignment for text
     * already on screen.
     *
     * @param enabled `true` to treat ambiguous-width characters as wide.
     */
    fun setTreatAmbiguousAsWide(enabled: Boolean)

    // ── Resize ────────────────────────────────────────────────────────────────

    /**
     * Resizes the terminal to [newWidth] × [newHeight].
     *
     * Existing content is reflowed to the new width, the cursor is relocated to
     * the corresponding position in the reflowed content, and scrollback history
     * is preserved within the configured capacity. The active scroll region is
     * reset to the full viewport after resize.
     *
     * @param newWidth  New terminal width in cells. Must be > 0.
     * @param newHeight New terminal height in rows. Must be > 0.
     * @throws IllegalArgumentException if either dimension is ≤ 0.
     */
    fun resize(newWidth: Int, newHeight: Int)

    // ── Writing ───────────────────────────────────────────────────────────────

    /**
     * Writes a single Unicode codepoint at the cursor position using the active
     * pen attributes, then advances the cursor.
     *
     * Wrapping and scrolling are applied automatically. Wide characters (e.g.
     * CJK, emoji) occupy two cells and are handled transparently.
     *
     * @param codepoint Unicode codepoint to write.
     */
    fun writeCodepoint(codepoint: Int)

    /**
     * Writes [text] literally to the buffer using the active pen attributes.
     *
     * Control characters (`\n`, `\r`, `\t`, etc.) are **not** interpreted —
     * they are written as ordinary codepoints. Use [newLine], [carriageReturn],
     * or [horizontalTab] for the corresponding terminal control behaviour.
     *
     * @param text Text to write.
     */
    fun writeText(text: String)

    /**
     * Executes a line feed (LF, `0x0A`).
     *
     * Moves the cursor down by one row without resetting the column. If the
     * cursor is on the bottom margin of the active scroll region, that region
     * scrolls up by one line.
     */
    fun newLine()

    /**
     * Executes Reverse Index (RI, `ESC M`).
     *
     * Moves the cursor up by one row without changing the column. If the cursor
     * is already on the top margin of the active scroll region, that region
     * scrolls down by one line and the cursor remains on the top margin.
     */
    fun reverseLineFeed()

    /**
     * Executes a carriage return (CR, `0x0D`).
     *
     * Moves the cursor to column 0 on the current row.
     */
    fun carriageReturn()

    // ── Scroll region ─────────────────────────────────────────────────────────

    /**
     * Sets the scroll region, clamping and validating inputs.
     * Homes the cursor as required by the VT spec:
     * - Without DECOM (origin mode off): cursor goes to (col=0, row=0).
     * - With DECOM (origin mode on): cursor goes to (col=0, row=scrollTop),
     *   i.e., the top of the newly established scroll region.
     *
     * @param top 1-based top row from the DECSTBM escape (converted to 0-based internally).
     * @param bottom 1-based bottom row from the DECSTBM escape (converted to 0-based internally).
     */
    fun setScrollRegion(top: Int, bottom: Int)

    /**
     * Resets the active scroll region to the full visible viewport.
     *
     * Homes the cursor to `(0, 0)`.
     */
    fun resetScrollRegion()

    /**
     * Scrolls the active scroll region upward by one line (SU, `CSI 1 S`).
     *
     * When the region spans the full viewport, the top line may enter scrollback
     * history and a blank line is exposed at the bottom. When the region is
     * restricted, only lines inside it rotate and scrollback is unchanged.
     * The cursor position is preserved.
     */
    fun scrollUp()

    /**
     * Scrolls the active scroll region downward by one line (SD, `CSI 1 T`).
     *
     * Lines inside the active region shift downward and a blank line is exposed
     * at the top of the region. Scrollback history is not consumed.
     * The cursor position is preserved.
     */
    fun scrollDown()

    // ── Vertical editing ──────────────────────────────────────────────────────

    /**
     * Inserts [count] blank lines at the cursor row within the active scroll
     * region (IL, `CSI n L`).
     *
     * Lines at and below the cursor row are shifted down. Lines pushed past the
     * bottom margin are discarded. Ignored if the cursor is outside the active
     * scroll region. The cursor position is preserved.
     *
     * @param count Number of blank lines to insert. Non-positive values are ignored.
     */
    fun insertLines(count: Int)

    /**
     * Deletes [count] lines starting at the cursor row within the active scroll
     * region (DL, `CSI n M`).
     *
     * Lines below the deleted area shift upward and blank lines are exposed at
     * the bottom margin. Ignored if the cursor is outside the active scroll
     * region. The cursor position is preserved.
     *
     * @param count Number of lines to delete. Non-positive values are ignored.
     */
    fun deleteLines(count: Int)

    // ── Character editing ─────────────────────────────────────────────────────

    /**
     * Inserts [count] blank cells at the cursor column, shifting existing cells
     * to the right (ICH, `CSI n @`).
     *
     * Cells shifted past the right margin are discarded.
     *
     * @param count Number of blank cells to insert. Non-positive values are ignored.
     */
    fun insertBlankCharacters(count: Int)

    /**
     * Deletes [count] characters at the cursor column, shifting the remainder
     * of the line left and filling vacated cells on the right with blanks using
     * the active pen attribute (DCH, `CSI n P`).
     *
     * The cursor position is not changed.
     *
     * @param count Number of characters to delete. Non-positive values are ignored.
     */
    fun deleteCharacters(count: Int)

    // ── Erase in Line (CSI n K) ───────────────────────────────────────────────

    /** Erases from the cursor to the end of the current line (EL 0, `CSI 0 K`). */
    fun eraseLineToEnd()

    /** Erases from the start of the current line through the cursor (EL 1, `CSI 1 K`). */
    fun eraseLineToCursor()

    /** Erases the entire current line (EL 2, `CSI 2 K`). The cursor is not moved. */
    fun eraseCurrentLine()

    // ── Erase in Display (CSI n J) ────────────────────────────────────────────

    /** Erases from the cursor to the end of the visible screen (ED 0, `CSI 0 J`). */
    fun eraseScreenToEnd()

    /** Erases from the start of the visible screen through the cursor (ED 1, `CSI 1 J`). */
    fun eraseScreenToCursor()

    /** Erases the entire visible screen without moving the cursor (ED 2, `CSI 2 J`). */
    fun eraseEntireScreen()

    /** Erases the entire visible screen and all scrollback history (ED 3, `CSI 3 J`). */
    fun eraseScreenAndHistory()

    /**
     * Clears the visible screen and homes the cursor (equivalent to ED 2 + CUP).
     *
     * Scrollback history is preserved. This matches what the shell `clear`
     * command sends.
     */
    fun clearScreen()

    /**
     * Clears all visible content and scrollback history, resets the pen, and
     * homes the cursor. The scroll region is not affected.
     *
     * This also clears the DECSC saved-cursor state, so a subsequent
     * `restoreCursor()` has no saved position to restore, and resets tab stops
     * to the VT100 defaults.
     *
     * For a complete terminal reset including the scroll region, use [reset].
     */
    fun clearAll()

    // ── Rendering (zero-allocation critical path) ─────────────────────────────

    /**
     * Returns a read-only view of a visible row.
     *
     * @param row Visible row index (0-based).
     * @return The row's line, or a blank `VoidLine` if [row] is out of bounds.
     */
    fun getLine(row: Int): TerminalLineApi

    /**
     * Returns the Unicode codepoint stored at the given screen position.
     *
     * @param col Column index (0-based).
     * @param row Row index (0-based).
     * @return The stored codepoint, or `0` if the cell is empty or out of bounds.
     */
    fun getCodepointAt(col: Int, row: Int): Int

    /**
     * Returns the packed attribute word at the given screen position.
     *
     * Intended for use in hot rendering paths. Callers should decode the packed
     * value directly via `AttributeCodec` rather than allocating an [Attributes]
     * object.
     *
     * @param col Column index (0-based).
     * @param row Row index (0-based).
     * @return The packed cell attributes, or the active pen attribute if out of bounds.
     */
    fun getPackedAttrAt(col: Int, row: Int): Int

    // ── Testing and debugging (allocating) ───────────────────────────────────

    /**
     * Returns the attributes at a screen position as an unpacked [Attributes] object.
     *
     * Allocates on every call. Intended for tests and debugging only — do not
     * use on a hot rendering path.
     *
     * @param col Column index (0-based).
     * @param row Row index (0-based).
     * @return Unpacked attributes, or `null` if the position is out of bounds.
     */
    fun getAttrAt(col: Int, row: Int): Attributes?

    /**
     * Returns the content of a visible row as a string, trimming trailing blank
     * cells while preserving intentional space characters stored in the line.
     *
     * @param row Visible row index (0-based).
     * @return The row text, or an empty string if the row is blank or out of bounds.
     */
    fun getLineAsString(row: Int): String

    /**
     * Returns the visible screen as a newline-joined string.
     *
     * @return Visible screen contents, one row per line, top to bottom.
     */
    fun getScreenAsString(): String

    /**
     * Returns the entire buffer — scrollback history followed by the visible
     * screen — as a newline-joined string.
     *
     * @return All buffer contents, oldest history line first.
     */
    fun getAllAsString(): String

    // ── Reset ─────────────────────────────────────────────────────────────────

    /**
     * Performs a full terminal reset (RIS, `ESC c`).
     *
     * Clears all visible content and scrollback history, resets the pen to
     * default attributes, homes the cursor, restores the scroll region to the
     * full viewport, resets all mode flags to their defaults, and restores tab
     * stops to the standard 8-column VT100 spacing.
     */
    fun reset()
}