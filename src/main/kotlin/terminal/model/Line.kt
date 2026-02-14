package com.gagik.terminal.model

import com.gagik.terminal.util.Validations.isInBounds
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
class Line(
    val width: Int
) {
    init {
        requirePositive(width, "width")
    }

    // The codepoints for each cell in the line. 0 means empty cell.
    val codepoints: IntArray = IntArray(width) { 0 }
    // The attributes for each cell in the line, packed into ints.
    val attrs: IntArray = IntArray(width)
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
     * If col is out of bounds, the method does nothing.
     * @param col The column index of the cell to set
     * @param codepoint The Unicode codepoint to set in the cell
     * @param attr The packed attribute Int to set for the cell
     */
    fun setCell(col: Int, codepoint: Int, attr: Int) {
        if (!isInBounds(col, width)) return
        codepoints[col] = codepoint
        attrs[col] = attr
    }

    /**
     * Gets the codepoint of the cell at the specified column.
     * @param col The column index of the cell to query
     * @return The Unicode codepoint at the specified column, or null if col is out of bounds
     */
    fun getCodepoint(col: Int): Int? = if (isInBounds(col, width)) codepoints[col] else null

    /**
     * Gets the attribute of the cell at the specified column.
     * @param col The column index of the cell to query
     * @return The packed attribute Int at the specified column, or null if col is out of bounds
     */
    fun getAttr(col: Int): Int? = if (isInBounds(col, width)) attrs[col] else null

    /**
     * Clears cells from the specified column to the end of the line.
     * @param startCol The starting column (inclusive)
     * @param attr The attribute to fill cleared cells with
     */
    fun clearFromColumn(startCol: Int, attr: Int) {
        val start = startCol.coerceIn(0, width)
        for (col in start until width) {
            codepoints[col] = 0
            attrs[col] = attr
        }
    }

    /**
     * Clears cells from the beginning of the line to the specified column.
     * @param endCol The ending column (inclusive)
     * @param attr The attribute to fill cleared cells with
     */
    fun clearToColumn(endCol: Int, attr: Int) {
        val end = (endCol + 1).coerceIn(0, width)
        for (col in 0 until end) {
            codepoints[col] = 0
            attrs[col] = attr
        }
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
    fun toTextTrimmed(): String = toText().trimEnd()
}