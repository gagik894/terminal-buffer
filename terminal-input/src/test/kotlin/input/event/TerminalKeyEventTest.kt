package com.gagik.terminal.input.event

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TerminalKeyEventTest {

    @Test
    fun `creates non-printable key event`() {
        val event = TerminalKeyEvent.key(
            key = TerminalKey.UP,
            modifiers = TerminalModifiers.SHIFT,
        )

        assertAll(
            { assertEquals(TerminalKey.UP, event.key) },
            { assertEquals(TerminalKeyEvent.NO_CODEPOINT, event.codepoint) },
            { assertEquals(TerminalModifiers.SHIFT, event.modifiers) },
        )
    }

    @Test
    fun `creates printable codepoint event`() {
        val event = TerminalKeyEvent.codepoint(
            codepoint = 'a'.code,
            modifiers = TerminalModifiers.ALT,
        )

        assertAll(
            { assertNull(event.key) },
            { assertEquals('a'.code, event.codepoint) },
            { assertEquals(TerminalModifiers.ALT, event.modifiers) },
        )
    }

    @Test
    fun `rejects invalid modifier bits`() {
        assertThrows(IllegalArgumentException::class.java) {
            TerminalKeyEvent.key(
                key = TerminalKey.ENTER,
                modifiers = 1 shl 12,
            )
        }
    }

    @Test
    fun `rejects neither key nor codepoint`() {
        assertThrows(IllegalArgumentException::class.java) {
            TerminalKeyEvent()
        }
    }

    @Test
    fun `rejects both key and codepoint`() {
        assertThrows(IllegalArgumentException::class.java) {
            TerminalKeyEvent(
                key = TerminalKey.ENTER,
                codepoint = 'x'.code,
            )
        }
    }

    @Test
    fun `rejects surrogate codepoint`() {
        assertThrows(IllegalArgumentException::class.java) {
            TerminalKeyEvent.codepoint(0xd800)
        }
    }

    @Test
    fun `rejects codepoint above Unicode scalar range`() {
        assertThrows(IllegalArgumentException::class.java) {
            TerminalKeyEvent.codepoint(0x110000)
        }
    }
}
