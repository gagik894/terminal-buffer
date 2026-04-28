package com.gagik.terminal.input.impl

import com.gagik.core.api.TerminalInputState
import com.gagik.core.api.TerminalModeBits
import com.gagik.terminal.input.event.TerminalFocusEvent
import com.gagik.terminal.input.event.TerminalKey
import com.gagik.terminal.input.event.TerminalKeyEvent
import com.gagik.terminal.input.event.TerminalPasteEvent
import com.gagik.terminal.protocol.host.TerminalHostOutput
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DefaultTerminalInputEncoderTest {

    @Test
    fun `encodeKey reads mode bits once per event`() {
        val inputState = RecordingInputState(TerminalModeBits.APPLICATION_CURSOR_KEYS)
        val output = RecordingHostOutput()
        val encoder = DefaultTerminalInputEncoder(inputState, output)

        encoder.encodeKey(TerminalKeyEvent.key(TerminalKey.UP))

        assertEquals(1, inputState.reads)
        assertArrayEquals(byteArrayOf(0x1b, 'O'.code.toByte(), 'A'.code.toByte()), output.bytes)
    }

    @Test
    fun `encodePaste reads mode bits once per event`() {
        val inputState = RecordingInputState(TerminalModeBits.BRACKETED_PASTE)
        val output = RecordingHostOutput()
        val encoder = DefaultTerminalInputEncoder(
            inputState = inputState,
            output = output,
        )

        encoder.encodePaste(TerminalPasteEvent("text"))

        assertEquals(1, inputState.reads)
        assertArrayEquals(esc("[200~") + "text".encodeToByteArray() + esc("[201~"), output.bytes)
    }

    @Test
    fun `encodeFocus reads mode bits once per event`() {
        val inputState = RecordingInputState(TerminalModeBits.FOCUS_REPORTING)
        val output = RecordingHostOutput()
        val encoder = DefaultTerminalInputEncoder(
            inputState = inputState,
            output = output,
        )

        encoder.encodeFocus(TerminalFocusEvent(focused = true))

        assertEquals(1, inputState.reads)
        assertArrayEquals(esc("[I"), output.bytes)
    }

    private fun esc(textAfterEsc: String): ByteArray {
        return byteArrayOf(0x1b) + textAfterEsc.encodeToByteArray()
    }

    private class RecordingInputState(
        private val bits: Long,
    ) : TerminalInputState {
        var reads: Int = 0
            private set

        override fun getInputModeBits(): Long {
            reads++
            return bits
        }
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
