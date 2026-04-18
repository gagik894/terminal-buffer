package com.gagik.terminal.api

/**
 * Full public contract for the terminal buffer.
 *
 * Composes all role-specific interfaces into a single surface for host
 * applications and integration tests. The ANSI parser should depend only
 * on the narrower interfaces it actually needs ([TerminalWriter],
 * [TerminalCursor], [TerminalModeController]) rather than this full contract.
 *
 * All row and column indices are **0-based** unless a method explicitly states
 * otherwise (e.g. [TerminalWriter.setScrollRegion] follows the 1-based
 * DECSTBM convention).
 */
interface TerminalBufferApi :
    TerminalWriter,
    TerminalCursor,
    TerminalModeController,
    TerminalReader,
    TerminalInspector {

    /**
     * Resizes the terminal to [newWidth] × [newHeight].
     *
     * Existing content is reflowed to the new width. The cursor is relocated to
     * the corresponding position in the reflowed content. Scrollback history is
     * preserved within the configured capacity. The active scroll region is reset
     * to the full viewport. Both the primary and alternate grids are resized.
     *
     * @param newWidth  New terminal width in cells. Must be > 0.
     * @param newHeight New terminal height in rows. Must be > 0.
     * @throws IllegalArgumentException if either dimension is ≤ 0.
     */
    fun resize(newWidth: Int, newHeight: Int)

    /**
     * Performs a full terminal reset (RIS, `ESC c`).
     *
     * Clears all visible content and scrollback history; resets the pen to
     * defaults; homes the cursor; restores the scroll region to the full
     * viewport; resets all mode flags to their VT defaults; and restores tab
     * stops to the standard 8-column VT100 spacing. If the alternate buffer is
     * active, exits it first.
     */
    fun reset()
}