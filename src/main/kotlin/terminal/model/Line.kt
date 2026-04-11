package com.gagik.terminal.model

import com.gagik.terminal.util.Validations.requirePositive

/**
 * A single visual terminal line of fixed width.
 *
 * Storage is "packed":
 * Each `i` in the range [0, width-1] corresponds to a cell on the line.
 * - codepoints[i] == 0 means empty cell (renders as space)
 * - attrs[i] is a packed Int produced by AttributeCodec
 *
 * wrapped=true means this line is a soft continuation of the previous line
 * caused by wrapping at the terminal width.
 *
 * @param width The fixed width of the line in cells. Must be > 0.
 * @throws IllegalArgumentException if width is not greater than 0
 */
internal class Line(
    val width: Int
) {
    init {
        requirePositive(width, "width")
    }

    // The codepoints for each cell in the line. 0 means empty cell.
    private val codepoints: IntArray = IntArray(width) { 0 }
    // The attributes for each cell in the line, packed into ints.
    private val attrs: IntArray = IntArray(width)
    // Whether this line is a soft-wrapped continuation of the previous line.
    var wrapped: Boolean = false

    /**
     * Clears the line by resetting all codepoints to 0 and all attributes to defaultCellAttr.
     * Also sets wrapped to false.
     * @param defaultCellAttr The attribute to set for all cells after clearing.
     */
    fun clear(defaultCellAttr: Int) {
        codepoints.fill(0)
        attrs.fill(defaultCellAttr)
        wrapped = false
    }

    /**
     * Sets the cell at the specified column to the given codepoint and attribute.
     *
     * @param col The column index of the cell to set
     * @param codepoint The Unicode codepoint to set in the cell
     * @param attr The packed attribute Int to set for the cell
     *
     * @throws IndexOutOfBoundsException if col is out of bounds
     */
    fun setCell(col: Int, codepoint: Int, attr: Int) {
        codepoints[col] = codepoint
        attrs[col] = attr
    }

    /**
     * Gets the codepoint of the cell at the specified column.
     *
     * @param col The column index of the cell to query
     * @return The Unicode codepoint at the specified column
     *
     * @throws IndexOutOfBoundsException if col is out of bounds
     */
    fun getCodepoint(col: Int): Int = codepoints[col]


    /**
     * Gets the attribute of the cell at the specified column.
     *
     * @param col The column index of the cell to query
     * @return The packed attribute Int at the specified column
     *
     * @throws IndexOutOfBoundsException if col is out of bounds
     */
    fun getAttr(col: Int): Int = attrs[col]

    /**
     * Clears cells from the specified column to the end of the line.
     * @param startCol The starting column (inclusive)
     * @param attr The attribute to fill cleared cells with
     *
     * @throws IndexOutOfBoundsException if startCol is out of bounds
     */
    fun clearFromColumn(startCol: Int, attr: Int) {
        val start = startCol.coerceAtLeast(0)
        if (start >= width) return

        // Native block zeroing
        codepoints.fill(0, start, width)
        attrs.fill(attr, start, width)
    }

    /**
     * Clears cells from the beginning of the line to the specified column.
     * @param endCol The ending column (inclusive)
     * @param attr The attribute to fill cleared cells with
     *
     * @throws IndexOutOfBoundsException if endCol is out of bounds
     */
    fun clearToColumn(endCol: Int, attr: Int) {
        val end = (endCol + 1).coerceAtMost(width)
        if (end <= 0) return

        // Native block zeroing
        codepoints.fill(0, 0, end)
        attrs.fill(attr, 0, end)
    }

    /**
     * Copies the contents of another line into this line.
     * Both lines must have the same width.
     *
     * @param other The line to copy from
     * @throws IllegalArgumentException if widths don't match
     */
    fun copyFrom(other: Line) {
        require(other.width == width) { "width mismatch: this=$width, other=${other.width}" }
        System.arraycopy(other.codepoints, 0, codepoints, 0, width)
        System.arraycopy(other.attrs, 0, attrs, 0, width)
        this.wrapped = other.wrapped
    }

    /**
     * Fills the entire line with the specified character and attribute.
     *
     * @param codepoint Unicode codepoint to fill with (0 for empty/space)
     * @param attr The attribute to set for all cells
     */
    fun fill(codepoint: Int, attr: Int) {
        codepoints.fill(codepoint)
        attrs.fill(attr)
    }


    /**
     * Converts the line content to a string.
     * Empty cells (codepoint 0) are rendered as spaces.
     * Trailing spaces are preserved.
     *
     * @return String representation of the line
     */
    fun toText(): String {
        val sb = StringBuilder(width)
        for (cp in codepoints) {
            if (cp == 0) {
                sb.append(' ')
            } else {
                sb.appendCodePoint(cp)
            }
        }
        return sb.toString()
    }

    /**
     * Converts the line content to a string, trimming trailing spaces.
     *
     * @return String representation of the line with trailing spaces removed
     */
    fun toTextTrimmed(): String {
        var lastValidCol = width - 1

        // Scan backwards to find the last non-empty cell
        while (lastValidCol >= 0 && codepoints[lastValidCol] == 0) {
            lastValidCol--
        }

        // If the line is completely empty, return instantly without allocating
        if (lastValidCol < 0) return ""

        val sb = StringBuilder(lastValidCol + 1)
        for (col in 0..lastValidCol) {
            val cp = codepoints[col]
            if (cp == 0) {
                sb.append(' ')
            } else {
                sb.appendCodePoint(cp)
            }
        }
        return sb.toString()
    }


    /**
     * Inserts [count] blank cells at [col], shifting the remaining cells to the right.
     * Cells shifted beyond the line width are discarded.
     * Uses native block memory operations for zero-allocation performance.
     */
    fun insertCells(col: Int, count: Int, defaultAttr: Int) {
        if (col !in 0..<width || count <= 0) return

        val shiftCount = width - col - count
        if (shiftCount > 0) {
            // Native block shift to the right
            System.arraycopy(codepoints, col, codepoints, col + count, shiftCount)
            System.arraycopy(attrs, col, attrs, col + count, shiftCount)
        }

        // Fill the newly opened space with blanks
        val endFill = (col + count).coerceAtMost(width)
        codepoints.fill(0, col, endFill)
        attrs.fill(defaultAttr, col, endFill)
    }
}