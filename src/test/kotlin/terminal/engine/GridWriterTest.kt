package com.gagik.terminal.engine

import com.gagik.terminal.model.Line
import com.gagik.terminal.model.TerminalConstants
import com.gagik.terminal.state.TerminalState
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("GridWriter Test Suite")
class GridWriterTest {

    private fun createState(width: Int = 5, height: Int = 2, history: Int = 2): TerminalState {
        return TerminalState(width, height, maxHistory = history)
    }

    private fun lineAt(state: TerminalState, row: Int): Line {
        val top = (state.ring.size - state.dimensions.height).coerceAtLeast(0)
        return state.ring[top + row]
    }

    private fun writeAscii(writer: GridWriter, text: String) {
        for (ch in text) {
            writer.printCodepoint(ch.code, 1)
        }
    }

    @Nested
    @DisplayName("printCodepoint")
    inner class PrintCodepointTests {

        @Test
        fun `fast path appends single-width into empty cell`() {
            val state = createState(width = 4, height = 1)
            val writer = GridWriter(state)

            writer.printCodepoint('A'.code, charWidth = 1)

            assertAll(
                { assertEquals('A'.code, lineAt(state, 0).getCodepoint(0)) },
                { assertEquals(1, state.cursor.col) },
                { assertEquals(0, state.cursor.row) }
            )
        }

        @Test
        fun `non-standard width value is treated as single-width`() {
            val state = createState(width = 4, height = 1)
            val writer = GridWriter(state)

            writer.printCodepoint('Z'.code, charWidth = 3)

            assertAll(
                { assertEquals('Z'.code, lineAt(state, 0).getCodepoint(0)) },
                { assertEquals(1, state.cursor.col) },
                { assertEquals(0, state.cursor.row) }
            )
        }

        @Test
        fun `overwrite on leader cell replaces only that cell`() {
            val state = createState(width = 4, height = 1)
            val writer = GridWriter(state)
            writeAscii(writer, "AB")
            state.cursor.col = 0

            writer.printCodepoint('X'.code, charWidth = 1)

            assertAll(
                { assertEquals('X'.code, lineAt(state, 0).getCodepoint(0)) },
                { assertEquals('B'.code, lineAt(state, 0).getCodepoint(1)) },
                { assertEquals(1, state.cursor.col) }
            )
        }

        @Test
        fun `writes width-2 cluster as leader plus spacer`() {
            val state = createState(width = 4, height = 1)
            val writer = GridWriter(state)

            writer.printCodepoint(0x1F600, charWidth = 2)

            assertAll(
                { assertEquals(0x1F600, lineAt(state, 0).getCodepoint(0)) },
                { assertEquals(TerminalConstants.WIDE_CHAR_SPACER, lineAt(state, 0).getCodepoint(1)) },
                { assertEquals(2, state.cursor.col) },
                { assertEquals(0, state.cursor.row) }
            )
        }

        @Test
        fun `overwrite while cursor is on spacer resolves to cluster owner`() {
            val state = createState(width = 4, height = 1)
            val writer = GridWriter(state)

            writer.printCodepoint(0x1F600, charWidth = 2)
            state.cursor.col = 1

            writer.printCodepoint('A'.code, charWidth = 1)

            assertAll(
                { assertEquals('A'.code, lineAt(state, 0).getCodepoint(0)) },
                { assertEquals(TerminalConstants.EMPTY, lineAt(state, 0).getCodepoint(1)) },
                { assertEquals(1, state.cursor.col) }
            )
        }

        @Test
        fun `single-width write at end wraps and marks line wrapped`() {
            val state = createState(width = 2, height = 2)
            val writer = GridWriter(state)

            writer.printCodepoint('A'.code, 1)
            writer.printCodepoint('B'.code, 1)

            assertAll(
                { assertTrue(lineAt(state, 0).wrapped) },
                { assertEquals(0, state.cursor.col) },
                { assertEquals(1, state.cursor.row) }
            )
        }

        @Test
        fun `width-2 on last column wraps before write`() {
            val state = createState(width = 3, height = 2)
            val writer = GridWriter(state)
            writeAscii(writer, "AB")

            writer.printCodepoint(0x1F601, 2)

            assertAll(
                { assertEquals('A'.code, lineAt(state, 0).getCodepoint(0)) },
                { assertEquals(TerminalConstants.EMPTY, lineAt(state, 0).getCodepoint(2)) },
                { assertEquals(0x1F601, lineAt(state, 1).getCodepoint(0)) },
                { assertEquals(TerminalConstants.WIDE_CHAR_SPACER, lineAt(state, 1).getCodepoint(1)) },
                { assertEquals(2, state.cursor.col) },
                { assertEquals(1, state.cursor.row) }
            )
        }

        @Test
        fun `width-2 at last column on bottom row scrolls then writes`() {
            val state = createState(width = 3, height = 1, history = 4)
            val writer = GridWriter(state)
            writeAscii(writer, "AB")

            writer.printCodepoint(0x1F602, 2)

            assertAll(
                { assertTrue(state.ring.size >= 2) },
                { assertEquals(0x1F602, lineAt(state, 0).getCodepoint(0)) },
                { assertEquals(TerminalConstants.WIDE_CHAR_SPACER, lineAt(state, 0).getCodepoint(1)) },
                { assertEquals(2, state.cursor.col) },
                { assertEquals(0, state.cursor.row) }
            )
        }

        @Test
        fun `out-of-bounds cursor is ignored without mutation`() {
            val state = createState(width = 3, height = 2)
            val writer = GridWriter(state)
            writeAscii(writer, "ABC")
            state.cursor.col = 99

            writer.printCodepoint('Z'.code, 1)

            assertAll(
                { assertEquals('A'.code, lineAt(state, 0).getCodepoint(0)) },
                { assertEquals('B'.code, lineAt(state, 0).getCodepoint(1)) },
                { assertEquals('C'.code, lineAt(state, 0).getCodepoint(2)) },
                { assertEquals(99, state.cursor.col) }
            )
        }
    }

