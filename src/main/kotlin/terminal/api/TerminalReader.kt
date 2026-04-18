package com.gagik.terminal.api

/**
 * Zero-allocation read contract for the terminal buffer.
 *
 * Consumed by the renderer on every frame. No method in this interface
 * allocates — callers receive primitive values or pre-existing object
 * references only.
 */
interface TerminalReader {

    /** Width of the visible terminal grid, in cells. */
    val width: Int

    /** Height of the visible terminal grid, in rows. */
    val height: Int

    /** Current cursor column (0-based). */
    val cursorCol: Int

    /** Current cursor row (0-based). */
    val cursorRow: Int

    /** Number of lines currently retained in scrollback history. */
    val historySize: Int

    /** `true` while the alternate screen buffer is active. */
    //TODO: val isAltBufferActive: Boolean

    /**
     * Returns a read-only view of a visible row.
     *
     * @param row Visible row index (0-based).
     * @return The row's line, or a blank `VoidLine` if [row] is out of bounds.
     */
    fun getLine(row: Int): TerminalLineApi

    /**
     * Returns the Unicode codepoint stored at the given screen position.
     *
     * @param col Column index (0-based).
     * @param row Row index (0-based).
     * @return The stored codepoint, or `0` if the cell is empty or out of bounds.
     */
    fun getCodepointAt(col: Int, row: Int): Int

    /**
     * Returns the packed attribute word at the given screen position.
     *
     * Decode with `AttributeCodec` on the call site. Do not use [TerminalInspector.getAttrAt]
     * on a hot rendering path — it allocates.
     *
     * @param col Column index (0-based).
     * @param row Row index (0-based).
     * @return The packed cell attributes, or the active pen attribute if out of bounds.
     */
    fun getPackedAttrAt(col: Int, row: Int): Int
}