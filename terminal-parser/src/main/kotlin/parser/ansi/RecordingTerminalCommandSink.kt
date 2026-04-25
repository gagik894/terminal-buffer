package com.gagik.parser.ansi

import com.gagik.parser.TerminalCommandSink

internal class RecordingTerminalCommandSink : TerminalCommandSink {
    data class Osc(
        val commandCode: Int,
        val payload: ByteArray,
        val length: Int,
        val overflowed: Boolean,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Osc

            if (commandCode != other.commandCode) return false
            if (length != other.length) return false
            if (overflowed != other.overflowed) return false
            if (!payload.contentEquals(other.payload)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = commandCode
            result = 31 * result + length
            result = 31 * result + overflowed.hashCode()
            result = 31 * result + payload.contentHashCode()
            return result
        }
    }

    val events = ArrayList<String>()
    val osc = ArrayList<Osc>()

    override fun writeCodepoint(codepoint: Int) {
        events += "writeCodepoint:$codepoint"
    }

    override fun writeCluster(codepoints: IntArray, length: Int, charWidth: Int) {
        events += "writeCluster:$length:$charWidth:${codepoints.take(length).joinToString(":")}"
    }

    override fun bell() {
        events += "bell"
    }

    override fun backspace() {
        events += "backspace"
    }

    override fun tab() {
        events += "tab"
    }

    override fun lineFeed() {
        events += "lineFeed"
    }

    override fun carriageReturn() {
        events += "carriageReturn"
    }

    override fun reverseIndex() {
        events += "reverseIndex"
    }

    override fun nextLine() {
        events += "nextLine"
    }

    override fun saveCursor() {
        events += "saveCursor"
    }

    override fun restoreCursor() {
        events += "restoreCursor"
    }

    override fun cursorUp(n: Int) {
        events += "cursorUp:$n"
    }

    override fun cursorDown(n: Int) {
        events += "cursorDown:$n"
    }

    override fun cursorForward(n: Int) {
        events += "cursorForward:$n"
    }

    override fun cursorBackward(n: Int) {
        events += "cursorBackward:$n"
    }

    override fun cursorNextLine(n: Int) {
        events += "cursorNextLine:$n"
    }

    override fun cursorPreviousLine(n: Int) {
        events += "cursorPreviousLine:$n"
    }

    override fun setCursorColumn(col: Int) {
        events += "setCursorColumn:$col"
    }

    override fun setCursorRow(row: Int) {
        events += "setCursorRow:$row"
    }

    override fun setCursorAbsolute(row: Int, col: Int) {
        events += "setCursorAbsolute:$row:$col"
    }

    override fun eraseInDisplay(mode: Int, selective: Boolean) {
        events += "eraseInDisplay:$mode:$selective"
    }

    override fun eraseInLine(mode: Int, selective: Boolean) {
        events += "eraseInLine:$mode:$selective"
    }

    override fun insertLines(n: Int) {
        events += "insertLines:$n"
    }

    override fun deleteLines(n: Int) {
        events += "deleteLines:$n"
    }

    override fun insertCharacters(n: Int) {
        events += "insertCharacters:$n"
    }

    override fun deleteCharacters(n: Int) {
        events += "deleteCharacters:$n"
    }

    override fun eraseCharacters(n: Int) {
        events += "eraseCharacters:$n"
    }

    override fun scrollUp(n: Int) {
        events += "scrollUp:$n"
    }

    override fun scrollDown(n: Int) {
        events += "scrollDown:$n"
    }

    override fun setAnsiMode(mode: Int, enable: Boolean) {
        events += "setAnsiMode:$mode:$enable"
    }

    override fun setDecMode(mode: Int, enable: Boolean) {
        events += "setDecMode:$mode:$enable"
    }

    override fun onOsc(
        commandCode: Int,
        payload: ByteArray,
        length: Int,
        overflowed: Boolean,
    ) {
        events += "osc:$commandCode:$length:$overflowed"
        osc += Osc(
            commandCode = commandCode,
            payload = payload.copyOf(length),
            length = length,
            overflowed = overflowed,
        )
    }
}