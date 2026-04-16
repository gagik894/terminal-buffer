package com.gagik.terminal.state

import com.gagik.terminal.buffer.HistoryRing
import com.gagik.terminal.model.Cursor
import com.gagik.terminal.model.GridDimensions
import com.gagik.terminal.model.Line
import com.gagik.terminal.model.Pen
import com.gagik.terminal.store.ClusterStore

/**
 * Encapsulates the entire state of the terminal,
 * including dimensions, cursor position, pen attributes, and the history buffer.
 *
 * The ring buffer is initialized with enough space for the initial height,
 * plus the maximum number of lines to retain in the history buffer.
 *
 * @param initialWidth The initial width of the terminal
 * @param initialHeight The initial height of the terminal
 * @param maxHistory The maximum number of lines to retain in the history buffer
 * @param treatAmbiguousAsWide Whether to treat ambiguous width characters as wide (default: false)
 */
internal class TerminalState(
    initialWidth: Int,
    initialHeight: Int,
    val maxHistory: Int,
    var treatAmbiguousAsWide: Boolean = false
) {
    val dimensions = GridDimensions(initialWidth, initialHeight)
    val cursor = Cursor()
    val pen = Pen()
    var clusterStore: ClusterStore = ClusterStore()

    /**
     * Circular buffer of physical terminal lines, covering both scrollback history
     * and the live viewport.
     *
     * Capacity = [maxHistory] + [initialHeight], ensuring the viewport always fits
     * even when history is full.
     */
    var ring = HistoryRing(maxHistory + initialHeight) { Line(initialWidth, clusterStore) }



    init {
        repeat(initialHeight) {
            ring.push().clear(pen.currentAttr)
        }
    }

    /**
     * Resolves a visible viewport row to its backing line index in the ring.
     *
     * @param viewportRow Viewport row (0-based).
     */
    fun resolveRingIndex(viewportRow: Int): Int {
        val startIndex = (ring.size - dimensions.height).coerceAtLeast(0)
        return startIndex + viewportRow
    }
}