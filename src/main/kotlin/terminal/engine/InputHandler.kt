package com.gagik.terminal.engine

import com.gagik.terminal.model.Line
import com.gagik.terminal.state.TerminalState

/**
 * Handles user input and updates the terminal state accordingly.
 * This includes writing characters to the grid, moving the cursor, and managing line wrapping and scrolling.
 *
 * @param state The terminal state to update
 */
internal class InputHandler(
    private val state: TerminalState
) {

    /**
     * Translates the visual screen row to the absolute memory line in the HistoryRing.
     */
    private fun getActiveLine(row: Int): Line {
        // Safe access guided by the GridDimensions Source of Truth
        require(state.dimensions.isValidRow(row)) { "Row $row is outside visible screen" }

        val startIndex = (state.ring.size - state.dimensions.height).coerceAtLeast(0)
        return state.ring[startIndex + row]
    }

    /**
     * Writes a codepoint to the grid at the current cursor position,
     * using the current pen attributes, and advances the cursor.
     */
    fun print(codepoint: Int) {
        // Validate physics
        if (state.dimensions.isValidCol(state.cursor.col) && state.dimensions.isValidRow(state.cursor.row)) {
            // Write to memory
            val line = getActiveLine(state.cursor.row)
            line.setCell(state.cursor.col, codepoint, state.pen.currentAttr)
        }

        // Advance physics
        advanceCursor()
    }

    private fun advanceCursor() {
        state.cursor.col++

        // Check horizontal boundary
        if (state.cursor.col >= state.dimensions.width) {
            // Mark the current line as a soft-wrap
            if (state.dimensions.isValidRow(state.cursor.row)) {
                getActiveLine(state.cursor.row).wrapped = true
            }

            // Wrap coordinate to next line
            state.cursor.col = 0
            moveDown()
        }
    }

    /**
     * Executes a Carriage Return (\r)
     */
    fun carriageReturn() {
        state.cursor.col = 0
    }

    /**
     * Executes a Line Feed (\n)
     */
    fun newLine() {
        moveDown()
    }

    /**
     * Moves the cursor down. If it hits the bottom boundary, it triggers a scroll.
     */
    private fun moveDown() {
        state.cursor.row++

        if (state.cursor.row >= state.dimensions.height) {
            // Clamp to bottom
            state.cursor.row = state.dimensions.height - 1
            scrollUp()
        }
    }

    /**
     * Asks the memory allocator (HistoryRing) for a new line and clears it.
     */
    private fun scrollUp() {
        val newLine = state.ring.push()
        newLine.clear(state.pen.currentAttr)
    }
}