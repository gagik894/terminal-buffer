package com.gagik.terminal.input.event

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TerminalFocusEventTest {

    @Test
    fun `stores focus state`() {
        assertTrue(TerminalFocusEvent(focused = true).focused)
        assertFalse(TerminalFocusEvent(focused = false).focused)
    }
}
