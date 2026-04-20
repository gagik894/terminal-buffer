package com.gagik.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CursorTest {

    @Test
    fun `defaults to origin`() {
        val cursor = Cursor()

        assertEquals(0, cursor.col)
        assertEquals(0, cursor.row)
    }

    @Test
    fun `position can be mutated directly`() {
        val cursor = Cursor()

        cursor.col = 12
        cursor.row = 5

        assertEquals(12, cursor.col)
        assertEquals(5, cursor.row)
    }
}