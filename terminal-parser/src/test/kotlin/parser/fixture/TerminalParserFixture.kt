package com.gagik.parser.fixture

import com.gagik.parser.ansi.RecordingTerminalCommandSink
import com.gagik.parser.impl.TerminalParser
import com.gagik.parser.runtime.ParserState

internal class TerminalParserFixture(
    val sink: RecordingTerminalCommandSink = RecordingTerminalCommandSink(),
    val state: ParserState = ParserState(),
) {
    val parser = TerminalParser(sink, state)

    fun acceptAscii(text: String) {
        parser.accept(text.encodeToByteArray())
    }

    fun acceptUtf8(text: String) {
        parser.accept(text.encodeToByteArray())
    }

    fun acceptBytes(vararg byteValues: Int) {
        parser.accept(byteValues.map { it.toByte() }.toByteArray())
    }

    fun acceptByte(byteValue: Int) {
        parser.acceptByte(byteValue)
    }

    fun endOfInput() {
        parser.endOfInput()
    }

    fun reset() {
        parser.reset()
    }
}
