package com.gagik.terminal.pty

import com.gagik.core.TerminalBuffers
import com.gagik.core.api.TerminalBufferApi
import com.gagik.parser.api.TerminalOutputParser
import com.gagik.terminal.input.api.TerminalInputEncoder
import com.gagik.terminal.input.event.TerminalFocusEvent
import com.gagik.terminal.input.event.TerminalKeyEvent
import com.gagik.terminal.input.event.TerminalMouseEvent
import com.gagik.terminal.input.event.TerminalPasteEvent
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class TerminalPtySessionHardeningTest {
    @Test
    fun `resize does not run concurrently with parser accept`() {
        val enteredAccept = CountDownLatch(1)
        val releaseAccept = CountDownLatch(1)
        val parser = object : TerminalOutputParser by NoopParser {
            override fun accept(bytes: ByteArray, offset: Int, length: Int) {
                enteredAccept.countDown()
                assertTrue(releaseAccept.await(1, TimeUnit.SECONDS))
            }
        }
        val process = TestProcess(input = ByteArrayInputStream(byteArrayOf(1)))
        val session = testSession(process = process, parser = parser)

        session.startReader()
        assertTrue(enteredAccept.await(1, TimeUnit.SECONDS))

        val resizeReturned = AtomicBoolean(false)
        val resizeThread = Thread {
            session.resize(20, 4)
            resizeReturned.set(true)
        }
        resizeThread.start()

        Thread.sleep(100)
        assertFalse(resizeReturned.get())

        releaseAccept.countDown()
        resizeThread.join(1000)
        session.joinReader(1000)

        assertTrue(resizeReturned.get())
        assertEquals(20, session.terminal.width)
        assertEquals(4, session.terminal.height)
    }

    @Test
    fun `input encodeKey does not wait for terminal mutation lock`() {
        val enteredAccept = CountDownLatch(1)
        val releaseAccept = CountDownLatch(1)
        val parser = object : TerminalOutputParser by NoopParser {
            override fun accept(bytes: ByteArray, offset: Int, length: Int) {
                enteredAccept.countDown()
                assertTrue(releaseAccept.await(1, TimeUnit.SECONDS))
            }
        }
        val output = ByteArrayOutputStream()
        val session = testSession(
            process = TestProcess(input = ByteArrayInputStream(byteArrayOf(1)), output = output),
            parser = parser,
        )

        session.startReader()
        assertTrue(enteredAccept.await(1, TimeUnit.SECONDS))

        session.encodeKey(TerminalKeyEvent.codepoint('a'.code))

        assertEquals("a", output.toString(Charsets.UTF_8))
        releaseAccept.countDown()
        session.joinReader(1000)
    }

    @Test
    fun `endOfInput responses are drained`() {
        val terminal = TerminalBuffers.create(width = 5, height = 2)
        val parser = object : TerminalOutputParser by NoopParser {
            override fun endOfInput() {
                terminal.requestDeviceStatusReport(mode = 5, decPrivate = false)
            }
        }
        val output = ByteArrayOutputStream()
        val session = testSession(
            terminal = terminal,
            process = TestProcess(input = ByteArrayInputStream(ByteArray(0)), output = output),
            parser = parser,
        )

        session.startReader()
        session.joinReader(1000)

        assertEquals("\u001B[0n", output.toString(Charsets.UTF_8))
    }

    @Test
    fun `close prevents later input writes`() {
        val output = ByteArrayOutputStream()
        val session = testSession(process = TestProcess(output = output))

        session.close()
        session.encodeKey(TerminalKeyEvent.codepoint('a'.code))

        assertEquals("", output.toString(Charsets.UTF_8))
    }

    @Test
    fun `close destroys process closes output and is idempotent`() {
        val output = CloseCountingOutputStream()
        val process = TestProcess(output = output)
        val session = testSession(process = process)

        session.close()
        session.close()

        assertTrue(process.destroyed)
        assertEquals(1, output.closeCount.get())
    }

    @Test
    fun `close can be called from reader thread callback`() {
        lateinit var session: TerminalPtySession
        val listener = object : TerminalPtyEventListener by TerminalPtyEventListener.NONE {
            override fun bell(session: TerminalPtySession) {
                session.close()
            }
        }
        val bridge = SessionHostEventBridge(listener)
        val parser = object : TerminalOutputParser by NoopParser {
            override fun accept(bytes: ByteArray, offset: Int, length: Int) {
                bridge.bell()
            }
        }
        val process = TestProcess(input = ByteArrayInputStream(byteArrayOf(1)))
        session = testSession(
            process = process,
            parser = parser,
            bridge = bridge,
            listener = listener,
            attachBridge = false,
        )
        bridge.attach(session)

        session.startReader()
        session.joinReader(1000)

        assertTrue(process.destroyed)
    }

    @Test
    fun `close waits for active host write before closing stream`() {
        val output = BlockingOutputStream()
        val session = testSession(process = TestProcess(output = output))
        val encodeStarted = CountDownLatch(1)

        val encodeThread = Thread {
            encodeStarted.countDown()
            session.encodeKey(TerminalKeyEvent.codepoint('a'.code))
        }
        encodeThread.start()

        assertTrue(encodeStarted.await(1, TimeUnit.SECONDS))
        assertTrue(output.writeEntered.await(1, TimeUnit.SECONDS))

        val closeReturned = AtomicBoolean(false)
        val closeThread = Thread {
            session.close()
            closeReturned.set(true)
        }
        closeThread.start()

        Thread.sleep(100)
        assertFalse(closeReturned.get())

        output.releaseWrite.countDown()
        encodeThread.join(1000)
        closeThread.join(1000)

        assertTrue(closeReturned.get())
        assertEquals("a", output.bytes.toString(Charsets.UTF_8))
        assertEquals(1, output.closeCount.get())
    }

    @Test
    fun `input writes are serialized by hostWriteLock`() {
        val encoder = ConcurrentCheckingInputEncoder()
        val session = testSession(inputEncoder = encoder)
        val first = Thread { session.encodeKey(TerminalKeyEvent.codepoint('a'.code)) }
        val second = Thread { session.encodeKey(TerminalKeyEvent.codepoint('b'.code)) }

        first.start()
        second.start()
        first.join(1000)
        second.join(1000)

        assertEquals(1, encoder.maxConcurrent.get())
        assertEquals(2, encoder.calls.get())
    }

    @Test
    fun `host callbacks are queued until parser accept returns`() {
        val acceptReturned = AtomicBoolean(false)
        lateinit var session: TerminalPtySession
        val listener = object : TerminalPtyEventListener by TerminalPtyEventListener.NONE {
            override fun bell(session: TerminalPtySession) {
                assertTrue(acceptReturned.get())
            }
        }
        val bridge = SessionHostEventBridge(listener)
        val parser = object : TerminalOutputParser by NoopParser {
            override fun accept(bytes: ByteArray, offset: Int, length: Int) {
                bridge.bell()
                acceptReturned.set(true)
            }
        }

        session = testSession(
            process = TestProcess(input = ByteArrayInputStream(byteArrayOf(1))),
            parser = parser,
            bridge = bridge,
            listener = listener,
            attachBridge = false,
        )
        bridge.attach(session)

        session.startReader()
        session.joinReader(1000)

        assertTrue(acceptReturned.get())
    }

    @Test
    fun `listener exceptions are isolated from reader and watcher threads`() {
        val listener = object : TerminalPtyEventListener by TerminalPtyEventListener.NONE {
            override fun bell(session: TerminalPtySession) {
                throw IllegalStateException("bell failed")
            }

            override fun processExited(session: TerminalPtySession, exitCode: Int) {
                throw IllegalStateException("exit failed")
            }
        }
        val process = TestProcess(input = ByteArrayInputStream("\u0007".encodeToByteArray()), exitCode = 9)
        val session = TerminalPtySessions.start(
            TerminalPtyOptions(command = listOf("fake"), eventListener = listener),
            FixedProcessFactory(process),
        )

        session.joinReader(1000)
        session.joinWatcher(1000)

        assertNull(session.failure)
        assertEquals(9, session.exitCode)
    }

    private fun testSession(
        terminal: TerminalBufferApi = TerminalBuffers.create(width = 5, height = 2),
        process: TestProcess = TestProcess(),
        parser: TerminalOutputParser = NoopParser,
        inputEncoder: TerminalInputEncoder? = null,
        bridge: SessionHostEventBridge = SessionHostEventBridge(TerminalPtyEventListener.NONE),
        listener: TerminalPtyEventListener = TerminalPtyEventListener.NONE,
        attachBridge: Boolean = true,
    ): TerminalPtySession {
        val hostOutput = StreamTerminalHostOutput(process.output)
        val session = TerminalPtySession(
            terminal = terminal,
            process = process,
            parser = parser,
            inputEncoder = inputEncoder ?: com.gagik.terminal.input.impl.DefaultTerminalInputEncoder(terminal, hostOutput),
            hostOutput = hostOutput,
            hostEventBridge = bridge,
            readBufferSize = 16,
            readerThreadName = "hardening-reader",
            watcherThreadName = "hardening-watcher",
            eventListener = listener,
        )
        if (attachBridge) {
            bridge.attach(session)
        }
        return session
    }

    private class FixedProcessFactory(
        private val process: TerminalProcess,
    ) : TerminalProcessFactory {
        override fun start(options: TerminalPtyOptions): TerminalProcess = process
    }

    private class TestProcess(
        override val input: InputStream = ByteArrayInputStream(ByteArray(0)),
        override val output: OutputStream = ByteArrayOutputStream(),
        private val exitCode: Int = 0,
    ) : TerminalProcess {
        var destroyed: Boolean = false
            private set

        override fun isAlive(): Boolean = !destroyed
        override fun waitFor(): Int = exitCode
        override fun destroy() {
            destroyed = true
        }
        override fun resize(columns: Int, rows: Int) = Unit
    }

    private class BlockingOutputStream : OutputStream() {
        val bytes = ByteArrayOutputStream()
        val writeEntered = CountDownLatch(1)
        val releaseWrite = CountDownLatch(1)
        val closeCount = AtomicInteger()

        override fun write(byte: Int) {
            writeEntered.countDown()
            assertTrue(releaseWrite.await(1, TimeUnit.SECONDS))
            bytes.write(byte)
        }

        override fun close() {
            closeCount.incrementAndGet()
        }
    }

    private class CloseCountingOutputStream : OutputStream() {
        val closeCount = AtomicInteger()

        override fun write(byte: Int) = Unit

        override fun close() {
            closeCount.incrementAndGet()
        }
    }

    private class ConcurrentCheckingInputEncoder : TerminalInputEncoder {
        val active = AtomicInteger()
        val maxConcurrent = AtomicInteger()
        val calls = AtomicInteger()

        override fun encodeKey(event: TerminalKeyEvent) {
            val now = active.incrementAndGet()
            maxConcurrent.updateAndGet { current -> maxOf(current, now) }
            Thread.sleep(50)
            calls.incrementAndGet()
            active.decrementAndGet()
        }

        override fun encodePaste(event: TerminalPasteEvent) = Unit
        override fun encodeFocus(event: TerminalFocusEvent) = Unit
        override fun encodeMouse(event: TerminalMouseEvent) = Unit
    }

    private object NoopParser : TerminalOutputParser {
        override fun accept(bytes: ByteArray, offset: Int, length: Int) = Unit
        override fun acceptByte(byteValue: Int) = Unit
        override fun endOfInput() = Unit
        override fun reset() = Unit
    }
}
