package com.gagik.parser.unicode

import com.gagik.parser.runtime.ParserState
import com.gagik.parser.spi.TerminalCommandSink

/**
 * Allocation-free grapheme assembly boundary.
 *
 * The assembler owns cluster buffering only. Unicode classification and break decisions are
 * delegated to [GraphemeSegmenter], while terminal grid width remains owned by :terminal-core.
 */
internal class GraphemeAssembler(
    private val sink: TerminalCommandSink,
) {
    fun accept(state: ParserState, codepoint: Int) {
        val currentClass = UnicodeClass.graphemeBreakClass(codepoint)

        if (state.clusterLength == 0) {
            appendToClusterOrFlush(state, codepoint)
            GraphemeSegmenter.updateContext(state, codepoint, currentClass)
            return
        }

        if (GraphemeSegmenter.continuesCurrentCluster(state, currentClass, codepoint)) {
            appendToClusterOrFlush(state, codepoint)
            GraphemeSegmenter.updateContext(state, codepoint, currentClass)
            return
        }

        flush(state)

        appendToClusterOrFlush(state, codepoint)
        GraphemeSegmenter.updateContext(state, codepoint, currentClass)
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
