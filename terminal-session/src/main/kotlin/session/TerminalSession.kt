package com.gagik.terminal.session

import com.gagik.core.api.TerminalBufferApi
import com.gagik.core.api.TerminalHostResponseReader
import com.gagik.integration.CoreTerminalCommandSink
import com.gagik.integration.TerminalHostEventSink
import com.gagik.integration.TerminalHostPolicy
import com.gagik.parser.api.TerminalOutputParser
import com.gagik.parser.api.TerminalParsers
import com.gagik.terminal.input.api.TerminalInputEncoder
import com.gagik.terminal.input.event.TerminalFocusEvent
import com.gagik.terminal.input.event.TerminalKeyEvent
import com.gagik.terminal.input.event.TerminalMouseEvent
import com.gagik.terminal.input.event.TerminalPasteEvent
import com.gagik.terminal.input.impl.DefaultTerminalInputEncoder
import com.gagik.terminal.input.policy.TerminalInputPolicy
import com.gagik.terminal.transport.TerminalConnector
import com.gagik.terminal.transport.TerminalConnectorListener
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Runtime terminal session that binds core, parser, input encoding, and a
 * transport connector.
 *
 * The connector owns transport threads. This session owns parser/core mutation
 * serialization and all host-bound write ordering.
 *
 * @property terminal public terminal buffer mutated by host output.
 */
class TerminalSession(
    val terminal: TerminalBufferApi,
    private val responseReader: TerminalHostResponseReader,
    private val connector: TerminalConnector,
    private val parser: TerminalOutputParser,
    private val inputEncoder: TerminalInputEncoder,
    private val outboundWriteLock: Any = Any(),
) : TerminalConnectorListener, TerminalInputEncoder, AutoCloseable {
    private val localCloseRequested = AtomicBoolean(false)
    private val remoteClosed = AtomicBoolean(false)
    private val parserClosed = AtomicBoolean(false)

    private val mutationLock = Any()
    private val responseScratch = ByteArray(RESPONSE_BUFFER_SIZE)

    /**
     * Remote process exit code after [onClosed] receives one.
     */
    @Volatile
    var exitCode: Int? = null
        private set

    /**
     * Starts the connector after resizing core and transport to [columns] x
     * [rows].
     */
    fun start(columns: Int, rows: Int) {
        synchronized(mutationLock) {
            terminal.resize(columns, rows)
        }
        connector.resize(columns, rows)
        connector.start(this)
    }

    /**
     * Resizes both core and the active connector.
     */
    fun resize(columns: Int, rows: Int) {
        synchronized(mutationLock) {
            terminal.resize(columns, rows)
        }
        connector.resize(columns, rows)
    }

    /**
     * Encodes a key event and writes it to the connector unless closed.
     */
    override fun encodeKey(event: TerminalKeyEvent) {
        synchronized(outboundWriteLock) {
            if (!isClosed()) {
                inputEncoder.encodeKey(event)
            }
        }
    }

    /**
     * Encodes a paste event and writes it to the connector unless closed.
     */
    override fun encodePaste(event: TerminalPasteEvent) {
        synchronized(outboundWriteLock) {
            if (!isClosed()) {
                inputEncoder.encodePaste(event)
            }
        }
    }

    /**
     * Encodes a focus event and writes it to the connector unless closed.
     */
    override fun encodeFocus(event: TerminalFocusEvent) {
        synchronized(outboundWriteLock) {
            if (!isClosed()) {
                inputEncoder.encodeFocus(event)
            }
        }
    }

    /**
     * Encodes a mouse event and writes it to the connector unless closed.
     */
    override fun encodeMouse(event: TerminalMouseEvent) {
        synchronized(outboundWriteLock) {
            if (!isClosed()) {
                inputEncoder.encodeMouse(event)
            }
        }
    }

    /**
     * Consumes host bytes synchronously, mutating parser/core before returning.
     */
    override fun onBytes(bytes: ByteArray, offset: Int, length: Int) {
        if (isClosed()) return

        synchronized(mutationLock) {
            parser.accept(bytes, offset, length)
        }

        drainResponses()
    }

    /**
     * Records remote closure and closes the parser exactly once.
     */
    override fun onClosed(exitCode: Int?) {
        if (!remoteClosed.compareAndSet(false, true)) return
        this.exitCode = exitCode
        cleanupParser()
    }

    /**
     * Treats transport errors as remote closure without a process exit code.
     */
    override fun onError(error: Throwable) {
        onClosed(null)
    }

    /**
     * Requests local connector shutdown and closes parser input exactly once.
     */
    override fun close() {
        if (!localCloseRequested.compareAndSet(false, true)) return
        if (!remoteClosed.get()) {
            connector.close()
        }
        cleanupParser()
    }

    private fun drainResponses() {
        while (!isClosed()) {
            val count = synchronized(mutationLock) {
                responseReader.readResponseBytes(responseScratch, 0, responseScratch.size)
            }

            if (count <= 0) return

            synchronized(outboundWriteLock) {
                if (!isClosed()) {
                    connector.write(responseScratch, 0, count)
                }
            }
        }
    }

    private fun cleanupParser() {
        if (!parserClosed.compareAndSet(false, true)) return
        synchronized(mutationLock) {
            parser.endOfInput()
        }
    }

    private fun isClosed(): Boolean {
        return localCloseRequested.get() || remoteClosed.get()
    }

    companion object {
        private const val RESPONSE_BUFFER_SIZE: Int = 1024

        /**
         * Creates a production session with the standard parser, integration
         * sink, and default input encoder.
         */
        @JvmStatic
        fun create(
            terminal: TerminalBufferApi,
            connector: TerminalConnector,
            hostEvents: TerminalHostEventSink = TerminalHostEventSink.NONE,
            hostPolicy: TerminalHostPolicy = TerminalHostPolicy(),
            inputPolicy: TerminalInputPolicy = TerminalInputPolicy(),
        ): TerminalSession {
            val outboundWriteLock = Any()
            val hostOutput = ConnectorTerminalHostOutput(connector, outboundWriteLock)
            val sink = CoreTerminalCommandSink(terminal, hostEvents, hostPolicy)
            val parser = TerminalParsers.create(sink)
            val inputEncoder = DefaultTerminalInputEncoder(terminal, hostOutput, inputPolicy)

            return TerminalSession(
                terminal = terminal,
                responseReader = terminal,
                connector = connector,
                parser = parser,
                inputEncoder = inputEncoder,
                outboundWriteLock = outboundWriteLock,
            )
        }
    }
}
