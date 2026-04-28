package com.gagik.terminal.input.impl

import com.gagik.core.api.TerminalModeBits
import com.gagik.terminal.input.event.TerminalKey
import com.gagik.terminal.input.event.TerminalKeyEvent
import com.gagik.terminal.input.event.TerminalModifiers
import com.gagik.terminal.protocol.host.TerminalHostOutput
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test

class KeyboardEncoderTest {

    @Test
    fun `encodes printable UTF-8 codepoints`() {
        assertBytes(bytes(0x61), TerminalKeyEvent.codepoint('a'.code))
        assertBytes(bytes(0xc3, 0xa9), TerminalKeyEvent.codepoint(0x00e9))
        assertBytes(bytes(0xf0, 0x9f, 0x98, 0x80), TerminalKeyEvent.codepoint(0x1f600))
    }

    @Test
    fun `encodes Alt and Ctrl printable combinations`() {
        assertBytes(bytes(0x1b, 0x61), TerminalKeyEvent.codepoint('a'.code, TerminalModifiers.ALT))
        assertBytes(bytes(0x01), TerminalKeyEvent.codepoint('a'.code, TerminalModifiers.CTRL))
        assertBytes(bytes(0x1a), TerminalKeyEvent.codepoint('z'.code, TerminalModifiers.CTRL))
        assertBytes(bytes(0x1b), TerminalKeyEvent.codepoint('['.code, TerminalModifiers.CTRL))
        assertBytes(bytes(0x1c), TerminalKeyEvent.codepoint('\\'.code, TerminalModifiers.CTRL))
        assertBytes(bytes(0x1d), TerminalKeyEvent.codepoint(']'.code, TerminalModifiers.CTRL))
        assertBytes(bytes(0x1e), TerminalKeyEvent.codepoint('^'.code, TerminalModifiers.CTRL))
        assertBytes(bytes(0x1f), TerminalKeyEvent.codepoint('_'.code, TerminalModifiers.CTRL))
        assertBytes(bytes(0x7f), TerminalKeyEvent.codepoint('?'.code, TerminalModifiers.CTRL))
        assertBytes(bytes(0x1b, 0x01), TerminalKeyEvent.codepoint('a'.code, TerminalModifiers.CTRL or TerminalModifiers.ALT))
    }

    @Test
    fun `encodes unmodified special keys`() {
        assertBytes(bytes(0x0d), TerminalKeyEvent.key(TerminalKey.ENTER))
        assertBytes(bytes(0x0d, 0x0a), TerminalKeyEvent.key(TerminalKey.ENTER), TerminalModeBits.NEW_LINE_MODE)
        assertBytes(bytes(0x09), TerminalKeyEvent.key(TerminalKey.TAB))
        assertBytes(esc("[Z"), TerminalKeyEvent.key(TerminalKey.TAB, TerminalModifiers.SHIFT))
        assertBytes(bytes(0x7f), TerminalKeyEvent.key(TerminalKey.BACKSPACE))
        assertBytes(bytes(0x1b), TerminalKeyEvent.key(TerminalKey.ESCAPE))
    }

    @Test
    fun `encodes cursor keys in normal and application modes`() {
        assertBytes(esc("[A"), TerminalKeyEvent.key(TerminalKey.UP))
        assertBytes(esc("[B"), TerminalKeyEvent.key(TerminalKey.DOWN))
        assertBytes(esc("[C"), TerminalKeyEvent.key(TerminalKey.RIGHT))
        assertBytes(esc("[D"), TerminalKeyEvent.key(TerminalKey.LEFT))

        assertBytes(esc("OA"), TerminalKeyEvent.key(TerminalKey.UP), TerminalModeBits.APPLICATION_CURSOR_KEYS)
        assertBytes(esc("OB"), TerminalKeyEvent.key(TerminalKey.DOWN), TerminalModeBits.APPLICATION_CURSOR_KEYS)
        assertBytes(esc("OC"), TerminalKeyEvent.key(TerminalKey.RIGHT), TerminalModeBits.APPLICATION_CURSOR_KEYS)
        assertBytes(esc("OD"), TerminalKeyEvent.key(TerminalKey.LEFT), TerminalModeBits.APPLICATION_CURSOR_KEYS)
    }

