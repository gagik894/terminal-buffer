package com.gagik.terminal.input.impl

import com.gagik.core.api.TerminalInputState
import com.gagik.terminal.input.api.TerminalInputEncoder
import com.gagik.terminal.input.event.TerminalFocusEvent
import com.gagik.terminal.input.event.TerminalKeyEvent
import com.gagik.terminal.input.event.TerminalPasteEvent
import com.gagik.terminal.protocol.host.TerminalHostOutput

/**
 * Default terminal input encoder facade.
 *
 * The facade reads a coherent packed mode snapshot once per event and passes
 * that stable value to the specialized encoder for the event family. Paste and
 * focus behavior are intentionally deferred to later implementation phases.
 *
 * @param inputState read-only core mode state used for input decisions.
 * @param output host-bound byte sink.
 */
class DefaultTerminalInputEncoder(
    private val inputState: TerminalInputState,
    output: TerminalHostOutput,
) : TerminalInputEncoder {
    private val scratch = InputScratchBuffer()
    private val keyboard = KeyboardEncoder(output, scratch)

    /**
     * Encodes one keyboard event using one packed mode read.
     *
     * @param event non-printable key or printable Unicode scalar event.
     */
    override fun encodeKey(event: TerminalKeyEvent) {
        val modeBits = inputState.getInputModeBits()
        keyboard.encode(event, modeBits)
    }

    /**
     * Paste encoding is deferred to the paste encoder phase.
     *
     * @param event pasted text event.
     * @throws UnsupportedOperationException until the paste phase is implemented.
     */
    override fun encodePaste(event: TerminalPasteEvent) {
        throw UnsupportedOperationException("paste encoding is not implemented yet")
    }

    /**
     * Focus encoding is deferred to the focus encoder phase.
     *
     * @param event terminal focus transition.
     * @throws UnsupportedOperationException until the focus phase is implemented.
     */
    override fun encodeFocus(event: TerminalFocusEvent) {
        throw UnsupportedOperationException("focus encoding is not implemented yet")
    }
}
