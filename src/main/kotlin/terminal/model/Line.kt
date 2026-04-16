package com.gagik.terminal.model

import com.gagik.terminal.buffer.TerminalLineApi
import com.gagik.terminal.store.ClusterStore

/**
 * A mutable physical terminal line backed by two primitive arrays.
 *
 * ## Cell encoding
 *
 * Each column is represented by a pair of parallel `Int` values:
 * - `codepoints[col]` — the raw storage value (see [TerminalConstants] for the full encoding)
 * - `attrs[col]`      — the packed cell attribute (decoded via [com.gagik.terminal.codec.AttributeCodec])
 *
 * wrapped=true means this line is a soft continuation of the previous line
 * caused by wrapping at the terminal width.
 *
 * ## Ownership
 *
 * [store] is shared by all lines belonging to the same [com.gagik.terminal.buffer.HistoryRing].
 * Lines must never be transferred to a ring that uses a different store without
 * deep-copying their cluster payloads (see [com.gagik.terminal.engine.TerminalResizer]).
 *
 * ## Mutability
 *
 * All mutation methods are `internal` and called exclusively by
 * [com.gagik.terminal.engine.GridWriter]. The [TerminalLineApi] surface exposed to
 * the renderer is strictly read-only.
 *
 * @param width The number of columns in this line. Immutable after construction.
 * @param store The [ClusterStore] that owns cluster payloads for this line's ring.
 */
