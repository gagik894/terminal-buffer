package com.gagik.terminal.model

/**
 * The strict mathematical boundary of the terminal.
 * Provides pure validation and clamping logic for the Engine.
 */
internal class GridDimensions(var width: Int, var height: Int) {

    init {
        require(width > 0) { "Terminal width must be strictly positive, got $width" }
        require(height > 0) { "Terminal height must be strictly positive, got $height" }
    }

    /**
     * Checks if a column index is within the terminal's strict bounds.
     * @param col The column index to check
     * @return true if the column index is within the terminal's bounds, false otherwise
     */
    fun isValidCol(col: Int): Boolean = col in 0 until width


    /**
     * Checks if a row index is within the terminal's strict bounds.
     * @param row The row index to check
     * @return true if the row index is within the terminal's bounds, false otherwise
     */
    fun isValidRow(row: Int): Boolean = row in 0 until height


    /**
     * Safely restricts a column index to the terminal's bounds.
     * @param col The column index to clamp
     * @return The clamped column index within the terminal's bounds
     */
    fun clampCol(col: Int): Int = col.coerceIn(0, width - 1)

    /**
     * Safely restricts a row index to the terminal's bounds.
     * @param row The row index to clamp
     * @return The clamped row index within the terminal's bounds
     */
    fun clampRow(row: Int): Int = row.coerceIn(0, height - 1)
}