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
}