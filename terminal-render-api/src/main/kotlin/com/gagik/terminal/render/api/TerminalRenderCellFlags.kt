package com.gagik.terminal.render.api

import com.gagik.terminal.render.api.TerminalRenderCellFlags.CLUSTER
import com.gagik.terminal.render.api.TerminalRenderCellFlags.CODEPOINT
import com.gagik.terminal.render.api.TerminalRenderCellFlags.EMPTY
import com.gagik.terminal.render.api.TerminalRenderCellFlags.WIDE_LEADING
import com.gagik.terminal.render.api.TerminalRenderCellFlags.WIDE_TRAILING


/**
 * Public render cell flag bit set.
 *
 * Valid combinations are:
 *
 * - [EMPTY]
 * - [CODEPOINT]
 * - [CODEPOINT] or [WIDE_LEADING]
 * - [CLUSTER]
 * - [CLUSTER] or [WIDE_LEADING]
 * - [WIDE_TRAILING]
 */
object TerminalRenderCellFlags {
    /**
     * No glyph should be drawn for this cell.
     */
    const val EMPTY: Int = 1 shl 0

    /**
     * The corresponding code word contains a Unicode scalar value.
     */
    const val CODEPOINT: Int = 1 shl 1

    /**
     * This cell contains a grapheme cluster delivered through
     * [TerminalRenderClusterSink].
     */
    const val CLUSTER: Int = 1 shl 2

    /**
     * This cell is the leading cell of a width-2 glyph or cluster.
     */
    const val WIDE_LEADING: Int = 1 shl 3

    /**
     * This cell is the trailing continuation cell of a width-2 glyph or cluster.
     * Renderers must not draw text for this cell.
     */
    const val WIDE_TRAILING: Int = 1 shl 4

    /**
     * Returns whether [flags] is one of the valid public render cell flag
     * combinations.
     *
     * @param flags flag bit set to validate.
     * @return `true` when the bit set is valid for one render cell.
     */
    fun isValidCombination(flags: Int): Boolean = when (flags) {
        EMPTY,
        CODEPOINT,
        CODEPOINT or WIDE_LEADING,
        CLUSTER,
        CLUSTER or WIDE_LEADING,
        WIDE_TRAILING,
        -> true
        else -> false
    }
}
