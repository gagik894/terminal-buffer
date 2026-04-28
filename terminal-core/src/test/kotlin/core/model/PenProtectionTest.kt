package com.gagik.core.model

import com.gagik.core.codec.AttributeCodec
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PenProtectionTest {

    @Test
    fun `setSelectiveEraseProtection toggles current attr without disturbing visual attrs`() {
        val pen = Pen()
        pen.setAttributes(fg = 5, bg = 2, bold = true, faint = true, italic = true)
        val visualOnly = pen.currentAttr
        val visualOnlyExtended = pen.currentExtendedAttr

        pen.setSelectiveEraseProtection(true)
        val protectedAttr = pen.currentAttr

        assertAll(
            { assertTrue(pen.isSelectiveEraseProtected) },
            { assertTrue(AttributeCodec.isProtected(protectedAttr)) },
            { assertEquals(AttributeCodec.foreground(visualOnly), AttributeCodec.foreground(protectedAttr)) },
            { assertEquals(AttributeCodec.background(visualOnly), AttributeCodec.background(protectedAttr)) },
            { assertEquals(AttributeCodec.isBold(visualOnly), AttributeCodec.isBold(protectedAttr)) },
            { assertEquals(AttributeCodec.isFaint(visualOnly), AttributeCodec.isFaint(protectedAttr)) },
            { assertEquals(AttributeCodec.isItalic(visualOnly), AttributeCodec.isItalic(protectedAttr)) },
            { assertEquals(visualOnlyExtended, pen.currentExtendedAttr) }
        )
    }

    @Test
    fun `setAttributes preserves existing selective erase protection`() {
        val pen = Pen()
        pen.setSelectiveEraseProtection(true)

        pen.setAttributes(fg = 3, bg = 7, bold = false, italic = true, underlineStyle = UnderlineStyle.SINGLE)

        assertAll(
            { assertTrue(pen.isSelectiveEraseProtected) },
            { assertTrue(AttributeCodec.isProtected(pen.currentAttr)) }
        )
    }

    @Test
    fun `reset clears selective erase protection`() {
        val pen = Pen()
        pen.setSelectiveEraseProtection(true)

        pen.reset()

        assertAll(
            { assertFalse(pen.isSelectiveEraseProtected) },
            { assertFalse(AttributeCodec.isProtected(pen.currentAttr)) }
        )
    }

    @Test
    fun `blankAttr strips selective erase protection while preserving visual attrs`() {
        val pen = Pen()
        pen.setAttributes(fg = 6, bg = 1, bold = true, italic = false, underlineStyle = UnderlineStyle.SINGLE)
        pen.setSelectiveEraseProtection(true)

        val blankAttr = pen.blankAttr
        val blankExtendedAttr = pen.blankExtendedAttr

        assertAll(
            { assertFalse(AttributeCodec.isProtected(blankAttr)) },
            { assertEquals(AttributeCodec.foreground(pen.currentAttr), AttributeCodec.foreground(blankAttr)) },
            { assertEquals(AttributeCodec.background(pen.currentAttr), AttributeCodec.background(blankAttr)) },
            { assertEquals(AttributeCodec.isBold(pen.currentAttr), AttributeCodec.isBold(blankAttr)) },
            { assertEquals(AttributeCodec.isItalic(pen.currentAttr), AttributeCodec.isItalic(blankAttr)) },
            { assertEquals(pen.currentExtendedAttr, blankExtendedAttr) }
        )
    }
}
