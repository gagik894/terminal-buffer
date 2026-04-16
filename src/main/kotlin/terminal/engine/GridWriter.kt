package com.gagik.terminal.engine

import com.gagik.terminal.model.Line
import com.gagik.terminal.model.TerminalConstants
import com.gagik.terminal.state.TerminalState

/**
 * Dedicated mutation engine for grid writes and line-level erase/edit operations.
 *
 * This class owns overwrite physics so callers cannot leave orphaned wide spacers.
 *
 * @param state Shared terminal state for dimensions, cursor, pen attributes, and ring storage.
 */
internal class GridWriter(
    private val state: TerminalState
) {
    // width and height are cached for performance
    private val width: Int get() = state.dimensions.width
    private val height: Int get() = state.dimensions.height

    /**
     * Resolves a visible viewport row to its backing line in the ring.
     *
     * @param row Viewport row (0-based).
     * @return Mutable line for the given viewport row.
     */
    private fun getLine(row: Int): Line {
        val startIndex = state.ring.size - height
        return state.ring[startIndex + row]
    }

    /**
     * Pushes one new blank line and scrolls the viewport up by one row.
     */
    fun scrollUp() {
        state.ring.push().clear(state.pen.currentAttr)
    }

    /**
     * Returns the canonical owner cell for the cluster that covers [col].
     * For now, clusters are either 1-cell codepoints or 2-cell wide leaders + spacer.
     *
     * @param line Target line.
     * @param col Column within [line] to resolve.
     * @return Cluster owner column; for spacer cells this is the wide leader column.
     */
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

    /**
     * Core write engine shared by [printCodepoint] and [printCluster].
     *
     * Executes edge-wrap, annihilation, the caller-supplied write, spacer placement,
     * standard-wrap, and scroll — then commits the new cursor position.
     *
     * @param charWidth  Visual cell width (1 or 2).
     * @param writeCell  Lambda that writes the leader cell at column [col] on [line].
     *                   Called exactly once, after annihilation and edge-wrap are resolved.
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
            cRow++
            if (cRow >= height) { scrollUp(); cRow = height - 1 }
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
            cRow++
            if (cRow >= height) { scrollUp(); cRow = height - 1 }
        }

        state.cursor.col = cCol
        state.cursor.row = cRow
    }

    /**
     * Writes a Unicode codepoint to the grid and manages wide-character physics.
     *
     * ARCHITECTURAL WARNING:
     * Do NOT split this method into smaller `clear()`, `write()`, and `move()` helpers.
     * Terminal writing is not linear. Handling the "Edge Wrap" requires interleaving
     * clears and moves. Splitting this method will destroy the JIT-optimized fast path
     * and introduce severe state-mutation overhead.
     *
     * --- Overwrite Physics ---
     * If the write targets or overlaps an existing wide-character cluster (leader or spacer),
     * the entire existing cluster is annihilated (set to EMPTY) before the write occurs.
     * This guarantees that no orphaned halves or "ghost" emojis remain on the grid.
     *
     * --- Fast-Path Optimization ---
     * 99% of terminal output is 1-cell ASCII appended to empty space. If the target
     * cell is EMPTY and the incoming char is width = 1, the engine bypasses all collision
     * checks, writes directly.
     *
     * --- Wrap Logic ---
     * - Edge Wrap: A 2-cell char at the final column cannot fit. The final column is
     * cleared, the cursor wraps, and the character is printed on the next line.
     * - Standard Wrap: Any write that exceeds the line width triggers a soft wrap.
     * - Bottom Wrap: Wrapping past the bottom row triggers a History Ring allocation (scrollUp).
     *
     * @param codepoint The Unicode codepoint to write.
     * @param charWidth The visual width of the character. A value of 2 triggers wide
     * character logic; all other values are treated as 1.
     */
    fun printCodepoint(codepoint: Int, charWidth: Int) {
        val attr = state.pen.currentAttr

        // Fast path: 1-cell write into empty space.
        val cCol = state.cursor.col
        val cRow = state.cursor.row
        if (charWidth != 2 && cRow in 0 until height && cCol in 0 until width) {
            val line = getLine(cRow)
            if (line.getCodepoint(cCol) == TerminalConstants.EMPTY) {
                line.setCell(cCol, codepoint, attr)
                if (cCol == width - 1) {
                    line.wrapped = true
                    state.cursor.col = 0
                    state.cursor.row = cRow + 1
                    if (state.cursor.row >= height) { scrollUp(); state.cursor.row = height - 1 }
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

    /**
     * Inserts blank cells at the current cursor column on the active row.
     *
     * If the cursor is on a wide spacer, the owning wide cluster is annihilated first
     * to avoid leaving an orphaned spacer before the block shift happens.
     *
     * @param count Number of blank cells to insert. Non-positive values are ignored.
     */
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
     * Erases from the cursor to the end of the active row (EL 0 semantics).
     *
     * The current cell is annihilated first so clearing from a spacer also clears
     * its wide-character leader.
     */
    fun eraseLineToEnd() {
        val cRow = state.cursor.row
        val cCol = state.cursor.col
        if (cRow !in 0 until height || cCol !in 0 until width) return

        annihilateAt(cRow, cCol)
        getLine(cRow).clearFromColumn(cCol, state.pen.currentAttr)
    }

    /**
     * Erases from the start of the active row through the cursor (EL 1 semantics).
     *
     * The current cell is annihilated first so clearing through a spacer also clears
     * its wide-character leader.
     */
    fun eraseLineToCursor() {
        val cRow = state.cursor.row
        val cCol = state.cursor.col
        if (cRow !in 0 until height || cCol !in 0 until width) return

        annihilateAt(cRow, cCol)
        getLine(cRow).clearToColumn(cCol, state.pen.currentAttr)
    }

    /**
     * Erases the entire active row (EL 2 semantics) using the current pen attribute
     * as the fill attribute for cleared cells.
     */
    fun eraseCurrentLine() {
        val cRow = state.cursor.row
        if (cRow !in 0 until height) return

        getLine(cRow).clear(state.pen.currentAttr)
    }

    /**
     * Clears all currently visible lines in the viewport.
     */
    fun clearViewport() {
        val lineCount = height.coerceAtMost(state.ring.size)
        for (row in 0 until lineCount) {
            getLine(row).clear(state.pen.currentAttr)
        }
    }

    /**
     * Nukes the entire history ring and repopulates the viewport with blank lines.
     */
    fun clearAllHistory() {
        state.ring.clear()
        repeat(height) {
            state.ring.push().clear(state.pen.currentAttr)
        }
    }

    /**
     * Advances the cursor to the next line. If the cursor is on the last line,
     * the viewport scrolls up by one row.
     */
    fun newLine() {
        state.cursor.row++
        if (state.cursor.row >= state.dimensions.height) {
            state.cursor.row = state.dimensions.height - 1
            scrollUp()
        }
    }
}
