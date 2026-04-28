package com.gagik.terminal.input.impl

import com.gagik.terminal.protocol.host.TerminalHostOutput

internal class InputScratchBuffer(
    private val bytes: ByteArray = ByteArray(DEFAULT_CAPACITY),
) {
    var length: Int = 0
        private set

    fun clear() {
        length = 0
    }

    fun appendByte(byte: Int) {
        require(byte in 0..255) { "byte out of range: $byte" }
        require(length < bytes.size) { "input scratch buffer overflow" }
        bytes[length] = byte.toByte()
        length++
    }

    fun appendAscii(text: String) {
        var i = 0
        while (i < text.length) {
            val ch = text[i].code
            require(ch in 0..0x7f) { "non-ASCII char in appendAscii: $ch" }
            appendByte(ch)
            i++
        }
    }

    fun appendDecimal(value: Int) {
        require(value >= 0) { "value must be non-negative: $value" }

        if (value == 0) {
            appendByte('0'.code)
            return
        }

        val start = length
        var current = value
        while (current > 0) {
            appendByte('0'.code + (current % 10))
            current /= 10
        }

        var left = start
        var right = length - 1
        while (left < right) {
            val tmp = bytes[left]
            bytes[left] = bytes[right]
            bytes[right] = tmp
            left++
            right--
        }
    }

    fun writeTo(output: TerminalHostOutput) {
        output.writeBytes(bytes, 0, length)
    }

    companion object {
        private const val DEFAULT_CAPACITY: Int = 64
    }
}
