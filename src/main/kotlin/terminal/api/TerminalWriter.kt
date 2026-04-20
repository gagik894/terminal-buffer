package com.gagik.terminal.api

/**
 * Write-side contract for the terminal buffer.
 *
 * Consumed by the ANSI parser to push character data and control codes
 * into the active screen. All operations target the cursor's current position
 * unless otherwise stated.
 *
 * The core is intentionally spatial rather than temporal. Parser layers are
 * expected to decide grapheme-cluster boundaries and call either the scalar
 * fast path ([writeCodepoint]) or the explicit cluster ingress ([writeCluster]).
 */
interface TerminalWriter {

    /**
     * Writes one Unicode scalar value at the cursor position using the active
     * pen attributes, then advances the cursor.
     *
     * This is the core fast path for simple printable text. It does not perform
     * grapheme segmentation or merge combining marks into a previous cell; a
     * parser/segmenter must dispatch pre-segmented grapheme clusters via
     * [writeCluster].
     *
     * Wrapping, scrolling, and wide-character handling are applied automatically.
     *
     * @param codepoint Unicode codepoint to write.
     */
    fun writeCodepoint(codepoint: Int)

    /**
     * Writes [text] literally to the buffer using the active pen attributes.
     *
     * Control characters (`\n`, `\r`, `\t`, etc.) are not interpreted; they are
     * written as ordinary codepoints. This convenience path is scalar-only: it
     * forwards each decoded codepoint independently and does not perform
     * grapheme segmentation. Use [writeCluster] from a parser/segmenter when a
     * complete grapheme sequence must be written as one cell.
     *
     * @param text Text to write.
     */
    fun writeText(text: String)

    /**
     * Executes a line feed (LF, `0x0A`).
     *
     * Moves the cursor down one row without resetting the column. Scrolls the
     * active scroll region up if the cursor is on the bottom margin.
     */
    fun newLine()

    /**
     * Executes Reverse Index (RI, `ESC M`).
     *
     * Moves the cursor up one row without changing the column. Scrolls the
     * active scroll region down if the cursor is on the top margin.
     */
    fun reverseLineFeed()

    /**
     * Executes a carriage return (CR, `0x0D`).
     *
     * Moves the cursor to column 0 on the current row.
     */
    fun carriageReturn()

    /**
     * Sets the active vertical scroll region (DECSTBM, `CSI top ; bottom r`).
     *
     * [top] and [bottom] are 1-based inclusive row numbers per the DECSTBM
     * convention. Both are clamped to viewport bounds; degenerate ranges are
     * ignored. Homes the cursor per the current DECOM state.
     *
     * @param top First row of the scroll region (1-based, inclusive).
     * @param bottom Last row of the scroll region (1-based, inclusive).
     */
    fun setScrollRegion(top: Int, bottom: Int)

    /** Resets the scroll region to the full viewport and homes the cursor. */
    fun resetScrollRegion()

    /**
     * Scrolls the active scroll region up by one line (SU, `CSI 1 S`).
     *
     * The top line may enter scrollback when the region spans the full viewport.
     * The cursor position is preserved.
     */
    fun scrollUp()

    /**
     * Scrolls the active scroll region down by one line (SD, `CSI 1 T`).
     *
     * A blank line is exposed at the top of the region. Scrollback is not
     * consumed. The cursor position is preserved.
     */
    fun scrollDown()

    /**
     * Inserts [count] blank lines at the cursor row within the active scroll
     * region (IL, `CSI n L`).
     *
     * Lines shifted past the bottom margin are discarded. Ignored when the
     * cursor is outside the active scroll region.
     *
     * @param count Number of blank lines to insert. Non-positive values are ignored.
     */
    fun insertLines(count: Int)

    /**
     * Deletes [count] lines starting at the cursor row within the active scroll
     * region (DL, `CSI n M`).
     *
     * Blank lines are exposed at the bottom margin. Ignored when the cursor is
     * outside the active scroll region.
     *
     * @param count Number of lines to delete. Non-positive values are ignored.
     */
    fun deleteLines(count: Int)

    /**
     * Inserts [count] blank cells at the cursor column, shifting existing cells
     * right (ICH, `CSI n @`). Cells pushed past the right margin are discarded.
     *
     * @param count Number of blank cells to insert. Non-positive values are ignored.
     */
    fun insertBlankCharacters(count: Int)

    /**
     * Deletes [count] characters at the cursor column, shifting the remainder of
     * the line left and filling the vacated right cells with blanks using the
     * active pen attribute (DCH, `CSI n P`). Cursor position is not changed.
     *
     * @param count Number of characters to delete. Non-positive values are ignored.
     */
    fun deleteCharacters(count: Int)

    /** Erases from the cursor to the end of the current line (EL 0, `CSI 0 K`). */
    fun eraseLineToEnd()

    /** Erases from the start of the current line through the cursor (EL 1, `CSI 1 K`). */
    fun eraseLineToCursor()

    /** Erases the entire current line without moving the cursor (EL 2, `CSI 2 K`). */
    fun eraseCurrentLine()

    /** Erases from the cursor to the end of the visible screen (ED 0, `CSI 0 J`). */
    fun eraseScreenToEnd()

    /** Erases from the start of the visible screen through the cursor (ED 1, `CSI 1 J`). */
    fun eraseScreenToCursor()

    /** Erases the entire visible screen without moving the cursor (ED 2, `CSI 2 J`). */
    fun eraseEntireScreen()

    /**
     * Erases all scrollback history while preserving the visible viewport
     * (xterm/VTE ED 3, `CSI 3 J`).
     */
    fun eraseScreenAndHistory()

    /**
     * Clears the visible screen and homes the cursor (equivalent to ED 2 + CUP).
     *
     * Scrollback history is preserved. This matches what the shell `clear` command sends.
     */
    fun clearScreen()

    /**
     * Clears all visible content and scrollback history, resets the pen, homes the
     * cursor, clears the DECSC saved-cursor slot, and restores tab stops to the
     * VT100 default spacing.
     *
     * The scroll region is not affected. For a full terminal reset use
     * [TerminalBufferApi.reset].
     */
    fun clearAll()

    /**
     * Sets the active pen attributes used by all subsequent write and erase operations.
     *
     * Out-of-range colour indices are clamped to the nearest valid value.
     *
     * @param fg Foreground colour index (0 = default).
     * @param bg Background colour index (0 = default).
     * @param bold `true` to enable bold weight.
     * @param italic `true` to enable italic style.
     * @param underline `true` to enable underline.
     */
    fun setPenAttributes(
        fg: Int,
        bg: Int,
        bold: Boolean = false,
        italic: Boolean = false,
        underline: Boolean = false
    )

    /**
     * Resets the active pen to the terminal default attributes (`SGR 0`, `CSI 0 m`).
     */
    fun resetPen()
}
