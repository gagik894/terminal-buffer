package com.gagik.core.model


/**
 * Tracks the cursor position within the terminal grid.
 */
internal class Cursor {
    /** The current cursor column. 0-based.*/
    var col: Int = 0

    /** The current cursor row. 0-based.*/
    var row: Int = 0

    /**
     * True when the cursor is parked at the right margin and the next printable
     * character must trigger the deferred wrap before being written.
     */
    var pendingWrap: Boolean = false
}