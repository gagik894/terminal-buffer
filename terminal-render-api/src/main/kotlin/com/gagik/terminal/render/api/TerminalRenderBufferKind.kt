package com.gagik.terminal.render.api

/**
 * Identifies the visible terminal screen buffer.
 */
enum class TerminalRenderBufferKind {
    /**
     * Primary scrollback-backed screen buffer.
     */
    PRIMARY,

    /**
     * Alternate full-screen application buffer.
     */
    ALTERNATE,
}
