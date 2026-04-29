package com.gagik.terminal.pty

import com.gagik.terminal.session.TerminalSession
import java.io.IOException

/**
 * Session factories for local terminal hosts.
 */
object TerminalSessions {
    /**
     * Starts a local PTY and returns the shared transport-neutral session type.
     */
    @JvmStatic
    @Throws(IOException::class)
    fun localPty(options: TerminalPtyOptions = TerminalPtyOptions()): TerminalSession {
        return TerminalPtySessions.start(options)
    }
}
