package com.gagik.terminal.render.cache

import com.gagik.terminal.render.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.concurrent.thread

class TerminalRenderPublisherTest {

    @Test
    fun `TerminalRenderCache ownership assertion`() {
        val cache = TerminalRenderCache(3, 1)
        cache.assertOwnership() // owner = main

        var exception: Throwable? = null
        val t = thread(start = true) {
            try {
                cache.assertOwnership()
            } catch (e: Throwable) {
                exception = e
            }
        }
        t.join()

        assertNotNull(exception)
        assertTrue(exception is IllegalStateException)
        assertTrue(exception?.message?.contains("owned by") == true)
    }

    @Test
    fun `updateAndPublish rotates buffers`() {
        val publisher = TerminalRenderPublisher(3, 1)
        val frame1 = MockFrame(3, 1, "abc")
        val frame2 = MockFrame(3, 1, "def")

        publisher.updateAndPublish(frame1)
        val firstFront = publisher.current()
        assertEquals("abc", firstFront?.rowText(0))

        publisher.updateAndPublish(frame2)
        val secondFront = publisher.current()
        assertEquals("def", secondFront?.rowText(0))

        // Ensure they are different objects (triple buffering)
        assertNotSame(firstFront, secondFront)
    }

    @Test
    fun `resize updates all buffers`() {
        val publisher = TerminalRenderPublisher(3, 1)
        publisher.resize(5, 2)

        // Triple buffering: check other buffers via updateAndPublish
        val frame = MockFrame(5, 2, "12345")
        publisher.updateAndPublish(frame)
        assertEquals("12345", publisher.current()?.rowText(0))
    }

    @Test
    fun `resize resets buffer ownership for next render worker update`() {
        val publisher = TerminalRenderPublisher(3, 1)
        val firstFrame = MockFrame(3, 1, "abc")
        val secondFrame = MockFrame(5, 2, "12345")

        val firstRenderThread = thread(start = true) {
            publisher.updateAndPublish(firstFrame)
        }
        firstRenderThread.join()

        publisher.resize(5, 2)

        var exception: Throwable? = null
        val secondRenderThread = thread(start = true) {
            try {
                publisher.updateAndPublish(secondFrame)
            } catch (e: Throwable) {
                exception = e
            }
        }
        secondRenderThread.join()

        assertNull(exception)
        assertEquals("12345", publisher.current()?.rowText(0))
    }

    private fun TerminalRenderCache.rowText(row: Int): String =
        codeWords[row].map { if (it == 0) ' ' else it.toChar() }.joinToString("")

    private class MockFrame(
        override val columns: Int,
        override val rows: Int,
        val text: String
    ) : TerminalRenderFrame, TerminalRenderFrameReader {

        override val frameGeneration: Long = 1L
        override val structureGeneration: Long = 1L
        override val activeBuffer: TerminalRenderBufferKind = TerminalRenderBufferKind.PRIMARY
        override val cursor: TerminalRenderCursor = TerminalRenderCursor(0, 0, true, false, TerminalRenderCursorShape.BLOCK, 1L)

        override fun lineGeneration(row: Int): Long = 1L
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
            clusterSink: TerminalRenderClusterSink?
        ) {
            for (i in 0 until columns) {
                codeWords[codeOffset + i] = text.getOrNull(i)?.code ?: 0
                flags[flagOffset + i] = TerminalRenderCellFlags.CODEPOINT
            }
        }

        override fun readRenderFrame(consumer: TerminalRenderFrameConsumer) {
            consumer.accept(this)
        }
    }
}
