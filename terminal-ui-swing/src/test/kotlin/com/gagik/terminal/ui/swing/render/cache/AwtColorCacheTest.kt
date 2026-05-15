package com.gagik.terminal.ui.swing.render.cache

import kotlin.test.*

class AwtColorCacheTest {
    @Test
    fun reusesColorInstancesForRepeatedArgbValues() {
        val cache = AwtColorCache()

        assertSame(
            cache.color(0xFF010203.toInt()),
            cache.color(0xFF010203.toInt()),
        )
    }

    @Test
    fun evictsLeastRecentlyUsedColorWhenCapacityIsReached() {
        val cache = AwtColorCache(capacity = 2)
        val first = cache.color(0xFF010203.toInt())
        val second = cache.color(0xFF040506.toInt())

        assertSame(first, cache.color(0xFF010203.toInt()))
        cache.color(0xFF070809.toInt())

        assertSame(first, cache.color(0xFF010203.toInt()))
        assertNotSame(second, cache.color(0xFF040506.toInt()))
    }

    @Test
    fun keepsStorageBoundedForUniqueTruecolorStream() {
        val cache = AwtColorCache(capacity = 4)

        repeat(128) { index ->
            cache.color(0xFF000000.toInt() or index)
        }

        assertEquals(4, cache.cachedColorCount())
    }

    @Test
    fun rejectsNonPositiveCapacity() {
        assertFailsWith<IllegalArgumentException> {
            AwtColorCache(capacity = 0)
        }
    }

    private fun AwtColorCache.cachedColorCount(): Int {
        val field = AwtColorCache::class.java.getDeclaredField("size")
        field.isAccessible = true
        return field.getInt(this)
    }
}
