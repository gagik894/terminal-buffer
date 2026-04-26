package com.gagik.parser.text

import com.gagik.parser.ansi.PrintableActionSink
import com.gagik.parser.runtime.ParserState

/**
 * Adapter from ANSI ActionEngine printable callbacks to the real printable processor.
 */
internal class PrintableProcessorActionSink(
    private val processor: PrintableProcessor,
) : PrintableActionSink {
    override fun onAsciiByte(state: ParserState, byteValue: Int) {
        processor.acceptAsciiByte(state, byteValue)
    }

    override fun onUtf8Byte(state: ParserState, byteValue: Int) {
        error("UTF-8 payload must be decoded by TerminalParser before printable processing")
    }

    override fun flush(state: ParserState) {
        processor.flush(state)
    }
}
