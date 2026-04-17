package com.gagik.terminal.engine

import com.gagik.terminal.model.Line
import com.gagik.terminal.model.TerminalConstants
import com.gagik.terminal.state.TerminalState
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("GridWriter Test Suite")
class MutationEngineTest {

    private fun createState(width: Int = 5, height: Int = 2, history: Int = 2): TerminalState {
        return TerminalState(width, height, maxHistory = history)
    }

    private fun lineAt(state: TerminalState, row: Int): Line {
        val top = (state.ring.size - state.dimensions.height).coerceAtLeast(0)
        return state.ring[top + row]
    }

    private fun writeAscii(writer: MutationEngine, text: String) {
        for (ch in text) {
            writer.printCodepoint(ch.code, 1)
        }
    }

    private fun assertLineCodepoints(state: TerminalState, row: Int, expected: IntArray) {
        val line = lineAt(state, row)
        for (i in expected.indices) {
            assertEquals(expected[i], line.getCodepoint(i), "Codepoint mismatch at row=$row col=$i")
        }
    }

    private fun assertLineAttrs(state: TerminalState, row: Int, expected: IntArray) {
        val line = lineAt(state, row)
        for (i in expected.indices) {
            assertEquals(expected[i], line.getPackedAttr(i), "Attr mismatch at row=$row col=$i")
        }
    }

    private fun seedLine(state: TerminalState, row: Int, text: String, attr: Int = 0) {
        val line = lineAt(state, row)
        for ((i, ch) in text.withIndex()) {
            if (i >= line.width) break
            line.setCell(i, ch.code, attr)
        }
    }

