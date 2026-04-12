package com.gagik.terminal.buffer

import com.gagik.terminal.TerminalBuffers
import com.gagik.terminal.model.Attributes
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

@DisplayName("TerminalBuffer Test Suite")
class TerminalBufferTest {

	private fun newBuffer(width: Int = 4, height: Int = 3, maxHistory: Int = 5): TerminalBuffer {
		return TerminalBuffer(width, height, maxHistory)
	}

	private fun newApiBuffer(width: Int = 4, height: Int = 3, maxHistory: Int = 5): TerminalBufferApi {
		return TerminalBuffers.create(width, height, maxHistory)
	}

	private fun blankScreen(height: Int): String = List(height) { "" }.joinToString("\n")

	private fun defaultAttributes(): Attributes = Attributes(0, 0, bold = false, italic = false, underline = false)

	private fun assertCursor(buffer: TerminalBuffer, col: Int, row: Int) {
		assertAll(
			{ assertEquals(col, buffer.cursorCol, "Cursor column mismatch") },
			{ assertEquals(row, buffer.cursorRow, "Cursor row mismatch") }
		)
	}

	@Nested
	@DisplayName("Initialization & Validation")
	inner class InitializationTests {

		@ParameterizedTest(name = "Create buffer width={0}, height={1}, history={2}")
		@CsvSource(
			"1, 1, 0",
			"4, 3, 2",
			"8, 2, 0"
		)
		fun `creates a fully initialized blank buffer`(width: Int, height: Int, maxHistory: Int) {
			val buffer = TerminalBuffer(width, height, maxHistory)

			assertAll(
				{ assertEquals(width, buffer.width, "Width mismatch") },
				{ assertEquals(height, buffer.height, "Height mismatch") },
				{ assertEquals(0, buffer.cursorCol, "Cursor should start at column 0") },
				{ assertEquals(0, buffer.cursorRow, "Cursor should start at row 0") },
				{ assertEquals(0, buffer.historySize, "History should start empty") },
				{ assertEquals(blankScreen(height), buffer.getScreenAsString(), "Screen should start blank") },
				{ assertEquals(blankScreen(height), buffer.getAllAsString(), "Full buffer should start blank") }
			)
		}

		@Test
		fun `uses the default history size when maxHistory is omitted`() {
			val buffer = TerminalBuffer(3, 2)

			assertAll(
				{ assertEquals(3, buffer.width) },
				{ assertEquals(2, buffer.height) },
				{ assertEquals(0, buffer.historySize) },
				{ assertEquals(blankScreen(2), buffer.getScreenAsString()) },
				{ assertEquals(blankScreen(2), buffer.getAllAsString()) }
			)
		}

		@ParameterizedTest(name = "Reject invalid width={0}")
		@ValueSource(ints = [0, -1, -10])
		fun `rejects non-positive width`(invalidWidth: Int) {
			assertThrows<IllegalArgumentException> {
				TerminalBuffer(invalidWidth, 2)
			}
		}

		@ParameterizedTest(name = "Reject invalid height={0}")
		@ValueSource(ints = [0, -2, -99])
		fun `rejects non-positive height`(invalidHeight: Int) {
			assertThrows<IllegalArgumentException> {
				TerminalBuffer(2, invalidHeight)
			}
		}

		@Test
		fun `supports zero history without breaking initialization`() {
			val buffer = TerminalBuffer(3, 2, 0)

			assertAll(
				{ assertEquals(0, buffer.historySize) },
				{ assertEquals(blankScreen(2), buffer.getScreenAsString()) },
				{ assertEquals(blankScreen(2), buffer.getAllAsString()) }
			)
		}
	}

