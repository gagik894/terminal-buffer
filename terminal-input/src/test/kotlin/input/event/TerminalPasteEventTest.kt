package com.gagik.terminal.input.event

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TerminalPasteEventTest {

    @Test
    fun `stores paste text`() {
        assertEquals("hello", TerminalPasteEvent("hello").text)
    }
}
