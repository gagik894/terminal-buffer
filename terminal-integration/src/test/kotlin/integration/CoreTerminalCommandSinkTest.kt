package com.gagik.integration

import com.gagik.core.TerminalBuffers
import com.gagik.core.api.TerminalBufferApi
import com.gagik.core.model.AttributeColor
import com.gagik.core.model.UnderlineStyle
import com.gagik.parser.api.TerminalOutputParser
import com.gagik.parser.api.TerminalParsers
import com.gagik.terminal.protocol.MouseEncodingMode
import com.gagik.terminal.protocol.MouseTrackingMode
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

        @Test
        fun `DECSTR soft reset parsed from bytes preserves content and resets soft state`() {
            val f = Fixture(terminal = TerminalBuffers.create(width = 8, height = 3))

            f.acceptAscii("\u001B[?1;6;7;12;25;66;1004;1006;2004h")
            f.acceptAscii("\u001B[4h")
            f.acceptAscii("AB")
            f.acceptAscii("\u001B[1;38;5;196m")
            f.acceptAscii("\u001B[?7l")
            f.acceptAscii("\u001B[1\"q")
            f.acceptAscii("\u001B[!p")
            f.acceptAscii("C")
            f.end()

            val snapshot = f.terminal.getModeSnapshot()
            val attr = f.terminal.getAttrAt(2, 0)

            assertAll(
                { assertEquals("ABC", f.terminal.getLineAsString(0)) },
                { assertFalse(snapshot.isInsertMode) },
                { assertFalse(snapshot.isApplicationCursorKeys) },
                { assertFalse(snapshot.isApplicationKeypad) },
                { assertFalse(snapshot.isOriginMode) },
                { assertTrue(snapshot.isAutoWrap) },
                { assertTrue(snapshot.isCursorVisible) },
                { assertFalse(snapshot.isCursorBlinking) },
                { assertTrue(snapshot.isFocusReportingEnabled) },
                { assertTrue(snapshot.isBracketedPasteEnabled) },
                { assertEquals(MouseEncodingMode.SGR, snapshot.mouseEncodingMode) },
                { assertEquals(AttributeColor.DEFAULT, attr?.foreground) },
                { assertFalse(attr?.bold == true) },
                { assertFalse(attr?.selectiveEraseProtected == true) },
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
            f.acceptAscii("\u001B[?1;5;6;7;12;25;66;69;1004;2004h")

            val snapshot = f.terminal.getModeSnapshot()

            assertAll(
                { assertTrue(snapshot.isInsertMode) },
                { assertTrue(snapshot.isNewLineMode) },
                { assertTrue(snapshot.isApplicationCursorKeys) },
                { assertTrue(snapshot.isReverseVideo) },
                { assertTrue(snapshot.isOriginMode) },
                { assertTrue(snapshot.isAutoWrap) },
                { assertTrue(snapshot.isCursorBlinking) },
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

            f.acceptAscii("\u001B[4;20h\u001B[?1;5;6;7;12;25;66;69;1004;2004h")
            f.acceptAscii("\u001B[4;20l\u001B[?1;5;6;7;12;25;66;69;1004;2004l")

            val snapshot = f.terminal.getModeSnapshot()

            assertAll(
                { assertFalse(snapshot.isInsertMode) },
                { assertFalse(snapshot.isNewLineMode) },
                { assertFalse(snapshot.isApplicationCursorKeys) },
                { assertFalse(snapshot.isReverseVideo) },
                { assertFalse(snapshot.isOriginMode) },
                { assertFalse(snapshot.isAutoWrap) },
                { assertFalse(snapshot.isCursorBlinking) },
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
        fun `UTF8 and URXVT mouse encoding modes update core snapshot`() {
            val f = Fixture()

            f.acceptAscii("\u001B[?1005h")
            assertEquals(MouseEncodingMode.UTF8, f.terminal.getModeSnapshot().mouseEncodingMode)

            f.acceptAscii("\u001B[?1015h")
            assertEquals(MouseEncodingMode.URXVT, f.terminal.getModeSnapshot().mouseEncodingMode)

            f.acceptAscii("\u001B[?1015l")
            assertEquals(MouseEncodingMode.DEFAULT, f.terminal.getModeSnapshot().mouseEncodingMode)
        }

        @Test
        fun `synchronized output mode is parsed and explicitly ignored until renderer batching exists`() {
            val f = Fixture()

            val before = f.terminal.getModeSnapshot()
            f.acceptAscii("\u001B[?2026h")
            f.acceptAscii("\u001B[?2026l")

            assertEquals(before, f.terminal.getModeSnapshot())
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
        fun `alternate screen mode 47 switches buffers without clearing alt content`() {
            val f = Fixture()

            f.acceptAscii("P\u001B[?47hA\u001B[?47lQ\u001B[?47h")
            assertEquals("A", f.terminal.getLineAsString(0))

            f.acceptAscii("B")
            f.end()
            assertEquals("AB", f.terminal.getLineAsString(0))
        }

        @Test
        fun `alternate screen mode 1047 switches buffers and clears alt content on entry`() {
            val f = Fixture()

            f.acceptAscii("\u001B[?47hA")
            f.acceptAscii("\u001B[?47l")
            f.acceptAscii("\u001B[?1047h")
            assertEquals("", f.terminal.getLineAsString(0))

            f.acceptAscii("B")
            f.acceptAscii("\u001B[?1047l")
            f.acceptAscii("\u001B[?47h")
            assertEquals("B", f.terminal.getLineAsString(0))
        }

        @Test
        fun `alternate screen mode 1048 saves and restores cursor without switching buffers`() {
            val f = Fixture()

            f.acceptAscii("AB")
            f.acceptAscii("\u001B[?1048h")
            f.acceptAscii("\u001B[2;4H")
            f.acceptAscii("\u001B[?1048l")
            f.acceptAscii("C")
            f.end()

            assertEquals("ABC", f.terminal.getLineAsString(0))
        }

        @Test
        fun `alternate screen mode 1049 switches buffers and restores the primary cursor`() {
            val f = Fixture()

            f.acceptAscii("P\u001B[?1049hA\u001B[?1049lQ\u001B[?47h")
            assertEquals("A", f.terminal.getLineAsString(0))

            f.acceptAscii("\u001B[?47l")
            assertEquals("PQ", f.terminal.getLineAsString(0))
        }
    }

    @Nested
    @DisplayName("SGR and OSC policy")
    inner class SgrAndOscPolicy {

        @Test
        fun `SGR indexed color and styles update core pen attributes`() {
            val f = Fixture()

            f.acceptAscii("\u001B[1;2;3;4:3;5;7;8;9;53;38;5;196;48;5;17mX")
            f.end()

            val attr = f.terminal.getAttrAt(0, 0)

            assertAll(
                { assertEquals(AttributeColor.indexed(196), attr?.foreground) },
                { assertEquals(AttributeColor.indexed(17), attr?.background) },
                { assertEquals(true, attr?.bold) },
                { assertEquals(true, attr?.faint) },
                { assertEquals(true, attr?.italic) },
                { assertEquals(UnderlineStyle.CURLY, attr?.underlineStyle) },
                { assertEquals(true, attr?.blink) },
                { assertEquals(true, attr?.inverse) },
                { assertEquals(true, attr?.conceal) },
                { assertEquals(true, attr?.strikethrough) },
                { assertEquals(true, attr?.overline) },
            )
        }

        @Test
        fun `SGR underline color updates core extended pen attributes`() {
            val f = Fixture()

            f.acceptAscii("\u001B[58;2;1;2;3;4:5mX")
            f.acceptAscii("\u001B[59;24mY")
            f.end()

            val first = f.terminal.getAttrAt(0, 0)
            val second = f.terminal.getAttrAt(1, 0)

            assertAll(
                { assertEquals(AttributeColor.rgb(1, 2, 3), first?.underlineColor) },
                { assertEquals(UnderlineStyle.DASHED, first?.underlineStyle) },
                { assertEquals(AttributeColor.DEFAULT, second?.underlineColor) },
                { assertEquals(UnderlineStyle.NONE, second?.underlineStyle) },
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

            f.acceptAscii("L")
            f.acceptAscii("\u001B]8;;\u001B\\")
            f.acceptAscii("N")
            f.end()

            assertAll(
                { assertNull(f.sink.activeHyperlinkUri) },
                { assertNull(f.sink.activeHyperlinkId) },
                { assertEquals(1, f.terminal.getAttrAt(0, 0)?.hyperlinkId) },
                { assertEquals(0, f.terminal.getAttrAt(1, 0)?.hyperlinkId) },
            )
        }

        @Test
        fun `OSC hyperlink ids are bounded and do not overflow`() {
            val f = Fixture(terminal = TerminalBuffers.create(width = 3, height = 1))

            repeat(4096) { index ->
                f.sink.startHyperlink(uri = "https://example.com/$index", id = null)
            }

            f.sink.writeCodepoint('A'.code)
            f.sink.startHyperlink(uri = "https://example.com/overflow", id = null)
            f.sink.writeCodepoint('B'.code)
            f.sink.startHyperlink(uri = "https://example.com/0", id = null)
            f.sink.writeCodepoint('C'.code)

            assertAll(
                { assertEquals(4096, f.terminal.getAttrAt(0, 0)?.hyperlinkId) },
                { assertEquals(0, f.terminal.getAttrAt(1, 0)?.hyperlinkId) },
                { assertEquals(1, f.terminal.getAttrAt(2, 0)?.hyperlinkId) },
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