	@Nested
	@DisplayName("Cursor Movement")
	inner class CursorTests {

		@Test
		fun `setCursor clamps to visible bounds`() {
			val buffer = newBuffer(width = 5, height = 4)

			buffer.setCursor(-100, -100)
			assertCursor(buffer, 0, 0)

			buffer.setCursor(99, 99)
			assertCursor(buffer, 4, 3)
		}

		@Test
		fun `moveCursor is relative and clamps on both axes`() {
			val buffer = newBuffer(width = 5, height = 4)

			buffer.setCursor(2, 1)
			buffer.moveCursor(2, -1)
			assertCursor(buffer, 4, 0)

			buffer.moveCursor(-100, 100)
			assertCursor(buffer, 0, 3)
		}

		@Test
		fun `cursor helpers move in the expected direction and clamp`() {
			val buffer = newBuffer(width = 4, height = 3)

			buffer.setCursor(1, 1)
			buffer.cursorUp()
			assertCursor(buffer, 1, 0)

			buffer.cursorLeft()
			assertCursor(buffer, 0, 0)

			buffer.cursorDown()
			assertCursor(buffer, 0, 1)

			buffer.cursorRight()
			assertCursor(buffer, 1, 1)

			buffer.cursorUp(99)
			assertCursor(buffer, 1, 0)

			buffer.cursorLeft(99)
			assertCursor(buffer, 0, 0)
		}

		@Test
		fun `negative step values reverse direction instead of throwing`() {
			val buffer = newBuffer(width = 4, height = 4)

			buffer.setCursor(1, 1)
			buffer.cursorUp(-1)
			assertCursor(buffer, 1, 2)

			buffer.cursorLeft(-2)
			assertCursor(buffer, 3, 2)
		}

		@Test
		fun `resetCursor returns the cursor home`() {
			val buffer = newBuffer(width = 4, height = 4)

			buffer.setCursor(3, 2)
			buffer.resetCursor()

			assertCursor(buffer, 0, 0)
		}
	}

	@Nested
	@DisplayName("Pen Styling")
	inner class StylingTests {

		@Test
		fun `setPenAttributes applies to subsequent writes`() {
			val buffer = newBuffer()

			buffer.setPenAttributes(3, 7, bold = true, italic = true, underline = true)
			buffer.writeCodepoint('A'.code)

			assertAll(
				{ assertEquals('A'.code, buffer.getCodepointAt(0, 0)) },
				{ assertEquals(Attributes(3, 7, bold = true, italic = true, underline = true), buffer.getAttrAt(0, 0)) }
			)
		}

		@Test
		fun `setPenAttributes clamps out-of-range colors`() {
			val buffer = newBuffer()

			buffer.setPenAttributes(99, -4, bold = false, italic = true, underline = true)
			buffer.writeCodepoint('B'.code)

			assertEquals(Attributes(16, 0, bold = false, italic = true, underline = true), buffer.getAttrAt(0, 0))
		}

		@Test
		fun `resetPen affects only future writes`() {
			val buffer = newBuffer(width = 5, height = 2)

			buffer.setPenAttributes(5, 6, bold = true, italic = true, underline = true)
			buffer.writeCodepoint('A'.code)
			buffer.resetPen()
			buffer.writeCodepoint('B'.code)

			assertAll(
				{ assertEquals(Attributes(5, 6, true, true, true), buffer.getAttrAt(0, 0)) },
				{ assertEquals(defaultAttributes(), buffer.getAttrAt(1, 0)) }
			)
		}
	}

