package com.gagik.terminal.buffer

import com.gagik.terminal.engine.InputHandler
import com.gagik.terminal.state.TerminalState

/**
 * Terminal buffer coordinator.
 * Components:
 * - TerminalState: Manages terminal dimensions, cursor position, and scrollback history.
 * - InputHandler: Handles user input and updates the terminal state accordingly.
 * - AttributeCodec: Encodes terminal cell attributes into a compact Int.
 *
 * @param initialWidth Width of the terminal in columns. Must be > 0.
 * @param initialHeight Height of the visible screen in rows. Must be > 0.
 * @param maxHistory Maximum number of scrollback lines. Must be >= 0.
 * @throws IllegalArgumentException if width, height, or maxHistory are invalid
 */
class TerminalBuffer(
    initialWidth: Int,
    initialHeight: Int,
    maxHistory: Int = 1000
) {
    internal val state = TerminalState(initialWidth, initialHeight, maxHistory)
    private val inputHandler = InputHandler(state)

    // --- Public Properties ---

    val width: Int get() = state.dimensions.width
    val height: Int get() = state.dimensions.height
    val cursorCol: Int get() = state.cursor.col
    val cursorRow: Int get() = state.cursor.row

    // --- Core Writing API ---

    /**
     * Prints a single character and advances the cursor using the physics engine.
     */
    fun writeChar(value: Char) {
        inputHandler.print(value.code)
    }

    /**
     * Prints a string to the terminal.
     * Uses codePoints() to safely handle Unicode surrogate pairs (e.g., Emojis)
     * without breaking them across cell boundaries.
     */
    fun writeText(text: String) {
        text.codePoints().forEach { cp ->
            inputHandler.print(cp)
        }
    }

    /**
     * Executes a Line Feed (\n). Moves the cursor down, triggering a scroll if necessary.
     */
    fun newLine() {
        inputHandler.newLine()
    }

    /**
     * Executes a Carriage Return (\r). Moves the cursor to column 0.
     */
    fun carriageReturn() {
        inputHandler.carriageReturn()
    }

    // --- Styling API ---

    /**
     * Updates the active pen attributes for subsequent writes.
     */
    fun setPenAttributes(
        fg: Int,
        bg: Int,
        bold: Boolean = false,
        italic: Boolean = false,
        underline: Boolean = false
    ) {
        state.pen.setAttributes(fg, bg, bold, italic, underline)
    }

    /**
     * Resets the active pen to the terminal's default style.
     */
    fun resetPen() {
        state.pen.reset()
    }

    // --- Viewport / Rendering API ---

    /**
     * Gets the text of a specific visible row.
     * Useful for UI frameworks rendering the screen line-by-line.
     * * @param row Visual row (0 is top of screen, height-1 is bottom)
     */
    fun getVisibleLineText(row: Int): String {
        if (!state.dimensions.isValidRow(row)) return ""

        val startIndex = (state.ring.size - state.dimensions.height).coerceAtLeast(0)
        return state.ring[startIndex + row].toTextTrimmed()
    }

    /**
     * Retrieves the entire visible screen text as a single string.
     */
    fun getVisibleText(): String {
        val sb = StringBuilder()
        val lineCount = state.dimensions.height.coerceAtMost(state.ring.size)
        val startIndex = (state.ring.size - state.dimensions.height).coerceAtLeast(0)

        for (i in 0 until lineCount) {
            if (i > 0) sb.append('\n')
            sb.append(state.ring[startIndex + i].toTextTrimmed())
        }
        return sb.toString()
    }

    // --- Utility API ---

    /**
     * Clears only the visible portion of the screen and resets the cursor to home (0,0).
     */
    fun clearScreen() {
        val lineCount = state.dimensions.height.coerceAtMost(state.ring.size)
        val startIndex = (state.ring.size - state.dimensions.height).coerceAtLeast(0)

        for (i in 0 until lineCount) {
            state.ring[startIndex + i].clear(state.pen.currentAttr)
        }

        state.cursor.col = 0
        state.cursor.row = 0
    }
}