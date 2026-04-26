package com.gagik.parser

import com.gagik.parser.ansi.AnsiState
import com.gagik.parser.ansi.RecordingTerminalCommandSink
import com.gagik.parser.api.TerminalOutputParser
import com.gagik.parser.api.TerminalParsers
import com.gagik.parser.fixture.ParserEvents.writeCluster
import com.gagik.parser.fixture.ParserEvents.writeCodepoint
import com.gagik.parser.fixture.TerminalParserFixture
import com.gagik.parser.utf8.Utf8Decoder
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("TerminalParser")
class TerminalParserTest {

    // ----- API validation ---------------------------------------------------

    @Nested
    @DisplayName("API validation")
    inner class ApiValidation {

        @Test
        fun `accept rejects invalid offset and length ranges`() {
            val f = TerminalParserFixture()
            val bytes = byteArrayOf('a'.code.toByte())

            val negativeOffset = assertThrows(IllegalArgumentException::class.java) {
                f.parser.accept(bytes, offset = -1, length = 1)
            }
            val negativeLength = assertThrows(IllegalArgumentException::class.java) {
                f.parser.accept(bytes, offset = 0, length = -1)
            }
            val offsetOutOfRange = assertThrows(IllegalArgumentException::class.java) {
                f.parser.accept(bytes, offset = 2, length = 0)
            }
            val sumOutOfRange = assertThrows(IllegalArgumentException::class.java) {
                f.parser.accept(bytes, offset = 1, length = 1)
            }

            assertAll(
                { assertEquals("offset must be non-negative: -1", negativeOffset.message) },
                { assertEquals("length must be non-negative: -1", negativeLength.message) },
                { assertEquals("offset out of range: 2", offsetOutOfRange.message) },
                { assertEquals("offset + length out of range: offset=1 length=1 size=1", sumOutOfRange.message) }
            )
        }

        @Test
        fun `acceptByte rejects values outside unsigned byte range`() {
            val f = TerminalParserFixture()

            val below = assertThrows(IllegalArgumentException::class.java) {
                f.parser.acceptByte(-1)
            }
            val above = assertThrows(IllegalArgumentException::class.java) {
                f.parser.acceptByte(256)
            }

            assertAll(
                { assertEquals("byteValue out of range: -1", below.message) },
                { assertEquals("byteValue out of range: 256", above.message) }
            )
        }

        @Test
        fun `empty chunks are accepted without side effects`() {
            val f = TerminalParserFixture()

            f.parser.accept(ByteArray(0))
            f.parser.accept(byteArrayOf('a'.code.toByte()), offset = 0, length = 0)

            assertAll(
                { assertTrue(f.sink.events.isEmpty()) },
                { assertEquals(AnsiState.GROUND, f.state.fsmState) }
            )
        }

        @Test
        fun `factory creates parser behind TerminalOutputParser contract`() {
            val sink = RecordingTerminalCommandSink()
            val parser: TerminalOutputParser = TerminalParsers.create(sink)

            parser.accept("A".encodeToByteArray())
            parser.endOfInput()

            assertEquals(listOf(writeCodepoint('A'.code)), sink.events)
        }

        @Test
        fun `offset and length process only the selected slice`() {
            val f = TerminalParserFixture()
            val bytes = "zabcx".encodeToByteArray()

            f.parser.accept(bytes, offset = 1, length = 3)
            f.endOfInput()

            assertEquals(
                listOf(writeCodepoint('a'.code), writeCodepoint('b'.code), writeCodepoint('c'.code)),
                f.sink.events
            )
        }
    }

    // ----- Printable and UTF-8 ---------------------------------------------