	@Nested
	@DisplayName("Writing & Line Feed")
	inner class WritingTests {

        @Test
        fun `writeCodepoint with non-printable characters`() {
            val buffer = newBuffer(width = 5, height = 1)
            // Writing a control character directly - it should be stored as-is in the cell
            buffer.writeCodepoint(7) // BEL
            assertEquals(7, buffer.getCodepointAt(0, 0))
            assertCursor(buffer, 1, 0)
        }

        @Test
        fun `writeText with carriage return and newline characters written literally`() {
            val buffer = newBuffer(width = 5, height = 2)
            // writeText documentation says it does NOT interpret \n or \r
            buffer.writeText("A\nB")

            // It should occupy 3 cells on the same line (if it doesn't wrap)
            assertEquals('A'.code, buffer.getCodepointAt(0, 0))
            assertEquals('\n'.code, buffer.getCodepointAt(1, 0))
            assertEquals('B'.code, buffer.getCodepointAt(2, 0))
            assertCursor(buffer, 3, 0)
        }

        @Test
        fun `newLine at the bottom with specific attributes fills new line with those attributes`() {
            val buffer = newBuffer(width = 4, height = 2, maxHistory = 1)
            buffer.setPenAttributes(fg = 2, bg = 3, bold = true)
            buffer.writeText("LINE1") // This will wrap, so cursor is at (1, 1)

            // Now we are at the bottom row (row 1). Call newLine()
            buffer.setPenAttributes(fg = 4, bg = 5, bold = false)
            buffer.newLine()

            // History should have the first line
            // Screen row 1 should be new and filled with current pen (4, 5, false)
            assertEquals(Attributes(4, 5, false, false, false), buffer.getAttrAt(0, 1))
        }

		@Test
		fun `writeCodepoint writes at the cursor and advances it`() {
			val buffer = newBuffer(width = 5, height = 3)

			buffer.setCursor(2, 1)
			buffer.setPenAttributes(2, 4, bold = true)
			buffer.writeCodepoint('X'.code)

			assertAll(
				{ assertEquals('X'.code, buffer.getCodepointAt(2, 1)) },
				{ assertEquals(Attributes(2, 4, bold = true, italic = false, underline = false), buffer.getAttrAt(2, 1)) },
				{ assertCursor(buffer, 3, 1) }
			)
		}

		@Test
		fun `writeText preserves supplementary code points`() {
			val buffer = newBuffer(width = 6, height = 2)
			val text = "A😀B"

			buffer.writeText(text)

			assertAll(
				{ assertEquals(text, buffer.getLineAsString(0)) },
				{ assertEquals(0x1F600, buffer.getCodepointAt(1, 0)) },
				{ assertCursor(buffer, 3, 0) }
			)
		}

		@Test
		fun `writeText with an empty string is a no-op`() {
			val buffer = newBuffer(width = 4, height = 2)

			buffer.writeText("Hi")
			val beforeScreen = buffer.getScreenAsString()
			val beforeAll = buffer.getAllAsString()
			val beforeCol = buffer.cursorCol
			val beforeRow = buffer.cursorRow

			buffer.writeText("")

			assertAll(
				{ assertEquals(beforeScreen, buffer.getScreenAsString()) },
				{ assertEquals(beforeAll, buffer.getAllAsString()) },
				{ assertEquals(beforeCol, buffer.cursorCol) },
				{ assertEquals(beforeRow, buffer.cursorRow) }
			)
		}

		@Test
		fun `writing wraps at the end of the line`() {
			val buffer = newBuffer(width = 3, height = 2)

			buffer.writeText("ABC")

			assertAll(
				{ assertEquals("ABC", buffer.getLineAsString(0)) },
				{ assertEquals("", buffer.getLineAsString(1)) },
				{ assertCursor(buffer, 0, 1) }
			)
		}

		@Test
		fun `line feed moves down without resetting the column`() {
			val buffer = newBuffer(width = 3, height = 3)

			buffer.setCursor(2, 0)
			buffer.newLine()

			assertCursor(buffer, 2, 1)
		}

		@Test
		fun `line feed scrolls when invoked on the bottom row`() {
			val buffer = newBuffer(width = 2, height = 2, maxHistory = 4)

			buffer.setPenAttributes(4, 5, bold = true)
			buffer.setCursor(1, 1)
			buffer.newLine()

			assertAll(
				{ assertEquals(1, buffer.historySize) },
				{ assertCursor(buffer, 1, 1) },
				{ assertEquals(Attributes(4, 5, true, false, false), buffer.getAttrAt(0, 1)) }
			)
		}

		@Test
		fun `writing past the bottom row scrolls and retains history`() {
			val buffer = newBuffer(width = 3, height = 2, maxHistory = 4)

			buffer.writeText("ABCDEF")

			assertAll(
				{ assertEquals(1, buffer.historySize) },
				{ assertEquals("DEF", buffer.getLineAsString(0)) },
				{ assertEquals("", buffer.getLineAsString(1)) },
				{ assertEquals("DEF\n", buffer.getScreenAsString()) },
				{ assertCursor(buffer, 0, 1) }
			)
		}

		@Test
		fun `writing supplementary characters at the line boundary does not split them`() {
			val buffer = newBuffer(width = 2, height = 2)
			// "A😀" -> 'A' is 1st cell, '😀' (U+1F600) is 2nd cell.
			// Next write should wrap.
			buffer.writeText("A😀B")

			assertAll(
				{ assertEquals("A😀", buffer.getLineAsString(0)) },
				{ assertEquals("B", buffer.getLineAsString(1)) },
				{ assertCursor(buffer, 1, 1) }
			)
		}

		@Test
		fun `wrapped lines correctly carry current attributes to the new line`() {
			val buffer = newBuffer(width = 2, height = 2)
			buffer.setPenAttributes(fg = 1, bg = 2, bold = true)

			// Write 2 chars to fill first line, then 1 more to trigger wrap
			buffer.writeText("ABC")

			assertAll(
				{ assertEquals(Attributes(1, 2, true, false, false), buffer.getAttrAt(0, 1)) },
				{ assertEquals('C'.code, buffer.getCodepointAt(0, 1)) }
			)
		}

		@Test
		fun `maxHistory zero keeps no scrollback`() {
			val buffer = newBuffer(width = 2, height = 2, maxHistory = 0)

			buffer.writeText("ABCD")

			assertAll(
				{ assertEquals(0, buffer.historySize) },
				{ assertEquals("CD", buffer.getLineAsString(0)) },
				{ assertEquals("", buffer.getLineAsString(1)) },
				{ assertEquals("CD\n", buffer.getScreenAsString()) },
				{ assertEquals("CD\n", buffer.getAllAsString()) }
			)
		}

        @Test
        fun `long string write triggers multiple wraps and scrolls`() {
            val buffer = newBuffer(width = 2, height = 2, maxHistory = 10)
            buffer.writeText("ABCDEFG")
            // AB (row 0) -> CD (row 1, scroll 1) -> EF (row 0, scroll 2) -> G (row 1)
            // Final screen: EF, G
            // History: AB, CD
            assertAll(
                { assertEquals(2, buffer.historySize) },
                { assertEquals("EF", buffer.getLineAsString(0)) },
                { assertEquals("G", buffer.getLineAsString(1)) },
                { assertCursor(buffer, 1, 1) }
            )
        }
	}

