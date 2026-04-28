package com.gagik.terminal.input.event

/**
 * Focus transition event accepted by the terminal input encoder.
 *
 * @property focused true when the terminal surface gained focus, false when it
 * lost focus.
 */
data class TerminalFocusEvent(
    val focused: Boolean,
)
