package com.gagik.terminal.render.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class TerminalRenderFrameReaderTest {
    @Test
    fun `reader passes short lived frame to callback`() {
        val frame = RecordingFrame(columns = 80, rows = 24)
        val reader = object : TerminalRenderFrameReader {
            override fun readRenderFrame(consumer: TerminalRenderFrameConsumer) {
                consumer.accept(frame)
            }
        }

        var observed: TerminalRenderFrame? = null
        reader.readRenderFrame { callbackFrame ->
            observed = callbackFrame
            assertEquals(80, callbackFrame.columns)
            assertEquals(24, callbackFrame.rows)
        }

        assertSame(frame, observed)
    }

    private class RecordingFrame(
        override val columns: Int,
        override val rows: Int,
    ) : TerminalRenderFrame {
        override val frameGeneration: Long = 0L
        override val structureGeneration: Long = 0L
        override val activeBuffer: TerminalRenderBufferKind = TerminalRenderBufferKind.PRIMARY
        override val cursor: TerminalRenderCursor = TerminalRenderCursor(
            column = 0,
            row = 0,
            visible = true,
            blinking = false,
            shape = TerminalRenderCursorShape.BLOCK,
            generation = 0L,
        )

        override fun lineGeneration(row: Int): Long = 0L

        override fun lineWrapped(row: Int): Boolean = false

        override fun copyLine(
            row: Int,
            codeWords: IntArray,
            codeOffset: Int,
            attrWords: LongArray,
            attrOffset: Int,
            flags: IntArray,
            flagOffset: Int,
            extraAttrWords: LongArray?,
            extraAttrOffset: Int,
            hyperlinkIds: IntArray?,
            hyperlinkOffset: Int,
            clusterSink: TerminalRenderClusterSink?,
        ) {
            throw UnsupportedOperationException("not needed for callback contract test")
        }
    }
}
