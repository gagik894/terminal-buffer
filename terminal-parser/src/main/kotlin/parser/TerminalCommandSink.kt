package com.gagik.parser

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
    // Payload hooks
    // -------------------------------------------------------------------------

    /**
     * Bounded OSC payload handoff.
     *
     * Ownership rule:
     * - [payload] is parser-owned scratch storage.
     * - The sink must consume or copy synchronously.
     * - The parser may overwrite the buffer after this call returns.
     */
    fun onOsc(
        commandCode: Int,
        payload: ByteArray,
        length: Int,
        overflowed: Boolean,
    )
}