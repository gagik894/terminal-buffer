package com.gagik.terminal.state

import com.gagik.terminal.buffer.HistoryRing
import com.gagik.terminal.model.Cursor
import com.gagik.terminal.model.GridDimensions
import com.gagik.terminal.model.Line
import com.gagik.terminal.model.Pen

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
    val maxHistory: Int
) {
    val dimensions = GridDimensions(initialWidth, initialHeight)
    val cursor = Cursor()
    val pen = Pen()

    var ring = HistoryRing(maxHistory + initialHeight) { Line(initialWidth) }

    init {
        repeat(initialHeight) {
            ring.push().clear(pen.currentAttr)
        }
    }
}