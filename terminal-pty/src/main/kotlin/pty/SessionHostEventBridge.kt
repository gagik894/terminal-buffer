package com.gagik.terminal.pty

import com.gagik.integration.TerminalHostEventSink
import com.gagik.terminal.session.TerminalSession

internal class SessionHostEventBridge(
    private val listener: TerminalPtyEventListener,
) : TerminalHostEventSink {
    private var attachedSession: TerminalSession? = null

    fun attach(session: TerminalSession) {
        check(attachedSession == null) {
            "SessionHostEventBridge already attached"
        }
        attachedSession = session
    }

    override fun bell() {
        safeDispatch { session -> listener.bell(session) }
    }

    override fun iconTitleChanged(title: String) {
        safeDispatch { session -> listener.iconTitleChanged(session, title) }
    }

    override fun windowTitleChanged(title: String) {
        safeDispatch { session -> listener.windowTitleChanged(session, title) }
    }

    private inline fun safeDispatch(block: (TerminalSession) -> Unit) {
        val session = checkNotNull(attachedSession) {
            "SessionHostEventBridge not attached"
        }

        try {
            block(session)
        } catch (exception: Exception) {
            try {
                listener.listenerFailed(session, exception)
            } catch (_: Exception) {
                // Ignore secondary listener failure.
            }
        }
    }
}
