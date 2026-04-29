package com.gagik.terminal.render.api

/**
 * Receives grapheme cluster text while a row is copied.
 */
fun interface TerminalRenderClusterSink {
    /**
     * Called during `copyLine()` for a [TerminalRenderCellFlags.CLUSTER] cell.
     *
     * [column] is the visual column of the cluster-leading cell. [text] is the
     * full Unicode grapheme cluster. The text is only guaranteed to be valid for
     * the duration of the surrounding render frame callback unless an
     * implementation documents a longer lifetime.
     *
     * @param column zero-based visual column of the cluster-leading cell.
     * @param text full Unicode grapheme cluster text.
     */
    fun onCluster(column: Int, text: String)
}
