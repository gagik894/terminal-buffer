package com.gagik.terminal.pty

import com.pty4j.PtyProcess
import com.pty4j.PtyProcessBuilder
import com.pty4j.WinSize
import java.io.InputStream
import java.io.OutputStream

internal interface TerminalProcess {
    val input: InputStream
    val output: OutputStream

    fun isAlive(): Boolean

    fun waitFor(): Int

    fun destroy()

    fun resize(columns: Int, rows: Int)
}

internal interface TerminalProcessFactory {
    fun start(options: TerminalPtyOptions): TerminalProcess
}

internal object Pty4jTerminalProcessFactory : TerminalProcessFactory {
    override fun start(options: TerminalPtyOptions): TerminalProcess {
        val builder = PtyProcessBuilder()
            .setCommand(options.command.toTypedArray())
            .setEnvironment(options.environment)
            .setInitialColumns(options.columns)
            .setInitialRows(options.rows)

        options.workingDirectory?.let { directory ->
            builder.setDirectory(directory.toString())
        }

        return Pty4jTerminalProcess(builder.start())
    }
}

internal class Pty4jTerminalProcess(
    private val process: PtyProcess,
) : TerminalProcess {
    override val input: InputStream
        get() = process.inputStream

    override val output: OutputStream
        get() = process.outputStream

    override fun isAlive(): Boolean = process.isAlive

    override fun waitFor(): Int = process.waitFor()

    override fun destroy() {
        process.destroy()
    }

    override fun resize(columns: Int, rows: Int) {
        process.setWinSize(WinSize(columns, rows))
    }
}