    @Nested
    @DisplayName("erase operations")
    inner class EraseTests {

        @Test
        fun `eraseLineToEnd on spacer annihilates owning cluster`() {
            val state = createState(width = 4, height = 1)
            val writer = GridWriter(state)

            writer.printCodepoint(0x1F600, 2)
            writer.printCodepoint('B'.code, 1)
            state.cursor.col = 1

            writer.eraseLineToEnd()

            assertAll(
                { assertEquals(TerminalConstants.EMPTY, lineAt(state, 0).getCodepoint(0)) },
                { assertEquals(TerminalConstants.EMPTY, lineAt(state, 0).getCodepoint(1)) },
                { assertEquals(TerminalConstants.EMPTY, lineAt(state, 0).getCodepoint(2)) },
                { assertEquals(TerminalConstants.EMPTY, lineAt(state, 0).getCodepoint(3)) }
            )
        }

        @Test
        fun `eraseLineToCursor on spacer clears owning cluster and prefix`() {
            val state = createState(width = 5, height = 1)
            val writer = GridWriter(state)

            writer.printCodepoint('A'.code, 1)
            writer.printCodepoint(0x1F600, 2)
            writer.printCodepoint('Z'.code, 1)
            state.cursor.col = 2

            writer.eraseLineToCursor()

            assertAll(
                { assertEquals(TerminalConstants.EMPTY, lineAt(state, 0).getCodepoint(0)) },
                { assertEquals(TerminalConstants.EMPTY, lineAt(state, 0).getCodepoint(1)) },
                { assertEquals(TerminalConstants.EMPTY, lineAt(state, 0).getCodepoint(2)) },
                { assertEquals('Z'.code, lineAt(state, 0).getCodepoint(3)) }
            )
        }

        @Test
        fun `eraseCurrentLine clears the whole active row`() {
            val state = createState(width = 4, height = 2)
            val writer = GridWriter(state)
            writeAscii(writer, "ABCD")
            state.cursor.row = 0

            writer.eraseCurrentLine()

            for (col in 0 until 4) {
                assertEquals(TerminalConstants.EMPTY, lineAt(state, 0).getCodepoint(col))
            }
        }
    }

    @Nested
    @DisplayName("insertBlankCharacters")
    inner class InsertBlankCharactersTests {

        @Test
        fun `non-positive count is no-op`() {
            val state = createState(width = 5, height = 2)
            val writer = GridWriter(state)
            writeAscii(writer, "ABCDE")
            state.cursor.row = 0
            state.cursor.col = 2

            writer.insertBlankCharacters(0)
            writer.insertBlankCharacters(-1)

            assertAll(
                { assertEquals('A'.code, lineAt(state, 0).getCodepoint(0)) },
                { assertEquals('B'.code, lineAt(state, 0).getCodepoint(1)) },
                { assertEquals('C'.code, lineAt(state, 0).getCodepoint(2)) },
                { assertEquals('D'.code, lineAt(state, 0).getCodepoint(3)) },
                { assertEquals('E'.code, lineAt(state, 0).getCodepoint(4)) }
            )
        }

        @Test
        fun `insert on normal cell shifts content to the right`() {
            val state = createState(width = 6, height = 1)
            val writer = GridWriter(state)
            writeAscii(writer, "ABCD")
            state.cursor.col = 1

            writer.insertBlankCharacters(2)

            assertAll(
                { assertEquals('A'.code, lineAt(state, 0).getCodepoint(0)) },
                { assertEquals(TerminalConstants.EMPTY, lineAt(state, 0).getCodepoint(1)) },
                { assertEquals(TerminalConstants.EMPTY, lineAt(state, 0).getCodepoint(2)) },
                { assertEquals('B'.code, lineAt(state, 0).getCodepoint(3)) },
                { assertEquals('C'.code, lineAt(state, 0).getCodepoint(4)) },
                { assertEquals('D'.code, lineAt(state, 0).getCodepoint(5)) }
            )
        }

        @Test
        fun `insert on spacer annihilates owner then shifts remainder`() {
            val state = createState(width = 5, height = 1)
            val writer = GridWriter(state)

            writer.printCodepoint(0x1F600, 2)
            writer.printCodepoint('C'.code, 1)
            writer.printCodepoint('D'.code, 1)
            state.cursor.col = 1

            writer.insertBlankCharacters(1)

            assertAll(
                { assertEquals(TerminalConstants.EMPTY, lineAt(state, 0).getCodepoint(0)) },
                { assertEquals(TerminalConstants.EMPTY, lineAt(state, 0).getCodepoint(1)) },
                { assertEquals(TerminalConstants.EMPTY, lineAt(state, 0).getCodepoint(2)) },
                { assertEquals('C'.code, lineAt(state, 0).getCodepoint(3)) },
                { assertEquals('D'.code, lineAt(state, 0).getCodepoint(4)) }
            )
        }
    }
}
