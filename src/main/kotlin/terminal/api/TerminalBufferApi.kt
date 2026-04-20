package com.gagik.terminal.api

/**
 * Full public contract for the terminal buffer.
 *
 * Composes all role-specific interfaces into a single surface for host
 * applications and integration tests.
 *
 * Coordinates are always zero-based when they are expressed directly as rows
 * and columns on this API. DEC/ANSI commands that are traditionally 1-based
 * should be translated by the parser before they reach the core.
 *
 * The parser should depend only on the narrower interfaces it actually needs;
 * this facade mainly exists for host integration points and tests.
 */
interface TerminalBufferApi :
    TerminalWriter,
    TerminalCursor,
    TerminalModeController,
    TerminalReader,
    TerminalInspector {

    /**
     * Resizes the terminal to [newWidth] x [newHeight].
     *
     * Existing content is reflowed to the new width. The cursor is relocated to
     * the corresponding position in the reflowed content. Scrollback history is
     * preserved within the configured capacity. Both the primary and alternate
     * grids are resized, and both screen buffers reset their scroll regions to
     * the full viewport. Saved-cursor state is clamped to the new bounds.
     *
     * @param newWidth New terminal width in cells. Must be > 0.
     * @param newHeight New terminal height in rows. Must be > 0.
     * @throws IllegalArgumentException if either dimension is <= 0.
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

    /**
     * Executes DECCOLM (`CSI ? 3 h` / `CSI ? 3 l`) as a core-owned macro command.
     *
     * Valid widths are `80` and `132`; all other values are ignored.
     *
     * Sequence:
     * 1. Resize both buffers to `newWidth × currentHeight`
     * 2. Destructively clear the active display and its history
     * 3. Home the active cursor to absolute `(0, 0)` regardless of DECOM
     * 4. Reset active scroll margins to the full viewport
     * 5. Reset active left/right margins to the full width
     * 6. Reset tab stops to the default 8-column rhythm for the new width
     * 7. Cancel pending wrap
     * 8. Preserve both DECSC saved-cursor slots unchanged
     *
     * When the alternate screen is active, DECCOLM follows the xterm-style
     * policy used by [resize]: the alternate screen is wiped at the new width
     * while the primary screen is reflowed in the background.
     */
    fun executeDeccolm(newWidth: Int)
}
