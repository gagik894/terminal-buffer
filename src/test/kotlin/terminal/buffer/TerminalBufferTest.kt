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
                    assertEquals(0, buffer.getCharAt(col, row))
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

            assertEquals('A'.code, buffer.getCharAt(0, 0))
            assertEquals(1, buffer.cursorCol)
        }

        @Test
        fun `writes multiple characters sequentially`() {
            val buffer = TerminalBuffer(10, 5)
            buffer.writeChar('A'.code)
            buffer.writeChar('B'.code)
            buffer.writeChar('C'.code)

            assertEquals('A'.code, buffer.getCharAt(0, 0))
            assertEquals('B'.code, buffer.getCharAt(1, 0))
            assertEquals('C'.code, buffer.getCharAt(2, 0))
            assertEquals(3, buffer.cursorCol)
        }

        @Test
        fun `uses current pen attributes`() {
            val buffer = TerminalBuffer(10, 5)
            buffer.setAttributes(fg = 5, bg = 10, bold = true)
            buffer.writeChar('A'.code)

            val expected = AttributeCodec.pack(5, 10, bold = true, italic = false, underline = false)
            assertEquals(expected, buffer.getAttrAt(0, 0))
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

            assertEquals('D'.code, buffer.getCharAt(0, 1))
            assertEquals(1, buffer.cursorCol)
            assertEquals(1, buffer.cursorRow)
        }

        @Test
        fun `wrapping preserves content`() {
            val buffer = TerminalBuffer(3, 5)
            buffer.writeText("ABCDEF")

            assertEquals('A'.code, buffer.getCharAt(0, 0))
            assertEquals('B'.code, buffer.getCharAt(1, 0))
            assertEquals('C'.code, buffer.getCharAt(2, 0))
            assertEquals('D'.code, buffer.getCharAt(0, 1))
            assertEquals('E'.code, buffer.getCharAt(1, 1))
            assertEquals('F'.code, buffer.getCharAt(2, 1))
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

            assertEquals('A'.code, buffer.getHistoryCharAt(0, 0))
            assertEquals('D'.code, buffer.getCharAt(0, 0))
            assertEquals('G'.code, buffer.getCharAt(0, 1))
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

            assertEquals('H'.code, buffer.getCharAt(0, 0))
            assertEquals('e'.code, buffer.getCharAt(1, 0))
            assertEquals('l'.code, buffer.getCharAt(2, 0))
            assertEquals('l'.code, buffer.getCharAt(3, 0))
            assertEquals('o'.code, buffer.getCharAt(4, 0))
        }

        @Test
        fun `empty string does nothing`() {
            val buffer = TerminalBuffer(10, 5)
            buffer.writeText("")

            assertEquals(0, buffer.cursorCol)
            assertEquals(0, buffer.cursorRow)
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
                assertEquals(0, buffer.getCharAt(0, row))
            }
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

            assertEquals(0, buffer.getCharAt(0, 0))
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
            assertEquals(defaultAttr, buffer.getAttrAt(0, 0))
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

            assertEquals('D'.code, buffer.getCharAt(0, 0))
            assertEquals('G'.code, buffer.getCharAt(0, 1))
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

            assertEquals('T'.code, buffer.getCharAt(0, 0))
            assertEquals('M'.code, buffer.getCharAt(5, 1))
            assertEquals('B'.code, buffer.getCharAt(0, 2))
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
            val buffer = TerminalBuffer(10, 5)
            buffer.writeText("Hello")

            assertEquals('H'.code, buffer.getCharAt(0, 0))
            assertEquals('e'.code, buffer.getCharAt(1, 0))
            assertEquals('o'.code, buffer.getCharAt(4, 0))
        }

        @Test
        fun `getCharAt returns null for out of bounds`() {
            val buffer = TerminalBuffer(10, 5)
            buffer.writeText("Hello")

            assertNull(buffer.getCharAt(-1, 0))
            assertNull(buffer.getCharAt(100, 0))
            assertNull(buffer.getCharAt(0, 100))
        }

        @Test
        fun `getAttrAt returns attributes at position`() {
            val buffer = TerminalBuffer(10, 5)
            buffer.setAttributes(5, 3, bold = true)
            buffer.writeChar('X'.code)

            val attr = buffer.getAttrAt(0, 0)
            assertNotNull(attr)
            val expected = AttributeCodec.pack(5, 3, bold = true, italic = false, underline = false)
            assertEquals(expected, attr)
        }

        @Test
        fun `getLineAsString returns line content`() {
            val buffer = TerminalBuffer(10, 5)
            buffer.writeText("Hello")

            assertEquals("Hello", buffer.getLineAsString(0))
        }

        @Test
        fun `getScreenAsString returns all visible lines`() {
            val buffer = TerminalBuffer(10, 3)
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
            val buffer = TerminalBuffer(5, 2)
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
            val buffer = TerminalBuffer(10, 2)
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
            val buffer = TerminalBuffer(5, 3)
            buffer.setCursor(0, 1)
            buffer.fillLine('-'.code)

            for (col in 0 until 5) {
                assertEquals('-'.code, buffer.getCharAt(col, 1))
            }
            assertEquals(0, buffer.getCharAt(0, 0))
        }

        @Test
        fun `fillLineAt fills specific line`() {
            val buffer = TerminalBuffer(5, 3)
            buffer.fillLineAt(2, '*'.code)

            for (col in 0 until 5) {
                assertEquals('*'.code, buffer.getCharAt(col, 2))
            }
            assertEquals(0, buffer.getCharAt(0, 0))
        }

        @Test
        fun `fillLine with 0 clears line`() {
            val buffer = TerminalBuffer(5, 3)
            buffer.setCursor(0, 0)
            buffer.writeText("Hello")
            buffer.setCursor(0, 0)
            buffer.fillLine(0)

            for (col in 0 until 5) {
                assertEquals(0, buffer.getCharAt(col, 0))
            }
        }
    }

    @Nested
    @DisplayName("Clear All")
    inner class ClearAllTests {

        @Test
        fun `clearAll clears screen and history`() {
            val buffer = TerminalBuffer(10, 2)
            buffer.writeText("Line1")
            buffer.newLine()
            buffer.newLine()
            buffer.writeText("Line2")

            buffer.clearAll()

            assertEquals(0, buffer.historySize)
            assertEquals(0, buffer.getCharAt(0, 0))
            assertEquals(0, buffer.cursorCol)
            assertEquals(0, buffer.cursorRow)
        }
    }
}