	@Nested
	@DisplayName("Editing Commands")
	inner class EditingTests {

		@Test
		fun `insertBlankCharacters shifts content and uses current attr for blanks`() {
			val buffer = newBuffer(width = 6, height = 2)

			buffer.writeText("ABCD")
			buffer.setCursor(1, 0)
			buffer.setPenAttributes(7, 8, bold = false, italic = true, underline = true)
			buffer.insertBlankCharacters(2)

			assertAll(
				{ assertEquals("A  BCD", buffer.getLineAsString(0)) },
				{ assertEquals(Attributes(7, 8, false, true, true), buffer.getAttrAt(1, 0)) },
				{ assertEquals(Attributes(7, 8, false, true, true), buffer.getAttrAt(2, 0)) },
				{ assertEquals('B'.code, buffer.getCodepointAt(3, 0)) },
				{ assertEquals('C'.code, buffer.getCodepointAt(4, 0)) },
				{ assertEquals('D'.code, buffer.getCodepointAt(5, 0)) }
			)
		}

		@Test
		fun `insertBlankCharacters ignores zero and negative counts`() {
			val buffer = newBuffer(width = 5, height = 2)

			buffer.writeText("AB")
			buffer.setCursor(1, 0)
			val beforeLine = buffer.getLineAsString(0)
			val beforeScreen = buffer.getScreenAsString()
			val beforeAll = buffer.getAllAsString()

			buffer.insertBlankCharacters(0)
			buffer.insertBlankCharacters(-3)

			assertAll(
				{ assertEquals(beforeLine, buffer.getLineAsString(0)) },
				{ assertEquals(beforeScreen, buffer.getScreenAsString()) },
				{ assertEquals(beforeAll, buffer.getAllAsString()) },
				{ assertCursor(buffer, 1, 0) }
			)
		}

		@Test
		fun `insertBlankCharacters at the very end of line`() {
			val buffer = newBuffer(width = 5, height = 1)
			buffer.writeText("ABCD")
			buffer.setCursor(4, 0)
			buffer.insertBlankCharacters(1)
			// "ABCD" + 1 space = "ABCD ".
			// But getLineAsString() trims trailing spaces if they are codepoint 0.
			// insertBlankCharacters inserts codepoint 0.
			assertEquals("ABCD", buffer.getLineAsString(0))
			assertCursor(buffer, 4, 0)
		}

        @Test
        fun `insertBlankCharacters more than remaining width`() {
            val buffer = newBuffer(width = 5, height = 2) // Increase height to be safe
            buffer.setCursor(0, 0)
            buffer.writeText("ABCDE")
            // width=5, writeText("ABCDE") fills row 0, cursor wraps to (0, 1)

            buffer.setCursor(2, 0)
            buffer.insertBlankCharacters(10) // should shift remaining 3 cells (C,D,E) out

            // Expected row 0: A B 0 0 0
            assertEquals("AB", buffer.getLineAsString(0))
            assertEquals('A'.code, buffer.getCodepointAt(0, 0))
            assertEquals('B'.code, buffer.getCodepointAt(1, 0))
            assertEquals(0, buffer.getCodepointAt(2, 0))
            assertEquals(0, buffer.getCodepointAt(3, 0))
            assertEquals(0, buffer.getCodepointAt(4, 0))
        }

		@Test
		fun `carriageReturn resets only the column`() {
			val buffer = newBuffer(width = 4, height = 3)

			buffer.setCursor(3, 2)
			buffer.carriageReturn()

			assertCursor(buffer, 0, 2)
		}

		@Test
		fun `eraseLineToEnd clears from the cursor to the end of the row`() {
			val buffer = newBuffer(width = 5, height = 2)

			buffer.writeText("ABCDE")
			buffer.setCursor(2, 0)
			buffer.setPenAttributes(1, 2, bold = true)
			buffer.eraseLineToEnd()

			assertAll(
				{ assertEquals("AB", buffer.getLineAsString(0)) },
				{ assertEquals(Attributes(1, 2, true, false, false), buffer.getAttrAt(2, 0)) },
				{ assertEquals(Attributes(1, 2, true, false, false), buffer.getAttrAt(4, 0)) }
			)
		}

		@Test
		fun `eraseLineToCursor clears from the beginning through the cursor`() {
			val buffer = newBuffer(width = 5, height = 2)

			buffer.writeText("ABCDE")
			buffer.setCursor(2, 0)
			buffer.setPenAttributes(3, 4, italic = true)
			buffer.eraseLineToCursor()

			assertAll(
				{ assertEquals("   DE", buffer.getLineAsString(0)) },
				{ assertEquals(Attributes(3, 4, false, true, false), buffer.getAttrAt(0, 0)) },
				{ assertEquals(Attributes(3, 4, false, true, false), buffer.getAttrAt(2, 0)) },
				{ assertEquals('D'.code, buffer.getCodepointAt(3, 0)) },
				{ assertEquals('E'.code, buffer.getCodepointAt(4, 0)) }
			)
		}

		@Test
		fun `eraseCurrentLine clears the entire active row`() {
			val buffer = newBuffer(width = 5, height = 2)

			buffer.writeText("ABCDE")
			buffer.setCursor(2, 0)
			buffer.setPenAttributes(4, 5, bold = true, underline = true)
			buffer.eraseCurrentLine()

			assertAll(
				{ assertEquals("", buffer.getLineAsString(0)) },
				{ assertEquals(Attributes(4, 5, bold = true, italic = false, underline = true), buffer.getAttrAt(0, 0)) },
				{ assertEquals(Attributes(4, 5, bold = true, italic = false, underline = true), buffer.getAttrAt(4, 0)) }
			)
		}
	}

