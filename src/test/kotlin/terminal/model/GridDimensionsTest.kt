package com.gagik.terminal.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("GridDimensions")
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

    @Test
    fun `require methods fail fast`() {
        val dimensions = GridDimensions(10, 5)

        dimensions.requireValidCol(9)
        dimensions.requireValidRow(4)

        assertThrows<IllegalArgumentException> { dimensions.requireValidCol(10) }
        assertThrows<IllegalArgumentException> { dimensions.requireValidRow(5) }
    }
}

