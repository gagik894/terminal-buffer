package com.gagik.parser.ansi

import com.gagik.parser.charset.CharsetMapper
import com.gagik.parser.fixture.AnsiPrintableBridgeFixture
import com.gagik.parser.fixture.ParserEvents.writeCluster
import com.gagik.parser.fixture.ParserEvents.writeCodepoint
import com.gagik.parser.runtime.ParserState
import com.gagik.parser.utf8.Utf8Decoder
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("ANSI printable bridge integration")
class AnsiPrintableBridgeIntegrationTest {

    // ----- Charset integration --------------------------------------------

    @Nested
    @DisplayName("charset integration")
    inner class CharsetIntegration {

        @Test
        fun `ESC left paren 0 designates G0 DEC Special Graphics`() {
            val h = AnsiPrintableBridgeFixture()

            h.acceptAscii("\u001B(0")

            assertAll(
                { assertEquals(ParserState.CHARSET_DEC_SPECIAL_GRAPHICS, h.state.charsets[0]) },
                { assertEquals(ParserState.CHARSET_ASCII, h.state.charsets[1]) },
                { assertEquals(AnsiState.GROUND, h.state.fsmState) },
                { assertTrue(h.sink.events.isEmpty()) }
            )
        }

        @Test
        fun `ESC right paren 0 designates G1 DEC Special Graphics`() {
            val h = AnsiPrintableBridgeFixture()

            h.acceptAscii("\u001B)0")

            assertAll(
                { assertEquals(ParserState.CHARSET_ASCII, h.state.charsets[0]) },
                { assertEquals(ParserState.CHARSET_DEC_SPECIAL_GRAPHICS, h.state.charsets[1]) },
                { assertEquals(AnsiState.GROUND, h.state.fsmState) },
                { assertTrue(h.sink.events.isEmpty()) }
            )
        }

        @Test
        fun `ESC left paren B resets G0 to ASCII`() {
            val h = AnsiPrintableBridgeFixture()

            h.acceptAscii("\u001B(0")
            assertEquals(ParserState.CHARSET_DEC_SPECIAL_GRAPHICS, h.state.charsets[0])

            h.acceptAscii("\u001B(B")

            assertAll(
                { assertEquals(ParserState.CHARSET_ASCII, h.state.charsets[0]) },
                { assertEquals(AnsiState.GROUND, h.state.fsmState) },
                { assertTrue(h.sink.events.isEmpty()) }
            )
        }

        @Test
        fun `SO switches GL to G1 and SI switches GL back to G0`() {
            val h = AnsiPrintableBridgeFixture()

            h.acceptByte(0x0E)
            assertEquals(1, h.state.glSlot)

            h.acceptByte(0x0F)

            assertAll(
                { assertEquals(0, h.state.glSlot) },
                { assertEquals(-1, h.state.singleShiftSlot) },
                { assertTrue(h.sink.events.isEmpty()) }
            )
        }

        @Test
        fun `active DEC Special Graphics maps q to box drawing horizontal`() {
            val h = AnsiPrintableBridgeFixture()

            h.acceptAscii("\u001B(0q")
            h.endOfInput()

            assertEquals(listOf(writeCodepoint(0x2500)), h.sink.events)
        }

        @Test
        fun `inactive DEC Special Graphics designation does not map GL printable bytes`() {
            val h = AnsiPrintableBridgeFixture()

            h.acceptAscii("\u001B)0q")
            h.endOfInput()

            assertAll(
                { assertEquals(0, h.state.glSlot) },
                { assertEquals(ParserState.CHARSET_DEC_SPECIAL_GRAPHICS, h.state.charsets[1]) },
                { assertEquals(listOf(writeCodepoint('q'.code)), h.sink.events) }
            )
        }

        @Test
        fun `SO makes G1 DEC Special Graphics active for later printable bytes`() {
            val h = AnsiPrintableBridgeFixture()

            h.acceptAscii("\u001B)0")
            h.acceptByte(0x0E)
            h.acceptAscii("q")
            h.endOfInput()

            assertEquals(listOf(writeCodepoint(0x2500)), h.sink.events)
        }

        @Test
        fun `SI returns GL to G0 so inactive G1 DEC Special Graphics no longer maps`() {
            val h = AnsiPrintableBridgeFixture()

            h.acceptAscii("\u001B)0")
            h.acceptByte(0x0E)
            h.acceptAscii("q")
            h.acceptByte(0x0F)
            h.acceptAscii("q")
            h.endOfInput()

            assertEquals(
                listOf(writeCodepoint(0x2500), writeCodepoint('q'.code)),
                h.sink.events
            )
        }

        @Test
        fun `single shift G2 and G3 map one printable character only`() {
            val h = AnsiPrintableBridgeFixture()

            h.acceptAscii("\u001B*0\u001B+0")

            CharsetMapper.singleShiftG2(h.state)
            h.acceptAscii("qq")

            CharsetMapper.singleShiftG3(h.state)
            h.acceptAscii("xx")

            h.endOfInput()

            assertAll(
                { assertEquals(-1, h.state.singleShiftSlot) },
                {
                    assertEquals(
                        listOf(
                            writeCodepoint(0x2500),
                            writeCodepoint('q'.code),
                            writeCodepoint(0x2502),
                            writeCodepoint('x'.code),
                        ),
                        h.sink.events
                    )
                }
            )
        }

        @Test
        fun `ESC N q q single shifts G2 for one printable character through full parser path`() {
            val h = AnsiPrintableBridgeFixture()

            h.acceptAscii("\u001B*0\u001BNqq")
            h.endOfInput()

            assertAll(
                { assertEquals(-1, h.state.singleShiftSlot) },
                {
                    assertEquals(
                        listOf(
                            writeCodepoint(0x2500),
                            writeCodepoint('q'.code),
                        ),
                        h.sink.events
                    )
                }
            )
        }

        @Test
        fun `ESC O x x single shifts G3 for one printable character through full parser path`() {
            val h = AnsiPrintableBridgeFixture()

            h.acceptAscii("\u001B+0\u001BOxx")
            h.endOfInput()

            assertAll(
                { assertEquals(-1, h.state.singleShiftSlot) },
                {
                    assertEquals(
                        listOf(
                            writeCodepoint(0x2502),
                            writeCodepoint('x'.code),
                        ),
                        h.sink.events
                    )
                }
            )
        }

        @Test
        fun `unsupported charset designation final is swallowed without plain ESC dispatch`() {
            val h = AnsiPrintableBridgeFixture()

            h.acceptAscii("\u001B(D")

            assertAll(
                { assertEquals(AnsiState.GROUND, h.state.fsmState) },
                { assertEquals(ParserState.CHARSET_ASCII, h.state.charsets[0]) },
                { assertTrue(h.sink.events.isEmpty()) }
            )
        }

        @Test
        fun `unsupported ESC intermediate shape is swallowed without mis-dispatching final byte`() {
            val h = AnsiPrintableBridgeFixture()

            h.acceptAscii("\u001B#D")

            assertAll(
                { assertEquals(AnsiState.GROUND, h.state.fsmState) },
                { assertTrue(h.sink.events.isEmpty(), "ESC # D must not become ESC D lineFeed") }
            )
        }
    }

