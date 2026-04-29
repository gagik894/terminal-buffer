package com.gagik.terminal.pty

import com.gagik.terminal.protocol.host.TerminalHostOutput
import java.io.Closeable
import java.io.OutputStream
import java.nio.charset.StandardCharsets

/**
 * [TerminalHostOutput] implementation backed by a PTY stdin stream.
 *
 * Calls are synchronized so parser/core responses and serialized input events
 * keep byte order at the stream boundary.
 */
class StreamTerminalHostOutput internal constructor(
    private val output: OutputStream,
) : TerminalHostOutput, Closeable {
    /**
     * Writes one unsigned byte to the PTY stdin stream.
     */
    @Synchronized
    override fun writeByte(byte: Int) {
        require(byte in 0..255) { "Host byte must be in 0..255, got $byte" }
        output.write(byte)
        output.flush()
    }

    /**
     * Writes a contiguous byte range to PTY stdin.
     */
    @Synchronized
    override fun writeBytes(bytes: ByteArray, offset: Int, length: Int) {
        require(offset >= 0) { "offset must be >= 0, got $offset" }
        require(length >= 0) { "length must be >= 0, got $length" }
        require(offset <= bytes.size - length) {
            "offset + length must fit bytes.size=${bytes.size}, got offset=$offset length=$length"
        }
        output.write(bytes, offset, length)
        output.flush()
    }

    /**
     * Writes ASCII text to PTY stdin.
     */
    @Synchronized
    override fun writeAscii(text: String) {
        for (index in text.indices) {
            require(text[index].code <= 0x7F) { "Non-ASCII character at index $index" }
        }
        writeBytes(text.toByteArray(StandardCharsets.US_ASCII), 0, text.length)
    }

    /**
     * Writes UTF-8 text to PTY stdin.
     */
    @Synchronized
    override fun writeUtf8(text: String) {
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        writeBytes(bytes, 0, bytes.size)
    }

    /**
     * Closes the PTY stdin stream.
     */
    @Synchronized
    override fun close() {
        output.close()
    }
}
