package com.gagik.parser.ansi

import com.gagik.parser.TerminalCommandSink

internal class RecordingTerminalCommandSink : TerminalCommandSink {
    data class Osc(
        val commandCode: Int,
        val payload: ByteArray,
        val length: Int,
        val overflowed: Boolean,
    )

    val osc = ArrayList<Osc>()

    override fun writeCodepoint(codepoint: Int) = Unit
    override fun writeCluster(codepoints: IntArray, length: Int, charWidth: Int) = Unit

    override fun bell() = Unit
    override fun backspace() = Unit
    override fun tab() = Unit
    override fun lineFeed() = Unit
    override fun carriageReturn() = Unit
    override fun reverseIndex() = Unit
    override fun nextLine() = Unit

    override fun saveCursor() = Unit
    override fun restoreCursor() = Unit

    override fun cursorUp(n: Int) = Unit
    override fun cursorDown(n: Int) = Unit
    override fun cursorForward(n: Int) = Unit
    override fun cursorBackward(n: Int) = Unit
    override fun cursorNextLine(n: Int) = Unit
    override fun cursorPreviousLine(n: Int) = Unit
    override fun setCursorColumn(col: Int) = Unit
    override fun setCursorRow(row: Int) = Unit
    override fun setCursorAbsolute(row: Int, col: Int) = Unit

    override fun eraseInDisplay(mode: Int, selective: Boolean) = Unit
    override fun eraseInLine(mode: Int, selective: Boolean) = Unit
    override fun insertLines(n: Int) = Unit
    override fun deleteLines(n: Int) = Unit
    override fun insertCharacters(n: Int) = Unit
    override fun deleteCharacters(n: Int) = Unit
    override fun eraseCharacters(n: Int) = Unit
    override fun scrollUp(n: Int) = Unit
    override fun scrollDown(n: Int) = Unit

    override fun setAnsiMode(mode: Int, enable: Boolean) = Unit
    override fun setDecMode(mode: Int, enable: Boolean) = Unit

    override fun onOsc(
        commandCode: Int,
        payload: ByteArray,
        length: Int,
        overflowed: Boolean,
    ) {
        osc += Osc(
            commandCode = commandCode,
            payload = payload.copyOf(length),
            length = length,
            overflowed = overflowed,
        )
    }
}