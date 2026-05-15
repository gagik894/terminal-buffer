package com.gagik.terminal.ui.swing.render.painter

import com.gagik.terminal.render.api.TerminalRenderAttrs
import com.gagik.terminal.render.api.TerminalRenderCellFlags
import com.gagik.terminal.render.cache.TerminalRenderCache
import com.gagik.terminal.ui.swing.render.cache.*
import com.gagik.terminal.ui.swing.render.cellSpan
import com.gagik.terminal.ui.swing.render.font.TerminalTextRunBuffer
import com.gagik.terminal.ui.swing.render.hasDrawableText
import com.gagik.terminal.ui.swing.render.isFastAsciiCell
import com.gagik.terminal.ui.swing.render.primitives.TerminalCellPrimitivePainter
import com.gagik.terminal.ui.swing.render.terminalFontStyle
import com.gagik.terminal.ui.swing.settings.TerminalColorPalette
import com.gagik.terminal.ui.swing.settings.TerminalSwingMetrics
import com.gagik.terminal.ui.swing.settings.TerminalSwingSettings
import java.awt.Font
import java.awt.Graphics2D
import java.awt.font.FontRenderContext

/**
 * Paints terminal cell text runs and text-only cursor foreground.
 */
internal class TerminalTextPainter(
    private val colorCache: AwtColorCache,
    private val decorationPainter: TerminalDecorationPainter,
) {
    private val fontCache = TerminalFontCache()
    private val complexTextLayouts = TerminalComplexTextLayoutCache()
    private val asciiGlyphVectors = TerminalAsciiGlyphVectorCache()
    private val asciiDrawChars = TerminalAsciiDrawCharsCache()
    private val cellPrimitives = TerminalCellPrimitivePainter()
    private val textRun = TerminalTextRunBuffer(INITIAL_TEXT_RUN_CAPACITY)

    /**
     * Updates font-dependent caches for a settings snapshot.
     */
    fun updateSettings(settings: TerminalSwingSettings) {
        if (fontCache.update(settings.font, settings.fallbackFonts, settings.useSystemFallbackFonts)) {
            complexTextLayouts.clear()
            asciiGlyphVectors.clear()
            asciiDrawChars.clear()
        }
    }

    /**
     * Returns a cached font for [style].
     */
    fun font(style: Int): Font = fontCache.font(style)

    /**
     * Paints all drawable text runs in [row].
     */
    fun paintRow(
        g: Graphics2D,
        cache: TerminalRenderCache,
        palette: TerminalColorPalette,
        metrics: TerminalSwingMetrics,
        row: Int,
        fontRenderContext: FontRenderContext,
    ) {
        val flagsRow = cache.flags[row]
        val codeWordRow = cache.codeWords[row]
        val baselineY = row * metrics.cellHeight + metrics.baseline
        var column = 0

        while (column < cache.columns) {
            val flags = flagsRow[column]
            if (!hasDrawableText(flags)) {
                column++
                continue
            }

            val codeWord = codeWordRow[column]
            column = if (isFastAsciiCell(flags, codeWord)) {
                paintAsciiRun(
                    g = g,
                    cache = cache,
                    palette = palette,
                    metrics = metrics,
                    row = row,
                    startColumn = column,
                    baselineY = baselineY,
                    fontRenderContext = fontRenderContext,
                )
            } else {
                paintComplexCell(
                    g = g,
                    cache = cache,
                    palette = palette,
                    metrics = metrics,
                    row = row,
                    column = column,
                    baselineY = baselineY,
                    fontRenderContext = fontRenderContext,
                )
            }
        }
    }

    /**
     * Paints one cell's text clipped to a block cursor cell.
     */
    fun paintCellForeground(
        g: Graphics2D,
        cache: TerminalRenderCache,
        palette: TerminalColorPalette,
        metrics: TerminalSwingMetrics,
        column: Int,
        row: Int,
        foreground: Int,
        fontRenderContext: FontRenderContext,
    ) {
        val flags = cache.flags[row][column]
        if (!hasDrawableText(flags)) return

        val attr = cache.attrWords[row][column]
        val oldClip = g.clip
        try {
            g.clipRect(column * metrics.cellWidth, row * metrics.cellHeight, metrics.cellWidth, metrics.cellHeight)
            g.font = fontCache.font(terminalFontStyle(attr))
            g.color = colorCache.color(foreground)

            val baselineY = row * metrics.cellHeight + metrics.baseline
            val codeWord = cache.codeWords[row][column]
            if (flags and TerminalRenderCellFlags.CLUSTER == 0 && cellPrimitives.canPaint(codeWord)) {
                cellPrimitives.paint(g, codeWord, column, row, metrics)
            } else if (flags and TerminalRenderCellFlags.CLUSTER != 0) {
                val cluster = cache.clusters[row][column]
                if (cluster != null) {
                    drawComplexCluster(
                        g = g,
                        text = cluster,
                        fontStyle = terminalFontStyle(attr),
                        x = column * metrics.cellWidth,
                        baselineY = baselineY,
                        fontRenderContext = fontRenderContext,
                    )
                }
            } else {
                drawComplexCodePoint(
                    g = g,
                    codePoint = codeWord,
                    fontStyle = terminalFontStyle(attr),
                    x = column * metrics.cellWidth,
                    baselineY = baselineY,
                    fontRenderContext = fontRenderContext,
                )
            }
        } finally {
            g.clip = oldClip
        }
    }

    private fun paintAsciiRun(
        g: Graphics2D,
        cache: TerminalRenderCache,
        palette: TerminalColorPalette,
        metrics: TerminalSwingMetrics,
        row: Int,
        startColumn: Int,
        baselineY: Int,
        fontRenderContext: FontRenderContext,
    ): Int {
        val flagsRow = cache.flags[row]
        val attrRow = cache.attrWords[row]
        val extraAttrRow = cache.extraAttrWords[row]
        val codeWordRow = cache.codeWords[row]
        val attr = attrRow[startColumn]
        val extraAttr = extraAttrRow[startColumn]
        val foreground = palette.foreground(attr)
        val fontStyle = terminalFontStyle(attr)
        val decoration = decorationKey(attr, extraAttr)
        var column = startColumn

        textRun.clear()
        while (column < cache.columns) {
            val flags = flagsRow[column]
            val codeWord = codeWordRow[column]
            val currentAttr = attrRow[column]
            val currentExtraAttr = extraAttrRow[column]
            if (
                !isFastAsciiCell(flags, codeWord) ||
                palette.foreground(currentAttr) != foreground ||
                terminalFontStyle(currentAttr) != fontStyle ||
                decorationKey(currentAttr, currentExtraAttr) != decoration
            ) {
                break
            }

            textRun.appendAscii(codeWord)
            column++
        }

        g.font = fontCache.font(fontStyle)
        g.color = colorCache.color(foreground)
        drawAsciiRun(g, metrics, startColumn, baselineY, fontStyle, fontRenderContext)
        decorationPainter.paint(g, palette, attr, extraAttr, foreground, startColumn, column, row, metrics)
        return column
    }

    private fun paintComplexCell(
        g: Graphics2D,
        cache: TerminalRenderCache,
        palette: TerminalColorPalette,
        metrics: TerminalSwingMetrics,
        row: Int,
        column: Int,
        baselineY: Int,
        fontRenderContext: FontRenderContext,
    ): Int {
        val flags = cache.flags[row][column]
        val attr = cache.attrWords[row][column]
        val extraAttr = cache.extraAttrWords[row][column]
        val foreground = palette.foreground(attr)
        val fontStyle = terminalFontStyle(attr)
        val endColumn = minOf(cache.columns, column + cellSpan(flags))

        g.font = fontCache.font(fontStyle)
        g.color = colorCache.color(foreground)

        val codeWord = cache.codeWords[row][column]
        if (flags and TerminalRenderCellFlags.CLUSTER == 0 && cellPrimitives.canPaint(codeWord)) {
            cellPrimitives.paint(g, codeWord, column, row, metrics)
        } else if (flags and TerminalRenderCellFlags.CLUSTER != 0) {
            val cluster = cache.clusters[row][column]
            if (cluster != null) {
                drawComplexCluster(g, cluster, fontStyle, column * metrics.cellWidth, baselineY, fontRenderContext)
            }
        } else {
            drawComplexCodePoint(
                g = g,
                codePoint = codeWord,
                fontStyle = fontStyle,
                x = column * metrics.cellWidth,
                baselineY = baselineY,
                fontRenderContext = fontRenderContext,
            )
        }

        decorationPainter.paint(g, palette, attr, extraAttr, foreground, column, endColumn, row, metrics)
        return endColumn
    }

    private fun drawAsciiRun(
        g: Graphics2D,
        metrics: TerminalSwingMetrics,
        startColumn: Int,
        baselineY: Int,
        fontStyle: Int,
        fontRenderContext: FontRenderContext,
    ) {
        if (asciiDrawChars.canDrawChars(g.font, fontStyle, metrics.cellWidth, fontRenderContext)) {
            g.drawChars(textRun.chars, 0, textRun.length, startColumn * metrics.cellWidth, baselineY)
            return
        }

        val glyphVector = asciiGlyphVectors.glyphVector(
            chars = textRun.chars,
            offset = 0,
            length = textRun.length,
            font = g.font,
            style = fontStyle,
            cellWidth = metrics.cellWidth,
            fontRenderContext = fontRenderContext,
        )
        g.drawGlyphVector(glyphVector, (startColumn * metrics.cellWidth).toFloat(), baselineY.toFloat())
    }

    private fun drawComplexCluster(
        g: Graphics2D,
        text: String,
        fontStyle: Int,
        x: Int,
        baselineY: Int,
        fontRenderContext: FontRenderContext,
    ) {
        complexTextLayouts
            .clusterLayout(text, fontStyle, fontRenderContext, fontCache)
            .draw(g, x.toFloat(), baselineY.toFloat())
    }

    private fun drawComplexCodePoint(
        g: Graphics2D,
        codePoint: Int,
        fontStyle: Int,
        x: Int,
        baselineY: Int,
        fontRenderContext: FontRenderContext,
    ) {
        complexTextLayouts
            .codePointLayout(codePoint, fontStyle, fontRenderContext, fontCache)
            .draw(g, x.toFloat(), baselineY.toFloat())
    }

    private fun decorationKey(attr: Long, extraAttr: Long): Long {
        return TerminalRenderAttrs.underlineStyle(attr).toLong() or
            (if (TerminalRenderAttrs.isStrikethrough(attr)) STRIKETHROUGH_KEY else 0L) or
            (extraAttr shl EXTRA_ATTR_KEY_SHIFT)
    }

    private companion object {
        private const val INITIAL_TEXT_RUN_CAPACITY = 256
        private const val STRIKETHROUGH_KEY = 1L shl 8
        private const val EXTRA_ATTR_KEY_SHIFT = 9
    }
}