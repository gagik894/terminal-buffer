package com.gagik.terminal.pty

import com.gagik.core.TerminalBuffers
import com.gagik.integration.CoreTerminalCommandSink
import com.gagik.parser.api.TerminalParsers
import com.gagik.terminal.input.impl.DefaultTerminalInputEncoder
import java.io.IOException

/**
 * Factory for local PTY-backed terminal sessions.
 */
object TerminalPtySessions {
    /**
     * Starts a PTY process and connects it to parser, core, integration, and
     * input encoding components.
     *
     * PTY stdout is consumed on a daemon reader thread and fed to the parser.
     * Parser/core response bytes and UI input events are serialized onto PTY
     * stdin by the returned [TerminalPtySession].
     *
     * @param options PTY process and terminal dimensions.
     * @return running terminal session.
     * @throws IOException when PTY4J cannot start the process.
     */
    @JvmStatic
    @Throws(IOException::class)
    fun start(options: TerminalPtyOptions = TerminalPtyOptions()): TerminalPtySession {
        return start(options, Pty4jTerminalProcessFactory)
    }

    internal fun start(
        options: TerminalPtyOptions,
        processFactory: TerminalProcessFactory,
    ): TerminalPtySession {
        val process = processFactory.start(options)
        val terminal = TerminalBuffers.create(
            width = options.columns,
            height = options.rows,
            maxHistory = options.maxHistory,
        )
        val hostOutput = StreamTerminalHostOutput(process.output)
        val sink = CoreTerminalCommandSink(terminal)
        val parser = TerminalParsers.create(sink)
        val inputEncoder = DefaultTerminalInputEncoder(terminal, hostOutput)

        val session = TerminalPtySession(
            terminal = terminal,
            process = process,
            parser = parser,
            inputEncoder = inputEncoder,
            hostOutput = hostOutput,
            readBufferSize = options.readBufferSize,
            readerThreadName = options.readerThreadName,
        )
        session.startReader()
        return session
    }
}
