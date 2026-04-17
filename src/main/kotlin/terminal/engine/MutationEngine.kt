package com.gagik.terminal.engine

import com.gagik.terminal.model.Line
import com.gagik.terminal.model.TerminalConstants
import com.gagik.terminal.state.TerminalState

/**
 * Dedicated mutation engine for grid writes and line-level erase/edit operations.
 *
 * Owns all overwrite physics so callers cannot leave orphaned wide-character spacers.
 * All ring-index translation is delegated to [TerminalState.resolveRingIndex].
 * All circular-buffer arithmetic is encapsulated inside [com.gagik.terminal.buffer.HistoryRing].
 */
internal class MutationEngine(
    private val state: TerminalState
) {
    // width and height are cached for performance
    private val width: Int get() = state.dimensions.width
    private val height: Int get() = state.dimensions.height

    // ----- Line access --------------------------------------------------------

    /**
     * Returns the mutable Line for a given viewport row.
     * Single source of truth for viewport→ring translation.
     */
    private fun getLine(row: Int): Line = state.ring[state.resolveRingIndex(row)]

    // ----- Scroll -------------------------------------------------------------

    /**
     * Scrolls the active scroll region up by [count] lines.
     *
     * - Full-viewport scroll: pushes new blank lines into the history ring (cheap O(1) each).
     * - Partial-region scroll: rotates line references in place, no history is written.
     *
     * In both cases the newly exposed line at the bottom of the region is cleared with
     * the current pen attribute *after* rotation so the correct slot receives the fill.
     */
    fun scrollUp(count: Int = 1) {
        val top    = state.scrollTop
        val bottom = state.scrollBottom
        val n      = count.coerceIn(0, bottom - top + 1)
        if (n == 0) return

        if (state.isFullViewportScroll) {
            repeat(n) {
                state.ring.push().clear(state.pen.currentAttr)
            }
        } else {
            val absTop    = state.resolveRingIndex(top)
            val absBottom = state.resolveRingIndex(bottom)
            repeat(n) {
                state.ring.rotateUp(absTop, absBottom)
                state.ring[absBottom].clear(state.pen.currentAttr)
            }
        }
    }

    /**
     * Scrolls the active scroll region down by [count] lines.
     *
     * Always rotates in place — scroll-down never writes to history.
     * The newly exposed line at the top of the region is cleared after rotation.
     */
    fun scrollDown(count: Int = 1) {
        val top    = state.scrollTop
        val bottom = state.scrollBottom
        val n      = count.coerceIn(0, bottom - top + 1)
        if (n == 0) return

        val absTop    = state.resolveRingIndex(top)
        val absBottom = state.resolveRingIndex(bottom)
        repeat(n) {
            state.ring.rotateDown(absTop, absBottom)
            state.ring[absTop].clear(state.pen.currentAttr)
        }
    }

    // ----- Cursor row advancement helper -------------------------------------

    /**
     * Advances a viewport row by one, triggering [scrollUp] if the cursor is
     * sitting on [state.scrollBottom], otherwise simply clamping to [height]-1.
     *
     * Used by [writeToGrid], [printCodepoint], and [newLine] to keep wrap/scroll
     * logic in one place.
     */
    private fun advanceRow(row: Int): Int {
        return if (row == state.scrollBottom) {
            scrollUp()
            state.scrollBottom
        } else {
            (row + 1).coerceAtMost(height - 1)
        }
    }

    // ----- Wide-character helpers --------------------------------------------

    private fun findClusterStart(line: Line, col: Int): Int {
        if (col !in 0 until width) return col

        val cp = line.getCodepoint(col)
        if (cp == TerminalConstants.WIDE_CHAR_SPACER) {
            val prev = col - 1
            if (prev >= 0) {
                val prevCp = line.getCodepoint(prev)
                if (prevCp != TerminalConstants.EMPTY && prevCp != TerminalConstants.WIDE_CHAR_SPACER) {
                    return prev
                }
            }
        }
        return col
    }

    /**
     * Clears the full cluster at a coordinate.
     *
     * @param row Target viewport row.
     * @param col Target viewport column.
     */
    private fun annihilateAt(row: Int, col: Int) {
        if (row !in 0 until height || col !in 0 until width) return

        val line = getLine(row)
        val start = findClusterStart(line, col)
        val cp = line.getCodepoint(start)
        val attr = state.pen.currentAttr

        if (cp == TerminalConstants.WIDE_CHAR_SPACER) {
            line.setCell(start, TerminalConstants.EMPTY, attr)
            return
        }

        if (start + 1 < width && line.getCodepoint(start + 1) == TerminalConstants.WIDE_CHAR_SPACER) {
            line.setCell(start, TerminalConstants.EMPTY, attr)
            line.setCell(start + 1, TerminalConstants.EMPTY, attr)
            return
        }

        line.setCell(start, TerminalConstants.EMPTY, attr)
    }

    // ----- Core write engine -------------------------------------------------

    /**
     * Core write engine shared by [printCodepoint] and [printCluster].
     *
     * Executes: edge-wrap → annihilation → caller write → spacer placement →
     * standard-wrap → scroll, then commits the cursor.
     *
     * ARCHITECTURAL WARNING: do NOT split into smaller helpers. Edge-wrap
     * requires interleaved clears and moves; splitting destroys the JIT fast path.
     */
    private inline fun writeToGrid(charWidth: Int, writeCell: (line: Line, col: Int) -> Unit) {
        var cCol = state.cursor.col
        var cRow = state.cursor.row
        val widthInCells = if (charWidth == 2) 2 else 1

        if (cRow !in 0 until height || cCol !in 0 until width) return
        var line = getLine(cRow)

        // Edge Wrap: a 2-cell character cannot fit in the last column.
        if (widthInCells == 2 && cCol == width - 1) {
            annihilateAt(cRow, cCol)
            cCol = 0
            cRow = advanceRow(cRow)
            line = getLine(cRow)
        }

        // Annihilation: clear the blast radius before writing.
        annihilateAt(cRow, cCol)
        if (widthInCells == 2 && cCol + 1 < width) annihilateAt(cRow, cCol + 1)

        // Caller-supplied write phase.
        writeCell(line, cCol)
        cCol += 1

        // Wide spacer placement.
        if (widthInCells == 2 && cCol < width) {
            line.setCell(cCol, TerminalConstants.WIDE_CHAR_SPACER, state.pen.currentAttr)
            cCol += 1
        }

        // Standard wrap + scroll.
        if (cCol >= width) {
            line.wrapped = true
            cCol = 0
            cRow = advanceRow(cRow)
        }

        state.cursor.col = cCol
        state.cursor.row = cRow
    }

    // ----- Public write API --------------------------------------------------

    /**
     * Writes a Unicode codepoint to the grid and manages wide-character physics.
     *
     * Fast path: 1-cell write into an empty cell bypasses all collision checks.
     * Slow path: delegates to [writeToGrid] for full physics.
     */
    fun printCodepoint(codepoint: Int, charWidth: Int) {
        val attr = state.pen.currentAttr
        val cCol = state.cursor.col
        val cRow = state.cursor.row

        // Fast path: 1-cell write into empty space.
        if (charWidth != 2 && cRow in 0 until height && cCol in 0 until width) {
            val line = getLine(cRow)
            if (line.rawCodepoint(cCol) == TerminalConstants.EMPTY) {
                line.setCell(cCol, codepoint, attr)
                if (cCol == width - 1) {
                    line.wrapped = true
                    state.cursor.col = 0
                    state.cursor.row = advanceRow(cRow)
                } else {
                    state.cursor.col = cCol + 1
                }
                return
            }
        }

        // Slow path: delegate all physics to writeToGrid.
        writeToGrid(charWidth) { line, col ->
            line.setCell(col, codepoint, attr)
        }
    }

    fun printCluster(cps: IntArray, cpLen: Int, charWidth: Int) {
        if (cpLen == 1) {
            printCodepoint(cps[0], charWidth)
            return
        }
        val attr = state.pen.currentAttr
        writeToGrid(charWidth) { line, col ->
            line.setCluster(col, cps, cpLen, attr)
        }
    }

    // ----- Editing -----------------------------------------------------------
    /**
     * Applies a vertical line mutation inside the active scroll region.
     *
     * Returns immediately if the cursor is outside the region or the count is non-positive.
     * The callback receives the resolved ring indices for the cursor row and bottom margin,
     * plus the clamped number of repetitions.
     */
    private inline fun mutateLines(
        count: Int,
        onMutate: (absCursorRow: Int, absBottom: Int, times: Int) -> Unit
    ) {
        if (count <= 0) return

        val cRow = state.cursor.row
        val top = state.scrollTop
        val bottom = state.scrollBottom
        if (cRow !in top..bottom) return

        val times = count.coerceAtMost(bottom - cRow + 1)
        val absCursorRow = state.resolveRingIndex(cRow)
        val absBottom = state.resolveRingIndex(bottom)

        onMutate(absCursorRow, absBottom, times)
    }

    /**
     * Inserts [count] blank lines at the current cursor row (IL sequence).
     *
     * Lines at and below the cursor are shifted down inside the active scroll
     * region. Lines shifted past the bottom margin are dropped.
     *
     * Ignored when the cursor is outside the scroll region.
     */
    fun insertLines(count: Int) {
        mutateLines(count) { absCursorRow, absBottom, times ->
            repeat(times) {
                state.ring.rotateDown(absCursorRow, absBottom)
                state.ring[absCursorRow].clear(state.pen.currentAttr)
            }
        }
    }

    /**
     * Deletes [count] lines starting at the current cursor row (DL sequence).
     *
     * Lines below the deleted region are shifted up inside the active scroll
     * region. Newly exposed lines at the bottom margin are cleared.
     *
     * Ignored when the cursor is outside the scroll region.
     */
    fun deleteLines(count: Int) {
        mutateLines(count) { absCursorRow, absBottom, times ->
            repeat(times) {
                state.ring.rotateUp(absCursorRow, absBottom)
                state.ring[absBottom].clear(state.pen.currentAttr)
            }
        }
    }

    fun insertBlankCharacters(count: Int) {
        if (count <= 0) return

        val cRow = state.cursor.row
        val cCol = state.cursor.col
        if (cRow !in 0 until height || cCol !in 0 until width) return

        val line = getLine(cRow)
        if (line.getCodepoint(cCol) == TerminalConstants.WIDE_CHAR_SPACER) {
            annihilateAt(cRow, cCol)
        }
        line.insertCells(cCol, count, state.pen.currentAttr)
    }

    /**
     * Deletes [count] characters starting at the cursor column, shifting the
     * remainder of the line left and filling the vacated cells on the right
     * with blanks using the current pen attribute. The cursor position is not
     * changed. Corresponds to ANSI DCH (CSI n P).
     *
     * Wide-character safety:
     * - The cluster at the cursor is annihilated before the shift so the left
     *   boundary is never left with an orphaned spacer.
     * - The cell immediately after the deleted region is checked: if it is a
     *   wide-character spacer the owning leader would be shifted in without its
     *   spacer, so the entire cluster is annihilated before the shift proceeds.
     *   Ordinary characters at the right boundary are never touched.
     *
     * @param count Number of characters to delete. Non-positive values are ignored.
     *              Values larger than the remaining columns from the cursor are
     *              clamped to the line's right edge.
     */
    fun deleteCharacters(count: Int) {
        if (count <= 0) return

        val cRow = state.cursor.row
        val cCol = state.cursor.col
        if (cRow !in 0 until height || cCol !in 0 until width) return

        val safeCount = count.coerceAtMost(width - cCol)

        annihilateAt(cRow, cCol)

        if (safeCount < width - cCol) {
            val rightEdge = cCol + safeCount
            if (getLine(cRow).rawCodepoint(rightEdge) == TerminalConstants.WIDE_CHAR_SPACER) {
                annihilateAt(cRow, rightEdge)
            }
        }

        getLine(cRow).deleteCells(cCol, safeCount, state.pen.currentAttr)
    }

    fun eraseLineToEnd() {
        val cRow = state.cursor.row
        val cCol = state.cursor.col
        if (cRow !in 0 until height || cCol !in 0 until width) return
        annihilateAt(cRow, cCol)
        getLine(cRow).clearFromColumn(cCol, state.pen.currentAttr)
    }

    fun eraseLineToCursor() {
        val cRow = state.cursor.row
        val cCol = state.cursor.col
        if (cRow !in 0 until height || cCol !in 0 until width) return
        annihilateAt(cRow, cCol)
        getLine(cRow).clearToColumn(cCol, state.pen.currentAttr)
    }

    fun eraseCurrentLine() {
        val cRow = state.cursor.row
        if (cRow !in 0 until height) return
        getLine(cRow).clear(state.pen.currentAttr)
    }

    /**
     * Erases from the cursor to the end of the visible screen (ED 0).
     * The cursor position is not changed.
     */
    fun eraseScreenToEnd() {
        val cRow = state.cursor.row
        if (cRow !in 0 until height) return

        // Clear the remainder of the current line
        eraseLineToEnd()

        // Clear all subsequent lines
        for (row in cRow + 1 until height) {
            getLine(row).clear(state.pen.currentAttr)
        }
    }

    /**
     * Erases from the start of the visible screen through the cursor (ED 1).
     * The cursor position is not changed.
     */
    fun eraseScreenToCursor() {
        val cRow = state.cursor.row
        if (cRow !in 0 until height) return

        // Clear all preceding lines
        for (row in 0 until cRow) {
            getLine(row).clear(state.pen.currentAttr)
        }

        // Clear the beginning of the current line through the cursor
        eraseLineToCursor()
    }

    /**
     * Erases the entire visible screen and all scrollback history (xterm ED 3).
     * The cursor position is not changed.
     */
    fun eraseScreenAndHistory() {
        clearAllHistory()
    }

    fun clearViewport() {
        for (row in 0 until height.coerceAtMost(state.ring.size)) {
            getLine(row).clear(state.pen.currentAttr)
        }
    }

    fun clearAllHistory() {
        state.ring.clear()
        repeat(height) { state.ring.push().clear(state.pen.currentAttr) }
    }

    // ----- Cursor movement ---------------------------------------------------

    /**
     * Advances the cursor to the next line, scrolling the active region if needed.
     */
    fun newLine() {
        state.cursor.row = advanceRow(state.cursor.row)
    }

    /**
     * Moves the cursor up one line (reverse index), scrolling the active region
     * downward if the cursor is already at [state.scrollTop].
     */
    fun reverseLineFeed() {
        val cRow = state.cursor.row
        if (cRow == state.scrollTop) {
            scrollDown()
        } else {
            state.cursor.row = (cRow - 1).coerceAtLeast(0)
        }
    }
}