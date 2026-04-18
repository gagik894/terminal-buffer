package com.gagik.terminal.state

import com.gagik.terminal.buffer.HistoryRing
import com.gagik.terminal.model.Cursor
import com.gagik.terminal.model.Line
import com.gagik.terminal.model.SavedCursorState
import com.gagik.terminal.store.ClusterStore

/**
 * Represents a single cohesive terminal screen (Primary or Alternate).
 *
 * Each ScreenBuffer owns its entire memory arena — the ring, the cluster store,
 * the cursor, the DECSC save slot, and the active scroll margins.
 *
 * ## Memory ownership
 * [store] and [ring] are always co-owned. Every [Line] in the ring holds a
 * reference to [store]. The pair must always be replaced together — never
 * replace one without the other. [replaceStorage] is the single safe entry
 * point for doing this.
 *
 * ## Resize lifecycle (absolute, not relative to the active screen)
 * - **Primary buffer** — must always be *reflowed* by [TerminalResizer] to preserve
 *   scrollback history, regardless of which screen is currently active.
 * - **Alternate buffer** — must always be *wiped* via [replaceStorage]. It has no
 *   scrollback; its content is transient. The host app receives SIGWINCH and
 *   redraws itself onto the fresh grid.
 */
internal class ScreenBuffer(
    initialWidth: Int,
    initialHeight: Int,
    val maxHistory: Int,
) {
    // store and ring are always replaced together via replaceStorage().
    var store = ClusterStore()
        internal set

    var ring = HistoryRing(maxHistory + initialHeight) { Line(initialWidth, store) }
        internal set

    val cursor = Cursor()
    val savedCursor = SavedCursorState()

    var scrollTop: Int = 0; private set
    var scrollBottom: Int = initialHeight - 1; private set

    // ── Scroll region ─────────────────────────────────────────────────────────

    /**
     * Returns `true` when the scroll margins cover the entire viewport.
     *
     * Takes the live [viewportHeight] rather than caching [initialHeight] so
     * that the answer remains correct after a terminal resize.
     */
    fun isFullViewportScroll(viewportHeight: Int): Boolean =
        scrollTop == 0 && scrollBottom == viewportHeight - 1

    /**
     * DECSTBM: sets the scroll margins and homes the cursor per the VT spec.
     *
     * [top] and [bottom] are 1-based per the escape sequence convention.
     * Degenerate ranges are silently ignored.
     */
    fun setScrollRegion(top: Int, bottom: Int, isOriginMode: Boolean, viewportHeight: Int) {
        val t = (top - 1).coerceIn(0, viewportHeight - 1)
        val b = (bottom - 1).coerceIn(0, viewportHeight - 1)
        if (t >= b) return
        scrollTop = t
        scrollBottom = b
        cursor.col = 0
        cursor.row = if (isOriginMode) t else 0
        cursor.pendingWrap = false
    }

    /** Resets scroll margins to the full viewport. */
    fun resetScrollRegion(viewportHeight: Int) {
        scrollTop = 0
        scrollBottom = viewportHeight - 1
    }

    // ── Grid operations ───────────────────────────────────────────────────────

    /**
     * Clears the grid and fills it with [viewportHeight] blank lines.
     * Reuses the existing [ring] and [store] — does not replace them.
     */
    fun clearGrid(penAttr: Int, viewportHeight: Int) {
        ring.clear()
        repeat(viewportHeight) { ring.push().clear(penAttr) }
    }

    /**
     * Replaces the entire memory arena with a fresh store and ring sized to
     * [newWidth] × [newHeight], then fills the new ring with blank lines.
     *
     * Used exclusively by the **alternate buffer resize path**. The old store
     * and ring are released to the GC as a unit — no per-slot free loop needed.
     *
     * **Never call this on the primary buffer.** The primary buffer must always
     * be reflowed by [com.gagik.terminal.engine.TerminalResizer] to preserve scrollback history.
     */
    fun replaceStorage(newWidth: Int, newHeight: Int, penAttr: Int) {
        store = ClusterStore()
        ring = HistoryRing(maxHistory + newHeight) { Line(newWidth, store) }
        repeat(newHeight) { ring.push().clear(penAttr) }
        scrollTop = 0
        scrollBottom = newHeight - 1

        cursor.col = cursor.col.coerceAtMost(maxOf(0, newWidth - 1))
        cursor.row = cursor.row.coerceAtMost(maxOf(0, newHeight - 1))

        // If the grid shrank and forced the cursor inward, pending wrap is no longer mathematically valid
        if (cursor.col < newWidth - 1) {
            cursor.pendingWrap = false
        }
    }
}