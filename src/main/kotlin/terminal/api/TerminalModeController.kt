package com.gagik.terminal.api

/**
 * Mode-control contract for the terminal buffer.
 *
 * Consumed by the ANSI parser when it processes `SM` / `RM` (`CSI h` / `CSI l`)
 * and `DECSET` / `DECRST` (`CSI ? h` / `CSI ? l`) sequences, and by the host
 * application for one-time initialisation settings.
 */
interface TerminalModeController {

    /**
     * Sets Insert/Replace Mode (IRM, `CSI 4 h` / `CSI 4 l`).
     *
     * - `true`  — insert mode: characters shift existing content right before writing.
     * - `false` — replace mode: characters overwrite the cell at the cursor (default).
     *
     * @param enabled `true` to enable insert mode.
     */
    fun setInsertMode(enabled: Boolean)

    /**
     * Sets Auto-Wrap Mode (DECAWM, `CSI ? 7 h` / `CSI ? 7 l`).
     *
     * - `true`  — cursor wraps to the next line at the right margin (default).
     * - `false` — cursor clamps at the right margin; subsequent writes overwrite
     *             the last cell.
     *
     * @param enabled `true` to enable auto-wrap.
     */
    fun setAutoWrap(enabled: Boolean)

    /**
     * Sets Origin Mode (DECOM, `CSI ? 6 h` / `CSI ? 6 l`).
     *
     * - `true`  — cursor row coordinates are relative to the top of the active
     *             scroll region and clamped within it.
     * - `false` — cursor row coordinates are absolute in the viewport (default).
     *
     * Changing this mode homes the cursor to `(0, 0)` in the new coordinate system.
     *
     * @param enabled `true` to enable origin mode.
     */
    fun setOriginMode(enabled: Boolean)

    /**
     * Sets Application Cursor Keys mode (DECCKM, `CSI ? 1 h` / `CSI ? 1 l`).
     *
     * - `true`  — cursor keys emit application sequences (`ESC O A/B/C/D`).
     * - `false` — cursor keys emit normal sequences (`ESC [ A/B/C/D`) (default).
     *
     * This is a **global** mode — it is not saved or restored on alternate screen
     * switches, as it affects keyboard output rather than screen state.
     *
     * @param enabled `true` to activate application cursor keys.
     */
    fun setApplicationCursorKeys(enabled: Boolean)

    /**
     * Controls how ambiguous-width Unicode characters are measured.
     *
     * - `true`  — ambiguous characters (e.g. U+00B7, U+2019) occupy 2 cells.
     * - `false` — ambiguous characters occupy 1 cell (default; matches most
     *             Western locales).
     *
     * This is a **global** mode. Set it once at terminal initialisation to match
     * the host locale. Changing it mid-session causes column misalignment for
     * text already on screen.
     *
     * @param enabled `true` to treat ambiguous-width characters as wide.
     */
    fun setTreatAmbiguousAsWide(enabled: Boolean)

    /**
     * Switches to the alternate screen buffer (ALTBUF on, `CSI ? 1049 h`).
     *
     * Natively saves the primary cursor position, pen attributes, and origin mode
     * (DECOM) via DECSC. It then clears the alternate grid, resets its scroll
     * margins, and activates it. All subsequent read and write operations target
     * the alternate buffer. The alternate buffer has no scrollback history.
     *
     * **Note:** Hardware modes (such as Insert Mode or Auto-Wrap) are global in
     * the VT500 specification. They are explicitly NOT saved per-screen.
     *
     * No-op if already in the alternate buffer.
     */
    fun enterAltBuffer()

    /**
     * Returns to the primary screen buffer (ALTBUF off, `CSI ? 1049 l`).
     *
     * Natively restores the primary cursor position, pen attributes, and origin
     * mode (DECOM) that were saved via DECRC when [enterAltBuffer] was called.
     * Alternate buffer content is discarded. Primary scrollback history is unaffected.
     *
     * **Note:** Global hardware modes mutated during the alternate session
     * (such as IRM or DECAWM) are preserved and do not revert upon exit.
     *
     * No-op if already on the primary buffer.
     */
    fun exitAltBuffer()
}