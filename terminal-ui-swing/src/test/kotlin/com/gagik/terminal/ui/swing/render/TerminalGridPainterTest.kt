package com.gagik.terminal.ui.swing.render

import com.gagik.terminal.render.api.*
import com.gagik.terminal.render.cache.TerminalRenderCache
import com.gagik.terminal.ui.swing.settings.TerminalColorPalette
import com.gagik.terminal.ui.swing.settings.TerminalSwingMetrics
import com.gagik.terminal.ui.swing.settings.TerminalSwingSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage

class TerminalGridPainterTest {
    @Test
    fun `block cursor redraws covered glyph with cursor foreground`() {
        val image = BufferedImage(40, 30, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        val settings = TerminalSwingSettings(
            font = Font(Font.MONOSPACED, Font.PLAIN, 14),
            palette = TerminalColorPalette(
                defaultForeground = WHITE,
                defaultBackground = BLACK,
                cursorForeground = RED,
                cursorBackground = BLUE,
            ),
            textAntialiasing = RenderingHints.VALUE_TEXT_ANTIALIAS_OFF,
        )
        val metrics = TerminalSwingMetrics.from(g.getFontMetrics(settings.font))
        val cache = TerminalRenderCache(columns = 1, rows = 1)
        cache.updateFrom(SingleCellFrame)

        TerminalGridPainter().paint(
            g = g,
            cache = cache,
            settings = settings,
            metrics = metrics,
            width = image.width,
            height = image.height,
            cursorBlinkVisible = true,
        )
        g.dispose()

        assertEquals(BLUE, image.getRGB(1, 1))
        assertTrue(
            image.containsColor(RED, metrics.cellWidth, metrics.cellHeight),
            "cursor foreground glyph was not painted over the block cursor",
        )
    }

    private fun BufferedImage.containsColor(argb: Int, width: Int, height: Int): Boolean {
        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                if (getRGB(x, y) == argb) return true
                x++
            }
            y++
        }
        return false
    }

    private object SingleCellFrame : TerminalRenderFrameReader, TerminalRenderFrame {
        override val columns: Int = 1
        override val rows: Int = 1
        override val frameGeneration: Long = 1
        override val structureGeneration: Long = 1
        override val activeBuffer: TerminalRenderBufferKind = TerminalRenderBufferKind.PRIMARY
        override val cursor: TerminalRenderCursor = TerminalRenderCursor(
            column = 0,
            row = 0,
            visible = true,
            blinking = false,
            shape = TerminalRenderCursorShape.BLOCK,
            generation = 1,
        )

        override fun readRenderFrame(consumer: TerminalRenderFrameConsumer) {
            consumer.accept(this)
        }

        override fun lineGeneration(row: Int): Long = 1

        override fun lineWrapped(row: Int): Boolean = false

        override fun copyLine(
            row: Int,
            codeWords: IntArray,
            codeOffset: Int,
            attrWords: LongArray,
            attrOffset: Int,
            flags: IntArray,
            flagOffset: Int,
            extraAttrWords: LongArray?,
            extraAttrOffset: Int,
            hyperlinkIds: IntArray?,
            hyperlinkOffset: Int,
            clusterSink: TerminalRenderClusterSink?,
        ) {
            codeWords[codeOffset] = 'A'.code
            attrWords[attrOffset] = TerminalRenderAttrs.DEFAULT
            flags[flagOffset] = TerminalRenderCellFlags.CODEPOINT
            extraAttrWords?.set(extraAttrOffset, TerminalRenderExtraAttrs.DEFAULT)
            hyperlinkIds?.set(hyperlinkOffset, 0)
        }
    }

    private companion object {
        private const val BLACK = 0xFF000000.toInt()
        private const val WHITE = 0xFFFFFFFF.toInt()
        private const val RED = 0xFFFF0000.toInt()
        private const val BLUE = 0xFF0000FF.toInt()
    }
}
