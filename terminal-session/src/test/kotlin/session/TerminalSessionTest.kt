package com.gagik.terminal.session

import com.gagik.core.TerminalBuffers
import com.gagik.parser.api.TerminalOutputParser
import com.gagik.terminal.input.api.TerminalInputEncoder
import com.gagik.terminal.input.event.TerminalFocusEvent
import com.gagik.terminal.input.event.TerminalKeyEvent
import com.gagik.terminal.input.event.TerminalMouseEvent
import com.gagik.terminal.input.event.TerminalPasteEvent
import com.gagik.terminal.testkit.MockConnector
import com.gagik.terminal.transport.TerminalConnector
import com.gagik.terminal.transport.TerminalConnectorListener
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class TerminalSessionTest {
    @Test
    fun `DSR CSI 5 n replies OK status`() {
        val connector = MockConnector()
        val session = createStartedSession(connector)

        connector.feedFromHost("\u001B[5n".ascii())

        assertEquals("\u001B[0n", connector.writtenBytes.asciiText())
        session.close()
    }

    @Test
    fun `CPR CSI 6 n replies one-based cursor position`() {
        val connector = MockConnector()
        val session = createStartedSession(connector)

        connector.feedFromHost("\u001B[2;3H\u001B[6n".ascii())

        assertEquals("\u001B[2;3R", connector.writtenBytes.asciiText())
        session.close()
    }

    @Test
    fun `close does not set exitCode to zero`() {
        val connector = MockConnector()
        val session = createStartedSession(connector)

        session.close()

        assertNull(session.exitCode)
        assertEquals(1, connector.closeCount)
    }

    @Test
    fun `remote close records exit code`() {
        val connector = MockConnector()
        val session = createStartedSession(connector)

        connector.simulateClosed(7)

        assertEquals(7, session.exitCode)
        assertEquals(0, connector.closeCount)
    }

    @Test
    fun `onClosed does not recursively close connector`() {
        val connector = MockConnector()
        createStartedSession(connector)

        connector.simulateClosed(7)

        assertEquals(0, connector.closeCount)
    }

    @Test
    fun `input key writes through connector`() {
        val connector = MockConnector()
        val session = createStartedSession(connector)

        session.encodeKey(TerminalKeyEvent.codepoint('a'.code))

        assertEquals("a", connector.writtenBytes.asciiText())
        session.close()
    }

    @Test
    fun `response write and key write do not interleave`() {
        lateinit var session: TerminalSession
        val connector = SlowFirstWriteConnector {
            Thread {
                session.encodeKey(TerminalKeyEvent.codepoint('a'.code))
            }.apply {
                name = "terminal-session-ordering-test"
                start()
            }
        }
        session = createStartedSession(connector)

        connector.feedFromHost("\u001B[5n".ascii())

        assertTrue(connector.awaitTriggeredWriter(), "key writer was not started")
        assertTrue(connector.awaitWrites(2), "key write did not complete")
        assertEquals("\u001B[0na", connector.writtenBytes.asciiText())
        session.close()
    }

    @Test
    fun `resize mutates core and calls connector resize`() {
        val connector = MockConnector()
        val session = createStartedSession(connector, columns = 10, rows = 3)

        session.resize(columns = 20, rows = 5)

        assertEquals(20, session.terminal.width)
        assertEquals(5, session.terminal.height)
        assertEquals(listOf(10 to 3, 20 to 5), connector.resizeCalls)
        session.close()
    }

    @Test
    fun `bytes are consumed synchronously before callback returns`() {
        val connector = MockConnector()
        val session = createStartedSession(connector)
        val bytes = "hello\u001B[5n".ascii()

        connector.feedFromHost(bytes)
        bytes.fill('?'.code.toByte())

        assertEquals("hello", session.terminal.getLineAsString(0))
        assertEquals("\u001B[0n", connector.writtenBytes.asciiText())
        session.close()
    }

    @Test
    fun `parser endOfInput is called once`() {
        val terminal = TerminalBuffers.create(width = 10, height = 3)
        val connector = MockConnector()
        val parser = RecordingParser()
        val session = TerminalSession(
            terminal = terminal,
            responseReader = terminal,
            connector = connector,
            parser = parser,
            inputEncoder = NoOpInputEncoder,
        )

        session.start(columns = 10, rows = 3)
        session.close()
        connector.simulateClosed(0)
        session.close()

        assertEquals(1, parser.endOfInputCalls)
    }

    private fun createStartedSession(
        connector: TerminalConnector,
        columns: Int = 10,
        rows: Int = 3,
    ): TerminalSession {
        val terminal = TerminalBuffers.create(width = columns, height = rows)
        val session = TerminalSession.create(terminal, connector)
        session.start(columns, rows)
        return session
    }

    private fun String.ascii(): ByteArray = toByteArray(StandardCharsets.US_ASCII)

    private fun ByteArray.asciiText(): String = toString(StandardCharsets.US_ASCII)

    private class RecordingParser : TerminalOutputParser {
        var endOfInputCalls: Int = 0
            private set

        override fun accept(bytes: ByteArray, offset: Int, length: Int) = Unit

        override fun acceptByte(byteValue: Int) = Unit

        override fun endOfInput() {
            endOfInputCalls++
        }

        override fun reset() = Unit
    }

    private object NoOpInputEncoder : TerminalInputEncoder {
        override fun encodeKey(event: TerminalKeyEvent) = Unit

        override fun encodePaste(event: TerminalPasteEvent) = Unit

        override fun encodeFocus(event: TerminalFocusEvent) = Unit

        override fun encodeMouse(event: TerminalMouseEvent) = Unit
    }

    private class SlowFirstWriteConnector(
        private val onFirstWriteStarted: () -> Thread,
    ) : TerminalConnector {
        private val writesDone = CountDownLatch(2)
        private val triggeredWriter = CountDownLatch(1)
        private val bytes = ArrayList<Byte>()
        private var listener: TerminalConnectorListener? = null
        private var writes: Int = 0
        private var writerThread: Thread? = null

        val writtenBytes: ByteArray
            get() = synchronized(bytes) {
                ByteArray(bytes.size) { index -> bytes[index] }
            }

        override fun start(listener: TerminalConnectorListener) {
            this.listener = listener
        }

        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            val currentWrite = synchronized(this) {
                writes++
                writes
            }

            if (currentWrite == 1) {
                writerThread = onFirstWriteStarted()
                triggeredWriter.countDown()
                Thread.sleep(50)
            }

            synchronized(this.bytes) {
                var index = 0
                while (index < length) {
                    this.bytes += bytes[offset + index]
                    index++
                }
            }
            writesDone.countDown()
        }

        override fun resize(columns: Int, rows: Int) = Unit

        override fun close() {
            writerThread?.join(1000)
        }

        fun feedFromHost(bytes: ByteArray) {
            listener?.onBytes(bytes, 0, bytes.size)
        }

        fun awaitTriggeredWriter(): Boolean {
            return triggeredWriter.await(1, TimeUnit.SECONDS)
        }

        fun awaitWrites(count: Int): Boolean {
            require(count == 2) { "this fixture only waits for the two expected writes" }
            val completed = writesDone.await(1, TimeUnit.SECONDS)
            writerThread?.join(1000)
            return completed
        }
    }
}
