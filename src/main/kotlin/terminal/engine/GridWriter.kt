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
     * Writes a codepoint to the grid and manages wide-character physics.
     * * If the write targets or overlaps a wide-character cluster (leader or spacer),
     * the existing cluster is annihilated to prevent orphaned spacers.
     *
     * Fast-path: 1-cell chars into EMPTY cells bypass annihilation checks.
     * Wrap-logic: 2-cell chars at the last column trigger an early wrap.
     * Any write exceeding line width triggers a wrap and potential scroll.
     *
     * @param codepoint The Unicode codepoint to write.
     * @param charWidth Visual width: 2 triggers wide logic; others treated as 1.
     */
    fun printCodepoint(codepoint: Int, charWidth: Int) {
        var cCol = state.cursor.col
        var cRow = state.cursor.row
        val attr = state.pen.currentAttr
        val widthInCells = if (charWidth == 2) 2 else 1

        if (cRow !in 0 until height || cCol !in 0 until width) return

        var line = getLine(cRow)

        // Hot path: appending a narrow character into an empty cell.
        // (If cCol is a WIDE_CHAR_SPACER, it is NOT empty, so it falls to the slow path)
        if (widthInCells == 1 && line.getCodepoint(cCol) == TerminalConstants.EMPTY) {
            line.setCell(cCol, codepoint, attr)
            if (cCol == width - 1) {
                line.wrapped = true
                cCol = 0
                cRow++
                if (cRow >= height) {
                    scrollUp()
                    cRow = height - 1
                }
            } else {
                cCol++
            }

            state.cursor.col = cCol
            state.cursor.row = cRow
            return
        }

        // Width=2 at last column cannot fit: clear landing cell and wrap first.
        if (widthInCells == 2 && cCol == width - 1) {
            annihilateAt(cRow, cCol)
            cCol = 0
            cRow++
            if (cRow >= height) {
                scrollUp()
                cRow = height - 1
            }
            line = getLine(cRow)
        }

        // Slow path: overwrite physics and wide-neighbor annihilation.
        annihilateAt(cRow, cCol)
        if (widthInCells == 2 && cCol + 1 < width) {
            annihilateAt(cRow, cCol + 1)
        }

        // Now write EXACTLY at cCol
        val targetLine = line
        targetLine.setCell(cCol, codepoint, attr)
        cCol += 1

        if (widthInCells == 2) {
            if (cCol < width) {
                targetLine.setCell(cCol, TerminalConstants.WIDE_CHAR_SPACER, attr)
            }
            cCol += 1
        }

        if (cCol >= width) {
            targetLine.wrapped = true
            cCol = 0
            cRow++
            if (cRow >= height) {
                scrollUp()
                cRow = height - 1
            }
        }

        state.cursor.col = cCol
        state.cursor.row = cRow
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

    fun newLine() {
        state.cursor.row++
        if (state.cursor.row >= state.dimensions.height) {
            state.cursor.row = state.dimensions.height - 1
            scrollUp()
        }
    }
}
