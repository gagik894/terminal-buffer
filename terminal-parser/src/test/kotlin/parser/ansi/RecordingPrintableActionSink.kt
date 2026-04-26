package com.gagik.parser.ansi

import com.gagik.parser.runtime.ParserState

internal class RecordingPrintableActionSink : PrintableActionSink {
    val asciiBytes = ArrayList<Int>()
    val utf8Bytes = ArrayList<Int>()
    var flushCount: Int = 0

    override fun onAsciiByte(state: ParserState, byteValue: Int) {
        asciiBytes += byteValue
    }

    override fun onUtf8Byte(state: ParserState, byteValue: Int) {
        utf8Bytes += byteValue
    }

    override fun flush(state: ParserState) {
        flushCount++
    }
}
