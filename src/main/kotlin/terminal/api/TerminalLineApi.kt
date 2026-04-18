package com.gagik.terminal.api

/**
 * A read-only, EPHEMERAL view of a single physical terminal line.
 * * DANGER - TEMPORAL COUPLING:
 * The UI Renderer MUST NOT store or hold references to this object outside the
 * immediate execution scope of the current render frame. The backing memory
 * may mutate at any time if background output arrives.
 */
interface TerminalLineApi {

    /** Number of columns in this line. */
    val width: Int

    /**
     * Returns the **base (first) codepoint** for the cell at [col].
     *
     * - For plain cells this is the full Unicode scalar value.
     * - For cluster cells this is the leading codepoint of the grapheme sequence.
     *   Simple renderers that map one cell to one glyph can use this value directly.
     * - Returns [com.gagik.terminal.model.TerminalConstants.EMPTY] (0) for blank cells.
     * - Returns [com.gagik.terminal.model.TerminalConstants.WIDE_CHAR_SPACER] (-1)
     *   for the right half of a 2-cell wide character; renderers should skip such cells.
     */
    fun getCodepoint(col: Int): Int

    /**
     * Returns the packed attribute integer for the cell at [col].
     * Decode with [com.gagik.terminal.codec.AttributeCodec] to access individual fields.
     * Use this method in production render loops; it is zero-allocation.
     */
    fun getPackedAttr(col: Int): Int

    /**
     * Returns `true` if the cell at [col] holds a multi-codepoint grapheme cluster
     * requiring a call to [readCluster] for full rendering.
     *
     * Defaults to `false` so that [com.gagik.terminal.model.VoidLine] and simple
     * stub implementations need not override it.
     */
    fun isCluster(col: Int): Boolean = false

    /**
     * Copies all codepoints of the grapheme cluster at [col] into [dest] and
     * returns the number of codepoints written.
     *
     * **Zero-allocation contract:** the renderer allocates `dest` once at startup
     * (an `IntArray` of size 16 covers every real-world cluster) and reuses it
     * across all frames. This method never allocates.
     *
     * Returns `0` for non-cluster cells; callers should check [isCluster] first
     * or treat a return value of `0` as "use [getCodepoint] instead".
     *
     * @param col  Column index (0-based).
     * @param dest Destination array. Must have capacity >= actual cluster length.
     * @return     Number of codepoints written, or 0 if the cell is not a cluster.
     */
    fun readCluster(col: Int, dest: IntArray): Int = 0
}