    @Test
    fun `encodes modified cursor keys as CSI modifier finals`() {
        assertBytes(esc("[1;5A"), TerminalKeyEvent.key(TerminalKey.UP, TerminalModifiers.CTRL))
        assertBytes(esc("[1;4A"), TerminalKeyEvent.key(TerminalKey.UP, TerminalModifiers.SHIFT or TerminalModifiers.ALT))
    }

    @Test
    fun `encodes home and end in normal and application modes`() {
        assertBytes(esc("[H"), TerminalKeyEvent.key(TerminalKey.HOME))
        assertBytes(esc("[F"), TerminalKeyEvent.key(TerminalKey.END))
        assertBytes(esc("OH"), TerminalKeyEvent.key(TerminalKey.HOME), TerminalModeBits.APPLICATION_CURSOR_KEYS)
        assertBytes(esc("OF"), TerminalKeyEvent.key(TerminalKey.END), TerminalModeBits.APPLICATION_CURSOR_KEYS)
    }

    @Test
    fun `encodes tilde navigation keys with optional modifiers`() {
        assertBytes(esc("[2~"), TerminalKeyEvent.key(TerminalKey.INSERT))
        assertBytes(esc("[3~"), TerminalKeyEvent.key(TerminalKey.DELETE))
        assertBytes(esc("[5~"), TerminalKeyEvent.key(TerminalKey.PAGE_UP))
        assertBytes(esc("[6~"), TerminalKeyEvent.key(TerminalKey.PAGE_DOWN))
        assertBytes(esc("[3;5~"), TerminalKeyEvent.key(TerminalKey.DELETE, TerminalModifiers.CTRL))
    }

    @Test
    fun `encodes function keys`() {
        assertBytes(esc("OP"), TerminalKeyEvent.key(TerminalKey.F1))
        assertBytes(esc("OQ"), TerminalKeyEvent.key(TerminalKey.F2))
        assertBytes(esc("OR"), TerminalKeyEvent.key(TerminalKey.F3))
        assertBytes(esc("OS"), TerminalKeyEvent.key(TerminalKey.F4))
        assertBytes(esc("[15~"), TerminalKeyEvent.key(TerminalKey.F5))
        assertBytes(esc("[24~"), TerminalKeyEvent.key(TerminalKey.F12))
        assertBytes(esc("[1;5P"), TerminalKeyEvent.key(TerminalKey.F1, TerminalModifiers.CTRL))
    }

    @Test
    fun `encodes each normal keypad key`() {
        assertBytes(ascii("0"), TerminalKeyEvent.key(TerminalKey.NUMPAD_0))
        assertBytes(ascii("1"), TerminalKeyEvent.key(TerminalKey.NUMPAD_1))
        assertBytes(ascii("2"), TerminalKeyEvent.key(TerminalKey.NUMPAD_2))
        assertBytes(ascii("3"), TerminalKeyEvent.key(TerminalKey.NUMPAD_3))
        assertBytes(ascii("4"), TerminalKeyEvent.key(TerminalKey.NUMPAD_4))
        assertBytes(ascii("5"), TerminalKeyEvent.key(TerminalKey.NUMPAD_5))
        assertBytes(ascii("6"), TerminalKeyEvent.key(TerminalKey.NUMPAD_6))
        assertBytes(ascii("7"), TerminalKeyEvent.key(TerminalKey.NUMPAD_7))
        assertBytes(ascii("8"), TerminalKeyEvent.key(TerminalKey.NUMPAD_8))
        assertBytes(ascii("9"), TerminalKeyEvent.key(TerminalKey.NUMPAD_9))
        assertBytes(ascii("."), TerminalKeyEvent.key(TerminalKey.NUMPAD_DECIMAL))
        assertBytes(ascii("/"), TerminalKeyEvent.key(TerminalKey.NUMPAD_DIVIDE))
        assertBytes(ascii("*"), TerminalKeyEvent.key(TerminalKey.NUMPAD_MULTIPLY))
        assertBytes(ascii("-"), TerminalKeyEvent.key(TerminalKey.NUMPAD_SUBTRACT))
        assertBytes(ascii("+"), TerminalKeyEvent.key(TerminalKey.NUMPAD_ADD))
    }

