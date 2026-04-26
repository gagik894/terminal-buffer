package com.gagik.parser.ansi

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("CsiCommand")
class CsiCommandTest {

    private val allCommands = listOf(
        CsiCommand.UNKNOWN,
        CsiCommand.CUU,
        CsiCommand.CUD,
        CsiCommand.CUF,
        CsiCommand.CUB,
        CsiCommand.CNL,
        CsiCommand.CPL,
        CsiCommand.CHA,
        CsiCommand.CUP,
        CsiCommand.VPA,
        CsiCommand.ED,
        CsiCommand.EL,
        CsiCommand.IL,
        CsiCommand.DL,
        CsiCommand.ICH,
        CsiCommand.DCH,
        CsiCommand.ECH,
        CsiCommand.SU,
        CsiCommand.SD,
        CsiCommand.SM_ANSI,
        CsiCommand.RM_ANSI,
        CsiCommand.SM_DEC,
        CsiCommand.RM_DEC,
        CsiCommand.DECSTR,
        CsiCommand.SGR
    )

    @Nested
    @DisplayName("constants")
    inner class Constants {

        @Test
        fun `command ids are contiguous and stable`() {
            assertEquals((0..24).toList(), allCommands)
        }

        @Test
        fun `UNKNOWN remains the zero command`() {
            assertEquals(0, CsiCommand.UNKNOWN)
        }

        @Test
        fun `command ids are unique`() {
            assertTrue(allCommands.size == allCommands.toSet().size)
        }
    }
}
