package com.gagik.terminal.engine

import com.gagik.terminal.model.Line
import com.gagik.terminal.model.TerminalConstants
import com.gagik.terminal.state.TerminalState

/**
 * Dedicated mutation engine for grid writes and line-level erase/edit operations.
 *
 * This class owns overwrite physics so callers cannot leave orphaned wide spacers.
 */
internal class GridWriter(
    private val state: TerminalState
) {
    private val width: Int get() = state.dimensions.width
    private val height: Int get() = state.dimensions.height

    private fun getLine(row: Int): Line {
        val startIndex = state.ring.size - height
        return state.ring[startIndex + row]
    }

    private fun scrollUp() {
        state.ring.push().clear(state.pen.currentAttr)
    }

    /**
     * Returns the canonical owner cell for the cluster that covers [col].
     * For now, clusters are either 1-cell codepoints or 2-cell wide leaders + spacer.
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

    /** Clears the full cluster that occupies [col]. */
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
     * Writes one codepoint with width-aware cursor movement.
     * The ASCII append fast-path avoids extra physics checks in the common case.
     */
    fun printCodepoint(codepoint: Int, charWidth: Int) {
        var cCol = state.cursor.col
        var cRow = state.cursor.row
        val attr = state.pen.currentAttr
        val widthInCells = if (charWidth == 2) 2 else 1

        if (cRow !in 0 until height || cCol !in 0 until width) return

        var line = getLine(cRow)
        cCol = findClusterStart(line, cCol)

        // Hot path: appending a narrow character into an empty cell.
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
}

