package com.gagik.terminal.input.event

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TerminalModifiersTest {

    @Test
    fun `recognizes individual modifier bits`() {
        val modifiers = TerminalModifiers.SHIFT or TerminalModifiers.CTRL

        assertAll(
            { assertTrue(TerminalModifiers.hasShift(modifiers)) },
            { assertFalse(TerminalModifiers.hasAlt(modifiers)) },
            { assertTrue(TerminalModifiers.hasCtrl(modifiers)) },
            { assertFalse(TerminalModifiers.hasMeta(modifiers)) },
        )
    }

    @Test
    fun `validates modifier bitmask`() {
        assertAll(
            { assertTrue(TerminalModifiers.isValid(TerminalModifiers.NONE)) },
            { assertTrue(TerminalModifiers.isValid(TerminalModifiers.VALID_MASK)) },
            { assertFalse(TerminalModifiers.isValid(TerminalModifiers.VALID_MASK + 1)) },
            { assertFalse(TerminalModifiers.isValid(-1)) },
        )
    }

    @Test
    fun `converts modifier bits to CSI modifier parameter`() {
        assertAll(
            { assertEquals(1, TerminalModifiers.toCsiModifierParam(TerminalModifiers.NONE)) },
            { assertEquals(2, TerminalModifiers.toCsiModifierParam(TerminalModifiers.SHIFT)) },
            { assertEquals(3, TerminalModifiers.toCsiModifierParam(TerminalModifiers.ALT)) },
            {
                assertEquals(
                    4,
                    TerminalModifiers.toCsiModifierParam(
                        TerminalModifiers.SHIFT or TerminalModifiers.ALT,
                    ),
                )
            },
            { assertEquals(5, TerminalModifiers.toCsiModifierParam(TerminalModifiers.CTRL)) },
            { assertEquals(9, TerminalModifiers.toCsiModifierParam(TerminalModifiers.META)) },
        )
    }

    @Test
    fun `rejects invalid modifier bits for CSI parameter`() {
        assertThrows(IllegalArgumentException::class.java) {
            TerminalModifiers.toCsiModifierParam(1 shl 8)
        }
    }
}
