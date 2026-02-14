package com.gagik.terminal.buffer

import com.gagik.terminal.codec.AttributeCodec
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("TerminalBuffer (Integration)")
class TerminalBufferTest {

    @Nested
    @DisplayName("Constructor")
    inner class ConstructorTests {

        @Test
        fun `initializes with correct dimensions`() {
            val buffer = TerminalBuffer(80, 24, maxHistory = 100)

            assertEquals(80, buffer.width)
            assertEquals(24, buffer.height)
        }

        @Test
        fun `initializes cursor at origin`() {
            val buffer = TerminalBuffer(80, 24)

            assertEquals(0, buffer.cursorCol)
            assertEquals(0, buffer.cursorRow)
        }

        @Test
        fun `throws on invalid dimensions`() {
            assertThrows<IllegalArgumentException> { TerminalBuffer(0, 24) }
            assertThrows<IllegalArgumentException> { TerminalBuffer(80, 0) }
            assertThrows<IllegalArgumentException> { TerminalBuffer(80, 24, -1) }
        }

        @Test
        fun `initializes with blank screen`() {
            val buffer = TerminalBuffer(10, 3)

            for (row in 0 until 3) {
                for (col in 0 until 10) {
                    assertEquals(0, buffer.getLine(row).getCodepoint(col))
                }
            }
        }
    }

    @Nested
    @DisplayName("writeChar() - Normal Cases")
    inner class WriteCharNormalTests {

        @Test
        fun `writes character at cursor`() {
            val buffer = TerminalBuffer(10, 5)
            buffer.writeChar('A'.code)

            assertEquals('A'.code, buffer.getLine(0).getCodepoint(0))
            assertEquals(1, buffer.cursorCol)
        }

        @Test
        fun `writes multiple characters sequentially`() {
            val buffer = TerminalBuffer(10, 5)
            buffer.writeChar('A'.code)
            buffer.writeChar('B'.code)
            buffer.writeChar('C'.code)

            assertEquals('A'.code, buffer.getLine(0).getCodepoint(0))
            assertEquals('B'.code, buffer.getLine(0).getCodepoint(1))
            assertEquals('C'.code, buffer.getLine(0).getCodepoint(2))
            assertEquals(3, buffer.cursorCol)
        }

        @Test
        fun `uses current pen attributes`() {
            val buffer = TerminalBuffer(10, 5)
            buffer.setAttributes(fg = 5, bg = 10, bold = true)
            buffer.writeChar('A'.code)

            val expected = AttributeCodec.pack(5, 10, bold = true, italic = false, underline = false)
            assertEquals(expected, buffer.getLine(0).getAttr(0))
        }
    }

    @Nested
    @DisplayName("writeChar() - Line Wrapping")
    inner class WriteCharWrappingTests {

        @Test
        fun `wraps to next line at end of row`() {
            val buffer = TerminalBuffer(3, 5)
            buffer.writeChar('A'.code)
            buffer.writeChar('B'.code)
            buffer.writeChar('C'.code)  // Fill line
            buffer.writeChar('D'.code)  // Should wrap

            assertEquals('D'.code, buffer.getLine(1).getCodepoint(0))
            assertEquals(1, buffer.cursorCol)
            assertEquals(1, buffer.cursorRow)
        }

        @Test
        fun `marks line as wrapped when wrapping`() {
            val buffer = TerminalBuffer(3, 5)
            buffer.writeText("ABC")  // Fills line, wraps

            assertTrue(buffer.getLine(0).wrapped)
        }

        @Test
        fun `wrapping preserves content`() {
            val buffer = TerminalBuffer(3, 5)
            buffer.writeText("ABCDEF")

            assertEquals('A'.code, buffer.getLine(0).getCodepoint(0))
            assertEquals('B'.code, buffer.getLine(0).getCodepoint(1))
            assertEquals('C'.code, buffer.getLine(0).getCodepoint(2))
            assertEquals('D'.code, buffer.getLine(1).getCodepoint(0))
            assertEquals('E'.code, buffer.getLine(1).getCodepoint(1))
            assertEquals('F'.code, buffer.getLine(1).getCodepoint(2))
        }
    }

