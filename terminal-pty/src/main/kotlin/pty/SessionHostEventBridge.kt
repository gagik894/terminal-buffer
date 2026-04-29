package com.gagik.terminal.pty

import com.gagik.integration.TerminalHostEventSink

internal class SessionHostEventBridge(
    private val listener: TerminalPtyEventListener,
) : TerminalHostEventSink {
    lateinit var session: TerminalPtySession

    override fun bell() {
        listener.bell(session)
    }

    override fun iconTitleChanged(title: String) {
        listener.iconTitleChanged(session, title)
    }

    override fun windowTitleChanged(title: String) {
        listener.windowTitleChanged(session, title)
    }
}