    // ----- Printable bridge ------------------------------------------------

    @Nested
    @DisplayName("printable bridge")
    inner class PrintableBridge {

        @Test
        fun `ASCII fast path writes codepoint after flush`() {
            val h = AnsiPrintableBridgeFixture()

            h.acceptAscii("A")
            h.endOfInput()

            assertEquals(listOf(writeCodepoint('A'.code)), h.sink.events)
        }

        @Test
        fun `UTF-8 e acute writes codepoint U+00E9`() {
            val h = AnsiPrintableBridgeFixture()

            h.acceptUtf8("\u00E9")
            h.endOfInput()

            assertEquals(listOf(writeCodepoint(0x00E9)), h.sink.events)
        }

        @Test
        fun `UTF-8 emoji writes one wide scalar through current assembler policy`() {
            val h = AnsiPrintableBridgeFixture()

            h.acceptUtf8("\uD83D\uDE00")
            h.endOfInput()

            assertEquals(listOf(writeCodepoint(0x1F600)), h.sink.events)
        }

        @Test
        fun `malformed UTF-8 lead followed by ASCII emits replacement then preserves ASCII`() {
            val h = AnsiPrintableBridgeFixture()

            h.acceptBytes(0xC3, 'A'.code)
            h.endOfInput()

            assertEquals(
                listOf(
                    writeCodepoint(Utf8Decoder.REPLACEMENT_CODEPOINT),
                    writeCodepoint('A'.code),
                ),
                h.sink.events
            )
        }

        @Test
        fun `malformed UTF-8 lead before ESC emits replacement then routes ESC as control sequence`() {
            val h = AnsiPrintableBridgeFixture()

            h.acceptBytes(0xC3, 0x1B, '['.code, 'A'.code)

            assertAll(
                { assertEquals(AnsiState.GROUND, h.state.fsmState) },
                {
                    assertEquals(
                        listOf(
                            writeCodepoint(Utf8Decoder.REPLACEMENT_CODEPOINT),
                            "cursorUp:1",
                        ),
                        h.sink.events
                    )
                }
            )
        }

        @Test
        fun `base plus combining mark emits one cluster`() {
            val h = AnsiPrintableBridgeFixture()

            h.acceptAscii("e")
            h.acceptUtf8("\u0301")
            h.endOfInput()

            assertEquals(listOf(writeCluster(1, 'e'.code, 0x0301)), h.sink.events)
        }

        @Test
        fun `base plus variation selector emits one cluster`() {
            val h = AnsiPrintableBridgeFixture()

            h.acceptUtf8("\u2764\uFE0F")
            h.endOfInput()

            assertEquals(listOf(writeCluster(1, 0x2764, 0xFE0F)), h.sink.events)
        }

        @Test
        fun `ZWJ emoji sequence stays one cluster`() {
            val h = AnsiPrintableBridgeFixture()

            h.acceptUtf8("\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67\u200D\uD83D\uDC66")
            h.endOfInput()

            assertEquals(
                listOf(writeCluster(2, 0x1F468, 0x200D, 0x1F469, 0x200D, 0x1F467, 0x200D, 0x1F466)),
                h.sink.events
            )
        }

        @Test
        fun `regional indicator pair stays one cluster`() {
            val h = AnsiPrintableBridgeFixture()

            h.acceptUtf8("\uD83C\uDDFA\uD83C\uDDF8")
            h.endOfInput()

            assertEquals(listOf(writeCluster(2, 0x1F1FA, 0x1F1F8)), h.sink.events)
        }

        @Test
        fun `control flushes pending cluster before dispatch`() {
            val h = AnsiPrintableBridgeFixture()

            h.acceptAscii("e")
            h.acceptUtf8("\u0301")
            h.acceptByte(0x07)

            assertEquals(
                listOf(writeCluster(1, 'e'.code, 0x0301), "bell"),
                h.sink.events
            )
        }

        @Test
        fun `ESC flushes pending cluster before entering escape state`() {
            val h = AnsiPrintableBridgeFixture()

            h.acceptAscii("e")
            h.acceptUtf8("\u0301")
            h.acceptAscii("\u001B7")

            assertAll(
                { assertEquals(AnsiState.GROUND, h.state.fsmState) },
                {
                    assertEquals(
                        listOf(writeCluster(1, 'e'.code, 0x0301), "saveCursor"),
                        h.sink.events
                    )
                }
            )
        }
    }
}
