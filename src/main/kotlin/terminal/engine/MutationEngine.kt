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
    private val width: Int get() = state.dimensions.width
    private val height: Int get() = state.dimensions.height

    // ----- Shared mutation protocol ------------------------------------------

    /**
     * Non-printing structural commands must cancel pending wrap before mutating state.
     * Keep this tiny and inline so it stays a protocol guard, not an abstraction tax.
     */
    private inline fun structuralMutation(block: () -> Unit) {
        state.cancelPendingWrap()
        block()
    }

    // ----- Line access --------------------------------------------------------

    /**
     * Returns the mutable Line for a given viewport row.
     * Single source of truth for viewport→ring translation.
     */
    private fun getLine(row: Int): Line = state.ring[state.resolveRingIndex(row)]

    // ----- Scroll -------------------------------------------------------------

    /**
     * Internal scroll-up primitive that does not touch pending-wrap state.
     *
     * CRITICAL: This function is called from two distinct contexts:
     * 1. Public API [scrollUp] via [structuralMutation] (which cancels pendingWrap first)
     * 2. Internal [advanceRow] → [writeToGrid] (where pendingWrap is managed by the write engine itself)
     *
     * For context 2, calling [state.cancelPendingWrap] here would silently corrupt mid-write
     * wrap state, since [writeToGrid] has already decided whether to set or defer wrapping.
     *
     * DO NOT add [state.cancelPendingWrap] to this function. The public API is responsible for
     * calling [structuralMutation], which handles the cancel. Adding it here would break writes
     * that legitimately rely on edge-wrap behavior inside [advanceRow].
     */
    private fun scrollUpInternal(count: Int = 1) {
        val top = state.scrollTop
        val bottom = state.scrollBottom
        val n = count.coerceIn(0, bottom - top + 1)
        if (n == 0) return

        if (state.isFullViewportScroll) {
            repeat(n) {
                state.ring.push().clear(state.pen.currentAttr)
            }
        } else {
            val absTop = state.resolveRingIndex(top)
            val absBottom = state.resolveRingIndex(bottom)
            repeat(n) {
                state.ring.rotateUp(absTop, absBottom)
                state.ring[absBottom].clear(state.pen.currentAttr)
            }
        }
    }

    /**
     * Internal scroll-down primitive that does not touch pending-wrap state.
     *
     * See [scrollUpInternal] for the architectural rationale: this function is called both
     * from the public API (via [structuralMutation]) and internally from [advanceRow].
     *
     * DO NOT add [state.cancelPendingWrap] to this function for the same reasons as
     * [scrollUpInternal]: doing so would corrupt pending wrap state managed by [writeToGrid].
     */
    private fun scrollDownInternal(count: Int = 1) {
        val top = state.scrollTop
        val bottom = state.scrollBottom
        val n = count.coerceIn(0, bottom - top + 1)
        if (n == 0) return

        val absTop = state.resolveRingIndex(top)
        val absBottom = state.resolveRingIndex(bottom)
        repeat(n) {
            state.ring.rotateDown(absTop, absBottom)
            state.ring[absTop].clear(state.pen.currentAttr)
        }
    }

    /**
     * Public scroll-up command.
     */
    fun scrollUp(count: Int = 1) = structuralMutation {
        scrollUpInternal(count)
    }

    /**
     * Public scroll-down command.
     */
    fun scrollDown(count: Int = 1) = structuralMutation {
        scrollDownInternal(count)
    }

    // ----- Cursor row advancement helper -------------------------------------

    /**
     * Advances a viewport row by one, triggering scroll if the cursor is
     * sitting on [state.scrollBottom], otherwise simply clamping to [height]-1.
     *
     * Scroll is only triggered when [row] == [state.scrollBottom]. A cursor
     * outside the scroll region advances freely and never triggers a scroll,
     * which is correct VT behaviour for writing below a restricted region.
     *
     * Invariant: [state.scrollBottom] is always <= height - 1, guaranteed by
     * [setScrollRegion] and [resetScrollRegion], so the clamp path can never
     * overshoot [state.scrollBottom] from below.
     *
     * Used by [writeToGrid], [printCodepoint], [printCluster], and [newLine].
     */
    private fun advanceRow(row: Int): Int {
        return if (row == state.scrollBottom) {
            scrollUpInternal()
            state.scrollBottom
        } else {
            (row + 1).coerceAtMost(height - 1)
        }
    }

    // ----- Wide-character helpers --------------------------------------------

    private fun findClusterStart(line: Line, col: Int): Int {
        if (col !in 0 until width) return col

        val raw = line.rawCodepoint(col)
        if (raw == TerminalConstants.WIDE_CHAR_SPACER) {
            val prev = col - 1
            if (prev >= 0) {
                val prevRaw = line.rawCodepoint(prev)
                if (prevRaw != TerminalConstants.EMPTY && prevRaw != TerminalConstants.WIDE_CHAR_SPACER) {
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
        val raw = line.rawCodepoint(start)
        val attr = state.pen.currentAttr

        if (raw == TerminalConstants.WIDE_CHAR_SPACER) {
            line.setCell(start, TerminalConstants.EMPTY, attr)
            return
        }

        if (start + 1 < width && line.rawCodepoint(start + 1) == TerminalConstants.WIDE_CHAR_SPACER) {
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
    private inline fun writeToGrid(charWidth: Int, crossinline writeCell: (line: Line, col: Int) -> Unit) {
        var cCol = state.cursor.col
        var cRow = state.cursor.row
        val widthInCells = if (charWidth == 2) 2 else 1

        if (cRow !in 0 until height || cCol !in 0 until width) return

        if (widthInCells == 2 && cCol >= width - 1 && !state.modes.isAutoWrap) {
            return // Ignored per VT spec: wide char does not fit, and wrap is forbidden
        }

        var line = getLine(cRow)

        if (state.cursor.pendingWrap) {
            state.cursor.pendingWrap = false
            line.wrapped = true
            cCol = 0
            cRow = advanceRow(cRow)
            line = getLine(cRow)
        }

        if (widthInCells == 2 && cCol >= width - 1) {
            annihilateAt(cRow, cCol)
            cCol = 0
            cRow = advanceRow(cRow)
            line = getLine(cRow)
        }

        if (state.modes.isInsertMode) {
            if (line.rawCodepoint(cCol) == TerminalConstants.WIDE_CHAR_SPACER) {
                annihilateAt(cRow, cCol)
            }
            line.insertCells(cCol, widthInCells, state.pen.currentAttr)
        }

        annihilateAt(cRow, cCol)
        if (widthInCells == 2 && cCol + 1 < width) {
            annihilateAt(cRow, cCol + 1)
        }

        writeCell(line, cCol)
        cCol += 1

        if (widthInCells == 2 && cCol < width) {
            line.setCell(cCol, TerminalConstants.WIDE_CHAR_SPACER, state.pen.currentAttr)
            cCol += 1
        }

        if (cCol >= width) {
            state.cursor.col = width - 1
            state.cursor.row = cRow
            state.cursor.pendingWrap = state.modes.isAutoWrap
            return
        }

        state.cursor.col = cCol
        state.cursor.row = cRow
        state.cursor.pendingWrap = false
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

        if (!state.modes.isInsertMode &&
            charWidth != 2 &&
            !state.cursor.pendingWrap &&
            cRow in 0 until height &&
            cCol in 0 until width
        ) {
            val line = getLine(cRow)
            if (line.rawCodepoint(cCol) == TerminalConstants.EMPTY) {
                line.setCell(cCol, codepoint, attr)
                if (cCol == width - 1) {
                    if (state.modes.isAutoWrap) {
                        state.cursor.pendingWrap = true
                    } else {
                        state.cursor.col = cCol
                        state.cursor.pendingWrap = false
                    }
                } else {
                    state.cursor.col = cCol + 1
                    state.cursor.pendingWrap = false
                }
                return
            }
        }

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
     * Returns immediately if the cursor is outside the region.
     * The callback receives the resolved ring indices for the cursor row and bottom margin,
     * plus the clamped number of repetitions.
     */
    private inline fun mutateLines(
        count: Int,
        onMutate: (absCursorRow: Int, absBottom: Int, times: Int) -> Unit
    ) {
        val cRow = state.cursor.row
        val top = state.scrollTop
        val bottom = state.scrollBottom
        if (cRow !in top..bottom) return

        val times = count.coerceAtMost(bottom - cRow + 1)
        if (times <= 0) return

        val absCursorRow = state.resolveRingIndex(cRow)
        val absBottom = state.resolveRingIndex(bottom)
        onMutate(absCursorRow, absBottom, times)
    }

    /**
     * Inserts [count] blank lines at the current cursor row (IL sequence).
     */
    fun insertLines(count: Int) {
        if (count <= 0) return

        structuralMutation {
            mutateLines(count) { absCursorRow, absBottom, times ->
                repeat(times) {
                    state.ring.rotateDown(absCursorRow, absBottom)
                    state.ring[absCursorRow].clear(state.pen.currentAttr)
                }
            }
        }
    }

    /**
     * Deletes [count] lines starting at the current cursor row (DL sequence).
     */
    fun deleteLines(count: Int) {
        if (count <= 0) return

        structuralMutation {
            mutateLines(count) { absCursorRow, absBottom, times ->
                repeat(times) {
                    state.ring.rotateUp(absCursorRow, absBottom)
                    state.ring[absBottom].clear(state.pen.currentAttr)
                }
            }
        }
    }

    fun insertBlankCharacters(count: Int) {
        if (count <= 0) return

        structuralMutation {
            val cRow = state.cursor.row
            val cCol = state.cursor.col
            if (cRow !in 0 until height || cCol !in 0 until width) return@structuralMutation

            val line = getLine(cRow)

            if (line.rawCodepoint(cCol) == TerminalConstants.WIDE_CHAR_SPACER) {
                annihilateAt(cRow, cCol)
            }

            val safeCount = count.coerceAtMost(width - cCol)
            val edgeCol = width - safeCount

            if (edgeCol > cCol && edgeCol < width &&
                line.rawCodepoint(edgeCol) == TerminalConstants.WIDE_CHAR_SPACER
            ) {
                annihilateAt(cRow, edgeCol)
            }

            line.insertCells(cCol, safeCount, state.pen.currentAttr)
        }
    }

    /**
     * Deletes [count] characters starting at the cursor column, shifting the
     * remainder of the line left and filling the vacated cells on the right
     * with blanks using the current pen attribute. The cursor position is not changed.
     */
    fun deleteCharacters(count: Int) {
        if (count <= 0) return

        structuralMutation {
            val cRow = state.cursor.row
            val cCol = state.cursor.col
            if (cRow !in 0 until height || cCol !in 0 until width) return@structuralMutation

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
    }

    private fun eraseLineToEndInternal(cRow: Int, cCol: Int) {
        annihilateAt(cRow, cCol)
        val line = getLine(cRow)
        line.clearFromColumn(cCol, state.pen.currentAttr)
        line.wrapped = false
    }

    fun eraseLineToEnd() = structuralMutation {
        val cRow = state.cursor.row
        val cCol = state.cursor.col
        if (cRow !in 0 until height || cCol !in 0 until width) return@structuralMutation
        eraseLineToEndInternal(cRow, cCol)
    }

    private fun eraseLineToCursorInternal(cRow: Int, cCol: Int) {
        annihilateAt(cRow, cCol)
        getLine(cRow).clearToColumn(cCol, state.pen.currentAttr)
    }

    fun eraseLineToCursor() = structuralMutation {
        val cRow = state.cursor.row
        val cCol = state.cursor.col.coerceAtMost(width - 1)
        if (cRow !in 0 until height || cCol < 0) return@structuralMutation
        eraseLineToCursorInternal(cRow, cCol)
    }

    fun eraseCurrentLine() = structuralMutation {
        val cRow = state.cursor.row
        if (cRow !in 0 until height) return@structuralMutation
        val line = getLine(cRow)
        line.clear(state.pen.currentAttr)
        line.wrapped = false
    }

    /**
     * Erases from the cursor to the end of the visible screen (ED 0).
     * The cursor position is not changed.
     */
    fun eraseScreenToEnd() = structuralMutation {
        val cRow = state.cursor.row
        val cCol = state.cursor.col
        if (cRow !in 0 until height) return@structuralMutation

        if (cCol in 0 until width) {
            eraseLineToEndInternal(cRow, cCol)
        }

        for (row in cRow + 1 until height) {
            getLine(row).clear(state.pen.currentAttr)
        }
    }

    /**
     * Erases from the start of the visible screen through the cursor (ED 1).
     * The cursor position is not changed.
     */
    fun eraseScreenToCursor() = structuralMutation {
        val cRow = state.cursor.row
        if (cRow !in 0 until height) return@structuralMutation

        for (row in 0 until cRow) {
            getLine(row).clear(state.pen.currentAttr)
        }

        val cCol = state.cursor.col.coerceAtMost(width - 1)
        if (cCol >= 0) {
            eraseLineToCursorInternal(cRow, cCol)
        }
    }

    /**
     * Erases the entire visible screen and all scrollback history (xterm ED 3).
     * The cursor position is not changed.
     */
    fun eraseScreenAndHistory() = structuralMutation {
        clearAllHistoryInternal()
    }

    private fun clearViewportInternal() {
        for (row in 0 until height.coerceAtMost(state.ring.size)) {
            getLine(row).clear(state.pen.currentAttr)
        }
    }

    fun clearViewport() = structuralMutation {
        clearViewportInternal()
    }

    private fun clearAllHistoryInternal() {
        state.ring.clear()
        repeat(height) {
            state.ring.push().clear(state.pen.currentAttr)
        }
    }

    fun clearAllHistory() = structuralMutation {
        clearAllHistoryInternal()
    }

    // ----- Cursor movement ---------------------------------------------------

    /**
     * Advances the cursor to the next line, scrolling the active region if needed.
     */
    fun newLine() = structuralMutation {
        state.cursor.row = advanceRow(state.cursor.row)
    }

    /**
     * Moves the cursor up one line (reverse index), scrolling the active region
     * downward if the cursor is already at [state.scrollTop].
     */
    fun reverseLineFeed() = structuralMutation {
        val cRow = state.cursor.row
        if (cRow == state.scrollTop) {
            scrollDownInternal()
        } else {
            state.cursor.row = (cRow - 1).coerceAtLeast(0)
        }
    }
}