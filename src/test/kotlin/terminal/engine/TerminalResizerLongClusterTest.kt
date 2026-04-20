package com.gagik.terminal.engine

import com.gagik.terminal.state.TerminalState
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TerminalResizerLongClusterTest {

    @Test
    fun `resize_longCluster_over16Codepoints_followsDefinedPolicy`() {
        val state = TerminalState(initialWidth = 8, initialHeight = 2, maxHistory = 0)
        val cluster = IntArray(17) { 0x1000 + it }
        val line = state.primaryBuffer.ring[state.resolveRingIndex(1)]
        line.setCluster(0, cluster, cluster.size, 7)
        state.cursor.row = 1

        assertDoesNotThrow {
            TerminalResizer.resizeBuffer(
                buffer = state.primaryBuffer,
                oldWidth = 8,
                oldHeight = 2,
                newWidth = 6,
                newHeight = 2
            )
        }

        val resizedLine = state.primaryBuffer.ring[(state.primaryBuffer.ring.size - 2).coerceAtLeast(0)]
        val dest = IntArray(17)
        val written = resizedLine.readCluster(0, dest)
        assertAll(
            { assertEquals(17, written) },
            { assertEquals(cluster.toList(), dest.take(written)) }
        )
    }
}
