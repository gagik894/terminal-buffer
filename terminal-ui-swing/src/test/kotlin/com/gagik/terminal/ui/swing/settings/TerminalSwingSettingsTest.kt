package com.gagik.terminal.ui.swing.settings

import com.gagik.terminal.ui.swing.api.TerminalSwingTerminal
import java.awt.Canvas
import java.awt.Font
import java.awt.RenderingHints
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TerminalSwingSettingsTest {
    @Test
    fun settingsRejectInvalidGridSizes() {
        assertFailsWith<IllegalArgumentException> {
            TerminalSwingSettings(columns = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            TerminalSwingSettings(rows = 0)
        }
    }

    @Test
    fun metricsArePositiveForMonospacedFont() {
        val component = Canvas()
        val fontMetrics = component.getFontMetrics(Font(Font.MONOSPACED, Font.PLAIN, 14))
        val metrics = TerminalSwingMetrics.from(fontMetrics)

        assertTrue(metrics.cellWidth > 0)
        assertTrue(metrics.cellHeight > 0)
        assertTrue(metrics.baseline in 0..metrics.cellHeight)
        assertTrue(metrics.cursorStrokeWidth > 0)
    }

    @Test
    fun settingsDefaultToHighQualityGridSafeTextHints() {
        val settings = TerminalSwingSettings()

        assertEquals(RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB, settings.textAntialiasing)
        assertEquals(RenderingHints.VALUE_FRACTIONALMETRICS_OFF, settings.fractionalMetrics)
        assertTrue(settings.fallbackFonts.isNotEmpty())
        assertEquals(false, settings.useSystemFallbackFonts)
    }

    @Test
    fun componentReportsVisibleGridFromFrozenMetrics() {
        val component = TerminalSwingTerminal {
            TerminalSwingSettings(columns = 10, rows = 4)
        }
        val preferred = component.preferredSize
        var visibleColumns = 0
        var visibleRows = 0

        SwingUtilities.invokeAndWait {
            component.setSize(preferred)
            val visible = component.visibleGridSize()
            visibleColumns = visible.width
            visibleRows = visible.height
        }

        assertEquals(10, visibleColumns)
        assertEquals(4, visibleRows)
    }
}