internal class Line(
    override val width: Int,
    internal val store: ClusterStore
) : TerminalLineApi {

    init {
        require(width > 0) { "Line width must be positive, got $width" }
    }


    /** Raw storage array. May contain codepoints, EMPTY, WIDE_CHAR_SPACER, or cluster handles. */
    private val codepoints = IntArray(width) { TerminalConstants.EMPTY }

    /** Packed cell attributes, parallel to [codepoints]. */
    private val attrs = IntArray(width)

    /**
     * True when this line's content continues on the next physical line.
     * Set by [com.gagik.terminal.engine.GridWriter] during soft-wrap events.
     */
    var wrapped: Boolean = false

    // Internal raw accessors: used by GridWriter and TerminalResizer only

    /**
     * Returns the raw value stored at [col] without any decoding.
     * May return a plain codepoint, [TerminalConstants.EMPTY],
     * [TerminalConstants.WIDE_CHAR_SPACER], or a cluster handle (<= -2).
     *
     * @param col Column index.
     */
    fun rawCodepoint(col: Int): Int = codepoints[col]

    /**
     * Writes [raw] and [attr] directly into [col] without allocating or freeing any
     * cluster handle. Used by [com.gagik.terminal.engine.TerminalResizer] to transplant
     * raw values (including live cluster handles) into a newly allocated line that
     * shares the same [store].
     *
     * **Caller is responsible** for ensuring the handle is valid in [store].
     *
     * @param col Column index.
     */
    fun setRawCell(col: Int, raw: Int, attr: Int) {
        codepoints[col] = raw
        attrs[col]      = attr
    }

    // TerminalLineApi — public read-only surface

    /**
     * Returns the base (first) codepoint for the cell at [col].
     *
     * For cluster cells the leading codepoint of the grapheme sequence is returned,
     * enabling simple renderers to draw one glyph without knowing about clusters.
     *
     * @param col Column index.
     */
    override fun getCodepoint(col: Int): Int {
        val raw = codepoints[col]
        return if (raw <= TerminalConstants.CLUSTER_HANDLE_MAX) store.baseCodepoint(raw) else raw
    }

    /**
     * Returns the packed attribute for the cell at [col].
     *
     * @param col Column index.
     */
    override fun getPackedAttr(col: Int): Int = attrs[col]

    /**
     * Returns `true` if [col] holds a multi-codepoint grapheme cluster.
     * Shape-aware renderers should call [readCluster] for such cells.
     *
     * @param col Column index.
     */
    override fun isCluster(col: Int): Boolean =
        codepoints[col] <= TerminalConstants.CLUSTER_HANDLE_MAX

    /**
     * Copies the full codepoint sequence of the cluster at [col] into [dest].
     * Returns the number of codepoints written, or 0 if the cell is not a cluster.
     *
     * Zero-allocation: no heap objects are created by this method.
     *
     * @param col  Column index.
     * @param dest Destination array. Must have capacity >= actual cluster length.
     */
    override fun readCluster(col: Int, dest: IntArray): Int {
        val raw = codepoints[col]
        if (raw > TerminalConstants.CLUSTER_HANDLE_MAX) return 0
        return store.readInto(raw, dest)
    }

    // Internal mutation — called exclusively by GridWriter

    /**
     * Writes a single codepoint into [col], freeing any cluster handle previously
     * stored there. This is the standard write path for all non-cluster cells.
     */
    fun setCell(col: Int, codepoint: Int, attr: Int) {
        freeHandleAt(col)
        codepoints[col] = codepoint
        attrs[col]      = attr
    }

    /**
     * Writes a grapheme cluster into [col] by allocating a slot in [store] and
     * storing the resulting handle. Any previous value (including another cluster
     * handle) at [col] is freed first.
     *
     * @param col    Target column.
     * @param cps    Source array of codepoints. Not retained after this call.
     * @param cpLen  Number of valid codepoints in [cps].
     * @param attr   Packed cell attribute.
     */
    fun setCluster(col: Int, cps: IntArray, cpLen: Int, attr: Int) {
        freeHandleAt(col)
        codepoints[col] = store.alloc(cps, 0, cpLen)
        attrs[col]      = attr
    }

    /**
     * Clears all cells to [defaultAttr] and frees every cluster handle in the line.
     * Also resets the [wrapped] flag.
     */
    fun clear(defaultAttr: Int) {
        store.freeRange(codepoints, 0, width)
        codepoints.fill(TerminalConstants.EMPTY)
        attrs.fill(defaultAttr)
        wrapped = false
    }

    /**
     * Clears cells from [startCol] (inclusive) to the end of the line,
     * freeing any cluster handles in that range.
     */
    fun clearFromColumn(startCol: Int, attr: Int) {
        val from = startCol.coerceAtLeast(0)
        if (from >= width) return
        store.freeRange(codepoints, from, width)
        codepoints.fill(TerminalConstants.EMPTY, from, width)
        attrs.fill(attr, from, width)
    }

    /**
     * Clears cells from the start of the line through [endCol] (inclusive),
     * freeing any cluster handles in that range.
     */
    fun clearToColumn(endCol: Int, attr: Int) {
        val to = (endCol + 1).coerceAtMost(width)
        if (to <= 0) return
        store.freeRange(codepoints, 0, to)
        codepoints.fill(TerminalConstants.EMPTY, 0, to)
        attrs.fill(attr, 0, to)
    }

    /**
     * Inserts [count] blank cells at [col], shifting existing content to the right.
     * Cells shifted off the right edge are freed (cluster handles are released).
     * The wide-cluster leader at [col] must be annihilated by the caller before
     * this method is invoked.
     */
    fun insertCells(col: Int, count: Int, defaultAttr: Int) {
        if (col !in 0 until width || count <= 0) return
        val safeCount  = count.coerceAtMost(width - col)
        val shiftCount = width - col - safeCount

        // Free handles that will fall off the right edge.
        store.freeRange(codepoints, width - safeCount, width)

        if (shiftCount > 0) {
            System.arraycopy(codepoints, col, codepoints, col + safeCount, shiftCount)
            System.arraycopy(attrs,      col, attrs,      col + safeCount, shiftCount)
        }
        codepoints.fill(TerminalConstants.EMPTY, col, col + safeCount)
        attrs.fill(defaultAttr, col, col + safeCount)
    }

    /**
     * Fills every cell with [codepoint] and [attr], freeing all cluster handles first.
     * Used for bulk background-color fills (e.g. erase-display operations).
     */
    fun fill(codepoint: Int, attr: Int) {
        store.freeRange(codepoints, 0, width)
        codepoints.fill(codepoint)
        attrs.fill(attr)
    }

    // -------------------------------------------------------------------------
    // Text rendering helpers — allocating, not for use in render loops
    // -------------------------------------------------------------------------

    /**
     * Renders the full line as a [String], including trailing spaces.
     * Wide-char spacer cells are omitted (the leader already contributed its glyph).
     * Intended for debugging and tests only.
     */
    fun toText(): String = buildString(width) {
        for (col in 0 until width) {
            appendCell(col)
        }
    }

    /**
     * Renders the line as a [String], trimming trailing blank cells.
     * Intended for scrollback serialisation, debugging, and tests.
     */
    fun toTextTrimmed(): String {
        var last = width - 1
        while (last >= 0 && codepoints[last] == TerminalConstants.EMPTY) last--
        if (last < 0) return ""
        return buildString(last + 1) {
            for (col in 0..last) appendCell(col)
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Appends the glyph(s) at [col] to [this] [StringBuilder].
     * Cluster cells emit all codepoints in sequence; spacer cells are skipped.
     */
    private fun StringBuilder.appendCell(col: Int) {
        val raw = codepoints[col]
        when {
            raw == TerminalConstants.EMPTY            -> append(' ')
            raw == TerminalConstants.WIDE_CHAR_SPACER -> Unit // skip; leader already appended
            raw <= TerminalConstants.CLUSTER_HANDLE_MAX -> {
                val len = store.length(raw)
                for (i in 0 until len) appendCodePoint(store.codepointAt(raw, i))
            }
            else -> appendCodePoint(raw)
        }
    }

    /** Frees the cluster handle at [col] if present; no-op otherwise. */
    private fun freeHandleAt(col: Int) {
        val raw = codepoints[col]
        if (raw <= TerminalConstants.CLUSTER_HANDLE_MAX) store.free(raw)
    }
}