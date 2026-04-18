package com.gagik.terminal.engine

import com.gagik.terminal.state.TerminalState

/**
 * Handles cursor and pen state operations that require no grid mutation.
 *
 * Owns: cursor positioning, pen save/restore (DECSC/DECRC), carriage return,
 * and horizontal tab. Does NOT touch the ring, lines, or any physical grid memory.
 *
 * ## Pending-wrap invariant
 *
 * Every method in this class that repositions the cursor must call
 * [TerminalState.cancelPendingWrap] before committing the new position. This ensures that
 * no cursor-movement sequence leaves a stale pending-wrap flag that could
 * cause the next printed character to wrap from an unexpected position.
 */
internal class CursorEngine(private val state: TerminalState) {

    private val width: Int get() = state.dimensions.width
    private val height: Int get() = state.dimensions.height

    // --- Cursor Positioning ----------------------------------------------

    /**
     * Moves the cursor to the beginning of the current line (CR, U+000D).
     * Cancels any pending wrap.
     */
    fun carriageReturn() {
        state.cancelPendingWrap()
        state.cursor.col = 0
    }

    /**
     * ANSI CUP/HVP positioning.
     * Applies VT100 Origin Mode (DECOM) translation.
     *
     * When DECOM is active, [row] is treated as relative to the active scroll
     * region, and the cursor is mathematically trapped within those margins.
     * When DECOM is inactive, positioning is absolute across the entire viewport.
     */
    fun setCursor(col: Int, row: Int) {
        state.cancelPendingWrap()
        state.cursor.col = state.dimensions.clampCol(col)

        state.cursor.row = if (state.modes.isOriginMode) {
            (state.scrollTop + row).coerceIn(state.scrollTop, state.scrollBottom)
        } else {
            state.dimensions.clampRow(row)
        }
    }

    // --- Relative Movement -----------------------------------------------

    /**
     * Moves the cursor up by [n] rows (CUU, CSI n A).
     *
     * If the cursor is within the active scroll region,
     * movement stops at scrollTop. If the cursor is above the scroll region
     * (i.e., outside it entirely), it clamps to row 0.
     */
    fun cursorUp(n: Int) {
        if (n <= 0) return
        state.cancelPendingWrap()
        val top = if (state.cursor.row in state.scrollTop..state.scrollBottom) state.scrollTop else 0
        state.cursor.row = (state.cursor.row - n).coerceAtLeast(top)
    }

    /**
     * Moves the cursor down by [n] rows (CUD, CSI n B).
     *
     * If the cursor is within the active scroll region,
     * movement stops at scrollBottom. If the cursor is below the scroll region,
     * it clamps to height - 1.
     */
    fun cursorDown(n: Int) {
        if (n <= 0) return
        state.cancelPendingWrap()
        val bottom = if (state.cursor.row in state.scrollTop..state.scrollBottom) state.scrollBottom else height - 1
        state.cursor.row = (state.cursor.row + n).coerceAtMost(bottom)
    }

    /**
     * Moves the cursor left by [n] columns, clamped to column 0 (CUB).
     */
    fun cursorLeft(n: Int) {
        if (n <= 0) return
        state.cancelPendingWrap()
        state.cursor.col = (state.cursor.col - n).coerceAtLeast(0)
    }

    /**
     * Moves the cursor right by [n] columns, clamped to width - 1 (CUF).
     */
    fun cursorRight(n: Int) {
        if (n <= 0) return
        state.cancelPendingWrap()
        state.cursor.col = (state.cursor.col + n).coerceAtMost(width - 1)
    }

    /**
     * Advances the cursor to the next tab stop (HT, U+0009).
     * Clamps to the right margin if no further stops exist.
     * Tab never triggers a line wrap.
     */
    fun horizontalTab() {
        state.cancelPendingWrap()
        state.cursor.col = state.tabStops.getNextStop(state.cursor.col)
    }

    // --- Save / Restore ----------------------------------------------

    /**
     * Saves the current cursor position, pen attributes, pending-wrap state,
     * and origin mode (DECSC, ESC 7).
     *
     * Mutates the pre-allocated state.savedCursor slot in place — zero heap
     * allocation. If called again before a restore, the previous save is overwritten.
     */
    fun saveCursor() {
        state.savedCursor.col = state.cursor.col
        state.savedCursor.row = state.cursor.row
        state.savedCursor.attr = state.pen.currentAttr
        state.savedCursor.pendingWrap = state.cursor.pendingWrap
        state.savedCursor.isOriginMode = state.modes.isOriginMode
        state.savedCursor.isSaved = true
    }

    /**
     * Restores the cursor state previously saved by [saveCursor] (DECRC, ESC 8).
     *
     * The restored state includes:
     * - cursor column
     * - cursor row
     * - pen attributes
     * - pending-wrap state
     * - origin mode (DECOM)
     *
     * ## Restoration model
     *
     * This implementation follows the professional emulator convention that
     * DECSC stores the cursor position in absolute screen coordinates, not
     * coordinates relative to the current origin mode or scroll margins.
     * Therefore, DECRC restores the saved row/column as absolute viewport
     * coordinates and clamps them only to the current screen bounds.
     *
     * Origin mode is restored as a terminal state, but it does not reinterpret
     * the saved cursor position relative to the current scroll region.
     *
     * ## Resize safety
     *
     * A saved pending-wrap state is only valid when the restored cursor is
     * still on the current right margin. If the terminal width changed since
     * [saveCursor], [pendingWrap] is restored only when `restoredCol == width - 1`;
     * otherwise it is cleared to avoid an impossible deferred-wrap state.
     *
     * ## No prior save
     *
     * If no cursor has been saved:
     * - the cursor homes to (0, 0)
     * - pending-wrap is cleared
     * - pen attributes are reset
     * - origin mode is cleared
     *
     * This matches documented DECRC reset behavior when no saved state exists.
     */
    fun restoreCursor() {
        if (!state.savedCursor.isSaved) {
            state.homeCursor()
            state.pen.reset()
            state.modes.isOriginMode = false
            return
        }

        state.modes.isOriginMode = state.savedCursor.isOriginMode

        val restoredCol = state.savedCursor.col.coerceIn(0, width - 1)
        val restoredRow = state.savedCursor.row.coerceIn(0, height - 1)

        state.cursor.col = restoredCol
        state.cursor.row = restoredRow
        state.cursor.pendingWrap = state.savedCursor.pendingWrap && restoredCol == width - 1

        state.pen.restoreAttr(state.savedCursor.attr)
    }
}