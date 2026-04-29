package com.gagik.core.render

import com.gagik.core.buffer.TerminalBuffer
import com.gagik.core.model.AttributeColor
import com.gagik.core.model.UnderlineStyle
import com.gagik.terminal.render.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CoreTerminalRenderFrameTest {
    @Test
    fun `terminal buffer exposes render frame reader callback`() {
        val buffer = TerminalBuffer(initialWidth = 3, initialHeight = 2)
        val reader = buffer as TerminalRenderFrameReader

        reader.readRenderFrame { frame ->
            assertAll(
                { assertEquals(3, frame.columns) },
                { assertEquals(2, frame.rows) },
                { assertEquals(TerminalRenderBufferKind.PRIMARY, frame.activeBuffer) },
                { assertEquals(0, frame.cursor.column) },
                { assertEquals(0, frame.cursor.row) },
                { assertTrue(frame.cursor.visible) },
                { assertFalse(frame.cursor.blinking) },
                { assertEquals(TerminalRenderCursorShape.BLOCK, frame.cursor.shape) },
            )
        }
    }

    @Test
    fun `empty line copies empty flags and default attrs`() {
        val buffer = TerminalBuffer(initialWidth = 3, initialHeight = 1)
        val reader = buffer as TerminalRenderFrameReader

        reader.readRenderFrame { frame ->
            val row = copyRow(frame)

            assertAll(
                { assertEquals(listOf(0, 0, 0), row.codeWords.toList()) },
                {
                    assertEquals(
                        listOf(
                            TerminalRenderCellFlags.EMPTY,
                            TerminalRenderCellFlags.EMPTY,
                            TerminalRenderCellFlags.EMPTY,
                        ),
                        row.flags.toList(),
                    )
                },
                { assertTrue(row.attrWords.all { it == TerminalRenderAttrs.DEFAULT }) },
            )
        }
    }

    @Test
    fun `ascii cells copy as codepoints`() {
        val buffer = TerminalBuffer(initialWidth = 4, initialHeight = 1)
        buffer.writeText("AB")
        val reader = buffer as TerminalRenderFrameReader

        reader.readRenderFrame { frame ->
            val row = copyRow(frame)

            assertAll(
                { assertEquals('A'.code, row.codeWords[0]) },
                { assertEquals('B'.code, row.codeWords[1]) },
                { assertEquals(TerminalRenderCellFlags.CODEPOINT, row.flags[0]) },
                { assertEquals(TerminalRenderCellFlags.CODEPOINT, row.flags[1]) },
                { assertEquals(TerminalRenderCellFlags.EMPTY, row.flags[2]) },
            )
        }
    }

    @Test
    fun `wide codepoint copies leader and trailing flags`() {
        val buffer = TerminalBuffer(initialWidth = 4, initialHeight = 1)
        buffer.writeCodepoint(0x1F600)
        val reader = buffer as TerminalRenderFrameReader

        reader.readRenderFrame { frame ->
            val row = copyRow(frame)

            assertAll(
                {
                    assertEquals(
                        TerminalRenderCellFlags.CODEPOINT or TerminalRenderCellFlags.WIDE_LEADING,
                        row.flags[0],
                    )
                },
                { assertEquals(TerminalRenderCellFlags.WIDE_TRAILING, row.flags[1]) },
                { assertEquals(0x1F600, row.codeWords[0]) },
                { assertEquals(0, row.codeWords[1]) },
            )
        }
    }

    @Test
    fun `cluster cells call sink and copy stable cluster flags`() {
        val buffer = TerminalBuffer(initialWidth = 4, initialHeight = 1)
        buffer.writeCluster(intArrayOf('e'.code, 0x0301), length = 2)
        val reader = buffer as TerminalRenderFrameReader

        reader.readRenderFrame { frame ->
            val clusters = mutableMapOf<Int, String>()
            val row = copyRow(frame) { col, text -> clusters[col] = text }

            assertAll(
                { assertEquals(TerminalRenderCellFlags.CLUSTER, row.flags[0]) },
                { assertEquals(0, row.codeWords[0]) },
                { assertEquals("e\u0301", clusters[0]) },
            )
        }
    }

    @Test
    fun `wide cluster copies leader and trailing flags`() {
        val buffer = TerminalBuffer(initialWidth = 4, initialHeight = 1)
        buffer.writeCluster(intArrayOf(0x1F468, 0x200D, 0x1F469), length = 3)
        val reader = buffer as TerminalRenderFrameReader

        reader.readRenderFrame { frame ->
            val clusters = mutableMapOf<Int, String>()
            val row = copyRow(frame) { col, text -> clusters[col] = text }

            assertAll(
                {
                    assertEquals(
                        TerminalRenderCellFlags.CLUSTER or TerminalRenderCellFlags.WIDE_LEADING,
                        row.flags[0],
                    )
                },
                { assertEquals(TerminalRenderCellFlags.WIDE_TRAILING, row.flags[1]) },
                { assertEquals(String(intArrayOf(0x1F468, 0x200D, 0x1F469), 0, 3), clusters[0]) },
            )
        }
    }

    @Test
    fun `cell attributes translate to public render ABI`() {
        val buffer = TerminalBuffer(initialWidth = 2, initialHeight = 1)
        buffer.setPenColors(
            foreground = AttributeColor.rgb(0x12_34_56),
            background = AttributeColor.indexed(42),
            underlineColor = AttributeColor.rgb(0x65_43_21),
            bold = true,
            faint = true,
            italic = true,
            underlineStyle = UnderlineStyle.DASHED,
            strikethrough = true,
            overline = true,
            blink = true,
            inverse = true,
            conceal = true,
        )
        buffer.writeCodepoint('X'.code)
        val reader = buffer as TerminalRenderFrameReader

        reader.readRenderFrame { frame ->
            val row = copyRow(frame)
            val attr = row.attrWords[0]
            val extra = row.extraAttrWords[0]

            assertAll(
                { assertEquals(TerminalRenderColorKind.RGB, TerminalRenderAttrs.foregroundKind(attr)) },
                { assertEquals(0x12_34_56, TerminalRenderAttrs.foregroundValue(attr)) },
                { assertEquals(TerminalRenderColorKind.INDEXED, TerminalRenderAttrs.backgroundKind(attr)) },
                { assertEquals(42, TerminalRenderAttrs.backgroundValue(attr)) },
                { assertTrue(TerminalRenderAttrs.isBold(attr)) },
                { assertTrue(TerminalRenderAttrs.isFaint(attr)) },
                { assertTrue(TerminalRenderAttrs.isItalic(attr)) },
                { assertEquals(TerminalRenderUnderline.DASHED, TerminalRenderAttrs.underlineStyle(attr)) },
                { assertTrue(TerminalRenderAttrs.isBlink(attr)) },
                { assertTrue(TerminalRenderAttrs.isInverse(attr)) },
                { assertTrue(TerminalRenderAttrs.isInvisible(attr)) },
                { assertTrue(TerminalRenderAttrs.isStrikethrough(attr)) },
                { assertEquals(TerminalRenderColorKind.RGB, TerminalRenderExtraAttrs.underlineColorKind(extra)) },
                { assertEquals(0x65_43_21, TerminalRenderExtraAttrs.underlineColorValue(extra)) },
                { assertTrue(TerminalRenderExtraAttrs.isOverline(extra)) },
            )
        }
    }

    @Test
    fun `reverse video is reflected in copied public attrs`() {
        val buffer = TerminalBuffer(initialWidth = 2, initialHeight = 1)
        buffer.writeCodepoint('X'.code)
        buffer.setReverseVideo(true)
        val reader = buffer as TerminalRenderFrameReader

        reader.readRenderFrame { frame ->
            val row = copyRow(frame)

            assertTrue(TerminalRenderAttrs.isInverse(row.attrWords[0]))
        }
    }

    @Test
    fun `hyperlink ids copy through optional array`() {
        val buffer = TerminalBuffer(initialWidth = 2, initialHeight = 1)
        buffer.setHyperlinkId(77)
        buffer.writeCodepoint('X'.code)
        val reader = buffer as TerminalRenderFrameReader

        reader.readRenderFrame { frame ->
            val row = copyRow(frame)

            assertEquals(77, row.hyperlinkIds[0])
        }
    }

    @Test
    fun `line metadata exposes generations and wrap flag`() {
        val buffer = TerminalBuffer(initialWidth = 2, initialHeight = 2)
        val reader = buffer as TerminalRenderFrameReader
        var oldFrame = 0L
        var oldLine = 0L

        reader.readRenderFrame { before ->
            oldFrame = before.frameGeneration
            oldLine = before.lineGeneration(0)
        }

        buffer.writeText("AB")
        buffer.writeCodepoint('C'.code)

        reader.readRenderFrame { after ->
            assertAll(
                { assertNotEquals(oldFrame, after.frameGeneration) },
                { assertNotEquals(oldLine, after.lineGeneration(0)) },
                { assertTrue(after.lineWrapped(0)) },
            )
        }
    }

    @Test
    fun `active buffer reports alternate after switch`() {
        val buffer = TerminalBuffer(initialWidth = 2, initialHeight = 1)
        val reader = buffer as TerminalRenderFrameReader

        buffer.enterAltBuffer()

        reader.readRenderFrame { frame ->
            assertEquals(TerminalRenderBufferKind.ALTERNATE, frame.activeBuffer)
        }
    }

    @Test
    fun `copyLine validates row and destination capacity`() {
        val buffer = TerminalBuffer(initialWidth = 2, initialHeight = 1)
        val reader = buffer as TerminalRenderFrameReader

        reader.readRenderFrame { frame ->
            assertAll(
                {
                    assertThrows(IllegalArgumentException::class.java) {
                        frame.lineGeneration(1)
                    }
                },
                {
                    assertThrows(IllegalArgumentException::class.java) {
                        frame.copyLine(
                            row = 0,
                            codeWords = IntArray(1),
                            attrWords = LongArray(2),
                            flags = IntArray(2),
                        )
                    }
                },
            )
        }
    }

    private fun copyRow(
        frame: com.gagik.terminal.render.api.TerminalRenderFrame,
        clusterSink: ((Int, String) -> Unit)? = null,
    ): CopiedRow {
        val row = CopiedRow(frame.columns)
        frame.copyLine(
            row = 0,
            codeWords = row.codeWords,
            attrWords = row.attrWords,
            flags = row.flags,
            extraAttrWords = row.extraAttrWords,
            hyperlinkIds = row.hyperlinkIds,
            clusterSink = if (clusterSink == null) {
                null
            } else {
                com.gagik.terminal.render.api.TerminalRenderClusterSink(clusterSink)
            },
        )
        row.flags.forEach {
            assertTrue(TerminalRenderCellFlags.isValidCombination(it), "invalid flag combination: $it")
        }
        return row
    }

    private class CopiedRow(columns: Int) {
        val codeWords = IntArray(columns)
        val attrWords = LongArray(columns)
        val flags = IntArray(columns)
        val extraAttrWords = LongArray(columns)
        val hyperlinkIds = IntArray(columns)
    }
}
