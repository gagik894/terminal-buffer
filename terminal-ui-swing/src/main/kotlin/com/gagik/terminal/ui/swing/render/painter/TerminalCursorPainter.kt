package com.gagik.terminal.ui.swing.render.painter

import com.gagik.terminal.render.api.TerminalColorPalette
import com.gagik.terminal.render.api.TerminalRenderCursorShape
import com.gagik.terminal.render.cache.TerminalRenderCache
import com.gagik.terminal.ui.swing.render.cache.AwtColorCache
import com.gagik.terminal.ui.swing.render.visualCellRangeSpan
import com.gagik.terminal.ui.swing.render.visualCellRangeStart
import com.gagik.terminal.ui.swing.settings.TerminalSwingMetrics
import java.awt.Graphics2D
import java.awt.font.FontRenderContext

/**
 * Paints terminal cursor shapes and block-cursor foreground text.
 */
internal class TerminalCursorPainter(
    private val colorCache: AwtColorCache,
    private val textPainter: TerminalTextPainter,
) {
    /**
     * Paints the current cursor from [cache].
     */
    fun paint(
        g: Graphics2D,
        cache: TerminalRenderCache,
        palette: TerminalColorPalette,
        metrics: TerminalSwingMetrics,
        cursorBlinkVisible: Boolean,
        fontRenderContext: FontRenderContext,
    ) {
        if (!cache.cursorVisible || (cache.cursorBlinking && !cursorBlinkVisible)) return
        if (cache.cursorColumn !in 0 until cache.columns || cache.cursorRow !in 0 until cache.rows) return

        val cursorIndex = cache.rowOffset(cache.cursorRow) + cache.cursorColumn
        val cursorFlags = cache.flags[cursorIndex]
        val startColumn = visualCellRangeStart(cursorFlags, cache.cursorColumn)
        val columnSpan = visualCellRangeSpan(cursorFlags, cache.cursorColumn, cache.columns)
        val x = startColumn * metrics.cellWidth
        val y = cache.cursorRow * metrics.cellHeight
        val width = columnSpan * metrics.cellWidth
        g.color = colorCache.color(palette.cursorBackground)

        when (cache.cursorShape) {
            TerminalRenderCursorShape.BLOCK -> g.fillRect(x, y, width, metrics.cellHeight)
            TerminalRenderCursorShape.UNDERLINE -> {
                g.fillRect(
                    x,
                    y + metrics.cellHeight - metrics.cursorStrokeWidth,
                    width,
                    metrics.cursorStrokeWidth,
                )
            }
            TerminalRenderCursorShape.BAR -> {
                g.fillRect(x, y, metrics.cursorStrokeWidth, metrics.cellHeight)
            }
        }

        if (cache.cursorShape == TerminalRenderCursorShape.BLOCK) {
            textPainter.paintCellForeground(
                g = g,
                cache = cache,
                metrics = metrics,
                column = startColumn,
                row = cache.cursorRow,
                columnSpan = columnSpan,
                foreground = palette.cursorForeground,
                fontRenderContext = fontRenderContext,
            )
        }
    }
}
