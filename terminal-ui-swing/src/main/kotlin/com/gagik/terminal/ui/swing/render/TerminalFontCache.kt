package com.gagik.terminal.ui.swing.render

import java.awt.Font

/**
 * Caches terminal font style variants for one settings snapshot.
 */
internal class TerminalFontCache(
    codePointFallbackCapacityPerStyle: Int = DEFAULT_CODE_POINT_FALLBACK_CAPACITY_PER_STYLE,
    textFallbackCapacityPerStyle: Int = DEFAULT_TEXT_FALLBACK_CAPACITY_PER_STYLE,
) {
    init {
        require(codePointFallbackCapacityPerStyle > 0) {
            "codePointFallbackCapacityPerStyle must be > 0, was $codePointFallbackCapacityPerStyle"
        }
        require(textFallbackCapacityPerStyle > 0) {
            "textFallbackCapacityPerStyle must be > 0, was $textFallbackCapacityPerStyle"
        }
    }

    private var baseFont: Font? = null
    private var fallbackBaseFonts: List<Font> = emptyList()
    private var systemFallbackBaseFonts: List<Font> = emptyList()
    private var useSystemFallbackFonts: Boolean = false
    private val styleFonts = arrayOfNulls<Font>(STYLE_COUNT)
    private var fallbackStyleFonts: Array<Array<Font?>> = emptyArray()
    private var systemStyleFonts: Array<Array<Font?>> = emptyArray()
    private val resolvedCodePointFonts = Array(STYLE_COUNT) {
        IntFontLru(codePointFallbackCapacityPerStyle)
    }
    private val resolvedTextFonts = Array(STYLE_COUNT) {
        StringFontLru(textFallbackCapacityPerStyle)
    }
    private var fontGeneration: Int = 0

    /**
     * Increments whenever font resolution can produce different fonts.
     */
    val generation: Int
        get() = fontGeneration

    /**
     * Rebuilds cached style variants when [font] changes.
     *
     * @param font base terminal font.
     * @return `true` when cached font state changed.
     */
    fun update(font: Font, fallbackFonts: List<Font>, useSystemFallbackFonts: Boolean): Boolean {
        if (
            font == baseFont &&
            fallbackFonts == fallbackBaseFonts &&
            useSystemFallbackFonts == this.useSystemFallbackFonts
        ) {
            return false
        }

        baseFont = font
        fallbackBaseFonts = fallbackFonts
        this.useSystemFallbackFonts = useSystemFallbackFonts
        styleFonts.fill(null)
        styleFonts[font.style and STYLE_MASK] = font
        fallbackStyleFonts = Array(fallbackFonts.size) { arrayOfNulls(STYLE_COUNT) }
        systemFallbackBaseFonts = if (useSystemFallbackFonts) {
            TerminalSystemFallbackFonts.fontsOrStartLoading()
        } else {
            emptyList()
        }
        systemStyleFonts = Array(systemFallbackBaseFonts.size) { arrayOfNulls(STYLE_COUNT) }
        for (cache in resolvedCodePointFonts) {
            cache.clear()
        }
        for (cache in resolvedTextFonts) {
            cache.clear()
        }
        fontGeneration++
        return true
    }

    /**
     * Returns a cached font variant for [style].
     *
     * @param style AWT style bit mask.
     * @return cached style font.
     */
    fun font(style: Int): Font {
        val normalizedStyle = style and STYLE_MASK
        val cached = styleFonts[normalizedStyle]
        if (cached != null) return cached

        val font = requireNotNull(baseFont) {
            "TerminalFontCache.update must be called before font"
        }.deriveFont(normalizedStyle)
        styleFonts[normalizedStyle] = font
        return font
    }

    /**
     * Returns the first cached style font that can display [codePoint].
     *
     * Single code points use a primitive-keyed bounded cache so hostile streams
     * of unique Unicode cells cannot retain unbounded strings.
     */
    fun fontForCodePoint(codePoint: Int, style: Int): Font {
        val normalizedStyle = style and STYLE_MASK
        val primary = font(normalizedStyle)
        if (primary.canDisplay(codePoint)) return primary

        val styleResolvedFonts = resolvedCodePointFonts[normalizedStyle]
        val cached = styleResolvedFonts[codePoint]
        if (cached != null) return cached

        var index = 0
        while (index < fallbackBaseFonts.size) {
            val fallback = fallbackFont(index, normalizedStyle)
            if (fallback.canDisplay(codePoint)) {
                styleResolvedFonts.put(codePoint, fallback)
                return fallback
            }
            index++
        }

        if (useSystemFallbackFonts) {
            refreshSystemFallbackFonts()
            index = 0
            while (index < systemFallbackBaseFonts.size) {
                val fallback = systemFallbackFont(index, normalizedStyle)
                if (fallback.canDisplay(codePoint)) {
                    styleResolvedFonts.put(codePoint, fallback)
                    return fallback
                }
                index++
            }
        }

        styleResolvedFonts.put(codePoint, primary)
        return primary
    }

    /**
     * Returns the first cached style font that can display all UTF-16 units in
     * [text], falling back to [font] when no configured fallback covers it.
     *
     * Grapheme-cluster lookups are bounded per style. The render cache already
     * owns cluster strings for visible cells; this renderer cache must not keep
     * every historical cluster alive for a months-long terminal session.
     */
    fun fontForText(text: String, style: Int): Font {
        val normalizedStyle = style and STYLE_MASK
        val primary = font(normalizedStyle)
        if (primary.canDisplayUpTo(text) < 0) return primary

        val styleResolvedTextFonts = resolvedTextFonts[normalizedStyle]
        val cached = styleResolvedTextFonts[text]
        if (cached != null) return cached

        var index = 0
        while (index < fallbackBaseFonts.size) {
            val fallback = fallbackFont(index, normalizedStyle)
            if (fallback.canDisplayUpTo(text) < 0) {
                styleResolvedTextFonts[text] = fallback
                return fallback
            }
            index++
        }

        if (useSystemFallbackFonts) {
            refreshSystemFallbackFonts()
            index = 0
            while (index < systemFallbackBaseFonts.size) {
                val fallback = systemFallbackFont(index, normalizedStyle)
                if (fallback.canDisplayUpTo(text) < 0) {
                    styleResolvedTextFonts[text] = fallback
                    return fallback
                }
                index++
            }
        }

        styleResolvedTextFonts[text] = primary
        return primary
    }

    /**
     * Refreshes asynchronously loaded system fallback fonts, if enabled.
     *
     * @return `true` when font resolution changed.
     */
    fun refreshSystemFallbackFonts(): Boolean {
        if (!useSystemFallbackFonts) return false

        val loadedFonts = TerminalSystemFallbackFonts.fontsOrStartLoading()
        if (loadedFonts == systemFallbackBaseFonts) return false

        systemFallbackBaseFonts = loadedFonts
        systemStyleFonts = Array(loadedFonts.size) { arrayOfNulls(STYLE_COUNT) }
        for (cache in resolvedCodePointFonts) {
            cache.clear()
        }
        for (cache in resolvedTextFonts) {
            cache.clear()
        }
        fontGeneration++
        return true
    }

    private fun fallbackFont(index: Int, style: Int): Font {
        val normalizedStyle = style and STYLE_MASK
        val cached = fallbackStyleFonts[index][normalizedStyle]
        if (cached != null) return cached

        val base = requireNotNull(baseFont) {
            "TerminalFontCache.update must be called before fallbackFont"
        }
        val fallback = fallbackBaseFonts[index]
            .deriveFont(normalizedStyle, base.size2D)
        fallbackStyleFonts[index][normalizedStyle] = fallback
        return fallback
    }

    private fun systemFallbackFont(index: Int, style: Int): Font {
        val normalizedStyle = style and STYLE_MASK
        val cached = systemStyleFonts[index][normalizedStyle]
        if (cached != null) return cached

        val base = requireNotNull(baseFont) {
            "TerminalFontCache.update must be called before systemFallbackFont"
        }
        val fallback = systemFallbackBaseFonts[index]
            .deriveFont(normalizedStyle, base.size2D)
        systemStyleFonts[index][normalizedStyle] = fallback
        return fallback
    }

    private class StringFontLru(
        private val capacity: Int,
    ) : LinkedHashMap<String, Font>(capacity, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Font>?): Boolean {
            return size > capacity
        }
    }

    private class IntFontLru(capacity: Int) {
        private val entryKeys = IntArray(capacity)
        private val entryFonts = arrayOfNulls<Font>(capacity)
        private val previous = IntArray(capacity) { EMPTY }
        private val next = IntArray(capacity) { EMPTY }
        private val hashKeys = IntArray(hashCapacity(capacity))
        private val hashEntries = IntArray(hashKeys.size) { EMPTY }
        private val hashMask = hashKeys.size - 1
        private var size = 0
        private var head = EMPTY
        private var tail = EMPTY

        operator fun get(key: Int): Font? {
            val entry = findEntry(key)
            if (entry == EMPTY) return null

            moveToHead(entry)
            return entryFonts[entry]
        }

        fun put(key: Int, font: Font) {
            val existing = findEntry(key)
            if (existing != EMPTY) {
                entryFonts[existing] = font
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
            entryFonts[entry] = font
            linkHead(entry)
            insertHashEntry(key, entry)
        }

        fun clear() {
            java.util.Arrays.fill(entryFonts, null)
            java.util.Arrays.fill(previous, EMPTY)
            java.util.Arrays.fill(next, EMPTY)
            java.util.Arrays.fill(hashEntries, EMPTY)
            size = 0
            head = EMPTY
            tail = EMPTY
        }

        private fun findEntry(key: Int): Int {
            var slot = hashSlot(key)
            while (true) {
                val entry = hashEntries[slot]
                if (entry == EMPTY) return EMPTY
                if (hashKeys[slot] == key) return entry
                slot = (slot + 1) and hashMask
            }
        }

        private fun insertHashEntry(key: Int, entry: Int) {
            var slot = hashSlot(key)
            while (hashEntries[slot] != EMPTY) {
                slot = (slot + 1) and hashMask
            }
            hashKeys[slot] = key
            hashEntries[slot] = entry
        }

        private fun removeHashEntry(key: Int, entry: Int) {
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

        private fun hashSlot(key: Int): Int {
            var hash = key
            hash = hash xor (hash ushr 16)
            hash *= -2048144789
            hash = hash xor (hash ushr 13)
            return hash and hashMask
        }
    }

    private companion object {
        private const val STYLE_COUNT = 4
        private const val STYLE_MASK = Font.BOLD or Font.ITALIC
        private const val DEFAULT_CODE_POINT_FALLBACK_CAPACITY_PER_STYLE = 4096
        private const val DEFAULT_TEXT_FALLBACK_CAPACITY_PER_STYLE = 1024
        private const val LOAD_FACTOR = 0.75f
        private const val EMPTY = -1

        private fun hashCapacity(entryCapacity: Int): Int {
            var capacity = 1
            while (capacity < entryCapacity * 2) {
                capacity = capacity shl 1
            }
            return capacity
        }
    }
}
