package com.gagik.parser.ansi

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("GeneratedCsiDispatchTable")
class GeneratedCsiDispatchTableTest {

    @Nested
    @DisplayName("lookup")
    inner class Lookup {

        @Test
        fun `known plain CSI signatures route to command ids`() {
            assertEquals(CsiCommand.ICH, lookup('@'))
            assertEquals(CsiCommand.CUU, lookup('A'))
            assertEquals(CsiCommand.CUD, lookup('B'))
            assertEquals(CsiCommand.CUF, lookup('C'))
            assertEquals(CsiCommand.CUB, lookup('D'))
            assertEquals(CsiCommand.CNL, lookup('E'))
            assertEquals(CsiCommand.CPL, lookup('F'))
            assertEquals(CsiCommand.CHA, lookup('G'))
            assertEquals(CsiCommand.CUP, lookup('H'))
            assertEquals(CsiCommand.CHT, lookup('I'))
            assertEquals(CsiCommand.ED, lookup('J'))
            assertEquals(CsiCommand.EL, lookup('K'))
            assertEquals(CsiCommand.IL, lookup('L'))
            assertEquals(CsiCommand.DL, lookup('M'))
            assertEquals(CsiCommand.DCH, lookup('P'))
            assertEquals(CsiCommand.SU, lookup('S'))
            assertEquals(CsiCommand.SD, lookup('T'))
            assertEquals(CsiCommand.ECH, lookup('X'))
            assertEquals(CsiCommand.CBT, lookup('Z'))
            assertEquals(CsiCommand.VPA, lookup('d'))
            assertEquals(CsiCommand.CUP, lookup('f'))
            assertEquals(CsiCommand.TBC, lookup('g'))
            assertEquals(CsiCommand.SM_ANSI, lookup('h'))
            assertEquals(CsiCommand.RM_ANSI, lookup('l'))
            assertEquals(CsiCommand.SGR, lookup('m'))
            assertEquals(CsiCommand.DECSTBM, lookup('r'))
            assertEquals(CsiCommand.DECSLRM, lookup('s'))
        }

        @Test
        fun `DEC private mode signatures route separately from ANSI modes`() {
            assertEquals(CsiCommand.SM_DEC, lookup('h', privateMarker = '?'.code))
            assertEquals(CsiCommand.RM_DEC, lookup('l', privateMarker = '?'.code))
        }

        @Test
        fun `DEC selective erase signatures route separately from normal erase`() {
            assertEquals(CsiCommand.DECSED, lookup('J', privateMarker = '?'.code))
            assertEquals(CsiCommand.DECSEL, lookup('K', privateMarker = '?'.code))
        }

        @Test
        fun `DECSTR routes only through CSI bang p`() {
            assertEquals(CsiCommand.DECSTR, lookup('p', intermediates = '!'.code, intermediateCount = 1))
            assertEquals(CsiCommand.UNKNOWN, lookup('p'))
            assertEquals(CsiCommand.UNKNOWN, lookup('p', intermediates = '"'.code, intermediateCount = 1))
        }

        @Test
        fun `DECSCA routes only through CSI double quote q`() {
            assertEquals(CsiCommand.DECSCA, lookup('q', intermediates = '"'.code, intermediateCount = 1))
            assertEquals(CsiCommand.UNKNOWN, lookup('q'))
            assertEquals(CsiCommand.UNKNOWN, lookup('q', intermediates = '!'.code, intermediateCount = 1))
        }

        @Test
        fun `unknown signatures return UNKNOWN`() {
            assertEquals(CsiCommand.UNKNOWN, lookup('m', privateMarker = '?'.code))
            assertEquals(CsiCommand.UNKNOWN, lookup('A', privateMarker = '?'.code))
            assertEquals(CsiCommand.UNKNOWN, lookup('A', intermediates = '!'.code, intermediateCount = 1))
        }
    }

    private fun lookup(
        finalByte: Char,
        privateMarker: Int = 0,
        intermediates: Int = 0,
        intermediateCount: Int = 0,
    ): Int = GeneratedCsiDispatchTable.lookup(
        CsiSignature.encode(
            finalByte = finalByte.code,
            privateMarker = privateMarker,
            intermediates = intermediates,
            intermediateCount = intermediateCount
        )
    )
}
