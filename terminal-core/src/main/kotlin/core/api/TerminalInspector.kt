package com.gagik.core.api

import com.gagik.core.model.Attributes

/**
 * Allocating inspection contract for the terminal buffer.
 *
 * Intended for tests and debugging only. Every method here allocates — do
 * not use on a hot rendering path. Production code should use [TerminalReader].
 */
interface TerminalInspector {

    /**
     * Returns the attributes at a screen position as an unpacked [Attributes] object.
     *
     * @param col Column index (0-based).
     * @param row Row index (0-based).
     * @return Unpacked attributes, or `null` if the position is out of bounds.
     */
    fun getAttrAt(col: Int, row: Int): Attributes?

    /**
     * Returns the content of a visible row as a string, trimming trailing blank
     * cells while preserving intentional space characters.
     *
     * @param row Visible row index (0-based).
     * @return The row text, or an empty string if the row is blank or out of bounds.
     */
    fun getLineAsString(row: Int): String

    /**
     * Returns the visible screen as a newline-joined string, top to bottom.
     */
    fun getScreenAsString(): String

    /**
     * Returns scrollback history followed by the visible screen as a
     * newline-joined string, oldest line first.
     */
    fun getAllAsString(): String
}