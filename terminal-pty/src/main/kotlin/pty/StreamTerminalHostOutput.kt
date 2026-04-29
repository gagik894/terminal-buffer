package com.gagik.terminal.pty

import com.gagik.terminal.protocol.host.TerminalHostOutput
import java.io.Closeable
import java.io.OutputStream
import java.nio.charset.StandardCharsets

/**
 * [TerminalHostOutput] backed by the PTY stdin stream.
 *
 * This class is synchronized at the stream boundary so direct callers cannot
 * interleave byte ranges. Higher-level ordering is owned by [TerminalPtySession].
 * [writeBytes] consumes the provided range synchronously.
 *
 * [writeAscii] avoids intermediate byte-array allocation. [writeUtf8] currently
 * allocates through the JVM UTF-8 encoder; hot key paths should prefer
 * [writeByte] or [writeBytes].
 *
 * @param output PTY stdin stream.
 * @param flushAfterWrite when true, every successful write is flushed.
 */
class StreamTerminalHostOutput internal constructor(
    private val output: OutputStream,
    private val flushAfterWrite: Boolean = true,
) : TerminalHostOutput, Closeable {
    /**
     * Writes one unsigned byte to the PTY stdin stream.
     */
    @Synchronized
    override fun writeByte(byte: Int) {
        require(byte in 0..255) { "Host byte must be in 0..255, got $byte" }

        output.write(byte)
        flushIfNeeded()
    }

    /**
     * Writes a contiguous byte range to PTY stdin.
     */
    @Synchronized
    override fun writeBytes(bytes: ByteArray, offset: Int, length: Int) {
        require(offset >= 0) { "offset must be non-negative, got $offset" }
        require(length >= 0) { "length must be non-negative, got $length" }
        require(offset <= bytes.size) { "offset $offset exceeds size ${bytes.size}" }
        require(length <= bytes.size - offset) {
            "offset + length exceeds size: offset=$offset length=$length size=${bytes.size}"
        }

        output.write(bytes, offset, length)
        flushIfNeeded()
    }

    /**
     * Writes ASCII text to PTY stdin.
     */
    @Synchronized
    override fun writeAscii(text: String) {
        var index = 0
        while (index < text.length) {
            val code = text[index].code
            require(code in 0..0x7F) { "Non-ASCII character at index $index: $code" }
            output.write(code)
            index++
        }

        flushIfNeeded()
    }

    /**
     * Writes UTF-8 text to PTY stdin. This path allocates an encoded byte array.
     */
    @Synchronized
    override fun writeUtf8(text: String) {
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        output.write(bytes, 0, bytes.size)
        flushIfNeeded()
    }

    /**
     * Closes the PTY stdin stream.
     */
    @Synchronized
    override fun close() {
        output.close()
    }

    private fun flushIfNeeded() {
        if (flushAfterWrite) {
            output.flush()
        }
    }
}
