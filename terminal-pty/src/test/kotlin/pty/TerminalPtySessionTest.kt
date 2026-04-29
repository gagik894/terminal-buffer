package com.gagik.terminal.pty

import com.gagik.terminal.input.event.TerminalKeyEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets

class TerminalPtySessionTest {
    @Test
    fun `pty stdout is parsed into terminal core`() {
        val process = FakeTerminalProcess(inputBytes = "hello".toByteArray(StandardCharsets.UTF_8))
        val session = TerminalPtySessions.start(
            options = TerminalPtyOptions(
                command = listOf("fake"),
                columns = 10,
                rows = 3,
                readerThreadName = "terminal-pty-test-reader",
            ),
            processFactory = FixedProcessFactory(process),
        )

        session.waitForReader()

        assertEquals("hello", session.terminal.getLineAsString(0))
    }

    @Test
    fun `parser core responses are written back to pty stdin`() {
        val bytes = "\u001B[6n".toByteArray(StandardCharsets.US_ASCII)
        val process = FakeTerminalProcess(inputBytes = bytes)
        val session = TerminalPtySessions.start(
            options = TerminalPtyOptions(command = listOf("fake"), columns = 10, rows = 3),
            processFactory = FixedProcessFactory(process),
        )

        session.waitForReader()

        assertEquals("\u001B[1;1R", process.outputText())
    }

    @Test
    fun `input events are encoded to pty stdin through session serialization point`() {
        val process = FakeTerminalProcess(inputBytes = ByteArray(0))
        val session = TerminalPtySessions.start(
            options = TerminalPtyOptions(command = listOf("fake"), columns = 10, rows = 3),
            processFactory = FixedProcessFactory(process),
        )

        session.encodeKey(TerminalKeyEvent.codepoint('a'.code))

        assertEquals("a", process.outputText())
    }

    @Test
    fun `resize updates process and terminal dimensions`() {
        val process = FakeTerminalProcess(inputBytes = ByteArray(0))
        val session = TerminalPtySessions.start(
            options = TerminalPtyOptions(command = listOf("fake"), columns = 10, rows = 3),
            processFactory = FixedProcessFactory(process),
        )

        session.resize(columns = 20, rows = 5)

        assertEquals(20, session.terminal.width)
        assertEquals(5, session.terminal.height)
        assertEquals(20 to 5, process.lastSize)
    }

    @Test
    fun `close destroys process`() {
        val process = FakeTerminalProcess(inputBytes = ByteArray(0))
        val session = TerminalPtySessions.start(
            options = TerminalPtyOptions(command = listOf("fake"), columns = 10, rows = 3),
            processFactory = FixedProcessFactory(process),
        )

        session.close()

        assertTrue(process.destroyed)
    }

    private fun TerminalPtySession.waitForReader() {
        joinReader(1000)
    }

    private class FixedProcessFactory(
        private val process: FakeTerminalProcess,
    ) : TerminalProcessFactory {
        override fun start(options: TerminalPtyOptions): TerminalProcess = process
    }

    private class FakeTerminalProcess(
        inputBytes: ByteArray,
    ) : TerminalProcess {
        override val input: InputStream = ByteArrayInputStream(inputBytes)
        private val capturedOutput = ByteArrayOutputStream()
        override val output: OutputStream = capturedOutput
        var destroyed: Boolean = false
            private set
        var lastSize: Pair<Int, Int>? = null
            private set

        override fun isAlive(): Boolean = !destroyed

        override fun waitFor(): Int = 0

        override fun destroy() {
            destroyed = true
        }

        override fun resize(columns: Int, rows: Int) {
            lastSize = columns to rows
        }

        fun outputText(): String = capturedOutput.toString(StandardCharsets.UTF_8)
    }
}
