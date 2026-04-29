package com.gagik.terminal.testkit

import com.gagik.terminal.transport.TerminalConnector
import com.gagik.terminal.transport.TerminalConnectorListener

/**
 * In-memory connector for session and transport-adapter tests.
 *
 * Local [close] only records that the connector was closed. Remote lifecycle
 * events are triggered explicitly through [simulateClosed] or [simulateCrash].
 */
class MockConnector : TerminalConnector {
    private val capturedWrites = ArrayList<Byte>()
    private var listener: TerminalConnectorListener? = null

    /** Number of times [start] was called. */
    var startCount: Int = 0
        private set

    /** Number of times [close] was called locally. */
    var closeCount: Int = 0
        private set

    /** Resize calls in the order they were received. */
    val resizeCalls: MutableList<Pair<Int, Int>> = mutableListOf()

    /** Captured outbound bytes written by the session. */
    val writtenBytes: ByteArray
        get() = ByteArray(capturedWrites.size) { index -> capturedWrites[index] }

    override fun start(listener: TerminalConnectorListener) {
        check(this.listener == null) { "connector already started" }
        this.listener = listener
        startCount++
    }

    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        require(offset >= 0) { "offset must be non-negative, got $offset" }
        require(length >= 0) { "length must be non-negative, got $length" }
        require(offset <= bytes.size) { "offset $offset exceeds size ${bytes.size}" }
        require(length <= bytes.size - offset) {
            "offset + length exceeds size: offset=$offset length=$length size=${bytes.size}"
        }

        var index = 0
        while (index < length) {
            capturedWrites += bytes[offset + index]
            index++
        }
    }

    override fun resize(columns: Int, rows: Int) {
        resizeCalls += columns to rows
    }

    override fun close() {
        closeCount++
    }

    /**
     * Delivers host output to the session.
     */
    fun feedFromHost(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size) {
        listenerOrThrow().onBytes(bytes, offset, length)
    }

    /**
     * Simulates remote transport closure.
     */
    fun simulateClosed(exitCode: Int? = null) {
        listenerOrThrow().onClosed(exitCode)
    }

    /**
     * Simulates remote transport failure.
     */
    fun simulateCrash(error: Throwable) {
        listenerOrThrow().onError(error)
    }

    private fun listenerOrThrow(): TerminalConnectorListener {
        return checkNotNull(listener) { "connector has not been started" }
    }
}
