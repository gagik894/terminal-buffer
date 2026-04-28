package com.gagik.terminal.protocol

import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TerminalProtocolModesTest {

    @Test
    fun `control codes expose ANSI wire byte values`() {
        assertAll(
            { assertEquals(0x00, ControlCode.NUL) },
            { assertEquals(0x07, ControlCode.BEL) },
            { assertEquals(0x08, ControlCode.BS) },
            { assertEquals(0x09, ControlCode.HT) },
            { assertEquals(0x0A, ControlCode.LF) },
            { assertEquals(0x0B, ControlCode.VT) },
            { assertEquals(0x0C, ControlCode.FF) },
            { assertEquals(0x0D, ControlCode.CR) },
            { assertEquals(0x18, ControlCode.CAN) },
            { assertEquals(0x1A, ControlCode.SUB) },
            { assertEquals(0x1B, ControlCode.ESC) },
            { assertEquals(0x7F, ControlCode.DEL) },
            { assertEquals(0x99, ControlCode.SGCI) },
            { assertEquals(0x9B, ControlCode.CSI) },
            { assertEquals(0x9D, ControlCode.OSC) },
        )
    }

    @Test
    fun `ANSI mode ids match CSI SM and RM protocol values`() {
        assertAll(
            { assertEquals(4, AnsiMode.INSERT) },
            { assertEquals(20, AnsiMode.NEW_LINE) },
        )
    }

    @Test
    fun `DEC private mode ids match common terminal protocol values`() {
        assertAll(
            { assertEquals(1, DecPrivateMode.APPLICATION_CURSOR_KEYS) },
            { assertEquals(6, DecPrivateMode.ORIGIN) },
            { assertEquals(7, DecPrivateMode.AUTO_WRAP) },
            { assertEquals(12, DecPrivateMode.CURSOR_BLINK) },
            { assertEquals(25, DecPrivateMode.CURSOR_VISIBLE) },
            { assertEquals(47, DecPrivateMode.ALT_SCREEN) },
            { assertEquals(1047, DecPrivateMode.ALT_SCREEN_BUFFER) },
            { assertEquals(1048, DecPrivateMode.SAVE_RESTORE_CURSOR) },
            { assertEquals(1049, DecPrivateMode.ALT_SCREEN_SAVE_CURSOR) },
            { assertEquals(1000, DecPrivateMode.MOUSE_NORMAL) },
            { assertEquals(1002, DecPrivateMode.MOUSE_BUTTON_EVENT) },
            { assertEquals(1003, DecPrivateMode.MOUSE_ANY_EVENT) },
            { assertEquals(1005, DecPrivateMode.MOUSE_UTF8) },
            { assertEquals(1006, DecPrivateMode.MOUSE_SGR) },
            { assertEquals(1015, DecPrivateMode.MOUSE_URXVT) },
            { assertEquals(2004, DecPrivateMode.BRACKETED_PASTE) },
        )
    }

    @Test
    fun `mouse modes expose input and core shared vocabulary`() {
        assertAll(
            { assertEquals("OFF", MouseTrackingMode.OFF.name) },
            { assertEquals("SGR", MouseEncodingMode.SGR.name) },
            { assertEquals("URXVT", MouseEncodingMode.URXVT.name) },
        )
    }
}
