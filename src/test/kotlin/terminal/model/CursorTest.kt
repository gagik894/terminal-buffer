package com.gagik.terminal.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Cursor")
class CursorTest {

    @Test
    fun `defaults to origin`() {
        val dimensions = GridDimensions(80, 24)
        val cursor = Cursor(dimensions)

        assertEquals(0, cursor.col)
        assertEquals(0, cursor.row)
    }

    @Test
    fun `set positions cursor within bounds`() {
        val dimensions = GridDimensions(80, 24)
        val cursor = Cursor(dimensions)

        cursor.set(10, 5)
        assertEquals(10, cursor.col)
        assertEquals(5, cursor.row)

        cursor.set(100, 50)
        assertEquals(79, cursor.col)
        assertEquals(23, cursor.row)

        cursor.set(-1, -1)
        assertEquals(0, cursor.col)
        assertEquals(0, cursor.row)
    }

    @Test
    fun `move shifts cursor relatively`() {
        val dimensions = GridDimensions(80, 24)
        val cursor = Cursor(dimensions)

        cursor.set(10, 10)
        cursor.move(5, -2)
        assertEquals(15, cursor.col)
        assertEquals(8, cursor.row)

        cursor.move(100, 100)
        assertEquals(79, cursor.col)
        assertEquals(23, cursor.row)

        cursor.move(-200, -200)
        assertEquals(0, cursor.col)
        assertEquals(0, cursor.row)
    }

    @Test
    fun `reset returns to origin`() {
        val dimensions = GridDimensions(80, 24)
        val cursor = Cursor(dimensions)

        cursor.set(40, 12)
        cursor.reset()
        assertEquals(0, cursor.col)
        assertEquals(0, cursor.row)
    }
}