    @Nested
    @DisplayName("printCodepoint")
    inner class PrintCodepointTests {

        @Test
        fun `fast path appends single-width into empty cell`() {
            val state = createState(width = 4, height = 1)
            val writer = MutationEngine(state)

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
            val writer = MutationEngine(state)

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
            val writer = MutationEngine(state)
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
            val writer = MutationEngine(state)

            writer.printCodepoint(0x1F600, charWidth = 2)

            assertAll(
                { assertEquals(0x1F600, lineAt(state, 0).getCodepoint(0)) },
                { assertEquals(TerminalConstants.WIDE_CHAR_SPACER, lineAt(state, 0).getCodepoint(1)) },
                { assertEquals(2, state.cursor.col) },
                { assertEquals(0, state.cursor.row) }
            )
        }

        @Test
        fun `overwrite while cursor is on spacer clears cluster and writes at cursor column`() {
            val state = createState(width = 4, height = 1)
            val writer = MutationEngine(state)

            writer.printCodepoint(0x1F600, charWidth = 2)
            state.cursor.col = 1

            writer.printCodepoint('A'.code, charWidth = 1)

            assertAll(
                { assertEquals(TerminalConstants.EMPTY, lineAt(state, 0).getCodepoint(0)) },
                { assertEquals('A'.code, lineAt(state, 0).getCodepoint(1)) },
                { assertEquals(2, state.cursor.col) }
            )
        }

        @Test
        fun `overlap crush wide on spacer annihilates old cluster and prevents leader-leader`() {
            val state = createState(width = 4, height = 1)
            val writer = MutationEngine(state)

            writer.printCodepoint(0x1F600, charWidth = 2)
            state.cursor.col = 1

            writer.printCodepoint(0x1F923, charWidth = 2)

            assertAll(
                { assertEquals(TerminalConstants.EMPTY, lineAt(state, 0).getCodepoint(0)) },
                { assertEquals(0x1F923, lineAt(state, 0).getCodepoint(1)) },
                { assertEquals(TerminalConstants.WIDE_CHAR_SPACER, lineAt(state, 0).getCodepoint(2)) },
                { assertEquals(TerminalConstants.EMPTY, lineAt(state, 0).getCodepoint(3)) },
                { assertEquals(3, state.cursor.col) },
                { assertEquals(0, state.cursor.row) }
            )
        }

        @Test
        fun `super crush wide overlap annihilates both neighboring clusters`() {
            val state = createState(width = 4, height = 1)
            val writer = MutationEngine(state)

            writer.printCodepoint(0x1F600, charWidth = 2) // A at 0-1
            writer.printCodepoint(0x1F603, charWidth = 2) // B at 2-3
            state.cursor.col = 1

            writer.printCodepoint(0x1F923, charWidth = 2) // C at 1-2

            assertAll(
                { assertEquals(TerminalConstants.EMPTY, lineAt(state, 0).getCodepoint(0)) },
                { assertEquals(0x1F923, lineAt(state, 0).getCodepoint(1)) },
                { assertEquals(TerminalConstants.WIDE_CHAR_SPACER, lineAt(state, 0).getCodepoint(2)) },
                { assertEquals(TerminalConstants.EMPTY, lineAt(state, 0).getCodepoint(3)) },
                { assertEquals(3, state.cursor.col) },
                { assertEquals(0, state.cursor.row) }
            )
        }

        @Test
        fun `single-width write at end wraps and marks line wrapped`() {
            val state = createState(width = 2, height = 2)
            val writer = MutationEngine(state)

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
            val writer = MutationEngine(state)
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
            val writer = MutationEngine(state)
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
            val writer = MutationEngine(state)
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

        @Test
        fun `row out-of-bounds cursor is ignored without mutation`() {
            val state = createState(width = 3, height = 2)
            val writer = MutationEngine(state)
            writeAscii(writer, "ABC")
            state.cursor.row = 99

            writer.printCodepoint('Z'.code, 1)

            assertAll(
                { assertLineCodepoints(state, 0, intArrayOf('A'.code, 'B'.code, 'C'.code)) },
                { assertEquals(99, state.cursor.row) }
            )
        }

        @Test
        fun `wide character fits when starting at width minus two`() {
            val state = createState(width = 4, height = 2)
            val writer = MutationEngine(state)
            seedLine(state, 0, "AB")
            state.cursor.row = 0
            state.cursor.col = 2

            writer.printCodepoint(0x1F923, 2)

            assertAll(
                { assertEquals('A'.code, lineAt(state, 0).getCodepoint(0)) },
                { assertEquals('B'.code, lineAt(state, 0).getCodepoint(1)) },
                { assertEquals(0x1F923, lineAt(state, 0).getCodepoint(2)) },
                { assertEquals(TerminalConstants.WIDE_CHAR_SPACER, lineAt(state, 0).getCodepoint(3)) },
                { assertEquals(0, state.cursor.col) },
                { assertEquals(1, state.cursor.row) }
            )
        }
    }

    @Nested
    @DisplayName("erase operations")
    inner class EraseTests {

        @Test
        fun `eraseLineToEnd on spacer annihilates owning cluster`() {
            val state = createState(width = 4, height = 1)
            val writer = MutationEngine(state)

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
            val writer = MutationEngine(state)

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
            val writer = MutationEngine(state)
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
            val writer = MutationEngine(state)
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
            val writer = MutationEngine(state)
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
            val writer = MutationEngine(state)

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

        @Test
        fun `insert is no-op when cursor is out of bounds`() {
            val state = createState(width = 4, height = 2)
            val writer = MutationEngine(state)
            writeAscii(writer, "ABCD")
            state.cursor.col = 99

            writer.insertBlankCharacters(2)

            assertLineCodepoints(state, 0, intArrayOf('A'.code, 'B'.code, 'C'.code, 'D'.code))
        }

        @Test
        fun `insert clamps when count exceeds remaining width`() {
            val state = createState(width = 5, height = 2)
            val writer = MutationEngine(state)
            seedLine(state, 0, "ABCDE")
            state.cursor.row = 0
            state.cursor.col = 3

            writer.insertBlankCharacters(99)

            assertLineCodepoints(
                state,
                0,
                intArrayOf('A'.code, 'B'.code, 'C'.code, TerminalConstants.EMPTY, TerminalConstants.EMPTY)
            )
        }
    }

    @Nested
    @DisplayName("insertLines")
    inner class InsertLinesTests {

        @Test
        fun `insertLines non-positive count is no-op`() {
            val state = createState(width = 3, height = 3)
            val writer = MutationEngine(state)
            seedLine(state, 0, "AAA")
            seedLine(state, 1, "BBB")
            seedLine(state, 2, "CCC")
            state.cursor.row = 1

            writer.insertLines(0)
            writer.insertLines(-10)

            assertAll(
                { assertLineCodepoints(state, 0, intArrayOf('A'.code, 'A'.code, 'A'.code)) },
                { assertLineCodepoints(state, 1, intArrayOf('B'.code, 'B'.code, 'B'.code)) },
                { assertLineCodepoints(state, 2, intArrayOf('C'.code, 'C'.code, 'C'.code)) }
            )
        }

        @Test
        fun `insertLines is ignored when cursor is outside active scroll region`() {
            val state = createState(width = 3, height = 4)
            val writer = MutationEngine(state)
            state.scrollTop = 1
            state.scrollBottom = 2
            seedLine(state, 0, "AAA")
            seedLine(state, 1, "BBB")
            seedLine(state, 2, "CCC")
            seedLine(state, 3, "DDD")

            state.cursor.row = 0
            writer.insertLines(1)
            state.cursor.row = 3
            writer.insertLines(1)

            assertAll(
                { assertLineCodepoints(state, 0, intArrayOf('A'.code, 'A'.code, 'A'.code)) },
                { assertLineCodepoints(state, 1, intArrayOf('B'.code, 'B'.code, 'B'.code)) },
                { assertLineCodepoints(state, 2, intArrayOf('C'.code, 'C'.code, 'C'.code)) },
                { assertLineCodepoints(state, 3, intArrayOf('D'.code, 'D'.code, 'D'.code)) }
            )
        }

        @Test
        fun `insertLines shifts down from cursor through bottom and clears inserted row`() {
            val state = createState(width = 3, height = 5)
            val writer = MutationEngine(state)
            state.scrollTop = 1
            state.scrollBottom = 3
            seedLine(state, 0, "AAA")
            seedLine(state, 1, "BBB")
            seedLine(state, 2, "CCC")
            seedLine(state, 3, "DDD")
            seedLine(state, 4, "EEE")
            state.cursor.row = 2

            writer.insertLines(1)

            assertAll(
                { assertLineCodepoints(state, 0, intArrayOf('A'.code, 'A'.code, 'A'.code)) },
                { assertLineCodepoints(state, 1, intArrayOf('B'.code, 'B'.code, 'B'.code)) },
                {
                    assertLineCodepoints(
                        state,
                        2,
                        intArrayOf(TerminalConstants.EMPTY, TerminalConstants.EMPTY, TerminalConstants.EMPTY)
                    )
                },
                { assertLineCodepoints(state, 3, intArrayOf('C'.code, 'C'.code, 'C'.code)) },
                { assertLineCodepoints(state, 4, intArrayOf('E'.code, 'E'.code, 'E'.code)) }
            )
        }

        @Test
        fun `insertLines count is clamped to remaining region height`() {
            val state = createState(width = 3, height = 4)
            val writer = MutationEngine(state)
            state.scrollTop = 1
            state.scrollBottom = 3
            seedLine(state, 0, "AAA")
            seedLine(state, 1, "BBB")
            seedLine(state, 2, "CCC")
            seedLine(state, 3, "DDD")
            state.cursor.row = 2

            writer.insertLines(99)

            assertAll(
                { assertLineCodepoints(state, 0, intArrayOf('A'.code, 'A'.code, 'A'.code)) },
                { assertLineCodepoints(state, 1, intArrayOf('B'.code, 'B'.code, 'B'.code)) },
                {
                    assertLineCodepoints(
                        state,
                        2,
                        intArrayOf(TerminalConstants.EMPTY, TerminalConstants.EMPTY, TerminalConstants.EMPTY)
                    )
                },
                {
                    assertLineCodepoints(
                        state,
                        3,
                        intArrayOf(TerminalConstants.EMPTY, TerminalConstants.EMPTY, TerminalConstants.EMPTY)
                    )
                }
            )
        }

        @Test
        fun `insertLines at bottom margin clears only bottom row`() {
            val state = createState(width = 3, height = 3)
            val writer = MutationEngine(state)
            seedLine(state, 0, "AAA")
            seedLine(state, 1, "BBB")
            seedLine(state, 2, "CCC")
            state.cursor.row = 2

            writer.insertLines(5)

            assertAll(
                { assertLineCodepoints(state, 0, intArrayOf('A'.code, 'A'.code, 'A'.code)) },
                { assertLineCodepoints(state, 1, intArrayOf('B'.code, 'B'.code, 'B'.code)) },
                {
                    assertLineCodepoints(
                        state,
                        2,
                        intArrayOf(TerminalConstants.EMPTY, TerminalConstants.EMPTY, TerminalConstants.EMPTY)
                    )
                }
            )
        }

        @Test
        fun `insertLines clears inserted rows using current pen attribute`() {
            val state = createState(width = 3, height = 3)
            val writer = MutationEngine(state)
            seedLine(state, 0, "AAA", attr = 11)
            seedLine(state, 1, "BBB", attr = 11)
            seedLine(state, 2, "CCC", attr = 11)
            state.pen.setAttributes(fg = 5, bg = 2, bold = true)
            val clearAttr = state.pen.currentAttr
            state.cursor.row = 1

            writer.insertLines(1)

            assertLineAttrs(state, 1, intArrayOf(clearAttr, clearAttr, clearAttr))
            assertLineCodepoints(state, 2, intArrayOf('B'.code, 'B'.code, 'B'.code))
        }

        @Test
        fun `insertLines uses resolveRingIndex when viewport has history offset`() {
            val state = createState(width = 2, height = 2, history = 4)
            val writer = MutationEngine(state)
            seedLine(state, 0, "AA")
            seedLine(state, 1, "BB")
            writer.scrollUp() // grow ring so viewport start index is no longer zero
            seedLine(state, 0, "CC")
            seedLine(state, 1, "DD")
            state.cursor.row = 0
            val oldSize = state.ring.size

            writer.insertLines(1)

            assertAll(
                { assertEquals(oldSize, state.ring.size, "insertLines must not push to history") },
                { assertLineCodepoints(state, 0, intArrayOf(TerminalConstants.EMPTY, TerminalConstants.EMPTY)) },
                { assertLineCodepoints(state, 1, intArrayOf('C'.code, 'C'.code)) }
            )
        }
    }

    @Nested
    @DisplayName("deleteLines")
    inner class DeleteLinesTests {

        @Test
        fun `deleteLines non-positive count is no-op`() {
            val state = createState(width = 3, height = 3)
            val writer = MutationEngine(state)
            seedLine(state, 0, "AAA")
            seedLine(state, 1, "BBB")
            seedLine(state, 2, "CCC")
            state.cursor.row = 1

            writer.deleteLines(0)
            writer.deleteLines(-3)

            assertAll(
                { assertLineCodepoints(state, 0, intArrayOf('A'.code, 'A'.code, 'A'.code)) },
                { assertLineCodepoints(state, 1, intArrayOf('B'.code, 'B'.code, 'B'.code)) },
                { assertLineCodepoints(state, 2, intArrayOf('C'.code, 'C'.code, 'C'.code)) }
            )
        }

        @Test
        fun `deleteLines is ignored when cursor is outside active scroll region`() {
            val state = createState(width = 3, height = 4)
            val writer = MutationEngine(state)
            state.scrollTop = 1
            state.scrollBottom = 2
            seedLine(state, 0, "AAA")
            seedLine(state, 1, "BBB")
            seedLine(state, 2, "CCC")
            seedLine(state, 3, "DDD")

            state.cursor.row = 0
            writer.deleteLines(1)
            state.cursor.row = 3
            writer.deleteLines(1)

            assertAll(
                { assertLineCodepoints(state, 0, intArrayOf('A'.code, 'A'.code, 'A'.code)) },
                { assertLineCodepoints(state, 1, intArrayOf('B'.code, 'B'.code, 'B'.code)) },
                { assertLineCodepoints(state, 2, intArrayOf('C'.code, 'C'.code, 'C'.code)) },
                { assertLineCodepoints(state, 3, intArrayOf('D'.code, 'D'.code, 'D'.code)) }
            )
        }

        @Test
        fun `deleteLines shifts up from below cursor and clears bottom margin`() {
            val state = createState(width = 3, height = 5)
            val writer = MutationEngine(state)
            state.scrollTop = 1
            state.scrollBottom = 3
            seedLine(state, 0, "AAA")
            seedLine(state, 1, "BBB")
            seedLine(state, 2, "CCC")
            seedLine(state, 3, "DDD")
            seedLine(state, 4, "EEE")
            state.cursor.row = 2

            writer.deleteLines(1)

            assertAll(
                { assertLineCodepoints(state, 0, intArrayOf('A'.code, 'A'.code, 'A'.code)) },
                { assertLineCodepoints(state, 1, intArrayOf('B'.code, 'B'.code, 'B'.code)) },
                { assertLineCodepoints(state, 2, intArrayOf('D'.code, 'D'.code, 'D'.code)) },
                {
                    assertLineCodepoints(
                        state,
                        3,
                        intArrayOf(TerminalConstants.EMPTY, TerminalConstants.EMPTY, TerminalConstants.EMPTY)
                    )
                },
                { assertLineCodepoints(state, 4, intArrayOf('E'.code, 'E'.code, 'E'.code)) }
            )
        }

        @Test
        fun `deleteLines count is clamped to remaining region height`() {
            val state = createState(width = 3, height = 4)
            val writer = MutationEngine(state)
            state.scrollTop = 1
            state.scrollBottom = 3
            seedLine(state, 0, "AAA")
            seedLine(state, 1, "BBB")
            seedLine(state, 2, "CCC")
            seedLine(state, 3, "DDD")
            state.cursor.row = 2

            writer.deleteLines(99)

            assertAll(
                { assertLineCodepoints(state, 0, intArrayOf('A'.code, 'A'.code, 'A'.code)) },
                { assertLineCodepoints(state, 1, intArrayOf('B'.code, 'B'.code, 'B'.code)) },
                {
                    assertLineCodepoints(
                        state,
                        2,
                        intArrayOf(TerminalConstants.EMPTY, TerminalConstants.EMPTY, TerminalConstants.EMPTY)
                    )
                },
                {
                    assertLineCodepoints(
                        state,
                        3,
                        intArrayOf(TerminalConstants.EMPTY, TerminalConstants.EMPTY, TerminalConstants.EMPTY)
                    )
                }
            )
        }

        @Test
        fun `deleteLines at bottom margin clears only bottom row`() {
            val state = createState(width = 3, height = 3)
            val writer = MutationEngine(state)
            seedLine(state, 0, "AAA")
            seedLine(state, 1, "BBB")
            seedLine(state, 2, "CCC")
            state.cursor.row = 2

            writer.deleteLines(5)

            assertAll(
                { assertLineCodepoints(state, 0, intArrayOf('A'.code, 'A'.code, 'A'.code)) },
                { assertLineCodepoints(state, 1, intArrayOf('B'.code, 'B'.code, 'B'.code)) },
                {
                    assertLineCodepoints(
                        state,
                        2,
                        intArrayOf(TerminalConstants.EMPTY, TerminalConstants.EMPTY, TerminalConstants.EMPTY)
                    )
                }
            )
        }

        @Test
        fun `deleteLines clears bottom rows using current pen attribute`() {
            val state = createState(width = 3, height = 3)
            val writer = MutationEngine(state)
            seedLine(state, 0, "AAA", attr = 13)
            seedLine(state, 1, "BBB", attr = 13)
            seedLine(state, 2, "CCC", attr = 13)
            state.pen.setAttributes(fg = 1, bg = 6, underline = true)
            val clearAttr = state.pen.currentAttr
            state.cursor.row = 0

            writer.deleteLines(1)

            assertLineAttrs(state, 2, intArrayOf(clearAttr, clearAttr, clearAttr))
            assertLineCodepoints(state, 0, intArrayOf('B'.code, 'B'.code, 'B'.code))
        }

        @Test
        fun `deleteLines uses resolveRingIndex when viewport has history offset`() {
            val state = createState(width = 2, height = 2, history = 4)
            val writer = MutationEngine(state)
            seedLine(state, 0, "AA")
            seedLine(state, 1, "BB")
            writer.scrollUp() // grow ring so viewport start index is no longer zero
            seedLine(state, 0, "CC")
            seedLine(state, 1, "DD")
            state.cursor.row = 0
            val oldSize = state.ring.size

            writer.deleteLines(1)

            assertAll(
                { assertEquals(oldSize, state.ring.size, "deleteLines must not push to history") },
                { assertLineCodepoints(state, 0, intArrayOf('D'.code, 'D'.code)) },
                { assertLineCodepoints(state, 1, intArrayOf(TerminalConstants.EMPTY, TerminalConstants.EMPTY)) }
            )
        }
    }

    @Nested
    @DisplayName("eraseLineToEnd")
    inner class EraseLineToEndTests {

        @Test
        fun `eraseLineToEnd clears suffix including cursor cell`() {
            val state = createState(width = 5, height = 2)
            val writer = MutationEngine(state)
            seedLine(state, 0, "ABCDE")
            state.cursor.row = 0
            state.cursor.col = 2

            writer.eraseLineToEnd()

            assertLineCodepoints(
                state,
                0,
                intArrayOf(
                    'A'.code,
                    'B'.code,
                    TerminalConstants.EMPTY,
                    TerminalConstants.EMPTY,
                    TerminalConstants.EMPTY
                )
            )
        }

        @Test
        fun `eraseLineToEnd is no-op when cursor out of bounds`() {
            val state = createState(width = 4, height = 2)
            val writer = MutationEngine(state)
            writeAscii(writer, "ABCD")
            state.cursor.col = -1

            writer.eraseLineToEnd()

            assertLineCodepoints(state, 0, intArrayOf('A'.code, 'B'.code, 'C'.code, 'D'.code))
        }
    }

    @Nested
    @DisplayName("eraseLineToCursor")
    inner class EraseLineToCursorTests {

        @Test
        fun `eraseLineToCursor clears prefix through cursor`() {
            val state = createState(width = 5, height = 2)
            val writer = MutationEngine(state)
            seedLine(state, 0, "ABCDE")
            state.cursor.row = 0
            state.cursor.col = 2

            writer.eraseLineToCursor()

            assertLineCodepoints(
                state,
                0,
                intArrayOf(
                    TerminalConstants.EMPTY,
                    TerminalConstants.EMPTY,
                    TerminalConstants.EMPTY,
                    'D'.code,
                    'E'.code
                )
            )
        }

        @Test
        fun `eraseLineToCursor is no-op when cursor out of bounds`() {
            val state = createState(width = 4, height = 2)
            val writer = MutationEngine(state)
            writeAscii(writer, "ABCD")
            state.cursor.row = 99

            writer.eraseLineToCursor()

            assertLineCodepoints(state, 0, intArrayOf('A'.code, 'B'.code, 'C'.code, 'D'.code))
        }
    }

    @Nested
    @DisplayName("eraseCurrentLine")
    inner class EraseCurrentLineTests {

        @Test
        fun `eraseCurrentLine does not affect other rows`() {
            val state = createState(width = 3, height = 2)
            val writer = MutationEngine(state)
            seedLine(state, 0, "ABC")
            seedLine(state, 1, "DEF")
            state.cursor.row = 0

            writer.eraseCurrentLine()

            assertAll(
                {
                    assertLineCodepoints(
                        state,
                        0,
                        intArrayOf(TerminalConstants.EMPTY, TerminalConstants.EMPTY, TerminalConstants.EMPTY)
                    )
                },
                { assertLineCodepoints(state, 1, intArrayOf('D'.code, 'E'.code, 'F'.code)) }
            )
        }
    }

    @Nested
    @DisplayName("scrollUp")
    inner class ScrollUpTests {

        @Test
        fun `scrollUp pushes a blank line and preserves cursor`() {
            val state = createState(width = 3, height = 2, history = 4)
            val writer = MutationEngine(state)
            state.cursor.col = 2
            state.cursor.row = 1
            val oldSize = state.ring.size

            writer.scrollUp()

            assertAll(
                { assertTrue(state.ring.size >= oldSize) },
                { assertEquals(2, state.cursor.col) },
                { assertEquals(1, state.cursor.row) },
                {
                    assertLineCodepoints(
                        state,
                        1,
                        intArrayOf(TerminalConstants.EMPTY, TerminalConstants.EMPTY, TerminalConstants.EMPTY)
                    )
                }
            )
        }
    }

    @Nested
    @DisplayName("clearViewport")
    inner class ClearViewportTests {

        @Test
        fun `clearViewport clears visible rows and resets wrapped`() {
            val state = createState(width = 3, height = 2)
            val writer = MutationEngine(state)
            writeAscii(writer, "ABC")
            writeAscii(writer, "DEF")
            lineAt(state, 0).wrapped = true
            lineAt(state, 1).wrapped = true
            val oldSize = state.ring.size

            writer.clearViewport()

            assertAll(
                { assertEquals(oldSize, state.ring.size) },
                {
                    assertLineCodepoints(
                        state,
                        0,
                        intArrayOf(TerminalConstants.EMPTY, TerminalConstants.EMPTY, TerminalConstants.EMPTY)
                    )
                },
                {
                    assertLineCodepoints(
                        state,
                        1,
                        intArrayOf(TerminalConstants.EMPTY, TerminalConstants.EMPTY, TerminalConstants.EMPTY)
                    )
                },
                { assertFalse(lineAt(state, 0).wrapped) },
                { assertFalse(lineAt(state, 1).wrapped) }
            )
        }
    }

    @Nested
    @DisplayName("clearAllHistory")
    inner class ClearAllHistoryTests {

        @Test
        fun `clearAllHistory resets ring to exactly viewport height with blank lines`() {
            val state = createState(width = 4, height = 2, history = 5)
            val writer = MutationEngine(state)
            writeAscii(writer, "ABCDEFGH")
            writer.scrollUp()

            writer.clearAllHistory()

            assertAll(
                { assertEquals(2, state.ring.size) },
                {
                    assertLineCodepoints(
                        state,
                        0,
                        intArrayOf(
                            TerminalConstants.EMPTY,
                            TerminalConstants.EMPTY,
                            TerminalConstants.EMPTY,
                            TerminalConstants.EMPTY
                        )
                    )
                },
                {
                    assertLineCodepoints(
                        state,
                        1,
                        intArrayOf(
                            TerminalConstants.EMPTY,
                            TerminalConstants.EMPTY,
                            TerminalConstants.EMPTY,
                            TerminalConstants.EMPTY
                        )
                    )
                }
            )
        }
    }

    @Nested
    @DisplayName("newLine")
    inner class NewLineTests {

        @Test
        fun `newLine moves down and preserves column`() {
            val state = createState(width = 4, height = 3)
            val writer = MutationEngine(state)
            state.cursor.col = 2
            state.cursor.row = 0

            writer.newLine()

            assertAll(
                { assertEquals(2, state.cursor.col) },
                { assertEquals(1, state.cursor.row) }
            )
        }

        @Test
        fun `newLine at bottom scrolls and keeps cursor at last row`() {
            val state = createState(width = 3, height = 2, history = 4)
            val writer = MutationEngine(state)
            state.cursor.row = 1
            state.cursor.col = 1
            val oldSize = state.ring.size

            writer.newLine()

            assertAll(
                { assertTrue(state.ring.size >= oldSize) },
                { assertEquals(1, state.cursor.row) },
                { assertEquals(1, state.cursor.col) },
                {
                    assertLineCodepoints(
                        state,
                        1,
                        intArrayOf(TerminalConstants.EMPTY, TerminalConstants.EMPTY, TerminalConstants.EMPTY)
                    )
                }
            )
        }
    }

    @Nested
    @DisplayName("reverseLineFeed")
    inner class ReverseLineFeedTests {

        @Test
        fun `reverseLineFeed moves cursor up when not at top`() {
            val state = createState(width = 3, height = 3)
            val writer = MutationEngine(state)
            state.cursor.row = 2
            state.cursor.col = 1

            writer.reverseLineFeed()

            assertAll(
                { assertEquals(1, state.cursor.row) },
                { assertEquals(1, state.cursor.col) }
            )
        }

        @Test
        fun `reverseLineFeed at top scrolls region down`() {
            val state = createState(width = 3, height = 2, history = 4)
            val writer = MutationEngine(state)
            state.cursor.row = 0
            state.cursor.col = 1
            seedLine(state, 0, "ABC")
            seedLine(state, 1, "DEF")

            writer.reverseLineFeed()

            assertAll(
                { assertEquals(0, state.cursor.row) },
                { assertEquals(1, state.cursor.col) },
                {
                    assertLineCodepoints(
                        state,
                        0,
                        intArrayOf(TerminalConstants.EMPTY, TerminalConstants.EMPTY, TerminalConstants.EMPTY)
                    )
                },
                { assertLineCodepoints(state, 1, intArrayOf('A'.code, 'B'.code, 'C'.code)) }
            )
        }

        @Test
        fun `reverseLineFeed clamps to bottom when at top after scroll`() {
            val state = createState(width = 3, height = 1, history = 4)
            val writer = MutationEngine(state)
            state.cursor.row = 0

            writer.reverseLineFeed()

            assertEquals(0, state.cursor.row, "Should not go above top")
        }
    }

    @Nested
    @DisplayName("scrollDown")
    inner class ScrollDownTests {

        @Test
        fun `scrollDown rotates region downward and clears top line`() {
            val state = createState(width = 3, height = 3)
            val writer = MutationEngine(state)
            seedLine(state, 0, "AAA")
            seedLine(state, 1, "BBB")
            seedLine(state, 2, "CCC")

            writer.scrollDown()

            assertAll(
                {
                    assertLineCodepoints(
                        state,
                        0,
                        intArrayOf(TerminalConstants.EMPTY, TerminalConstants.EMPTY, TerminalConstants.EMPTY)
                    )
                },
                { assertLineCodepoints(state, 1, intArrayOf('A'.code, 'A'.code, 'A'.code)) },
                { assertLineCodepoints(state, 2, intArrayOf('B'.code, 'B'.code, 'B'.code)) }
            )
        }

        @Test
        fun `scrollDown multiple times`() {
            val state = createState(width = 2, height = 2)
            val writer = MutationEngine(state)
            seedLine(state, 0, "AB")
            seedLine(state, 1, "CD")

            writer.scrollDown(2)

            assertAll(
                { assertLineCodepoints(state, 0, intArrayOf(TerminalConstants.EMPTY, TerminalConstants.EMPTY)) },
                { assertLineCodepoints(state, 1, intArrayOf(TerminalConstants.EMPTY, TerminalConstants.EMPTY)) }
            )
        }

        @Test
        fun `scrollDown clamped by region size`() {
            val state = createState(width = 2, height = 2)
            val writer = MutationEngine(state)
            seedLine(state, 0, "AB")
            seedLine(state, 1, "CD")

            writer.scrollDown(999)

            assertAll(
                { assertLineCodepoints(state, 0, intArrayOf(TerminalConstants.EMPTY, TerminalConstants.EMPTY)) },
                { assertLineCodepoints(state, 1, intArrayOf(TerminalConstants.EMPTY, TerminalConstants.EMPTY)) }
            )
        }

        @Test
        fun `scrollDown with partial scroll region`() {
            val state = createState(width = 3, height = 3)
            val writer = MutationEngine(state)
            state.scrollTop = 1
            state.scrollBottom = 2
            seedLine(state, 0, "AAA")
            seedLine(state, 1, "BBB")
            seedLine(state, 2, "CCC")

            writer.scrollDown()

            assertAll(
                { assertLineCodepoints(state, 0, intArrayOf('A'.code, 'A'.code, 'A'.code)) },
                {
                    assertLineCodepoints(
                        state,
                        1,
                        intArrayOf(TerminalConstants.EMPTY, TerminalConstants.EMPTY, TerminalConstants.EMPTY)
                    )
                },
                { assertLineCodepoints(state, 2, intArrayOf('B'.code, 'B'.code, 'B'.code)) }
            )
        }
    }

    @Nested
    @DisplayName("scrollUp with region variations")
    inner class ScrollUpVariationsTests {

        @Test
        fun `scrollUp full viewport writes to history`() {
            val state = createState(width = 3, height = 2, history = 4)
            val writer = MutationEngine(state)
            seedLine(state, 0, "AAA")
            seedLine(state, 1, "BBB")
            val oldSize = state.ring.size

            writer.scrollUp()

            assertTrue(state.ring.size >= oldSize, "Ring should grow or stay same")
            assertLineCodepoints(
                state,
                1,
                intArrayOf(TerminalConstants.EMPTY, TerminalConstants.EMPTY, TerminalConstants.EMPTY)
            )
        }

        @Test
        fun `scrollUp partial region rotates without history`() {
            val state = createState(width = 3, height = 3)
            val writer = MutationEngine(state)
            state.scrollTop = 1
            state.scrollBottom = 2
            seedLine(state, 0, "AAA")
            seedLine(state, 1, "BBB")
            seedLine(state, 2, "CCC")
            val oldSize = state.ring.size

            writer.scrollUp()

            assertEquals(oldSize, state.ring.size, "Partial region scroll should not change ring size")
            assertLineCodepoints(state, 0, intArrayOf('A'.code, 'A'.code, 'A'.code))
            assertLineCodepoints(state, 1, intArrayOf('C'.code, 'C'.code, 'C'.code))
            assertLineCodepoints(
                state,
                2,
                intArrayOf(TerminalConstants.EMPTY, TerminalConstants.EMPTY, TerminalConstants.EMPTY)
            )
        }

        @Test
        fun `scrollUp zero count is no-op`() {
            val state = createState(width = 2, height = 2)
            val writer = MutationEngine(state)
            seedLine(state, 0, "AB")
            seedLine(state, 1, "CD")
            val oldSize = state.ring.size

            writer.scrollUp(0)

            assertEquals(oldSize, state.ring.size)
            assertLineCodepoints(state, 0, intArrayOf('A'.code, 'B'.code))
            assertLineCodepoints(state, 1, intArrayOf('C'.code, 'D'.code))
        }

        @Test
        fun `scrollUp negative count is no-op`() {
            val state = createState(width = 2, height = 2)
            val writer = MutationEngine(state)
            seedLine(state, 0, "AB")
            val oldSize = state.ring.size

            writer.scrollUp(-5)

            assertEquals(oldSize, state.ring.size)
            assertLineCodepoints(state, 0, intArrayOf('A'.code, 'B'.code))
        }

        @Test
        fun `scrollUp multiple times fills history`() {
            val state = createState(width = 2, height = 3, history = 5)
            val writer = MutationEngine(state)
            seedLine(state, 0, "AB")
            seedLine(state, 1, "CD")
            seedLine(state, 2, "EF")
            val initialSize = state.ring.size

            writer.scrollUp(3)

            // Each scrollUp push adds to history when full viewport
            assertTrue(state.ring.size >= initialSize + 2)
        }
    }

    @Nested
    @DisplayName("printCluster")
    inner class PrintClusterTests {

        @Test
        fun `printCluster with single codepoint delegates to printCodepoint`() {
            val state = createState(width = 4, height = 1)
            val writer = MutationEngine(state)

            val cpArray = intArrayOf('A'.code)
            writer.printCluster(cpArray, 1, 1)

            assertEquals('A'.code, lineAt(state, 0).getCodepoint(0))
            assertEquals(1, state.cursor.col)
        }

        @Test
        fun `printCluster with multiple codepoints stores cluster`() {
            val state = createState(width = 4, height = 1)
            val writer = MutationEngine(state)

            val cpArray = intArrayOf('A'.code, 0x0301)  // A with combining accent
            writer.printCluster(cpArray, 2, 1)

            assertTrue(lineAt(state, 0).isCluster(0), "Should store as cluster")
            assertEquals('A'.code, lineAt(state, 0).getCodepoint(0), "Base codepoint should be 'A'")
            assertEquals(1, state.cursor.col)
        }

        @Test
        fun `printCluster with width-2 and multiple codepoints`() {
            val state = createState(width = 4, height = 1)
            val writer = MutationEngine(state)

            val cpArray = intArrayOf(0x1F600, 0xFE0F)  // Emoji with variation selector
            writer.printCluster(cpArray, 2, 2)

            assertTrue(lineAt(state, 0).isCluster(0), "Should store as cluster")
            assertEquals(TerminalConstants.WIDE_CHAR_SPACER, lineAt(state, 0).rawCodepoint(1))
            assertEquals(2, state.cursor.col)
        }

        @Test
        fun `printCluster wraps at edge`() {
            val state = createState(width = 2, height = 2)
            val writer = MutationEngine(state)

            val cpArray = intArrayOf('A'.code, 'B'.code)
            state.cursor.col = 1
            writer.printCluster(cpArray, 2, 1)

            assertEquals(0, state.cursor.col)
            assertEquals(1, state.cursor.row)
            assertTrue(lineAt(state, 0).wrapped)
        }
    }

    @Nested
    @DisplayName("Wide character edge cases")
    inner class WideCharacterEdgeCases {

        @Test
        fun `wide character at width minus one wraps correctly`() {
            val state = createState(width = 3, height = 2)
            val writer = MutationEngine(state)
            state.cursor.col = 2

            writer.printCodepoint(0x1F600, 2)

            assertEquals(2, state.cursor.col)
            assertEquals(1, state.cursor.row)
            assertEquals(0x1F600, lineAt(state, 1).getCodepoint(0))
            assertEquals(TerminalConstants.WIDE_CHAR_SPACER, lineAt(state, 1).getCodepoint(1))
        }

        @Test
        fun `overwrite second half of wide char annihilates leader`() {
            val state = createState(width = 4, height = 1)
            val writer = MutationEngine(state)

            writer.printCodepoint(0x1F600, 2)
            state.cursor.col = 1

            writer.printCodepoint('X'.code, 1)

            assertEquals(TerminalConstants.EMPTY, lineAt(state, 0).getCodepoint(0))
            assertEquals('X'.code, lineAt(state, 0).getCodepoint(1))
            assertEquals(TerminalConstants.EMPTY, lineAt(state, 0).getCodepoint(2))
        }

        @Test
        fun `consecutive wide chars maintain spacer invariant`() {
            val state = createState(width = 5, height = 1)
            val writer = MutationEngine(state)

            writer.printCodepoint(0x1F600, 2)
            writer.printCodepoint(0x1F603, 2)

            assertEquals(0x1F600, lineAt(state, 0).getCodepoint(0))
            assertEquals(TerminalConstants.WIDE_CHAR_SPACER, lineAt(state, 0).getCodepoint(1))
            assertEquals(0x1F603, lineAt(state, 0).getCodepoint(2))
            assertEquals(TerminalConstants.WIDE_CHAR_SPACER, lineAt(state, 0).getCodepoint(3))
        }

        @Test
        fun `overwrite from spacer into next character`() {
            val state = createState(width = 5, height = 1)
            val writer = MutationEngine(state)

            writer.printCodepoint(0x1F600, 2)
            writer.printCodepoint('A'.code, 1)
            state.cursor.col = 1

            writer.printCodepoint(0x1F602, 2)

            assertEquals(TerminalConstants.EMPTY, lineAt(state, 0).getCodepoint(0))
            assertEquals(0x1F602, lineAt(state, 0).getCodepoint(1))
            assertEquals(TerminalConstants.WIDE_CHAR_SPACER, lineAt(state, 0).getCodepoint(2))
            assertEquals(TerminalConstants.EMPTY, lineAt(state, 0).getCodepoint(3))
        }
    }

    @Nested
    @DisplayName("Attribute handling")
    inner class AttributeHandlingTests {

        @Test
        fun `printCodepoint applies current pen attribute`() {
            val state = createState(width = 2, height = 1)
            val writer = MutationEngine(state)
            state.pen.setAttributes(fg = 5, bg = 1, bold = true)

            writer.printCodepoint('A'.code, 1)

            val attr = lineAt(state, 0).getPackedAttr(0)
            assertNotEquals(0, attr, "Attribute should be non-zero")
        }

        @Test
        fun `eraseLineToEnd uses current pen attribute`() {
            val state = createState(width = 3, height = 1)
            val writer = MutationEngine(state)
            seedLine(state, 0, "ABC", attr = 10)
            state.pen.setAttributes(fg = 3, bg = 2)
            state.cursor.col = 1

            writer.eraseLineToEnd()

            assertEquals(10, lineAt(state, 0).getPackedAttr(0), "Prefix should keep original attr")
            val erasedAttr = lineAt(state, 0).getPackedAttr(1)
            assertNotEquals(10, erasedAttr, "Erased cells should have new pen attr")
        }

        @Test
        fun `scrollUp clears with pen attribute`() {
            val state = createState(width = 2, height = 1, history = 2)
            val writer = MutationEngine(state)
            state.pen.setAttributes(fg = 6, bg = 4)

            writer.scrollUp()

            val attr0 = lineAt(state, 0).getPackedAttr(0)
            val attr1 = lineAt(state, 0).getPackedAttr(1)
            assertEquals(attr0, attr1, "Both cells should have same pen attr")
        }
    }

    @Nested
    @DisplayName("Boundary and overflow handling")
    inner class BoundaryAndOverflowTests {

        @Test
        fun `insertBlankCharacters beyond width is safe`() {
            val state = createState(width = 5, height = 1)
            val writer = MutationEngine(state)
            seedLine(state, 0, "ABCDE")
            state.cursor.col = 0

            writer.insertBlankCharacters(100)

            for (col in 0 until 5) {
                assertEquals(TerminalConstants.EMPTY, lineAt(state, 0).getCodepoint(col))
            }
        }

        @Test
        fun `erase operations on boundary rows are safe`() {
            val state = createState(width = 3, height = 2)
            val writer = MutationEngine(state)
            state.cursor.row = 99
            state.cursor.col = 1

            writer.eraseLineToEnd()
            writer.eraseLineToCursor()
            writer.eraseCurrentLine()

            // Should not throw, operations are no-ops
        }

        @Test
        fun `printCodepoint with very large codepoint`() {
            val state = createState(width = 2, height = 1)
            val writer = MutationEngine(state)

            writer.printCodepoint(0x10FFFF, 1)  // Max valid Unicode

            assertEquals(0x10FFFF, lineAt(state, 0).getCodepoint(0))
        }

        @Test
        fun `negative column cursor is ignored`() {
            val state = createState(width = 3, height = 1)
            val writer = MutationEngine(state)
            state.cursor.col = -1

            writer.printCodepoint('A'.code, 1)

            assertEquals(TerminalConstants.EMPTY, lineAt(state, 0).getCodepoint(0))
        }

        @Test
        fun `negative row cursor is ignored`() {
            val state = createState(width = 3, height = 1)
            val writer = MutationEngine(state)
            state.cursor.row = -1

            writer.printCodepoint('A'.code, 1)

            assertEquals(TerminalConstants.EMPTY, lineAt(state, 0).getCodepoint(0))
        }
    }

    @Nested
    @DisplayName("Complex interaction scenarios")
    inner class ComplexInteractionScenarios {

        @Test
        fun `write wrap scroll then erase`() {
            val state = createState(width = 2, height = 2, history = 3)
            val writer = MutationEngine(state)

            writeAscii(writer, "ABCD")
            writer.printCodepoint('E'.code, 1)
            state.cursor.col = 0
            writer.eraseLineToEnd()

            assertEquals(TerminalConstants.EMPTY, lineAt(state, 1).getCodepoint(0))
            assertEquals(TerminalConstants.EMPTY, lineAt(state, 1).getCodepoint(1))
        }

        @Test
        fun `insert then scroll`() {
            val state = createState(width = 4, height = 2, history = 3)
            val writer = MutationEngine(state)
            writeAscii(writer, "ABCD")
            state.cursor.row = 0
            state.cursor.col = 1

            writer.insertBlankCharacters(2)
            writer.scrollUp()

            // Cursor should remain exactly where we put it.
            assertEquals(0, state.cursor.row)
        }

        @Test
        fun `multiple wide chars with overwrites`() {
            val state = createState(width = 6, height = 2)
            val writer = MutationEngine(state)

            writer.printCodepoint(0x1F600, 2)
            writer.printCodepoint(0x1F603, 2)
            writer.printCodepoint(0x1F602, 2)

            // Move cursor back to row 0 to test annihilation
            state.cursor.row = 0
            state.cursor.col = 2

            writer.printCodepoint('X'.code, 1)

            assertEquals(0x1F600, lineAt(state, 0).getCodepoint(0))
            assertEquals(TerminalConstants.WIDE_CHAR_SPACER, lineAt(state, 0).getCodepoint(1))
            assertEquals('X'.code, lineAt(state, 0).getCodepoint(2))
            // Overwriting the leader annihilated the spacer at col 3
            assertEquals(TerminalConstants.EMPTY, lineAt(state, 0).getCodepoint(3))
            assertEquals(0x1F602, lineAt(state, 0).getCodepoint(4))
        }

        @Test
        fun `clearViewport followed by write`() {
            val state = createState(width = 3, height = 2)
            val writer = MutationEngine(state)
            writeAscii(writer, "ABCDEF")

            writer.clearViewport()
            state.cursor.row = 0
            state.cursor.col = 0
            writer.printCodepoint('X'.code, 1)

            assertEquals('X'.code, lineAt(state, 0).getCodepoint(0))
            assertEquals(TerminalConstants.EMPTY, lineAt(state, 1).getCodepoint(0))
        }

        @Test
        fun `clearAllHistory and scroll`() {
            val state = createState(width = 2, height = 1, history = 3)
            val writer = MutationEngine(state)
            writeAscii(writer, "AB")
            writer.scrollUp()

            writer.clearAllHistory()

            assertEquals(1, state.ring.size)
            writer.scrollUp()

            assertTrue(state.ring.size >= 1)
        }
    }

    @Nested
    @DisplayName("eraseScreenToEnd")
    inner class EraseScreenToEndTests {

        @Test
        fun `eraseScreenToEnd clears from cursor through end of screen`() {
            val state = createState(width = 3, height = 3)
            val writer = MutationEngine(state)
            seedLine(state, 0, "AAA")
            seedLine(state, 1, "BBB")
            seedLine(state, 2, "CCC")
            state.cursor.row = 1
            state.cursor.col = 1

            writer.eraseScreenToEnd()

            assertAll(
                { assertLineCodepoints(state, 0, intArrayOf('A'.code, 'A'.code, 'A'.code)) },
                {
                    assertLineCodepoints(
                        state,
                        1,
                        intArrayOf('B'.code, TerminalConstants.EMPTY, TerminalConstants.EMPTY)
                    )
                },
                {
                    assertLineCodepoints(
                        state,
                        2,
                        intArrayOf(TerminalConstants.EMPTY, TerminalConstants.EMPTY, TerminalConstants.EMPTY)
                    )
                }
            )
        }

        @Test
        fun `eraseScreenToEnd at row 0 col 0 clears entire screen`() {
            val state = createState(width = 2, height = 3)
            val writer = MutationEngine(state)
            seedLine(state, 0, "AA")
            seedLine(state, 1, "BB")
            seedLine(state, 2, "CC")
            state.cursor.row = 0
            state.cursor.col = 0

            writer.eraseScreenToEnd()

            assertAll(
                { assertLineCodepoints(state, 0, intArrayOf(TerminalConstants.EMPTY, TerminalConstants.EMPTY)) },
                { assertLineCodepoints(state, 1, intArrayOf(TerminalConstants.EMPTY, TerminalConstants.EMPTY)) },
                { assertLineCodepoints(state, 2, intArrayOf(TerminalConstants.EMPTY, TerminalConstants.EMPTY)) }
            )
        }

        @Test
        fun `eraseScreenToEnd at last row last col clears only that cell`() {
            val state = createState(width = 3, height = 3)
            val writer = MutationEngine(state)
            seedLine(state, 0, "AAA")
            seedLine(state, 1, "BBB")
            seedLine(state, 2, "CCC")
            state.cursor.row = 2
            state.cursor.col = 2

            writer.eraseScreenToEnd()

            assertAll(
                { assertLineCodepoints(state, 0, intArrayOf('A'.code, 'A'.code, 'A'.code)) },
                { assertLineCodepoints(state, 1, intArrayOf('B'.code, 'B'.code, 'B'.code)) },
                { assertLineCodepoints(state, 2, intArrayOf('C'.code, 'C'.code, TerminalConstants.EMPTY)) }
            )
        }

        @Test
        fun `eraseScreenToEnd does not change cursor position`() {
            val state = createState(width = 3, height = 3)
            val writer = MutationEngine(state)
            seedLine(state, 0, "AAA")
            seedLine(state, 1, "BBB")
            seedLine(state, 2, "CCC")
            state.cursor.row = 1
            state.cursor.col = 1

            writer.eraseScreenToEnd()

            assertAll(
                { assertEquals(1, state.cursor.col) },
                { assertEquals(1, state.cursor.row) }
            )
        }

        @Test
        fun `eraseScreenToEnd is no-op when cursor is out of bounds`() {
            val state = createState(width = 3, height = 3)
            val writer = MutationEngine(state)
            seedLine(state, 0, "AAA")
            seedLine(state, 1, "BBB")
            seedLine(state, 2, "CCC")
            state.cursor.row = 99

            writer.eraseScreenToEnd()

            assertAll(
                { assertLineCodepoints(state, 0, intArrayOf('A'.code, 'A'.code, 'A'.code)) },
                { assertLineCodepoints(state, 1, intArrayOf('B'.code, 'B'.code, 'B'.code)) },
                { assertLineCodepoints(state, 2, intArrayOf('C'.code, 'C'.code, 'C'.code)) }
            )
        }

        @Test
        fun `eraseScreenToEnd uses current pen attribute`() {
            val state = createState(width = 3, height = 2)
            val writer = MutationEngine(state)
            seedLine(state, 0, "AAA", attr = 10)
            seedLine(state, 1, "BBB", attr = 10)
            state.pen.setAttributes(fg = 5, bg = 2, bold = true)
            val clearAttr = state.pen.currentAttr
            state.cursor.row = 0
            state.cursor.col = 1

            writer.eraseScreenToEnd()

            assertLineAttrs(state, 0, intArrayOf(10, clearAttr, clearAttr))
            assertLineAttrs(state, 1, intArrayOf(clearAttr, clearAttr, clearAttr))
        }
    }

    @Nested
    @DisplayName("eraseScreenToCursor")
    inner class EraseScreenToCursorTests {

        @Test
        fun `eraseScreenToCursor clears from start of screen through cursor`() {
            val state = createState(width = 3, height = 3)
            val writer = MutationEngine(state)
            seedLine(state, 0, "AAA")
            seedLine(state, 1, "BBB")
            seedLine(state, 2, "CCC")
            state.cursor.row = 1
            state.cursor.col = 1

            writer.eraseScreenToCursor()

            assertAll(
                {
                    assertLineCodepoints(
                        state,
                        0,
                        intArrayOf(TerminalConstants.EMPTY, TerminalConstants.EMPTY, TerminalConstants.EMPTY)
                    )
                },
                {
                    assertLineCodepoints(
                        state,
                        1,
                        intArrayOf(TerminalConstants.EMPTY, TerminalConstants.EMPTY, 'B'.code)
                    )
                },
                { assertLineCodepoints(state, 2, intArrayOf('C'.code, 'C'.code, 'C'.code)) }
            )
        }

        @Test
        fun `eraseScreenToCursor at row 0 col 0 clears only that cell`() {
            val state = createState(width = 2, height = 3)
            val writer = MutationEngine(state)
            seedLine(state, 0, "AA")
            seedLine(state, 1, "BB")
            seedLine(state, 2, "CC")
            state.cursor.row = 0
            state.cursor.col = 0

            writer.eraseScreenToCursor()

            assertAll(
                { assertLineCodepoints(state, 0, intArrayOf(TerminalConstants.EMPTY, 'A'.code)) },
                { assertLineCodepoints(state, 1, intArrayOf('B'.code, 'B'.code)) },
                { assertLineCodepoints(state, 2, intArrayOf('C'.code, 'C'.code)) }
            )
        }

        @Test
        fun `eraseScreenToCursor at last row last col clears entire screen`() {
            val state = createState(width = 3, height = 3)
            val writer = MutationEngine(state)
            seedLine(state, 0, "AAA")
            seedLine(state, 1, "BBB")
            seedLine(state, 2, "CCC")
            state.cursor.row = 2
            state.cursor.col = 2

            writer.eraseScreenToCursor()

            assertAll(
                {
                    assertLineCodepoints(
                        state,
                        0,
                        intArrayOf(TerminalConstants.EMPTY, TerminalConstants.EMPTY, TerminalConstants.EMPTY)
                    )
                },
                {
                    assertLineCodepoints(
                        state,
                        1,
                        intArrayOf(TerminalConstants.EMPTY, TerminalConstants.EMPTY, TerminalConstants.EMPTY)
                    )
                },
                {
                    assertLineCodepoints(
                        state,
                        2,
                        intArrayOf(TerminalConstants.EMPTY, TerminalConstants.EMPTY, TerminalConstants.EMPTY)
                    )
                }
            )
        }

        @Test
        fun `eraseScreenToCursor does not change cursor position`() {
            val state = createState(width = 3, height = 3)
            val writer = MutationEngine(state)
            seedLine(state, 0, "AAA")
            seedLine(state, 1, "BBB")
            seedLine(state, 2, "CCC")
            state.cursor.row = 1
            state.cursor.col = 1

            writer.eraseScreenToCursor()

            assertAll(
                { assertEquals(1, state.cursor.col) },
                { assertEquals(1, state.cursor.row) }
            )
        }

        @Test
        fun `eraseScreenToCursor is no-op when cursor is out of bounds`() {
            val state = createState(width = 3, height = 3)
            val writer = MutationEngine(state)
            seedLine(state, 0, "AAA")
            seedLine(state, 1, "BBB")
            seedLine(state, 2, "CCC")
            state.cursor.row = 99

            writer.eraseScreenToCursor()

            assertAll(
                { assertLineCodepoints(state, 0, intArrayOf('A'.code, 'A'.code, 'A'.code)) },
                { assertLineCodepoints(state, 1, intArrayOf('B'.code, 'B'.code, 'B'.code)) },
                { assertLineCodepoints(state, 2, intArrayOf('C'.code, 'C'.code, 'C'.code)) }
            )
        }

        @Test
        fun `eraseScreenToCursor uses current pen attribute`() {
            val state = createState(width = 3, height = 2)
            val writer = MutationEngine(state)
            seedLine(state, 0, "AAA", attr = 10)
            seedLine(state, 1, "BBB", attr = 10)
            state.pen.setAttributes(fg = 1, bg = 6, underline = true)
            val clearAttr = state.pen.currentAttr
            state.cursor.row = 1
            state.cursor.col = 1

            writer.eraseScreenToCursor()

            assertLineAttrs(state, 0, intArrayOf(clearAttr, clearAttr, clearAttr))
            assertLineAttrs(state, 1, intArrayOf(clearAttr, clearAttr, 10))
        }

        @Test
        fun `eraseScreenToCursor with wide character on cursor annihilates it`() {
            val state = createState(width = 4, height = 2)
            val writer = MutationEngine(state)
            writer.printCodepoint(0x1F600, 2)
            writer.printCodepoint('C'.code, 1)
            writer.printCodepoint('D'.code, 1)
            seedLine(state, 1, "EFGH")
            state.cursor.row = 1
            state.cursor.col = 2

            writer.eraseScreenToCursor()

            assertAll(
                {
                    assertLineCodepoints(
                        state,
                        0,
                        intArrayOf(
                            TerminalConstants.EMPTY,
                            TerminalConstants.EMPTY,
                            TerminalConstants.EMPTY,
                            TerminalConstants.EMPTY
                        )
                    )
                },
                {
                    assertLineCodepoints(
                        state,
                        1,
                        intArrayOf(TerminalConstants.EMPTY, TerminalConstants.EMPTY, TerminalConstants.EMPTY, 'H'.code)
                    )
                }
            )
        }
    }

    @Nested
    @DisplayName("eraseScreenAndHistory")
    inner class EraseScreenAndHistoryTests {

        @Test
        fun `eraseScreenAndHistory clears visible screen and history without moving cursor`() {
            val state = createState(width = 2, height = 2, history = 4)
            val writer = MutationEngine(state)
            seedLine(state, 0, "AA")
            seedLine(state, 1, "BB")
            writer.scrollUp()
            seedLine(state, 0, "CC")
            seedLine(state, 1, "DD")
            state.cursor.row = 1
            state.cursor.col = 1
            assertEquals(1, state.ring.size - state.dimensions.height, "Should have history before erase")

            writer.eraseScreenAndHistory()

            assertAll(
                { assertEquals(state.dimensions.height, state.ring.size, "Ring should have only viewport height") },
                { assertLineCodepoints(state, 0, intArrayOf(TerminalConstants.EMPTY, TerminalConstants.EMPTY)) },
                { assertLineCodepoints(state, 1, intArrayOf(TerminalConstants.EMPTY, TerminalConstants.EMPTY)) },
                { assertEquals(1, state.cursor.col, "Cursor column must not change") },
                { assertEquals(1, state.cursor.row, "Cursor row must not change") }
            )
        }

        @Test
        fun `eraseScreenAndHistory uses current pen attribute`() {
            val state = createState(width = 2, height = 2, history = 2)
            val writer = MutationEngine(state)
            seedLine(state, 0, "AA")
            seedLine(state, 1, "BB")
            state.pen.setAttributes(fg = 7, bg = 6, underline = true)
            val clearAttr = state.pen.currentAttr

            writer.eraseScreenAndHistory()

            assertLineAttrs(state, 0, intArrayOf(clearAttr, clearAttr))
            assertLineAttrs(state, 1, intArrayOf(clearAttr, clearAttr))
        }
    }
}
