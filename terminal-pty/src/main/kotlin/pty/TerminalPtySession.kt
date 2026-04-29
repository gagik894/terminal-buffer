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
 * PTY runtime session.
 *
 * Threading model:
 * - PTY output parsing and core mutation are serialized by terminalMutationLock.
 * - Host-bound writes are serialized by hostWriteLock.
 * - Blocking host I/O is never performed while terminalMutationLock is held.
 * - hostWriteLock is never held while acquiring terminalMutationLock.
 * - Input methods acquire only hostWriteLock and never mutate terminal core state.
 * - Host listener callbacks are queued during parsing and dispatched after
 *   terminalMutationLock is released.
 *
 * This class is safe for UI threads to call input methods and resize concurrently
 * with the PTY reader thread.
 *
 * @property terminal public terminal buffer populated from PTY output.
 */
class TerminalPtySession internal constructor(
    val terminal: TerminalBufferApi,
    private val process: TerminalProcess,
    private val parser: TerminalOutputParser,
    private val inputEncoder: TerminalInputEncoder,
    private val hostOutput: StreamTerminalHostOutput,
    private val hostEventBridge: SessionHostEventBridge,
    private val readBufferSize: Int,
    private val readerThreadName: String,
    private val watcherThreadName: String,
    private val eventListener: TerminalPtyEventListener,
) : TerminalInputEncoder, AutoCloseable {
    private val terminalMutationLock = Any()
    private val hostWriteLock = Any()
    private val responseBuffer = ByteArray(RESPONSE_BUFFER_SIZE)
    private val closed = AtomicBoolean(false)

    @Volatile
    private var readerFailure: IOException? = null

    @Volatile
    private var processExitCode: Int? = null

    private lateinit var readerThread: Thread
    private lateinit var watcherThread: Thread

    init {
        require(readBufferSize > 0) { "readBufferSize must be positive, got $readBufferSize" }
    }

    /**
     * Returns true while the underlying process reports that it is alive.
     */
    val isAlive: Boolean
        get() = process.isAlive()

    /**
     * Reader failure captured from the PTY stdout thread, or `null` when no
     * failure has occurred.
     */
    val failure: IOException?
        get() = readerFailure

    /**
     * Child process exit code after the process watcher observes termination.
     */
    val exitCode: Int?
        get() = processExitCode

    internal fun startReader() {
        readerThread = Thread(this::readProcessOutput, readerThreadName).apply {
            isDaemon = true
            start()
        }
    }

    internal fun startWatcher() {
        watcherThread = Thread(this::watchProcessExit, watcherThreadName).apply {
            isDaemon = true
            start()
        }
    }

    /**
     * Resizes both the public terminal buffer and the PTY process.
     *
     * Core is resized first while terminal mutation is locked so resize-reactive
     * child output cannot race ahead of the buffer dimensions.
     *
     * @param columns new terminal width in cells.
     * @param rows new terminal height in rows.
     */
    fun resize(columns: Int, rows: Int) {
        require(columns > 0) { "PTY columns must be positive, got $columns" }
        require(rows > 0) { "PTY rows must be positive, got $rows" }

        synchronized(terminalMutationLock) {
            terminal.resize(columns, rows)
        }

        process.resize(columns, rows)
    }

    /**
     * Waits for the child process to exit.
     *
     * @return child process exit code.
     */
    @Throws(InterruptedException::class)
    fun waitFor(): Int = process.waitFor()

    internal fun joinReader(timeoutMillis: Long): Boolean {
        if (!::readerThread.isInitialized) {
            return true
        }

        return try {
            readerThread.join(timeoutMillis)
            !readerThread.isAlive
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
    }

    internal fun joinWatcher(timeoutMillis: Long): Boolean {
        if (!::watcherThread.isInitialized) {
            return true
        }

        return try {
            watcherThread.join(timeoutMillis)
            !watcherThread.isAlive
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
    }

    /**
     * Encodes one key event and writes it to PTY stdin.
     */
    override fun encodeKey(event: TerminalKeyEvent) {
        synchronized(hostWriteLock) {
            if (!closed.get()) inputEncoder.encodeKey(event)
        }
    }

    /**
     * Encodes one paste event and writes it to PTY stdin.
     */
    override fun encodePaste(event: TerminalPasteEvent) {
        synchronized(hostWriteLock) {
            if (!closed.get()) inputEncoder.encodePaste(event)
        }
    }

    /**
     * Encodes one focus event and writes it to PTY stdin.
     */
    override fun encodeFocus(event: TerminalFocusEvent) {
        synchronized(hostWriteLock) {
            if (!closed.get()) inputEncoder.encodeFocus(event)
        }
    }

    /**
     * Encodes one mouse event and writes it to PTY stdin.
     */
    override fun encodeMouse(event: TerminalMouseEvent) {
        synchronized(hostWriteLock) {
            if (!closed.get()) inputEncoder.encodeMouse(event)
        }
    }

    /**
     * Stops the PTY process and closes its host-bound input stream.
     */
    override fun close() {
        if (!closed.compareAndSet(false, true)) return

        try {
            process.destroy()
        } finally {
            synchronized(hostWriteLock) {
                try {
                    hostOutput.close()
                } catch (_: IOException) {
                    // Ignore close failure.
                }
            }

            joinThreads()
        }
    }

    private fun readProcessOutput() {
        val buffer = ByteArray(readBufferSize)

        try {
            while (!closed.get()) {
                val read = process.input.read(buffer)
                if (read < 0) break
                if (read == 0) continue

                synchronized(terminalMutationLock) {
                    parser.accept(buffer, 0, read)
                }

                drainCoreResponses()
                dispatchPendingHostEvents()
            }
        } catch (exception: IOException) {
            if (!closed.get()) {
                readerFailure = exception
                safeInvokeListener {
                    eventListener.readerFailed(this@TerminalPtySession, exception)
                }
            }
        } finally {
            synchronized(terminalMutationLock) {
                parser.endOfInput()
            }

            drainCoreResponses()
            dispatchPendingHostEvents()
        }
    }

    private fun watchProcessExit() {
        try {
            val code = process.waitFor()
            processExitCode = code

            safeInvokeListener {
                eventListener.processExited(this@TerminalPtySession, code)
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun drainCoreResponses() {
        while (!closed.get()) {
            val read = synchronized(terminalMutationLock) {
                terminal.readResponseBytes(responseBuffer, 0, responseBuffer.size)
            }

            if (read <= 0) return

            synchronized(hostWriteLock) {
                if (closed.get()) return
                try {
                    hostOutput.writeBytes(responseBuffer, 0, read)
                } catch (exception: IOException) {
                    if (!closed.get()) {
                        readerFailure = exception
                        safeInvokeListener {
                            eventListener.readerFailed(this@TerminalPtySession, exception)
                        }
                    }
                    return
                }
            }
        }
    }

    private fun dispatchPendingHostEvents() {
        hostEventBridge.drainTo { event ->
            safeInvokeListener {
                hostEventBridge.dispatch(event)
            }
        }
    }

    private fun joinThreads() {
        if (::readerThread.isInitialized && Thread.currentThread() !== readerThread) {
            try {
                readerThread.join(CLOSE_JOIN_MILLIS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return
            }
        }

        if (::watcherThread.isInitialized && Thread.currentThread() !== watcherThread) {
            try {
                watcherThread.join(CLOSE_JOIN_MILLIS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return
            }
        }
    }

    private inline fun safeInvokeListener(block: () -> Unit) {
        try {
            block()
        } catch (exception: Exception) {
            notifyListenerFailed(exception)
        }
    }

    private fun notifyListenerFailed(exception: Exception) {
        try {
            eventListener.listenerFailed(this@TerminalPtySession, exception)
        } catch (_: Exception) {
            // Ignore secondary listener failure.
        }
    }

    private companion object {
        const val CLOSE_JOIN_MILLIS: Long = 250
        const val RESPONSE_BUFFER_SIZE: Int = 256
    }
}
