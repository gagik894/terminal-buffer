package com.gagik.terminal.api

/**
 * Zero-allocation read contract for the terminal buffer.
 *
 * Exposes viewport-relative state plus random-access helpers for the currently
 * active screen buffer. Out-of-bounds probes never throw; they return stable
 * sentinel values so renderers can remain branch-light.
 */
interface TerminalReader {

    /** Current viewport width in cells. */
    val width: Int

    /** Current viewport height in rows. */
    val height: Int

    /** Active cursor column in zero-based viewport coordinates. */
    val cursorCol: Int

    /** Active cursor row in zero-based viewport coordinates. */
    val cursorRow: Int

    /** Number of retained off-screen history lines in the active buffer. */
    val historySize: Int

    /** Returns the visible line at [row], or a shared void line when [row] is out of bounds. */
    fun getLine(row: Int): TerminalLineApi

    /**
     * Returns the display/base codepoint at `[col, row]`.
     *
     * - Plain cells return their stored Unicode scalar value.
     * - Cluster cells return the leading codepoint of the grapheme sequence.
     * - Wide-character spacer cells return `-1`.
     * - Blank cells and out-of-bounds probes return `0`.
     */
    fun getCodepointAt(col: Int, row: Int): Int

    /**
     * Returns the packed cell attributes, or the active pen attribute if out of bounds.
     * This mirrors the terminal's current erase/write attribute for off-grid queries.
     */
    fun getPackedAttrAt(col: Int, row: Int): Int
}
