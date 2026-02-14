package com.gagik.terminal.model

import com.gagik.terminal.buffer.HistoryRing

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
 * @param height The number of visible rows on the screen. Must be > 0.
 * @param width The number of columns in each line. Must be > 0.
 * @throws IllegalArgumentException if height or width are not greater than 0
 */
class Screen(
    private val ring: HistoryRing,
    private val height: Int,
    private val width: Int
) {
    init {
        require(height > 0) { "height must be > 0" }
        require(width > 0) { "width must be > 0" }
    }

    /**
     * Gets the Line at the specified screen row.
     * Maps visual row to the absolute ring index.
     *
     * @param row Visual row (0 = top of screen, height-1 = bottom)
     * @return The Line at that screen position
     * @throws IllegalArgumentException if row is out of bounds
     */
    fun getLine(row: Int): Line {
        require(row in 0 until height) { "row $row out of bounds (0..<$height)" }

        // Screen is the last [height] lines of the ring
        val startIndex = (ring.size - height).coerceAtLeast(0)
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
        if (row in 0 until height && col in 0 until width) {
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
        val startIndex = (ring.size - height).coerceAtLeast(0)
        for (i in 0 until height.coerceAtMost(ring.size)) {
            ring[startIndex + i].clear(fillAttr)
        }
    }
}