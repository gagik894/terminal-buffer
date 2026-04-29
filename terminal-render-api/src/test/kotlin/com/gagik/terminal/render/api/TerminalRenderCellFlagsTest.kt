package com.gagik.terminal.render.api

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TerminalRenderCellFlagsTest {
    @Test
    fun `valid flag combinations match public cell encoding contract`() {
        assertAll(
            { assertTrue(TerminalRenderCellFlags.isValidCombination(TerminalRenderCellFlags.EMPTY)) },
            { assertTrue(TerminalRenderCellFlags.isValidCombination(TerminalRenderCellFlags.CODEPOINT)) },
            {
                assertTrue(
                    TerminalRenderCellFlags.isValidCombination(
                        TerminalRenderCellFlags.CODEPOINT or TerminalRenderCellFlags.WIDE_LEADING,
                    ),
                )
            },
            { assertTrue(TerminalRenderCellFlags.isValidCombination(TerminalRenderCellFlags.CLUSTER)) },
            {
                assertTrue(
                    TerminalRenderCellFlags.isValidCombination(
                        TerminalRenderCellFlags.CLUSTER or TerminalRenderCellFlags.WIDE_LEADING,
                    ),
                )
            },
            { assertTrue(TerminalRenderCellFlags.isValidCombination(TerminalRenderCellFlags.WIDE_TRAILING)) },
        )
    }

    @Test
    fun `invalid flag combinations are rejected`() {
        assertAll(
            {
                assertFalse(
                    TerminalRenderCellFlags.isValidCombination(
                        TerminalRenderCellFlags.EMPTY or TerminalRenderCellFlags.CODEPOINT,
                    ),
                )
            },
            {
                assertFalse(
                    TerminalRenderCellFlags.isValidCombination(
                        TerminalRenderCellFlags.EMPTY or TerminalRenderCellFlags.CLUSTER,
                    ),
                )
            },
            {
                assertFalse(
                    TerminalRenderCellFlags.isValidCombination(
                        TerminalRenderCellFlags.CODEPOINT or TerminalRenderCellFlags.CLUSTER,
                    ),
                )
            },
            {
                assertFalse(
                    TerminalRenderCellFlags.isValidCombination(
                        TerminalRenderCellFlags.WIDE_TRAILING or TerminalRenderCellFlags.CODEPOINT,
                    ),
                )
            },
            {
                assertFalse(
                    TerminalRenderCellFlags.isValidCombination(
                        TerminalRenderCellFlags.WIDE_TRAILING or TerminalRenderCellFlags.CLUSTER,
                    ),
                )
            },
            {
                assertFalse(
                    TerminalRenderCellFlags.isValidCombination(
                        TerminalRenderCellFlags.WIDE_TRAILING or TerminalRenderCellFlags.WIDE_LEADING,
                    ),
                )
            },
            { assertFalse(TerminalRenderCellFlags.isValidCombination(0)) },
            { assertFalse(TerminalRenderCellFlags.isValidCombination(1 shl 12)) },
        )
    }
}
