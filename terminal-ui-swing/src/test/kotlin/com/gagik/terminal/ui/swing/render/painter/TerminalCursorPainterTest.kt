package com.gagik.terminal.ui.swing.render.painter

import com.gagik.terminal.render.api.TerminalRenderCellFlags
import com.gagik.terminal.render.api.TerminalRenderCursor
import com.gagik.terminal.render.api.TerminalRenderCursorShape
import com.gagik.terminal.render.cache.TerminalRenderCache
import com.gagik.terminal.ui.swing.render.*
import com.gagik.terminal.ui.swing.render.cache.AwtColorCache
import com.gagik.terminal.ui.swing.settings.TerminalSwingMetrics
import com.gagik.terminal.ui.swing.settings.TerminalSwingSettings
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TerminalCursorPainterTest {
    @Nested
    inner class Visibility {
        @Test
        fun `does not paint invisible cursor`() {
            val fixture = fixture(cursor(visible = false))

            fixture.paint()

            assertTrue(!fixture.image.containsColor(TEST_BLUE, fixture.metrics.cellWidth, fixture.metrics.cellHeight))
        }

        @Test
        fun `does not paint blinking cursor when blink is hidden`() {
            val fixture = fixture(cursor(blinking = true))

            fixture.paint(cursorBlinkVisible = false)

            assertTrue(!fixture.image.containsColor(TEST_BLUE, fixture.metrics.cellWidth, fixture.metrics.cellHeight))
        }

        @Test
        fun `ignores cursor outside cache bounds`() {
            val fixture = fixture(cursor(column = 2))

            fixture.paint()

            assertTrue(!fixture.image.containsColor(TEST_BLUE, fixture.metrics.cellWidth, fixture.metrics.cellHeight))
        }
    }

    @Nested
    inner class Shapes {
        @Test
        fun `block cursor fills full cell and redraws foreground`() {
            val fixture = fixture(cursor(shape = TerminalRenderCursorShape.BLOCK))

            fixture.paint()

            assertEquals(TEST_BLUE, fixture.image.getRGB(1, 1))
            assertTrue(fixture.image.containsColor(TEST_RED, fixture.metrics.cellWidth, fixture.metrics.cellHeight))
        }

        @Test
        fun `underline cursor fills bottom stroke only`() {
            val fixture = fixture(cursor(shape = TerminalRenderCursorShape.UNDERLINE))

            fixture.paint()

            assertEquals(TEST_BLUE, fixture.image.getRGB(1, fixture.metrics.cellHeight - 1))
            assertEquals(TEST_BLACK, fixture.image.getRGB(1, 1))
        }

        @Test
        fun `bar cursor fills leading stroke`() {
            val fixture = fixture(cursor(shape = TerminalRenderCursorShape.BAR))

            fixture.paint()

            assertEquals(TEST_BLUE, fixture.image.getRGB(0, 1))
            assertEquals(TEST_BLACK, fixture.image.getRGB(fixture.metrics.cellWidth - 1, 1))
        }
    }

    private data class Fixture(
        val image: BufferedImage,
        val g: Graphics2D,
        val settings: TerminalSwingSettings,
        val metrics: TerminalSwingMetrics,
        val cache: TerminalRenderCache,
        val painter: TerminalCursorPainter,
        val textPainter: TerminalTextPainter,
    ) {
        fun paint(cursorBlinkVisible: Boolean = true) {
            textPainter.updateSettings(settings)
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, settings.textAntialiasing)
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, settings.fractionalMetrics)
            painter.paint(g, cache, settings.palette, metrics, cursorBlinkVisible, g.fontRenderContext)
        }
    }

    private fun fixture(cursor: TerminalRenderCursor): Fixture {
        val image = BufferedImage(50, 30, BufferedImage.TYPE_INT_ARGB)
        val settings = defaultTestSettings(foreground = TEST_WHITE, background = TEST_BLACK)
        val metrics = testMetrics(image, settings)
        val colorCache = AwtColorCache()
        val textPainter = TerminalTextPainter(colorCache, TerminalDecorationPainter(colorCache))
        val cache = renderCache(TestRenderFrame.text("A").copyWithCursor(cursor))
        return Fixture(
            image = image,
            g = image.createGraphics().also { it.color = colorCache.color(TEST_BLACK); it.fillRect(0, 0, image.width, image.height) },
            settings = settings,
            metrics = metrics,
            cache = cache,
            painter = TerminalCursorPainter(colorCache, textPainter),
            textPainter = textPainter,
        )
    }

    private fun cursor(
        column: Int = 0,
        row: Int = 0,
        visible: Boolean = true,
        blinking: Boolean = false,
        shape: TerminalRenderCursorShape = TerminalRenderCursorShape.BLOCK,
    ): TerminalRenderCursor {
        return TerminalRenderCursor(
            column = column,
            row = row,
            visible = visible,
            blinking = blinking,
            shape = shape,
            generation = 1,
        )
    }

    private fun TestRenderFrame.copyWithCursor(cursor: TerminalRenderCursor): TestRenderFrame {
        return TestRenderFrame(
            cells = arrayOf(arrayOf(TestCell(codeWord = 'A'.code, flags = TerminalRenderCellFlags.CODEPOINT))),
            cursorValue = cursor,
        )
    }
}
