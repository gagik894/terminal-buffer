package com.gagik.terminal.model

/**
 * Sentinel values stored directly in a Line's raw codepoint array.
 *
 * Cell value encoding contract (all values are Ints):
 * The gap between SPACER (-1) and the first valid cluster handle (-2) is intentional.
 * It lets any consumer distinguish the three cases with a single comparison:
 *   value > 0 → codepoint
 *   value == 0 → empty
 *   value == -1 → spacer
 *   value <= -2 → cluster handle
 */
internal object TerminalConstants {
    /** A blank, unwritten cell. */
    const val EMPTY: Int = 0

    /**
     * Placeholder occupying the right column of a 2-cell wide character.
     * The cell immediately to the left holds the leader codepoint or cluster handle.
     */
    const val WIDE_CHAR_SPACER: Int = -1

    /**
     * All cluster handles are <= this value.
     * Using `rawValue <= CLUSTER_HANDLE_MAX` reliably detects any handle.
     */
    const val CLUSTER_HANDLE_MAX: Int = -2
}