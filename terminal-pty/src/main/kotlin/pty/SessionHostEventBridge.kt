package com.gagik.terminal.pty

import com.gagik.integration.TerminalHostEventSink
import java.util.*

internal class SessionHostEventBridge(
    private val listener: TerminalPtyEventListener,
) : TerminalHostEventSink {
    private val eventLock = Any()
    private val pendingEvents = ArrayDeque<PendingHostEvent>()

    private var attachedSession: TerminalPtySession? = null

    fun attach(session: TerminalPtySession) {
        check(attachedSession == null) {
            "SessionHostEventBridge already attached"
        }
        attachedSession = session
    }

    fun drainTo(listenerInvoker: (PendingHostEvent) -> Unit) {
        while (true) {
            val event = synchronized(eventLock) {
                if (pendingEvents.isEmpty()) null else pendingEvents.removeFirst()
            } ?: return

            listenerInvoker(event)
        }
    }

    override fun bell() {
        enqueue(PendingHostEvent.Bell)
    }

    override fun iconTitleChanged(title: String) {
        enqueue(PendingHostEvent.IconTitleChanged(title))
    }

    override fun windowTitleChanged(title: String) {
        enqueue(PendingHostEvent.WindowTitleChanged(title))
    }

    private fun enqueue(event: PendingHostEvent) {
        checkNotNull(attachedSession) {
            "SessionHostEventBridge not attached"
        }

        synchronized(eventLock) {
            pendingEvents.addLast(event)
        }
    }

    internal fun dispatch(event: PendingHostEvent) {
        val session = checkNotNull(attachedSession) {
            "SessionHostEventBridge not attached"
        }

        when (event) {
            PendingHostEvent.Bell -> listener.bell(session)
            is PendingHostEvent.IconTitleChanged -> listener.iconTitleChanged(session, event.title)
            is PendingHostEvent.WindowTitleChanged -> listener.windowTitleChanged(session, event.title)
        }
    }
}

internal sealed interface PendingHostEvent {
    data object Bell : PendingHostEvent

    data class IconTitleChanged(
        val title: String,
    ) : PendingHostEvent

    data class WindowTitleChanged(
        val title: String,
    ) : PendingHostEvent
}
