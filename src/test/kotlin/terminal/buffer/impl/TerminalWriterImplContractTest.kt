package com.gagik.terminal.buffer.impl

import com.gagik.terminal.TerminalBuffers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TerminalWriterImplContractTest {

    @Test
    fun `clearAll_resetsTabStopsToDefault`() {
        val buffer = TerminalBuffers.create(width = 12, height = 2)
        buffer.clearAllTabStops()
        buffer.clearAll()
        buffer.horizontalTab()

        assertEquals(8, buffer.cursorCol, "clearAll must restore the default VT tab stops")
    }
}
