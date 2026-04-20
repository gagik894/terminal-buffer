package com.gagik.terminal.model

/**
 * Manages VT100 horizontal tab stop positions.
 *
 * Default state mirrors the VT100 hardware default: one stop every 8 columns
 * (columns 0, 8, 16, 24, …). Stops are stored as a flat [BooleanArray] —
 * zero heap allocation in the hot loop; only [resize] allocates, and only
 * when the terminal width actually changes.
 *
 * ### ANSI / VT command mapping
 * | Command      | Escape sequence | Method            |
 * |--------------|-----------------|-------------------|
 * | HTS          | `ESC H`         | [setStop]         |
 * | TBC clear one| `CSI 0 g`       | [clearStop]       |
 * | TBC clear all| `CSI 3 g`       | [clearAll]        |
 * | Hard reset   | `RIS` / `ESC c` | [resetToDefault]  |
 *
 * @param width Initial terminal width (number of columns).
 */
internal class TabStops(private var width: Int) {

    /** `true` at index `i` means a tab stop exists at column `i`. */
    private var stops = BooleanArray(width) { col -> col % 8 == 0 }

    // ----- Lifecycle --------------------------------------------------------

    /**
     * Resizes the internal stop array to [newWidth].
     *
     * - **Shrink**: existing stops beyond [newWidth] are discarded. When the
     *   terminal later expands again, the newly exposed columns receive fresh
     *   default stops — stale custom stops are never resurrected.
     * - **Grow**: existing stops are preserved; newly exposed columns are
     *   initialised to the standard 8-column rhythm (`i % 8 == 0`).
     * - **No change**: returns immediately without allocating.
     *
     * Called by `TerminalResizer` whenever the terminal width changes.
     *
     * @param newWidth The new terminal width in columns.
     */
    fun resize(newWidth: Int) {
        val oldWidth = stops.size

        if (newWidth <= oldWidth) {
            // Truncate safely
            stops = stops.copyOf(newWidth)
            width = newWidth
            return
        }

        // Expand
        stops = stops.copyOf(newWidth)

        // Populate the newly created space with default 8-col VT stops
        for (i in oldWidth until newWidth) {
            stops[i] = (i % 8 == 0)
        }

        width = newWidth
    }

    // ----- Mutation ---------------------------------------------------------

    /**
     * Sets a tab stop at column [col].
     *
     * Called when the parser receives **HTS** (`ESC H`) with the cursor at
     * column [col].
     *
     * @param col Zero-based column index. Out-of-bounds values are ignored.
     */
    fun setStop(col: Int) {
        if (col in 0 until width) stops[col] = true
    }

    /**
     * Clears the tab stop at column [col].
     *
     * Called when the parser receives **TBC 0** (`CSI 0 g`) with the cursor
     * at column [col].
     *
     * @param col Zero-based column index. Out-of-bounds values are ignored.
     */
    fun clearStop(col: Int) {
        if (col in 0 until width) stops[col] = false
    }

    /**
     * Clears all tab stops.
     *
     * Called when the parser receives **TBC 3** (`CSI 3 g`). After this call,
     * [getNextStop] will always return the right margin until stops are
     * restored or [resetToDefault] is called.
     */
    fun clearAll() {
        stops.fill(false)
    }

    /**
     * Resets all tab stops to the VT100 default (one stop every 8 columns).
     *
     * Called on a hard terminal reset (`RIS` / `ESC c`). This is distinct from
     * [clearAll]: [clearAll] erases stops (`CSI 3 g`); [resetToDefault]
     * restores factory defaults.
     */
    fun resetToDefault() {
        for (i in 0 until width) stops[i] = i % 8 == 0
    }

    // ----- Query ------------------------------------------------------------

    /**
     * Returns the column of the next tab stop strictly to the right of
     * [currentCol].
     *
     * If no stop exists to the right, returns the right margin (`width - 1`)
     * — tab never wraps to the next line. If the terminal has zero columns,
     * returns `0`.
     *
     * @param currentCol The cursor's current zero-based column index.
     * @return The column index the cursor should jump to.
     */
    fun getNextStop(currentCol: Int): Int {
        val margin = (width - 1).coerceAtLeast(0)
        if (currentCol >= margin) return margin
        for (i in currentCol + 1 until width) {
            if (stops[i]) return i
        }
        return margin
    }
}
