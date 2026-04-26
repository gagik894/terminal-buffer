package com.gagik.parser.text

import com.gagik.parser.runtime.ParserState
import com.gagik.parser.spi.TerminalCommandSink
import com.gagik.parser.unicode.GraphemeSegmenter

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
            GraphemeSegmenter.updateContext(state, codepoint)
            return
        }

        if (GraphemeSegmenter.continuesCurrentCluster(state, codepoint)) {
            appendToClusterOrFlush(state, codepoint)
            GraphemeSegmenter.updateContext(state, codepoint)
            return
        }

        flush(state)

        appendToClusterOrFlush(state, codepoint)
        GraphemeSegmenter.updateContext(state, codepoint)
    }

    fun flush(state: ParserState) {
        when (state.clusterLength) {
            0 -> return
            1 -> sink.writeCodepoint(state.clusterBuffer[0])
            else -> sink.writeCluster(
                codepoints = state.clusterBuffer,
                length = state.clusterLength,
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
}
