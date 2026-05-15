package com.gagik.terminal.ui.swing.render.cache

import com.gagik.terminal.ui.swing.render.cache.TerminalComplexTextLayoutCache.Companion.REPLACEMENT_CHARACTER
import java.awt.Font
import java.awt.font.FontRenderContext
import java.awt.font.TextLayout
import java.util.*

/**
 * Bounded renderer-local cache for shaped complex text layouts.
 *
 * Single code points use a primitive-key LRU so repeated Unicode cells do not
 * allocate lookup keys. Grapheme clusters are already stored as strings in the
 * render cache, so cluster layouts use bounded access-order maps by style.
 *
 * **Thread Safety:** Not thread-safe. This cache must only be accessed
 * from the Swing Event Dispatch Thread (EDT).
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
    private var fontGeneration: Int = -1

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
     * Resolves a shaped text layout for a single Unicode code point using an allocation-free primitive key.
     *
     * Avoids heap allocation by converting the 32-bit integer code point and its corresponding
     * style mask into a single packed 64-bit primitive key. This layout bypasses standard string
     * instantiation during cache lookup passes.
     *
     * @param codePoint The 32-bit Unicode scalar value to render.
     * @param style The packed integer bitmask specifying the target font style.
     * @param fontRenderContext The active Java2D graphics context for metrics tracking.
     * @param fontCache The stateful font resolver for fallback execution.
     * @return An immutable, single-character [TextLayout].
     */
    fun codePointLayout(
        codePoint: Int,
        style: Int,
        fontRenderContext: FontRenderContext,
        fontCache: TerminalFontCache,
    ): TextLayout {
        fontCache.refreshSystemFallbackFonts()
        prepare(fontRenderContext, fontCache.generation)

        val normalizedStyle = style and STYLE_MASK
        val key = codePointKey(codePoint, normalizedStyle)
        val cached = codePointLayouts[key]
        if (cached != null) return cached

        // Convert the code point directly to a transient char array on cache misses to minimize
        // object lifecycle footprints prior to layout shaping.
        val text = String(Character.toChars(codePoint))
        val layout = TextLayout(text, fontCache.fontForCodePoint(codePoint, normalizedStyle), fontRenderContext)
        codePointLayouts.put(key, layout)
        return layout
    }

    /**
     * Resolves a shaped text layout for a multi-code-unit grapheme cluster, enforcing size limits
     * to insulate the rendering pipeline from resource exhaustion.
     *
     * This method handles the complex text shaping pipeline. It applies a defensive length filter
     * to neutralize adversarial input sequences (e.g., pathological Zero-Width Joiner loops)
     * *prior* to cache interrogation. This design choice collapses unbounded variants into a
     * uniform tracking token, guaranteeing an upper bound on memory consumption and preventing
     * cache thrashing.
     *
     * @param text The raw character sequence or grapheme cluster to be shaped.
     * @param style The packed integer bitmask specifying the target font style (Bold/Italic variants).
     * @param fontRenderContext The active Java2D graphics context specifying scaling and anti-aliasing configurations.
     * @param fontCache The stateful primary and fallback font resolver for typography resolution.
     * @return An immutable, shaped [TextLayout] strictly bound to terminal cell advances.
     */
    fun clusterLayout(
        text: String,
        style: Int,
        fontRenderContext: FontRenderContext,
        fontCache: TerminalFontCache,
    ): TextLayout {
        // Enforce an upper bound on text length to isolate the OpenType layout engine from
        // CPU-bound execution spikes. Hostile or out-of-spec inputs are mapped onto a safe
        // replacement character sequence.
        val safeText = sanitizeCluster(text)

        fontCache.refreshSystemFallbackFonts()
        prepare(fontRenderContext, fontCache.generation)

        val normalizedStyle = style and STYLE_MASK
        val styleLayouts = clusterLayouts[normalizedStyle]

        // Interrogate the style-segregated LRU cache using the sanitized text token to
        // ensure O(1) layout retrieval for repeating clusters and neutralized attack strings.
        val cached = styleLayouts[safeText]
        if (cached != null) return cached

        // Execute heavy font-fallback tracking and glyph layout calculation only on a cache miss.
        // The resultant layout is committed to the LRU map to eliminate subsequent allocation.
        val layout = TextLayout(safeText, fontCache.fontForText(safeText, normalizedStyle), fontRenderContext)
        styleLayouts[safeText] = layout
        return layout
    }

    private fun prepare(nextFontRenderContext: FontRenderContext, nextFontGeneration: Int) {
        if (nextFontRenderContext == fontRenderContext && nextFontGeneration == fontGeneration) return

        fontRenderContext = nextFontRenderContext
        fontGeneration = nextFontGeneration
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

    /**
     * ARCHITECTURAL WARNING: MANUAL MONOMORPHIZATION
     * * Do not attempt to DRY (Don't Repeat Yourself) this cache logic using
     * generic base classes (e.g., `<T>`).
     * * This terminal emulator relies on a strict zero-allocation render loop.
     * Because the JVM uses Type Erasure, passing primitives (Int, Long) to a
     * generic type parameter forces boxing (allocating `java.lang.Integer` on the heap).
     * * To maintain 60FPS without Garbage Collection stutter, this hash map logic
     * is manually duplicated to operate directly on contiguous primitive arrays
     * (`IntArray`, `LongArray`). Suppress IDE duplication warnings and leave
     * this math alone.
     */
    @Suppress("DuplicatedCode")
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
            Arrays.fill(entryLayouts, null)
            Arrays.fill(previous, EMPTY)
            Arrays.fill(next, EMPTY)
            Arrays.fill(hashEntries, EMPTY)
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
        private const val STYLE_MASK = Font.BOLD or Font.ITALIC
        private const val LOAD_FACTOR = 0.75f
        private const val EMPTY = -1

        /**
         * The absolute upper bound for permitted UTF-16 code units within a single cluster.
         * Designed to safely accommodate complex multi-modifier emojis (e.g., standard family
         * configurations) while intercepting malicious deep-nested styling exploits.
         */
        private const val MAX_CLUSTER_LENGTH = 32

        /** The uniform replacement token used to substitute out-of-bounds sequences. */
        private const val REPLACEMENT_CHARACTER = "\uFFFD"

        @JvmStatic
        private fun codePointKey(codePoint: Int, style: Int): Long {
            return (style.toLong() shl 32) or (codePoint.toLong() and 0xFFFF_FFFFL)
        }

        @JvmStatic
        private fun hashCapacity(entryCapacity: Int): Int {
            var capacity = 1
            while (capacity < entryCapacity * 2) {
                capacity = capacity shl 1
            }
            return capacity
        }

        /**
         * Evaluates a grapheme sequence against string length constraints to defend against
         * CPU-exhaustion vectors.
         *
         * @param cluster The input text sequence under inspection.
         * @return The original sequence if it satisfies structural constraints; otherwise, the
         * fallback [REPLACEMENT_CHARACTER] string.
         */
        @JvmStatic
        fun sanitizeCluster(cluster: String): String {
            return if (cluster.length > MAX_CLUSTER_LENGTH) {
                REPLACEMENT_CHARACTER
            } else {
                cluster
            }
        }
    }
}