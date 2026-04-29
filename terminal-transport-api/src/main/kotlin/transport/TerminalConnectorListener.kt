package com.gagik.terminal.transport

/**
 * Callback sink for terminal transport events.
 */
interface TerminalConnectorListener {
    /**
     * Delivers bytes emitted by the remote host.
     *
     * The listener must consume the byte range synchronously before returning.
     * Connectors may reuse [bytes] after this callback returns.
     */
    fun onBytes(bytes: ByteArray, offset: Int, length: Int)

    /**
     * Reports remote transport closure.
     *
     * @param exitCode process exit code when the transport has one, otherwise
     * `null`.
     */
    fun onClosed(exitCode: Int?)

    /**
     * Reports a remote transport failure.
     */
    fun onError(error: Throwable)
}
