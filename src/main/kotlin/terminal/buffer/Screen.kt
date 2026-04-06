package com.gagik.terminal.buffer

import com.gagik.terminal.model.GridDimensions
import com.gagik.terminal.model.Line

/**
 * A lightweight viewport ("lens") over the HistoryRing.
 *
 * The screen is always a view of the last [height] lines in the ring.
 *
 * Responsibilities:
 * - Maps visual row (0..height-1) to ring index
 * - Provides write access at screen coordinates
 * - Handles scrolling via ring.push()
 *
 * @param ring The HistoryRing that stores all lines (history + visible)
 * @param dimensions The dimensions of the screen (height, width)
 * @throws IllegalArgumentException if height or width are not greater than 0
 */
internal class Screen(
    private val ring: HistoryRing,
    private val dimensions: GridDimensions
) {

    /**
     * Gets the Line at the specified screen row.
     * Maps visual row to the absolute ring index.
     *
     * @param row Visual row (0 = top of screen, height-1 = bottom)
     * @return The Line at that screen position
     * @throws IllegalArgumentException if row is out of bounds
     */
    fun getLine(row: Int): Line {
        dimensions.requireValidRow(row)

        // Screen is the last [height] lines of the ring
        val startIndex = (ring.size - dimensions.height).coerceAtLeast(0)
        return ring[startIndex + row]
    }

    /**
     * Writes a character to a specific screen location.
     * Out-of-bounds writes are ignored.
     *
     * @param row Screen row (0-based)
     * @param col Screen column (0-based)
     * @param codepoint Unicode codepoint to write
     * @param attr Packed attribute value
     */
    fun write(row: Int, col: Int, codepoint: Int, attr: Int) {
        if (dimensions.isValidRow(row) && dimensions.isValidCol(col)) {
            getLine(row).setCell(col, codepoint, attr)
        }
    }

    /**
     * Scrolls the view up by pushing a new blank line to the ring.
     * The new line becomes the new bottom line of the screen.
     * The top line of the screen is effectively scrolled off and becomes part of the history.
     * The new line is cleared with the specified fill attribute.
     *
     * @param fillAttr Attribute for the new blank line
     */
    fun scrollUp(fillAttr: Int) {
        val newLine = ring.push()
        newLine.clear(fillAttr)
    }

    /**
     * Clears all visible screen lines.
     * History lines are not affected.
     *
     * @param fillAttr Attribute to fill cleared cells with
     */
    fun clear(fillAttr: Int) {
        val startIndex = (ring.size - dimensions.height).coerceAtLeast(0)
        for (i in 0 until dimensions.height.coerceAtMost(ring.size)) {
            ring[startIndex + i].clear(fillAttr)
        }
    }

    /**
     * Clears from a specific position to the end of the screen.
     * Clears from (row, col) to the bottom-right corner.
     *
     * @param row Starting row (inclusive)
     * @param col Starting column (inclusive) on the starting row
     * @param fillAttr Attribute to fill cleared cells with
     */
    fun clearFromPosition(row: Int, col: Int, fillAttr: Int) {
        if (!dimensions.isValidRow(row)) return

        getLine(row).clearFromColumn(col, fillAttr)

        for (r in (row + 1) until dimensions.height) {
            getLine(r).clear(fillAttr)
        }
    }

    /**
     * Clears from the beginning of the screen to a specific position.
     * Clears from the top-left corner to (row, col).
     *
     * @param row Ending row (inclusive)
     * @param col Ending column (inclusive) on the ending row
     * @param fillAttr Attribute to fill cleared cells with
     */
    fun clearToPosition(row: Int, col: Int, fillAttr: Int) {
        if (!dimensions.isValidRow(row)) return

        for (r in 0 until row) {
            getLine(r).clear(fillAttr)
        }

        getLine(row).clearToColumn(col, fillAttr)
    }

    /**
     * Gets the visible screen content as a string.
     * Lines are separated by newlines.
     * Trailing spaces on each line are trimmed.
     *
     * @return String representation of the visible screen
     */
    fun toText(): String {
        val sb = StringBuilder()
        val lineCount = dimensions.height.coerceAtMost(ring.size)
        for (row in 0 until lineCount) {
            if (row > 0) sb.append('\n')
            sb.append(getLine(row).toTextTrimmed())
        }
        return sb.toString()
    }
}