package com.gagik.terminal.input.api

import com.gagik.terminal.input.event.TerminalFocusEvent
import com.gagik.terminal.input.event.TerminalKeyEvent
import com.gagik.terminal.input.event.TerminalPasteEvent

/**
 * Encodes UI-level terminal input events into host-bound bytes.
 *
 * Implementations are responsible for reading the current input-facing mode
 * state at the appropriate event boundary and writing the resulting bytes to a
 * host output sink. Mouse encoding is intentionally excluded from this first
 * API milestone.
 */
interface TerminalInputEncoder {
    /**
     * Encodes one keyboard event.
     *
     * @param event non-printable key or printable Unicode scalar event.
     */
    fun encodeKey(event: TerminalKeyEvent)

    /**
     * Encodes one paste event.
     *
     * @param event pasted text event.
     */
    fun encodePaste(event: TerminalPasteEvent)

    /**
     * Encodes one focus transition event.
     *
     * @param event terminal focus transition.
     */
    fun encodeFocus(event: TerminalFocusEvent)
}
