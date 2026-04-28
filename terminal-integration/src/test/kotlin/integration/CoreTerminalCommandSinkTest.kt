package com.gagik.integration

import com.gagik.core.TerminalBuffers
import com.gagik.core.api.TerminalBufferApi
import com.gagik.core.model.AttributeColor
import com.gagik.terminal.protocol.MouseEncodingMode
import com.gagik.terminal.protocol.MouseTrackingMode
import com.gagik.parser.api.TerminalOutputParser
import com.gagik.parser.api.TerminalParsers
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("CoreTerminalCommandSink")
class CoreTerminalCommandSinkTest {

    private data class Fixture(
        val terminal: TerminalBufferApi = TerminalBuffers.create(width = 10, height = 5),
        val sink: CoreTerminalCommandSink = CoreTerminalCommandSink(terminal),
        val parser: TerminalOutputParser = TerminalParsers.create(sink),
    ) {
        fun acceptAscii(text: String) {
            parser.accept(text.encodeToByteArray())
        }

        fun end() {
            parser.endOfInput()
        }

        fun drainResponses(): String {
            val destination = ByteArray(128)
            val count = terminal.readResponseBytes(destination)
            return destination.decodeToString(0, count)
        }
    }

    @Nested
    @DisplayName("printable and cursor pipeline")
    inner class PrintableAndCursorPipeline {

        @Test
        fun `plain text parsed through adapter writes the core grid`() {
            val f = Fixture()

            f.acceptAscii("abc")
            f.end()

            assertEquals("abc", f.terminal.getLineAsString(0))
        }

        @Test
        fun `CSI absolute cursor position writes at core coordinates`() {
            val f = Fixture()

            f.acceptAscii("\u001B[2;3HX")
            f.end()

            assertAll(
                { assertEquals('X'.code, f.terminal.getCodepointAt(2, 1)) },
                { assertEquals(3, f.terminal.cursorCol) },
                { assertEquals(1, f.terminal.cursorRow) },
            )
        }

        @Test
        fun `origin mode makes CUP relative to the scroll region`() {
            val f = Fixture()

            f.acceptAscii("\u001B[2;4r")
            f.acceptAscii("\u001B[?6h")
            f.acceptAscii("\u001B[1;1HX")
            f.end()

            assertAll(
                { assertEquals('X'.code, f.terminal.getCodepointAt(0, 1)) },
                { assertEquals(1, f.terminal.cursorCol) },
                { assertEquals(1, f.terminal.cursorRow) },
            )
        }

        @Test
        fun `insert mode shifts existing cells through real core mutation`() {
            val f = Fixture(terminal = TerminalBuffers.create(width = 6, height = 2))

            f.acceptAscii("ABCD")
            f.acceptAscii("\u001B[1;2H")
            f.acceptAscii("\u001B[4h")
            f.acceptAscii("X")
            f.end()

            assertEquals("AXBCD", f.terminal.getLineAsString(0))
        }

        @Test
        fun `new line mode makes LF also return carriage through adapter policy`() {
            val f = Fixture()

            f.acceptAscii("A")
            f.acceptAscii("\u001B[20h")
            f.acceptAscii("\nB")
            f.end()

            assertAll(
                { assertEquals("A", f.terminal.getLineAsString(0)) },
                { assertEquals("B", f.terminal.getLineAsString(1)) },
                { assertEquals(1, f.terminal.cursorCol) },
                { assertEquals(1, f.terminal.cursorRow) },
            )
        }

        @Test
        fun `auto wrap reset keeps printable output on the right edge`() {
            val f = Fixture(terminal = TerminalBuffers.create(width = 3, height = 2))

            f.acceptAscii("\u001B[?7l")
            f.acceptAscii("ABCD")
            f.end()

            assertAll(
                { assertEquals("ABD", f.terminal.getLineAsString(0)) },
                { assertEquals("", f.terminal.getLineAsString(1)) },
                { assertEquals(2, f.terminal.cursorCol) },
            )
        }

        @Test
        fun `RIS hard reset parsed from bytes resets core state`() {
            val f = Fixture(terminal = TerminalBuffers.create(width = 6, height = 2))

            f.acceptAscii("ABC")
            f.acceptAscii("\u001B[1;38;5;196m")
            f.acceptAscii("\u001B[?7l")
            f.acceptAscii("\u001B[2;3H")
            f.acceptAscii("\u001Bc")
            f.acceptAscii("X")
            f.end()

            val attr = f.terminal.getAttrAt(0, 0)

            assertAll(
                { assertEquals("X", f.terminal.getLineAsString(0)) },
                { assertEquals("", f.terminal.getLineAsString(1)) },
                { assertEquals(1, f.terminal.cursorCol) },
                { assertEquals(0, f.terminal.cursorRow) },
                { assertTrue(f.terminal.getModeSnapshot().isAutoWrap) },
                { assertEquals(AttributeColor.DEFAULT, attr?.foreground) },
                { assertEquals(false, attr?.bold) },
            )
        }
    }

