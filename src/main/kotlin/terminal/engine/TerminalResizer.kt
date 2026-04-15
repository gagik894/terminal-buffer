package com.gagik.terminal.engine

import com.gagik.terminal.buffer.HistoryRing
import com.gagik.terminal.model.Line
import com.gagik.terminal.model.TerminalConstants
import com.gagik.terminal.state.TerminalState

internal object TerminalResizer {
    fun resize(state: TerminalState, newWidth: Int, newHeight: Int) {
        val oldWidth = state.dimensions.width
        val oldHeight = state.dimensions.height
        val newRing = HistoryRing(state.maxHistory + newHeight) { Line(newWidth) }
        val builder = LogicalLineBuilder(oldWidth * 10)

        // The cursor row expressed as an absolute index into the ring,
        // counting from the top of history rather than the top of the screen.
        val absoluteOldCursorRow = (state.ring.size - oldHeight).coerceAtLeast(0) + state.cursor.row

        var newAbsoluteCursorRow = 0
        var newCursorCol = 0

        // Becomes true the moment we locate the cursor cell during reflow.
        // If it never fires, the cursor was on a blank virtual row below all
        // ring content and we fall back to preserving its relative screen row.
        var cursorPlaced = false

        // Writes the logical line accumulated in `builder` into `newRing`,
        // chopping it into newWidth-wide physical lines and setting the
        // wrapped flag on every line except the last chunk.
        fun flushBuilder() {
            if (builder.size == 0) {
                // Empty logical line: push one blank physical line.
                val newLine = newRing.push()
                newLine.clear(0)
                // cursorAbsoluteIndex != -1 means the cursor was explicitly
                // placed on this otherwise-empty line (e.g. cursor at col 0
                // on a line with no other content).
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
                val chunkLength = minOf(newWidth, builder.size - offset)
                // Mark this physical line as wrapped if more content follows.
                newLine.wrapped = (offset + chunkLength < builder.size)
                for (i in 0 until chunkLength) {
                    newLine.setCell(i, builder.codepoints[offset + i], builder.attrs[offset + i])
                    if (offset + i == builder.cursorAbsoluteIndex) {
                        newAbsoluteCursorRow = newRing.size - 1
                        newCursorCol = i
                        cursorPlaced = true
                    }
                }
                offset += newWidth
            }
        }

        for (i in 0 until state.ring.size) {
            val oldLine = state.ring[i]

            // Trailing null codepoints are visual padding, not real content.
            val logicalLen = getLogicalLength(oldLine)

            // For a wrapped line, all oldWidth columns belong to the logical
            // line even if the tail is blank, so we must read the full width.
            // An empty wrapped line is an orphaned wrap flag; treat it as empty.
            val dataLength = if (oldLine.wrapped && logicalLen > 0) oldWidth else logicalLen

            val hasCursor = (i == absoluteOldCursorRow)

            // Always read at least as far as the cursor column so the cursor
            // position is recorded even if it sits beyond any real content.
            val readLength = if (hasCursor) maxOf(dataLength, state.cursor.col + 1) else dataLength

            for (col in 0 until readLength) {
                val isCursor = hasCursor && (col == state.cursor.col)
                builder.append(oldLine.getCodepoint(col), oldLine.getPackedAttr(col), isCursor)
            }

            // A non-wrapped line ends the current logical line; flush and reset.
            if (!oldLine.wrapped) {
                flushBuilder()
                builder.clear()
            }
        }

        // Flush any trailing wrapped content, or handle the case where the
        // cursor row was beyond the ring entirely (blank virtual rows).
        if (builder.size > 0 || absoluteOldCursorRow >= state.ring.size) {
            flushBuilder()
        }

        // Pad the ring with blank lines until it fills at least one full screen.
        while (newRing.size < newHeight) {
            newRing.push().clear(0)
        }

        val liveScreenTop = (newRing.size - newHeight).coerceAtLeast(0)

        // Cursor was on a virtual blank row below all ring content, so it was
        // never encountered during reflow. Preserve its relative screen row.
        if (!cursorPlaced) {
            newCursorCol = state.cursor.col
            newAbsoluteCursorRow = (liveScreenTop + state.cursor.row).coerceIn(liveScreenTop, newRing.size - 1)
        }

        val newRelativeRow = (newAbsoluteCursorRow - liveScreenTop).coerceIn(0, newHeight - 1)

        state.dimensions.width = newWidth
        state.dimensions.height = newHeight
        state.ring = newRing
        state.cursor.col = newCursorCol
        state.cursor.row = newRelativeRow
    }

    private fun getLogicalLength(line: Line): Int {
        var len = line.width
        while (len > 0 && line.getCodepoint(len - 1) == TerminalConstants.EMPTY) len--
        return len
    }
}

private class LogicalLineBuilder(initialCapacity: Int) {
    var codepoints = IntArray(initialCapacity)
    var attrs = IntArray(initialCapacity)
    var size = 0
    var cursorAbsoluteIndex = -1  // index of the cursor cell, or -1 if not on this logical line

    fun append(cp: Int, attr: Int, isCursor: Boolean) {
        if (size == codepoints.size) {
            codepoints = codepoints.copyOf(size * 2)
            attrs = attrs.copyOf(size * 2)
        }
        if (isCursor) cursorAbsoluteIndex = size
        codepoints[size] = cp
        attrs[size] = attr
        size++
    }

    fun clear() {
        size = 0
        cursorAbsoluteIndex = -1
    }
}