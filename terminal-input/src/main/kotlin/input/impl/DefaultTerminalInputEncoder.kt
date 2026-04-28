package com.gagik.terminal.input.impl

import com.gagik.core.api.TerminalInputState
import com.gagik.terminal.input.api.TerminalInputEncoder
import com.gagik.terminal.input.event.TerminalFocusEvent
import com.gagik.terminal.input.event.TerminalKeyEvent
import com.gagik.terminal.input.event.TerminalPasteEvent
import com.gagik.terminal.input.policy.TerminalInputPolicy
import com.gagik.terminal.protocol.host.TerminalHostOutput

/**
 * Default terminal input encoder facade.
 *
 * The facade reads a coherent packed mode snapshot once per event and passes
 * that stable value to the specialized encoder for the event family.
 *
 * Not thread-safe. Calls must be serialized by the terminal event loop.
 *
 * This is intentional: terminal-to-host byte ordering must be deterministic,
 * and the encoder reuses one scratch buffer to avoid per-event allocation.
 *
 * @param inputState read-only core mode state used for input decisions.
 * @param output host-bound byte sink.
 * @param policy policy for ambiguous or unsupported keyboard encodings.
 */
class DefaultTerminalInputEncoder(
    private val inputState: TerminalInputState,
    output: TerminalHostOutput,
    policy: TerminalInputPolicy = TerminalInputPolicy(),
) : TerminalInputEncoder {
    private val scratch = InputScratchBuffer()
    private val keyboard = KeyboardEncoder(output, scratch, policy)
    private val paste = PasteEncoder(output)
    private val focus = FocusEncoder(output)

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
     * Encodes one paste event using one packed mode read.
     *
     * @param event pasted text event.
     */
    override fun encodePaste(event: TerminalPasteEvent) {
        val modeBits = inputState.getInputModeBits()
        paste.encode(event, modeBits)
    }

    /**
     * Encodes one focus transition event using one packed mode read.
     *
     * @param event terminal focus transition.
     */
    override fun encodeFocus(event: TerminalFocusEvent) {
        val modeBits = inputState.getInputModeBits()
        focus.encode(event, modeBits)
    }
}
