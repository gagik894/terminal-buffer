package com.gagik.core.state

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ScreenBufferLifecycleTest {

    @Test
    fun `clearGrid_withHistoryBacklog_doesNotLeakClusterHandles`() {
        val buffer = ScreenBuffer(initialWidth = 4, initialHeight = 2, maxHistory = 3)
        buffer.clearGrid(penAttr = 0, viewportHeight = 2)
        repeat(3) { buffer.ring.push().clear(0) }

        val backlogLine = buffer.ring[4]
        backlogLine.setCluster(0, intArrayOf('A'.code, 0x0301), 2, 0)
        val originalHandle = backlogLine.rawCodepoint(0)

        buffer.clearGrid(penAttr = 0, viewportHeight = 2)
        buffer.ring[0].setCluster(0, intArrayOf('B'.code, 0x0301), 2, 0)

        assertEquals(
            originalHandle,
            buffer.ring[0].rawCodepoint(0),
            "Clearing the grid must release cluster slots from discarded history lines"
        )
    }
}
