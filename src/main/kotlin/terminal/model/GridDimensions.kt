package com.gagik.terminal.model

/**
 * Manages the dimensions and boundary logic for the terminal grid.
 * * Separates soft boundary enforcement (clamping for cursor movement)
 * from hard boundary enforcement (validating internal memory access).
 */
class GridDimensions(var width: Int, var height: Int) {

    /**
     * Clamps the column index to the visible grid bounds.
     * Used for safely positioning the cursor even if external commands overflow.
     */
    fun clampCol(col: Int): Int = col.coerceIn(0, width - 1)

    /**
     * Clamps the row index to the visible grid bounds.
     */
    fun clampRow(row: Int): Int = row.coerceIn(0, height - 1)

    fun isValidCol(col: Int): Boolean = col in 0 until width
    fun isValidRow(row: Int): Boolean = row in 0 until height

    /**
     * Asserts that a column index is strictly within memory bounds.
     * @throws IllegalArgumentException if out of bounds.
     */
    fun requireValidCol(col: Int) {
        require(col in 0 until width) { "Column $col is out of bounds (0 until $width)" }
    }

    /**
     * Asserts that a row index is strictly within memory bounds.
     * @throws IllegalArgumentException if out of bounds.
     */
    fun requireValidRow(row: Int) {
        require(row in 0 until height) { "Row $row is out of bounds (0 until $height)" }
    }
}