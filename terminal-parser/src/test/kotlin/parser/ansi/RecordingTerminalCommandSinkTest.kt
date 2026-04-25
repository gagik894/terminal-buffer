package com.gagik.parser.ansi

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("RecordingTerminalCommandSink")
class RecordingTerminalCommandSinkTest {

    @Nested
    @DisplayName("OSC recording")
    inner class OscRecording {

        @Test
        fun `records OSC metadata and bounded payload bytes`() {
            val sink = RecordingTerminalCommandSink()
            val payload = byteArrayOf('0'.code.toByte(), ';'.code.toByte(), 't'.code.toByte(), '!'.code.toByte())

            sink.onOsc(
                commandCode = 0,
                payload = payload,
                length = 3,
                overflowed = true
            )

            val osc = sink.osc.single()
            assertAll(
                { assertEquals(0, osc.commandCode) },
                { assertArrayEquals(byteArrayOf('0'.code.toByte(), ';'.code.toByte(), 't'.code.toByte()), osc.payload) },
                { assertEquals(3, osc.length) },
                { assertTrue(osc.overflowed) }
            )
        }

        @Test
        fun `takes a defensive copy of OSC payload at recorded length`() {
            val sink = RecordingTerminalCommandSink()
            val payload = byteArrayOf('a'.code.toByte(), 'b'.code.toByte(), 'c'.code.toByte())

            sink.onOsc(
                commandCode = 52,
                payload = payload,
                length = 2,
                overflowed = false
            )
            payload[0] = 'z'.code.toByte()

            val osc = sink.osc.single()
            assertAll(
                { assertEquals(52, osc.commandCode) },
                { assertArrayEquals(byteArrayOf('a'.code.toByte(), 'b'.code.toByte()), osc.payload) },
                { assertEquals(2, osc.length) },
                { assertEquals(false, osc.overflowed) }
            )
        }
    }

    @Nested
    @DisplayName("ignored terminal commands")
    inner class IgnoredTerminalCommands {

        @Test
        fun `non-OSC terminal commands are harmless no-ops`() {
            val sink = RecordingTerminalCommandSink()

            assertDoesNotThrow {
                sink.writeCodepoint('A'.code)
                sink.writeCluster(intArrayOf('A'.code), length = 1, charWidth = 1)
                sink.bell()
                sink.backspace()
                sink.tab()
                sink.lineFeed()
                sink.carriageReturn()
                sink.reverseIndex()
                sink.nextLine()
                sink.saveCursor()
                sink.restoreCursor()
                sink.cursorUp(1)
                sink.cursorDown(1)
                sink.cursorForward(1)
                sink.cursorBackward(1)
                sink.cursorNextLine(1)
                sink.cursorPreviousLine(1)
                sink.setCursorColumn(1)
                sink.setCursorRow(1)
                sink.setCursorAbsolute(1, 1)
                sink.eraseInDisplay(0, selective = false)
                sink.eraseInLine(0, selective = false)
                sink.insertLines(1)
                sink.deleteLines(1)
                sink.insertCharacters(1)
                sink.deleteCharacters(1)
                sink.eraseCharacters(1)
                sink.scrollUp(1)
                sink.scrollDown(1)
                sink.setAnsiMode(4, enable = true)
                sink.setDecMode(25, enable = false)
            }

            assertTrue(sink.osc.isEmpty())
        }
    }
}
