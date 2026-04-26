package com.gagik.parser.text

import com.gagik.parser.runtime.ParserState
import com.gagik.parser.spi.TerminalCommandSink

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