    @Nested
    @DisplayName("printable and UTF-8")
    inner class PrintableAndUtf8 {

        @Test
        fun `plain ASCII flushes previous scalars during streaming and final scalar at endOfInput`() {
            val f = TerminalParserFixture()

            f.acceptAscii("abc")
            assertEquals(listOf(writeCodepoint('a'.code), writeCodepoint('b'.code)), f.sink.events)

            f.endOfInput()

            assertEquals(
                listOf(writeCodepoint('a'.code), writeCodepoint('b'.code), writeCodepoint('c'.code)),
                f.sink.events
            )
        }

        @Test
        fun `UTF-8 scalar may be split across chunks`() {
            val f = TerminalParserFixture()

            f.parser.accept(byteArrayOf(0xC3.toByte()))
            assertTrue(f.sink.events.isEmpty())

            f.parser.accept(byteArrayOf(0xA9.toByte()))
            f.endOfInput()

            assertEquals(listOf(writeCodepoint(0x00E9)), f.sink.events)
        }

        @Test
        fun `emoji scalar is decoded through top level UTF-8 path`() {
            val f = TerminalParserFixture()

            f.acceptUtf8("\uD83D\uDE00")
            f.endOfInput()

            assertEquals(listOf(writeCodepoint(0x1F600)), f.sink.events)
        }

        @Test
        fun `endOfInput emits replacement for truncated UTF-8 sequence`() {
            val f = TerminalParserFixture()

            f.parser.accept(byteArrayOf(0xE2.toByte(), 0x82.toByte()))
            f.endOfInput()

            assertEquals(listOf(writeCodepoint(Utf8Decoder.REPLACEMENT_CODEPOINT)), f.sink.events)
        }

        @Test
        fun `malformed UTF-8 lead followed by ASCII emits replacement then replays ASCII`() {
            val f = TerminalParserFixture()

            f.acceptBytes(0xC3, 'A'.code)
            f.endOfInput()

            assertEquals(
                listOf(writeCodepoint(Utf8Decoder.REPLACEMENT_CODEPOINT), writeCodepoint('A'.code)),
                f.sink.events
            )
        }

        @Test
        fun `malformed UTF-8 lead before ESC emits replacement then routes ESC sequence`() {
            val f = TerminalParserFixture()

            f.acceptBytes(0xC3, 0x1B, '['.code, 'A'.code)

            assertAll(
                { assertEquals(AnsiState.GROUND, f.state.fsmState) },
                {
                    assertEquals(
                        listOf(writeCodepoint(Utf8Decoder.REPLACEMENT_CODEPOINT), "cursorUp:1"),
                        f.sink.events
                    )
                },
                { assertFalse(f.sink.events.contains(writeCodepoint(0x1B))) }
            )
        }

        @Test
        fun `combining mark variation selector ZWJ and regional indicators form clusters`() {
            val f = TerminalParserFixture()

            f.acceptAscii("e")
            f.acceptUtf8("\u0301")
            f.acceptUtf8("\u2764\uFE0F")
            f.acceptUtf8("\uD83D\uDC68\u200D\uD83D\uDC69")
            f.acceptUtf8("\uD83C\uDDFA\uD83C\uDDF8")
            f.endOfInput()

            assertEquals(
                listOf(
                    writeCluster(1, 'e'.code, 0x0301),
                    writeCluster(1, 0x2764, 0xFE0F),
                    writeCluster(2, 0x1F468, 0x200D, 0x1F469),
                    writeCluster(2, 0x1F1FA, 0x1F1F8),
                ),
                f.sink.events
            )
        }
    }

    // ----- Structural routing ---------------------------------------------

    @Nested
    @DisplayName("structural routing")
    inner class StructuralRouting {

        @Test
        fun `C0 control flushes pending printable output before dispatch`() {
            val f = TerminalParserFixture()

            f.acceptAscii("A")
            f.acceptByte(0x07)

            assertEquals(listOf(writeCodepoint('A'.code), "bell"), f.sink.events)
        }

        @Test
        fun `ESC command flushes pending printable output before dispatch`() {
            val f = TerminalParserFixture()

            f.acceptAscii("A\u001B7")

            assertEquals(listOf(writeCodepoint('A'.code), "saveCursor"), f.sink.events)
        }

        @Test
        fun `CSI sequence dispatches through command dispatcher across chunks`() {
            val f = TerminalParserFixture()

            f.acceptAscii("\u001B[12")
            assertEquals(AnsiState.CSI_PARAM, f.state.fsmState)

            f.acceptAscii(";34H")

            assertAll(
                { assertEquals(AnsiState.GROUND, f.state.fsmState) },
                { assertEquals(listOf("setCursorAbsolute:11:33"), f.sink.events) }
            )
        }

        @Test
        fun `DEC private CSI mode dispatch survives chunk boundaries`() {
            val f = TerminalParserFixture()

            f.acceptAscii("\u001B[?25")
            f.acceptAscii("h")

            assertEquals(listOf("setDecMode:25:true"), f.sink.events)
        }

        @Test
        fun `OSC payload is bounded and dispatched on BEL or ST`() {
            val bel = TerminalParserFixture()
            val st = TerminalParserFixture()

            bel.acceptAscii("\u001B]0;title\u0007")
            st.acceptAscii("\u001B]1;name\u001B\\")

            assertAll(
                { assertEquals("0;title", bel.sink.osc.single().payload.decodeToString()) },
                { assertEquals("1;name", st.sink.osc.single().payload.decodeToString()) },
                { assertTrue(bel.sink.events.single().startsWith("osc:")) },
                { assertTrue(st.sink.events.single().startsWith("osc:")) }
            )
        }

        @Test
        fun `DCS ST drops payload without dispatching plain ESC backslash`() {
            val f = TerminalParserFixture()

            f.acceptAscii("\u001BPabc\u001B\\")

            assertAll(
                { assertEquals(AnsiState.GROUND, f.state.fsmState) },
                { assertTrue(f.sink.events.isEmpty()) },
                { assertTrue(f.sink.osc.isEmpty()) }
            )
        }
    }

