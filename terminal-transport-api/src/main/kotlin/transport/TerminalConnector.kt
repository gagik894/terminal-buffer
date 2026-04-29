package com.gagik.terminal.transport

/**
 * Transport-neutral terminal byte connector.
 *
 * A connector owns any transport-specific reader, watcher, or writer threads.
 * The terminal session that uses the connector owns parser/core synchronization
 * and host-bound byte ordering.
 */
interface TerminalConnector : AutoCloseable {
    /**
     * Starts delivering transport events to [listener].
     *
     * Implementations may call listener methods from transport-owned threads.
     */
    fun start(listener: TerminalConnectorListener)

    /**
     * Writes a contiguous byte range to the remote host input stream.
     *
     * The range must be synchronously consumed or copied before this method
     * returns because callers may immediately reuse [bytes].
     */
    fun write(
        bytes: ByteArray,
        offset: Int = 0,
        length: Int = bytes.size,
    )

    /**
     * Resizes the remote terminal transport in character cells.
     */
    fun resize(columns: Int, rows: Int)

    /**
     * Requests local transport shutdown.
     */
    override fun close()
}
