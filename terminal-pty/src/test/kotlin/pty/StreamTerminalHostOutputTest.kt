package com.gagik.terminal.pty

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream

class StreamTerminalHostOutputTest {
    @Test
    fun `writeByte rejects values outside unsigned byte range`() {
        val output = StreamTerminalHostOutput(RecordingOutputStream())

        assertThrows(IllegalArgumentException::class.java) { output.writeByte(-1) }
        assertThrows(IllegalArgumentException::class.java) { output.writeByte(256) }
    }

    @Test
    fun `writeBytes validates offset and length`() {
        val output = StreamTerminalHostOutput(RecordingOutputStream())
        val bytes = byteArrayOf(1, 2, 3)

        assertThrows(IllegalArgumentException::class.java) { output.writeBytes(bytes, -1, 1) }
        assertThrows(IllegalArgumentException::class.java) { output.writeBytes(bytes, 0, -1) }
        assertThrows(IllegalArgumentException::class.java) { output.writeBytes(bytes, 4, 0) }
        assertThrows(IllegalArgumentException::class.java) { output.writeBytes(bytes, 2, 2) }
    }

    @Test
    fun `writeBytes accepts empty range`() {
        val stream = RecordingOutputStream()
        val output = StreamTerminalHostOutput(stream)

        output.writeBytes(byteArrayOf(1, 2, 3), 3, 0)

        assertEquals(0, stream.bytes().size)
        assertEquals(1, stream.flushes)
    }

    @Test
    fun `writeAscii rejects non ASCII and writes ASCII bytes`() {
        assertThrows(IllegalArgumentException::class.java) {
            StreamTerminalHostOutput(RecordingOutputStream()).writeAscii("A\u0100")
        }

        val stream = RecordingOutputStream()
        val output = StreamTerminalHostOutput(stream)
        output.writeAscii("ABC")

        assertArrayEquals(byteArrayOf(65, 66, 67), stream.bytes())
    }

    @Test
    fun `writeUtf8 writes ASCII bytes`() {
        val stream = RecordingOutputStream()

        StreamTerminalHostOutput(stream).writeUtf8("ABC")

        assertArrayEquals(byteArrayOf(65, 66, 67), stream.bytes())
    }

    @Test
    fun `writeUtf8 writes two byte codepoints`() {
        val stream = RecordingOutputStream()

        StreamTerminalHostOutput(stream).writeUtf8("\u00E9")

        assertArrayEquals(byteArrayOf(0xC3.toByte(), 0xA9.toByte()), stream.bytes())
    }

    @Test
    fun `writeUtf8 writes four byte surrogate pair codepoints`() {
        val stream = RecordingOutputStream()

        StreamTerminalHostOutput(stream).writeUtf8("\uD83D\uDE00")

        assertArrayEquals(
            byteArrayOf(0xF0.toByte(), 0x9F.toByte(), 0x98.toByte(), 0x80.toByte()),
            stream.bytes(),
        )
    }

    @Test
    fun `writeUtf8 replaces lone high surrogate`() {
        val stream = RecordingOutputStream()

        StreamTerminalHostOutput(stream).writeUtf8("\uD83D")

        assertArrayEquals(
            byteArrayOf(0xEF.toByte(), 0xBF.toByte(), 0xBD.toByte()),
            stream.bytes(),
        )
    }

    @Test
    fun `writeUtf8 replaces lone low surrogate`() {
        val stream = RecordingOutputStream()

        StreamTerminalHostOutput(stream).writeUtf8("\uDE00")

        assertArrayEquals(
            byteArrayOf(0xEF.toByte(), 0xBF.toByte(), 0xBD.toByte()),
            stream.bytes(),
        )
    }

    @Test
    fun `writeUtf8 writes large text in multiple chunks`() {
        val stream = RecordingOutputStream()
        val text = "a".repeat(8193)

        StreamTerminalHostOutput(stream).writeUtf8(text)

        assertEquals(8193, stream.bytes().size)
        assertEquals(listOf(8192, 1), stream.bulkWriteLengths)
        assertTrue(stream.bytes().all { it == 97.toByte() })
    }

    @Test
    fun `writeUtf8 flushes once when flush policy is enabled`() {
        val stream = RecordingOutputStream()

        StreamTerminalHostOutput(stream, flushAfterWrite = true).writeUtf8("abc")

        assertEquals(1, stream.flushes)
    }

    @Test
    fun `writeUtf8 does not flush when flush policy is disabled`() {
        val stream = RecordingOutputStream()

        StreamTerminalHostOutput(stream, flushAfterWrite = false).writeUtf8("abc")

        assertEquals(0, stream.flushes)
    }

    @Test
    fun `writeUtf8 empty string follows flush policy`() {
        val flushing = RecordingOutputStream()
        StreamTerminalHostOutput(flushing, flushAfterWrite = true).writeUtf8("")

        val buffered = RecordingOutputStream()
        StreamTerminalHostOutput(buffered, flushAfterWrite = false).writeUtf8("")

        assertAll(
            { assertArrayEquals(ByteArray(0), flushing.bytes()) },
            { assertEquals(1, flushing.flushes) },
            { assertArrayEquals(ByteArray(0), buffered.bytes()) },
            { assertEquals(0, buffered.flushes) },
        )
    }

    @Test
    fun `flush policy is configurable`() {
        val flushing = RecordingOutputStream()
        StreamTerminalHostOutput(flushing, flushAfterWrite = true).writeByte(1)
        assertEquals(1, flushing.flushes)

        val buffered = RecordingOutputStream()
        StreamTerminalHostOutput(buffered, flushAfterWrite = false).writeByte(1)
        assertEquals(0, buffered.flushes)
    }

    @Test
    fun `close closes underlying stream`() {
        val stream = RecordingOutputStream()

        StreamTerminalHostOutput(stream).close()

        assertEquals(1, stream.closes)
    }

    private class RecordingOutputStream : ByteArrayOutputStream() {
        var flushes: Int = 0
            private set
        var closes: Int = 0
            private set
        val bulkWriteLengths = mutableListOf<Int>()

        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            bulkWriteLengths += length
            super.write(bytes, offset, length)
        }

        override fun flush() {
            flushes++
        }

        override fun close() {
            closes++
            super.close()
        }

        fun bytes(): ByteArray = toByteArray()
    }
}
