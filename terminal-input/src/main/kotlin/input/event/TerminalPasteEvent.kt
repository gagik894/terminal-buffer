package com.gagik.terminal.input.event

/**
 * Paste event accepted by the terminal input encoder.
 *
 * @property text pasted text before any future host paste policy is applied.
 */
data class TerminalPasteEvent(
    val text: String,
)
