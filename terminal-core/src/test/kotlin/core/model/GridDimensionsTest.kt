package com.gagik.core.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class GridDimensionsTest {

    @Test
    fun `rejects non-positive dimensions`() {
        assertThrows<IllegalArgumentException> { GridDimensions(0, 1) }
        assertThrows<IllegalArgumentException> { GridDimensions(1, 0) }
        assertThrows<IllegalArgumentException> { GridDimensions(-1, 1) }
        assertThrows<IllegalArgumentException> { GridDimensions(1, -1) }
    }

    @Test
    fun `clamps coordinates to bounds`() {
        val dimensions = GridDimensions(80, 24)

        assertEquals(0, dimensions.clampCol(-5))
        assertEquals(79, dimensions.clampCol(100))
        assertEquals(0, dimensions.clampRow(-9))
        assertEquals(23, dimensions.clampRow(999))
    }

    @Test
    fun `reports valid coordinates`() {
        val dimensions = GridDimensions(10, 5)

        assertTrue(dimensions.isValidCol(0))
        assertTrue(dimensions.isValidRow(4))
        assertFalse(dimensions.isValidCol(10))
        assertFalse(dimensions.isValidRow(5))
    }
}

