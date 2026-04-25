package com.gagik.parser.text

import com.gagik.parser.ansi.RecordingTerminalCommandSink
import com.gagik.parser.runtime.ParserState
import com.gagik.parser.utf8.Utf8Decoder
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("PrintableProcessor")
class PrintableProcessorTest {

    // ----- Helpers ----------------------------------------------------------

    private data class Fixture(
        val state: ParserState = ParserState(),
        val sink: RecordingTerminalCommandSink = RecordingTerminalCommandSink(),
    ) {
        val processor = PrintableProcessor(sink)

        fun acceptAscii(text: String) {
            for (ch in text) {
                processor.acceptAsciiByte(state, ch.code)
            }
        }

        fun acceptUtf8Bytes(vararg byteValues: Int) {
            for (byteValue in byteValues) {
                processor.acceptUtf8Byte(state, byteValue)
            }
        }

        fun acceptDecoderBytes(vararg byteValues: Int) {
            for (byteValue in byteValues) {
                processor.acceptUtf8DecoderByte(state, byteValue)
            }
        }

        fun acceptUtf8Codepoints(vararg codepoints: Int) {
            val text = buildString {
                for (codepoint in codepoints) {
                    appendCodePoint(codepoint)
                }
            }
            for (byteValue in text.encodeToByteArray()) {
                processor.acceptUtf8Byte(state, byteValue.toInt() and 0xff)
            }
        }

        fun flush() {
            processor.flush(state)
        }

        fun endOfInput() {
            processor.endOfInput(state)
        }

        fun reset() {
            processor.reset(state)
        }
    }

    private fun writeCodepoint(codepoint: Int): String = "writeCodepoint:$codepoint"

    private fun writeCluster(charWidth: Int, vararg codepoints: Int): String {
        return "writeCluster:${codepoints.size}:$charWidth:${codepoints.joinToString(":")}"
    }

    // ----- Input validation -------------------------------------------------

    @Nested
    @DisplayName("input validation")
    inner class InputValidation {

        @Test
        fun `acceptAsciiByte accepts printable 7-bit ASCII only`() {
            val f = Fixture()

            f.processor.acceptAsciiByte(f.state, 0x20)
            f.processor.acceptAsciiByte(f.state, 0x7E)

            val below = assertThrows(IllegalArgumentException::class.java) {
                f.processor.acceptAsciiByte(f.state, 0x1F)
            }
            val del = assertThrows(IllegalArgumentException::class.java) {
                f.processor.acceptAsciiByte(f.state, 0x7F)
            }
            val above = assertThrows(IllegalArgumentException::class.java) {
                f.processor.acceptAsciiByte(f.state, 0x80)
            }

            assertAll(
                { assertEquals("byteValue is not printable ASCII: 31", below.message) },
                { assertEquals("byteValue is not printable ASCII: 127", del.message) },
                { assertEquals("byteValue is not printable ASCII: 128", above.message) }
            )
        }

        @Test
        fun `acceptUtf8Byte accepts non-ASCII byte lane only`() {
            val f = Fixture()

            f.processor.acceptUtf8Byte(f.state, 0xC2)

            val ascii = assertThrows(IllegalArgumentException::class.java) {
                f.processor.acceptUtf8Byte(f.state, 'A'.code)
            }
            val above = assertThrows(IllegalArgumentException::class.java) {
                f.processor.acceptUtf8Byte(f.state, 256)
            }

            assertAll(
                { assertEquals("byteValue is not UTF-8 payload byte: 65", ascii.message) },
                { assertEquals("byteValue is not UTF-8 payload byte: 256", above.message) }
            )
        }

        @Test
        fun `acceptUtf8DecoderByte accepts full unsigned byte range only`() {
            val f = Fixture()

            f.processor.acceptUtf8DecoderByte(f.state, 0)
            f.processor.acceptUtf8DecoderByte(f.state, 255)

            val below = assertThrows(IllegalArgumentException::class.java) {
                f.processor.acceptUtf8DecoderByte(f.state, -1)
            }
            val above = assertThrows(IllegalArgumentException::class.java) {
                f.processor.acceptUtf8DecoderByte(f.state, 256)
            }

            assertAll(
                { assertEquals("byteValue out of range: -1", below.message) },
                { assertEquals("byteValue out of range: 256", above.message) }
            )
        }

        @Test
        fun `acceptAsciiByte fails loudly while UTF-8 decoder has pending state`() {
            val f = Fixture()
            f.processor.acceptUtf8Byte(f.state, 0xC2)

            val error = assertThrows(IllegalStateException::class.java) {
                f.processor.acceptAsciiByte(f.state, 'A'.code)
            }

            assertEquals(
                "ASCII printable byte received while UTF-8 decoder has a pending sequence",
                error.message
            )
        }
    }

    // ----- ASCII printable flow --------------------------------------------

