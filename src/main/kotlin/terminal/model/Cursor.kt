package com.gagik.terminal.model


/**
 * Tracks the cursor position within the terminal grid.
 *
 * The cursor can be moved absolutely (set) or relatively (move).
 * All movements are clamped to the bounds of the terminal dimensions.
 *
 * @param width The width of the terminal in columns. Must be > 0.
 * @param height The height of the terminal in rows. Must be > 0.
 * @throws IllegalArgumentException if width or height are not greater than 0
 */
class Cursor(
    private var width: Int,
    private var height: Int
) {
    init {
        require(height > 0) { "height ($height) must be > 0" }
        require(width > 0) { "width ($width) must be > 0" }
    }

    var col: Int = 0 // column index, 0-based
        private set
    var row: Int = 0 // row index, 0-based
        private set

    /**
     * Absolute movement. Clamps to bounds.
     * If col or row are out of bounds, they will be clamped to the nearest valid position.
     * @param col The target column index (0-based)
     * @param row The target row index (0-based)
     */
    fun set(col: Int, row: Int) {
        this.col = col.coerceIn(0, width - 1)
        this.row = row.coerceIn(0, height - 1)
    }

    /**
     * Relative movement. Clamps to bounds.
     * The cursor will move by the specified deltas, but will not go outside the terminal bounds.
     * @param dx The change in column index (positive moves right, negative moves left)
     * @param dy The change in row index (positive moves down, negative moves up)
     */
    fun move(dx: Int, dy: Int) {
        // Long to prevent integer overflow during addition
        val newCol = (col.toLong() + dx).coerceIn(0L, (width - 1).toLong()).toInt()
        val newRow = (row.toLong() + dy).coerceIn(0L, (height - 1).toLong()).toInt()

        set(newCol, newRow)
    }

    /**
     * Advances cursor by one position (for character writing).
     * Handles automatic line wrapping.
     *
     * @return AdvanceResult indicating what happened
     */
    fun advance(): AdvanceResult {
        val oldRow = row
        col++

        if (col >= width) {
            // Wrap to next line
            col = 0
            row++

            if (row >= height) {
                // Wrapped past bottom - need to scroll
                row = height - 1
                return AdvanceResult.ScrollNeeded(oldRow)
            }

            return AdvanceResult.Wrapped(oldRow)
        }

        return AdvanceResult.Normal
    }

    /**
     * Resets the cursor position to the top-left corner (0, 0).
     */
    fun reset() {
        col = 0
        row = 0
    }
}

/**
 * Result of cursor advance operation.
 */
sealed class AdvanceResult {
    /** Normal advancement within a line */
    object Normal : AdvanceResult()

    /** Wrapped to next line */
    data class Wrapped(val fromRow: Int) : AdvanceResult()

    /** Wrapped past bottom edge - scroll needed */
    data class ScrollNeeded(val fromRow: Int) : AdvanceResult()
}