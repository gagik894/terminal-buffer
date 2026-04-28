package com.gagik.core.api

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TerminalModeBitsTest {

    @Test
    fun `tests boolean flags`() {
        val bits = TerminalModeBits.APPLICATION_CURSOR_KEYS or TerminalModeBits.BRACKETED_PASTE

        assertTrue(TerminalModeBits.hasFlag(bits, TerminalModeBits.APPLICATION_CURSOR_KEYS))
        assertTrue(TerminalModeBits.hasFlag(bits, TerminalModeBits.BRACKETED_PASTE))
        assertFalse(TerminalModeBits.hasFlag(bits, TerminalModeBits.APPLICATION_KEYPAD))
    }

    @Test
    fun `stores and extracts packed values`() {
        val bits = TerminalModeBits.withPackedValue(
            bits = 0L,
            mask = TerminalModeBits.MOUSE_TRACKING_MASK,
            shift = TerminalModeBits.MOUSE_TRACKING_SHIFT,
            value = 4,
        )

        assertEquals(4, TerminalModeBits.packedValue(bits, TerminalModeBits.MOUSE_TRACKING_MASK, TerminalModeBits.MOUSE_TRACKING_SHIFT))
    }

    @Test
    fun `rejects packed values outside mask`() {
        assertThrows(IllegalArgumentException::class.java) {
            TerminalModeBits.withPackedValue(
                bits = 0L,
                mask = TerminalModeBits.MOUSE_ENCODING_MASK,
                shift = TerminalModeBits.MOUSE_ENCODING_SHIFT,
                value = 4,
            )
        }
    }

    @Test
    fun `TerminalInputState helpers decode input mode bits`() {
        val bits = TerminalModeBits.APPLICATION_CURSOR_KEYS or
            TerminalModeBits.APPLICATION_KEYPAD or
            TerminalModeBits.NEW_LINE_MODE or
            TerminalModeBits.BRACKETED_PASTE or
            TerminalModeBits.FOCUS_REPORTING

        assertTrue(TerminalInputState.isApplicationCursorKeys(bits))
        assertTrue(TerminalInputState.isApplicationKeypad(bits))
        assertTrue(TerminalInputState.isNewLineMode(bits))
        assertTrue(TerminalInputState.isBracketedPasteEnabled(bits))
        assertTrue(TerminalInputState.isFocusReportingEnabled(bits))
    }
}
