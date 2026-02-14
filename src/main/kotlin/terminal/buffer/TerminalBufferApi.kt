package com.gagik.terminal.buffer

import com.gagik.terminal.model.Attributes

/**
 * Public API for the terminal buffer.
 * Exposes only the operations needed by consumers.
 */
interface TerminalBufferApi {
    val width: Int
    val height: Int
    val cursorCol: Int
    val cursorRow: Int
    val historySize: Int

    fun setAttributes(attributes: Attributes)

    fun resetPen()

    fun setCursor(col: Int, row: Int)
    fun moveCursor(dx: Int, dy: Int)
    fun cursorUp(n: Int = 1)
    fun cursorDown(n: Int = 1)
    fun cursorLeft(n: Int = 1)
    fun cursorRight(n: Int = 1)
    fun resetCursor()

    fun writeChar(codepoint: Int)
    fun writeText(text: String)
    fun insertText(text: String)
    fun newLine()
    fun carriageReturn()

    fun scrollUp()
    fun clearScreen()
    fun clearAll()
    fun fillLine(codepoint: Int = 0)
    fun fillLineAt(row: Int, codepoint: Int = 0)

    fun getCharAt(col: Int, row: Int): Int?
    fun getCodepointAt(col: Int, row: Int): Int?
    fun getCharAsStringAt(col: Int, row: Int): String?
    fun getAttrAt(col: Int, row: Int): Attributes?
    fun getHistoryCharAt(index: Int, col: Int): Int?
    fun getHistoryCodepointAt(index: Int, col: Int): Int?
    fun getHistoryCharAsStringAt(index: Int, col: Int): String?
    fun getHistoryAttrAt(index: Int, col: Int): Attributes?
    fun getLineAsString(row: Int): String
    fun getHistoryLineAsString(index: Int): String
    fun getScreenAsString(): String
    fun getAllAsString(): String

    fun reset()
}
