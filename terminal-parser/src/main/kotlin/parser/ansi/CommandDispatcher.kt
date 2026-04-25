package com.gagik.parser.ansi

import com.gagik.parser.TerminalCommandSink
import com.gagik.parser.charset.CharsetMapper
import com.gagik.parser.runtime.ParserState

/**
 * Semantic dispatcher boundary used by ActionEngine.
 *
 * ESC/CSI/control meaning lives here, not in the matrix and not in ActionEngine.
 */
internal interface CommandDispatcher {
    fun executeControl(
        sink: TerminalCommandSink,
        state: ParserState,
        controlByte: Int,
    )

    fun dispatchEsc(
        sink: TerminalCommandSink,
        state: ParserState,
        finalByte: Int,
    )

    fun dispatchCsi(
        sink: TerminalCommandSink,
        state: ParserState,
        finalByte: Int,
    )
}

internal object AnsiCommandDispatcher : CommandDispatcher {

    override fun executeControl(
        sink: TerminalCommandSink,
        state: ParserState,
        controlByte: Int,
    ) {
        when (controlByte) {
            0x07 -> sink.bell()
            0x08 -> sink.backspace()
            0x09 -> sink.tab()
            0x0A, 0x0B, 0x0C -> sink.lineFeed()
            0x0D -> sink.carriageReturn()
            0x0e -> CharsetMapper.lockingShiftG1(state) // SO
            0x0f -> CharsetMapper.lockingShiftG0(state) // SI
        }
    }

    override fun dispatchEsc(
        sink: TerminalCommandSink,
        state: ParserState,
        finalByte: Int,
    ) {
        if (dispatchCharsetDesignation(state, finalByte)) {
            return
        }

        if (state.intermediateCount != 0) {
            return
        }

        when (finalByte) {
            '7'.code -> sink.saveCursor()
            '8'.code -> sink.restoreCursor()
            'D'.code -> sink.lineFeed()
            'E'.code -> sink.nextLine()
            'M'.code -> sink.reverseIndex()
        }
    }

    override fun dispatchCsi(
        sink: TerminalCommandSink,
        state: ParserState,
        finalByte: Int,
    ) {
        val signature = CsiSignature.encode(
            finalByte = finalByte,
            privateMarker = state.privateMarker,
            intermediates = state.intermediates,
            intermediateCount = state.intermediateCount,
        )

        when (GeneratedCsiDispatchTable.lookup(signature)) {
            CsiCommand.UNKNOWN -> Unit

            CsiCommand.CUU -> sink.cursorUp(countParam(state, 0))
            CsiCommand.CUD -> sink.cursorDown(countParam(state, 0))
            CsiCommand.CUF -> sink.cursorForward(countParam(state, 0))
            CsiCommand.CUB -> sink.cursorBackward(countParam(state, 0))
            CsiCommand.CNL -> sink.cursorNextLine(countParam(state, 0))
            CsiCommand.CPL -> sink.cursorPreviousLine(countParam(state, 0))
            CsiCommand.CHA -> sink.setCursorColumn(oneBasedPositionParam(state, 0))
            CsiCommand.CUP -> sink.setCursorAbsolute(
                row = oneBasedPositionParam(state, 0),
                col = oneBasedPositionParam(state, 1),
            )
            CsiCommand.VPA -> sink.setCursorRow(oneBasedPositionParam(state, 0))

            CsiCommand.ED -> sink.eraseInDisplay(modeParam(state, 0), selective = false)
            CsiCommand.EL -> sink.eraseInLine(modeParam(state, 0), selective = false)
            CsiCommand.IL -> sink.insertLines(countParam(state, 0))
            CsiCommand.DL -> sink.deleteLines(countParam(state, 0))
            CsiCommand.ICH -> sink.insertCharacters(countParam(state, 0))
            CsiCommand.DCH -> sink.deleteCharacters(countParam(state, 0))
            CsiCommand.ECH -> sink.eraseCharacters(countParam(state, 0))
            CsiCommand.SU -> sink.scrollUp(countParam(state, 0))
            CsiCommand.SD -> sink.scrollDown(countParam(state, 0))

            CsiCommand.SM_ANSI -> dispatchAnsiMode(sink, state, enable = true)
            CsiCommand.RM_ANSI -> dispatchAnsiMode(sink, state, enable = false)
            CsiCommand.SM_DEC -> dispatchDecMode(sink, state, enable = true)
            CsiCommand.RM_DEC -> dispatchDecMode(sink, state, enable = false)

            CsiCommand.DECSTR -> sink.softReset()
        }
    }

    private fun countParam(state: ParserState, index: Int): Int {
        val value = paramOrMissing(state, index)
        return if (value <= 0) 1 else value
    }

    private fun modeParam(state: ParserState, index: Int): Int {
        val value = paramOrMissing(state, index)
        return if (value < 0) 0 else value
    }

    private fun oneBasedPositionParam(state: ParserState, index: Int): Int {
        val value = paramOrMissing(state, index)
        return if (value <= 0) 0 else value - 1
    }

    private fun paramOrMissing(state: ParserState, index: Int): Int {
        return if (index < state.paramCount) state.params[index] else -1
    }

    private fun dispatchAnsiMode(
        sink: TerminalCommandSink,
        state: ParserState,
        enable: Boolean,
    ) {
        forEachMaterializedMode(state) { mode ->
            sink.setAnsiMode(mode, enable)
        }
    }

    private fun dispatchDecMode(
        sink: TerminalCommandSink,
        state: ParserState,
        enable: Boolean,
    ) {
        forEachMaterializedMode(state) { mode ->
            sink.setDecMode(mode, enable)
        }
    }

    private inline fun forEachMaterializedMode(
        state: ParserState,
        block: (Int) -> Unit,
    ) {
        var i = 0
        while (i < state.paramCount) {
            val mode = state.params[i]
            if (mode >= 0) {
                block(mode)
            }
            i++
        }
    }

    private fun dispatchCharsetDesignation(
        state: ParserState,
        finalByte: Int,
    ): Boolean {
        if (state.intermediateCount != 1) {
            return false
        }

        val slot = when (state.intermediates and 0xff) {
            '('.code -> 0
            ')'.code -> 1
            '*'.code -> 2
            '+'.code -> 3
            else -> return false
        }

        when (finalByte) {
            'B'.code -> CharsetMapper.designateAscii(state, slot)
            '0'.code -> CharsetMapper.designateDecSpecialGraphics(state, slot)
            else -> return true // Recognized designation shape, unsupported charset final ignored.
        }

        return true
    }
}