	@Nested
	@DisplayName("Viewport & History")
	inner class ViewportTests {

		@Test
		fun `scrollUp with zero maxHistory still works but history remains zero`() {
			val buffer = TerminalBuffer(2, 2, maxHistory = 0)
			buffer.writeText("AB")
			buffer.scrollUp()

			assertAll(
				{ assertEquals(0, buffer.historySize) },
				{ assertEquals("\n", buffer.getScreenAsString()) },
				{ assertEquals("\n", buffer.getAllAsString()) }
			)
		}

		@Test
		fun `scrollUp adds a blank line and moves the old top line into history`() {
			val buffer = newBuffer(width = 2, height = 2, maxHistory = 4)

			buffer.setPenAttributes(6, 7, bold = true)
			buffer.writeText("AB")
			buffer.scrollUp()

			assertAll(
				{ assertEquals(1, buffer.historySize) },
				{ assertEquals("\n", buffer.getScreenAsString()) },
				{ assertCursor(buffer, 0, 1) },
				{ assertEquals(Attributes(6, 7, true, false, false), buffer.getAttrAt(0, 1)) }
			)
		}

		@Test
		fun `clearScreen preserves history and resets the cursor`() {
			val buffer = newBuffer(width = 2, height = 2, maxHistory = 4)

			buffer.writeText("ABCD")
			buffer.setPenAttributes(3, 4, bold = true)
			buffer.clearScreen()

			assertAll(
				{ assertEquals(1, buffer.historySize) },
				{ assertCursor(buffer, 0, 0) },
				{ assertEquals("\n", buffer.getScreenAsString()) },
				{ assertEquals(Attributes(3, 4, true, false, false), buffer.getAttrAt(0, 0)) },
				{ assertEquals(Attributes(3, 4, true, false, false), buffer.getAttrAt(1, 1)) }
			)
		}

		@Test
		fun `clearAll removes history and resets pen and cursor`() {
			val buffer = newBuffer(width = 2, height = 2, maxHistory = 2)

			buffer.setPenAttributes(8, 9, bold = true, italic = true, underline = true)
			buffer.writeText("ABCD")
			buffer.clearAll()

			assertAll(
				{ assertEquals(0, buffer.historySize) },
				{ assertCursor(buffer, 0, 0) },
				{ assertEquals(blankScreen(2), buffer.getScreenAsString()) },
				{ assertEquals(blankScreen(2), buffer.getAllAsString()) },
				{ assertEquals(defaultAttributes(), buffer.getAttrAt(0, 0)) },
				{ assertEquals(0, buffer.getPackedAttrAt(-1, 0)) }
			)

			buffer.writeCodepoint('Z'.code)
			assertEquals(defaultAttributes(), buffer.getAttrAt(0, 0))
		}

		@Test
		fun `reset leaves the buffer in the same observable state as a fresh instance`() {
			val dirty = newBuffer(width = 3, height = 2, maxHistory = 1)

			dirty.setPenAttributes(5, 6, bold = true)
			dirty.writeText("ABCDEF")
			dirty.reset()

			assertAll(
				{ assertEquals(0, dirty.historySize) },
				{ assertCursor(dirty, 0, 0) },
				{ assertEquals(blankScreen(2), dirty.getScreenAsString()) },
				{ assertEquals(blankScreen(2), dirty.getAllAsString()) },
				{ assertEquals(defaultAttributes(), dirty.getAttrAt(0, 0)) }
			)

			dirty.setPenAttributes(1, 1, underline = true)
			dirty.writeCodepoint('X'.code)
			dirty.reset()

			assertAll(
				{ assertEquals(0, dirty.historySize) },
				{ assertCursor(dirty, 0, 0) },
				{ assertEquals(blankScreen(2), dirty.getScreenAsString()) },
				{ assertEquals(blankScreen(2), dirty.getAllAsString()) },
				{ assertEquals(defaultAttributes(), dirty.getAttrAt(0, 0)) }
			)
		}
	}

