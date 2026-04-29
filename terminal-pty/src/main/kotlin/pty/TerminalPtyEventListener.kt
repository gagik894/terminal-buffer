package com.gagik.terminal.pty

import java.io.IOException

/**
 * Host callbacks for a running PTY session.
 *
 * Implementations should return quickly. Callbacks are delivered from the PTY
 * reader or process-watcher threads, not from a UI thread.
 */
interface TerminalPtyEventListener {
    /**
     * Called when BEL is received from the terminal process.
     *
     * @param session session that received the event.
     */
    fun bell(session: TerminalPtySession)

    /**
     * Called after the OSC icon title changes.
     *
     * @param session session that received the event.
     * @param title new icon title.
     */
    fun iconTitleChanged(session: TerminalPtySession, title: String)

    /**
     * Called after the OSC window title changes.
     *
     * @param session session that received the event.
     * @param title new window title.
     */
    fun windowTitleChanged(session: TerminalPtySession, title: String)

    /**
     * Called when the PTY reader fails before the session is closed.
     *
     * @param session session whose reader failed.
     * @param exception read failure.
     */
    fun readerFailed(session: TerminalPtySession, exception: IOException)

    /**
     * Called after the child process exits.
     *
     * @param session session whose child process exited.
     * @param exitCode child process exit code.
     */
    fun processExited(session: TerminalPtySession, exitCode: Int)

    companion object {
        /**
         * Listener used when the host does not need PTY callbacks.
         */
        @JvmField
        val NONE: TerminalPtyEventListener = object : TerminalPtyEventListener {
            override fun bell(session: TerminalPtySession) = Unit
            override fun iconTitleChanged(session: TerminalPtySession, title: String) = Unit
            override fun windowTitleChanged(session: TerminalPtySession, title: String) = Unit
            override fun readerFailed(session: TerminalPtySession, exception: IOException) = Unit
            override fun processExited(session: TerminalPtySession, exitCode: Int) = Unit
        }
    }
}
