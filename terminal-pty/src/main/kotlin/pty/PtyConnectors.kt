package com.gagik.terminal.pty

import com.gagik.terminal.transport.TerminalConnector
import java.io.IOException
import java.nio.file.Path

/**
 * Factory for local PTY-backed terminal connectors.
 */
object PtyConnectors {
    /**
     * Creates a connector for a new local PTY process.
     *
     * Callers should depend on [TerminalConnector] instead of PTY4J process
     * classes.
     */
    @JvmStatic
    @Throws(IOException::class)
    fun create(
        command: List<String>,
        env: Map<String, String> = System.getenv(),
        workingDirectory: Path? = null,
        columns: Int = 80,
        rows: Int = 24,
    ): TerminalConnector {
        val options = TerminalPtyOptions(
            command = command,
            environment = env,
            workingDirectory = workingDirectory,
            columns = columns,
            rows = rows,
        )
        return create(options, Pty4jTerminalProcessFactory)
    }

    internal fun create(
        options: TerminalPtyOptions,
        processFactory: TerminalProcessFactory,
    ): PtyConnector {
        val process = processFactory.start(options)
        return PtyConnector(
            process = process,
            readBufferSize = options.readBufferSize,
            readerThreadName = options.readerThreadName,
            watcherThreadName = options.watcherThreadName,
        )
    }
}
