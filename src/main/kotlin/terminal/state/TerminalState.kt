package com.gagik.terminal.state

import com.gagik.terminal.buffer.HistoryRing
import com.gagik.terminal.model.*
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
 */
internal class TerminalState(
    initialWidth: Int,
    initialHeight: Int,
    val maxHistory: Int,
) {
    val modes = TerminalModes()
    val dimensions = GridDimensions(initialWidth, initialHeight)
    val cursor = Cursor()
    val savedCursor = SavedCursorState()
    val pen = Pen()

    // NOTE: clusterStore must be declared before ring because ring's factory
    // lambda captures clusterStore by reference during array initialization.
    var clusterStore: ClusterStore = ClusterStore()
    /**
     * Circular buffer of physical terminal lines, covering both scrollback history
     * and the live viewport.
     *
     * Capacity = [maxHistory] + [initialHeight], ensuring the viewport always fits
     * even when history is full.
     */
    var ring = HistoryRing(maxHistory + initialHeight) { Line(initialWidth, clusterStore) }
    /**
     * Top row of the active scroll region, 0-based, inclusive.
     * Default: 0 (top of screen).
     */
    var scrollTop: Int = 0
    /**
     * Bottom row of the active scroll region, 0-based, inclusive.
     * Default: height - 1 (bottom of screen).
     */
    var scrollBottom: Int = initialHeight - 1
    val tabStops = TabStops(dimensions.width)

    init {
        repeat(initialHeight) {
            ring.push().clear(pen.currentAttr)
        }
    }

    /**
     * Cancels any pending wrap operation.
     * This is called when the user presses a key that does not trigger a wrap.
     */
    fun cancelPendingWrap() {
        cursor.pendingWrap = false
    }

    /**
     * Resets the cursor position to (0, 0).
     */
    fun homeCursor() {
        cursor.col = 0
        cursor.row = 0
        cursor.pendingWrap = false
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


    /** True when the scroll region covers the entire viewport. */
    val isFullViewportScroll: Boolean
        get() = scrollTop == 0 && scrollBottom == dimensions.height - 1

    /**
     * Sets the scroll region, clamping and validating inputs.
     * Resets the cursor to (0, 0) as required by the VT spec.
     *
     * @param top 1-based top row from the DECSTBM escape (converted to 0-based internally).
     * @param bottom 1-based bottom row from the DECSTBM escape (converted to 0-based internally).
     */
    fun setScrollRegion(top: Int, bottom: Int) {
        val t = (top - 1).coerceIn(0, dimensions.height - 1)
        val b = (bottom - 1).coerceIn(0, dimensions.height - 1)
        if (t >= b) return

        scrollTop = t
        scrollBottom = b
        cursor.col = 0
        cursor.row = if (modes.isOriginMode) t else 0
        cursor.pendingWrap = false
    }

    /**
     * Resets the scroll region to the full viewport.
     * Called on resize and terminal reset.
     */
    fun resetScrollRegion() {
        scrollTop = 0
        scrollBottom = dimensions.height - 1
        homeCursor()
    }
}