    @Nested
    @DisplayName("ASCII printable flow")
    inner class AsciiPrintableFlow {

        @Test
        fun `plain ASCII is emitted in order once cluster boundaries are known`() {
            val f = Fixture()

            f.acceptAscii("abc")
            f.flush()

            assertAll(
                { assertEquals(listOf(writeCodepoint('a'.code), writeCodepoint('b'.code), writeCodepoint('c'.code)), f.sink.events) },
                { assertEquals(0, f.state.clusterLength) }
            )
        }

        @Test
        fun `ASCII may be buffered until flush`() {
            val f = Fixture()

            f.processor.acceptAsciiByte(f.state, 'a'.code)

            assertAll(
                { assertTrue(f.sink.events.isEmpty()) },
                { assertEquals(1, f.state.clusterLength) }
            )
        }

        @Test
        fun `flush emits the active ASCII scalar once and clears cluster state`() {
            val f = Fixture()

            f.processor.acceptAsciiByte(f.state, 'x'.code)
            f.flush()
            f.flush()

            assertAll(
                { assertEquals(listOf(writeCodepoint('x'.code)), f.sink.events) },
                { assertEquals(0, f.state.clusterLength) },
                { assertFalse(f.state.prevWasZwj) },
                { assertEquals(0, f.state.regionalIndicatorParity) }
            )
        }

        @Test
        fun `endOfInput flushes pending ASCII scalar`() {
            val f = Fixture()

            f.processor.acceptAsciiByte(f.state, 'z'.code)
            f.endOfInput()

            assertEquals(listOf(writeCodepoint('z'.code)), f.sink.events)
        }
    }

    // ----- UTF-8 printable flow --------------------------------------------

    @Nested
    @DisplayName("UTF-8 printable flow")
    inner class Utf8PrintableFlow {

        @Test
        fun `valid two three and four byte scalars decode and print in order`() {
            val f = Fixture()

            f.acceptUtf8Bytes(
                0xC2, 0xA2,
                0xE2, 0x82, 0xAC,
                0xF0, 0x9F, 0x98, 0x80
            )
            f.flush()

            assertEquals(
                listOf(
                    writeCodepoint(0x00A2),
                    writeCodepoint(0x20AC),
                    writeCodepoint(0x1F600),
                ),
                f.sink.events
            )
        }

        @Test
        fun `hasPendingUtf8Sequence tracks partial multibyte state`() {
            val f = Fixture()

            assertFalse(f.processor.hasPendingUtf8Sequence())
            f.processor.acceptUtf8Byte(f.state, 0xE2)
            assertTrue(f.processor.hasPendingUtf8Sequence())
            f.processor.acceptUtf8Byte(f.state, 0x82)
            assertTrue(f.processor.hasPendingUtf8Sequence())
            f.processor.acceptUtf8Byte(f.state, 0xAC)

            assertFalse(f.processor.hasPendingUtf8Sequence())
        }

        @Test
        fun `acceptUtf8DecoderByte repairs malformed pending sequence and preserves following ASCII`() {
            val f = Fixture()

            f.processor.acceptUtf8Byte(f.state, 0xC2)
            f.processor.acceptUtf8DecoderByte(f.state, 'A'.code)
            f.flush()

            assertEquals(
                listOf(writeCodepoint(Utf8Decoder.REPLACEMENT_CODEPOINT), writeCodepoint('A'.code)),
                f.sink.events
            )
        }

        @Test
        fun `malformed UTF-8 emits replacement and continues with later valid scalar`() {
            val f = Fixture()

            f.acceptUtf8Bytes(0xFF, 0xE2, 0x82, 0xAC)
            f.flush()

            assertEquals(
                listOf(writeCodepoint(Utf8Decoder.REPLACEMENT_CODEPOINT), writeCodepoint(0x20AC)),
                f.sink.events
            )
        }

        @Test
        fun `endOfInput emits replacement for truncated UTF-8 then flushes it`() {
            val f = Fixture()

            f.processor.acceptUtf8Byte(f.state, 0xF0)
            f.processor.acceptUtf8Byte(f.state, 0x9F)
            f.endOfInput()

            assertAll(
                { assertEquals(listOf(writeCodepoint(Utf8Decoder.REPLACEMENT_CODEPOINT)), f.sink.events) },
                { assertFalse(f.processor.hasPendingUtf8Sequence()) },
                { assertEquals(0, f.state.clusterLength) }
            )
        }

        @Test
        fun `reset drops pending UTF-8 without emitting replacement`() {
            val f = Fixture()

            f.processor.acceptUtf8Byte(f.state, 0xE2)
            f.reset()
            f.acceptAscii("A")
            f.flush()

            assertEquals(listOf(writeCodepoint('A'.code)), f.sink.events)
        }
    }

    // ----- Grapheme assembly ------------------------------------------------

