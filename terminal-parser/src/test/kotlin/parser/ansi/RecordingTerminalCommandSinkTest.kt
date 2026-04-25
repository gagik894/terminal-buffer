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
    @DisplayName("terminal command recording")
    inner class TerminalCommandRecording {

        @Test
        fun `records terminal commands in call order`() {
            val sink = RecordingTerminalCommandSink()

            sink.writeCodepoint('A'.code)
            sink.writeCluster(intArrayOf('A'.code), length = 1, charWidth = 1)
            sink.bell()
            sink.backspace()
            sink.tab()
            sink.lineFeed()
            sink.carriageReturn()
            sink.reverseIndex()
            sink.nextLine()
            sink.softReset()
            sink.saveCursor()
            sink.restoreCursor()
            sink.cursorUp(1)
            sink.cursorDown(2)
            sink.cursorForward(3)
            sink.cursorBackward(4)
            sink.cursorNextLine(5)
            sink.cursorPreviousLine(6)
            sink.setCursorColumn(7)
            sink.setCursorRow(8)
            sink.setCursorAbsolute(9, 10)
            sink.eraseInDisplay(0, selective = false)
            sink.eraseInLine(1, selective = true)
            sink.insertLines(2)
            sink.deleteLines(3)
            sink.insertCharacters(4)
            sink.deleteCharacters(5)
            sink.eraseCharacters(6)
            sink.scrollUp(7)
            sink.scrollDown(8)
            sink.setAnsiMode(4, enable = true)
            sink.setDecMode(25, enable = false)

            assertEquals(
                listOf(
                    "writeCodepoint:${'A'.code}",
                    "writeCluster:1:1:65",
                    "bell",
                    "backspace",
                    "tab",
                    "lineFeed",
                    "carriageReturn",
                    "reverseIndex",
                    "nextLine",
                    "softReset",
                    "saveCursor",
                    "restoreCursor",
                    "cursorUp:1",
                    "cursorDown:2",
                    "cursorForward:3",
                    "cursorBackward:4",
                    "cursorNextLine:5",
                    "cursorPreviousLine:6",
                    "setCursorColumn:7",
                    "setCursorRow:8",
                    "setCursorAbsolute:9:10",
                    "eraseInDisplay:0:false",
                    "eraseInLine:1:true",
                    "insertLines:2",
                    "deleteLines:3",
                    "insertCharacters:4",
                    "deleteCharacters:5",
                    "eraseCharacters:6",
                    "scrollUp:7",
                    "scrollDown:8",
                    "setAnsiMode:4:true",
                    "setDecMode:25:false"
                ),
                sink.events
            )
        }

        @Test
        fun `starts with no recorded terminal commands`() {
            val sink = RecordingTerminalCommandSink()

            assertAll(
                { assertTrue(sink.events.isEmpty()) },
                { assertTrue(sink.osc.isEmpty()) }
            )
        }
    }
}