    // ----- SGR --------------------------------------------------------------

    @Nested
    @DisplayName("SGR")
    inner class Sgr {

        @Test
        fun `CSI m and CSI 0 m reset attributes through the full parser`() {
            val empty = TerminalParserFixture()
            val zero = TerminalParserFixture()

            empty.acceptAscii("\u001B[m")
            zero.acceptAscii("\u001B[0m")

            assertAll(
                { assertEquals(listOf("resetAttributes"), empty.sink.events) },
                { assertEquals(listOf("resetAttributes"), zero.sink.events) }
            )
        }

        @Test
        fun `CSI 1 31 m applies bold and indexed foreground through the full parser`() {
            val f = TerminalParserFixture()

            f.acceptAscii("\u001B[1;31m")

            assertEquals(listOf("setBold:true", "setForegroundIndexed:1"), f.sink.events)
        }

        @Test
        fun `CSI 38 5 indexed foreground applies through the full parser`() {
            val f = TerminalParserFixture()

            f.acceptAscii("\u001B[38;5;196m")

            assertEquals(listOf("setForegroundIndexed:196"), f.sink.events)
        }

        @Test
        fun `CSI 48 2 RGB background applies through the full parser`() {
            val f = TerminalParserFixture()

            f.acceptAscii("\u001B[48;2;10;20;30m")

            assertEquals(listOf("setBackgroundRgb:10:20:30"), f.sink.events)
        }

        @Test
        fun `CSI colon RGB foreground with omitted color-space id applies through the full parser`() {
            val f = TerminalParserFixture()

            f.acceptAscii("\u001B[38:2::10:20:30m")

            assertEquals(listOf("setForegroundRgb:10:20:30"), f.sink.events)
        }
    }

    // ----- Charset and shifts ---------------------------------------------

    @Nested
    @DisplayName("charset and shifts")
    inner class CharsetAndShifts {

        @Test
        fun `ESC charset designation maps active DEC Special Graphics through printable path`() {
            val f = TerminalParserFixture()

            f.acceptAscii("\u001B(0q")
            f.endOfInput()

            assertEquals(listOf(writeCodepoint(0x2500)), f.sink.events)
        }

        @Test
        fun `inactive G1 DEC Special does not map until SO selects G1 and SI restores G0`() {
            val f = TerminalParserFixture()

            f.acceptAscii("\u001B)0q")
            f.acceptByte(0x0E)
            f.acceptAscii("q")
            f.acceptByte(0x0F)
            f.acceptAscii("q")
            f.endOfInput()

            assertEquals(
                listOf(
                    writeCodepoint('q'.code),
                    writeCodepoint(0x2500),
                    writeCodepoint('q'.code),
                ),
                f.sink.events
            )
        }

        @Test
        fun `ESC N and ESC O single shift G2 and G3 for one printable character each`() {
            val f = TerminalParserFixture()

            f.acceptAscii("\u001B*0\u001B+0\u001BNqq\u001BOxx")
            f.endOfInput()

            assertEquals(
                listOf(
                    writeCodepoint(0x2500),
                    writeCodepoint('q'.code),
                    writeCodepoint(0x2502),
                    writeCodepoint('x'.code),
                ),
                f.sink.events
            )
        }

        @Test
        fun `unsupported charset designation shape is swallowed without plain ESC dispatch`() {
            val f = TerminalParserFixture()

            f.acceptAscii("\u001B(D\u001B#D")

            assertAll(
                { assertEquals(AnsiState.GROUND, f.state.fsmState) },
                { assertTrue(f.sink.events.isEmpty()) },
                { assertArrayEquals(intArrayOf(0, 0, 0, 0), f.state.charsets) }
            )
        }
    }

    // ----- Reset ------------------------------------------------------------

    @Nested
    @DisplayName("reset")
    inner class Reset {

        @Test
        fun `reset drops pending printable cluster CSI state and charset shifts`() {
            val f = TerminalParserFixture()

            f.acceptAscii("\u001B(0")
            f.acceptByte(0x0E)
            f.acceptAscii("\u001B[12")

            f.reset()
            f.acceptAscii("q")
            f.endOfInput()

            assertAll(
                { assertEquals(AnsiState.GROUND, f.state.fsmState) },
                { assertArrayEquals(intArrayOf(0, 0, 0, 0), f.state.charsets) },
                { assertEquals(0, f.state.glSlot) },
                { assertEquals(-1, f.state.singleShiftSlot) },
                { assertEquals(listOf(writeCodepoint('q'.code)), f.sink.events) }
            )
        }

        @Test
        fun `reset does not emit replacement for pending UTF-8 or flush pending printable cluster`() {
            val f = TerminalParserFixture()

            f.acceptAscii("A")
            f.acceptByte(0xC3)
            f.reset()

            assertTrue(f.sink.events.isEmpty())
        }
    }
}