	@Nested
	@DisplayName("Resize")
	inner class ResizeTests {

		@Test
		fun `resize is exposed through the public API`() {
			val buffer = newApiBuffer(width = 4, height = 2, maxHistory = 3)
			buffer.writeText("ABCD")

			buffer.resize(newWidth = 2, newHeight = 3)

			assertAll(
				{ assertEquals(2, buffer.width) },
				{ assertEquals(3, buffer.height) },
				{ assertTrue(buffer.cursorCol in 0 until buffer.width) },
				{ assertTrue(buffer.cursorRow in 0 until buffer.height) }
			)
		}

		@Test
		fun `resize rejects non-positive dimensions through the public API`() {
			val buffer = newApiBuffer()

			assertThrows<IllegalArgumentException> { buffer.resize(0, 2) }
			assertThrows<IllegalArgumentException> { buffer.resize(2, 0) }
			assertThrows<IllegalArgumentException> { buffer.resize(-1, 2) }
			assertThrows<IllegalArgumentException> { buffer.resize(2, -1) }
		}
	}

	@Nested
	@DisplayName("Rendering & Query API")
	inner class RenderingTests {

		@Test
		fun `getLine provides a read-only view of a row`() {
			val buffer = newBuffer(width = 5, height = 2)
			buffer.writeText("HELLO")
			buffer.setPenAttributes(fg = 1, bg = 2, bold = true)

			val line = buffer.getLine(0)
			// No longer null-safe check needed as it returns VoidLine instead of null
			assertEquals(5, line.width)
			assertEquals('H'.code, line.getCodepoint(0))
			assertEquals('O'.code, line.getCodepoint(4))

			val line2 = buffer.getLine(1)
			assertEquals(0, line2.getCodepoint(0))
		}

		@Test
		fun `getLine returns a VoidLine for out of bounds rows`() {
			val buffer = newBuffer(width = 5, height = 2)

			// VoidLine has width 0 and returns 0 for all queries
			val outOfBounds1 = buffer.getLine(-1)
			assertEquals(0, outOfBounds1.width)
			assertEquals(0, outOfBounds1.getCodepoint(0))
			assertEquals(0, outOfBounds1.getPackedAttr(0))

			val outOfBounds2 = buffer.getLine(2)
			assertEquals(0, outOfBounds2.width)

			val outOfBounds3 = buffer.getLine(99)
			assertEquals(0, outOfBounds3.width)
		}

		@Test
		fun `getCodepointAt getPackedAttrAt and getAttrAt honor valid and invalid coordinates`() {
			val buffer = newBuffer(width = 4, height = 2)

			buffer.setPenAttributes(2, 3, bold = true, underline = true)
			buffer.writeCodepoint('Q'.code)
			val expectedAttr = buffer.getPackedAttrAt(0, 0)

			assertAll(
				{ assertEquals('Q'.code, buffer.getCodepointAt(0, 0)) },
				{ assertEquals(expectedAttr, buffer.getPackedAttrAt(0, 0)) },
				{ assertEquals(Attributes(2, 3, true, false, true), buffer.getAttrAt(0, 0)) },
				{ assertEquals(0, buffer.getCodepointAt(-1, 0)) },
				{ assertEquals(0, buffer.getCodepointAt(0, -1)) },
				{ assertEquals(expectedAttr, buffer.getPackedAttrAt(-1, 0)) },
				{ assertEquals(expectedAttr, buffer.getPackedAttrAt(0, 99)) },
				{ assertNull(buffer.getAttrAt(-1, 0)) },
				{ assertNull(buffer.getAttrAt(99, 99)) }
			)
		}

		@Test
		fun `getLineAsString trims trailing empty cells but preserves leading and internal spaces`() {
			val buffer = newBuffer(width = 10, height = 1)
			// ' ' (32) is a space, 0 is an empty cell.
			// writeText("  A  ") will write Codepoint 32, 32, 65, 32, 32.
			// Remaining 5 cells are 0.
			buffer.writeText("  A  ")

			// toTextTrimmed() should find the last non-0 cell at index 4 (the last space from "  A  ")
			// and return "  A  ".
			assertEquals("  A  ", buffer.getLineAsString(0))

			buffer.clearScreen()
			buffer.writeText("ABC")
			// Line is [65, 66, 67, 0, 0, 0, 0, 0, 0, 0]
			assertEquals("ABC", buffer.getLineAsString(0))
		}

		@Test
		fun `getLineAsString with only empty cells returns empty string`() {
			val buffer = newBuffer(width = 5, height = 1)
			// All cells are 0.
			assertEquals("", buffer.getLineAsString(0))
		}

		@Test
		fun `getLineAsString with only spaces returns spaces`() {
			val buffer = newBuffer(width = 5, height = 1)
			buffer.writeText("   ")
			// Line is [32, 32, 32, 0, 0]
			assertEquals("   ", buffer.getLineAsString(0))
		}

		@Test
		fun `getScreenAsString joins visible rows with newlines`() {
			val buffer = newBuffer(width = 2, height = 3, maxHistory = 0)

			buffer.writeText("ABCD")

			assertAll(
				{ assertEquals("AB\nCD\n", buffer.getScreenAsString()) },
				{ assertEquals("AB\nCD\n", buffer.getAllAsString()) }
			)
		}

		@Test
		fun `getAllAsString includes scrollback before the visible screen`() {
			val buffer = newBuffer(width = 2, height = 2, maxHistory = 2)

			buffer.writeText("ABCDEF")

			assertAll(
				{ assertEquals(2, buffer.historySize) },
				{ assertEquals("EF\n", buffer.getScreenAsString()) },
				{ assertEquals("AB\nCD\nEF\n", buffer.getAllAsString()) }
			)
		}
	}
}

