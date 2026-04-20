package com.gagik.terminal.buffer

import com.gagik.terminal.TerminalBuffers
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TerminalWriterUnicodeTest {

    @Test
    fun `writeText_combiningMark_doesNotConsumeSecondCell`() {
        val buffer = TerminalBuffers.create(width = 6, height = 2)

        buffer.writeText("e\u0301B")

        val line = buffer.getLine(0)
        val clusterBuf = IntArray(4)
        val clusterLen = line.readCluster(0, clusterBuf)

        assertAll(
            { assertTrue(line.isCluster(0), "Base letter + combining mark must be stored as one cluster") },
            { assertEquals(2, clusterLen) },
            { assertEquals('e'.code, clusterBuf[0]) },
            { assertEquals(0x0301, clusterBuf[1]) },
            { assertEquals('B'.code, buffer.getCodepointAt(1, 0), "Next printable must land in the next cell") },
            { assertEquals(2, buffer.cursorCol) }
        )
    }

    @Test
    fun `writeText_emojiZwjFamily_staysOneClusterAndOneVisualWidthSequence`() {
        val buffer = TerminalBuffers.create(width = 8, height = 2)
        val family = "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67\u200D\uD83D\uDC66"

        buffer.writeText(family + "X")

        val line = buffer.getLine(0)
        val clusterBuf = IntArray(8)
        val clusterLen = line.readCluster(0, clusterBuf)

        assertAll(
            { assertTrue(line.isCluster(0), "ZWJ emoji family must be stored as a single cluster leader") },
            { assertEquals(7, clusterLen) },
            { assertEquals(0x1F468, clusterBuf[0]) },
            { assertEquals(0x200D, clusterBuf[1]) },
            { assertEquals(0x1F469, clusterBuf[2]) },
            { assertEquals(0x200D, clusterBuf[3]) },
            { assertEquals(0x1F467, clusterBuf[4]) },
            { assertEquals(0x200D, clusterBuf[5]) },
            { assertEquals(0x1F466, clusterBuf[6]) },
            { assertEquals(-1, buffer.getCodepointAt(1, 0), "Wide cluster must reserve a spacer cell") },
            { assertEquals('X'.code, buffer.getCodepointAt(2, 0), "Next printable must start after the full visual sequence") },
            { assertEquals(3, buffer.cursorCol) }
        )
    }

    @Test
    fun `writeCodepoint_variationSelector_appendsToPreviousCellNotNextCell`() {
        val buffer = TerminalBuffers.create(width = 6, height = 2)

        buffer.writeCodepoint(0x2764)
        buffer.writeCodepoint(0xFE0F)
        buffer.writeCodepoint('X'.code)

        val line = buffer.getLine(0)
        val clusterBuf = IntArray(4)
        val clusterLen = line.readCluster(0, clusterBuf)

        assertAll(
            { assertTrue(line.isCluster(0), "Variation selector must merge into the previous cell") },
            { assertEquals(2, clusterLen) },
            { assertEquals(0x2764, clusterBuf[0]) },
            { assertEquals(0xFE0F, clusterBuf[1]) },
            { assertEquals('X'.code, buffer.getCodepointAt(1, 0)) },
            { assertEquals(2, buffer.cursorCol) }
        )
    }
}
