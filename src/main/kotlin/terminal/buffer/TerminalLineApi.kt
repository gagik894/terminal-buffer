package com.gagik.terminal.buffer

/**
 * A read-only, EPHEMERAL view of a single physical terminal line.
 * * DANGER - TEMPORAL COUPLING:
 * The UI Renderer MUST NOT store or hold references to this object outside the 
 * immediate execution scope of the current render frame. The backing memory 
 * may mutate at any time if background output arrives.
 */
interface TerminalLineApi {
    val width: Int
    fun getCodepoint(col: Int): Int
    fun getPackedAttr(col: Int): Int
}
