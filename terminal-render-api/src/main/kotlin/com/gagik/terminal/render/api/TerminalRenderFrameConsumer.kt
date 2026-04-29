package com.gagik.terminal.render.api

/**
 * Receives a short-lived [TerminalRenderFrame] during a render read callback.
 */
fun interface TerminalRenderFrameConsumer {
    /**
     * Consumes [frame] before the enclosing read callback returns.
     *
     * @param frame render frame view valid only for the callback duration.
     */
    fun accept(frame: TerminalRenderFrame)
}
