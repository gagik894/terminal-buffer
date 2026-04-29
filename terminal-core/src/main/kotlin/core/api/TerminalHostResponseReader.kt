package com.gagik.core.api

/**
 * Narrow reader for terminal-to-host response bytes.
 *
 * Sessions drain this interface after parser/core mutations and forward the
 * bytes to the active connector. Implementations must synchronously copy bytes
 * into [dst] before returning.
 */
interface TerminalHostResponseReader {
    /**
     * Reads up to [length] queued response bytes into [dst].
     *
     * @return number of bytes copied, or `0` when no response is pending.
     */
    fun readResponseBytes(
        dst: ByteArray,
        offset: Int = 0,
        length: Int = dst.size - offset,
    ): Int
}
