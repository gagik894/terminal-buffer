package com.gagik.terminal.render.api

/**
 * Caller-owned primitive cache for render frames.
 *
 * This cache consumes [TerminalRenderFrameReader] and stores copied primitive
 * row data after the frame callback returns. It deliberately contains no
 * backend-specific glyph runs, font state, paint objects, selection model, or UI
 * timer logic. Swing, Compose, and other renderers can build their local layout
 * and paint caches from this primitive data.
 *
 * @param columns initial cache width in cells.
 * @param rows initial cache height in rows.
 */
class TerminalRenderCache(
    columns: Int,
    rows: Int,
) {
    /**
     * Cached visible width in cells.
     */
    var columns: Int = columns
        private set

    /**
     * Cached visible height in rows.
     */
    var rows: Int = rows
        private set

    /**
     * Copied row code words. See [TerminalRenderFrame.copyLine].
     */
    var codeWords: Array<IntArray> = emptyIntRows()
        private set

    /**
     * Copied primary public render attribute words.
     */
    var attrWords: Array<LongArray> = emptyLongRows()
        private set

    /**
     * Copied public render cell flags.
     */
    var flags: Array<IntArray> = emptyIntRows()
        private set

    /**
     * Copied optional public extra-attribute words.
     */
    var extraAttrWords: Array<LongArray> = emptyLongRows()
        private set

    /**
     * Copied optional hyperlink identifiers. Zero means no hyperlink.
     */
    var hyperlinkIds: Array<IntArray> = emptyIntRows()
        private set

    /**
     * Copied cluster text by row and column. Non-cluster cells contain `null`.
     */
    var clusters: Array<Array<String?>> = emptyClusterRows()
        private set

    /**
     * Cached per-row render generations.
     */
    var lineGenerations: LongArray = LongArray(0)
        private set

    /**
     * Cached per-row soft-wrap flags.
     */
    var lineWrapped: BooleanArray = BooleanArray(0)
        private set

    /**
     * Last copied frame generation.
     */
    var frameGeneration: Long = UNINITIALIZED_GENERATION
        private set

    /**
     * Last copied structure generation.
     */
    var structureGeneration: Long = UNINITIALIZED_GENERATION
        private set

    /**
     * Last copied active buffer kind.
     */
    var activeBuffer: TerminalRenderBufferKind = TerminalRenderBufferKind.PRIMARY
        private set

    /**
     * Last copied cursor state.
     */
    var cursor: TerminalRenderCursor? = null
        private set

    /**
     * Rows copied by the most recent [updateFrom] call.
     *
     * Renderers can consume this and mark only those row layout caches dirty.
     * The array instance is stable until resize and its contents are overwritten
     * on each update.
     */
    var dirtyRows: BooleanArray = BooleanArray(0)
        private set

    /**
     * Whether the most recent [updateFrom] call resized the primitive storage.
     */
    var resizedOnLastUpdate: Boolean = false
        private set

    /**
     * Whether the cursor generation changed during the most recent [updateFrom].
     */
    var cursorChangedOnLastUpdate: Boolean = false
        private set

    init {
        require(columns > 0) { "columns must be > 0, was $columns" }
        require(rows > 0) { "rows must be > 0, was $rows" }
        resizeStorage(columns, rows)
    }

    /**
     * Copies changed rows and cursor state from [reader].
     *
     * The read callback is used only to copy primitive frame data into this
     * cache. After this method returns, renderers should build glyph or text
     * runs from the copied rows and paint from their own backend-specific state.
     *
     * @param reader source of the short-lived render frame.
     */
    fun updateFrom(reader: TerminalRenderFrameReader) {
        reader.readRenderFrame { frame ->
            resizedOnLastUpdate = false
            if (columns != frame.columns || rows != frame.rows) {
                resizeStorage(frame.columns, frame.rows)
                resizedOnLastUpdate = true
                structureGeneration = UNINITIALIZED_GENERATION
            }

            dirtyRows.fill(false)
            cursorChangedOnLastUpdate = false

            val structureChanged = structureGeneration != frame.structureGeneration
            if (structureChanged) {
                clearAllClusters()
            }

            var row = 0
            while (row < frame.rows) {
                val lineGeneration = frame.lineGeneration(row)
                val wrapped = frame.lineWrapped(row)
                if (structureChanged ||
                    lineGenerations[row] != lineGeneration ||
                    lineWrapped[row] != wrapped
                ) {
                    clearClusterRow(row)
                    frame.copyLine(
                        row = row,
                        codeWords = codeWords[row],
                        attrWords = attrWords[row],
                        flags = flags[row],
                        extraAttrWords = extraAttrWords[row],
                        hyperlinkIds = hyperlinkIds[row],
                        clusterSink = TerminalRenderClusterSink { col, text ->
                            clusters[row][col] = text
                        },
                    )
                    lineGenerations[row] = lineGeneration
                    lineWrapped[row] = wrapped
                    dirtyRows[row] = true
                }
                row++
            }

            activeBuffer = frame.activeBuffer
            val oldCursor = cursor
            val newCursor = frame.cursor
            if (oldCursor?.generation != newCursor.generation) {
                cursorChangedOnLastUpdate = true
            }
            cursor = newCursor
            frameGeneration = frame.frameGeneration
            structureGeneration = frame.structureGeneration
        }
    }

    private fun resizeStorage(newColumns: Int, newRows: Int) {
        require(newColumns > 0) { "columns must be > 0, was $newColumns" }
        require(newRows > 0) { "rows must be > 0, was $newRows" }
        columns = newColumns
        rows = newRows
        codeWords = Array(newRows) { IntArray(newColumns) }
        attrWords = Array(newRows) { LongArray(newColumns) }
        flags = Array(newRows) { IntArray(newColumns) }
        extraAttrWords = Array(newRows) { LongArray(newColumns) }
        hyperlinkIds = Array(newRows) { IntArray(newColumns) }
        clusters = Array(newRows) { arrayOfNulls(newColumns) }
        lineGenerations = LongArray(newRows) { UNINITIALIZED_GENERATION }
        lineWrapped = BooleanArray(newRows)
        dirtyRows = BooleanArray(newRows)
    }

    private fun clearAllClusters() {
        var row = 0
        while (row < rows) {
            clearClusterRow(row)
            row++
        }
    }

    private fun clearClusterRow(row: Int) {
        clusters[row].fill(null)
    }

    private companion object {
        private const val UNINITIALIZED_GENERATION = -1L

        private fun emptyIntRows(): Array<IntArray> = emptyArray()

        private fun emptyLongRows(): Array<LongArray> = emptyArray()

        private fun emptyClusterRows(): Array<Array<String?>> = emptyArray()
    }
}
