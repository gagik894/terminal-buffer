package com.gagik.terminal.input.impl

import com.gagik.core.TerminalBuffers
import com.gagik.terminal.input.event.TerminalFocusEvent
import com.gagik.terminal.input.event.TerminalKey
import com.gagik.terminal.input.event.TerminalKeyEvent
import com.gagik.terminal.input.event.TerminalPasteEvent
import com.gagik.terminal.protocol.host.TerminalHostOutput
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test

class DefaultTerminalInputEncoderCoreStateTest {

    @Test
    fun `uses real core application cursor mode for keyboard encoding`() {
        val terminal = TerminalBuffers.create(width = 4, height = 2)
        val output = RecordingHostOutput()
        val encoder = DefaultTerminalInputEncoder(terminal, output)

        encoder.encodeKey(TerminalKeyEvent.key(TerminalKey.UP))
        terminal.setApplicationCursorKeys(true)
        encoder.encodeKey(TerminalKeyEvent.key(TerminalKey.UP))

        assertArrayEquals(esc("[A") + esc("OA"), output.bytes)
    }

    @Test
    fun `uses real core new line mode for enter encoding`() {
        val terminal = TerminalBuffers.create(width = 4, height = 2)
        val output = RecordingHostOutput()
        val encoder = DefaultTerminalInputEncoder(terminal, output)

        encoder.encodeKey(TerminalKeyEvent.key(TerminalKey.ENTER))
        terminal.setNewLineMode(true)
        encoder.encodeKey(TerminalKeyEvent.key(TerminalKey.ENTER))

        assertArrayEquals(byteArrayOf(0x0d, 0x0d, 0x0a), output.bytes)
    }

    @Test
    fun `uses real core application keypad mode for keypad encoding`() {
        val terminal = TerminalBuffers.create(width = 4, height = 2)
        val output = RecordingHostOutput()
        val encoder = DefaultTerminalInputEncoder(terminal, output)

        encoder.encodeKey(TerminalKeyEvent.key(TerminalKey.NUMPAD_1))
        terminal.setApplicationKeypad(true)
        encoder.encodeKey(TerminalKeyEvent.key(TerminalKey.NUMPAD_1))

        assertArrayEquals("1".encodeToByteArray() + esc("Oq"), output.bytes)
    }

    @Test
    fun `uses real core bracketed paste mode for paste encoding`() {
        val terminal = TerminalBuffers.create(width = 4, height = 2)
        val output = RecordingHostOutput()
        val encoder = DefaultTerminalInputEncoder(terminal, output)

        encoder.encodePaste(TerminalPasteEvent("a"))
        terminal.setBracketedPasteEnabled(true)
        encoder.encodePaste(TerminalPasteEvent("b"))

        assertArrayEquals("a".encodeToByteArray() + esc("[200~") + "b".encodeToByteArray() + esc("[201~"), output.bytes)
    }

    @Test
    fun `uses real core focus reporting mode for focus encoding`() {
        val terminal = TerminalBuffers.create(width = 4, height = 2)
        val output = RecordingHostOutput()
        val encoder = DefaultTerminalInputEncoder(terminal, output)

        encoder.encodeFocus(TerminalFocusEvent(focused = true))
        terminal.setFocusReportingEnabled(true)
        encoder.encodeFocus(TerminalFocusEvent(focused = true))
        encoder.encodeFocus(TerminalFocusEvent(focused = false))

        assertArrayEquals(esc("[I") + esc("[O"), output.bytes)
    }

    @Test
    fun `uses fresh core mode bits across consecutive mixed events`() {
        val terminal = TerminalBuffers.create(width = 4, height = 2)
        val output = RecordingHostOutput()
        val encoder = DefaultTerminalInputEncoder(terminal, output)

        encoder.encodeKey(TerminalKeyEvent.key(TerminalKey.UP))
        terminal.setApplicationCursorKeys(true)
        encoder.encodeKey(TerminalKeyEvent.key(TerminalKey.UP))
        encoder.encodePaste(TerminalPasteEvent("a"))
        terminal.setBracketedPasteEnabled(true)
        encoder.encodePaste(TerminalPasteEvent("b"))
        encoder.encodeFocus(TerminalFocusEvent(focused = true))
        terminal.setFocusReportingEnabled(true)
        encoder.encodeFocus(TerminalFocusEvent(focused = true))
        terminal.setApplicationKeypad(true)
        encoder.encodeKey(TerminalKeyEvent.key(TerminalKey.NUMPAD_1))
        terminal.setNewLineMode(true)
        encoder.encodeKey(TerminalKeyEvent.key(TerminalKey.ENTER))

        assertArrayEquals(
            esc("[A") +
                esc("OA") +
                "a".encodeToByteArray() +
                esc("[200~") +
                "b".encodeToByteArray() +
                esc("[201~") +
                esc("[I") +
                esc("Oq") +
                byteArrayOf(0x0d, 0x0a),
            output.bytes,
        )
    }

    private fun esc(textAfterEsc: String): ByteArray {
        return byteArrayOf(0x1b) + textAfterEsc.encodeToByteArray()
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