    @Nested
    @DisplayName("mode policy")
    inner class ModePolicy {

        @Test
        fun `ANSI and DEC modes parsed from bytes update core mode snapshot`() {
            val f = Fixture()

            f.acceptAscii("\u001B[4;20h")
            f.acceptAscii("\u001B[?1;5;6;7;25;66;69;1004;2004h")

            val snapshot = f.terminal.getModeSnapshot()

            assertAll(
                { assertTrue(snapshot.isInsertMode) },
                { assertTrue(snapshot.isNewLineMode) },
                { assertTrue(snapshot.isApplicationCursorKeys) },
                { assertTrue(snapshot.isReverseVideo) },
                { assertTrue(snapshot.isOriginMode) },
                { assertTrue(snapshot.isAutoWrap) },
                { assertTrue(snapshot.isCursorVisible) },
                { assertTrue(snapshot.isApplicationKeypad) },
                { assertTrue(snapshot.isLeftRightMarginMode) },
                { assertTrue(snapshot.isFocusReportingEnabled) },
                { assertTrue(snapshot.isBracketedPasteEnabled) },
            )
        }

        @Test
        fun `DEC mode reset parsed from bytes updates core mode snapshot`() {
            val f = Fixture()

            f.acceptAscii("\u001B[4;20h\u001B[?1;5;6;7;25;66;69;1004;2004h")
            f.acceptAscii("\u001B[4;20l\u001B[?1;5;6;7;25;66;69;1004;2004l")

            val snapshot = f.terminal.getModeSnapshot()

            assertAll(
                { assertFalse(snapshot.isInsertMode) },
                { assertFalse(snapshot.isNewLineMode) },
                { assertFalse(snapshot.isApplicationCursorKeys) },
                { assertFalse(snapshot.isReverseVideo) },
                { assertFalse(snapshot.isOriginMode) },
                { assertFalse(snapshot.isAutoWrap) },
                { assertFalse(snapshot.isCursorVisible) },
                { assertFalse(snapshot.isApplicationKeypad) },
                { assertFalse(snapshot.isLeftRightMarginMode) },
                { assertFalse(snapshot.isFocusReportingEnabled) },
                { assertFalse(snapshot.isBracketedPasteEnabled) },
            )
        }

        @Test
        fun `mouse tracking and SGR mouse encoding modes update core snapshot`() {
            val f = Fixture()

            f.acceptAscii("\u001B[?1002;1006h")

            var snapshot = f.terminal.getModeSnapshot()
            assertAll(
                { assertEquals(MouseTrackingMode.BUTTON_EVENT, snapshot.mouseTrackingMode) },
                { assertEquals(MouseEncodingMode.SGR, snapshot.mouseEncodingMode) },
            )

            f.acceptAscii("\u001B[?1002;1006l")

            snapshot = f.terminal.getModeSnapshot()
            assertAll(
                { assertEquals(MouseTrackingMode.OFF, snapshot.mouseTrackingMode) },
                { assertEquals(MouseEncodingMode.DEFAULT, snapshot.mouseEncodingMode) },
            )
        }

        @Test
        fun `DECCOLM set and reset resize the core width`() {
            val f = Fixture(terminal = TerminalBuffers.create(width = 80, height = 3))

            f.acceptAscii("\u001B[?3h")
            assertEquals(132, f.terminal.width)

            f.acceptAscii("\u001B[?3l")
            assertEquals(80, f.terminal.width)
        }

        @Test
        fun `DSR CPR and DA parsed from bytes queue terminal-to-host responses`() {
            val f = Fixture(terminal = TerminalBuffers.create(width = 10, height = 5))

            f.acceptAscii("\u001B[5n")
            f.acceptAscii("\u001B[2;3H")
            f.acceptAscii("\u001B[6n")
            f.acceptAscii("\u001B[?6n")
            f.acceptAscii("\u001B[c")
            f.acceptAscii("\u001B[>c")
            f.acceptAscii("\u001B[=c")
            f.end()

            assertEquals(
                "\u001B[0n\u001B[2;3R\u001B[?2;3R\u001B[?1;2c\u001B[>0;0;0c",
                f.drainResponses(),
            )
        }

        @Test
        fun `safe xterm window reports parsed from bytes queue terminal-to-host responses`() {
            val f = Fixture(terminal = TerminalBuffers.create(width = 120, height = 40))

            f.terminal.setWindowSizePixels(width = 800, height = 400)
            f.acceptAscii("\u001B[14t")
            f.acceptAscii("\u001B[18t")
            f.acceptAscii("\u001B[8;10;20t")
            f.end()

            assertEquals("\u001B[4;400;800t\u001B[8;40;120t", f.drainResponses())
        }

        @Test
        fun `DECSLRM parsed from bytes updates core left right margins when mode is enabled`() {
            val f = Fixture(terminal = TerminalBuffers.create(width = 8, height = 3))

            f.acceptAscii("\u001B[?69h")
            f.acceptAscii("\u001B[3;6s")
            f.end()

            assertAll(
                { assertEquals(2, f.terminal.cursorCol) },
                { assertEquals(0, f.terminal.cursorRow) },
            )
        }

        @Test
        fun `alternate screen mode 1049 switches buffers while 47 and 1047 remain explicit TODO gaps`() {
            val f = Fixture()

            f.acceptAscii("P")
            f.acceptAscii("\u001B[?1049h")
            f.acceptAscii("A")
            f.end()

            assertEquals("A", f.terminal.getLineAsString(0))

            f.acceptAscii("\u001B[?1049l")

            assertEquals("P", f.terminal.getLineAsString(0))

            f.acceptAscii("\u001B[?47hX")
            f.acceptAscii("\u001B[?47l")
            f.acceptAscii("\u001B[?1047hY")
            f.acceptAscii("\u001B[?1047l")
            f.end()

            assertEquals("PXY", f.terminal.getLineAsString(0))
        }
    }

