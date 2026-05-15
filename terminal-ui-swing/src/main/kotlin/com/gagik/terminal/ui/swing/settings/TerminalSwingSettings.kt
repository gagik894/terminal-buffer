package com.gagik.terminal.ui.swing.settings

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
    val palette: TerminalColorPalette = TerminalColorPalette(),
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
