package com.gagik.terminal.buffer

import com.gagik.terminal.model.Attributes
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
                    assertEquals(null, buffer.getCharAt(col, row))
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
            buffer.writeChar('A')

            assertEquals('A', buffer.getCharAt(0, 0))
            assertEquals(1, buffer.cursorCol)
        }

        @Test
        fun `writes multiple characters sequentially`() {
            val buffer = TerminalBuffer(10, 5)
            buffer.writeChar('A')
            buffer.writeChar('B')
            buffer.writeChar('C')

            assertEquals('A', buffer.getCharAt(0, 0))
            assertEquals('B', buffer.getCharAt(1, 0))
            assertEquals('C', buffer.getCharAt(2, 0))
            assertEquals(3, buffer.cursorCol)
        }

        @Test
        fun `uses current pen attributes`() {
            val buffer = TerminalBuffer(10, 5)
            buffer.setAttributes(Attributes(fg = 5, bg = 10, bold = true, italic = false, underline = false))
            buffer.writeChar('A')

            val expected = Attributes(fg = 5, bg = 10, bold = true, italic = false, underline = false)
            assertEquals(expected, buffer.getAttrAt(0, 0))
        }
    }

    @Nested
    @DisplayName("writeChar() - Line Wrapping")
    inner class WriteCharWrappingTests {

        @Test
        fun `wraps to next line at end of row`() {
            val buffer = TerminalBuffer(3, 5)
            buffer.writeChar('A')
            buffer.writeChar('B')
            buffer.writeChar('C')  // Fill line
            buffer.writeChar('D')  // Should wrap

            assertEquals('D', buffer.getCharAt(0, 1))
            assertEquals(1, buffer.cursorCol)
            assertEquals(1, buffer.cursorRow)
        }

        @Test
        fun `wrapping preserves content`() {
            val buffer = TerminalBuffer(3, 5)
            buffer.writeText("ABCDEF")

            assertEquals('A', buffer.getCharAt(0, 0))
            assertEquals('B', buffer.getCharAt(1, 0))
            assertEquals('C', buffer.getCharAt(2, 0))
            assertEquals('D', buffer.getCharAt(0, 1))
            assertEquals('E', buffer.getCharAt(1, 1))
            assertEquals('F', buffer.getCharAt(2, 1))
        }
    }

    @Nested
    @DisplayName("writeChar() - Scrolling")
    inner class WriteCharScrollingTests {

        @Test
        fun `scrolls when wrapping past bottom`() {
            val buffer = TerminalBuffer(2, 2)
            buffer.writeText("ABCD")  // Fill screen
            buffer.writeChar('E')  // Should scroll

            assertEquals(1, buffer.historySize)
            assertEquals(1, buffer.cursorRow)
        }

        @Test
        fun `scrolled content moves to history`() {
            val buffer = TerminalBuffer(3, 2)
            buffer.writeText("ABC")  // Line 0, wraps
            buffer.writeText("DEF")  // Line 1, wraps
            buffer.writeChar('G')  // Scroll happens

            assertEquals('A', buffer.getHistoryCharAt(0, 0))
            assertEquals('D', buffer.getCharAt(0, 0))
            assertEquals('G', buffer.getCharAt(0, 1))
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

            assertEquals('H', buffer.getCharAt(0, 0))
            assertEquals('e', buffer.getCharAt(1, 0))
            assertEquals('l', buffer.getCharAt(2, 0))
            assertEquals('l', buffer.getCharAt(3, 0))
            assertEquals('o', buffer.getCharAt(4, 0))
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
            buffer.writeChar('A')
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
                assertEquals(null, buffer.getCharAt(0, row))
            }
        }
    }

    @Nested
    @DisplayName("reset()")
    inner class ResetTests {

        @Test
        fun `clears screen`() {
            val buffer = TerminalBuffer(10, 3)
            buffer.writeText("Test")
            buffer.reset()

            assertEquals(null, buffer.getCharAt(0, 0))
        }

        @Test
        fun `resets pen`() {
            val buffer = TerminalBuffer(10, 5)
            buffer.setAttributes(Attributes(fg = 10, bg = 5, bold = true, italic = false, underline = false))
            buffer.reset()
            buffer.writeChar('A')

            val defaultAttr = Attributes(fg = 0, bg = 0, bold = false, italic = false, underline = false)
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
            buffer.writeChar('G')

            assertEquals('D', buffer.getCharAt(0, 0))
            assertEquals('G', buffer.getCharAt(0, 1))
            assertTrue(buffer.historySize > 0)
        }

        @Test
        fun `mixed operations preserve correctness`() {
            val buffer = TerminalBuffer(10, 3)
            buffer.writeText("Top")
            buffer.setCursor(0, 2)
            buffer.writeText("Bottom")
            buffer.setCursor(5, 1)
            buffer.writeChar('M')

            assertEquals('T', buffer.getCharAt(0, 0))
            assertEquals('M', buffer.getCharAt(5, 1))
            assertEquals('B', buffer.getCharAt(0, 2))
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

            assertEquals('H', buffer.getCharAt(0, 0))
            assertEquals('e', buffer.getCharAt(1, 0))
            assertEquals('o', buffer.getCharAt(4, 0))
        }

        @Test
        fun `getAttrAt returns attributes at position`() {
            val buffer = TerminalBuffer(10, 5)
            buffer.setAttributes(Attributes(fg = 5, bg = 3, bold = true, italic = false, underline = false))
            buffer.writeChar('X')

            val attr = buffer.getAttrAt(0, 0)
            assertNotNull(attr)
            val expected = Attributes(fg = 5, bg = 3, bold = true, italic = false, underline = false)
            assertEquals(expected, attr)
        }
    }

    @Nested
    @DisplayName("Fill Operations")
    inner class FillTests {

        @Test
        fun `fillLine fills current line`() {
            val buffer = TerminalBuffer(5, 3)
            buffer.setCursor(0, 1)
            buffer.fillLine('-')

            for (col in 0 until 5) {
                assertEquals('-', buffer.getCharAt(col, 1))
            }
            assertEquals(null, buffer.getCharAt(0, 0))
        }

        @Test
        fun `fillLineAt fills specific line`() {
            val buffer = TerminalBuffer(5, 3)
            buffer.fillLineAt(2, '*')

            for (col in 0 until 5) {
                assertEquals('*', buffer.getCharAt(col, 2))
            }
            assertEquals(null, buffer.getCharAt(0, 0))
        }

        @Test
        fun `fillLine with null clears line`() {
            val buffer = TerminalBuffer(5, 3)
            buffer.setCursor(0, 0)
            buffer.writeText("Hello")
            buffer.setCursor(0, 0)
            buffer.fillLine(null)

            for (col in 0 until 5) {
                assertEquals(null, buffer.getCharAt(col, 0))
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
            assertEquals(null, buffer.getCharAt(0, 0))
            assertEquals(0, buffer.cursorCol)
            assertEquals(0, buffer.cursorRow)
        }
    }
}
