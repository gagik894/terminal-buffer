package com.gagik.parser.unicode

import com.gagik.parser.runtime.ParserState
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("GraphemeSegmenter")
class GraphemeSegmenterTest {

    private fun accept(state: ParserState, codepoint: Int) {
        GraphemeSegmenter.updateContext(state, codepoint)
    }

    @Nested
    @DisplayName("cluster continuation")
    inner class ClusterContinuation {

        @Test
        fun `combining marks variation selectors and spacing marks continue the current cluster`() {
            val state = ParserState()
            accept(state, 'e'.code)

            assertTrue(GraphemeSegmenter.continuesCurrentCluster(state, 0x0301))
            assertTrue(GraphemeSegmenter.continuesCurrentCluster(state, 0xFE0F))
            assertTrue(GraphemeSegmenter.continuesCurrentCluster(state, 0x0903))
        }

        @Test
        fun `extended pictographic ZWJ extended pictographic continues the current cluster`() {
            val state = ParserState()
            accept(state, 0x1F468)
            accept(state, 0x200D)

            assertTrue(GraphemeSegmenter.continuesCurrentCluster(state, 0x1F469))
        }

        @Test
        fun `ZWJ before non extended pictographic does not force continuation`() {
            val state = ParserState()
            accept(state, 'A'.code)
            accept(state, 0x200D)

            assertFalse(GraphemeSegmenter.continuesCurrentCluster(state, 'B'.code))
        }

        @Test
        fun `regional indicators continue only as pairs`() {
            val state = ParserState()
            accept(state, 0x1F1FA)
            assertTrue(GraphemeSegmenter.continuesCurrentCluster(state, 0x1F1F8))

            accept(state, 0x1F1F8)
            assertFalse(GraphemeSegmenter.continuesCurrentCluster(state, 0x1F1E8))
        }

        @Test
        fun `Hangul Jamo sequences continue according to UAX 29 L V T rules`() {
            val state = ParserState()
            accept(state, 0x1100)
            assertTrue(GraphemeSegmenter.continuesCurrentCluster(state, 0x1161))

            accept(state, 0x1161)
            assertTrue(GraphemeSegmenter.continuesCurrentCluster(state, 0x11A8))

            accept(state, 0x11A8)
            assertFalse(GraphemeSegmenter.continuesCurrentCluster(state, 'A'.code))
        }
    }
}
