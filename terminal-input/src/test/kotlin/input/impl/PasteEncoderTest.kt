package com.gagik.terminal.input.impl

import com.gagik.core.api.TerminalModeBits
import com.gagik.terminal.input.event.TerminalPasteEvent
import com.gagik.terminal.protocol.host.TerminalHostOutput
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test

class PasteEncoderTest {

    @Test
    fun `plain paste writes UTF-8 text`() {
        assertBytes("plain é".encodeToByteArray(), TerminalPasteEvent("plain é"))
    }

    @Test
    fun `bracketed paste wraps UTF-8 text`() {
        assertBytes(
            expected = esc("[200~") + "text".encodeToByteArray() + esc("[201~"),
            event = TerminalPasteEvent("text"),
            modeBits = TerminalModeBits.BRACKETED_PASTE,
        )
    }

    @Test
    fun `empty bracketed paste still emits wrappers`() {
        assertBytes(
            expected = esc("[200~") + esc("[201~"),
            event = TerminalPasteEvent(""),
            modeBits = TerminalModeBits.BRACKETED_PASTE,
        )
    }

    private fun assertBytes(
        expected: ByteArray,
        event: TerminalPasteEvent,
        modeBits: Long = 0L,
    ) {
        val output = RecordingHostOutput()
        val encoder = PasteEncoder(output)

        encoder.encode(event, modeBits)

        assertArrayEquals(expected, output.bytes)
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
