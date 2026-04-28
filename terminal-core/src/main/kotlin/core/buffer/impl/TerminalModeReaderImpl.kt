package com.gagik.core.buffer.impl

import com.gagik.core.api.TerminalModeReader
import com.gagik.core.api.TerminalModeSnapshot
import com.gagik.core.state.TerminalState

internal class TerminalModeReaderImpl(
    private val state: TerminalState
) : TerminalModeReader {

    override fun getModeBitsSnapshot(): Long {
        return state.modes.getModeBitsSnapshot()
    }

    override fun getInputModeBits(): Long {
        return state.modes.getInputModeBits()
    }

    override fun getModeSnapshot(): TerminalModeSnapshot {
        return state.modes.getModeSnapshot()
    }
}
