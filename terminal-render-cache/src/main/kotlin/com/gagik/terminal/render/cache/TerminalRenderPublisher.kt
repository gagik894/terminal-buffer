package com.gagik.terminal.render.cache

import com.gagik.terminal.render.api.TerminalRenderFrameReader
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Triple-buffered render cache publisher.
 *
 * One buffer is writer-owned (back).
 * One buffer is UI-readable (front).
 * One buffer is spare (recycled after front is replaced).
 *
 * UI reads are lock-free snapshots of the currently published front buffer.
 * Readers must not retain the cache after [readCurrent] returns.
 */
class TerminalRenderPublisher(
    columns: Int,
    rows: Int,
) {
    @PublishedApi internal val buffers = Array(3) { TerminalRenderCache(columns, rows) }
    private val writerOwned = BooleanArray(BUFFER_COUNT)

    // Buffer indices and writer leases are mutated under publishLock.
    @PublishedApi internal var frontIndex = NO_FRONT
    private var nextWriteIndex = 0
    @PublishedApi internal val publishLock = ReentrantLock()
    private val bufferAvailable = publishLock.newCondition()

    // AtomicReference for lock-free front reads.
    @PublishedApi internal val frontRef = AtomicReference<TerminalRenderCache?>(null)

    /**
     * Called from render worker thread only.
     * Reads from [reader], updates back buffer, publishes as new front.
     */
    fun updateAndPublish(reader: TerminalRenderFrameReader) {
        updateAndPublish(reader, scrollbackOffset = 0)
    }

    /**
     * Called from render worker thread only.
     *
     * [scrollbackOffset] is caller-owned viewport state in lines above the live
     * bottom viewport. The source reader clamps it before rows are copied.
     */
    fun updateAndPublish(reader: TerminalRenderFrameReader, scrollbackOffset: Int) {
        updateAndPublish(reader, scrollbackOffset, viewportRows = 0)
    }

    /**
     * Called from render worker thread only.
     *
     * [viewportRows] requests render-only overscan rows for UI composition. It
     * does not resize terminal state; the source reader clamps the resolved
     * frame height before rows are copied.
     */
    fun updateAndPublish(reader: TerminalRenderFrameReader, scrollbackOffset: Int, viewportRows: Int) {
        val writeIndex = acquireWritableIndex()
        val back = buffers[writeIndex]
        var published = false

        try {
            // The selected back buffer is writer-exclusive until publish or
            // release.
            back.updateFrom(reader, scrollbackOffset, viewportRows)

            publishLock.withLock {
                writerOwned[writeIndex] = false
                frontIndex = writeIndex
                frontRef.set(buffers[frontIndex])
                published = true
                bufferAvailable.signalAll()
            }
        } finally {
            if (!published) {
                releaseWritableIndex(writeIndex)
            }
        }
    }

    /**
     * Returns the latest published snapshot without acquiring a reader lease.
     *
     * This is intended for short polling and tests that do not retain the
     * returned cache or require multi-field snapshot stability. Paint and
     * repaint-planning code should use [readCurrent].
     */
    fun current(): TerminalRenderCache? = frontRef.get()

    /**
     * Reads the latest published front buffer without taking a lock.
     *
     * The callback should only copy or paint from the cache and must not retain
     * it or call back into this publisher. Returning `null` means no frame has
     * been published yet. This method deliberately does not lease the front
     * buffer so Swing paint and repaint planning cannot block publication.
     *
     * @param block reader invoked with the current front buffer.
     * @return [block]'s result, or `null` when no frame is available.
     */
    inline fun <T> readCurrent(block: (TerminalRenderCache) -> T): T? {
        val cache = frontRef.get() ?: return null
        return block(cache)
    }


    private fun acquireWritableIndex(): Int {
        publishLock.withLock {
            while (true) {
                var offset = 0
                while (offset < BUFFER_COUNT) {
                    val index = (nextWriteIndex + offset) % BUFFER_COUNT
                    if (index != frontIndex && !writerOwned[index]) {
                        writerOwned[index] = true
                        nextWriteIndex = (index + 1) % BUFFER_COUNT
                        return index
                    }
                    offset++
                }
                bufferAvailable.await()
            }
        }
    }

    private fun releaseWritableIndex(index: Int) {
        publishLock.withLock {
            check(writerOwned[index]) {
                "TerminalRenderPublisher writer lease underflow for buffer $index"
            }
            writerOwned[index] = false
            bufferAvailable.signalAll()
        }
    }

    companion object {
        @PublishedApi internal const val BUFFER_COUNT = 3
        @PublishedApi internal const val NO_FRONT = -1
    }
}
