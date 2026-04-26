package com.gagik.parser.text

import com.gagik.parser.charset.CharsetMapper
import com.gagik.parser.runtime.ParserState
import com.gagik.parser.spi.TerminalCommandSink
import com.gagik.parser.utf8.Utf8DecodeResult
import com.gagik.parser.utf8.Utf8Decoder

/**
 * Printable ingress bridge.
 *
 * Responsibilities:
 * - Owns UTF-8 byte-to-codepoint forwarding for printable payload bytes.
 * - Applies parser printable policy before grapheme assembly.
 * - Keeps ActionEngine free from UTF-8 and Unicode segmentation details.
 *
 * Current policy:
 * - ASCII bytes are emitted as codepoints directly through [acceptAsciiByte].
 * - UTF-8 bytes are decoded through [Utf8Decoder].
 * - U+FFFD replacement output is treated as normal printable input.
 * - GL charset mapping is applied through [CharsetMapper] before grapheme assembly.
 * - Grapheme segmentation is delegated to [GraphemeAssembler].
 */
internal class PrintableProcessor(
    private val sink: TerminalCommandSink,
    private val utf8Decoder: Utf8Decoder = Utf8Decoder(),
    private val graphemeAssembler: GraphemeAssembler = GraphemeAssembler(sink),
) {
    fun hasPendingUtf8Sequence(): Boolean = utf8Decoder.hasPendingSequence()

    /**
     * Accepts one ASCII-domain printable byte from the ANSI FSM GROUND state.
     *
     * Do not call this when [hasPendingUtf8Sequence] is true. In that case the future top-level
     * parser must call [acceptUtf8DecoderByte] even if the byte is ASCII/control-domain, so the
     * decoder can close or repair the malformed sequence first.
     */
    fun acceptAsciiByte(state: ParserState, byteValue: Int) {
        require(byteValue in 0x20..0x7e) { "byteValue is not printable ASCII: $byteValue" }
        check(!utf8Decoder.hasPendingSequence()) {
            "ASCII printable byte received while UTF-8 decoder has a pending sequence"
        }
        acceptCodepoint(state, byteValue)
    }

    /**
     * Accepts one Unicode codepoint from the ANSI FSM GROUND state.
     *
     * This is the only way to emit a codepoint from the parser.
     * The top-level parser must call this from the ActionEngine callback after UTF-8 decoding,
     * GL charset mapping, and any other policy decisions are applied.
     * The processor will handle grapheme assembly and forwarding to the sink.
     */
    fun acceptDecodedCodepoint(state: ParserState, codepoint: Int) {
        require(codepoint in 0..0x10ffff) { "invalid codepoint: $codepoint" }
        check(!utf8Decoder.hasPendingSequence()) {
            "Decoded codepoint received while UTF-8 decoder has a pending sequence"
        }
        acceptCodepoint(state, codepoint)
    }

    /**
     * Accepts one non-ASCII raw byte routed from ByteClass.UTF8_PAYLOAD.
     */
    fun acceptUtf8Byte(state: ParserState, byteValue: Int) {
        require(byteValue in 0x80..0xff) { "byteValue is not UTF-8 payload byte: $byteValue" }
        acceptUtf8DecoderByte(state, byteValue)
    }

    /**
     * Accepts one byte directly into the UTF-8 decoder.
     *
     * This exists for future TerminalParser orchestration. If the UTF-8 decoder has a pending
     * partial sequence, the next byte must be given to the decoder even when that byte is ASCII or
     * control-domain. If malformed, the decoder may emit U+FFFD and request one bounded reprocess.
     */
    fun acceptUtf8DecoderByte(state: ParserState, byteValue: Int) {
        require(byteValue in 0..255) { "byteValue out of range: $byteValue" }

        val first = utf8Decoder.accept(byteValue)
        if (Utf8DecodeResult.hasOutput(first)) {
            acceptCodepoint(state, Utf8DecodeResult.codepoint(first))
        }

        if (!Utf8DecodeResult.shouldReprocessCurrentByte(first)) {
            return
        }

        val second = utf8Decoder.accept(byteValue)
        if (Utf8DecodeResult.hasOutput(second)) {
            acceptCodepoint(state, Utf8DecodeResult.codepoint(second))
        }

        check(!Utf8DecodeResult.shouldReprocessCurrentByte(second)) {
            "Utf8Decoder violated replay contract: repeated reprocess for byte $byteValue"
        }
    }

    /**
     * Flushes any pending UTF-8 partial sequence at end of input, then flushes printable cluster state.
     */
    fun endOfInput(state: ParserState) {
        val result = utf8Decoder.flushEndOfInput()
        if (Utf8DecodeResult.hasOutput(result)) {
            acceptCodepoint(state, Utf8DecodeResult.codepoint(result))
        }
        flush(state)
    }

    /**
     * Flushes pending UTF-8 repair output and active grapheme state before structural parser actions.
     */
    fun flush(state: ParserState) {
        val result = utf8Decoder.flushEndOfInput()
        if (Utf8DecodeResult.hasOutput(result)) {
            acceptCodepoint(state, Utf8DecodeResult.codepoint(result))
        }
        graphemeAssembler.flush(state)
    }

    fun reset(state: ParserState) {
        utf8Decoder.reset()
        graphemeAssembler.reset(state)
    }

    private fun acceptCodepoint(state: ParserState, codepoint: Int) {
        val mapped = CharsetMapper.map(state, codepoint)
        graphemeAssembler.accept(state, mapped)
    }
}
