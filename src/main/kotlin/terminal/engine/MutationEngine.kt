package com.gagik.terminal.engine

import com.gagik.terminal.buffer.HistoryRing
import com.gagik.terminal.model.Line
import com.gagik.terminal.model.TerminalConstants
import com.gagik.terminal.state.TerminalState
import com.gagik.terminal.store.ClusterStore

/**
 * Dedicated mutation engine for grid writes and line-level erase/edit operations.
 *
 * Owns all overwrite physics so callers cannot leave orphaned wide-character
 * spacers or partially overwritten clusters.
 *
 * Responsibilities:
 * - printable writes, including deferred-wrap handling
 * - line and screen erase operations
 * - scroll-region mutations (`IL`, `DL`, `SU`, `SD`, `RI`)
 * - cell-shift edits (`ICH`, `DCH`)
 *
 * Non-responsibilities:
 * - grapheme segmentation over a byte/codepoint stream
 * - cursor-addressing policy
 * - direct circular-buffer arithmetic outside [HistoryRing]
 *
 * All ring-index translation is delegated to [TerminalState.resolveRingIndex].
 * All circular-buffer arithmetic is encapsulated inside [HistoryRing].
 */
internal class MutationEngine(
    private val state: TerminalState
) {
    private val width: Int get() = state.dimensions.width
    private val height: Int get() = state.dimensions.height
    private val leftMargin: Int get() = state.effectiveLeftMargin
    private val rightMargin: Int get() = state.effectiveRightMargin
    private var clusterScratch = IntArray(16)

    /**
     * Wraps mutations that conceptually break phantom-column state.
     *
     * Deferred wrap survives only until the next printable write. Structural
     * edits and explicit scroll operations must cancel it first.
     */
    private inline fun structuralMutation(block: () -> Unit) {
        state.cancelPendingWrap()
        block()
    }

    /**
     * Returns the mutable [Line] for a viewport row.
     *
     * This is the single source of truth for viewport-to-ring translation.
     */
    private fun getLine(row: Int): Line = state.ring[state.resolveRingIndex(row)]

    /**
     * Internal scroll-up primitive that deliberately does not cancel pending wrap.
     *
     * This helper is used both from public structural commands and from
     * printable-write flow via [advanceRow]. The caller is responsible for
     * deciding whether pending-wrap state must survive.
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
     * Internal scroll-down primitive that deliberately does not cancel pending wrap.
     *
     * See [scrollUpInternal] for the rationale: this helper is shared by public
     * commands and internal row-advance logic.
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

    /** Scrolls the active region upward by [count] lines. */
    fun scrollUp(count: Int = 1) = structuralMutation {
        scrollUpInternal(count)
    }

    /** Scrolls the active region downward by [count] lines. */
    fun scrollDown(count: Int = 1) = structuralMutation {
        scrollDownInternal(count)
    }

    /**
     * Advances one viewport row, scrolling only when the cursor is exactly on
     * the active bottom margin.
     *
     * A cursor outside a restricted scroll region never triggers a scroll here,
     * which matches VT behavior for writes below the margins.
     */
    private fun advanceRow(row: Int): Int {
        return if (row == state.scrollBottom) {
            scrollUpInternal()
            state.scrollBottom
        } else {
            (row + 1).coerceAtMost(height - 1)
        }
    }

    /**
     * Resolves the canonical owner column for [col].
     *
     * If [col] points at a wide spacer, the owner is the preceding leader cell.
     * Ordinary cells resolve to themselves.
     */
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
     * Clears the full visual occupant that owns `[row, col]`.
     *
     * If [col] lands on a wide spacer, this method walks back to the leader so
     * overwrite operations never leave an orphan spacer behind.
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

    /**
     * Deep-copies a horizontal slice from [src] to [dest].
     *
     * This helper is intentionally used only for rectangular LR-margin edits.
     * It preserves cluster ownership correctly by copying cluster payloads into
     * fresh slots before the source cells are later cleared or overwritten.
     */
    private fun copySlice(src: Line, dest: Line, left: Int, right: Int) {
        for (col in left..right) {
            val raw = src.rawCodepoint(col)
            val attr = src.getPackedAttr(col)
            if (raw <= TerminalConstants.CLUSTER_HANDLE_MAX) {
                val cpLen = src.store.length(raw)
                if (clusterScratch.size < cpLen) {
                    clusterScratch = IntArray(cpLen)
                }
                src.store.readInto(raw, clusterScratch, 0)
                dest.setCluster(col, clusterScratch, cpLen, attr)
            } else {
                dest.setCell(col, raw, attr)
            }
        }
    }

    /**
     * Core write engine shared by [printCodepoint] and [printCluster].
     *
     * Ordering matters here: deferred wrap, annihilation, insert-mode shifting,
     * payload write, spacer placement, and cursor commit are intentionally
     * interleaved. Keep this hot path monolithic unless profiling shows a real
     * improvement from refactoring.
     */
    private inline fun writeToGrid(charWidth: Int, crossinline writeCell: (line: Line, col: Int) -> Unit) {
        var cCol = state.cursor.col
        var cRow = state.cursor.row
        val widthInCells = if (charWidth == 2) 2 else 1

        if (cRow !in 0 until height || cCol !in 0 until width) return
        if (cCol !in leftMargin..rightMargin) return

        if (widthInCells == 2 && cCol >= rightMargin && !state.modes.isAutoWrap) {
            return
        }

        var line = getLine(cRow)

        if (state.cursor.pendingWrap) {
            state.cursor.pendingWrap = false
            line.wrapped = true
            cCol = leftMargin
            cRow = advanceRow(cRow)
            line = getLine(cRow)
        }

        if (widthInCells == 2 && cCol >= rightMargin) {
            annihilateAt(cRow, cCol)
            cCol = leftMargin
            cRow = advanceRow(cRow)
            line = getLine(cRow)
        }

        if (state.modes.isInsertMode) {
            if (line.rawCodepoint(cCol) == TerminalConstants.WIDE_CHAR_SPACER) {
                annihilateAt(cRow, cCol)
            }
            line.insertCellsInRange(cCol, widthInCells, rightMargin, state.pen.currentAttr)
        }

        annihilateAt(cRow, cCol)
        if (widthInCells == 2 && cCol + 1 <= rightMargin) {
            annihilateAt(cRow, cCol + 1)
        }

        writeCell(line, cCol)
        cCol += 1

        if (widthInCells == 2 && cCol < width) {
            line.setCell(cCol, TerminalConstants.WIDE_CHAR_SPACER, state.pen.currentAttr)
            cCol += 1
        }

        if (cCol > rightMargin) {
            state.cursor.col = rightMargin
            state.cursor.row = cRow
            state.cursor.pendingWrap = state.modes.isAutoWrap
            return
        }

        state.cursor.col = cCol
        state.cursor.row = cRow
        state.cursor.pendingWrap = false
    }

    /**
     * Writes one scalar codepoint using the active pen and width supplied by the caller.
     *
     * Fast path: width-1 overwrite into an empty cell with no pending wrap and
     * no insert mode. Slow path: delegates to [writeToGrid] for full cell physics.
     */
    fun printCodepoint(codepoint: Int, charWidth: Int) {
        val attr = state.pen.currentAttr
        val cCol = state.cursor.col
        val cRow = state.cursor.row

        if (!state.modes.isInsertMode &&
            charWidth != 2 &&
            !state.cursor.pendingWrap &&
            cRow in 0 until height &&
            cCol in leftMargin..rightMargin
        ) {
            val line = getLine(cRow)
            if (line.rawCodepoint(cCol) == TerminalConstants.EMPTY) {
                line.setCell(cCol, codepoint, attr)
                if (cCol == rightMargin) {
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

    /**
     * Writes one pre-segmented grapheme cluster.
     *
     * Single-codepoint clusters fall back to [printCodepoint] so the scalar fast
     * path remains available.
     */
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

    /**
     * Applies a vertical line mutation within the active scroll region.
     *
     * Returns immediately when the cursor is outside the region. The callback
     * receives resolved ring indices and a count already clamped to the region.
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

    /** Inserts [count] blank lines at the cursor row within the active scroll region. */
    fun insertLines(count: Int) {
        if (count <= 0) return

        structuralMutation {
            mutateLines(count) { absCursorRow, absBottom, times ->
                if (!state.modes.isLeftRightMarginMode) {
                    repeat(times) {
                        state.ring.rotateDown(absCursorRow, absBottom)
                        state.ring[absCursorRow].clear(state.pen.currentAttr)
                    }
                    return@mutateLines
                }

                val topRow = state.cursor.row
                val bottomRow = state.scrollBottom
                for (row in bottomRow downTo topRow + times) {
                    copySlice(getLine(row - times), getLine(row), leftMargin, rightMargin)
                    getLine(row).wrapped = false
                }
                for (row in topRow until topRow + times) {
                    val line = getLine(row)
                    line.clearRange(leftMargin, rightMargin + 1, state.pen.currentAttr)
                    line.wrapped = false
                }
            }
        }
    }

    /** Deletes [count] lines at the cursor row within the active scroll region. */
    fun deleteLines(count: Int) {
        if (count <= 0) return

        structuralMutation {
            mutateLines(count) { absCursorRow, absBottom, times ->
                if (!state.modes.isLeftRightMarginMode) {
                    repeat(times) {
                        state.ring.rotateUp(absCursorRow, absBottom)
                        state.ring[absBottom].clear(state.pen.currentAttr)
                    }
                    return@mutateLines
                }

                val topRow = state.cursor.row
                val bottomRow = state.scrollBottom
                for (row in topRow..bottomRow - times) {
                    copySlice(getLine(row + times), getLine(row), leftMargin, rightMargin)
                    getLine(row).wrapped = false
                }
                for (row in bottomRow - times + 1..bottomRow) {
                    val line = getLine(row)
                    line.clearRange(leftMargin, rightMargin + 1, state.pen.currentAttr)
                    line.wrapped = false
                }
            }
        }
    }

    /**
     * Inserts [count] blank cells at the cursor column, shifting the remainder
     * of the line right and discarding cells pushed past the right margin.
     */
    fun insertBlankCharacters(count: Int) {
        if (count <= 0) return

        structuralMutation {
            val cRow = state.cursor.row
            val cCol = state.cursor.col
            if (cRow !in 0 until height || cCol !in 0 until width) return@structuralMutation
            if (cCol !in leftMargin..rightMargin) return@structuralMutation

            val line = getLine(cRow)

            if (line.rawCodepoint(cCol) == TerminalConstants.WIDE_CHAR_SPACER) {
                annihilateAt(cRow, cCol)
            }

            val safeCount = count.coerceAtMost(rightMargin - cCol + 1)
            val edgeCol = rightMargin - safeCount + 1

            if (edgeCol > cCol && edgeCol <= rightMargin &&
                line.rawCodepoint(edgeCol) == TerminalConstants.WIDE_CHAR_SPACER
            ) {
                annihilateAt(cRow, edgeCol)
            }

            line.insertCellsInRange(cCol, safeCount, rightMargin, state.pen.currentAttr)
        }
    }

    /**
     * Deletes [count] cells at the cursor column, shifting remaining content
     * left and blank-filling the vacated right edge with the current pen attr.
     */
    fun deleteCharacters(count: Int) {
        if (count <= 0) return

        structuralMutation {
            val cRow = state.cursor.row
            val cCol = state.cursor.col
            if (cRow !in 0 until height || cCol !in 0 until width) return@structuralMutation
            if (cCol !in leftMargin..rightMargin) return@structuralMutation

            val safeCount = count.coerceAtMost(rightMargin - cCol + 1)

            annihilateAt(cRow, cCol)

            if (safeCount < rightMargin - cCol + 1) {
                val rightEdge = cCol + safeCount
                if (rightEdge <= rightMargin &&
                    getLine(cRow).rawCodepoint(rightEdge) == TerminalConstants.WIDE_CHAR_SPACER
                ) {
                    annihilateAt(cRow, rightEdge)
                }
            }

            getLine(cRow).deleteCellsInRange(cCol, safeCount, rightMargin, state.pen.currentAttr)
        }
    }

    private fun eraseLineToEndInternal(cRow: Int, cCol: Int) {
        val line = getLine(cRow)
        if (state.modes.isLeftRightMarginMode) {
            if (cCol !in leftMargin..rightMargin) {
                line.wrapped = false
                return
            }
            val start = maxOf(cCol, leftMargin)
            if (start <= rightMargin) {
                annihilateAt(cRow, start)
                line.clearRange(start, rightMargin + 1, state.pen.currentAttr)
            }
        } else {
            annihilateAt(cRow, cCol)
            line.clearFromColumn(cCol, state.pen.currentAttr)
        }
        line.wrapped = false
    }

    /** Erases from the cursor through the end of the current line (EL 0). */
    fun eraseLineToEnd() = structuralMutation {
        val cRow = state.cursor.row
        val cCol = state.cursor.col
        if (cRow !in 0 until height || cCol !in 0 until width) return@structuralMutation
        eraseLineToEndInternal(cRow, cCol)
    }

    private fun eraseLineToCursorInternal(cRow: Int, cCol: Int) {
        val line = getLine(cRow)
        if (state.modes.isLeftRightMarginMode) {
            if (cCol !in leftMargin..rightMargin) return
            val end = minOf(cCol, rightMargin)
            if (end >= leftMargin) {
                annihilateAt(cRow, end)
                line.clearRange(leftMargin, end + 1, state.pen.currentAttr)
            }
        } else {
            annihilateAt(cRow, cCol)
            line.clearToColumn(cCol, state.pen.currentAttr)
        }
    }

    /** Erases from the start of the line through the cursor (EL 1). */
    fun eraseLineToCursor() = structuralMutation {
        val cRow = state.cursor.row
        val cCol = state.cursor.col.coerceAtMost(width - 1)
        if (cRow !in 0 until height || cCol < 0) return@structuralMutation
        eraseLineToCursorInternal(cRow, cCol)
    }

    /** Erases the entire current line without moving the cursor (EL 2). */
    fun eraseCurrentLine() = structuralMutation {
        val cRow = state.cursor.row
        if (cRow !in 0 until height) return@structuralMutation
        val line = getLine(cRow)
        if (state.modes.isLeftRightMarginMode) {
            line.clearRange(leftMargin, rightMargin + 1, state.pen.currentAttr)
        } else {
            line.clear(state.pen.currentAttr)
        }
        line.wrapped = false
    }

    /** Erases from the cursor through the end of the visible screen (ED 0). */
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

    /** Erases from the start of the visible screen through the cursor (ED 1). */
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
     * Clears scrollback history while preserving the current visible viewport (ED 3).
     *
     * Visible rows are deep-copied into a fresh ring/store pair so dropped
     * history lines release any cluster payloads they owned.
     */
    fun eraseScreenAndHistory() = structuralMutation {
        val buffer = state.activeBuffer
        val sourceStore = buffer.store
        val newStore = ClusterStore()
        val newRing = HistoryRing(buffer.maxHistory + height) { Line(width, newStore) }
        val visibleTop = (buffer.ring.size - height).coerceAtLeast(0)
        var clusterBuf = IntArray(16)

        for (row in 0 until height) {
            val srcLine = buffer.ring[visibleTop + row]
            val destLine = newRing.push()
            for (col in 0 until width) {
                val raw = srcLine.rawCodepoint(col)
                val attr = srcLine.getPackedAttr(col)
                if (raw <= TerminalConstants.CLUSTER_HANDLE_MAX) {
                    val cpLen = sourceStore.length(raw)
                    if (clusterBuf.size < cpLen) {
                        clusterBuf = IntArray(cpLen)
                    }
                    sourceStore.readInto(raw, clusterBuf, 0)
                    destLine.setCluster(col, clusterBuf, cpLen, attr)
                } else {
                    destLine.setRawCell(col, raw, attr)
                }
            }
            destLine.wrapped = srcLine.wrapped
        }

        buffer.store = newStore
        buffer.ring = newRing
    }

    private fun clearViewportInternal() {
        for (row in 0 until height.coerceAtMost(state.ring.size)) {
            getLine(row).clear(state.pen.currentAttr)
        }
    }

    /** Clears the visible viewport without touching scrollback history. */
    fun clearViewport() = structuralMutation {
        clearViewportInternal()
    }

    /** Clears both the viewport and retained history on the active buffer. */
    private fun clearAllHistoryInternal() {
        state.activeBuffer.clearGrid(state.pen.currentAttr, height)
    }

    /** Clears the active buffer's viewport and scrollback history. */
    fun clearAllHistory() = structuralMutation {
        clearAllHistoryInternal()
    }

    /** Executes a line feed relative to the active scroll region. */
    fun newLine() = structuralMutation {
        state.cursor.row = advanceRow(state.cursor.row)
    }

    /** Executes reverse index relative to the active scroll region. */
    fun reverseLineFeed() = structuralMutation {
        val cRow = state.cursor.row
        if (cRow == state.scrollTop) {
            scrollDownInternal()
        } else {
            state.cursor.row = (cRow - 1).coerceAtLeast(0)
        }
    }
}
