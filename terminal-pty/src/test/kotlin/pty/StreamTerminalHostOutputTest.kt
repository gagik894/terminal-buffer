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