    @Test
    fun `encodes application keypad keys`() {
        val bits = TerminalModeBits.APPLICATION_KEYPAD

        assertBytes(esc("Op"), TerminalKeyEvent.key(TerminalKey.NUMPAD_0), bits)
        assertBytes(esc("Oq"), TerminalKeyEvent.key(TerminalKey.NUMPAD_1), bits)
        assertBytes(esc("Or"), TerminalKeyEvent.key(TerminalKey.NUMPAD_2), bits)
        assertBytes(esc("Os"), TerminalKeyEvent.key(TerminalKey.NUMPAD_3), bits)
        assertBytes(esc("Ot"), TerminalKeyEvent.key(TerminalKey.NUMPAD_4), bits)
        assertBytes(esc("Ou"), TerminalKeyEvent.key(TerminalKey.NUMPAD_5), bits)
        assertBytes(esc("Ov"), TerminalKeyEvent.key(TerminalKey.NUMPAD_6), bits)
        assertBytes(esc("Ow"), TerminalKeyEvent.key(TerminalKey.NUMPAD_7), bits)
        assertBytes(esc("Ox"), TerminalKeyEvent.key(TerminalKey.NUMPAD_8), bits)
        assertBytes(esc("Oy"), TerminalKeyEvent.key(TerminalKey.NUMPAD_9), bits)
        assertBytes(esc("On"), TerminalKeyEvent.key(TerminalKey.NUMPAD_DECIMAL), bits)
        assertBytes(esc("Oo"), TerminalKeyEvent.key(TerminalKey.NUMPAD_DIVIDE), bits)
        assertBytes(esc("Oj"), TerminalKeyEvent.key(TerminalKey.NUMPAD_MULTIPLY), bits)
        assertBytes(esc("Om"), TerminalKeyEvent.key(TerminalKey.NUMPAD_SUBTRACT), bits)
        assertBytes(esc("Ok"), TerminalKeyEvent.key(TerminalKey.NUMPAD_ADD), bits)
        assertBytes(esc("OM"), TerminalKeyEvent.key(TerminalKey.NUMPAD_ENTER), bits)
    }

    private fun assertBytes(
        expected: ByteArray,
        event: TerminalKeyEvent,
        modeBits: Long = 0L,
    ) {
        val output = RecordingHostOutput()
        val encoder = KeyboardEncoder(output, InputScratchBuffer())

        encoder.encode(event, modeBits)

        assertArrayEquals(expected, output.bytes)
    }

    private fun esc(textAfterEsc: String): ByteArray {
        return bytes(0x1b) + ascii(textAfterEsc)
    }

    private fun ascii(text: String): ByteArray {
        val bytes = ByteArray(text.length)
        var i = 0
        while (i < text.length) {
            bytes[i] = text[i].code.toByte()
            i++
        }
        return bytes
    }

    private fun bytes(vararg values: Int): ByteArray {
        val bytes = ByteArray(values.size)
        var i = 0
        while (i < values.size) {
            bytes[i] = values[i].toByte()
            i++
        }
        return bytes
    }

    private class RecordingHostOutput : TerminalHostOutput {
        var bytes: ByteArray = ByteArray(0)
            private set

        override fun writeByte(byte: Int) {
            bytes += byte.toByte()
        }

        override fun writeBytes(
            bytes: ByteArray,
            offset: Int,
            length: Int,
        ) {
            this.bytes += bytes.copyOfRange(offset, offset + length)
        }

        override fun writeAscii(text: String) {
            bytes += text.encodeToByteArray()
        }

        override fun writeUtf8(text: String) {
            bytes += text.encodeToByteArray()
        }
    }
}
