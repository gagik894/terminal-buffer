package com.gagik.terminal.util

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ValidationsTest {

    @Test
    fun `requirePositive accepts strictly positive values`() {
        Validations.requirePositive(1, "value")
    }

    @Test
    fun `requirePositive rejects zero or negative`() {
        assertThrows<IllegalArgumentException> { Validations.requirePositive(0, "value") }
        assertThrows<IllegalArgumentException> { Validations.requirePositive(-1, "value") }
    }

    @Test
    fun `requireNonNegative accepts zero and positive values`() {
        Validations.requireNonNegative(0, "value")
        Validations.requireNonNegative(2, "value")
    }

    @Test
    fun `requireNonNegative rejects negative`() {
        assertThrows<IllegalArgumentException> { Validations.requireNonNegative(-1, "value") }
    }

    @Test
    fun `isInBounds returns expected values`() {
        assertTrue(Validations.isInBounds(0, 2))
        assertTrue(Validations.isInBounds(1, 2))
        assertFalse(Validations.isInBounds(2, 2))
        assertFalse(Validations.isInBounds(-1, 2))
    }
}

