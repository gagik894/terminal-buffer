package com.gagik.terminal.ui.swing.render.primitives

import com.gagik.terminal.ui.swing.settings.TerminalSwingMetrics
import java.awt.Graphics2D

/**
 * Routes terminal cell-native glyphs to allocation-free primitive painters.
 */
internal class TerminalCellPrimitivePainter {
    private val boxDrawingPainter = TerminalBoxDrawingPainter()
    private val blockElementPainter = TerminalBlockElementPainter()

    /**
     * Returns true when [codePoint] is handled by this primitive painter.
     */
    fun canPaint(codePoint: Int): Boolean {
        return TerminalBoxDrawingGlyphs.canPaint(codePoint) ||
            TerminalBlockElementGlyphs.canPaint(codePoint)
    }

    /**
     * Paints one supported cell-native glyph.
     */
    fun paint(
        g: Graphics2D,
        codePoint: Int,
        column: Int,
        row: Int,
        metrics: TerminalSwingMetrics,
    ) {
        val x = column * metrics.cellWidth
        val y = row * metrics.cellHeight
        when {
            TerminalBoxDrawingGlyphs.canPaint(codePoint) -> {
                boxDrawingPainter.paint(g, codePoint, x, y, metrics.cellWidth, metrics.cellHeight)
            }

            TerminalBlockElementGlyphs.canPaint(codePoint) -> {
                blockElementPainter.paint(g, codePoint, x, y, metrics.cellWidth, metrics.cellHeight)
            }
        }
    }
}