    @Nested
    @DisplayName("writeChar() - Scrolling")
    inner class WriteCharScrollingTests {

        @Test
        fun `scrolls when wrapping past bottom`() {
            val buffer = TerminalBuffer(2, 2)
            buffer.writeText("ABCD")  // Fill screen
            buffer.writeChar('E'.code)  // Should scroll

            assertEquals(1, buffer.historySize)
            assertEquals(1, buffer.cursorRow)
        }

        @Test
        fun `scrolled content moves to history`() {
            val buffer = TerminalBuffer(3, 2)
            buffer.writeText("ABC")  // Line 0, wraps
            buffer.writeText("DEF")  // Line 1, wraps
            buffer.writeChar('G'.code)  // Scroll happens

            assertEquals('A'.code, buffer.getHistoryLine(0).getCodepoint(0))
            assertEquals('D'.code, buffer.getLine(0).getCodepoint(0))
            assertEquals('G'.code, buffer.getLine(1).getCodepoint(0))
        }

        @Test
        fun `multiple scrolls build history`() {
            val buffer = TerminalBuffer(2, 2)
            buffer.writeText("ABCDEFGH")  // Should scroll multiple times

            assertTrue(buffer.historySize >= 2)
        }
    }

    @Nested
    @DisplayName("writeText()")
    inner class WriteTextTests {

        @Test
        fun `writes string`() {
            val buffer = TerminalBuffer(10, 5)
            buffer.writeText("Hello")

            assertEquals('H'.code, buffer.getLine(0).getCodepoint(0))
            assertEquals('e'.code, buffer.getLine(0).getCodepoint(1))
            assertEquals('l'.code, buffer.getLine(0).getCodepoint(2))
            assertEquals('l'.code, buffer.getLine(0).getCodepoint(3))
            assertEquals('o'.code, buffer.getLine(0).getCodepoint(4))
        }

        @Test
        fun `empty string does nothing`() {
            val buffer = TerminalBuffer(10, 5)
            buffer.writeText("")

            assertEquals(0, buffer.cursorCol)
            assertEquals(0, buffer.cursorRow)
        }

        @Test
        fun `handles wrapping in long text`() {
            val buffer = TerminalBuffer(5, 3)
            buffer.writeText("Hello World")

            assertTrue(buffer.getLine(0).wrapped)
            assertEquals(' '.code, buffer.getLine(1).getCodepoint(0))
        }
    }

    @Nested
    @DisplayName("newLine()")
    inner class NewLineTests {

        @Test
        fun `moves to next row`() {
            val buffer = TerminalBuffer(10, 5)
            buffer.writeChar('A'.code)
            buffer.newLine()

            assertEquals(0, buffer.cursorCol)
            assertEquals(1, buffer.cursorRow)
        }

        @Test
        fun `scrolls when at bottom`() {
            val buffer = TerminalBuffer(10, 3)
            buffer.setCursor(0, 2)  // Bottom row
            buffer.newLine()

            assertEquals(2, buffer.cursorRow)
            assertTrue(buffer.historySize > 0)
        }

        @Test
        fun `newLine does not mark line as wrapped`() {
            val buffer = TerminalBuffer(10, 3)
            buffer.writeChar('A'.code)
            buffer.newLine()

            assertFalse(buffer.getLine(0).wrapped)
        }
    }

    @Nested
    @DisplayName("carriageReturn()")
    inner class CarriageReturnTests {

        @Test
        fun `moves to column zero`() {
            val buffer = TerminalBuffer(10, 5)
            buffer.setCursor(5, 2)
            buffer.carriageReturn()

            assertEquals(0, buffer.cursorCol)
            assertEquals(2, buffer.cursorRow)
        }
    }

