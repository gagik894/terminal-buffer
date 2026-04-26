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
        if (processor.hasPendingUtf8Sequence()) {
            processor.acceptUtf8DecoderByte(state, byteValue)
        } else {
            processor.acceptAsciiByte(state, byteValue)
        }
    }

    override fun onUtf8Byte(state: ParserState, byteValue: Int) {
        processor.acceptUtf8Byte(state, byteValue)
    }

    override fun flush(state: ParserState) {
        processor.flush(state)
    }
}
