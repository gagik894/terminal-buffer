package com.gagik.terminal.pty

import com.gagik.core.api.TerminalBufferApi
import com.gagik.parser.api.TerminalOutputParser
import com.gagik.terminal.input.api.TerminalInputEncoder
import com.gagik.terminal.input.event.TerminalFocusEvent
import com.gagik.terminal.input.event.TerminalKeyEvent
import com.gagik.terminal.input.event.TerminalMouseEvent
import com.gagik.terminal.input.event.TerminalPasteEvent
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Running PTY-backed terminal pipeline.
 *
 * This session is the serialization point for terminal-to-host bytes. Callers
 * should send UI input through this object instead of directly sharing the
 * underlying default encoder across threads.
 *
 * @property terminal public terminal buffer populated from PTY output.
 */
class TerminalPtySession internal constructor(
    val terminal: TerminalBufferApi,
    private val process: TerminalProcess,
    private val parser: TerminalOutputParser,
    private val inputEncoder: TerminalInputEncoder,
    private val hostOutput: StreamTerminalHostOutput,
    private val readBufferSize: Int,
    private val readerThreadName: String,
) : TerminalInputEncoder, AutoCloseable {
    private val hostWriteLock = Any()
    private val closed = AtomicBoolean(false)
    @Volatile
    private var readerFailure: IOException? = null
    private lateinit var readerThread: Thread

    /**
     * Returns true while the underlying process reports that it is alive.
     */
    val isAlive: Boolean
        get() = process.isAlive()

    internal fun startReader() {
        readerThread = Thread(this::readProcessOutput, readerThreadName)
        readerThread.isDaemon = true
        readerThread.start()
    }

    /**
     * Resizes both the PTY process and the public terminal buffer.
     *
     * @param columns new terminal width in cells.
     * @param rows new terminal height in rows.
     */
    fun resize(columns: Int, rows: Int) {
        require(columns > 0) { "PTY columns must be positive, got $columns" }
        require(rows > 0) { "PTY rows must be positive, got $rows" }
        process.resize(columns, rows)
        terminal.resize(columns, rows)
    }

    /**
     * Waits for the child process to exit.
     *
     * @return child process exit code.
     */
    @Throws(InterruptedException::class)
    fun waitFor(): Int = process.waitFor()

    internal fun joinReader(timeoutMillis: Long) {
        if (::readerThread.isInitialized) {
            readerThread.join(timeoutMillis)
        }
    }

    /**
     * Encodes one key event and writes it to PTY stdin.
     */
    override fun encodeKey(event: TerminalKeyEvent) {
        synchronized(hostWriteLock) {
            inputEncoder.encodeKey(event)
        }
    }

    /**
     * Encodes one paste event and writes it to PTY stdin.
     */
    override fun encodePaste(event: TerminalPasteEvent) {
        synchronized(hostWriteLock) {
            inputEncoder.encodePaste(event)
        }
    }

    /**
     * Encodes one focus event and writes it to PTY stdin.
     */
    override fun encodeFocus(event: TerminalFocusEvent) {
        synchronized(hostWriteLock) {
            inputEncoder.encodeFocus(event)
        }
    }

    /**
     * Encodes one mouse event and writes it to PTY stdin.
     */
    override fun encodeMouse(event: TerminalMouseEvent) {
        synchronized(hostWriteLock) {
            inputEncoder.encodeMouse(event)
        }
    }

    /**
     * Stops the PTY process and closes its host-bound input stream.
     */
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        try {
            hostOutput.close()
        } catch (_: IOException) {
            // Closing an already-terminated PTY may race with the reader thread.
        }
        process.destroy()
        if (::readerThread.isInitialized && Thread.currentThread() !== readerThread) {
            readerThread.join(CLOSE_JOIN_MILLIS)
        }
    }

    private fun readProcessOutput() {
        val buffer = ByteArray(readBufferSize)
        try {
            while (!closed.get()) {
                val read = process.input.read(buffer)
                if (read < 0) break
                if (read == 0) continue
                parser.accept(buffer, 0, read)
                drainCoreResponses()
            }
        } catch (exception: IOException) {
            if (!closed.get()) readerFailure = exception
        } finally {
            parser.endOfInput()
        }
    }

    private fun drainCoreResponses() {
        synchronized(hostWriteLock) {
            val buffer = ByteArray(RESPONSE_BUFFER_SIZE)
            while (terminal.pendingResponseBytes > 0) {
                val read = terminal.readResponseBytes(buffer, 0, buffer.size)
                if (read <= 0) return
                hostOutput.writeBytes(buffer, 0, read)
            }
        }
    }

    private companion object {
        const val CLOSE_JOIN_MILLIS: Long = 250
        const val RESPONSE_BUFFER_SIZE: Int = 256
    }
}
