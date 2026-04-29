package com.gagik.terminal.render.api

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TerminalRenderExtraAttrsTest {
    @Test
    fun `default word decodes to default underline color and no overline`() {
        val word = TerminalRenderExtraAttrs.DEFAULT

        assertAll(
            { assertEquals(TerminalRenderColorKind.DEFAULT, TerminalRenderExtraAttrs.underlineColorKind(word)) },
            { assertEquals(0, TerminalRenderExtraAttrs.underlineColorValue(word)) },
            { assertFalse(TerminalRenderExtraAttrs.isOverline(word)) },
        )
    }

    @Test
    fun `packed underline color and overline decode through public ABI`() {
        val word = TerminalRenderExtraAttrs.pack(
            underlineColorKind = TerminalRenderColorKind.RGB,
            underlineColorValue = 0xAA_55_11,
            overline = true,
        )

        assertAll(
            { assertEquals(TerminalRenderColorKind.RGB, TerminalRenderExtraAttrs.underlineColorKind(word)) },
            { assertEquals(0xAA_55_11, TerminalRenderExtraAttrs.underlineColorValue(word)) },
            { assertTrue(TerminalRenderExtraAttrs.isOverline(word)) },
            {
                assertEquals(
                    (TerminalRenderColorKind.RGB.toLong() shl 0) or
                        (0xAA_55_11L shl 2) or
                        (1L shl 26),
                    word,
                )
            },
        )
    }

    @Test
    fun `packer rejects invalid underline color values`() {
        assertAll(
            {
                assertThrows(IllegalArgumentException::class.java) {
                    TerminalRenderExtraAttrs.pack(
                        underlineColorKind = TerminalRenderColorKind.DEFAULT,
                        underlineColorValue = 1,
                    )
                }
            },
            {
                assertThrows(IllegalArgumentException::class.java) {
                    TerminalRenderExtraAttrs.pack(
                        underlineColorKind = TerminalRenderColorKind.INDEXED,
                        underlineColorValue = 256,
                    )
                }
            },
            {
                assertThrows(IllegalArgumentException::class.java) {
                    TerminalRenderExtraAttrs.pack(underlineColorKind = 3)
                }
            },
        )
    }
}
