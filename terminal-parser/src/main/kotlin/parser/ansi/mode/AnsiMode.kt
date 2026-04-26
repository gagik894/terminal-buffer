package com.gagik.parser.ansi.mode

/**
 * Standard ANSI modes used in terminal emulation.
 *
 * Toggled via CSI Pn h (SM) and CSI Pn l (RM).
 */
internal object AnsiMode {
    const val INSERT: Int = 4
    const val NEW_LINE: Int = 20
}
