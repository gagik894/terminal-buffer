package com.gagik.terminal.api

/**
 * Cursor-movement contract for the terminal buffer.
 *
 * Consumed by the ANSI parser for all cursor positioning, save/restore,
 * and tab stop commands.
 */
interface TerminalCursor {

    /**
     * Moves the cursor to an absolute position (CUP / HVP, `CSI row ; col H`).
     *
     * When DECOM is active, [row] is relative to the top of the scroll region
     * and clamped within it. When DECOM is inactive, [row] is absolute in the
     * viewport. [col] is always absolute.
     *
     * @param col Target column (0-based).
     * @param row Target row (0-based).
     */
    fun positionCursor(col: Int, row: Int)

    /**
     * Moves the cursor up by [n] rows (CUU, `CSI n A`).
     *
     * Stops at the top of the scroll region when the cursor is inside it,
     * or at row 0 when outside. Non-positive values are no-ops.
     *
     * @param n Number of rows. Must be ≥ 1.
     */
    fun cursorUp(n: Int = 1)

    /**
     * Moves the cursor down by [n] rows (CUD, `CSI n B`).
     *
     * Stops at the bottom of the scroll region when the cursor is inside it,
     * or at the last row when outside. Non-positive values are no-ops.
     *
     * @param n Number of rows. Must be ≥ 1.
     */
    fun cursorDown(n: Int = 1)

    /**
     * Moves the cursor left by [n] columns, clamped to column 0 (CUB, `CSI n D`).
     *
     * @param n Number of columns. Non-positive values are no-ops.
     */
    fun cursorLeft(n: Int = 1)

    /**
     * Moves the cursor right by [n] columns, clamped to the right margin
     * (CUF, `CSI n C`).
     *
     * @param n Number of columns. Non-positive values are no-ops.
     */
    fun cursorRight(n: Int = 1)

    /**
     * Saves the current cursor position and pen attributes (DECSC, `ESC 7`).
     *
     * One save slot exists per screen. Subsequent calls overwrite the slot.
     * Restored by [restoreCursor].
     */
    fun saveCursor()

    /**
     * Restores the cursor position and pen attributes saved by [saveCursor]
     * (DECRC, `ESC 8`).
     *
     * If no cursor has been saved, homes the cursor to `(0, 0)` and resets
     * the pen to its default — matching xterm behaviour. The restored position
     * is clamped to the current grid bounds.
     */
    fun restoreCursor()

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
     * The stop persists until cleared by [clearTabStop], [clearAllTabStops],
     * or a hard reset.
     */
    fun setTabStop()

    /**
     * Clears the tab stop at the current cursor column (TBC 0, `CSI 0 g`).
     *
     * No-op if no stop exists at the current column.
     */
    fun clearTabStop()

    /**
     * Clears all tab stops (TBC 3, `CSI 3 g`).
     *
     * After this call, [horizontalTab] advances the cursor directly to the right
     * margin until stops are re-established.
     */
    fun clearAllTabStops()

    /**
     * Advances the cursor to the next tab stop (HT, `0x09`).
     *
     * Clamps to the right margin (`width - 1`) when no stop exists to the right.
     * Never triggers a line wrap.
     */
    fun horizontalTab()
}