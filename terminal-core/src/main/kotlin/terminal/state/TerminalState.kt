package com.gagik.terminal.state

import com.gagik.terminal.model.*

/**
 * The global hardware context of the terminal.
 *
 * Owns the global mode flags, pen, dimensions, and tab stops, and routes all
 * read/write operations to the correct [ScreenBuffer] via [activeBuffer].
 *
 * [primaryBuffer] and [altBuffer] each own their own memory arenas
 * ([ScreenBuffer.store] + [ScreenBuffer.ring]) so a resize of one buffer
 * can never corrupt the other.
 */
internal class TerminalState(
    initialWidth: Int,
    initialHeight: Int,
    maxHistory: Int,
) {
    // Global hardware state.

    val modes = TerminalModes()
    val tabStops = TabStops(initialWidth)
    val pen = Pen()
    val dimensions = GridDimensions(initialWidth, initialHeight)

    // Physical screens.

    val primaryBuffer = ScreenBuffer(initialWidth, initialHeight, maxHistory)
        .apply { clearGrid(pen.currentAttr, initialHeight) }

    /** Alternate buffer always has zero scrollback. */
    val altBuffer = ScreenBuffer(initialWidth, initialHeight, maxHistory = 0)
        .apply { clearGrid(pen.currentAttr, initialHeight) }

    // Hot-swap pointer.

    var activeBuffer: ScreenBuffer = primaryBuffer
        private set

    val isAltScreenActive: Boolean
        get() = activeBuffer === altBuffer

    /**
     * Switches to the alternate screen (`CSI ? 1049 h`).
     *
     * Callers must invoke `CursorEngine.saveCursor()` before this call (DECSC).
     * Re-entering the alternate screen clears any previous alternate content.
     * No-op when already in the alternate screen.
     */
    fun enterAltScreen() {
        if (isAltScreenActive) return
        altBuffer.clearGrid(pen.blankAttr, dimensions.height)
        altBuffer.resetScrollRegion(dimensions.height)
        altBuffer.resetLeftRightMargins(dimensions.width)
        altBuffer.cursor.col = 0
        altBuffer.cursor.row = 0
        altBuffer.cursor.pendingWrap = false
        altBuffer.savedCursor.clear()
        activeBuffer = altBuffer
    }

    /**
     * Returns to the primary screen (`CSI ? 1049 l`).
     *
     * Callers must invoke `CursorEngine.restoreCursor()` after this call (DECRC).
     * Alternate content becomes invisible after the switch and will be discarded
     * on the next alternate entry or resize.
     */
    fun exitAltScreen() {
        if (!isAltScreenActive) return
        activeBuffer = primaryBuffer
    }

    // Transparent engine accessors.

    val ring
        get() = activeBuffer.ring
    val cursor: Cursor
        get() = activeBuffer.cursor
    val savedCursor
        get() = activeBuffer.savedCursor
    val scrollTop
        get() = activeBuffer.scrollTop
    val scrollBottom
        get() = activeBuffer.scrollBottom
    val effectiveLeftMargin: Int
        get() = if (modes.isLeftRightMarginMode) activeBuffer.leftMargin else 0
    val effectiveRightMargin: Int
        get() = if (modes.isLeftRightMarginMode) activeBuffer.rightMargin else dimensions.width - 1

    val isFullViewportScroll: Boolean
        get() = activeBuffer.isFullViewportScroll(dimensions.height)

    fun resolveRingIndex(viewportRow: Int): Int =
        (activeBuffer.ring.size - dimensions.height).coerceAtLeast(0) + viewportRow

    // Convenience helpers.

    /** Clears the phantom-column pending-wrap flag on the active cursor. */
    fun cancelPendingWrap() {
        activeBuffer.cursor.pendingWrap = false
    }
}
