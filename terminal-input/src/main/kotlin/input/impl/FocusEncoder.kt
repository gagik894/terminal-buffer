package com.gagik.terminal.input.impl

import com.gagik.core.api.TerminalInputState
import com.gagik.terminal.input.event.TerminalFocusEvent
import com.gagik.terminal.protocol.host.TerminalHostOutput

internal class FocusEncoder(
    private val output: TerminalHostOutput,
) {
    fun encode(event: TerminalFocusEvent, modeBits: Long) {
        if (!TerminalInputState.isFocusReportingEnabled(modeBits)) {
            return
        }

        val sequence = if (event.focused) {
            TerminalSequences.FOCUS_IN
        } else {
            TerminalSequences.FOCUS_OUT
        }

        output.writeBytes(sequence, 0, sequence.size)
    }
}
