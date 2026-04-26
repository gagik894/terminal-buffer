package com.gagik.parser.api

import com.gagik.parser.spi.TerminalCommandSink

/**
 * Byte-stream parser contract for terminal host output.
 *
 * Implementations accept raw bytes from PTY/network/process output, preserve parser state across
 * arbitrary chunk boundaries, and emit semantic terminal operations to a [TerminalCommandSink].
 */
interface TerminalOutputParser {
    fun accept(
        bytes: ByteArray,
        offset: Int = 0,
        length: Int = bytes.size,
    )

    fun acceptByte(byteValue: Int)

    fun endOfInput()

    fun reset()
}
