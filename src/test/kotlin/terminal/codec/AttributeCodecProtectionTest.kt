package com.gagik.terminal.codec

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AttributeCodecProtectionTest {

    @Test
    fun `pack and unpack preserve selective erase protection`() {
        val packed = AttributeCodec.pack(
            fg = 9,
            bg = 4,
            bold = true,
            italic = false,
            underline = true,
            protected = true
        )
        val unpacked = AttributeCodec.unpack(packed)

        assertAll(
            { assertTrue(AttributeCodec.isProtected(packed)) },
            { assertEquals(1 shl 13, packed and (1 shl 13)) },
            { assertTrue(unpacked.selectiveEraseProtected) }
        )
    }

    @Test
    fun `withProtected toggles only the protection bit`() {
        val base = AttributeCodec.pack(7, 3, bold = true, italic = true, underline = false)
        val protectedAttr = AttributeCodec.withProtected(base, enabled = true)
        val unprotectedAttr = AttributeCodec.withProtected(protectedAttr, enabled = false)

        assertAll(
            { assertTrue(AttributeCodec.isProtected(protectedAttr)) },
            { assertEquals(AttributeCodec.foreground(base), AttributeCodec.foreground(protectedAttr)) },
            { assertEquals(AttributeCodec.background(base), AttributeCodec.background(protectedAttr)) },
            { assertEquals(AttributeCodec.isBold(base), AttributeCodec.isBold(protectedAttr)) },
            { assertEquals(AttributeCodec.isItalic(base), AttributeCodec.isItalic(protectedAttr)) },
            { assertEquals(AttributeCodec.isUnderline(base), AttributeCodec.isUnderline(protectedAttr)) },
            { assertFalse(AttributeCodec.isProtected(unprotectedAttr)) },
            { assertEquals(base, unprotectedAttr) }
        )
    }
}