    @Nested
    @DisplayName("SGR and OSC policy")
    inner class SgrAndOscPolicy {

        @Test
        fun `SGR indexed color and styles update core pen attributes`() {
            val f = Fixture()

            f.acceptAscii("\u001B[1;3;4;7;38;5;196;48;5;17mX")
            f.end()

            val attr = f.terminal.getAttrAt(0, 0)

            assertAll(
                { assertEquals(AttributeColor.indexed(196), attr?.foreground) },
                { assertEquals(AttributeColor.indexed(17), attr?.background) },
                { assertEquals(true, attr?.bold) },
                { assertEquals(true, attr?.italic) },
                { assertEquals(true, attr?.underline) },
                { assertEquals(true, attr?.inverse) },
            )
        }

        @Test
        fun `SGR reset restores default core pen attributes`() {
            val f = Fixture()

            f.acceptAscii("\u001B[1;31mX")
            f.acceptAscii("\u001B[0mY")
            f.end()

            val first = f.terminal.getAttrAt(0, 0)
            val second = f.terminal.getAttrAt(1, 0)

            assertAll(
                { assertEquals(true, first?.bold) },
                { assertEquals(AttributeColor.indexed(1), first?.foreground) },
                { assertEquals(false, second?.bold) },
                { assertEquals(AttributeColor.DEFAULT, second?.foreground) },
                { assertEquals(AttributeColor.DEFAULT, second?.background) },
            )
        }

        @Test
        fun `RGB SGR updates core pen colors`() {
            val f = Fixture()

            f.acceptAscii("\u001B[38;2;10;20;30;48;2;40;50;60mX")
            f.end()

            val attr = f.terminal.getAttrAt(0, 0)

            assertAll(
                { assertEquals(AttributeColor.rgb(10, 20, 30), attr?.foreground) },
                { assertEquals(AttributeColor.rgb(40, 50, 60), attr?.background) },
            )
        }

        @Test
        fun `SGR default colors and inverse reset preserve other pen attributes`() {
            val f = Fixture()

            f.acceptAscii("\u001B[1;7;38;5;196;48;2;40;50;60mX")
            f.acceptAscii("\u001B[27;39;49mY")
            f.end()

            val first = f.terminal.getAttrAt(0, 0)
            val second = f.terminal.getAttrAt(1, 0)

            assertAll(
                { assertEquals(AttributeColor.indexed(196), first?.foreground) },
                { assertEquals(AttributeColor.rgb(40, 50, 60), first?.background) },
                { assertEquals(true, first?.bold) },
                { assertEquals(true, first?.inverse) },
                { assertEquals(AttributeColor.DEFAULT, second?.foreground) },
                { assertEquals(AttributeColor.DEFAULT, second?.background) },
                { assertEquals(true, second?.bold) },
                { assertEquals(false, second?.inverse) },
            )
        }

        @Test
        fun `DECSCA and DECSEL preserve protected cells through parser and adapter`() {
            val f = Fixture(terminal = TerminalBuffers.create(width = 6, height = 2))

            f.acceptAscii("\u001B[1\"qA")
            f.acceptAscii("\u001B[2\"qB")
            f.acceptAscii("\u001B[?2K")
            f.end()

            assertAll(
                { assertEquals("A", f.terminal.getLineAsString(0)) },
                { assertTrue(f.terminal.getAttrAt(0, 0)?.selectiveEraseProtected == true) },
            )
        }

        @Test
        fun `DECSCA and DECSED preserve protected cells through parser and adapter`() {
            val f = Fixture(terminal = TerminalBuffers.create(width = 6, height = 2))

            f.acceptAscii("\u001B[1\"qA")
            f.acceptAscii("\u001B[2\"qB")
            f.acceptAscii("\u001B[2;1HC")
            f.acceptAscii("\u001B[?2J")
            f.end()

            assertAll(
                { assertEquals("A", f.terminal.getLineAsString(0)) },
                { assertEquals("", f.terminal.getLineAsString(1)) },
                { assertTrue(f.terminal.getAttrAt(0, 0)?.selectiveEraseProtected == true) },
            )
        }

        @Test
        fun `OSC titles and hyperlinks are retained as adapter metadata`() {
            val f = Fixture()

            f.acceptAscii("\u001B]0;both\u0007")
            f.acceptAscii("\u001B]8;id=abc;https://example.com\u001B\\")

            assertAll(
                { assertEquals("both", f.sink.iconTitle) },
                { assertEquals("both", f.sink.windowTitle) },
                { assertEquals("https://example.com", f.sink.activeHyperlinkUri) },
                { assertEquals("abc", f.sink.activeHyperlinkId) },
            )

            f.acceptAscii("\u001B]8;;\u001B\\")

            assertAll(
                { assertNull(f.sink.activeHyperlinkUri) },
                { assertNull(f.sink.activeHyperlinkId) },
            )
        }

        @Test
        fun `xterm title stack push and pop restores icon and window titles`() {
            val f = Fixture()

            f.acceptAscii("\u001B]0;base\u0007")
            f.acceptAscii("\u001B[22t")
            f.acceptAscii("\u001B]1;icon-temp\u0007")
            f.acceptAscii("\u001B]2;window-temp\u0007")
            f.acceptAscii("\u001B[23t")

            assertAll(
                { assertEquals("base", f.sink.iconTitle) },
                { assertEquals("base", f.sink.windowTitle) },
            )

            f.acceptAscii("\u001B]1;icon-only-base\u0007")
            f.acceptAscii("\u001B]2;window-stays\u0007")
            f.acceptAscii("\u001B[22;1t")
            f.acceptAscii("\u001B]1;icon-only-temp\u0007")
            f.acceptAscii("\u001B[23;1t")

            assertAll(
                { assertEquals("icon-only-base", f.sink.iconTitle) },
                { assertEquals("window-stays", f.sink.windowTitle) },
            )
        }
    }
}
