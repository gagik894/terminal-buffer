package com.gagik.terminal.ui.swing.render

import java.awt.font.FontRenderContext
import java.awt.font.TextLayout

/**
 * Bounded renderer-local cache for shaped complex text layouts.
 *
 * Single code points use a primitive-key LRU so repeated Unicode cells do not
 * allocate lookup keys. Grapheme clusters are already stored as strings in the
 * render cache, so cluster layouts use bounded access-order maps by style.
 */
internal class TerminalComplexTextLayoutCache(
    codePointCapacity: Int = DEFAULT_CODE_POINT_CAPACITY,
    clusterCapacityPerStyle: Int = DEFAULT_CLUSTER_CAPACITY_PER_STYLE,
) {
    init {
        require(codePointCapacity > 0) {
            "codePointCapacity must be > 0, was $codePointCapacity"
        }
        require(clusterCapacityPerStyle > 0) {
            "clusterCapacityPerStyle must be > 0, was $clusterCapacityPerStyle"
        }
    }

    private val codePointLayouts = LongTextLayoutLru(codePointCapacity)
    private val clusterLayouts = Array(STYLE_COUNT) {
        StringTextLayoutLru(clusterCapacityPerStyle)
    }
    private var fontRenderContext: FontRenderContext? = null

    /**
     * Clears cached layouts.
     */
    fun clear() {
        fontRenderContext = null
        codePointLayouts.clear()
        for (cache in clusterLayouts) {
            cache.clear()
        }
    }

    /**
     * Returns a cached layout for a single Unicode code point.
     */
    fun codePointLayout(
        codePoint: Int,
        style: Int,
        fontRenderContext: FontRenderContext,
        fontCache: TerminalFontCache,
    ): TextLayout {
        prepare(fontRenderContext)
        val normalizedStyle = style and STYLE_MASK
        val key = codePointKey(codePoint, normalizedStyle)
        val cached = codePointLayouts[key]
        if (cached != null) return cached

        val text = String(Character.toChars(codePoint))
        val layout = TextLayout(text, fontCache.fontForText(text, normalizedStyle), fontRenderContext)
        codePointLayouts.put(key, layout)
        return layout
    }

    /**
     * Returns a cached layout for a grapheme cluster.
     */
    fun clusterLayout(
        text: String,
        style: Int,
        fontRenderContext: FontRenderContext,
        fontCache: TerminalFontCache,
    ): TextLayout {
        prepare(fontRenderContext)
        val normalizedStyle = style and STYLE_MASK
        val styleLayouts = clusterLayouts[normalizedStyle]
        val cached = styleLayouts[text]
        if (cached != null) return cached

        val layout = TextLayout(text, fontCache.fontForText(text, normalizedStyle), fontRenderContext)
        styleLayouts[text] = layout
        return layout
    }

    private fun prepare(nextFontRenderContext: FontRenderContext) {
        if (nextFontRenderContext == fontRenderContext) return

        fontRenderContext = nextFontRenderContext
        codePointLayouts.clear()
        for (cache in clusterLayouts) {
            cache.clear()
        }
    }

    private class StringTextLayoutLru(
        private val capacity: Int,
    ) : LinkedHashMap<String, TextLayout>(capacity, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, TextLayout>?): Boolean {
            return size > capacity
        }
    }

    private class LongTextLayoutLru(capacity: Int) {
        private val entryKeys = LongArray(capacity)
        private val entryLayouts = arrayOfNulls<TextLayout>(capacity)
        private val previous = IntArray(capacity) { EMPTY }
        private val next = IntArray(capacity) { EMPTY }
        private val hashKeys = LongArray(hashCapacity(capacity))
        private val hashEntries = IntArray(hashKeys.size) { EMPTY }
        private val hashMask = hashKeys.size - 1
        private var size = 0
        private var head = EMPTY
        private var tail = EMPTY

        operator fun get(key: Long): TextLayout? {
            val entry = findEntry(key)
            if (entry == EMPTY) return null

            moveToHead(entry)
            return entryLayouts[entry]
        }

        fun put(key: Long, layout: TextLayout) {
            val existing = findEntry(key)
            if (existing != EMPTY) {
                entryLayouts[existing] = layout
                moveToHead(existing)
                return
            }

            val entry = if (size < entryKeys.size) {
                size++
            } else {
                val evicted = tail
                removeHashEntry(entryKeys[evicted], evicted)
                unlink(evicted)
                evicted
            }

            entryKeys[entry] = key
            entryLayouts[entry] = layout
            linkHead(entry)
            insertHashEntry(key, entry)
        }

        fun clear() {
            java.util.Arrays.fill(entryLayouts, null)
            java.util.Arrays.fill(previous, EMPTY)
            java.util.Arrays.fill(next, EMPTY)
            java.util.Arrays.fill(hashEntries, EMPTY)
            size = 0
            head = EMPTY
            tail = EMPTY
        }

        private fun findEntry(key: Long): Int {
            var slot = hashSlot(key)
            while (true) {
                val entry = hashEntries[slot]
                if (entry == EMPTY) return EMPTY
                if (hashKeys[slot] == key) return entry
                slot = (slot + 1) and hashMask
            }
        }

        private fun insertHashEntry(key: Long, entry: Int) {
            var slot = hashSlot(key)
            while (hashEntries[slot] != EMPTY) {
                slot = (slot + 1) and hashMask
            }
            hashKeys[slot] = key
            hashEntries[slot] = entry
        }

        private fun removeHashEntry(key: Long, entry: Int) {
            var slot = hashSlot(key)
            while (true) {
                if (hashEntries[slot] == entry && hashKeys[slot] == key) {
                    hashEntries[slot] = EMPTY
                    reinsertHashCluster((slot + 1) and hashMask)
                    return
                }
                slot = (slot + 1) and hashMask
            }
        }

        private fun reinsertHashCluster(startSlot: Int) {
            var slot = startSlot
            while (hashEntries[slot] != EMPTY) {
                val key = hashKeys[slot]
                val entry = hashEntries[slot]
                hashEntries[slot] = EMPTY
                insertHashEntry(key, entry)
                slot = (slot + 1) and hashMask
            }
        }

        private fun moveToHead(entry: Int) {
            if (entry == head) return
            unlink(entry)
            linkHead(entry)
        }

        private fun unlink(entry: Int) {
            val previousEntry = previous[entry]
            val nextEntry = next[entry]
            if (previousEntry != EMPTY) {
                next[previousEntry] = nextEntry
            } else {
                head = nextEntry
            }
            if (nextEntry != EMPTY) {
                previous[nextEntry] = previousEntry
            } else {
                tail = previousEntry
            }
            previous[entry] = EMPTY
            next[entry] = EMPTY
        }

        private fun linkHead(entry: Int) {
            previous[entry] = EMPTY
            next[entry] = head
            if (head != EMPTY) {
                previous[head] = entry
            } else {
                tail = entry
            }
            head = entry
        }

        private fun hashSlot(key: Long): Int {
            var hash = key
            hash = hash xor (hash ushr 33)
            hash *= -49064778989728563L
            hash = hash xor (hash ushr 33)
            return hash.toInt() and hashMask
        }
    }

    private companion object {
        private const val DEFAULT_CODE_POINT_CAPACITY = 4096
        private const val DEFAULT_CLUSTER_CAPACITY_PER_STYLE = 1024
        private const val STYLE_COUNT = 4
        private const val STYLE_MASK = java.awt.Font.BOLD or java.awt.Font.ITALIC
        private const val LOAD_FACTOR = 0.75f
        private const val EMPTY = -1

        private fun codePointKey(codePoint: Int, style: Int): Long {
            return (style.toLong() shl 32) or (codePoint.toLong() and 0xFFFF_FFFFL)
        }

        private fun hashCapacity(entryCapacity: Int): Int {
            var capacity = 1
            while (capacity < entryCapacity * 2) {
                capacity = capacity shl 1
            }
            return capacity
        }
    }
}
