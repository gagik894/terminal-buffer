package com.gagik.core.model

/**
 * Encapsulates all terminal behavioral mode flags.
 *
 * Allocated once inside `TerminalState` and mutated in place; there is no
 * hot-path allocation here.
 *
 * This object is the shared source of truth for durable mode state toggled by
 * the host. Some flags are consumed by core mutation/cursor logic today, while
 * others are staged here for future parser, input, and renderer coordination.
 *
 * Each flag defaults to its VT/xterm-style initial state.
 */
internal class TerminalModes {

    /** Mode 4: Insert/Replace Mode (IRM). False = replace (default), true = insert. */
    var isInsertMode: Boolean = false

    /** Mode 7: Auto-Wrap Mode (DECAWM). True = wrap at right margin (default). */
    var isAutoWrap: Boolean = true

    /** Mode 1: Application Cursor Keys (DECCKM). False = normal cursor keys (default). */
    var isApplicationCursorKeys: Boolean = false

    /** DECNKM application keypad mode. False = numeric keypad (default). */
    var isApplicationKeypad: Boolean = false

    /** Mode 6: Origin Mode (DECOM). False = absolute, true = relative to scroll region. */
    var isOriginMode: Boolean = false

    /** Mode 20: New Line Mode (LNM). False = LF only, true = LF also performs CR. */
    var isNewLineMode: Boolean = false

    /**
     * DEC left/right margin mode (DECLRMM, `CSI ? 69 h`).
     * False = full-width semantics (default).
     */
    var isLeftRightMarginMode: Boolean = false

    /** Reverse-video presentation flag (DECSCNM). False = normal video (default). */
    var isReverseVideo: Boolean = false

    /** Cursor visibility presentation flag. True = cursor visible (default). */
    var isCursorVisible: Boolean = true

    /** Cursor blink presentation flag. False = steady cursor (default). */
    var isCursorBlinking: Boolean = false

    /** Bracketed paste reporting mode. False = disabled (default). */
    var isBracketedPasteEnabled: Boolean = false

    /** Focus in/out reporting mode. False = disabled (default). */
    var isFocusReportingEnabled: Boolean = false

    /**
     * Whether ambiguous-width Unicode characters are treated as wide (2-cell).
     * False = treat as narrow (default).
     */
    var treatAmbiguousAsWide: Boolean = false

    /** Mouse reporting selection. Defaults to [MouseTrackingMode.OFF]. */
    var mouseTrackingMode: MouseTrackingMode = MouseTrackingMode.OFF

    /** Mouse encoding selection. Defaults to [MouseEncodingMode.DEFAULT]. */
    var mouseEncodingMode: MouseEncodingMode = MouseEncodingMode.DEFAULT

    /**
     * Modify-other-keys level.
     *
     * `0` means disabled. Higher values are parser/input-layer specific policy
     * and are stored here only as durable shared state.
     */
    var modifyOtherKeysMode: Int = 0

    /**
     * Resets all modes to their VT/xterm-style defaults.
     * Called on hard reset.
     */
    fun reset() {
        isInsertMode = false
        isAutoWrap = true
        isApplicationCursorKeys = false
        isApplicationKeypad = false
        isOriginMode = false
        isNewLineMode = false
        isLeftRightMarginMode = false
        isReverseVideo = false
        isCursorVisible = true
        isCursorBlinking = false
        isBracketedPasteEnabled = false
        isFocusReportingEnabled = false
        treatAmbiguousAsWide = false
        mouseTrackingMode = MouseTrackingMode.OFF
        mouseEncodingMode = MouseEncodingMode.DEFAULT
        modifyOtherKeysMode = 0
    }
}
