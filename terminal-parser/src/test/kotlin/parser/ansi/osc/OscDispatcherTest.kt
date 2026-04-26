package com.gagik.parser.ansi.osc

import com.gagik.parser.ansi.RecordingTerminalCommandSink
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("OscDispatcher")
class OscDispatcherTest {

    private fun dispatch(
        payload: String,
        overflowed: Boolean = false,
    ): RecordingTerminalCommandSink {
        val sink = RecordingTerminalCommandSink()
        val bytes = payload.encodeToByteArray()
        OscDispatcher.dispatch(
            sink = sink,
            payload = bytes,
            length = bytes.size,
            overflowed = overflowed,
        )
        return sink
    }

    @Nested
    @DisplayName("titles")
    inner class Titles {

        @Test
        fun `OSC 0 sets icon and window title`() {
            assertEquals(
                listOf("setIconAndWindowTitle:hello"),
                dispatch("0;hello").events
            )
        }

        @Test
        fun `OSC 1 sets icon title`() {
            assertEquals(
                listOf("setIconTitle:icon"),
                dispatch("1;icon").events
            )
        }

        @Test
        fun `OSC 2 sets window title`() {
            assertEquals(
                listOf("setWindowTitle:window"),
                dispatch("2;window").events
            )
        }
    }

    @Nested
    @DisplayName("hyperlinks")
    inner class Hyperlinks {

        @Test
        fun `OSC 8 with empty params starts hyperlink without id`() {
            assertEquals(
                listOf("startHyperlink:https://example.com:null"),
                dispatch("8;;https://example.com").events
            )
        }

        @Test
        fun `OSC 8 with id param starts hyperlink with id`() {
            assertEquals(
                listOf("startHyperlink:https://example.com:abc"),
                dispatch("8;id=abc;https://example.com").events
            )
        }

        @Test
        fun `OSC 8 with empty URI ends hyperlink`() {
            assertEquals(listOf("endHyperlink"), dispatch("8;;").events)
        }
    }

    @Nested
    @DisplayName("ignored payloads")
    inner class IgnoredPayloads {

        @Test
        fun `malformed command is ignored`() {
            assertTrue(dispatch("x;hello").events.isEmpty())
            assertTrue(dispatch(";hello").events.isEmpty())
            assertTrue(dispatch("hello").events.isEmpty())
            assertTrue(dispatch("999999999999999999999999;hello").events.isEmpty())
        }

        @Test
        fun `unsupported command is ignored`() {
            assertTrue(dispatch("9;ignored").events.isEmpty())
        }

        @Test
        fun `overflowed payload is ignored`() {
            assertTrue(dispatch("0;truncated", overflowed = true).events.isEmpty())
        }

        @Test
        fun `OSC 52 clipboard is ignored`() {
            assertTrue(dispatch("52;c;SGVsbG8=").events.isEmpty())
        }
    }
}
