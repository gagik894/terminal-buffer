package com.gagik.parser.spi

/**
 * Parser-facing terminal command sink.
 *
 * This is the narrow semantic handoff boundary from :terminal-parser to :terminal-core.
 *
 * Rules:
 * - The parser emits terminal operations.
 * - The sink/core owns grid physics, bounds clamping, wrapping, margins, storage, and mode persistence.
 * - The parser must not know terminal width, height, cursor bounds, or rendering details.
 */
internal interface TerminalCommandSink {
    // -------------------------------------------------------------------------
    // Printable ingress
    // -------------------------------------------------------------------------

    fun writeCodepoint(codepoint: Int)

    fun writeCluster(
        codepoints: IntArray,
        length: Int,
        charWidth: Int,
    )

    // -------------------------------------------------------------------------
    // C0 / ESC structural controls
    // -------------------------------------------------------------------------

    fun bell()
    fun backspace()
    fun tab()
    fun lineFeed()
    fun carriageReturn()
    fun reverseIndex()
    fun nextLine()

    /**
     * DECSTR soft terminal reset: CSI ! p.
     *
     * The parser identifies the sequence; the core owns the actual reset semantics.
     */
    fun softReset()

    fun saveCursor()
    fun restoreCursor()

    // -------------------------------------------------------------------------
    // Cursor navigation
    // -------------------------------------------------------------------------

    fun cursorUp(n: Int)
    fun cursorDown(n: Int)
    fun cursorForward(n: Int)
    fun cursorBackward(n: Int)

    fun cursorNextLine(n: Int)
    fun cursorPreviousLine(n: Int)

    fun cursorForwardTabs(n: Int)
    fun cursorBackwardTabs(n: Int)

    /**
     * Column is parser-translated to zero-origin before handoff.
     * The core may clamp; the parser must not.
     */
    fun setCursorColumn(col: Int)

    /**
     * Row is parser-translated to zero-origin before handoff.
     * The core may clamp; the parser must not.
     */
    fun setCursorRow(row: Int)

    /**
     * Row and column are parser-translated to zero-origin before handoff.
     * The core may clamp; the parser must not.
     */
    fun setCursorAbsolute(row: Int, col: Int)

    /**
     * DECSTBM scroll region.
     *
     * Top and bottom are parser-translated to zero-origin before handoff.
     * A bottom value of -1 means the sequence omitted the bottom margin, so the
     * core should use the terminal's current last row.
     */
    fun setScrollRegion(top: Int, bottom: Int)

    // -------------------------------------------------------------------------
    // Erase / edit / scroll
    // -------------------------------------------------------------------------

    fun eraseInDisplay(mode: Int, selective: Boolean)
    fun eraseInLine(mode: Int, selective: Boolean)

    fun insertLines(n: Int)
    fun deleteLines(n: Int)

    fun insertCharacters(n: Int)
    fun deleteCharacters(n: Int)
    fun eraseCharacters(n: Int)

    fun scrollUp(n: Int)
    fun scrollDown(n: Int)

    // -------------------------------------------------------------------------
    // Tab stops
    // -------------------------------------------------------------------------

    fun setTabStop()
    fun clearTabStop()
    fun clearAllTabStops()

    // -------------------------------------------------------------------------
    // Modes
    // -------------------------------------------------------------------------

    /**
     * ANSI mode set/reset.
     *
     * Example: CSI 4 h/l insert mode.
     */
    fun setAnsiMode(mode: Int, enable: Boolean)

    /**
     * DEC private mode set/reset.
     *
     * Example: CSI ? 25 h/l cursor visibility.
     */
    fun setDecMode(mode: Int, enable: Boolean)

    // -------------------------------------------------------------------------
    // SGR / pen attributes
    // -------------------------------------------------------------------------

    fun resetAttributes()

    fun setBold(enabled: Boolean)
    fun setFaint(enabled: Boolean)
    fun setItalic(enabled: Boolean)
    fun setUnderlineStyle(style: Int)
    fun setBlink(enabled: Boolean)
    fun setInverse(enabled: Boolean)
    fun setConceal(enabled: Boolean)
    fun setStrikethrough(enabled: Boolean)

    fun setForegroundDefault()
    fun setBackgroundDefault()

    fun setForegroundIndexed(index: Int)
    fun setBackgroundIndexed(index: Int)

    fun setForegroundRgb(red: Int, green: Int, blue: Int)
    fun setBackgroundRgb(red: Int, green: Int, blue: Int)

    // -------------------------------------------------------------------------
    // OSC
    // -------------------------------------------------------------------------

    fun setWindowTitle(title: String)
    fun setIconTitle(title: String)
    fun setIconAndWindowTitle(title: String)

    fun startHyperlink(uri: String, id: String?)
    fun endHyperlink()
}
