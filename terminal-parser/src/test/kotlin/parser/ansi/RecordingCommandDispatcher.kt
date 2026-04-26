package com.gagik.parser.ansi

import com.gagik.parser.runtime.ParserState
import com.gagik.parser.spi.TerminalCommandSink

internal class RecordingCommandDispatcher : CommandDispatcher {
    data class C0(val byte: Int)
    data class Esc(
        val finalByte: Int,
        val intermediates: Int,
        val intermediateCount: Int,
    )

    data class Csi(
        val finalByte: Int,
        val params: List<Int>,
        val privateMarker: Int,
        val intermediates: Int,
        val intermediateCount: Int,
        val subParameterMask: Int,
    )

    val controls = ArrayList<C0>()
    val esc = ArrayList<Esc>()
    val csi = ArrayList<Csi>()

    override fun executeControl(
        sink: TerminalCommandSink,
        state: ParserState,
        controlByte: Int,
    ) {
        controls += C0(controlByte)
    }

    override fun dispatchEsc(
        sink: TerminalCommandSink,
        state: ParserState,
        finalByte: Int,
    ) {
        esc += Esc(
            finalByte = finalByte,
            intermediates = state.intermediates,
            intermediateCount = state.intermediateCount,
        )
    }

    override fun dispatchCsi(
        sink: TerminalCommandSink,
        state: ParserState,
        finalByte: Int,
    ) {
        val params = ArrayList<Int>(state.paramCount)
        var i = 0
        while (i < state.paramCount) {
            params += state.params[i]
            i++
        }

        csi += Csi(
            finalByte = finalByte,
            params = params,
            privateMarker = state.privateMarker,
            intermediates = state.intermediates,
            intermediateCount = state.intermediateCount,
            subParameterMask = state.subParameterMask,
        )
    }
}