    @Nested
    @DisplayName("Cursor Operations")
    inner class CursorOperationsTests {

        @Test
        fun `setCursor positions cursor`() {
            val buffer = TerminalBuffer(10, 5)
            buffer.setCursor(5, 3)

            assertEquals(5, buffer.cursorCol)
            assertEquals(3, buffer.cursorRow)
        }

        @Test
        fun `moveCursor moves relatively`() {
            val buffer = TerminalBuffer(10, 5)
            buffer.setCursor(5, 2)
            buffer.moveCursor(2, 1)

            assertEquals(7, buffer.cursorCol)
            assertEquals(3, buffer.cursorRow)
        }

        @Test
        fun `resetCursor returns to origin`() {
            val buffer = TerminalBuffer(10, 5)
            buffer.setCursor(5, 3)
            buffer.resetCursor()

            assertEquals(0, buffer.cursorCol)
            assertEquals(0, buffer.cursorRow)
        }
    }

    @Nested
    @DisplayName("Clear Operations")
    inner class ClearOperationsTests {

        @Test
        fun `clearScreen clears all visible lines`() {
            val buffer = TerminalBuffer(10, 3)
            buffer.writeText("Test")
            buffer.newLine()
            buffer.writeText("Data")

            buffer.clearScreen()

            for (row in 0 until 3) {
                assertEquals(0, buffer.getLine(row).getCodepoint(0))
            }
        }

        @Test
        fun `clearLine clears current line only`() {
            val buffer = TerminalBuffer(10, 3)
            buffer.writeText("Line0")
            buffer.newLine()
            buffer.writeText("Line1")
            buffer.setCursor(0, 1)

            buffer.clearLine()

            assertEquals('L'.code, buffer.getLine(0).getCodepoint(0))
            assertEquals(0, buffer.getLine(1).getCodepoint(0))
        }

        @Test
        fun `clearToEndOfLine clears from cursor to end`() {
            val buffer = TerminalBuffer(10, 3)
            buffer.writeText("ABCDEFGHIJ")
            buffer.setCursor(5, 0)

            buffer.clearToEndOfLine()

            assertEquals('A'.code, buffer.getLine(0).getCodepoint(0))
            assertEquals('E'.code, buffer.getLine(0).getCodepoint(4))
            assertEquals(0, buffer.getLine(0).getCodepoint(5))
            assertEquals(0, buffer.getLine(0).getCodepoint(9))
        }

        @Test
        fun `clearToBeginningOfLine clears from start to cursor`() {
            val buffer = TerminalBuffer(10, 3)
            buffer.writeText("ABCDEFGHIJ")
            buffer.setCursor(5, 0)

            buffer.clearToBeginningOfLine()

            assertEquals(0, buffer.getLine(0).getCodepoint(0))
            assertEquals(0, buffer.getLine(0).getCodepoint(5))
            assertEquals('G'.code, buffer.getLine(0).getCodepoint(6))
        }

        @Test
        fun `clearToEndOfScreen clears from cursor to end`() {
            val buffer = TerminalBuffer(5, 5)
            buffer.writeText("AAAAA")
            buffer.writeText("BBBBB")
            buffer.writeText("CCCCC")
            buffer.setCursor(2, 1)

            buffer.clearToEndOfScreen()

            assertEquals('A'.code, buffer.getLine(0).getCodepoint(0))
            assertEquals('B'.code, buffer.getLine(1).getCodepoint(0))
            assertEquals(0, buffer.getLine(1).getCodepoint(2))
            assertEquals(0, buffer.getLine(2).getCodepoint(0))
        }

        @Test
        fun `clearToBeginningOfScreen clears from start to cursor`() {
            val buffer = TerminalBuffer(5, 3)
            buffer.writeText("AAAAA")
            buffer.writeText("BBBBB")
            buffer.writeText("CCCCC")
            buffer.setCursor(2, 1)

            buffer.clearToBeginningOfScreen()

            assertEquals(0, buffer.getLine(0).getCodepoint(0))
            assertEquals(0, buffer.getLine(1).getCodepoint(0))
            assertEquals(0, buffer.getLine(1).getCodepoint(2))
            assertEquals('C'.code, buffer.getLine(1).getCodepoint(3))
        }
    }

