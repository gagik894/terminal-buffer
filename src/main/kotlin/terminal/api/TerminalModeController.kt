package com.gagik.terminal.api

/**
 * Mode-control contract for the terminal buffer.
 *
 * These toggles affect how subsequent cursor motion and printable writes behave.
 * They do not expose the underlying storage model to the parser.
 */
interface TerminalModeController {

    /** Enables or disables Insert Replace Mode (IRM, `CSI 4 h` / `CSI 4 l`). */
    fun setInsertMode(enabled: Boolean)

    /** Enables or disables DECAWM auto-wrap (`CSI ? 7 h` / `CSI ? 7 l`). */
    fun setAutoWrap(enabled: Boolean)

    /**
     * Enables or disables Origin Mode (DECOM, `CSI ? 6 h` / `CSI ? 6 l`).
     *
     * When enabled, cursor-home semantics become relative to the active scroll
     * region rather than the full viewport.
     */
    fun setOriginMode(enabled: Boolean)

    /** Toggles application cursor key mode (DECCKM, `CSI ? 1 h` / `CSI ? 1 l`). */
    fun setApplicationCursorKeys(enabled: Boolean)

    /**
     * Controls how East Asian Ambiguous codepoints are measured for future writes.
     *
     * Existing stored content is not reinterpreted when this flag changes.
     */
    fun setTreatAmbiguousAsWide(enabled: Boolean)

    /**
     * Switches to the alternate screen buffer (ALTBUF on, `CSI ? 1049 h`).
     *
     * Saves the primary cursor position, pen attributes, and origin mode via
     * DECSC, clears the alternate grid, resets its scroll margins, and
     * activates it. The alternate buffer has no scrollback history.
     *
     * Re-entering the alternate buffer discards any previous alternate content.
     */
    fun enterAltBuffer()

    /**
     * Returns to the primary screen buffer (ALTBUF off, `CSI ? 1049 l`).
     *
     * Restores the primary cursor position, pen attributes, and origin mode
     * saved when [enterAltBuffer] was called. Alternate buffer content is not
     * visible after the switch and will be wiped on the next alternate-screen
     * entry or resize. Primary scrollback history is unaffected.
     */
    fun exitAltBuffer()
}