package com.gagik.terminal

import com.gagik.terminal.buffer.TerminalBuffer
import com.gagik.terminal.buffer.TerminalBufferApi

/**
 * Factory for creating terminal buffer instances.
 */
object TerminalBuffers {
    fun create(width: Int, height: Int, maxHistory: Int = 1000): TerminalBufferApi {
        return TerminalBuffer(width, height, maxHistory)
    }
}