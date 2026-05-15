package com.gagik.terminal.ui.swing.render.painter

import com.gagik.terminal.render.cache.TerminalRenderCache
import com.gagik.terminal.ui.swing.render.cache.AwtColorCache
import com.gagik.terminal.ui.swing.settings.TerminalColorPalette
import com.gagik.terminal.ui.swing.settings.TerminalSwingMetrics
import java.awt.Graphics2D

/**
 * Paints terminal default clears and row background runs.
 */
internal class TerminalBackgroundPainter(
    private val colorCache: AwtColorCache,
) {
    /**
     * Clears a rectangular component area with the palette default background.
     */
    fun clear(
        g: Graphics2D,
        palette: TerminalColorPalette,
        width: Int,
        height: Int,
    ) {
        fill(g, 0, 0, width, height, palette.defaultBackground)
    }

    /**
     * Paints contiguous background runs for [row].
     */
    fun paintRow(
        g: Graphics2D,
        cache: TerminalRenderCache,
        palette: TerminalColorPalette,
        metrics: TerminalSwingMetrics,
        row: Int,
    ) {
        val attrRow = cache.attrWords[row]
        val y = row * metrics.cellHeight
        var column = 0
        while (column < cache.columns) {
            val background = palette.background(attrRow[column])
            val start = column

            column++
            while (column < cache.columns && palette.background(attrRow[column]) == background) {
                column++
            }

            fill(
                g = g,
                x = start * metrics.cellWidth,
                y = y,
                width = (column - start) * metrics.cellWidth,
                height = metrics.cellHeight,
                argb = background,
            )
        }
    }

    private fun fill(g: Graphics2D, x: Int, y: Int, width: Int, height: Int, argb: Int) {
        g.color = colorCache.color(argb)
        g.fillRect(x, y, width, height)
    }
}