    @Nested
    @DisplayName("reset()")
    inner class ResetTests {

        @Test
        fun `resets cursor`() {
            val buffer = TerminalBuffer(10, 5)
            buffer.setCursor(5, 3)
            buffer.reset()

            assertEquals(0, buffer.cursorCol)
            assertEquals(0, buffer.cursorRow)
        }

        @Test
        fun `clears screen`() {
            val buffer = TerminalBuffer(10, 3)
            buffer.writeText("Test")
            buffer.reset()

            assertEquals(0, buffer.getLine(0).getCodepoint(0))
        }

        @Test
        fun `clears history`() {
            val buffer = TerminalBuffer(10, 2)
            buffer.newLine()
            buffer.newLine()
            buffer.reset()

            assertEquals(0, buffer.historySize)
        }

        @Test
        fun `resets pen`() {
            val buffer = TerminalBuffer(10, 5)
            buffer.setAttributes(10, 5, true)
            buffer.reset()
            buffer.writeChar('A'.code)

            val defaultAttr = AttributeCodec.pack(0, 0, false, false, false)
            assertEquals(defaultAttr, buffer.getLine(0).getAttr(0))
        }
    }

    @Nested
    @DisplayName("Complex Scenarios")
    inner class ComplexTests {

        @Test
        fun `write, scroll, write maintains state`() {
            val buffer = TerminalBuffer(3, 2)
            buffer.writeText("ABCDEF")  // Fill and scroll
            buffer.writeChar('G'.code)

            assertEquals('D'.code, buffer.getLine(0).getCodepoint(0))
            assertEquals('G'.code, buffer.getLine(1).getCodepoint(0))
            assertTrue(buffer.historySize > 0)
        }

        @Test
        fun `mixed operations preserve correctness`() {
            val buffer = TerminalBuffer(10, 3)
            buffer.writeText("Top")
            buffer.setCursor(0, 2)
            buffer.writeText("Bottom")
            buffer.setCursor(5, 1)
            buffer.writeChar('M'.code)

            assertEquals('T'.code, buffer.getLine(0).getCodepoint(0))
            assertEquals('M'.code, buffer.getLine(1).getCodepoint(5))
            assertEquals('B'.code, buffer.getLine(2).getCodepoint(0))
        }
    }

    @Nested
    @DisplayName("Directional Cursor Movement")
    inner class DirectionalCursorTests {

        @Test
        fun `cursorUp moves up by n rows`() {
            val buffer = TerminalBuffer(10, 5)
            buffer.setCursor(5, 3)
            buffer.cursorUp(2)

            assertEquals(5, buffer.cursorCol)
            assertEquals(1, buffer.cursorRow)
        }

        @Test
        fun `cursorDown moves down by n rows`() {
            val buffer = TerminalBuffer(10, 5)
            buffer.setCursor(5, 1)
            buffer.cursorDown(2)

            assertEquals(5, buffer.cursorCol)
            assertEquals(3, buffer.cursorRow)
        }

        @Test
        fun `cursorLeft moves left by n columns`() {
            val buffer = TerminalBuffer(10, 5)
            buffer.setCursor(5, 2)
            buffer.cursorLeft(3)

            assertEquals(2, buffer.cursorCol)
            assertEquals(2, buffer.cursorRow)
        }

        @Test
        fun `cursorRight moves right by n columns`() {
            val buffer = TerminalBuffer(10, 5)
            buffer.setCursor(2, 2)
            buffer.cursorRight(4)

            assertEquals(6, buffer.cursorCol)
            assertEquals(2, buffer.cursorRow)
        }

        @Test
        fun `cursor movement clamps to bounds`() {
            val buffer = TerminalBuffer(10, 5)
            buffer.setCursor(0, 0)
            buffer.cursorUp(10)
            assertEquals(0, buffer.cursorRow)

            buffer.cursorLeft(10)
            assertEquals(0, buffer.cursorCol)

            buffer.cursorDown(100)
            assertEquals(4, buffer.cursorRow)

            buffer.cursorRight(100)
            assertEquals(9, buffer.cursorCol)
        }
    }