    @Nested
    @DisplayName("grapheme assembly")
    inner class GraphemeAssembly {

        @Test
        fun `ASCII base followed by combining mark is emitted as one cluster`() {
            val f = Fixture()

            f.processor.acceptAsciiByte(f.state, 'e'.code)
            f.acceptUtf8Codepoints(0x0301)
            f.flush()

            assertEquals(listOf(writeCluster(1, 'e'.code, 0x0301)), f.sink.events)
        }

        @Test
        fun `non-ASCII base followed by combining mark is emitted as one cluster`() {
            val f = Fixture()

            f.acceptUtf8Codepoints(0x03B1, 0x0301)
            f.flush()

            assertEquals(listOf(writeCluster(1, 0x03B1, 0x0301)), f.sink.events)
        }

        @Test
        fun `combining mark at start is emitted as its own scalar`() {
            val f = Fixture()

            f.acceptUtf8Codepoints(0x0301)
            f.flush()

            assertEquals(listOf(writeCodepoint(0x0301)), f.sink.events)
        }

        @Test
        fun `variation selector stays with its base codepoint`() {
            val f = Fixture()

            f.acceptUtf8Codepoints(0x2764, 0xFE0F)
            f.flush()

            assertEquals(listOf(writeCluster(1, 0x2764, 0xFE0F)), f.sink.events)
        }

        @Test
        fun `supplement variation selector stays with its base codepoint`() {
            val f = Fixture()

            f.acceptUtf8Codepoints(0x4E00, 0xE0100)
            f.flush()

            assertEquals(listOf(writeCluster(2, 0x4E00, 0xE0100)), f.sink.events)
        }

        @Test
        fun `emoji ZWJ sequence is emitted as one wide cluster`() {
            val f = Fixture()

            f.acceptUtf8Codepoints(0x1F468, 0x200D, 0x1F469, 0x200D, 0x1F467, 0x200D, 0x1F466)
            f.flush()

            assertEquals(
                listOf(writeCluster(2, 0x1F468, 0x200D, 0x1F469, 0x200D, 0x1F467, 0x200D, 0x1F466)),
                f.sink.events
            )
        }

        @Test
        fun `regional indicators are paired into flag clusters`() {
            val f = Fixture()

            f.acceptUtf8Codepoints(0x1F1FA, 0x1F1F8, 0x1F1E8)
            f.flush()

            assertEquals(
                listOf(
                    writeCluster(2, 0x1F1FA, 0x1F1F8),
                    writeCodepoint(0x1F1E8),
                ),
                f.sink.events
            )
        }

        @Test
        fun `wide base with combining mark keeps width two`() {
            val f = Fixture()

            f.acceptUtf8Codepoints(0x4E00, 0x0301)
            f.flush()

            assertEquals(listOf(writeCluster(2, 0x4E00, 0x0301)), f.sink.events)
        }

        @Test
        fun `new base codepoint flushes prior cluster before starting the next one`() {
            val f = Fixture()

            f.processor.acceptAsciiByte(f.state, 'e'.code)
            f.acceptUtf8Codepoints(0x0301)
            f.processor.acceptAsciiByte(f.state, 'X'.code)
            f.flush()

            assertEquals(
                listOf(writeCluster(1, 'e'.code, 0x0301), writeCodepoint('X'.code)),
                f.sink.events
            )
        }
    }

    // ----- Cluster capacity and state --------------------------------------

    @Nested
    @DisplayName("cluster capacity and state")
    inner class ClusterCapacityAndState {

        @Test
        fun `cluster buffer capacity flushes safely instead of writing out of bounds`() {
            val f = Fixture(state = ParserState(maxCluster = 2))

            f.acceptUtf8Codepoints(0x1F468, 0x200D, 0x1F469)
            f.flush()

            assertEquals(
                listOf(
                    writeCluster(2, 0x1F468, 0x200D),
                    writeCodepoint(0x1F469),
                ),
                f.sink.events
            )
        }

        @Test
        fun `flush clears grapheme context after writing a cluster`() {
            val f = Fixture()

            f.acceptUtf8Codepoints(0x1F1FA, 0x1F1F8)
            f.flush()

            assertAll(
                { assertEquals(0, f.state.clusterLength) },
                { assertEquals(0, f.state.prevGraphemeBreakClass) },
                { assertFalse(f.state.prevWasZwj) },
                { assertEquals(0, f.state.regionalIndicatorParity) }
            )
        }

        @Test
        fun `reset drops pending cluster without writing it`() {
            val f = Fixture()

            f.processor.acceptAsciiByte(f.state, 'A'.code)
            f.reset()
            f.processor.acceptAsciiByte(f.state, 'B'.code)
            f.flush()

            assertAll(
                { assertEquals(listOf(writeCodepoint('B'.code)), f.sink.events) },
                { assertEquals(0, f.state.clusterLength) }
            )
        }
    }

    // ----- PrintableActionSink adapter -------------------------------------

    @Nested
    @DisplayName("PrintableProcessorActionSink")
    inner class PrintableProcessorActionSinkTest {

        @Test
        fun `adapter forwards ASCII UTF-8 and flush callbacks to processor`() {
            val f = Fixture()
            val actionSink = PrintableProcessorActionSink(f.processor)

            actionSink.onAsciiByte(f.state, 'A'.code)
            actionSink.onUtf8Byte(f.state, 0xE2)
            actionSink.onUtf8Byte(f.state, 0x82)
            actionSink.onUtf8Byte(f.state, 0xAC)
            actionSink.flush(f.state)

            assertEquals(
                listOf(writeCodepoint('A'.code), writeCodepoint(0x20AC)),
                f.sink.events
            )
        }
    }
}
