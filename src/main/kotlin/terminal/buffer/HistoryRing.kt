package com.gagik.terminal.buffer

import com.gagik.terminal.model.Line


/**
 * A fixed-capacity ring buffer of Lines.
 *
 * purely handles storage mechanics:
 * - 0 is the oldest logical line
 * - size-1 is the newest logical line
 * - managing the circular index
 */
internal class HistoryRing(
    val capacity: Int,
    private val lineFactory: () -> Line
) {
    private val data: Array<Line> = Array(capacity) { lineFactory() }

    private var head: Int = 0 // physical index of the oldest element
    var size: Int = 0 // number of logical lines currently in the ring
        private set


    /**
     * Gets the Line at the specified logical index.
     *
     * @param i The logical index of the line to retrieve (0 is oldest, size-1 is newest)
     * @return The Line at the specified logical index
     * @throws IndexOutOfBoundsException if i is not in the range [0, size-1]
     */
    operator fun get(i: Int): Line {
        if (i !in 0 until size) {
            throw IndexOutOfBoundsException("index $i out of bounds (size=$size)")
        }
        val physical = (head + i) % capacity
        return data[physical]
    }

    /**
     * Pushes a new line into the ring.
     * If the ring is not full, it returns the next available slot for the new line.
     * If the ring is full, it recycles the oldest line (at head) and returns it for reuse.
     *
     * @return A Line that can be used for the new entry. This may be a new Line or a recycled one.
     */
    fun push(): Line {
        if (size < capacity) {
            // Use the next available slot
            val tailPhysical = (head + size) % capacity
            size++
            return data[tailPhysical]
        } else {
            // Buffer full. Recycle the oldest line (head).
            val recycledLine = data[head]
            head = (head + 1) % capacity
            return recycledLine
        }
    }

    /**
     * Clears the ring buffer by resetting head and size.
     * The Line objects themselves are not modified, but they will be overwritten by future pushes.
     */
    fun clear() {
        head = 0
        size = 0
    }
}