package com.gagik.terminal.engine

import com.gagik.terminal.state.TerminalState

/**
 * Handles cursor and pen state operations that require no grid mutation.
 *
 * Owns: cursor positioning, pen save/restore (DECSC/DECRC), carriage return.
 * Does NOT touch the ring, lines, or any physical grid memory.
 */
internal class CursorEngine(private val state: TerminalState) {

    private val width:  Int get() = state.dimensions.width
    private val height: Int get() = state.dimensions.height

    /**
     * Moves the cursor to column 0 on the current row (CR, \r).
     */
    fun carriageReturn() {
        state.cursor.col = 0
    }

    /**
     * Sets the cursor to an absolute position, clamping to grid bounds.
     *
     * @param col Target column (0-based).
     * @param row Target row (0-based).
     */
    fun setCursor(col: Int, row: Int) {
        state.cursor.col = state.dimensions.clampCol(col)
        state.cursor.row = state.dimensions.clampRow(row)
    }

    /**
     * Saves the current cursor position and pen attributes (DECSC, ESC 7).
     *
     * Mutates the pre-allocated [state.savedCursor] slot in place — zero heap
     * allocation. If called again before a restore, the previous save is overwritten.
     *
     * TODO: When alt screen is implemented, a second independent SavedCursorState
     *       is needed — one slot per screen.
     */
    fun saveCursor() {
        state.savedCursor.col     = state.cursor.col
        state.savedCursor.row     = state.cursor.row
        state.savedCursor.attr    = state.pen.currentAttr
        state.savedCursor.isSaved = true
    }

    /**
     * Restores the cursor position and pen attributes saved by [saveCursor] (DECRC, ESC 8).
     *
     * If no cursor has been saved, homes the cursor to (0, 0) and resets the pen —
     * matching xterm behaviour.
     * The restored position is clamped to the current grid bounds in case the
     * terminal was resized between the save and the restore.
     */
    fun restoreCursor() {
        if (!state.savedCursor.isSaved) {
            state.cursor.col = 0
            state.cursor.row = 0
            state.pen.reset()
            return
        }
        state.cursor.col = state.savedCursor.col.coerceIn(0, width - 1)
        state.cursor.row = state.savedCursor.row.coerceIn(0, height - 1)
        state.pen.restoreAttr(state.savedCursor.attr)
    }
}