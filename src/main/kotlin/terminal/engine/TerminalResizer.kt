package com.gagik.terminal.engine

import com.gagik.terminal.buffer.HistoryRing
import com.gagik.terminal.model.Line
import com.gagik.terminal.model.TerminalConstants
import com.gagik.terminal.state.TerminalState
import com.gagik.terminal.store.ClusterStore

/**
 * Stateless resize engine.
 *
 * Reflowing a terminal grid on resize is a three-phase operation:
 *
 * 1. **Logical-line reconstruction** — adjacent physical lines that were soft-wrapped
 *    are concatenated into a single logical line of unbounded width.
 * 2. **Re-wrapping** — each logical line is chopped into new-width physical lines and
 *    pushed into a fresh [HistoryRing].
 * 3. **Cursor relocation** — the cursor cell is tracked through reflow so it lands
 *    on the correct row/column in the new grid.
 *
 * ## ClusterStore deep-copy
 *
 * A resize creates a brand-new [ClusterStore] alongside a brand-new [HistoryRing].
 * Cluster payloads that survive reflow are **deep-copied** into the new store via
 * [ClusterStore.readInto] + [ClusterStore.alloc]. Cluster payloads belonging to
 * lines that are dropped (history overflow) are simply abandoned; the old store is
 * handed to the JVM GC as a unit once [TerminalState.clusterStore] is overwritten.
 * No per-slot free loop is needed.
 */
internal object TerminalResizer {

    /**
     * Resizes the terminal to [newWidth] × [newHeight], reflowing all content and
     * updating [state] atomically at the end.
     */
    fun resize(state: TerminalState, newWidth: Int, newHeight: Int) {
        val oldWidth  = state.dimensions.width
        val oldHeight = state.dimensions.height

        // Allocate the new store and ring together — they are always co-owned.
        val newStore = ClusterStore()
        val newRing = HistoryRing(state.maxHistory + newHeight) { Line(newWidth, newStore) }

        // Reusable scratch buffer for cluster codepoint transfer during reflow.
        val clusterBuf = IntArray(MAX_CLUSTER_CODEPOINTS)

        val builder = LogicalLineBuilder(oldWidth * 10)

        // Absolute index of the cursor in the old ring (0 = oldest history line).
        val absoluteOldCursorRow =
            (state.ring.size - oldHeight).coerceAtLeast(0) + state.cursor.row

        var newAbsoluteCursorRow = 0
        var newCursorCol = 0
        var cursorPlaced = false

        // Flushes the current logical line accumulated in [builder] into [newRing],
        // chopping it into newWidth-wide physical lines and setting the wrap flag on
        // every chunk except the last.
        fun flushBuilder() {
            if (builder.size == 0) {
                val newLine = newRing.push()
                newLine.clear(0)
                if (builder.cursorAbsoluteIndex != -1) {
                    newAbsoluteCursorRow = newRing.size - 1
                    newCursorCol = 0
                    cursorPlaced = true
                }
                return
            }
            var offset = 0
            while (offset < builder.size) {
                val newLine = newRing.push()
                newLine.clear(0)

                var chunkLength = minOf(newWidth, builder.size - offset)

                // If we are chopping exactly at the new width boundary, check if
                // the NEXT character in the buffer is a wide-character spacer.
                if (chunkLength == newWidth
                    && chunkLength > 1
                    && offset + chunkLength < builder.size
                    && builder.codepoints[offset + chunkLength] == TerminalConstants.WIDE_CHAR_SPACER
                ) {
                    chunkLength--
                }

                // Set the wrapped flag after the line has been cleared once. Do NOT
                // clear the line again afterwards because `clear()` resets `wrapped`.
                newLine.wrapped = (offset + chunkLength < builder.size)

                for (i in 0 until chunkLength) {
                    val srcIndex = offset + i
                    val raw = builder.codepoints[srcIndex]
                    val attr = builder.attrs[srcIndex]

                    if (raw <= TerminalConstants.CLUSTER_HANDLE_MAX) {
                        val cpLen = state.clusterStore.readInto(raw, clusterBuf)
                        newLine.setCluster(i, clusterBuf, cpLen, attr)
                    } else {
                        newLine.setRawCell(i, raw, attr)
                    }

                    if (srcIndex == builder.cursorAbsoluteIndex) {
                        newAbsoluteCursorRow = newRing.size - 1
                        newCursorCol = i
                        cursorPlaced = true
                    }
                }
                offset += chunkLength
            }
        }

        // Traverse every line in the old ring, rebuilding logical lines.
        for (i in 0 until state.ring.size) {
            val oldLine    = state.ring[i]
            val logicalLen = getLogicalLength(oldLine)
            val dataLength = if (oldLine.wrapped && logicalLen > 0) oldWidth else logicalLen
            val hasCursor  = (i == absoluteOldCursorRow)

            // Only extend readLength past dataLength when the cursor is genuinely
            // beyond the content. A cursor at col=0 on an empty line must NOT force
            // a cell read — the empty-builder path in flushBuilder handles it.
            val readLength = if (hasCursor && state.cursor.col > 0)
                maxOf(dataLength, state.cursor.col + 1)
            else
                dataLength

            for (col in 0 until readLength) {
                val isCursor = hasCursor && (col == state.cursor.col)
                builder.append(oldLine.rawCodepoint(col), oldLine.getPackedAttr(col), isCursor)
            }

            // Mark cursor on the logical line even when readLength == 0
            // so flushBuilder places it on the resulting blank line.
            if (hasCursor && readLength == 0) {
                builder.cursorAbsoluteIndex = 0  // will be on the blank line
            }

            if (!oldLine.wrapped) {
                flushBuilder()
                builder.clear()
            }
        }

        // Flush any remaining wrapped content or handle cursor below ring content.
        if (builder.size > 0 || absoluteOldCursorRow >= state.ring.size) {
            flushBuilder()
        }

        // Ensure the new ring is tall enough to fill at least one full screen.
        while (newRing.size < newHeight) {
            newRing.push().clear(0)
        }

        val liveScreenTop = (newRing.size - newHeight).coerceAtLeast(0)

        if (!cursorPlaced) {
            // Cursor was on a virtual blank row below all ring content.
            newCursorCol         = state.cursor.col
            newAbsoluteCursorRow =
                (liveScreenTop + state.cursor.row).coerceIn(liveScreenTop, newRing.size - 1)
        }

        val newRelativeRow = (newAbsoluteCursorRow - liveScreenTop).coerceIn(0, newHeight - 1)

        // Atomic state swap. The old store and old ring are released to the GC here.
        state.dimensions.width  = newWidth
        state.dimensions.height = newHeight
        state.clusterStore      = newStore
        state.ring              = newRing
        state.cursor.col        = newCursorCol
        state.cursor.row        = newRelativeRow
        state.tabStops.resize(newWidth)
    }

