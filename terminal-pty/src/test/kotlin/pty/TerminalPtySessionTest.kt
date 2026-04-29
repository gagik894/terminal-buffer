package com.gagik.terminal.pty

import com.gagik.terminal.input.event.TerminalKeyEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.*
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

    @Test
    fun `bell and title changes are delivered to listener from parsed host output`() {
        val listener = RecordingPtyEventListener()
        val input = "\u0007\u001B]0;both\u001B\\".toByteArray(StandardCharsets.US_ASCII)
        val process = FakeTerminalProcess(inputBytes = input)
        val session = TerminalPtySessions.start(
            options = TerminalPtyOptions(
                command = listOf("fake"),
                columns = 10,
                rows = 3,
                eventListener = listener,
            ),
            processFactory = FixedProcessFactory(process),
        )

        session.waitForReader()

        assertEquals(1, listener.bells)
        assertEquals(listOf("both"), listener.iconTitles)
        assertEquals(listOf("both"), listener.windowTitles)
    }

    @Test
    fun `reader failure is captured and delivered to listener`() {
        val listener = RecordingPtyEventListener()
        val failure = IOException("read failed")
        val process = FakeTerminalProcess(inputStream = FailingInputStream(failure))
        val session = TerminalPtySessions.start(
            options = TerminalPtyOptions(
                command = listOf("fake"),
                columns = 10,
                rows = 3,
                eventListener = listener,
            ),
            processFactory = FixedProcessFactory(process),
        )

        session.waitForReader()

        assertEquals(failure, session.failure)
        assertEquals(listOf(failure), listener.readerFailures)
    }

    @Test
    fun `process exit is captured and delivered to listener`() {
        val listener = RecordingPtyEventListener()
        val process = FakeTerminalProcess(inputBytes = ByteArray(0), exitCode = 7)
        val session = TerminalPtySessions.start(
            options = TerminalPtyOptions(
                command = listOf("fake"),
                columns = 10,
                rows = 3,
                eventListener = listener,
            ),
            processFactory = FixedProcessFactory(process),
        )

        session.waitForWatcher()

        assertEquals(7, session.exitCode)
        assertEquals(listOf(7), listener.exitCodes)
    }

    private fun TerminalPtySession.waitForReader() {
        assertTrue(joinReader(1000), "reader thread did not stop")
    }

    private fun TerminalPtySession.waitForWatcher() {
        assertTrue(joinWatcher(1000), "watcher thread did not stop")
    }

    private class FixedProcessFactory(
        private val process: FakeTerminalProcess,
    ) : TerminalProcessFactory {
        override fun start(options: TerminalPtyOptions): TerminalProcess = process
    }

    private class FakeTerminalProcess private constructor(
        override val input: InputStream,
        private val exitCode: Int,
        @Suppress("UNUSED_PARAMETER")
        marker: Unit,
    ) : TerminalProcess {
        constructor(
            inputBytes: ByteArray,
            exitCode: Int = 0,
        ) : this(ByteArrayInputStream(inputBytes), exitCode, Unit)

        constructor(
            inputStream: InputStream,
            exitCode: Int = 0,
        ) : this(inputStream, exitCode, Unit)

        private val capturedOutput = ByteArrayOutputStream()
        override val output: OutputStream = capturedOutput
        var destroyed: Boolean = false
            private set
        var lastSize: Pair<Int, Int>? = null
            private set

        override fun isAlive(): Boolean = !destroyed

        override fun waitFor(): Int = exitCode

        override fun destroy() {
            destroyed = true
        }

        override fun resize(columns: Int, rows: Int) {
            lastSize = columns to rows
        }

        fun outputText(): String = capturedOutput.toString(StandardCharsets.UTF_8)
    }

    private class FailingInputStream(
        private val failure: IOException,
    ) : InputStream() {
        override fun read(): Int {
            throw failure
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            throw failure
        }
    }

    private class RecordingPtyEventListener : TerminalPtyEventListener {
        var bells: Int = 0
        val iconTitles = mutableListOf<String>()
        val windowTitles = mutableListOf<String>()
        val readerFailures = mutableListOf<IOException>()
        val exitCodes = mutableListOf<Int>()

        override fun bell(session: TerminalPtySession) {
            bells++
        }

        override fun iconTitleChanged(session: TerminalPtySession, title: String) {
            iconTitles += title
        }

        override fun windowTitleChanged(session: TerminalPtySession, title: String) {
            windowTitles += title
        }

        override fun readerFailed(session: TerminalPtySession, exception: IOException) {
            readerFailures += exception
        }

        override fun processExited(session: TerminalPtySession, exitCode: Int) {
            exitCodes += exitCode
        }

        override fun listenerFailed(session: TerminalPtySession, exception: Exception) = Unit
    }
}
