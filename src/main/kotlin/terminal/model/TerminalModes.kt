package com.gagik.terminal.model

/**
 * Encapsulates all terminal behavioral mode flags.
 *
 * Allocated once inside [com.gagik.terminal.state.TerminalState] and mutated
 * in place — zero heap allocation in the hot loop.
 *
 * Each flag defaults to its VT/xterm-defined initial state.
 */
internal class TerminalModes {

    /** Mode 4 — Insert/Replace Mode (IRM). False = replace (default), true = insert. */
    var isInsertMode: Boolean = false

    /** Mode 7 — Auto-Wrap Mode (DECAWM). True = wrap at right margin (default). */
    var isAutoWrap: Boolean = true

    /** Mode 1 — Application Cursor Keys (DECCKM). False = normal cursor keys (default). */
    var isApplicationCursorKeys: Boolean = false

    /**
     * Whether ambiguous-width Unicode characters are treated as wide (2-cell).
     * False = treat as narrow (default).
     */
    var treatAmbiguousAsWide: Boolean = false

    /**
     * Resets all modes to their VT/xterm-defined defaults.
     * Called on hard reset.
     */
    fun reset() {
        isInsertMode            = false
        isAutoWrap              = true
        isApplicationCursorKeys = false
        treatAmbiguousAsWide    = false
    }
}