    // Private helpers

    /**
     * Returns the index one past the last non-blank cell, using the raw value so
     * that cluster handles (<= -2) are correctly treated as content.
     */
    private fun getLogicalLength(line: Line): Int {
        var len = line.width
        while (len > 0 && line.rawCodepoint(len - 1) == TerminalConstants.EMPTY) len--
        return len
    }

    /**
     * Maximum number of codepoints in a single grapheme cluster.
     * 16 is far beyond any real-world sequence (ZWJ family emoji peak at ~10).
     */
    private const val MAX_CLUSTER_CODEPOINTS = 16
}


/**
 * Accumulates cells from one or more soft-wrapped physical lines into a single
 * flat logical line for re-wrapping at the new width.
 *
 * All arrays are grown by doubling; the initial capacity should be generous enough
 * to avoid a growth on typical terminal content (old_width * 10 is safe).
 *
 * @param initialCapacity Starting array capacity in cells.
 */
private class LogicalLineBuilder(initialCapacity: Int) {

    var codepoints = IntArray(initialCapacity)
    var attrs      = IntArray(initialCapacity)
    var size       = 0

    /**
     * Absolute index of the cursor cell within this logical line, or -1 if the
     * cursor is not on the line currently being built.
     */
    var cursorAbsoluteIndex = -1

    /**
     * Appends one cell (raw codepoint value + packed attr) to the builder.
     *
     * @param raw      The raw [Int] from [Line.rawCodepoint] — may be a handle.
     * @param attr     The packed cell attribute.
     * @param isCursor `true` if this cell is the current cursor position.
     */
    fun append(raw: Int, attr: Int, isCursor: Boolean) {
        if (size == codepoints.size) grow()
        if (isCursor) cursorAbsoluteIndex = size
        codepoints[size] = raw
        attrs[size] = attr
        size++
    }

    /** Resets the builder for the next logical line without releasing arrays. */
    fun clear() {
        size = 0
        cursorAbsoluteIndex = -1
    }

    private fun grow() {
        codepoints = codepoints.copyOf(size * 2)
        attrs = attrs.copyOf(size * 2)
    }
}