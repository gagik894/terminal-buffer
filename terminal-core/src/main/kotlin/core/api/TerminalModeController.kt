package com.gagik.core.api

import com.gagik.core.model.MouseEncodingMode
import com.gagik.core.model.MouseTrackingMode

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

    /** Toggles application keypad mode (DECNKM). */
    fun setApplicationKeypad(enabled: Boolean)

    /**
     * Enables or disables left/right margin mode (DECLRMM, `CSI ? 69 h` / `CSI ? 69 l`).
     *
     * The mode flag is global, while the actual horizontal margins are stored
     * per screen buffer. Enabling or disabling the mode homes the cursor.
     */
    fun setLeftRightMarginMode(enabled: Boolean)

    /** Enables or disables New Line Mode (LNM, `CSI 20 h` / `CSI 20 l`). */
    fun setNewLineMode(enabled: Boolean)

    /** Sets the active mouse tracking mode used by terminal-to-host reporting. */
    fun setMouseTrackingMode(mode: MouseTrackingMode)

    /** Sets the active mouse report encoding mode used by terminal-to-host reporting. */
    fun setMouseEncodingMode(mode: MouseEncodingMode)

    /** Enables or disables bracketed paste reporting (`CSI ? 2004 h` / `CSI ? 2004 l`). */
    fun setBracketedPasteEnabled(enabled: Boolean)

    /** Enables or disables focus in/out reporting (`CSI ? 1004 h` / `CSI ? 1004 l`). */
    fun setFocusReportingEnabled(enabled: Boolean)

    /** Sets the modify-other-keys reporting level. */
    fun setModifyOtherKeysMode(mode: Int)

    /**
     * Toggles reverse-video presentation state (DECSCNM, `CSI ? 5 h` / `CSI ? 5 l`).
     *
     * This is renderer-facing state stored in core because the host controls it.
     */
    fun setReverseVideo(enabled: Boolean)

    /**
     * Toggles cursor visibility presentation state (DECTCEM, `CSI ? 25 h` / `CSI ? 25 l`).
     *
     * This is renderer-facing state stored in core because the host controls it.
     */
    fun setCursorVisible(enabled: Boolean)

    /** Toggles cursor blink presentation state. */
    fun setCursorBlinking(enabled: Boolean)

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
