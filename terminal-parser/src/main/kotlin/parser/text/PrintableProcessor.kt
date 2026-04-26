package com.gagik.parser.text

import com.gagik.parser.TerminalCommandSink
import com.gagik.parser.ansi.PrintableActionSink
import com.gagik.parser.charset.CharsetMapper
import com.gagik.parser.runtime.ParserState
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

/**
 * Minimal allocation-free grapheme assembly boundary.
 *
 * This is intentionally conservative. Full UAX #29 support belongs behind this same API after
 * generated Unicode property tables are added.
 */
internal class GraphemeAssembler(
    private val sink: TerminalCommandSink,
) {
    fun accept(state: ParserState, codepoint: Int) {
        if (state.clusterLength == 0) {
            appendToClusterOrFlush(state, codepoint)
            updateMinimalContext(state, codepoint)
            return
        }

        if (continuesCurrentCluster(state, codepoint)) {
            appendToClusterOrFlush(state, codepoint)
            updateMinimalContext(state, codepoint)
            return
        }

        flush(state)

        appendToClusterOrFlush(state, codepoint)
        updateMinimalContext(state, codepoint)
    }

    fun flush(state: ParserState) {
        when (state.clusterLength) {
            0 -> return
            1 -> sink.writeCodepoint(state.clusterBuffer[0])
            else -> sink.writeCluster(
                codepoints = state.clusterBuffer,
                length = state.clusterLength,
                charWidth = clusterWidth(state),
            )
        }
        state.clearActiveClusterAfterFlush()
    }

    fun reset(state: ParserState) {
        state.clearActiveClusterAfterFlush()
    }

    private fun appendToClusterOrFlush(state: ParserState, codepoint: Int) {
        if (state.clusterLength >= state.clusterBuffer.size) {
            flush(state)
        }
        state.clusterBuffer[state.clusterLength] = codepoint
        state.clusterLength++
    }

    private fun continuesCurrentCluster(state: ParserState, codepoint: Int): Boolean {
        return isCombiningMark(codepoint) ||
                isVariationSelector(codepoint) ||
                codepoint == ZERO_WIDTH_JOINER ||
                state.prevWasZwj ||
                isRegionalIndicatorContinuation(state, codepoint)
    }

    private fun updateMinimalContext(state: ParserState, codepoint: Int) {
        state.prevWasZwj = codepoint == ZERO_WIDTH_JOINER

        if (isRegionalIndicator(codepoint)) {
            state.regionalIndicatorParity = state.regionalIndicatorParity xor 1
        } else {
            state.regionalIndicatorParity = 0
        }

        state.prevGraphemeBreakClass = minimalBreakClass(codepoint)
    }

    private fun isRegionalIndicatorContinuation(state: ParserState, codepoint: Int): Boolean {
        return isRegionalIndicator(codepoint) && state.regionalIndicatorParity == 1
    }

    private fun clusterWidth(state: ParserState): Int {
        // Minimal policy. Full width policy comes later with generated Unicode tables and mode-aware
        // ambiguous-width handling.
        var i = 0
        while (i < state.clusterLength) {
            val cp = state.clusterBuffer[i]
            if (isWideCodepointApprox(cp)) {
                return 2
            }
            i++
        }
        return 1
    }

    private fun isCombiningMark(codepoint: Int): Boolean {
        return codepoint in 0x0300..0x036f ||
                codepoint in 0x1ab0..0x1aff ||
                codepoint in 0x1dc0..0x1dff ||
                codepoint in 0x20d0..0x20ff ||
                codepoint in 0xfe20..0xfe2f
    }

    private fun isVariationSelector(codepoint: Int): Boolean {
        return codepoint in 0xfe00..0xfe0f || codepoint in 0xe0100..0xe01ef
    }

    private fun isRegionalIndicator(codepoint: Int): Boolean = codepoint in 0x1f1e6..0x1f1ff

    private fun isWideCodepointApprox(codepoint: Int): Boolean {
        return codepoint in 0x1100..0x115f ||
                codepoint in 0x2329..0x232a ||
                codepoint in 0x2e80..0xa4cf ||
                codepoint in 0xac00..0xd7a3 ||
                codepoint in 0xf900..0xfaff ||
                codepoint in 0xfe10..0xfe19 ||
                codepoint in 0xfe30..0xfe6f ||
                codepoint in 0xff00..0xff60 ||
                codepoint in 0xffe0..0xffe6 ||
                isRegionalIndicator(codepoint) ||
                codepoint in 0x1f300..0x1faff
    }

    private fun minimalBreakClass(codepoint: Int): Int {
        return when {
            codepoint == ZERO_WIDTH_JOINER -> BREAK_ZWJ
            isCombiningMark(codepoint) -> BREAK_EXTEND
            isVariationSelector(codepoint) -> BREAK_EXTEND
            isRegionalIndicator(codepoint) -> BREAK_REGIONAL_INDICATOR
            else -> BREAK_OTHER
        }
    }

    companion object {
        private const val ZERO_WIDTH_JOINER: Int = 0x200d

        private const val BREAK_OTHER: Int = 0
        private const val BREAK_EXTEND: Int = 1
        private const val BREAK_ZWJ: Int = 2
        private const val BREAK_REGIONAL_INDICATOR: Int = 3
    }
}

/**
 * Adapter from ANSI ActionEngine printable callbacks to the real printable processor.
 */
internal class PrintableProcessorActionSink(
    private val processor: PrintableProcessor,
) : PrintableActionSink {
    override fun onAsciiByte(state: ParserState, byteValue: Int) {
        if (processor.hasPendingUtf8Sequence()) {
            processor.acceptUtf8DecoderByte(state, byteValue)
        } else {
            processor.acceptAsciiByte(state, byteValue)
        }
    }

    override fun onUtf8Byte(state: ParserState, byteValue: Int) {
        processor.acceptUtf8Byte(state, byteValue)
    }

    override fun flush(state: ParserState) {
        processor.flush(state)
    }
}
