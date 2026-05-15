package com.gagik.terminal.ui.swing.render.font

/**
 * Reusable mutable text buffer for Java2D draw calls.
 *
 * **Security:** This buffer employs a hard capacity limit to prevent Out-Of-Memory (OOM)
 * exhaustion from hostile terminal streams (e.g., unbounded single-line text dumps).
 *
 * @param initialCapacity Starting size of the char array.
 * @param maxCapacity Absolute upper bound for array growth.
 */
internal class TerminalTextRunBuffer(
    initialCapacity: Int,
    private val maxCapacity: Int = 8192 // Safe upper bound for a physical terminal row
) {
    init {
        require(initialCapacity > 0) { "initialCapacity must be > 0, was $initialCapacity" }
        require(maxCapacity >= initialCapacity) { "maxCapacity must be >= initialCapacity" }
    }

    /** The backing array of code points. */
    var chars: CharArray = CharArray(initialCapacity)
        private set

    /** The number of code points in the buffer. */
    var length: Int = 0
        private set

    /**
     * Resets the buffer without clearing its backing array.
     */
    fun clear() {
        length = 0
    }

    /**
     * Appends one printable ASCII code point.
     * Drops the char if maximum capacity is reached.
     *
     * @param codepoint ASCII code point
     */
    fun appendAscii(codepoint: Int) {
        if (length >= maxCapacity) return // Circuit Breaker
        ensureCapacity(length + 1)
        chars[length] = codepoint.toChar()
        length++
    }

    /**
     * Appends one Unicode scalar value as UTF-16 code units.
     * Drops the char if maximum capacity is reached.
     *
     * @param codepoint Unicode scalar value
     */
    fun appendCodePoint(codepoint: Int) {
        if (codepoint <= 0xffff) {
            if (length >= maxCapacity) return // Circuit Breaker
            ensureCapacity(length + 1)
            chars[length] = codepoint.toChar()
            length++
        } else {
            if (length + 1 >= maxCapacity) return // Circuit Breaker
            ensureCapacity(length + 2)
            val value = codepoint - 0x10000
            chars[length] = (0xd800 or (value ushr 10)).toChar()
            chars[length + 1] = (0xdc00 or (value and 0x3ff)).toChar()
            length += 2
        }
    }

    private fun ensureCapacity(required: Int) {
        if (required <= chars.size) return

        var newCapacity = chars.size * 2
        while (newCapacity < required) {
            newCapacity *= 2
        }

        // Hard clamp to prevent memory exhaustion
        if (newCapacity > maxCapacity) {
            newCapacity = maxCapacity
        }
        chars = chars.copyOf(newCapacity)
    }
}