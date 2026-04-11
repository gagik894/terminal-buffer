package com.gagik.terminal.model


/**
 * Tracks the cursor position within the terminal grid.
 *
 * @param col The column index (0-based)
 * @param row The row index (0-based)
 */
internal class Cursor {
    var col: Int = 0
    var row: Int = 0
}