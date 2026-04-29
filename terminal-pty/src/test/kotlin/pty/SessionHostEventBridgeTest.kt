package com.gagik.terminal.pty

import com.gagik.core.TerminalBuffers
import com.gagik.parser.api.TerminalOutputParser
import com.gagik.terminal.input.api.TerminalInputEncoder
import com.gagik.terminal.input.event.TerminalFocusEvent
import com.gagik.terminal.input.event.TerminalKeyEvent
import com.gagik.terminal.input.event.TerminalMouseEvent
import com.gagik.terminal.input.event.TerminalPasteEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

class SessionHostEventBridgeTest {
    @Test
    fun `attach twice fails`() {
        val bridge = SessionHostEventBridge(RecordingListener())
        val session = testSession(bridge)

        bridge.attach(session)

        assertThrows(IllegalStateException::class.java) {
            bridge.attach(session)
        }
    }

    @Test
    fun `events before attach fail predictably`() {
        val bridge = SessionHostEventBridge(RecordingListener())

        assertThrows(IllegalStateException::class.java) { bridge.bell() }
        assertThrows(IllegalStateException::class.java) { bridge.iconTitleChanged("icon") }
        assertThrows(IllegalStateException::class.java) { bridge.windowTitleChanged("window") }
    }

    @Test
    fun `queued events dispatch in order`() {
        val listener = RecordingListener()
        val bridge = SessionHostEventBridge(listener)
        bridge.attach(testSession(bridge, listener))

        bridge.bell()
        bridge.iconTitleChanged("icon")
        bridge.windowTitleChanged("window")
        bridge.drainTo { bridge.dispatch(it) }

        assertEquals(listOf("bell", "icon:icon", "window:window"), listener.events)
    }

    private fun testSession(
        bridge: SessionHostEventBridge,
        listener: TerminalPtyEventListener = TerminalPtyEventListener.NONE,
    ): TerminalPtySession {
        val terminal = TerminalBuffers.create(width = 5, height = 2)
        return TerminalPtySession(
            terminal = terminal,
            process = TestProcess(),
            parser = NoopParser,
            inputEncoder = NoopInputEncoder,
            hostOutput = StreamTerminalHostOutput(ByteArrayOutputStream()),
            hostEventBridge = bridge,
            readBufferSize = 16,
            readerThreadName = "bridge-test-reader",
            watcherThreadName = "bridge-test-watcher",
            eventListener = listener,
        )
    }

    private class RecordingListener : TerminalPtyEventListener {
        val events = mutableListOf<String>()
        override fun bell(session: TerminalPtySession) {
            events += "bell"
        }
        override fun iconTitleChanged(session: TerminalPtySession, title: String) {
            events += "icon:$title"
        }
        override fun windowTitleChanged(session: TerminalPtySession, title: String) {
            events += "window:$title"
        }
        override fun readerFailed(session: TerminalPtySession, exception: IOException) = Unit
        override fun processExited(session: TerminalPtySession, exitCode: Int) = Unit
        override fun listenerFailed(session: TerminalPtySession, exception: Exception) = Unit
    }

    private class TestProcess : TerminalProcess {
        override val input = ByteArrayInputStream(ByteArray(0))
        override val output = ByteArrayOutputStream()
        override fun isAlive(): Boolean = true
        override fun waitFor(): Int = 0
        override fun destroy() = Unit
        override fun resize(columns: Int, rows: Int) = Unit
    }

    private object NoopParser : TerminalOutputParser {
        override fun accept(bytes: ByteArray, offset: Int, length: Int) = Unit
        override fun acceptByte(byteValue: Int) = Unit
        override fun endOfInput() = Unit
        override fun reset() = Unit
    }

    private object NoopInputEncoder : TerminalInputEncoder {
        override fun encodeKey(event: TerminalKeyEvent) = Unit
        override fun encodePaste(event: TerminalPasteEvent) = Unit
        override fun encodeFocus(event: TerminalFocusEvent) = Unit
        override fun encodeMouse(event: TerminalMouseEvent) = Unit
    }
}
