package com.gagik.terminal.input.impl

import com.gagik.core.api.TerminalInputState
import com.gagik.terminal.input.event.TerminalPasteEvent
import com.gagik.terminal.protocol.host.TerminalHostOutput

internal class PasteEncoder(
    private val output: TerminalHostOutput,
) {
    fun encode(event: TerminalPasteEvent, modeBits: Long) {
        if (TerminalInputState.isBracketedPasteEnabled(modeBits)) {
            output.writeBytes(
                TerminalSequences.BRACKETED_PASTE_START,
                0,
                TerminalSequences.BRACKETED_PASTE_START.size,
            )
            output.writeUtf8(event.text)
            output.writeBytes(
                TerminalSequences.BRACKETED_PASTE_END,
                0,
                TerminalSequences.BRACKETED_PASTE_END.size,
            )
        } else {
            output.writeUtf8(event.text)
        }
    }
}