    @Nested
    @DisplayName("Content Access")
    inner class ContentAccessTests {

        @Test
        fun `getCharAt returns character at position`() {
            val buffer = TerminalBuffer1(10, 5)
            buffer.writeText("Hello")

            assertEquals('H'.code, buffer.getCharAt(0, 0))
            assertEquals('e'.code, buffer.getCharAt(1, 0))
            assertEquals('o'.code, buffer.getCharAt(4, 0))
        }

        @Test
        fun `getCharAt returns null for out of bounds`() {
            val buffer = TerminalBuffer1(10, 5)
            buffer.writeText("Hello")

            assertNull(buffer.getCharAt(-1, 0))
            assertNull(buffer.getCharAt(100, 0))
            assertNull(buffer.getCharAt(0, 100))
        }

        @Test
        fun `getAttrAt returns attributes at position`() {
            val buffer = TerminalBuffer1(10, 5)
            buffer.setAttributes(5, 3, bold = true)
            buffer.writeChar('X'.code)

            val attr = buffer.getAttrAt(0, 0)
            assertNotNull(attr)
            val expected = AttributeCodec.pack(5, 3, bold = true, italic = false, underline = false)
            assertEquals(expected, attr)
        }

        @Test
        fun `getLineAsString returns line content`() {
            val buffer = TerminalBuffer1(10, 5)
            buffer.writeText("Hello")

            assertEquals("Hello", buffer.getLineAsString(0))
        }

        @Test
        fun `getScreenAsString returns all visible lines`() {
            val buffer = TerminalBuffer1(10, 3)
            buffer.writeText("Line1")
            buffer.newLine()
            buffer.writeText("Line2")
            buffer.newLine()
            buffer.writeText("Line3")

            val expected = "Line1\nLine2\nLine3"
            assertEquals(expected, buffer.getScreenAsString())
        }

        @Test
        fun `getAllAsString includes history`() {
            val buffer = TerminalBuffer1(5, 2)
            buffer.writeText("AAA")
            buffer.newLine()
            buffer.newLine()
            buffer.writeText("BBB")
            buffer.newLine()
            buffer.writeText("CCC")

            val content = buffer.getAllAsString()
            assertTrue(content.contains("AAA"))
            assertTrue(content.contains("BBB") || content.contains("CCC"))
        }

        @Test
        fun `getHistoryLineAsString returns history line`() {
            val buffer = TerminalBuffer1(10, 2)
            buffer.writeText("History")
            buffer.newLine()
            buffer.newLine()

            assertTrue(buffer.historySize > 0)
            assertEquals("History", buffer.getHistoryLineAsString(0))
        }
    }

    @Nested
    @DisplayName("Fill Operations")
    inner class FillTests {

        @Test
        fun `fillLine fills current line`() {
            val buffer = TerminalBuffer1(5, 3)
            buffer.setCursor(0, 1)
            buffer.fillLine('-'.code)

            for (col in 0 until 5) {
                assertEquals('-'.code, buffer.getLine(1).getCodepoint(col))
            }
            assertEquals(0, buffer.getLine(0).getCodepoint(0))
        }

        @Test
        fun `fillLineAt fills specific line`() {
            val buffer = TerminalBuffer1(5, 3)
            buffer.fillLineAt(2, '*'.code)

            for (col in 0 until 5) {
                assertEquals('*'.code, buffer.getLine(2).getCodepoint(col))
            }
            assertEquals(0, buffer.getLine(0).getCodepoint(0))
        }

        @Test
        fun `fillLine with 0 clears line`() {
            val buffer = TerminalBuffer1(5, 3)
            buffer.setCursor(0, 0)
            buffer.writeText("Hello")
            buffer.setCursor(0, 0)
            buffer.fillLine(0)

            for (col in 0 until 5) {
                assertEquals(0, buffer.getLine(0).getCodepoint(col))
            }
        }
    }

    @Nested
    @DisplayName("Clear All")
    inner class ClearAllTests {

        @Test
        fun `clearAll clears screen and history`() {
            val buffer = TerminalBuffer1(10, 2)
            buffer.writeText("Line1")
            buffer.newLine()
            buffer.newLine()
            buffer.writeText("Line2")

            buffer.clearAll()

            assertEquals(0, buffer.historySize)
            assertEquals(0, buffer.getLine(0).getCodepoint(0))
            assertEquals(0, buffer.cursorCol)
            assertEquals(0, buffer.cursorRow)
        }
    }
}