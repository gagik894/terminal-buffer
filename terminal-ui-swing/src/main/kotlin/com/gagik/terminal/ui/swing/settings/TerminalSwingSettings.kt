package com.gagik.terminal.ui.swing.settings

import com.gagik.terminal.render.api.TerminalColorPalette
import com.gagik.terminal.ui.swing.api.TerminalSwingTerminal
import java.awt.Font
import java.awt.GraphicsEnvironment
import java.awt.RenderingHints

/**
 * Immutable Swing terminal UI settings.
 *
 * Hosts can replace this value and call
 * [TerminalSwingTerminal.reloadSettings] to rebuild metrics and repaint.
 *
 * @property font primary terminal font.
 * @property palette resolved terminal color palette.
 * @property columns initial preferred column count.
 * @property rows initial preferred row count.
 * @property cursorBlinkMillis cursor blink period in milliseconds.
 * @property textAntialiasing text antialiasing hint used during painting.
 * @property fractionalMetrics fractional font metrics hint used during painting.
 * @property fallbackFonts ordered fonts used by the complex-text renderer when
 * [font] cannot display a non-ASCII cluster.
 * @property useSystemFallbackFonts whether the complex-text renderer may use
 * installed system fonts after [fallbackFonts] fail. System font discovery is
 * asynchronous and disabled by default to keep Swing startup and painting
 * responsive.
 */
data class TerminalSwingSettings(
    val font: Font = defaultTerminalFont(),
    val fallbackFonts: List<Font> = defaultFallbackFonts(),
    val useSystemFallbackFonts: Boolean = false,
    val palette: TerminalColorPalette = defaultPalette(),
    val columns: Int = 80,
    val rows: Int = 24,
    val cursorBlinkMillis: Int = 600,
    val textAntialiasing: Any = RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB,
    val fractionalMetrics: Any = RenderingHints.VALUE_FRACTIONALMETRICS_OFF,
) {
    init {
        require(columns > 0) { "columns must be > 0, was $columns" }
        require(rows > 0) { "rows must be > 0, was $rows" }
        require(cursorBlinkMillis > 0) {
            "cursorBlinkMillis must be > 0, was $cursorBlinkMillis"
        }
    }

    companion object {
        private const val DEFAULT_FONT_SIZE = 16
        private val preferredDefaultFontFamilies = arrayOf(
            "Cascadia Mono",
            "Cascadia Code",
            "Consolas",
            Font.MONOSPACED,
        )
        private val resolvedDefaultTerminalFont: Font by lazy(LazyThreadSafetyMode.PUBLICATION) {
            Font(resolveDefaultFontFamily(), Font.PLAIN, DEFAULT_FONT_SIZE)
        }

        /**
         * Returns the default terminal font used when hosts do not provide one.
         *
         * The preferred families match common modern Windows terminal defaults,
         * with the logical monospaced font as a portable fallback.
         */
        @JvmStatic
        fun defaultTerminalFont(): Font = resolvedDefaultTerminalFont

        /**
         * Returns conservative logical and common platform fonts for complex
         * script fallback. Hosts can replace this list with their own font
         * resolver policy.
         */
        @JvmStatic
        fun defaultFallbackFonts(): List<Font> = listOf(
            Font("Dialog", Font.PLAIN, DEFAULT_FONT_SIZE),
            Font(Font.SANS_SERIF, Font.PLAIN, DEFAULT_FONT_SIZE),
            Font("Segoe UI", Font.PLAIN, DEFAULT_FONT_SIZE),
            Font("Segoe UI Symbol", Font.PLAIN, DEFAULT_FONT_SIZE),
            Font("Segoe UI Historic", Font.PLAIN, DEFAULT_FONT_SIZE),
            Font("Ebrima", Font.PLAIN, DEFAULT_FONT_SIZE),
            Font("Leelawadee UI", Font.PLAIN, DEFAULT_FONT_SIZE),
            Font("Nyala", Font.PLAIN, DEFAULT_FONT_SIZE),
            Font("Abyssinica SIL", Font.PLAIN, DEFAULT_FONT_SIZE),
            Font("Noto Sans Thai", Font.PLAIN, DEFAULT_FONT_SIZE),
            Font("Noto Sans Ethiopic", Font.PLAIN, DEFAULT_FONT_SIZE),
            Font("Noto Sans Runic", Font.PLAIN, DEFAULT_FONT_SIZE),
            Font("Noto Sans CJK SC", Font.PLAIN, DEFAULT_FONT_SIZE),
            Font("Noto Color Emoji", Font.PLAIN, DEFAULT_FONT_SIZE),
        )

        /**
         * Returns the default Swing terminal palette.
         *
         * Theme colors live in the Swing layer so the dependency-free render
         * API can remain renderer-neutral.
         */
        @JvmStatic
        fun defaultPalette(): TerminalColorPalette {
            return TerminalColorPalette(
                defaultForeground = 0xFFE6E8EF.toInt(),
                defaultBackground = 0xFF111318.toInt(),
                selectionForeground = 0xFFFFFFFF.toInt(),
                selectionBackground = 0xFF2F6FEB.toInt(),
                cursorForeground = 0xFF111318.toInt(),
                cursorBackground = 0xFFE6E8EF.toInt(),
                indexedColors = defaultIndexedColors(),
                boldAsBright = true,
            )
        }

        private fun resolveDefaultFontFamily(): String {
            val installedFamilies = GraphicsEnvironment
                .getLocalGraphicsEnvironment()
                .availableFontFamilyNames
            for (preferredFamily in preferredDefaultFontFamilies) {
                for (installedFamily in installedFamilies) {
                    if (installedFamily.equals(preferredFamily, ignoreCase = true)) {
                        return installedFamily
                    }
                }
            }
            return Font.MONOSPACED
        }

        private fun defaultIndexedColors(): IntArray {
            val colors = TerminalColorPalette.defaultIndexedColors()
            val ansi16 = intArrayOf(
                0xFF1D2027.toInt(),
                0xFFC94F6D.toInt(),
                0xFF81B29A.toInt(),
                0xFFE6C07B.toInt(),
                0xFF6EA8FE.toInt(),
                0xFFC678DD.toInt(),
                0xFF56B6C2.toInt(),
                0xFFD8DEE9.toInt(),
                0xFF5C6370.toInt(),
                0xFFE06C75.toInt(),
                0xFF98C379.toInt(),
                0xFFE5C07B.toInt(),
                0xFF61AFEF.toInt(),
                0xFFC678DD.toInt(),
                0xFF56B6C2.toInt(),
                0xFFFFFFFF.toInt(),
            )
            ansi16.copyInto(colors)
            return colors
        }
    }
}

/**
 * Provides immutable settings snapshots to [TerminalSwingTerminal].
 */
fun interface TerminalSwingSettingsProvider {
    /**
     * Returns the current immutable settings snapshot.
     *
     * @return settings snapshot for metrics, colors, and painting hints.
     */
    fun currentSettings(): TerminalSwingSettings
}
