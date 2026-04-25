package com.gagik.parser.ansi

import com.gagik.parser.TerminalCommandSink
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
        }
    }

    override fun dispatchEsc(
        sink: TerminalCommandSink,
        state: ParserState,
        finalByte: Int,
    ) {
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
        when (finalByte) {
            'A'.code -> sink.cursorUp(countParam(state, 0))
            'B'.code -> sink.cursorDown(countParam(state, 0))
            'C'.code -> sink.cursorForward(countParam(state, 0))
            'D'.code -> sink.cursorBackward(countParam(state, 0))
            'H'.code, 'f'.code -> sink.setCursorAbsolute(
                row = oneBasedPositionParam(state, 0),
                col = oneBasedPositionParam(state, 1),
            )
            'J'.code -> sink.eraseInDisplay(modeParam(state, 0), selective = false)
            'K'.code -> sink.eraseInLine(modeParam(state, 0), selective = false)
            'L'.code -> sink.insertLines(countParam(state, 0))
            'M'.code -> sink.deleteLines(countParam(state, 0))
            '@'.code -> sink.insertCharacters(countParam(state, 0))
            'P'.code -> sink.deleteCharacters(countParam(state, 0))
            'X'.code -> sink.eraseCharacters(countParam(state, 0))
            'S'.code -> sink.scrollUp(countParam(state, 0))
            'T'.code -> sink.scrollDown(countParam(state, 0))
            'h'.code -> dispatchMode(sink, state, enable = true)
            'l'.code -> dispatchMode(sink, state, enable = false)
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

    private fun dispatchMode(
        sink: TerminalCommandSink,
        state: ParserState,
        enable: Boolean,
    ) {
        when (state.privateMarker) {
            0 -> dispatchAnsiMode(sink, state, enable)
            '?'.code -> dispatchDecMode(sink, state, enable)
        }
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
}
