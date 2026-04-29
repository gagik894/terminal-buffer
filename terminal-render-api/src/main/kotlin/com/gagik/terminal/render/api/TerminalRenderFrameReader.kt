package com.gagik.terminal.render.api

/**
 * Provides a short-lived render frame view.
 *
 * The frame passed to [consumer] is valid only during the callback.
 * Implementations may hold a terminal mutation lock while invoking [consumer].
 * Consumers must copy anything they need before returning.
 */
interface TerminalRenderFrameReader {
    /**
     * Invokes [consumer] with a render frame view whose lifetime is limited to
     * this call.
     *
     * Implementations may reuse the same frame object across calls, so consumers
     * must not retain the frame after [consumer] returns.
     *
     * @param consumer receiver that copies any render data it needs.
     */
    fun readRenderFrame(consumer: TerminalRenderFrameConsumer)
}
