package com.gagik.terminal.pty

import com.gagik.terminal.protocol.host.TerminalHostOutput
import java.io.Closeable
import java.io.OutputStream

/**
 * [TerminalHostOutput] backed by the PTY stdin stream.
 *
 * This class is synchronized at the stream boundary so direct callers cannot
 * interleave byte ranges. Higher-level ordering is owned by [TerminalPtySession].
 * [writeBytes] consumes the provided range synchronously.
 *
 * [writeAscii] and [writeUtf8] avoid per-call encoded byte-array allocation.
 *
 * @param output PTY stdin stream.
 * @param flushAfterWrite when true, every successful write is flushed.
 */
class StreamTerminalHostOutput internal constructor(
    private val output: OutputStream,
    private val flushAfterWrite: Boolean = true,
) : TerminalHostOutput, Closeable {
    private val utf8Buffer = ByteArray(UTF8_BUFFER_SIZE)

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
     * Writes UTF-8 text to PTY stdin.
     *
     * Invalid UTF-16 surrogate code units are encoded as U+FFFD, matching the
     * replacement behavior expected by host-bound text encoding.
     */
    @Synchronized
    override fun writeUtf8(text: String) {
        if (text.isEmpty()) {
            flushIfNeeded()
            return
        }

        var bufferOffset = 0
        var charIndex = 0
        val length = text.length

        while (charIndex < length) {
            val ch = text[charIndex]
            val codepoint: Int

            if (Character.isHighSurrogate(ch)) {
                if (
                    charIndex + 1 < length &&
                    Character.isLowSurrogate(text[charIndex + 1])
                ) {
                    codepoint = Character.toCodePoint(ch, text[charIndex + 1])
                    charIndex += 2
                } else {
                    codepoint = REPLACEMENT_CODEPOINT
                    charIndex++
                }
            } else if (Character.isLowSurrogate(ch)) {
                codepoint = REPLACEMENT_CODEPOINT
                charIndex++
            } else {
                codepoint = ch.code
                charIndex++
            }

            val bytesNeeded = when {
                codepoint <= 0x7F -> 1
                codepoint <= 0x7FF -> 2
                codepoint <= 0xFFFF -> 3
                else -> 4
            }

            if (bufferOffset + bytesNeeded > utf8Buffer.size) {
                output.write(utf8Buffer, 0, bufferOffset)
                bufferOffset = 0
            }

            when (bytesNeeded) {
                1 -> {
                    utf8Buffer[bufferOffset++] = codepoint.toByte()
                }
                2 -> {
                    utf8Buffer[bufferOffset++] = (0xC0 or (codepoint shr 6)).toByte()
                    utf8Buffer[bufferOffset++] = (0x80 or (codepoint and 0x3F)).toByte()
                }
                3 -> {
                    utf8Buffer[bufferOffset++] = (0xE0 or (codepoint shr 12)).toByte()
                    utf8Buffer[bufferOffset++] = (0x80 or ((codepoint shr 6) and 0x3F)).toByte()
                    utf8Buffer[bufferOffset++] = (0x80 or (codepoint and 0x3F)).toByte()
                }
                else -> {
                    utf8Buffer[bufferOffset++] = (0xF0 or (codepoint shr 18)).toByte()
                    utf8Buffer[bufferOffset++] = (0x80 or ((codepoint shr 12) and 0x3F)).toByte()
                    utf8Buffer[bufferOffset++] = (0x80 or ((codepoint shr 6) and 0x3F)).toByte()
                    utf8Buffer[bufferOffset++] = (0x80 or (codepoint and 0x3F)).toByte()
                }
            }
        }

        if (bufferOffset > 0) {
            output.write(utf8Buffer, 0, bufferOffset)
        }

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

    private companion object {
        const val UTF8_BUFFER_SIZE: Int = 8192
        const val REPLACEMENT_CODEPOINT: Int = 0xFFFD
    }
}
